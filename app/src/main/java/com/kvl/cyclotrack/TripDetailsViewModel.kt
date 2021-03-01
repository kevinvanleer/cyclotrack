package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TripDetailsViewModel @ViewModelInject constructor(
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
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

    fun measurements() = measurementsRepository.getTripCriticalMeasurements(tripId)
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