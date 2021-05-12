package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
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
    fun observeTripMeasurements(tripId: Long) =
        measurementsRepository.observeCritical(tripId)

    suspend fun getTripMeasurements(tripId: Long) =
        measurementsRepository.getCritical(tripId)

    suspend fun getTripTimeStates(tripId: Long): Array<TimeState> =
        timeStateRepository.getTimeStates(tripId)

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