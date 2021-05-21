package com.kvl.cyclotrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDeepLinkBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
@Singleton
class TripInProgressService @Inject constructor() : LifecycleService() {
    private val logTag = "TripInProgressService"
    var currentTripId: Long? = null

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

    private fun start(tripId: Long) {
        currentTripId = tripId
        Log.d(logTag, "Start trip service for ID ${currentTripId}; this=$this")
        gpsService.observe(this, gpsObserver)

        if (BuildConfig.BUILD_TYPE != "prod") {
            onboardSensors.observe(this, sensorObserver)
        }

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
                getString(R.string.action_start_trip_service) -> start(it.getLongExtra("tripId",
                    -1))
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
        gpsService.startListening()
        bleService.initialize()
    }
}
