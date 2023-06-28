package com.kvl.cyclotrack

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val tripsRepository: TripsRepository,
    private val splitRepository: SplitRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository
) : ViewModel() {
    val realTrips = tripsRepository.observeRealTrips()
    fun recentTrips(start: Long, end: Long) =
        tripsRepository.observeDateRange(start, end)

    fun longestTrips(limit: Int = 3) = tripsRepository.longestTrips(limit)
    fun tripTotals(start: Long, end: Long) = tripsRepository.observeTripTotals(start, end)
    val tripTotals = tripsRepository.observeTripTotals()

    fun monthlyTotals() = tripsRepository.observeMonthlyTotals(3)
    fun weeklyTotals() = tripsRepository.observeWeeklyTotals(3)

    suspend fun getTripMeasurements(tripId: Long) = measurementsRepository.get(tripId)
    suspend fun getTripTimeStates(tripId: Long) = timeStateRepository.getTimeStates(tripId)

    val bikeTotals = tripsRepository.observeBikeTotals()
    fun popularDistances(
        conversionFactor: Double,
        timestamp: Long = 0,
        bucketSize: Int = 1,
        limit: Int = 3
    ) =
        tripsRepository.mostPopularDistances(conversionFactor, timestamp, bucketSize, limit)

    fun tripsOfDistance(distance: Int, conversionFactor: Double, bucketSize: Int, limit: Int) =
        tripsRepository.tripsOfDistance(distance, conversionFactor, bucketSize, limit)

    fun fastestDistance(distance: Int, conversionFactor: Double, limit: Int = 3) =
        splitRepository.observeFastestDistance(distance, conversionFactor, limit)
}
