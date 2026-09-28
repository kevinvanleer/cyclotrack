package com.kvl.cyclotrack

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.database.sqlite.SQLiteConstraintException
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDeepLinkBuilder
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.CadenceSpeedMeasurementRepository
import com.kvl.cyclotrack.data.CyclingPowerMeasurementRepository
import com.kvl.cyclotrack.data.DerivedTripState
import com.kvl.cyclotrack.data.HeartRateMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurementRepository
import com.kvl.cyclotrack.data.OnboardSensorMeasurement
import com.kvl.cyclotrack.data.SensorType
import com.kvl.cyclotrack.events.BatteryEvent
import com.kvl.cyclotrack.events.ConnectedBikeEvent
import com.kvl.cyclotrack.events.OnboardSensorEvent
import com.kvl.cyclotrack.events.PhysicsSensorEvent
import com.kvl.cyclotrack.events.SlopeEvent
import com.kvl.cyclotrack.events.StartTripEvent
import com.kvl.cyclotrack.events.ThermalTierEvent
import com.kvl.cyclotrack.events.TripProgressEvent
import com.kvl.cyclotrack.events.WheelCircumferenceEvent
import com.kvl.cyclotrack.util.SystemUtils
import com.kvl.cyclotrack.util.shouldCollectOnboardSensors
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import javax.inject.Inject
import kotlin.math.max

fun Array<Split>.lastTwo(tripId: Long): Pair<Split, Split> {
    val lastTwo = this.drop(max(this.size - 2, 0))
    val secondLast = when (lastTwo.size) {
        2 -> lastTwo[0]
        else -> Split(tripId = tripId)
    }
    val last = when (lastTwo.size) {
        2 -> lastTwo[1]
        1 -> lastTwo[0]
        else -> Split(tripId = tripId)
    }
    return Pair(secondLast, last)
}

fun doSplitStuff(
    lastSplit: Split,
    durationDelta: Double,
    distanceDelta: Float,
    priorSplitDistance: Double,
    distanceConversionFactor: Double,
): Split = incrementSplit(
    when (crossedSplitThreshold(
        distanceConversionFactor,
        lastSplit.totalDistance,
        priorSplitDistance
    )) {
        true -> Split(
            totalDuration = lastSplit.totalDuration,
            totalDistance = lastSplit.totalDistance,
            tripId = lastSplit.tripId
        )

        else -> lastSplit
    }, durationDelta, distanceDelta
)

