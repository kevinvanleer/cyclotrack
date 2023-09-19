package com.kvl.cyclotrack

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvl.cyclotrack.data.parseSearchString
import com.kvl.cyclotrack.data.tripPassesExpression
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
    val filteredTrips = MutableLiveData<Array<Trip>>(allTrips.value)

    fun filterTrips() {
        try {
            val searchExpression = parseSearchString(searchText)
            if (searchExpression.isNullOrEmpty()) {
                filteredTrips.value = allTrips.value
                return
            }

            filteredTrips.value = allTrips.value?.filter {
                tripPassesExpression(it, searchExpression)
            }?.toTypedArray()
        } catch (e: Exception) {
            filteredTrips.value = allTrips.value
        }
    }

    var searchText = ""
        set(newValue) {
            field = newValue
            if (newValue == "") filterTrips()
        }

    suspend fun getMeasurements(tripId: Long) =
        measurementsRepository.get(tripId)

    suspend fun getTripTimeStates(tripId: Long): Array<TimeState> =
        timeStateRepository.getTimeStates(tripId)

    fun cleanupTrips() =
        viewModelScope.launch {
            val cleanupTrips = tripsRepository.getCleanupTrips()
            Log.d("OPTIONS_MENU_CLEANUP", "Removing ${cleanupTrips.size} trips")
            tripsRepository.removeTrips(cleanupTrips)
        }
}
