package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripSummariesViewModel @Inject constructor(
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
) : ViewModel() {
    val tripListState = Bundle()
    val allTrips = tripsRepository.observeAll()
    val realTrips = tripsRepository.observeRealTrips()
    fun getTripMeasurements(tripId: Long): LiveData<Array<CriticalMeasurements>> =
        measurementsRepository.observeCritical(tripId)

    fun getTripTimeStates(tripId: Long): LiveData<Array<TimeState>> =
        timeStateRepository.observeTimeStates(tripId)

    fun removeTrips(trips: Array<Long>) {
        viewModelScope.launch {
            trips.forEach {
                tripsRepository.removeTrip(it)
            }
        }
    }

    fun cleanupTrips() =
        viewModelScope.launch {
            val cleanupTrips = tripsRepository.getCleanupTrips()
            Log.d("OPTIONS_MENU_CLEANUP", "Removing ${cleanupTrips.size} trips")
            tripsRepository.removeTrips(cleanupTrips)
        }
}