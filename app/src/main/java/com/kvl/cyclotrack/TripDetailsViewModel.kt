package com.kvl.cyclotrack

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel

class TripDetailsViewModel @ViewModelInject constructor(
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
) : ViewModel() {
    var tripId: Long = 0

    fun tripOverview() = tripsRepository.getTrip(tripId)
    fun timeState() = timeStateRepository.getTimeStates(tripId)
    fun splits() = splitRepository.getTripSplits(tripId)
    fun measurements() = measurementsRepository.getTripMeasurements(tripId)
}