package com.kvl.cyclotrack

import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvl.cyclotrack.events.ThermalTierEvent
import com.kvl.cyclotrack.events.TripProgressEvent
import com.kvl.cyclotrack.util.SystemUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.timerTask

enum class DisplayLevel { NORMAL, OUTLINE, DIM, SLEEP }
enum class DisplayPolicy { BURN_IN, POWER_SAVER }

@HiltViewModel
class TripInProgressViewModel @Inject constructor(
    private val bikeRepository: BikeRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
    private val gpsService: GpsService,
    weatherRepository: WeatherRepository
) : ViewModel() {
    private val logTag = "TripInProgressViewModel"

    var bikeWheelCircumference: Float? = null

    var burnInReductionUserPref = false;

    // the 2h auto-enable is absolute from trip start — no pause exclusion, no reset.
    // tripStartMs comes from the first TimeState, so the boundary is invariant across
    // fragment recreation and process restarts
    private var tripStartMs: Long? = null

    fun burnInReductionEnabled() =
        burnInReductionUserPref || twoHourRemainingMs()?.let { it <= 0 } == true

    fun twoHourRemainingMs(): Long? =
        tripStartMs?.let { it + 7_200_000 - SystemUtils.currentTimeMillis() }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            bikeWheelCircumference =
                userCircumferenceToMeters(bikeRepository.getDefaultBike().wheelCircumference)
            Log.d(logTag, bikeWheelCircumference.toString())
        }
    }

    var currentState: TimeStateEnum = TimeStateEnum.STOP
    suspend fun getCurrentTimeState(tripId: Long) =
        timeStateRepository.getLatest(tripId).let { newTimeState ->
            currentState = newTimeState.state
            newTimeState
        }

    private var accumulatedDuration = 0.0
    private var startTime = Double.NaN
    private var _lastCompleteSplitLive = MutableLiveData<Split>()
    private val clockTick = Timer()
    private val _currentProgress = MutableLiveData<TripProgress>()
    private val _currentTime = MutableLiveData<Double>()
    private val currentTimeStateObserver: Observer<TimeState> = Observer { timeState ->
        Log.d(logTag, "onChanged current time state observer: ${timeState.state}")
        currentState = timeState.state
    }

    private fun tripInProgress() = isTripInProgress(currentState)

    val gpsEnabled: LiveData<Boolean>
        get() = gpsService.accessGranted

    val location: LiveData<Location>
        get() = gpsService

    val hrmSensor = MutableLiveData(HrmData(null, null))
    val cadenceSensor = MutableLiveData(CadenceData(null, null, null, null))
    val speedSensor = MutableLiveData(SpeedData(null, null, null, null))
    val powerSensor = MutableLiveData(PowerData(null, null, null, null, null, null))

    val latestWeather = weatherRepository.observeLatest()

    val cadenceRpm: LiveData<CadenceData?> = MediatorLiveData<CadenceData?>().apply {
        addSource(cadenceSensor) {
            value = CadenceData(
                batteryLevel = it.batteryLevel,
                revolutionCount = it.revolutionCount,
                lastEvent = it.lastEvent,
                rpm = it.rpm
            )
        }
        addSource(powerSensor) {
            if (cadenceSensor.value?.rpm == null) {
                value = CadenceData(
                    batteryLevel = it.batteryLevel,
                    revolutionCount = it.cumulativeCrankRevolutions,
                    lastEvent = it.lastCrankEventTime,
                    rpm = when {
                        it.rpm == 0f && (it.instantaneousPower ?: 0) > 0 -> value?.rpm
                        else -> it.rpm
                    }
                )
            }
        }
    }

    suspend fun getFastestDistance(distance: Int, conversionFactor: Double, limit: Int = 10) =
        splitRepository.getFastestDistance(
            distance = distance,
            conversionFactor = conversionFactor,
            limit = limit
        )

    suspend fun getFastestSplit(distance: Int, conversionFactor: Double, limit: Int = 10) =
        splitRepository.getFastestSplit(
            distance = distance,
            conversionFactor = conversionFactor,
            limit = limit
        )

    @Subscribe
    fun onHrmData(hrm: HrmData) {
        hrmSensor.postValue(hrm)
    }

    @Subscribe
    fun onCadenceData(cadence: CadenceData) {
        cadenceSensor.postValue(cadence)
    }

    @Subscribe
    fun onSpeedData(speed: SpeedData) {
        speedSensor.postValue(speed)
    }

    @Subscribe
    fun onPowerData(power: PowerData) {
        val inProgress = isTripInProgress(currentState)
        val instantaneousPower = power.instantaneousPower?.toDouble() ?: 0.0
        val newSampleCount = if (inProgress) powerSensor.value?.sampleCount?.plus(1)
            ?: 1 else powerSensor.value?.sampleCount
        powerSensor.postValue(
            power.copy(
                emaPower = exponentialSmoothing(
                    alpha = 2 / (3 + 1.0),
                    current = instantaneousPower,
                    last = (powerSensor.value?.emaPower?.toDouble() ?: instantaneousPower)
                ).toInt(),
                averagePower = if (inProgress) average(
                    newValue = instantaneousPower,
                    sampleSize = newSampleCount ?: 1,
                    lastAverage = powerSensor.value?.averagePower?.toDouble()
                        ?: instantaneousPower,
                ).toInt() else powerSensor.value?.averagePower,
                sampleCount = newSampleCount
            )
        )
    }

    val currentProgress: LiveData<TripProgress>
        get() = _currentProgress

    val currentTime: LiveData<Double>
        get() = _currentTime

    val lastCompleteSplit: LiveData<Split>
        get() = _lastCompleteSplitLive

    var tripId: Long? = null

    private fun accumulateDuration(timeStates: Array<TimeState>?) {
        timeStates?.let { ts ->
            tripStartMs = ts.firstOrNull()?.timestamp ?: tripStartMs
            getTripInProgressIntervals(ts).takeIf { it.isNotEmpty() }?.let { intervals ->
                accumulatedDuration = accumulateTripTime(intervals)
                startTime = intervals.last().last / 1e3
            }
        }
        Log.v(
            logTag,
            "accumulatedDuration = ${accumulatedDuration}; startTime = ${startTime}"
        )
    }

    private val accumulateDurationObserver: Observer<Array<TimeState>> =
        Observer { accumulateDuration(it) }

    private val lastCompleteSplitObserver: Observer<Split?> = Observer { newSplit ->
        newSplit?.let { n -> _lastCompleteSplitLive.value = n };
    }


    val displayLevel = MutableLiveData(DisplayLevel.NORMAL)
    private val requests = mutableMapOf<DisplayPolicy, DisplayLevel>()
    private var peekUntilMs = 0L
    private val peekHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val peekCallback = Runnable { publishDisplayLevel() }

    var thermalProtectionUserPref = true

    fun publishBurnInRequest() {
        val baseline =
            if (burnInReductionEnabled()) DisplayLevel.OUTLINE else DisplayLevel.NORMAL
        // the sticky event is the single readable copy of the tier
        val tier = EventBus.getDefault()
            .getStickyEvent(ThermalTierEvent::class.java)?.tier ?: ThermalTier.NORMAL
        val thermal =
            if (thermalProtectionUserPref) tier.level else DisplayLevel.NORMAL
        requestDisplayLevel(DisplayPolicy.BURN_IN, maxOf(baseline, thermal))
    }

    // sticky: a recreated ViewModel (dashboard navigation) re-applies the current tier
    // at registration; ThermalMonitor owns the state machine for the life of the trip
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onThermalTierEvent(event: ThermalTierEvent) {
        // also re-evaluates the 2h baseline between UI events
        publishBurnInRequest()
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onTripProgressEvent(event: TripProgressEvent) {
        Log.d(logTag, "Received trip progress event")
        _currentProgress.value = event.tripProgress
    }

    fun currentTimeState(tripId: Long) = timeStateRepository.observeLatest(tripId)

    private fun getDuration() =
        if (startTime.isFinite() && tripInProgress()) accumulatedDuration + (SystemUtils.currentTimeMillis() / 1e3) - startTime else accumulatedDuration

    private fun startObserving(tripId: Long, lifecycleOwner: LifecycleOwner) {
        Log.d(logTag, "Start observing trip ID $tripId $currentTimeStateObserver")
        timeStateRepository.observeLatest(tripId)
            .observe(lifecycleOwner, currentTimeStateObserver)
        timeStateRepository.observeTimeStates(tripId)
            .observe(lifecycleOwner, accumulateDurationObserver)
        splitRepository.observeLastCompleteSplit(tripId)
            .observe(lifecycleOwner, lastCompleteSplitObserver)
    }

    fun requestDisplayLevel(policy: DisplayPolicy, level: DisplayLevel) {
        requests[policy] = level
        publishDisplayLevel()
    }

    fun peekDisplay(durationMs: Long = 10_000) {
        peekUntilMs = SystemUtils.currentTimeMillis() + durationMs
        publishDisplayLevel()
        peekHandler.removeCallbacks(peekCallback)
        peekHandler.postDelayed(peekCallback, durationMs)   // re-applies max after the peek
    }

    // peek does not change the level — it is a transient reveal the presentation layer
    // renders on top of the current level (SLEEP's peek shows the dashboard in green)
    val displayPeek = MutableLiveData(false)

    private fun publishDisplayLevel() {
        val effective = requests.values.maxOrNull() ?: DisplayLevel.NORMAL
        if (displayLevel.value != effective) displayLevel.value = effective
        val peeking = SystemUtils.currentTimeMillis() < peekUntilMs
        if (displayPeek.value != peeking) displayPeek.value = peeking
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
                getDuration().let {
                    Log.v("TIP_TIME_TICK", it.toString())
                    _currentTime.value = it
                }
            }
        }, 1000 - SystemUtils.currentTimeMillis() % 1000, 500)
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

    init {
        // last initializer on purpose: registering delivers the sticky ThermalTierEvent
        // synchronously, and its handler touches properties declared above — an earlier
        // init block would run before those backing fields exist and crash
        EventBus.getDefault().register(this)
    }
}
