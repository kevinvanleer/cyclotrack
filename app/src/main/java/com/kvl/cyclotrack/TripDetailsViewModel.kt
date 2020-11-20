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
    fun clearSplits() {
        viewModelScope.launch {
            splitRepository.removeTripSplits(tripId)
        }
    }

    fun measurements() = measurementsRepository.getTripMeasurements(tripId)
    fun addSplits() {
        measurements().observeForever(object : Observer<Array<Measurements>> {
            override fun onChanged(t: Array<Measurements>) {
                if (t.isNotEmpty()) {
                    val tripSplits = arrayListOf<Split>()
                    var totalDistance = 0.0
                    val startTime = t[0].elapsedRealtimeNanos

                    var prev = t[0]
                    for (index in 1 until t.size) {
                        val lastSplit = if (tripSplits.isEmpty()) Split(0,
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            0,
                            0) else tripSplits.last()
                        val curr = t[index]

                        if (curr.accuracy < 5 && prev.accuracy < 5) {
                            var distanceArray = floatArrayOf(0f)
                            android.location.Location.distanceBetween(curr.latitude,
                                curr.longitude,
                                prev.latitude,
                                prev.longitude,
                                distanceArray)
                            totalDistance += distanceArray[0]
                            val newTotalDuration = (curr.elapsedRealtimeNanos - startTime) / 1e9

                            if (crossedSplitThreshold(sharedPreferences,
                                    totalDistance,
                                    lastSplit.totalDistance)
                            ) {
                                val splitDistance = totalDistance - lastSplit.totalDistance
                                val splitDuration = newTotalDuration - lastSplit.totalDuration
                                tripSplits.add(Split(timestamp = curr.time,
                                    duration = splitDuration,
                                    distance = splitDistance,
                                    totalDuration = newTotalDuration,
                                    totalDistance = totalDistance,
                                    tripId = tripId))
                            }
                        }
                        if (curr.accuracy < 5) prev = curr
                    }
                    viewModelScope.launch {
                        Log.d("TRIP_DETAILS_VIEW_MODEL",
                            "Inserting post-trip computed splits in database")
                        splitRepository.addSplits(tripSplits.toTypedArray())
                    }
                    measurements().removeObserver(this)
                }
            }
        })
    }
}