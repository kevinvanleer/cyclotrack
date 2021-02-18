package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.math.abs
import kotlin.math.max

class TripInProgressViewModel @ViewModelInject constructor(
    coroutineScopeProvider: CoroutineScope?,
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
    private val gpsService: GpsService,
    private val bleService: BleService,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {

    private val TAG = "TIP_VIEW_MODEL"
    private val coroutineScope = getViewModelScope(coroutineScopeProvider)

    var currentState: TimeStateEnum = TimeStateEnum.STOP
    private val userCircumference: Float? = getUserCircumferenceOrNull(sharedPreferences)
    private var _autoCircumference: Float? = null

    val autoCircumference: Float?
        get() = _autoCircumference
    val circumference: Float?
        get() = userCircumference ?: _autoCircumference

    private var accumulatedDuration = 0.0
    private var tripId: Long? = null
    private var startTime: Double = Double.NaN
    private var timeAtLastSplit: Double = 0.0
    private var distanceAtLastSplit: Double = 0.0
    private var measuringCircumference = false
    private var initialMeasureCircRevs = 0
    private var initialMeasureCircDistance = 0.0

    private val clockTick = Timer()
    private val accuracyThreshold = 107.5f
    private val defaultSpeedThreshold = 0.5f
    private val _currentProgress = MutableLiveData<TripProgress>()
    private val _currentTime = MutableLiveData<Double>()
    private val currentTimeStateObserver: Observer<TimeState> = Observer {
        Log.d(TAG, "onChanged current time state observer: ${it.state}")
        if (it != null) currentState = it.state
    }

    private fun tripInProgress() = isTripInProgress(currentState)
    var gpsEnabled = gpsService.accessGranted
    var hrmSensor = bleService.hrmSensor
    var cadenceSensor = bleService.cadenceSensor
    var speedSensor = bleService.speedSensor

    private val gpsObserver: Observer<Location> = Observer<Location> { newLocation ->
        Log.d(TAG, "onChanged gps observer")
        if (tripId != null) {
            coroutineScope.launch {
                measurementsRepository.insertMeasurements(Measurements(tripId!!,
                    LocationData(newLocation),
                    hrmSensor.value?.bpm,
                    cadenceSensor.value,
                    speedSensor.value))
            }
        }
    }

    private val newMeasurementsObserver: Observer<Measurements> = Observer { newMeasurements ->
        Log.d(TAG, "onChanged measurements observer")
        if (newMeasurements == null) {
            Log.d(TAG, "measurements observation is null")
        } else {
            if (currentState == TimeStateEnum.RESUME || currentState == TimeStateEnum.START) {
                setTripProgress(newMeasurements)
            } else {
                setTripPaused(newMeasurements)
            }
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
        Log.v(TAG,
            "accumulatedDuration = ${accumulatedDuration}; startTime = ${startTime}")
    }

    private val accumulateDurationObserver: Observer<Array<TimeState>> =
        Observer { accumulateDuration(it) }

    private val lastSplitObserver: Observer<Split> = Observer { newSplit ->
        if (newSplit != null) {
            timeAtLastSplit = newSplit.totalDuration
            distanceAtLastSplit = newSplit.totalDistance
        } else {
            timeAtLastSplit = 0.0
            distanceAtLastSplit = 0.0
        }
    }

    val currentProgress: LiveData<TripProgress>
        get() = _currentProgress

    val currentTime: LiveData<Double>
        get() = _currentTime

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

        new.speedRevolutions?.let { revs ->
            calculateWheelCircumference(new, newDistance,
                revs, old, oldDistance)
        }
        if (accurateEnough) {
            val distanceDelta = getDistanceDelta(old, new)

            val newSpeed = getSpeed(new, speedThreshold)

            var newSplitSpeed = old?.splitSpeed ?: 0f

            if (new.speed > speedThreshold) newDistance += distanceDelta

            if (crossedSplitThreshold(sharedPreferences,
                    newDistance,
                    old?.distance ?: Double.MAX_VALUE)
            ) {
                val splitDistance = newDistance - distanceAtLastSplit
                val splitDuration = newDuration - timeAtLastSplit
                newSplitSpeed =
                    (splitDistance / splitDuration).toFloat()
                coroutineScope.launch {
                    splitRepository.addSplit(Split(timestamp = System.currentTimeMillis(),
                        duration = splitDuration,
                        distance = splitDistance,
                        totalDuration = newDuration,
                        totalDistance = newDistance,
                        tripId = tripId!!))
                }
            }

            val oldAltitude: Double = old?.measurements?.altitude ?: 0.0
            val verticalSpeed = abs((new.altitude - oldAltitude) / durationDelta)

            var newSlope = calculateSlope(verticalSpeed,
                newSpeed,
                distanceDelta,
                new,
                speedThreshold,
                oldAltitude,
                old)
            val newAcceleration = getAcceleration(durationDelta, newSpeed, old)

            Log.v("TIP_VERTICAL_SPEED", verticalSpeed.toString())
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
                    splitSpeed = newSplitSpeed,
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
                        tracking = false)
        }
    }

    private fun calculateWheelCircumference(
        new: Measurements,
        newDistance: Double,
        speedRevolutions: Int,
        old: TripProgress?,
        oldDistance: Double,
    ) {
        if (new.accuracy < 3.5 && !measuringCircumference && new.speedRevolutions != null && _autoCircumference == null) {
            measuringCircumference = true
            initialMeasureCircDistance = newDistance
            initialMeasureCircRevs = speedRevolutions
        }
        if (measuringCircumference && new.accuracy > 3.5) {
            measuringCircumference = false
        }
        if (measuringCircumference && old?.measurements?.accuracy != null && old.measurements.accuracy < 3.5 && (oldDistance - initialMeasureCircDistance) > 1000) {
            val revs = old.measurements.speedRevolutions?.minus(initialMeasureCircRevs)
            val dist = newDistance - initialMeasureCircDistance
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
        var accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold
        var speedThreshold = defaultSpeedThreshold
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
        verticalSpeed: Double,
        newSpeed: Float,
        distanceDelta: Double,
        new: Measurements,
        speedThreshold: Float,
        oldAltitude: Double,
        old: TripProgress?,
    ): Double {
        var newSlope = 0.0
        if (verticalSpeed < newSpeed && distanceDelta != 0.0) {
            val slopeAlpha = 0.5
            newSlope = slopeAlpha * (
                    if (new.speed > speedThreshold) ((new.altitude - oldAltitude) / distanceDelta)
                    else (old?.slope ?: 0.0)
                    ) + ((1 - slopeAlpha) * (old?.slope ?: 0.0))
            Log.v("SLOPE", newSlope.toString())
        }
        return newSlope
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
            Log.d(TAG, "Start observing trip ID $tripId $currentTimeStateObserver")
            gpsService.observe(lifecycleOwner, gpsObserver)
            getLatest()?.observe(lifecycleOwner, newMeasurementsObserver)
            timeStateRepository.getLatest(tripId!!)
                .observe(lifecycleOwner, currentTimeStateObserver)
            timeStateRepository.getTimeStates(tripId!!)
                .observe(lifecycleOwner, accumulateDurationObserver)
            splitRepository.getLastSplit(tripId!!).observe(lifecycleOwner, lastSplitObserver)
        }
    }

    fun startTrip(lifecycleOwner: LifecycleOwner): LiveData<Long> {
        val tripStarted = MutableLiveData<Long>()

        //TODO: Add speed revs to time state for distance calculations
        coroutineScope.launch(Dispatchers.Default) {
            tripId = tripsRepository.createNewTrip()
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.START))
            Log.d(TAG, "created new trip with id ${tripId.toString()}")
            tripStarted.postValue(tripId)
        }
        tripStarted.observeForever(object : Observer<Long> {
            override fun onChanged(t: Long?) {
                startObserving(lifecycleOwner)
                tripStarted.removeObserver(this)
            }
        })

        clockTick.scheduleAtFixedRate(timerTask {
            val timeHandler = Handler(Looper.getMainLooper())
            timeHandler.post {
                Log.v("TIP_TIME_TICK", getDuration().toString())
                _currentTime.value = getDuration()
            }
        }, 1000 - System.currentTimeMillis() % 1000, 500)

        return tripStarted
    }

    fun pauseTrip() {
        coroutineScope.launch(Dispatchers.Default) {
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.PAUSE))
        }
    }

    fun resumeTrip() {
        coroutineScope.launch(Dispatchers.Default) {
            Log.d(TAG, "resumeTrip")
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.RESUME))
        }
    }

    fun getLatest(): LiveData<Measurements>? {
        if (tripId == null) throw UninitializedPropertyAccessException()
        return measurementsRepository.getLatestMeasurements(tripId!!)
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
            val splitDistance = newDistance - distanceAtLastSplit
            val splitDuration = newDuration - timeAtLastSplit

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
        Log.d(TAG, "Called onCleared")
        super.onCleared()
        //TODO: MAYBE DON'T RUDELY END THE TRIP WHEN THE VIEW MODEL IS CLEARED
        cleanup()
        endTrip()
    }
}

data class TripProgress(
    val measurements: Measurements?,
    val accuracy: Float,
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