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
import androidx.core.content.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDeepLinkBuilder
import com.kvl.cyclotrack.events.StartTripEvent
import com.kvl.cyclotrack.events.TripProgressEvent
import com.kvl.cyclotrack.events.WheelCircumferenceEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class TripInProgressService @Inject constructor() :
    LifecycleService() {
    private val logTag = "TripInProgressService"
    private val accuracyThreshold = 7.5f
    private val speedThreshold = 0.5f

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

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    private val userCircumference: Float?
        get() = getUserCircumferenceOrNull(sharedPreferences)

    private var circumferenceState = CircumferenceState(measuring = false,
        initialCircRevs = 0,
        initialCircDistance = 0.0,
        circumference = null)

    private var tripProgress: TripProgress? = null

    private fun hrmSensor() = bleService.hrmSensor
    private fun cadenceSensor() = bleService.cadenceSensor
    private fun speedSensor() = bleService.speedSensor

    private fun gpsObserver(tripId: Long): Observer<Location> = Observer<Location> { newLocation ->
        Log.d(logTag, "onChanged gps observer")
        val newMeasurement = Measurements(tripId,
            LocationData(newLocation),
            hrmSensor().value?.bpm,
            cadenceSensor().value,
            speedSensor().value)
        lifecycleScope.launch {
            timeStateRepository.getTimeStates(tripId).let { timeStates ->
                when (timeStates.lastOrNull()?.let { currentTimeState ->
                    (currentTimeState.state == TimeStateEnum.RESUME || currentTimeState.state == TimeStateEnum.START)
                } ?: false) {
                    true -> {
                        measurementsRepository.getLatestAccurate(tripId, accuracyThreshold)
                            ?.let { latest ->
                                setTripProgress(latest, newMeasurement, timeStates, tripId)
                            }
                    }
                    else -> setTripPaused(newMeasurement)
                }
            }
            if (tripId >= 0)
                measurementsRepository.insertMeasurements(newMeasurement)
        }
    }

    private suspend fun setTripProgress(
        old: Measurements,
        new: Measurements,
        timeStates: Array<TimeState>,
        tripId: Long,
    ) {
        val accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold
        val intervals = getTripInProgressIntervals(timeStates)
        val duration = accumulateTripTime(intervals)
        val trip = tripsRepository.get(tripId)

        tripProgress?.let {
            it.measurements?.speedRevolutions?.let { revs ->
                calculateWheelCircumference(
                    it.measurements,
                    trip.distance ?: 0.0,
                    revs,
                    circumferenceState).also { newState ->
                    circumferenceState = newState
                    circumferenceState.circumference?.let { newCirc ->
                        updateAutoCircumference(tripId, newCirc)
                    }
                }
            }
        }

        if (accurateEnough) {
            val distanceDelta = getDistance(new, old)
            val durationDelta = duration - (trip.duration ?: 0.0)
            val newSpeed = getSpeed(new, speedThreshold)
            val totalDistance = when (newSpeed > speedThreshold) {
                true -> (trip.distance ?: 0.0) + distanceDelta
                else -> trip.distance ?: 0.0
            }

            tripsRepository.updateTripStats(
                TripStats(id = tripId,
                    distance = totalDistance,
                    duration = duration,
                    averageSpeed = (totalDistance / duration).toFloat()))

            if (crossedSplitThreshold(sharedPreferences,
                    totalDistance,
                    trip.distance ?: Double.MAX_VALUE)
            ) {
                splitRepository.getTripSplits(tripId).lastOrNull()?.let { lastSplit ->
                    splitRepository.addSplit(Split(
                        timestamp = System.currentTimeMillis(),
                        duration = duration - lastSplit.totalDuration,
                        distance = totalDistance - lastSplit.totalDistance,
                        totalDuration = duration,
                        totalDistance = totalDistance,
                        tripId = tripId
                    ))
                } ?: splitRepository.addSplit(Split(
                    timestamp = System.currentTimeMillis(),
                    duration = duration,
                    distance = totalDistance,
                    totalDuration = duration,
                    totalDistance = totalDistance,
                    tripId = tripId
                ))
            }

            val newSlope = calculateSlope(
                newSpeed,
                distanceDelta,
                new,
                speedThreshold,
                tripProgress,
                durationDelta)

            TripProgress(
                measurements = new,
                speed = newSpeed,
                maxSpeed = max(if (newSpeed.isFinite()) newSpeed else 0f,
                    tripProgress?.maxSpeed ?: 0f),
                distance = totalDistance,
                slope = newSlope,
                duration = duration,
                accuracy = new.accuracy,
                bearing = new.bearing,
                tracking = true
            )
        } else {
            (tripProgress?.copy(duration = duration,
                accuracy = new.accuracy,
                tracking = false)
                ?: TripProgress(duration = duration,
                    speed = 0f,
                    maxSpeed = 0f,
                    distance = 0.0,
                    slope = 0.0,
                    measurements = null,
                    accuracy = new.accuracy,
                    bearing = new.bearing,
                    tracking = false))
        }.let {
            EventBus.getDefault().post(TripProgressEvent(it))
            tripProgress = it
        }
    }

    private suspend fun updateAutoCircumference(tripId: Long, circumference: Float) {
        sharedPreferences.edit {
            this.putFloat("auto_circumference", circumference)
        }
        tripsRepository.updateWheelCircumference(TripWheelCircumference(id = tripId,
            userWheelCircumference = userCircumference,
            autoWheelCircumference = circumference))
        EventBus.getDefault().post(WheelCircumferenceEvent(circumference))
    }

    private fun setTripPaused(new: Measurements) {
        val accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold

        val newSpeed = when (accurateEnough) {
            true -> getSpeed(new, speedThreshold)
            else -> 0f
        }

        when (accurateEnough) {
            true -> {
                tripProgress?.copy(
                    speed = newSpeed,
                    measurements = null,
                    accuracy = new.accuracy,
                    tracking = accurateEnough)
            }
            else ->
                tripProgress?.copy(
                    accuracy = new.accuracy,
                    measurements = null,
                    tracking = accurateEnough)
        } ?: TripProgress(duration = 0.0,
            speed = newSpeed,
            maxSpeed = 0f,
            distance = 0.0,
            slope = 0.0,
            measurements = null,
            accuracy = new.accuracy,
            bearing = new.bearing,
            tracking = accurateEnough).let {
            EventBus.getDefault().post(TripProgressEvent(it))
            tripProgress = it
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

    suspend fun getCombinedBiometrics(id: Long): Biometrics =
        getCombinedBiometrics(id,
            System.currentTimeMillis(),
            applicationContext,
            lifecycleScope,
            null,
            googleFitApiService)

    private suspend fun start() {
        Log.d(logTag, "Start trip")
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")
        var tripId: Long = -1

        clearState()

        lifecycleScope.launch(Dispatchers.IO) {
            tripId = tripsRepository.createNewTrip().also { id ->
                EventBus.getDefault().post(StartTripEvent(id))
                timeStateRepository.appendTimeState(TimeState(id, TimeStateEnum.START))
                Log.d(logTag, "created new trip with id ${id}")
            }
        }.join()

        Log.d(logTag, "Start trip service for ID ${tripId}; this=$this")
        startObserving(tripId)

        startForegroundCompat(tripId)

        lifecycleScope.launch(Dispatchers.IO) {
            tripsRepository.updateWheelCircumference(TripWheelCircumference(id = tripId,
                userWheelCircumference = userCircumference,
                autoWheelCircumference = circumferenceState.circumference))
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

    private fun clearState() {
        circumferenceState = CircumferenceState(measuring = false,
            initialCircRevs = 0,
            initialCircDistance = 0.0,
            circumference = null)

        tripProgress = null
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

        clearState()
        bleService.disconnect()
        gpsService.stopListening()
        job?.join()
        stopSelf()
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
        clearState()
    }
}
