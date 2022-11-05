package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.*
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.CadenceSpeedMeasurementRepository
import com.kvl.cyclotrack.data.HeartRateMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class TripDetailsViewModel @Inject constructor(
    private val tripsRepository: TripsRepository,
    private val bikeRepository: BikeRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
    private val onboardSensorsRepository: OnboardSensorsRepository,
    private val sharedPreferences: SharedPreferences,
    private val googleFitApiService: GoogleFitApiService,
    private val weatherRepository: WeatherRepository,
    private val hrmRepository: HeartRateMeasurementRepository,
    private val cscRepository: CadenceSpeedMeasurementRepository
) : ViewModel() {
    val logTag = "TripDetailsViewModel"
    var tripId: Long = 0
        set(value) {
            tripOverview = tripsRepository.observe(value)
            timeState = timeStateRepository.observeTimeStates(value)
            splits = splitRepository.observeTripSplits(value)
            locationMeasurements = measurementsRepository.observe(value)
            onboardSensors = onboardSensorsRepository.observeDecimated(value)
            tripWeather = weatherRepository.observeTripWeather(value)
            heartRateMeasurements = hrmRepository.observeTripHeartRates(value)
            cadenceMeasurements = cscRepository.observeTripCadence(value)
            speedMeasurements = cscRepository.observeTripSpeed(value)
            field = value
        }

    lateinit var tripOverview: LiveData<Trip> private set
    lateinit var timeState: LiveData<Array<TimeState>> private set
    lateinit var splits: LiveData<Array<Split>> private set
    lateinit var measurements: LiveData<Array<CriticalMeasurements>> private set
    lateinit var locationMeasurements: LiveData<Array<Measurements>> private set
    lateinit var onboardSensors: LiveData<Array<OnboardSensors>> private set
    lateinit var tripWeather: LiveData<Array<Weather>> private set
    lateinit var heartRateMeasurements: LiveData<Array<HeartRateMeasurement>> private set
    lateinit var cadenceMeasurements: LiveData<Array<CadenceSpeedMeasurement>> private set
    lateinit var speedMeasurements: LiveData<Array<CadenceSpeedMeasurement>> private set

    suspend fun getBikeWheelCircumference(bikeId: Long) =
        userCircumferenceToMeters(bikeRepository.get(bikeId)?.wheelCircumference) ?: 0f

    fun updateSplits() = viewModelScope.launch {
        val splits = splitRepository.getTripSplits(tripId)
        var areSplitsInSystem = false
        if (splits.isNotEmpty()) areSplitsInSystem =
            abs(getSplitThreshold(sharedPreferences) - splits[0].totalDistance) > 50.0
        if (true || splits.isEmpty() || !areSplitsInSystem) {
            // Force true. TripInProgressService doesn't add the last partial split
            Log.d(logTag, "Recomputing splits")
            clearSplits()
            addSplits()
        }
    }

    fun clearSplits() =
        viewModelScope.launch {
            splitRepository.removeTripSplits(tripId)
        }

    fun removeTrip() =
        viewModelScope.launch { tripsRepository.removeTrip(tripId) }

    suspend fun getCombinedBiometrics(timestamp: Long, context: Context): Biometrics =
        com.kvl.cyclotrack.util.getCombinedBiometrics(
            tripId,
            timestamp,
            context,
            viewModelScope,
            tripsRepository,
            googleFitApiService
        )

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

    data class SpeedData(
        val summary: Trip? = null,
        val locationMeasurements: Array<Measurements> = emptyArray(),
        val timeStates: Array<TimeState> = emptyArray(),
        val speedMeasurements: Array<CadenceSpeedMeasurement> = emptyArray(),
    )

    data class ExportData(
        val summary: Trip? = null,
        val measurements: Array<Measurements>? = null,
        val timeStates: Array<TimeState>? = null,
        val splits: Array<Split>? = null,
        val onboardSensors: Array<OnboardSensors>? = null,
        val weather: Array<Weather>? = null,
        val heartRateMeasurements: Array<HeartRateMeasurement>? = null,
        val speedMeasurements: Array<CadenceSpeedMeasurement>? = null,
        val cadenceMeasurements: Array<CadenceSpeedMeasurement>? = null,
    )

    fun speedLiveData() = MediatorLiveData<SpeedData>().apply {
        addSource(tripOverview) {
            value = value?.copy(summary = it) ?: SpeedData(summary = it)
        }
        addSource(locationMeasurements) {
            value = value?.copy(locationMeasurements = it) ?: SpeedData(locationMeasurements = it)
        }
        addSource(timeState) {
            value = value?.copy(timeStates = it) ?: SpeedData(timeStates = it)
        }
        addSource(speedMeasurements) {
            value = value?.copy(speedMeasurements = it) ?: SpeedData(speedMeasurements = it)
        }
    }

    fun addSplits() {
        val combined = zipLiveData(locationMeasurements, timeState)
        combined.observeForever(object :
            Observer<Pair<Array<Measurements>, Array<TimeState>>> {
            override fun onChanged(pair: Pair<Array<Measurements>, Array<TimeState>>) {
                val measurements = pair.first
                val timeStates = pair.second

                if (measurements.isNotEmpty()) {
                    val tripSplits =
                        calculateSplits(tripId, measurements, timeStates, sharedPreferences)
                    viewModelScope.launch {
                        Log.d(
                            "TRIP_DETAILS_VIEW_MODEL",
                            "Inserting post-trip computed splits in database"
                        )
                        splitRepository.addSplits(tripSplits.toTypedArray())
                    }
                    combined.removeObserver(this)
                }
            }
        })
    }
}
