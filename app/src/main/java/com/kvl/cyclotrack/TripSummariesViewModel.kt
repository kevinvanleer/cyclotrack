package com.kvl.cyclotrack

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel

class TripSummariesViewModel @ViewModelInject constructor(
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
) : ViewModel() {
    val allTrips = tripsRepository.getAllTrips()
    val realTrips = tripsRepository.getRealTrips()
    fun getTripMeasurements(tripId: Long): LiveData<Array<Measurements>> =
        measurementsRepository.getTripMeasurements(tripId)
}