@AndroidEntryPoint
class TripInProgressService @Inject constructor() :
    LifecycleService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var blockAutoResumeEnabled = true
    private var autopauseEnabled = false
    private var autopausePauseThreshold = 5000L
    private var autopauseResumeThreshold = 5000L
    private var autopauseRpmThreshold: Float = 50f
    private var autoTimeState = TimeStateEnum.RESUME
    private val logTag = "TripInProgressService"
    private val accuracyThreshold = LOCATION_ACCURACY_THRESHOLD
    private val speedThreshold = 0.5f
    private var nextWeatherUpdate = 0L
    private val weatherUpdatePeriod = 5 * 60000

    companion object {
        @Volatile
        var running = false
            private set

        // ponytail: fixed 1 s vibration-averaging EMA; make configurable if a device needs tuning
        private const val GRADE_ACCEL_TAU_SECONDS = 1.0
    }

    var bike: Bike? = null

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var bikesRepository: BikeRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var sensorsRepository: OnboardSensorsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var splitRepository: SplitRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var heartRateMeasurementRepository: HeartRateMeasurementRepository

    @Inject
    lateinit var cadenceSpeedMeasurementRepository: CadenceSpeedMeasurementRepository

    @Inject
    lateinit var cyclingPowerMeasurementRepository: CyclingPowerMeasurementRepository

    @Inject
    lateinit var gpsService: GpsService

    @Inject
    lateinit var bleService: BleService

    @Inject
    lateinit var onboardSensorSource: OnboardSensorSource

    @Inject
    lateinit var thermalMonitor: ThermalMonitor

    @Inject
    lateinit var batterySource: BatterySource

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    private val userCircumference: Float?
        get() = userCircumferenceToMeters(bike?.wheelCircumference)

    private var autoCircumference: Float? = null

    private var tripProgress: TripProgress? = null

    private val derivedTripState = ArrayList<DerivedTripState>()

    // Trips whose removal is in flight. Guards the resume+delete race: a racing resume for a
    // trip being deleted is ignored so it can't re-start recording into a row about to vanish.
    // Main-thread confined (only touched from onStartCommand).
    private val removingTrips = mutableSetOf<Long>()

    private var hrmBpm: Short? = null
    private var speed: SpeedData? = null
    private var cadence: CadenceData? = null
    private var power: PowerData? = null

    val hrmEventHandler: (Long) -> (HrmData) -> Unit = { tripId ->
        { event ->
            if (running) event.bpm?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        heartRateMeasurementRepository.save(
                            HeartRateMeasurement(
                                tripId = tripId,
                                heartRate = event.bpm,
                                energyExpended = event.energyExpended,
                                rrIntervals = event.rrIntervals,
                                timestamp = event.timestamp ?: SystemUtils.currentTimeMillis()
                            )
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.e(logTag, "Failed to add heart rate measurement", e)
                        handleSqliteConstraintException(e)
                    }
                }
            }
        }
    }
    lateinit var thisHrmEventHandler: (HrmData) -> Unit

    @Subscribe
    fun onHrmData(event: HrmData) {
        hrmBpm = event.bpm
        thisHrmEventHandler(event)
    }

    val cadenceEventHandler: (Long) -> (CadenceData) -> Unit = { tripId ->
        { event ->
            if (running) event.revolutionCount?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        cadenceSpeedMeasurementRepository.save(
                            CadenceSpeedMeasurement(
                                tripId = tripId,
                                revolutions = event.revolutionCount,
                                lastEvent = event.lastEvent!!,
                                rpm = event.rpm,
                                sensorType = SensorType.CADENCE,
                                timestamp = event.timestamp ?: SystemUtils.currentTimeMillis()
                            )
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.e(logTag, "Failed to add cadence measurement", e)
                        handleSqliteConstraintException(e)
                    }
                }
            }
        }
    }
    lateinit var thisCadenceEventHandler: (CadenceData) -> Unit

    @Subscribe
    fun onCadenceData(event: CadenceData) {
        cadence = event
        thisCadenceEventHandler(event)
    }

    val speedEventHandler: (Long) -> (SpeedData) -> Unit = { tripId ->
        { event ->
            if (running) event.revolutionCount?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    val currentTime = SystemUtils.currentTimeMillis();
                    try {
                        cadenceSpeedMeasurementRepository.save(
                            CadenceSpeedMeasurement(
                                tripId = tripId,
                                revolutions = event.revolutionCount,
                                lastEvent = event.lastEvent!!,
                                rpm = event.rpm,
                                sensorType = SensorType.SPEED,
                                timestamp = event.timestamp ?: currentTime
                            )
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.e(logTag, "Failed to add speed measurements", e)
                        handleSqliteConstraintException(e)
                    }

                    if (autopauseEnabled) {
                        handleAutoPause(tripId, currentTime)
                    }
                }
            }
        }
    }

    private suspend fun handleAutoPause(tripId: Long, currentTime: Long) =
        cadenceSpeedMeasurementRepository.getAutoTimeStates(
            tripId = tripId,
            referenceTime = currentTime,
            pauseThreshold = autopausePauseThreshold,
            resumeThreshold = autopauseResumeThreshold,
            rpmThreshold = autopauseRpmThreshold,
        ).find { p -> p.triggered }?.let { currentAutoTimeState ->
            Log.d(
                logTag,
                "$autopauseEnabled,$autopausePauseThreshold,$autopauseResumeThreshold,$autopauseRpmThreshold"
            )
            val currentTimeState = timeStateRepository.getLatest(tripId)

            val blockAutoResume =
                blockAutoResumeEnabled && currentAutoTimeState.timeState == TimeStateEnum.RESUME
                        && !currentTimeState.auto
                        && currentTimeState.state == TimeStateEnum.PAUSE

            if (currentAutoTimeState.timeState != autoTimeState) {
                autoTimeState = currentAutoTimeState.timeState
                if (!blockAutoResume && autoTimeState != currentTimeState.state) {
                    try {
                        timeStateRepository.appendTimeState(
                            TimeState(
                                tripId = tripId,
                                state = autoTimeState,
                                timestamp = currentAutoTimeState.timestamp,
                                auto = true
                            )
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.e(logTag, "Failed to add auto-pause time state", e)
                        handleSqliteConstraintException(e)
                    }
                }
            }
        }

    lateinit var thisSpeedEventHandler: (SpeedData) -> Unit

    @Subscribe
    fun onSpeedData(event: SpeedData) {
        speed = event
        thisSpeedEventHandler(event)
    }

    @Subscribe
    fun onBikeConnect(event: ConnectedBikeEvent) {
        updateBike(event.bike)
    }

    val powerEventHandler: (Long) -> (PowerData) -> Unit = { tripId ->
        { event ->
            if (running) event.instantaneousPower?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        cyclingPowerMeasurementRepository.insertPowerData(tripId, event)
                    } catch (e: SQLiteConstraintException) {
                        Log.e(logTag, "Failed to add power measurement", e)
                        handleSqliteConstraintException(e)
                    }
                }
            }
        }
    }

    lateinit var thisPowerEventHandler: (PowerData) -> Unit

    @Subscribe
    fun onPowerData(event: PowerData) {
        power = event
        thisPowerEventHandler(event)
    }

    private fun updateBike(newBike: Bike) {
        if (newBike != bike) {
            bike = newBike
        }
        newBike.id?.let { bikeId ->
            lifecycle.coroutineScope.launch {
                tripsRepository.getNewest()?.let { trip ->
                    if (trip.inProgress && trip.id != null && trip.bikeId != bikeId) {
                        try {
                            tripsRepository.updateBikeId(trip.id, bikeId)
                            tripsRepository.updateWheelCircumference(
                                TripWheelCircumference(
                                    id = trip.id,
                                    userWheelCircumference = userCircumferenceToMeters(newBike.wheelCircumference),
                                    autoWheelCircumference = autoCircumference
                                )
                            )
                        } catch (e: SQLiteConstraintException) {
                            Log.e(logTag, "Failed to update bike for trip", e)
                            handleSqliteConstraintException(e)
                        }
                    }
                }
            }
        }
    }

    private fun getWeather(location: LocationData, tripId: Long) {
        val currentTime = SystemUtils.currentTimeMillis()
        if (currentTime > nextWeatherUpdate) {
            when (nextWeatherUpdate) {
                0L -> nextWeatherUpdate =
                    currentTime + weatherUpdatePeriod

                else -> while (nextWeatherUpdate < currentTime) {
                    nextWeatherUpdate += weatherUpdatePeriod
                }
            }
            val lat = location.latitude
            val lng = location.longitude

            val jsonAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter(WeatherResponse::class.java)
            val devAppId = getString(R.string.openweather_api_key)
            val weatherUrl =
                "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lng&appid=$devAppId"
            OkHttpClient().let { client ->
                Request.Builder()
                    .url(weatherUrl)
                    .build()
                    .let { request ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                client.newCall(request).execute().let { response ->
                                    Log.d(logTag, "Received response from OpenWeather")
                                    Log.d(logTag, response.body.toString())
                                    if (response.isSuccessful) {
                                        try {
                                            response.body?.source()
                                                ?.let { jsonAdapter.nullSafe().fromJson(it) }
                                                ?.toWeather(tripId)
                                                ?.let { weatherRepository.recordWeather(it) }
                                        } catch (e: JsonDataException) {
                                            Log.w(logTag, "Failed to parse response", e)
                                            FirebaseCrashlytics.getInstance().recordException(e)
                                        } catch (e: SQLiteConstraintException) {
                                            Log.e(logTag, "Failed to add weather measurements", e)
                                            handleSqliteConstraintException(e)
                                        }
                                    } else {
                                        nextWeatherUpdate = 0
                                        Log.i(
                                            logTag,
                                            "Unexpected openweathermap response: $response"
                                        )
                                    }
                                }
                            } catch (e: IOException) {
                                Log.i(logTag, "Weather request failed", e)
                                nextWeatherUpdate = 0
                            }
                        }
                    }
            }
        }
    }

    private fun gpsObserver(tripId: Long): Observer<Location> = Observer { newLocation ->
        Log.d(logTag, "onChanged gps observer")
        if (!running) return@Observer
        val newMeasurement = Measurements(
            tripId,
            LocationData(newLocation),
        )
        getWeather(LocationData(newLocation), tripId)
        lifecycleScope.launch {
            if (tripId >= 0) {
                try {
                    measurementsRepository.insertMeasurements(newMeasurement)
                } catch (e: SQLiteConstraintException) {
                    Log.e(logTag, "Failed to add gps measurements", e)
                    handleSqliteConstraintException(e)
                }
            }
            try {
                timeStateRepository.getTimeStates(tripId).let { timeStates ->
                    timeStates.lastOrNull().let { currentTimeState ->
                        when (
                            currentTimeState?.state == TimeStateEnum.RESUME || currentTimeState?.state == TimeStateEnum.START
                        ) {
                            true -> {
                                measurementsRepository.getLatestAccurate(
                                    tripId = tripId,
                                    accuracyThreshold = accuracyThreshold,
                                    minTime = currentTimeState?.timestamp ?: 0L,
                                    maxTime = newMeasurement.time
                                )
                                    ?.let { latest ->
                                        setTripProgress(latest, newMeasurement, timeStates, tripId)
                                    }
                            }

                            else -> setTripPaused(newMeasurement)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to set trip progress", e)
            }
        }
    }

    private fun handleSqliteConstraintException(e: SQLiteConstraintException) {
        FirebaseCrashlytics.getInstance().recordException(e)
        Log.i(logTag, "Terminating after SQLiteConstraintException...")
        shutdown()
    }

    private fun updateDerivedTripState(
        tripId: Long,
        totalDistance: Double,
        distanceDelta: Double,
        totalDuration: Double,
        durationDelta: Double,
        newMeasurements: Measurements,
        derivedTripState: ArrayList<DerivedTripState>,
    ): DerivedTripState {
        var revCount = 0
        var distance = 0.0
        derivedTripState.find { it.speedRevolutions != null }?.let {
            revCount = it.speedRevolutions?.let { startRevs ->
                speed?.revolutionCount?.minus(startRevs)
            } ?: derivedTripState.lastOrNull()?.revTotal ?: 0
            distance = totalDistance - it.totalDistance
        }

        return DerivedTripState(
            tripId = tripId,
            timestamp = newMeasurements.time,
            duration = totalDuration,
            durationDelta = durationDelta,
            totalDistance = totalDistance,
            distanceDelta = distanceDelta,
            altitude = newMeasurements.altitude,
            altitudeDelta = derivedTripState.lastOrNull()?.altitude?.let { lastAlt ->
                newMeasurements.altitude - lastAlt
            } ?: 0.0,
            revTotal = revCount,
            circumference = distance / revCount,
            slope = 0.0,
            speedRevolutions = speed?.revolutionCount
        )
    }

    private suspend fun setTripProgress(
        old: Measurements,
        new: Measurements,
        timeStates: Array<TimeState>,
        tripId: Long,
    ) {
        Log.v(logTag, "setTripProgress")
        val accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold

        splitRepository.getTripSplits(tripId).lastTwo(tripId)
            .let { (secondLast, lastSplit) ->
                val totalDuration = accumulateTripTime(
                    getTripInProgressIntervals(
                        timeStates
                    )
                )
                if (accurateEnough) {
                    val distanceDelta = getDistance(new, old)
                    val durationDelta = totalDuration - lastSplit.totalDuration
                    val newSpeed = getSpeed(new, speedThreshold)
                    val totalDistance = lastSplit.totalDistance + distanceDelta

                    val sampleSize = 100
                    val varianceThreshold = 1e-6
                    derivedTripState.add(
                        updateDerivedTripState(
                            tripId = tripId,
                            totalDistance = totalDistance,
                            distanceDelta = distanceDelta.toDouble(),
                            totalDuration = totalDuration,
                            durationDelta = durationDelta,
                            newMeasurements = new,
                            derivedTripState = derivedTripState
                        )
                    )

                    if (autoCircumference == null) calculateWheelCircumference(
                        derivedTripState.toTypedArray(),
                        sampleSize,
                        varianceThreshold
                    )?.let { circumference ->
                        autoCircumference = circumference
                        autoCircumference?.let { newCircumference ->
                            updateAutoCircumference(tripId, newCircumference)
                        }
                    }

                    if (FeatureFlags.devBuild) derivedTripState.filter { it.circumference.isFinite() }
                        .takeLast(sampleSize).takeIf { it.isNotEmpty() }?.map { it.circumference }
                        ?.let {
                            EventBus.getDefault()
                                .post(
                                    WheelCircumferenceEvent(
                                        circumference = it.average().toFloat(),
                                        variance = it.sampleVariance()
                                    )
                                )
                        }

                    try {
                        tripsRepository.updateTripStats(
                            TripStats(
                                id = tripId,
                                distance = totalDistance,
                                duration = totalDuration,
                                averageSpeed = (totalDistance / totalDuration).toFloat()
                            )
                        )

                    } catch (e: SQLiteConstraintException) {
                        Log.e(logTag, "Failed to update trip stats", e)
                        handleSqliteConstraintException(e)
                    }
                    try {
                        if (lastSplit.totalDistance == secondLast.totalDistance) {
                            Log.v(logTag, "new split equals old split")
                        }

                        splitRepository.updateSplit(
                            doSplitStuff(
                                lastSplit,
                                durationDelta,
                                distanceDelta,
                                secondLast.totalDistance,
                                getUserDistance(applicationContext, 1.0)
                            )
                        )
                    } catch (e: SQLiteConstraintException) {
                        Log.e(logTag, "Failed to update splits", e)
                        handleSqliteConstraintException(e)
                    }

                    // Slope is its own channel (SlopeEvent). The estimator owns all routing: it
                    // calibrates the gravity offset (baro if present, else GPS altitude) and keeps
                    // the GPS-altitude fallback. Post here only when gravity isn't driving the
                    // display (sensors off, or gravity still warming up) — otherwise the sensor-rate
                    // onGravityGrade subscriber handles it.
                    gradeState = GradeEstimator.onLocation(
                        gradeState,
                        lastPressure,
                        new.altitude,
                        totalDistance,
                        speedMps = new.speed.toDouble(),
                        timeMs = new.time,
                    )
                    gradeState.committedOffset?.let { persistSlopeOffset(tripId, it) }
                    if (!gradeState.gravityWarm) {
                        EventBus.getDefault().post(SlopeEvent(GradeEstimator.slope(gradeState)))
                    }

                    TripProgress(
                        measurements = new,
                        speed = newSpeed,
                        maxSpeed = max(
                            if (newSpeed.isFinite()) newSpeed else 0f,
                            tripProgress?.maxSpeed ?: 0f
                        ),
                        distance = totalDistance,
                        duration = totalDuration,
                        accuracy = new.accuracy,
                        bearing = new.bearing,
                        tracking = true
                    )
                } else {
                    (tripProgress?.copy(
                        duration = totalDuration,
                        accuracy = new.accuracy,
                        tracking = false
                    )
                        ?: TripProgress(
                            duration = totalDuration,
                            speed = 0f,
                            maxSpeed = 0f,
                            distance = 0.0,
                            measurements = null,
                            accuracy = new.accuracy,
                            bearing = new.bearing,
                            tracking = false
                        ))
                }
            }.let {
                EventBus.getDefault().post(TripProgressEvent(it))
                tripProgress = it
            }
    }

    private suspend fun updateAutoCircumference(tripId: Long, circumference: Float) {
        sharedPreferences.edit {
            this.putFloat("auto_circumference", circumference)
        }
        try {
            tripsRepository.updateWheelCircumference(
                TripWheelCircumference(
                    id = tripId,
                    userWheelCircumference = userCircumference,
                    autoWheelCircumference = circumference
                )
            )
        } catch (e: SQLiteConstraintException) {
            Log.e(logTag, "Failed to update wheel circumference", e)
            handleSqliteConstraintException(e)
        }
        EventBus.getDefault().post(WheelCircumferenceEvent(circumference))
    }

    private fun setTripPaused(new: Measurements) {
        if (!FeatureFlags.devBuild) derivedTripState.clear()

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
                    tracking = accurateEnough
                )
            }

            else ->
                tripProgress?.copy(
                    accuracy = new.accuracy,
                    measurements = null,
                    tracking = accurateEnough
                )
        } ?: TripProgress(
            duration = 0.0,
            speed = newSpeed,
            maxSpeed = 0f,
            distance = 0.0,
            measurements = null,
            accuracy = new.accuracy,
            bearing = new.bearing,
            tracking = accurateEnough
        ).let {
            EventBus.getDefault().post(TripProgressEvent(it))
            tripProgress = it
        }
    }

    private lateinit var thisGpsObserver: Observer<Location>

    private val sensorChannel = Channel<OnboardSensorMeasurement>(
        capacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Single service-lifetime consumer started in onCreate; batches drained writes
    // to ease SQLite write-lock pressure. onOnboardSensorEvent is just the producer.
    private fun consumeSensorBatches() = lifecycleScope.launch {
        for (first in sensorChannel) {
            val batch = ArrayList<OnboardSensorMeasurement>().apply { add(first) }
            while (true) {
                val next = sensorChannel.tryReceive().getOrNull() ?: break
                batch.add(next)
            }
            try {
                if (running) sensorsRepository.insertSensorMeasurements(batch)
            } catch (e: SQLiteConstraintException) {
                Log.e(logTag, "Failed to add sensor measurements", e)
                handleSqliteConstraintException(e)
            }
        }
    }

    val onboardSensorEventHandler: (Long) -> (OnboardSensorEvent) -> Unit = { tripId ->
        { event ->
            if (running) sensorChannel.trySend(event.toMeasurement(tripId))
        }
    }
    lateinit var thisOnboardSensorEventHandler: (OnboardSensorEvent) -> Unit
    lateinit var thisBatteryEventHandler: (BatteryEvent) -> Unit

    @Subscribe
    fun onPhysicsSensorEvent(event: PhysicsSensorEvent) {
        thisOnboardSensorEventHandler(event)
    }

    @Subscribe
    fun onBatteryEvent(event: BatteryEvent) {
        thisBatteryEventHandler(event)
    }

    // Fresh per trip (initGradeEstimator), seeded from the last locked calibration so grade is
    // correct from the first sample. A new trip resets totalDistance to 0, so the estimator's
    // distance-window state must not carry over from a prior trip — hence the per-trip reset.
    private lateinit var gradeState: GradeState
    private var savedGradeOffset: Double? = null

    private fun initGradeEstimator() {
        // Seed from this bike's last locked offset so grade is correct from the first sample.
        // The bike record may not be loaded yet (fresh process; start() loads it on IO), so seed
        // NaN now and fill the offset in when the record arrives — before the fix the whole trip
        // ran un-offset and the display sat at the raw mount tilt until a lock.
        gradeState = GradeEstimator.seed((bike?.autoSlopeOffset ?: Float.NaN).toDouble())
        savedGradeOffset = null
        if (bike == null) lifecycleScope.launch(Dispatchers.IO) {
            val seed = (bikesRepository.getDefaultBike()?.also { bike = bike ?: it }
                ?.autoSlopeOffset ?: Float.NaN).toDouble()
            withContext(Dispatchers.Main) {
                if (!gradeState.locked && gradeState.offset.isNaN() && !seed.isNaN())
                    gradeState = gradeState.copy(offset = seed)
            }
        }
    }

    // Grade rides on the raw accelerometer, not TYPE_GRAVITY: software gravity fusions drift
    // several % under riding dynamics on some devices (Pixel 11 Pro: fine at rest, median +6.7%
    // / p90 +19% tilt error while moving vs the accelerometer; ride 859 analysis 2026-08-31).
    // A per-component EMA averages vibration out of the raw signal; ratio-of-means, never
    // mean-of-ratios — z crosses spikes make per-sample y/z explode.
    private var accelEma: FloatArray? = null
    private var lastAccelTimeMs = 0L

    // MAIN thread mode: gradeState is also mutated by the GPS handler on the main
    // thread, so grade updates stay single-threaded
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAccelerometerGrade(event: PhysicsSensorEvent.Accelerometer) {
        val ema = accelEma?.also {
            val dt = if (lastAccelTimeMs in 1 until event.timestamp)
                (event.timestamp - lastAccelTimeMs) / 1000.0 else 0.02
            val alpha = (dt / (GRADE_ACCEL_TAU_SECONDS + dt)).toFloat()
            it[0] += alpha * (event.x - it[0])
            it[1] += alpha * (event.y - it[1])
            it[2] += alpha * (event.z - it[2])
        } ?: floatArrayOf(event.x, event.y, event.z).also { accelEma = it }
        lastAccelTimeMs = event.timestamp
        gradeState = GradeEstimator.onGravity(gradeState, ema, timestampMs = event.timestamp)
        val s = GradeEstimator.slope(gradeState)
        if (FeatureFlags.devBuild) Log.v(logTag, "grade aY=${ema[1]} aZ=${ema[2]} slope=$s")
        EventBus.getDefault().post(SlopeEvent(s))
    }

    private var lastPressure: Float? = null

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPressureGrade(event: PhysicsSensorEvent.Pressure) {
        lastPressure = event.pressure
    }

    // On lock, record the offset per-trip (audit/reproducibility) and per-bike (seeds this bike's next trip).
    private suspend fun persistSlopeOffset(tripId: Long, value: Double) {
        if (value == savedGradeOffset) return
        savedGradeOffset = value
        val offset = value.toFloat()
        tripsRepository.updateSlopeOffset(TripSlopeOffset(id = tripId, slopeOffset = offset))
        bike?.let {
            val updated = it.copy(autoSlopeOffset = offset)
            bikesRepository.update(updated)
            bike = updated
        }
        Log.d(logTag, "grade offset locked; per-trip + per-bike offset=$value")
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val channel = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_LOW
        )
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // retired IMPORTANCE_HIGH channel; importance is immutable once created
        service.deleteNotificationChannel("notification_id_trip_in_progress")
        service.createNotificationChannel(channel)
        return channelId
    }

    private var notifiedTripId: Long? = null

    private fun tripNotification(tripId: Long, contentText: CharSequence): Notification {
        val channelId = createNotificationChannel(
            getString(R.string.notification_id_trip_in_progress),
            getString(R.string.notification_channel_name_trip_in_progress)
        )

        val pendingIntent = try {
            NavDeepLinkBuilder(this).apply {
                setComponentName(MainActivity::class.java)
                setGraph(R.navigation.cyclotrack_nav_graph)
                setDestination(R.id.DashboardActivity)
                setArguments(Bundle().apply { putLong("tripId", tripId) })
            }.createPendingIntent()
        } catch (e: NullPointerException) {
            // ponytail: NavDeepLinkBuilder(Context) NPEs on some devices where
            // getLaunchIntentForPackage resolves a null className. Fall back to a
            // plain launch — notification still opens the app, just not the deep link.
            FirebaseCrashlytics.getInstance().recordException(e)
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java)
                    .putExtra("tripId", tripId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, channelId).apply {
            setOngoing(true)
            setShowWhen(false)
            setContentTitle(getText(R.string.notification_trip_in_progress_title))
            setContentText(contentText)
            setSmallIcon(R.drawable.ic_cyclotrack_notification)
            setContentIntent(pendingIntent)
        }.build().also { it.flags = it.flags or Notification.FLAG_ONGOING_EVENT }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onThermalTierEvent(event: ThermalTierEvent) {
        val tripId = notifiedTripId ?: return
        if (!running) return
        FirebaseAnalytics.getInstance(applicationContext).logEvent("ThermalTierChanged") {
            param("tier", event.tier.name)
            param("batteryTempC", event.tempC.toDouble())
        }
        val text = when (event.tier) {
            ThermalTier.NORMAL -> getText(R.string.notification_trip_in_progress_message)
            ThermalTier.WARM, ThermalTier.HOT ->
                getString(R.string.notification_display_protection_active, event.tempC)

            ThermalTier.EXTREME ->
                getString(R.string.notification_display_protection_sleep, event.tempC)
        }
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(tripId.toInt(), tripNotification(tripId, text))
        } catch (e: SecurityException) {
            Log.w(logTag, "Unable to update trip notification", e)
        }
    }

    private fun startForegroundCompat(tripId: Long) {
        notifiedTripId = tripId
        val notification =
            tripNotification(tripId, getText(R.string.notification_trip_in_progress_message))

        val serviceTypes = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                FOREGROUND_SERVICE_TYPE_LOCATION or
                        when (PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) -> FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

                            else -> 0
                        }
            }

            else -> {
                0
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    tripId.toInt(),
                    notification,
                    serviceTypes
                )
            } else {
                startForeground(
                    tripId.toInt(),
                    notification
                )
            }
            running = true
        } catch (e: SecurityException) {
            // FGS-location start rejected: app not in an eligible state (background
            // start) or location permission revoked; stop cleanly so the next
            // foreground entry can retry instead of crashing
            Log.e(logTag, "Not allowed to start foreground service", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            stopSelf()
        } catch (e: IllegalStateException) {
            Log.e(logTag, "Not allowed to start foreground service", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            stopSelf()
        }
    }

    private suspend fun getCombinedBiometrics(id: Long): Biometrics =
        com.kvl.cyclotrack.util.getCombinedBiometrics(
            id,
            SystemUtils.currentTimeMillis(),
            applicationContext,
            lifecycleScope,
            null,
            googleFitApiService
        )

    private suspend fun start() {
        Log.d(logTag, "Start trip")
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")
        var tripId: Long = -1

        clearState()

        lifecycleScope.launch(Dispatchers.IO) {
            if (bike == null) {
                bike = bikesRepository.getDefaultBike()
            }
            tripId =
                tripsRepository.createNewTrip(bike?.id ?: 1).also { id ->
                    timeStateRepository.appendTimeState(TimeState(id, TimeStateEnum.START))
                    EventBus.getDefault().post(StartTripEvent(id))
                    Log.d(logTag, "created new trip with id $id")
                }
        }.join()

        Log.d(logTag, "Start trip service for ID ${tripId}; this=$this")
        startObserving(tripId)

        startForegroundCompat(tripId)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                tripsRepository.updateWheelCircumference(
                    TripWheelCircumference(
                        id = tripId,
                        userWheelCircumference = userCircumference,
                        autoWheelCircumference = autoCircumference
                    )
                )
                tripsRepository.updateBiometrics(
                    getCombinedBiometrics(tripId)
                )
            } catch (e: SQLiteConstraintException) {
                Log.e(logTag, "Failed to add sensor measurements", e)
                handleSqliteConstraintException(e)
            }
        }
    }

    private fun restart(tripId: Long) {

        Log.d(logTag, "Restart trip")
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")

        bleService.restore()
        if (!::gradeState.isInitialized) initGradeEstimator()  // recover after process death

        Log.d(logTag, "Restart trip service for ID ${tripId}; this=$this")

        lifecycleScope.launch(Dispatchers.IO) {
            tripsRepository.getOrNull(tripId)?.let {
                EventBus.getDefault().post(
                    TripProgressEvent(
                        TripProgress(
                            measurements = null,
                            accuracy = 0f,
                            bearing = 0f,
                            speed = 0f,
                            maxSpeed = 0f,
                            distance = it.distance ?: 0.0,
                            duration = it.duration ?: 0.0,
                            tracking = false
                        )
                    )
                )
            }
        }

        if (running) return

        startObserving(tripId)
        startForegroundCompat(tripId)
    }

    private fun startObserving(tripId: Long) {
        if (!::thisGpsObserver.isInitialized || !gpsService.hasObservers()) {
            thisGpsObserver = gpsObserver(tripId)
            gpsService.observe(this, thisGpsObserver)
        }

        if (!::thisHrmEventHandler.isInitialized) {
            thisHrmEventHandler = hrmEventHandler(tripId)
        }

        if (!::thisCadenceEventHandler.isInitialized) {
            thisCadenceEventHandler = cadenceEventHandler(tripId)
        }

        if (!::thisSpeedEventHandler.isInitialized) {
            thisSpeedEventHandler = speedEventHandler(tripId)
        }

        if (!::thisPowerEventHandler.isInitialized) {
            thisPowerEventHandler = powerEventHandler(tripId)
        }

        thisBatteryEventHandler = onboardSensorEventHandler(tripId)
        thermalMonitor.start()
        batterySource.start()

        // Gravity grade rides on the onboard sensors, which are only powered when logging is
        // enabled (TYPE_GRAVITY fuses the gyro, so it can't run cheaply on its own). When off,
        // fall back to GPS-altitude slope in the GPS handler — no extra sensors powered.
        if (shouldCollectOnboardSensors(applicationContext)) {
            thisOnboardSensorEventHandler = onboardSensorEventHandler(tripId)
            onboardSensorSource.start()
        }
    }

    private fun pause(tripId: Long) {
        Log.d(logTag, "Called pause()")
        lifecycle.coroutineScope.launch {
            try {
                timeStateRepository.appendTimeState(
                    TimeState(
                        tripId = tripId,
                        state = TimeStateEnum.PAUSE
                    )
                )
            } catch (e: SQLiteConstraintException) {
                Log.e(logTag, "Failed to add pause time state", e)
                handleSqliteConstraintException(e)
            }
        }
    }

    private fun resume(tripId: Long) {
        Log.d(logTag, "Called resume()")
        bleService.restore()
        lifecycle.coroutineScope.launch {
            try {
                timeStateRepository.appendTimeState(
                    TimeState(
                        tripId = tripId,
                        state = TimeStateEnum.RESUME
                    )
                )
            } catch (e: SQLiteConstraintException) {
                Log.e(logTag, "Failed to add resume time state", e)
                handleSqliteConstraintException(e)
            }
        }
        startObserving(tripId)
    }

    private fun clearState() {
        autoCircumference = null
        tripProgress = null
        initGradeEstimator()   // fresh per trip: a new trip resets totalDistance to 0
        if (!FeatureFlags.devBuild) derivedTripState.clear()
    }

    private suspend fun end(tripId: Long) {
        Log.d(logTag, "Called end() with $tripId")
        var job: Job? = null

        tripId.takeIf { it >= 0 }?.let { id ->
            job = lifecycle.coroutineScope.launch {
                try {
                    timeStateRepository.appendTimeState(
                        TimeState(
                            tripId = id,
                            state = TimeStateEnum.STOP
                        )
                    )
                } catch (e: SQLiteConstraintException) {
                    Log.e(logTag, "Failed to add stop time state", e)
                    handleSqliteConstraintException(e)
                }
                try {
                    tripsRepository.endTrip(id)
                } catch (e: SQLiteConstraintException) {
                    Log.e(logTag, "Failed to end trip", e)
                    handleSqliteConstraintException(e)
                }
            }
        }

        if (::thisGpsObserver.isInitialized) gpsService.removeObserver(thisGpsObserver)
        onboardSensorSource.stop()
        batterySource.stop()
        thermalMonitor.stop()

        running = false
        clearState()
        job?.join()
    }

    private fun shutdown() {
        Log.d(logTag, "called shutdown()")
        if (!running) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                getString(R.string.action_initialize_trip_service) -> Log.d(
                    logTag,
                    "Initialize trip service"
                )

                getString(R.string.action_start_trip_service) -> {
                    when (val tripId = it.getLongExtra("tripId", -1)) {
                        -1L -> lifecycleScope.launch { start() }
                        in removingTrips -> Log.w(
                            logTag,
                            "Ignoring resume for trip $tripId being removed"
                        )

                        else -> restart(tripId)
                    }
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            delay(5L * 60 * 1000)
                        }
                        bleService.stopAllScans()
                    }
                }

                getString(R.string.action_pause_trip_service) ->
                    pause(it.getLongExtra("tripId", -1))

                getString(R.string.action_resume_trip_service) ->
                    resume(it.getLongExtra("tripId", -1))

                getString(R.string.action_stop_trip_service) -> lifecycleScope.launch {
                    end(it.getLongExtra("tripId", -1))
                    shutdown()
                }

                getString(R.string.action_remove_trip_service) -> {
                    it.getLongExtra("tripId", -1).takeIf { tripId -> tripId >= 0 }?.let { tripId ->
                        removingTrips.add(tripId)
                        lifecycleScope.launch {
                            end(tripId)
                            tripsRepository.removeTrip(tripId)
                            shutdown()
                        }
                    }
                }

                getString(R.string.action_shutdown_trip_service) -> shutdown()
                else -> Log.d(logTag, "Received intent $intent")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        consumeSensorBatches()
        Log.d(logTag, "::onCreate; this=$this")
        Log.d(logTag, "gpsService=$gpsService")
        Log.d(logTag, "bleService=$bleService")
        Log.d(logTag, "measurementsRepository=$measurementsRepository")
        Log.d(logTag, "tripsRepository=$tripsRepository")
        gpsService.startListening()
        bleService.initialize()
        clearState()
        EventBus.getDefault().register(this)
        initializeFromSharedPrefs()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun getAutoPauseRpmThreshold(key: String?) =
        sharedPreferences.getInt(
            key, 0
        ).toFloat().let { thresholdSpeed ->
            getSystemSpeed(
                applicationContext,
                thresholdSpeed
            )
        }.let { systemSpeed ->
            systemSpeed * 60f / (userCircumference ?: 2f)
        }

    private fun initializeFromSharedPrefs() {
        autopauseEnabled =
            sharedPreferences.getBoolean(
                getString(R.string.preference_key_autopause_enable),
                false
            ) == true
        blockAutoResumeEnabled =
            sharedPreferences.getBoolean(
                getString(R.string.preference_key_autopause_manual_override),
                true
            ) == true
        autopausePauseThreshold =
            sharedPreferences.getInt(
                getString(R.string.preference_key_autopause_pause_threshold),
                5
            ) * 1000.toLong()
        autopauseResumeThreshold =
            sharedPreferences.getInt(
                getString(R.string.preference_key_autopause_resume_threshold),
                5
            ) * 1000.toLong()
        autopauseRpmThreshold =
            getAutoPauseRpmThreshold(getString(R.string.preference_key_autopause_speed_threshold))
        Log.d(
            logTag,
            "SharedPrefsInit: $autopauseEnabled,$autopausePauseThreshold,$autopauseResumeThreshold,$autopauseRpmThreshold"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false

        if (::thisGpsObserver.isInitialized) gpsService.removeObserver(thisGpsObserver)
        onboardSensorSource.stop()
        batterySource.stop()
        thermalMonitor.stop()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        EventBus.getDefault().unregister(this)
        bleService.disconnect()
        gpsService.stopListening()
        Log.d(logTag, "onDestroy")
    }

    override fun onSharedPreferenceChanged(sharedPrefs: SharedPreferences?, key: String?) {
        sharedPrefs?.let { prefs ->
            when (key) {
                getString(R.string.preference_key_autopause_enable) -> autopauseEnabled =
                    prefs.getBoolean(key, false) == true

                getString(R.string.preference_key_autopause_manual_override) -> blockAutoResumeEnabled =
                    prefs.getBoolean(key, true) == true

                getString(R.string.preference_key_autopause_pause_threshold) -> autopausePauseThreshold =
                    prefs.getInt(key, 5) * 1000.toLong()

                getString(R.string.preference_key_autopause_resume_threshold) -> autopauseResumeThreshold =
                    prefs.getInt(key, 5) * 1000.toLong()

                getString(R.string.preference_key_autopause_speed_threshold) -> autopauseRpmThreshold =
                    getAutoPauseRpmThreshold(key)
            }
        }
        Log.d(
            logTag,
            "SharedPrefsChanged: $autopauseEnabled,$autopausePauseThreshold,$autopauseResumeThreshold,$autopauseRpmThreshold"
        )
    }
}
