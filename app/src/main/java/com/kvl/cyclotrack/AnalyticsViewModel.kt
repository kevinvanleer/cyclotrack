package com.kvl.cyclotrack

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val tripsRepository: TripsRepository,
) : ViewModel() {
    val allTrips = tripsRepository.observeAll()
    fun recentTrips(start: Long, end: Long = Instant.now().toEpochMilli()) =
        tripsRepository.observeDateRange(start, end)

    fun longestTrips(limit: Int = 3) = tripsRepository.longestTrips(limit)
    fun tripTotals(start: Long, end: Long = Instant.now().toEpochMilli()) =
        tripsRepository.observeTripTotals(start, end)
}