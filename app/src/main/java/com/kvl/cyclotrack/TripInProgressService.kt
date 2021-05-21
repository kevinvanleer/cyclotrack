package com.kvl.cyclotrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavDeepLinkBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@AndroidEntryPoint
class TripInProgressService @Inject constructor() : LifecycleService() {
    private val logTag = "TripInProgressService"
    var currentTripId: Long? = null

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var sensorsRepository: OnboardSensorsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var gpsService: GpsService

    @Inject
    lateinit var bleService: BleService

    @Inject
    lateinit var onboardSensors: SensorLiveData

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    fun hrmSensor() = bleService.hrmSensor
    fun cadenceSensor() = bleService.cadenceSensor
    fun speedSensor() = bleService.speedSensor

    //fun gpsEnabled() = gpsService.accessGranted

    private val gpsObserver: Observer<Location> = Observer<Location> { newLocation ->
        Log.d(logTag, "onChanged gps observer")
        currentTripId?.let { id ->
            lifecycleScope.launch {
                measurementsRepository.insertMeasurements(Measurements(id,
                    LocationData(newLocation),
                    hrmSensor().value?.bpm,
                    cadenceSensor().value,
                    speedSensor().value))
            }
        }
    }

    private val sensorObserver: Observer<SensorModel> = Observer { newData ->
        currentTripId?.let { id ->
            lifecycleScope.launch {
                sensorsRepository.insertMeasurements(id, newData)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        //TODO: MAKE THIS YOUR OWN -- COPIED FROM POST
        val channel = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH)
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    private fun startForegroundCompat() {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(
                getString(R.string.notification_id_trip_in_progress),
                getString(R.string.notification_channel_name_trip_in_progress)
            )
        } else {
            getString(R.string.notification_id_trip_in_progress)
        }

        val pendingIntent = NavDeepLinkBuilder(this).apply {
            setGraph(R.navigation.cyclotrack_nav_graph)
            setDestination(R.id.TripInProgressFragment)
            setArguments(Bundle().apply { putLong("tripId", currentTripId ?: 0) })
        }.createPendingIntent()

        startForeground(currentTripId?.toInt() ?: 0,
            NotificationCompat.Builder(this, channelId).apply {
                priority = NotificationCompat.PRIORITY_MAX
                setOngoing(true)
                setShowWhen(false)
                setContentTitle(getText(R.string.notification_trip_in_progress_title))
                setContentText(getText(R.string.notification_export_trip_in_progress_message))
                setSmallIcon(R.drawable.ic_cyclotrack_notification)
                setContentIntent(pendingIntent)
            }.build().also { it.flags = it.flags or Notification.FLAG_ONGOING_EVENT })
    }

    suspend fun getCombinedBiometrics(id: Long): Biometrics {
        var biometrics = getBiometrics(id, sharedPreferences)
        Log.d(logTag, "biometrics prefs: ${biometrics}")

        lifecycleScope.launch {
            val googleFitApiService = GoogleFitApiService.instance
            if (googleFitApiService.hasPermission()) {
                val weightDeferred = async { googleFitApiService.getLatestWeight() }
                val heightDeferred = async { googleFitApiService.getLatestHeight() }
                val hrDeferred = async { googleFitApiService.getLatestRestingHeartRate() }

                weightDeferred.await().let {
                    Log.d(logTag, "google weight: ${it}")
                    biometrics = biometrics.copy(userWeight = it)
                }
                heightDeferred.await().let {
                    Log.d(logTag, "google height: ${it}")
                    biometrics = biometrics.copy(userHeight = it)
                }
                hrDeferred.await().let {
                    Log.d(logTag, "google resting hr: ${it}")
                    biometrics = biometrics.copy(userRestingHeartRate = it)
                }
                Log.d(logTag, "biometrics google: ${biometrics}")
            }
        }.join()

        Log.d(logTag, "biometrics: ${biometrics}")
        return biometrics
    }

    private suspend fun start() {
        Log.d(logTag, "Start trip")
        Log.d(logTag, "::start(); this=$this; tripId=$currentTripId")
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")
        lifecycleScope.launch(Dispatchers.IO) {
            currentTripId = tripsRepository.createNewTrip().also { id ->
                tripsRepository.updateBiometrics(
                    getCombinedBiometrics(id))
                LocalBroadcastManager.getInstance(this@TripInProgressService)
                    .sendBroadcast(Intent(getString(R.string.intent_action_tripId_created)).apply {
                        putExtra("tripId", id)
                    })
                timeStateRepository.appendTimeState(TimeState(id, TimeStateEnum.START))
                Log.d(logTag, "created new trip with id ${id}")
            }
        }.join()

        Log.d(logTag, "Start trip service for ID ${currentTripId}; this=$this")
        gpsService.observe(this, gpsObserver)

        if (BuildConfig.BUILD_TYPE != "prod") {
            onboardSensors.observe(this, sensorObserver)
        }

        //startClock()
        startForegroundCompat()
    }

    private fun pause(tripId: Long) {
        Log.d(logTag, "Called pause()")
        lifecycle.coroutineScope.launch {
            timeStateRepository.appendTimeState(TimeState(tripId = tripId,
                state = TimeStateEnum.PAUSE))
        }
    }

    private fun resume(tripId: Long) {
        Log.d(logTag, "Called resume()")
        lifecycle.coroutineScope.launch {
            timeStateRepository.appendTimeState(TimeState(tripId = tripId,
                state = TimeStateEnum.RESUME))
        }
    }

    private fun end(tripId: Long) {
        Log.d(logTag, "Called end()")
        tripId.takeIf { it >= 0 }?.let { id ->
            lifecycle.coroutineScope.launch {
                timeStateRepository.appendTimeState(TimeState(tripId = id,
                    state = TimeStateEnum.STOP))
            }
        }
        //TODO: I DO NOT SEEM TO GET THE SAME SERVICE OBJECT ON REENTRY
        currentTripId = null
        bleService.disconnect()
        gpsService.stopListening()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                getString(R.string.action_initialize_trip_service) -> Log.d(logTag,
                    "Initialize trip service")
                getString(R.string.action_start_trip_service) -> lifecycleScope.launch { start() }
                getString(R.string.action_pause_trip_service) ->
                    pause(it.getLongExtra("tripId", -1))
                getString(R.string.action_resume_trip_service) ->
                    resume(it.getLongExtra("tripId", -1))
                getString(R.string.action_stop_trip_service) ->
                    end(it.getLongExtra("tripId", -1))
                else -> Log.d(logTag, "Received intent ${intent}")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(logTag, "::onCreate; this=$this; tripId=$currentTripId")
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")
        gpsService.startListening()
        bleService.initialize()
    }
}
