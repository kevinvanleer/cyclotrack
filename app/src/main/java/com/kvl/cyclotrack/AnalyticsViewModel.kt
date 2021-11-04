package com.kvl.cyclotrack

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository
) : ViewModel() {
    val allTrips = tripsRepository.observeAll()
    fun recentTrips(start: Long, end: Long = Instant.now().toEpochMilli()) =
        tripsRepository.observeDateRange(start, end)

    fun longestTrips(limit: Int = 3) = tripsRepository.longestTrips(limit)
    fun tripTotals(start: Long, end: Long = Instant.now().toEpochMilli()) =
        tripsRepository.observeTripTotals(start, end)

    fun monthlyTotals() = tripsRepository.observeMonthlyTotals(3)
    fun weeklyTotals() = tripsRepository.observeWeeklyTotals(3)

    suspend fun getTripMeasurements(tripId: Long) = measurementsRepository.getCritical(tripId)
    suspend fun getTripTimeStates(tripId: Long) = timeStateRepository.getTimeStates(tripId)

}