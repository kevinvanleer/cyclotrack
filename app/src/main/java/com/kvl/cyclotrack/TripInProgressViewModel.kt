package com.kvl.cyclotrack

import android.location.Location
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

class TripInProgressViewModel @ViewModelInject constructor(
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val gpsService: GpsService,
) : ViewModel() {

    var currentState: TimeStateEnum = TimeStateEnum.STOP
    private var tripId: Long? = null
    private var record = false
    private var startTime: Double = Double.NaN
    private var splitTime: Double = 0.0
    private var splitDistance: Double = 0.0
    private val accuracyThreshold = 7.5f
    private val defaultSpeedThreshold = 0.5f
    private val _currentProgress = MutableLiveData<TripProgress>()

    val currentProgress: LiveData<TripProgress>
        get() = _currentProgress

    fun startGps() = gpsService.startListening()
    //fun currentState() = timeStateRepository.getLatest(tripId!!)

    private fun setTripProgress(new: Measurements) {
        val old = _currentProgress.value
        val oldDistance: Double = old?.distance ?: 0.0
        var newDistance: Double = oldDistance
        var accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold
        var speedThreshold = defaultSpeedThreshold

        if (!startTime.isFinite()) startTime = new.elapsedRealtimeNanos / 1e9

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (new.hasSpeedAccuracy()) {
                //accurateEnough = accurateEnough && new.speed > new.speedAccuracyMetersPerSecond
                //speedThreshold = max(defaultSpeedThreshold, new.speedAccuracyMetersPerSecond * 1.5f)
            }
        }

        val newDuration =
            if (startTime.isFinite()) (new.elapsedRealtimeNanos / 1e9) - startTime else 0.0
        //val durationDelta = (newDuration - (old?.duration ?: 0.0))
        val durationDelta =
            newDuration - if (startTime.isFinite()) ((old?.location?.elapsedRealtimeNanos
                ?: 0L) / 1e9) - startTime else 0.0

        if (accurateEnough) {
            var distanceResults = floatArrayOf(0f)
            Location.distanceBetween(old?.location?.latitude ?: new.latitude,
                old?.location?.longitude ?: new.longitude,
                new.latitude,
                new.longitude,
                distanceResults)
            val distanceDelta = distanceResults[0].toDouble()

            //val newSpeed: Float =
            //    if (newDuration == 0.0) 0f else (distanceDelta / durationDelta).toFloat()
            val newSpeed = if (new.speed > speedThreshold) new.speed else 0f

            var newSplitSpeed = old?.splitSpeed ?: 0f

            if (new.speed > speedThreshold) newDistance += distanceDelta

            if (floor(newDistance * 0.000621371) > floor((old?.distance
                    ?: Double.MAX_VALUE) * 0.000621371)
            ) {
                newSplitSpeed =
                    ((newDistance - splitDistance) / (newDuration - splitTime)).toFloat()
                splitTime = newDuration
                splitDistance = newDistance
            }

            val oldAltitude: Double = old?.location?.altitude ?: 0.0
            val verticalSpeed = abs((new.altitude - oldAltitude) / durationDelta)
            Log.v("VERTICAL_SPEED", verticalSpeed.toString())
            var newSlope = 0.0
            if (verticalSpeed < newSpeed && distanceDelta != 0.0) {
                val slopeAlpha = 0.5
                newSlope = slopeAlpha * (
                        if (new.speed > speedThreshold) ((new.altitude - oldAltitude) / distanceDelta)
                        else (old?.slope ?: 0.0)
                        ) + ((1 - slopeAlpha) * (old?.slope ?: 0.0))
                Log.v("SLOPE", newSlope.toString())
            }
            val newAcceleration =
                if (durationDelta == 0.0) 0f
                else ((newSpeed - (old?.speed ?: 0f)) / durationDelta).toFloat()

            Log.d("SPEED", newSpeed.toString())
            Log.d("SPEED_DISTANCE_DELTA", distanceDelta.toString())
            Log.d("SPEED_DURATION_DELTA", durationDelta.toString())

            Log.v("LOCATION_MODEL_NEW",
                "accuracy: ${new.accuracy}; speed: ${newSpeed}; acceleration: ${newAcceleration}; distance: $newDistance; slope: $newSlope; duration: $newDuration")

            Log.d("MAX_ACCELERATION", max(newAcceleration, old?.maxAcceleration ?: 0f).toString())

            viewModelScope.launch {
                if (tripId != null) tripsRepository.updateTripStats(TripStats(tripId!!,
                    newDistance,
                    newDuration,
                    (newDistance / newDuration).toFloat()))
            }
            _currentProgress.value =
                TripProgress(location = new,
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
                        location = null,
                        accuracy = new.accuracy,
                        tracking = false)
        }
    }

    private val gpsObserver: Observer<Location> = Observer<Location> { newLocation ->
        if (record && tripId != null) {
            viewModelScope.launch {
                measurementsRepository.insertMeasurements(Measurements(tripId!!,
                    LocationData(newLocation)))
            }
        }
    }

    private val newMeasurementsObserver: Observer<Measurements> = Observer { newMeasurements ->
        if (newMeasurements == null) {
            Log.d("TIP_VIEW_MODEL", "measurements observation is null")
        } else {
            setTripProgress(newMeasurements)
        }
    }

    fun startTrip(): LiveData<Long> {
        val tripStarted = MutableLiveData<Long>()

        viewModelScope.launch(Dispatchers.Default) {
            tripId = tripsRepository.createNewTrip()
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.START))
            Log.d("TIP_VIEW_MODEL", "created new trip with id ${tripId.toString()}")
            record = true
            tripStarted.postValue(tripId)
        }
        gpsService.observeForever(gpsObserver)
        tripStarted.observeForever(object : Observer<Long> {
            override fun onChanged(t: Long?) {
                getLatest()?.observeForever(newMeasurementsObserver)
                timeStateRepository.getLatest(tripId!!).observeForever { currentState = it.state }
                tripStarted.removeObserver(this)
            }
        })

        return tripStarted
    }

    fun pauseTrip() {
        viewModelScope.launch(Dispatchers.Default) {
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.PAUSE))
        }
    }

    fun resumeTrip() {
        viewModelScope.launch(Dispatchers.Default) {
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.RESUME))
        }
    }

    fun endTrip() {
        viewModelScope.launch(Dispatchers.Default) {
            timeStateRepository.appendTimeState(TimeState(tripId!!, TimeStateEnum.STOP))
        }
    }

    fun getLatest(): LiveData<Measurements>? {
        if (tripId == null) throw UninitializedPropertyAccessException()
        return measurementsRepository.getLatestMeasurements(tripId!!)
    }

    override fun onCleared() {
        Log.d("TIP_VIEW_MODEL", "Called onCleared")
        super.onCleared()
        gpsService.removeObserver(gpsObserver)
    }
}

data class TripProgress(
    val location: Measurements?,
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