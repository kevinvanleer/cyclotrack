package com.kvl.cyclotrack

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.CadenceSpeedMeasurementRepository
import com.kvl.cyclotrack.data.DerivedTripState
import com.kvl.cyclotrack.data.HeartRateMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurementRepository
import com.kvl.cyclotrack.data.SensorType
import com.kvl.cyclotrack.events.ConnectedBikeEvent
import com.kvl.cyclotrack.events.StartTripEvent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
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
    private var running = false
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
        get() = userCircumferenceToMeters(bike?.wheelCircumference)

    private var autoCircumference: Float? = null

    private var tripProgress: TripProgress? = null

    private val derivedTripState = ArrayList<DerivedTripState>()

    private var hrmBpm: Short? = null
    private var speed: SpeedData? = null
    private var cadence: CadenceData? = null

    val hrmEventHandler: (Long) -> (HrmData) -> Unit = { tripId ->
        { event ->
            event.bpm?.let {
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
            event.revolutionCount?.let {
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
            event.revolutionCount?.let {
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
                "https://api.openweathermap.org/data/2.5/onecall?lat=$lat&lon=$lng&exclude=minutely,hourly,daily&appid=$devAppId"
            OkHttpClient().let { client ->
                Request.Builder()
                    .url(weatherUrl)
                    .build()
                    .let { request ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                client.newCall(request).execute().let { response ->
                                    if (response.isSuccessful) {
                                        try {
                                            val weatherResponse = response.body?.source()
                                                ?.let { jsonAdapter.nullSafe().fromJson(it) }
                                            weatherResponse?.current?.let {
                                                weatherRepository.recordWeather(
                                                    it,
                                                    tripId
                                                )
                                            }
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
        onDestroy()
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

                    val newSlope = calculateSlope(derivedTripState.takeLast(20))

                    TripProgress(
                        measurements = new,
                        speed = newSpeed,
                        maxSpeed = max(
                            if (newSpeed.isFinite()) newSpeed else 0f,
                            tripProgress?.maxSpeed ?: 0f
                        ),
                        distance = totalDistance,
                        slope = newSlope,
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
                            slope = 0.0,
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
            slope = 0.0,
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

    private fun sensorObserver(tripId: Long): Observer<SensorModel> = Observer { newData ->
        lifecycleScope.launch {
            try {
                sensorsRepository.insertMeasurements(tripId, newData)
            } catch (e: SQLiteConstraintException) {
                Log.e(logTag, "Failed to add sensor measurements", e)
                handleSqliteConstraintException(e)
            }
        }
    }

    private lateinit var thisSensorObserver: Observer<SensorModel>

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        //TODO: MAKE THIS YOUR OWN -- COPIED FROM POST
        val channel = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    private fun startForegroundCompat(tripId: Long) {
        val channelId = createNotificationChannel(
            getString(R.string.notification_id_trip_in_progress),
            getString(R.string.notification_channel_name_trip_in_progress)
        )

        val pendingIntent = NavDeepLinkBuilder(this).apply {
            setGraph(R.navigation.cyclotrack_nav_graph)
            setDestination(R.id.DashboardActivity)
            setArguments(Bundle().apply { putLong("tripId", tripId) })
        }.createPendingIntent()

        val notification = NotificationCompat.Builder(this, channelId).apply {
            priority = NotificationCompat.PRIORITY_MAX
            setOngoing(true)
            setShowWhen(false)
            setContentTitle(getText(R.string.notification_trip_in_progress_title))
            setContentText(getText(R.string.notification_export_trip_in_progress_message))
            setSmallIcon(R.drawable.ic_cyclotrack_notification)
            setContentIntent(pendingIntent)
        }.build().also { it.flags = it.flags or Notification.FLAG_ONGOING_EVENT }

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

        Log.d(logTag, "Restart trip service for ID ${tripId}; this=$this")

        lifecycleScope.launch(Dispatchers.IO) {
            tripsRepository.get(tripId).let {
                EventBus.getDefault().post(
                    TripProgressEvent(
                        TripProgress(
                            measurements = null,
                            accuracy = 0f,
                            bearing = 0f,
                            speed = 0f,
                            maxSpeed = 0f,
                            distance = it.distance ?: 0.0,
                            slope = calculateSlope(derivedTripState.takeLast(20)),
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

        if (shouldCollectOnboardSensors(applicationContext)) {
            if (!::thisSensorObserver.isInitialized || !onboardSensors.hasObservers()) {
                thisSensorObserver = sensorObserver(tripId)
                onboardSensors.observe(this, thisSensorObserver)
            }
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
        if (::thisSensorObserver.isInitialized) onboardSensors.removeObserver(thisSensorObserver)

        running = false
        clearState()
        job?.join()
        stopSelf()
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
                }

                getString(R.string.action_shutdown_trip_service) -> shutdown()
                else -> Log.d(logTag, "Received intent $intent")
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

        if (::thisGpsObserver.isInitialized) gpsService.removeObserver(thisGpsObserver)
        if (::thisSensorObserver.isInitialized) onboardSensors.removeObserver(thisSensorObserver)

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
