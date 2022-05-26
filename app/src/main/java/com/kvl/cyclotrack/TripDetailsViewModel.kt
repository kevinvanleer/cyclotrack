package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.*
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
) : ViewModel() {
    val logTag = "TRIP_DETAILS_VIEW_MODEL"
    var tripId: Long = 0
        set(value) {
            tripOverview = tripsRepository.observe(value)
            timeState = timeStateRepository.observeTimeStates(value)
            splits = splitRepository.observeTripSplits(value)
            measurements = measurementsRepository.observeCritical(value)
            exportMeasurements = measurementsRepository.observe(value)
            onboardSensors = onboardSensorsRepository.observeDecimated(value)
            field = value
        }

    lateinit var tripOverview: LiveData<Trip> private set
    lateinit var timeState: LiveData<Array<TimeState>> private set
    lateinit var splits: LiveData<Array<Split>> private set
    lateinit var measurements: LiveData<Array<CriticalMeasurements>> private set
    lateinit var onboardSensors: LiveData<Array<OnboardSensors>> private set
    private lateinit var exportMeasurements: LiveData<Array<Measurements>>

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
        getCombinedBiometrics(tripId,
            timestamp,
            context,
            viewModelScope,
            tripsRepository,
            googleFitApiService)

    data class ExportData(
        var summary: Trip? = null,
        var measurements: Array<Measurements>? = null,
        var timeStates: Array<TimeState>? = null,
        var splits: Array<Split>? = null,
        var onboardSensors: Array<OnboardSensors>? = null,
    )

    fun exportData() = MediatorLiveData<ExportData>().apply {
        value = ExportData()
        addSource(tripOverview) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export trip")
            value = value?.copy(summary = it)
        }
        addSource(exportMeasurements) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export measurements")
            value = value?.copy(measurements = it)
        }
        addSource(timeState) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export timeStates")
            value = value?.copy(timeStates = it)
        }
        addSource(splits) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export splits")
            value = value?.copy(splits = it)
        }
        addSource(onboardSensors) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export onboardSensors")
            value = value?.copy(onboardSensors = it)
        }
    }

    fun addSplits() {
        val combined = zipLiveData(measurements, timeState)
        combined.observeForever(object :
            Observer<Pair<Array<CriticalMeasurements>, Array<TimeState>>> {
            override fun onChanged(pair: Pair<Array<CriticalMeasurements>, Array<TimeState>>) {
                val measurements = pair.first
                val timeStates = pair.second

                if (measurements.isNotEmpty()) {
                    val tripSplits =
                        calculateSplits(tripId, measurements, timeStates, sharedPreferences)
                    viewModelScope.launch {
                        Log.d("TRIP_DETAILS_VIEW_MODEL",
                            "Inserting post-trip computed splits in database")
                        splitRepository.addSplits(tripSplits.toTypedArray())
                    }
                    combined.removeObserver(this)
                }
            }
        })
    }

}