package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.timerTask
import kotlin.math.abs
import kotlin.math.max

@HiltViewModel
class TripInProgressViewModel @Inject constructor(
    coroutineScopeProvider: CoroutineScope?,
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
    private val sensorsRepository: OnboardSensorsRepository,
    private val gpsService: GpsService,
    private val bleService: BleService,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {

    private val logTag = "TIP_VIEW_MODEL"
    private val coroutineScope = getViewModelScope(coroutineScopeProvider)

    var currentState: TimeStateEnum = TimeStateEnum.STOP
    private var userCircumference: Float? = getUserCircumferenceOrNull(sharedPreferences)
    private var _autoCircumference: Float? = null

    val autoCircumference: Float?
        get() = _autoCircumference
    val circumference: Float?
        get() = _autoCircumference ?: userCircumference

    private var accumulatedDuration = 0.0
    var tripId: Long? = null
    private var startTime = Double.NaN
    private var lastSplit = Split(0, 0.0, 0.0, 0.0, 0.0)
    private var measuringCircumference = false
    private var initialMeasureCircRevs = 0
    private var initialMeasureCircDistance = 0.0

    private val clockTick = Timer()
    private val accuracyThreshold = 7.5f
    private val defaultSpeedThreshold = 0.5f
    private val _currentProgress = MutableLiveData<TripProgress>()
    private val _currentTime = MutableLiveData<Double>()
    private val currentTimeStateObserver: Observer<TimeState> = Observer {
        Log.d(logTag, "onChanged current time state observer: ${it.state}")
        if (it != null) currentState = it.state
    }

    private fun tripInProgress() = isTripInProgress(currentState)
    var gpsEnabled = gpsService.accessGranted
    var hrmSensor = bleService.hrmSensor
    var cadenceSensor = bleService.cadenceSensor
    var speedSensor = bleService.speedSensor

    private val newMeasurementsObserver: Observer<Measurements> = Observer { newMeasurements ->
        Log.d(logTag, "onChanged measurements observer")
        if (newMeasurements == null) {
            Log.d(logTag, "measurements observation is null")
        } else {
            if (currentState == TimeStateEnum.RESUME || currentState == TimeStateEnum.START) {
                setTripProgress(newMeasurements)
            } else {
                setTripPaused(newMeasurements)
            }
        }
    }

    private val sensorObserver: Observer<SensorModel> = Observer { newData ->
        coroutineScope.launch {
            sensorsRepository.insertMeasurements(tripId!!, newData)
        }
    }

    private fun accumulateDuration(timeStates: Array<TimeState>?) {
        var durationAcc = 0L
        var localStartTime = 0L

        timeStates?.forEach { timeState ->
            when (timeState.state) {
                TimeStateEnum.START -> {
                    durationAcc = 0L
                    localStartTime = timeState.timestamp
                }
                TimeStateEnum.PAUSE -> {
                    durationAcc += timeState.timestamp - localStartTime
                }
                TimeStateEnum.RESUME -> {
                    localStartTime = timeState.timestamp
                }
                TimeStateEnum.STOP -> {
                    durationAcc += timeState.timestamp - localStartTime
                    localStartTime = 0L
                }
            }
        }
        accumulatedDuration = durationAcc / 1e3
        startTime = localStartTime / 1e3
        Log.v(logTag,
            "accumulatedDuration = ${accumulatedDuration}; startTime = ${startTime}")
    }

    private val accumulateDurationObserver: Observer<Array<TimeState>> =
        Observer { accumulateDuration(it) }

    private val lastSplitObserver: Observer<Split> = Observer { newSplit ->
        if (newSplit != null) {
            lastSplit = newSplit
        }
    }

    val currentProgress: LiveData<TripProgress>
        get() = _currentProgress

    val currentTime: LiveData<Double>
        get() = _currentTime

    fun currentTimeState(tripId: Long) = timeStateRepository.observeLatest(tripId)

    fun startGps() = gpsService.startListening()
    fun startBle() = bleService.initialize()
    fun stopBle() = bleService.disconnect()

    private fun setTripProgress(new: Measurements) {
        val old = _currentProgress.value
        val oldDistance: Double = old?.distance ?: 0.0
        var newDistance: Double = oldDistance
        var accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold
        var speedThreshold = defaultSpeedThreshold

        val newDuration = getDuration()
        val durationDelta = getDurationDelta(newDuration, old)

        old?.measurements?.speedRevolutions?.let { revs ->
            calculateWheelCircumference(old.measurements, newDistance,
                revs)
        }
        if (accurateEnough) {
            val distanceDelta = getDistanceDelta(old, new)

            val newSpeed = getSpeed(new, speedThreshold)

            if (new.speed > speedThreshold) newDistance += distanceDelta

            if (crossedSplitThreshold(sharedPreferences,
                    newDistance,
                    old?.distance ?: Double.MAX_VALUE)
            ) {
                coroutineScope.launch {
                    splitRepository.addSplit(Split(timestamp = System.currentTimeMillis(),
                        duration = newDuration - lastSplit.totalDuration,
                        distance = newDistance - lastSplit.totalDistance,
                        totalDuration = newDuration,
                        totalDistance = newDistance,
                        tripId = tripId!!))
                }
            }

            var newSlope = calculateSlope(
                newSpeed,
                distanceDelta,
                new,
                speedThreshold,
                old,
                durationDelta)
            val newAcceleration = getAcceleration(durationDelta, newSpeed, old)

            Log.v("TIP_SLOPE", newSlope.toString())
            Log.d("TIP_DISTANCE_DELTA", distanceDelta.toString())
            Log.d("TIP_DURATION_DELTA", durationDelta.toString())
            Log.v("TIP_UPDATE",
                "accuracy: ${new.accuracy}; speed: ${newSpeed}; acceleration: ${newAcceleration}; distance: $newDistance; slope: $newSlope; duration: $newDuration")
            Log.d("TIP_MAX_ACCELERATION",
                max(newAcceleration, old?.maxAcceleration ?: 0f).toString())

            coroutineScope.launch {
                if (tripId != null) tripsRepository.updateTripStats(TripStats(tripId!!,
                    newDistance,
                    newDuration,
                    (newDistance / newDuration).toFloat(),
                    userCircumference,
                    _autoCircumference))
            }

            _currentProgress.value =
                TripProgress(measurements = new,
                    speed = newSpeed,
                    maxSpeed = max(if (newSpeed.isFinite()) newSpeed else 0f,
                        old?.maxSpeed ?: 0f),
                    distance = newDistance,
                    acceleration = newAcceleration,
                    maxAcceleration = max(if (newAcceleration.isFinite()) newAcceleration else 0f,
                        old?.maxAcceleration ?: 0f),
                    slope = newSlope,
                    duration = newDuration,
                    accuracy = new.accuracy,
                    bearing = new.bearing,
                    splitSpeed = (lastSplit.distance / lastSplit.duration.coerceAtLeast(0.0001)).toFloat(),
                    tracking = true)

        } else {
            _currentProgress.value =
                _currentProgress.value?.copy(duration = newDuration,
                    accuracy = new.accuracy,
                    tracking = false)
                    ?: TripProgress(duration = newDuration,
                        speed = 0f,
                        maxSpeed = 0f,
                        acceleration = 0f,
                        maxAcceleration = 0f,
                        distance = 0.0,
                        slope = 0.0,
                        splitSpeed = 0f,
                        measurements = null,
                        accuracy = new.accuracy,
                        bearing = new.bearing,
                        tracking = false)
        }
    }

    fun calculateWheelCircumference(
        progress: Measurements,
        totalDistance: Double,
        speedRevolutions: Int,
    ) {
        val autoCircumferenceAccThreshold = 5.0
        val autoCircumferenceSpeedThreshold = 4.0
        if (progress.accuracy < autoCircumferenceAccThreshold &&
            progress.speed >= autoCircumferenceSpeedThreshold && !measuringCircumference &&
            progress.speedRevolutions != null && _autoCircumference == null
        ) {
            measuringCircumference = true
            initialMeasureCircDistance = totalDistance
            initialMeasureCircRevs = speedRevolutions
        }
        if (measuringCircumference && progress.accuracy > autoCircumferenceAccThreshold ||
            progress.speed < autoCircumferenceSpeedThreshold || _autoCircumference != null
        ) {
            measuringCircumference = false
        }
        if (measuringCircumference && (totalDistance - initialMeasureCircDistance) > 400
        ) {
            val revs = progress.speedRevolutions?.minus(initialMeasureCircRevs)
            val dist = totalDistance - initialMeasureCircDistance
            if (revs != null) {
                measuringCircumference = false
                _autoCircumference = (dist / revs).toFloat()
                sharedPreferences.edit {
                    this.putFloat("auto_circumference", _autoCircumference!!.toFloat())
                }
            }
        }
    }

    private fun setTripPaused(new: Measurements) {
        val accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold
        val speedThreshold = defaultSpeedThreshold
        if (!startTime.isFinite()) startTime = new.elapsedRealtimeNanos / 1e9

        if (accurateEnough) {
            val newSpeed = getSpeed(new, speedThreshold)

            _currentProgress.value =
                _currentProgress.value?.copy(
                    speed = newSpeed,
                    measurements = null,
                    accuracy = new.accuracy,
                    tracking = true)
                    ?: TripProgress(duration = 0.0,
                        speed = newSpeed,
                        maxSpeed = 0f,
                        acceleration = 0f,
                        maxAcceleration = 0f,
                        distance = 0.0,
                        slope = 0.0,
                        splitSpeed = 0f,
                        measurements = null,
                        accuracy = new.accuracy,
                        bearing = new.bearing,
                        tracking = true)

        } else {
            _currentProgress.value =
                _currentProgress.value?.copy(
                    accuracy = new.accuracy,
                    measurements = null,
                    tracking = false)
                    ?: TripProgress(duration = 0.0,
                        speed = 0f,
                        maxSpeed = 0f,
                        acceleration = 0f,
                        maxAcceleration = 0f,
                        distance = 0.0,
                        slope = 0.0,
                        splitSpeed = 0f,
                        measurements = null,
                        accuracy = new.accuracy,
                        bearing = new.bearing,
                        tracking = false)
        }
    }

    private fun getSpeed(
        new: Measurements,
        speedThreshold: Float,
    ): Float {
        return if (circumference != null && new.speedRpm != null) {
            val rps = new.speedRpm.div(60)
            circumference!! * rps
        } else if (new.speed > speedThreshold) new.speed else 0f
    }

    private fun getAcceleration(
        durationDelta: Double,
        newSpeed: Float,
        old: TripProgress?,
    ) = if (durationDelta == 0.0) 0f
    else ((newSpeed - (old?.speed ?: 0f)) / durationDelta).toFloat()

    private fun calculateSlope(
        newSpeed: Float,
        distanceDelta: Double,
        new: Measurements,
        speedThreshold: Float,
        old: TripProgress?,
        durationDelta: Double,
    ): Double {
        val oldAltitude: Double = old?.measurements?.altitude ?: 0.0
        val verticalSpeed = abs((new.altitude - oldAltitude) / durationDelta)

        val slopeAlpha = 0.5
        return if (verticalSpeed < newSpeed && distanceDelta != 0.0) {
            slopeAlpha * (
                    if (new.speed > speedThreshold) ((new.altitude - oldAltitude) / distanceDelta)
                    else (old?.slope ?: 0.0)
                    ) + ((1 - slopeAlpha) * (old?.slope ?: 0.0))
        } else 0.0
    }

    private fun getDistanceDelta(
        old: TripProgress?,
        new: Measurements,
    ): Double {
        var distanceResults = floatArrayOf(0f)
        Location.distanceBetween(old?.measurements?.latitude ?: new.latitude,
            old?.measurements?.longitude ?: new.longitude,
            new.latitude,
            new.longitude,
            distanceResults)
        return distanceResults[0].toDouble()
    }

    private fun getDurationDelta(
        newDuration: Double,
        old: TripProgress?,
    ) = newDuration - if (startTime.isFinite()) ((old?.measurements?.elapsedRealtimeNanos
        ?: 0L) / 1e9) - startTime else 0.0

    private fun getDuration() =
        if (startTime.isFinite() && tripInProgress()) accumulatedDuration + (System.currentTimeMillis() / 1e3) - startTime else accumulatedDuration

    fun startObserving(lifecycleOwner: LifecycleOwner) {
        if (tripId != null) {
            Log.d(logTag, "Start observing trip ID $tripId $currentTimeStateObserver")
            getLatest()?.observe(lifecycleOwner, newMeasurementsObserver)
            timeStateRepository.observeLatest(tripId!!)
                .observe(lifecycleOwner, currentTimeStateObserver)
            timeStateRepository.observeTimeStates(tripId!!)
                .observe(lifecycleOwner, accumulateDurationObserver)
            splitRepository.observeLastSplit(tripId!!).observe(lifecycleOwner, lastSplitObserver)
        }
    }

    suspend fun getCombinedBiometrics(id: Long): Biometrics {
        var biometrics = getBiometrics(id, sharedPreferences)
        Log.d(logTag, "biometrics prefs: ${biometrics}")

        viewModelScope.launch {
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

    fun startTrip(lifecycleOwner: LifecycleOwner): LiveData<Long> {
        val tripStarted = MutableLiveData<Long>()

        //TODO: Add speed revs to time state for distance calculations
        coroutineScope.launch(Dispatchers.Default) {
            tripId = tripsRepository.createNewTrip().also { id ->
                tripsRepository.updateBiometrics(
                    getCombinedBiometrics(id))

            }
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.START))
            Log.d(logTag, "created new trip with id ${tripId.toString()}")
            tripStarted.postValue(tripId)
        }
        tripStarted.observeForever(object : Observer<Long> {
            override fun onChanged(t: Long?) {
                startObserving(lifecycleOwner)
                tripStarted.removeObserver(this)
            }
        })

        startClock()

        return tripStarted
    }

    private fun startClock() {
        clockTick.scheduleAtFixedRate(timerTask {
            val timeHandler = Handler(Looper.getMainLooper())
            timeHandler.post {
                Log.v("TIP_TIME_TICK", getDuration().toString())
                _currentTime.value = getDuration()
            }
        }, 1000 - System.currentTimeMillis() % 1000, 500)
    }

    fun pauseTrip() {
        coroutineScope.launch(Dispatchers.Default) {
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.PAUSE))
        }
    }

    fun resumeTrip(tripId: Long, lifecycleOwner: LifecycleOwner) {
        Log.d(logTag, "Resuming trip $tripId")
        this.tripId = tripId
        coroutineScope.launch {
            val tripState = tripsRepository.get(tripId)
            val currentMeasurement = measurementsRepository.get(tripId).last()
            userCircumference = tripState.userWheelCircumference
            _autoCircumference = tripState.autoWheelCircumference

            _currentProgress.value = TripProgress(
                measurements = currentMeasurement,
                distance = tripState.distance ?: 0.0,
                duration = tripState.duration ?: 0.0,
                speed = currentMeasurement.speed,
                accuracy = currentMeasurement.accuracy,
                bearing = currentMeasurement.bearing,
                acceleration = 0f,
                maxAcceleration = 0f,
                maxSpeed = 0f,
                slope = 0.0,
                splitSpeed = 0f,
                tracking = true,
            )
            startObserving(lifecycleOwner)
        }
        startClock()
    }

    fun resumeTrip() {
        coroutineScope.launch(Dispatchers.Default) {
            Log.d(logTag, "resumeTrip")
            timeStateRepository.appendTimeState(TimeState(this@TripInProgressViewModel.tripId!!,
                TimeStateEnum.RESUME))
        }
    }

    fun getLatest(): LiveData<Measurements>? {
        if (tripId == null) throw UninitializedPropertyAccessException()
        return measurementsRepository.observeLatest(tripId!!)
    }

    private fun cleanup() {
        gpsService.stopListening()
        clockTick.cancel()
    }

    fun endTrip() {
        if (tripId != null && currentState != TimeStateEnum.STOP) {
            val old = _currentProgress.value
            val oldDistance: Double = old?.distance ?: 0.0
            val newDistance: Double = oldDistance
            val newDuration = getDuration()
            val splitDistance = newDistance - lastSplit.totalDistance
            val splitDuration = newDuration - lastSplit.totalDuration

            coroutineScope.launch(Dispatchers.Default) {
                timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.STOP))
                splitRepository.addSplit(Split(timestamp = System.currentTimeMillis(),
                    duration = splitDuration,
                    distance = splitDistance,
                    totalDuration = newDuration,
                    totalDistance = newDistance,
                    tripId = tripId!!))
            }
        }
    }

    override fun onCleared() {
        Log.d(logTag, "Called onCleared")
        super.onCleared()
        //TODO: MAYBE DON'T RUDELY END THE TRIP WHEN THE VIEW MODEL IS CLEARED
        //cleanup()
        //endTrip()
    }
}

data class TripProgress(
    val measurements: Measurements?,
    val accuracy: Float,
    val bearing: Float,
    val speed: Float,
    val splitSpeed: Float,
    val maxSpeed: Float,
    val acceleration: Float,
    val maxAcceleration: Float,
    val distance: Double,
    val slope: Double,
    val duration: Double,
    val tracking: Boolean,
)