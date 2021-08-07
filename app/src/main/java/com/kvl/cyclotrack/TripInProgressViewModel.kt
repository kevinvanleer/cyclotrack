package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.kvl.cyclotrack.events.TripProgressEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.timerTask

@HiltViewModel
class TripInProgressViewModel @Inject constructor(
    coroutineScopeProvider: CoroutineScope?,
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
    private val gpsService: GpsService,
    private val bleService: BleService,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {
    private val logTag = "TripInProgressViewModel"
    private val coroutineScope = getViewModelScope(coroutineScopeProvider)

    init {
        EventBus.getDefault().register(this)
    }

    var currentState: TimeStateEnum = TimeStateEnum.STOP

    private var accumulatedDuration = 0.0
    private var startTime = Double.NaN
    private var _lastSplitLive = MutableLiveData<Split>()
    private val clockTick = Timer()
    private val _currentProgress = MutableLiveData<TripProgress>()
    private val _currentTime = MutableLiveData<Double>()
    private val _autoCircumference = MutableLiveData<Float?>()
    private val _userCircumference = MutableLiveData<Float?>()
    private val currentTimeStateObserver: Observer<TimeState> = Observer { timeState ->
        timeState?.let {
            Log.d(logTag, "onChanged current time state observer: ${it.state}")
            currentState = it.state
        }
    }
    private val currentTripObserver: Observer<Trip> = Observer { trip ->
        _userCircumference.value = trip.userWheelCircumference
        _autoCircumference.value = trip.autoWheelCircumference
    }
    val circumference: Float?
        get() = when (sharedPreferences.getBoolean(CyclotrackApp.instance.getString(
            R.string.preference_key_useAutoCircumference), true)) {
            true -> _autoCircumference.value ?: _userCircumference.value
            else -> _userCircumference.value ?: _autoCircumference.value
        }

    private fun tripInProgress() = isTripInProgress(currentState)
    fun gpsEnabled(): LiveData<Boolean> = gpsService.accessGranted
    fun hrmSensor(): LiveData<HrmData> = bleService.hrmSensor
    fun cadenceSensor(): LiveData<CadenceData> = bleService.cadenceSensor
    fun speedSensor(): LiveData<SpeedData> = bleService.speedSensor

    var tripId: Long? = null

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
            _lastSplitLive.value = newSplit
        }
    }

    val currentProgress: LiveData<TripProgress>
        get() = _currentProgress

    val currentTime: LiveData<Double>
        get() = _currentTime

    val lastSplit: LiveData<Split>
        get() = _lastSplitLive

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTripProgressEvent(event: TripProgressEvent) {
        _currentProgress.value = event.tripProgress
    }

    fun currentTimeState(tripId: Long) = timeStateRepository.observeLatest(tripId)

    private fun getDuration() =
        if (startTime.isFinite() && tripInProgress()) accumulatedDuration + (System.currentTimeMillis() / 1e3) - startTime else accumulatedDuration

    private fun startObserving(tripId: Long, lifecycleOwner: LifecycleOwner) {
        Log.d(logTag, "Start observing trip ID $tripId $currentTimeStateObserver")
        tripsRepository.observe(tripId).observe(lifecycleOwner, currentTripObserver)
        timeStateRepository.observeLatest(tripId)
            .observe(lifecycleOwner, currentTimeStateObserver)
        timeStateRepository.observeTimeStates(tripId)
            .observe(lifecycleOwner, accumulateDurationObserver)
        splitRepository.observeLastSplit(tripId).observe(lifecycleOwner, lastSplitObserver)
    }

    fun startTrip(tripId: Long, lifecycleOwner: LifecycleOwner) {
        this.tripId = tripId
        startObserving(tripId, lifecycleOwner)
        startClock()
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

    fun resumeTrip(tripId: Long, lifecycleOwner: LifecycleOwner) {
        Log.d(logTag, "Resuming trip $tripId")
        startObserving(tripId, lifecycleOwner)
        startClock()
    }

    private fun cleanup() {
        EventBus.getDefault().unregister(this)
        clockTick.cancel()
    }

    override fun onCleared() {
        Log.d(logTag, "Called onCleared")
        super.onCleared()
        cleanup()
    }
}
