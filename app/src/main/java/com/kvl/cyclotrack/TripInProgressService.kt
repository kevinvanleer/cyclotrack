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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TripInProgressService @Inject constructor() : LifecycleService() {
    private val logTag = "TripInProgressService"
    private val accuracyThreshold = 7.5f

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var sensorsRepository: OnboardSensorsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var splitRepository: SplitRepository

    @Inject
    lateinit var gpsService: GpsService

    @Inject
    lateinit var bleService: BleService

    @Inject
    lateinit var onboardSensors: SensorLiveData

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private fun hrmSensor() = bleService.hrmSensor
    private fun cadenceSensor() = bleService.cadenceSensor
    private fun speedSensor() = bleService.speedSensor

    //fun gpsEnabled() = gpsService.accessGranted

    private fun gpsObserver(tripId: Long): Observer<Location> = Observer<Location> { newLocation ->
        Log.d(logTag, "onChanged gps observer")
        val newMeasurement = Measurements(tripId,
            LocationData(newLocation),
            hrmSensor().value?.bpm,
            cadenceSensor().value,
            speedSensor().value)
        lifecycleScope.launch {
            measurementsRepository.getLatestAccurate(tripId, accuracyThreshold)?.let { latest ->
                setTripProgress(latest, newMeasurement, tripId)
            }
            measurementsRepository.insertMeasurements(newMeasurement)
        }
    }

    private suspend fun setTripProgress(old: Measurements, new: Measurements, tripId: Long) {
        val timeStates = timeStateRepository.getTimeStates(tripId)
        val currentTimeState = timeStates.last()

        if (currentTimeState.state == TimeStateEnum.RESUME || currentTimeState.state == TimeStateEnum.START) {
            val accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold
            if (accurateEnough) {
                val trip = tripsRepository.get(tripId)
                val intervals = getTripInProgressIntervals(timeStates)
                val duration = accumulateTripTime(intervals)
                val distance = (trip.distance ?: 0.0) + getDistance(new, old)

                tripsRepository.updateTripStats(
                    TripStats(id = tripId,
                        distance = distance,
                        duration = duration,
                        averageSpeed = (distance / duration).toFloat(),
                        userWheelCircumference = trip.userWheelCircumference,
                        autoWheelCircumference = trip.autoWheelCircumference))

                if (crossedSplitThreshold(sharedPreferences,
                        distance,
                        trip.distance ?: Double.MAX_VALUE)
                ) {
                    try {
                        val lastSplit = splitRepository.getTripSplits(tripId).last()
                        splitRepository.addSplit(Split(
                            timestamp = System.currentTimeMillis(),
                            duration = duration - lastSplit.totalDuration,
                            distance = distance - lastSplit.totalDistance,
                            totalDuration = duration,
                            totalDistance = distance,
                            tripId = tripId
                        ))
                    } catch (e: NoSuchElementException) {
                        splitRepository.addSplit(Split(
                            timestamp = System.currentTimeMillis(),
                            duration = duration,
                            distance = distance,
                            totalDuration = duration,
                            totalDistance = distance,
                            tripId = tripId
                        ))
                    }
                }
            }
        }
    }

    private lateinit var thisGpsObserver: Observer<Location>

    private fun sensorObserver(tripId: Long): Observer<SensorModel> = Observer { newData ->
        lifecycleScope.launch {
            sensorsRepository.insertMeasurements(tripId, newData)
        }
    }

    private lateinit var thisSensorObserver: Observer<SensorModel>

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

    private fun startForegroundCompat(tripId: Long) {
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
            setArguments(Bundle().apply { putLong("tripId", tripId) })
        }.createPendingIntent()

        startForeground(tripId.toInt(),
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
        var biometrics = getBiometrics(id, applicationContext)
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
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")
        var tripId: Long = -1
        lifecycleScope.launch(Dispatchers.IO) {
            tripId = tripsRepository.createNewTrip().also { id ->
                LocalBroadcastManager.getInstance(this@TripInProgressService)
                    .sendBroadcast(Intent(getString(R.string.intent_action_tripId_created)).apply {
                        putExtra("tripId", id)
                    })
                timeStateRepository.appendTimeState(TimeState(id, TimeStateEnum.START))
                Log.d(logTag, "created new trip with id ${id}")
            }
        }.join()

        Log.d(logTag, "Start trip service for ID ${tripId}; this=$this")
        startObserving(tripId)

        startForegroundCompat(tripId)

        lifecycleScope.launch(Dispatchers.IO) {
            tripsRepository.updateBiometrics(
                getCombinedBiometrics(tripId))
        }
    }

    private fun restart(tripId: Long) {
        Log.d(logTag, "Restart trip")
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")
        /*lifecycleScope.launch(Dispatchers.IO) {
            timeStateRepository.appendTimeState(TimeState(tripId, TimeStateEnum.RESUME))
            Log.d(logTag, "restart trip with id ${tripId}")
        }*/

        Log.d(logTag, "Restart trip service for ID ${tripId}; this=$this")
        startObserving(tripId)

        startForegroundCompat(tripId)
    }

    private fun startObserving(tripId: Long) {
        if (!::thisGpsObserver.isInitialized || !gpsService.hasObservers()) {
            thisGpsObserver = gpsObserver(tripId)
            gpsService.observe(this, thisGpsObserver)
        }

        if (FeatureFlags.betaBuild) {
            if (!::thisSensorObserver.isInitialized || !onboardSensors.hasObservers()) {
                thisSensorObserver = sensorObserver(tripId)
                onboardSensors.observe(this, thisSensorObserver)
            }
        }
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
        startObserving(tripId)
    }

    private suspend fun end(tripId: Long) {
        Log.d(logTag, "Called end()")
        var job: Job? = null
        tripId.takeIf { it >= 0 }?.let { id ->
            job = lifecycle.coroutineScope.launch {
                timeStateRepository.appendTimeState(TimeState(tripId = id,
                    state = TimeStateEnum.STOP))
                tripsRepository.endTrip(id)
            }
        }

        if (::thisGpsObserver.isInitialized) {
            gpsService.removeObserver(thisGpsObserver)
        }
        if (::thisSensorObserver.isInitialized) {
            onboardSensors.removeObserver(thisSensorObserver)
        }

        bleService.disconnect()
        gpsService.stopListening()
        job?.join()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                getString(R.string.action_initialize_trip_service) -> Log.d(logTag,
                    "Initialize trip service")
                getString(R.string.action_start_trip_service) -> {

                    when (val tripId = it.getLongExtra("tripId", -1)) {
                        -1L -> lifecycleScope.launch { start() }
                        else -> restart(tripId)
                    }
                }
                getString(R.string.action_pause_trip_service) ->
                    pause(it.getLongExtra("tripId", -1))
                getString(R.string.action_resume_trip_service) ->
                    resume(it.getLongExtra("tripId", -1))
                getString(R.string.action_stop_trip_service) -> lifecycleScope.launch {
                    end(it.getLongExtra("tripId", -1))
                }
                else -> Log.d(logTag, "Received intent ${intent}")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(logTag, "::onCreate; this=$this")
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")
        gpsService.startListening()
        bleService.initialize()
    }
}
