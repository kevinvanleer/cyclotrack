package com.kvl.cyclotrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
@Singleton
class TripInProgressService @Inject constructor() : LifecycleService() {
    private val logTag = "TripInProgressService"
    private var tripId: Long? = null

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

    fun gpsEnabled() = gpsService.accessGranted

    fun startGps() = gpsService.startListening()
    fun startBle() = bleService.initialize()
    fun stopBle() = bleService.disconnect()

    private val gpsObserver: Observer<Location> = Observer<Location> { newLocation ->
        Log.d(logTag, "onChanged gps observer")
        if (tripId != null) {
            lifecycleScope.launch {
                measurementsRepository.insertMeasurements(Measurements(tripId!!,
                    LocationData(newLocation),
                    hrmSensor().value?.bpm,
                    cadenceSensor().value,
                    speedSensor().value))
            }
        }
    }

    private val sensorObserver: Observer<SensorModel> = Observer { newData ->
        lifecycleScope.launch {
            sensorsRepository.insertMeasurements(tripId!!, newData)
        }
    }

    private fun start(tripId: Long) {
        this.tripId = tripId
        Log.d(logTag, "Start trip service for ID $this.tripId")
        gpsService.observe(this, gpsObserver)

        if (BuildConfig.BUILD_TYPE != "prod") {
            onboardSensors.observe(this, sensorObserver)
        }


        /*startForeground(tripId.toInt(), NotificationCompat.Builder(this,
            getString(R.string.notification_id_trip_in_progress))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentTitle(getText(R.string.notification_trip_in_progress_title))
            .setContentText(getText(R.string.notification_export_trip_in_progress_message))
            .setSmallIcon(R.drawable.ic_cyclotrack_notification)
            .setContentIntent(pendingIntent)
            .setTicker("Important ride information that is displayed here and is too long to fit")
            .build())*/
        startForegroundCompat()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val channel = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
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
                "Cyclotrack: in ride"
            )
        } else {
            getString(R.string.notification_id_trip_in_progress)
        }

        val pendingIntent: PendingIntent =
            Intent(this, TripInProgressFragment::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        startForeground(tripId?.toInt() ?: 0, NotificationCompat.Builder(this, channelId)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentTitle(getText(R.string.notification_trip_in_progress_title))
            .setContentText(getText(R.string.notification_export_trip_in_progress_message))
            .setSmallIcon(R.drawable.ic_cyclotrack_notification)
            .setContentIntent(pendingIntent)
            .setTicker("Important ride information that is displayed here and is too long to fit")
            .build())
    }

    override fun onCreate() {
        super.onCreate()
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
        lifecycle.coroutineScope.launch {
            timeStateRepository.appendTimeState(TimeState(tripId = tripId,
                state = TimeStateEnum.STOP))
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                getString(R.string.action_start_trip_service) -> start(it.getLongExtra("tripId", 0))
                getString(R.string.action_pause_trip_service) ->
                    pause(it.getLongExtra("tripId", 0))
                getString(R.string.action_resume_trip_service) ->
                    resume(it.getLongExtra("tripId", 0))
                getString(R.string.action_stop_trip_service) ->
                    end(it.getLongExtra("tripId", 0))
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
}
