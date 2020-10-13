package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import javax.inject.Inject

class TripsRepository @Inject constructor(private val tripDao: TripDao) {
    fun getTrip(id: Long): LiveData<Trip> {
        return tripDao.load(id)
    }
    fun getAllTrips(): LiveData<Array<Trip>> {
        return tripDao.loadAll()
    }
    fun getRealTrips(): LiveData<Array<Trip>> {
        return tripDao.getRealTrips()
    }
    fun createNewTrip(): Long {
        return tripDao.save(Trip())
    }
    suspend fun updateTripStats(stats: TripStats) = with(tripDao) { updateStats(stats) }
}