package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripDetailsViewModel @Inject constructor(
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
    private val onboardSensorsRepository: OnboardSensorsRepository,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {
    var tripId: Long = 0

    fun tripOverview() = tripsRepository.getTrip(tripId)
    fun timeState() = timeStateRepository.getTimeStates(tripId)
    fun splits() = splitRepository.getTripSplits(tripId)
    fun clearSplits() =
        viewModelScope.launch {
            splitRepository.removeTripSplits(tripId)
        }

    fun removeTrip() =
        viewModelScope.launch { tripsRepository.removeTrip(tripId) }

    suspend fun getDefaultBiometrics() = tripsRepository.getDefaultBiometrics(tripId)

    fun measurements() = measurementsRepository.getTripCriticalMeasurements(tripId)
    fun exportMeasurements() = measurementsRepository.getTripMeasurements(tripId)
    fun onboardSensors() = onboardSensorsRepository.getTripMeasurements(tripId)

    data class ExportData(
        var summary: Trip? = null,
        var measurements: Array<Measurements>? = null,
        var timeStates: Array<TimeState>? = null,
        var splits: Array<Split>? = null,
        var onboardSensors: Array<OnboardSensors>? = null,
    )

    fun exportData() = MediatorLiveData<ExportData>().apply {
        value = ExportData()
        addSource(tripOverview()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export trip")
            value = value?.copy(summary = it)
        }
        addSource(exportMeasurements()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export measurements")
            value = value?.copy(measurements = it)
        }
        addSource(timeState()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export timeStates")
            value = value?.copy(timeStates = it)
        }
        addSource(splits()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export splits")
            value = value?.copy(splits = it)
        }
        addSource(onboardSensors()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export onboardSensors")
            value = value?.copy(onboardSensors = it)
        }
    }

    fun addSplits() {
        val combined = zipLiveData(measurements(), timeState())
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