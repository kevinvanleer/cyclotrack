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

    suspend fun getCleanupTrips(): Array<Trip> {
        return tripDao.getCleanupTrips()
    }

    fun createNewTrip(): Long {
        return tripDao.save(Trip())
    }

    suspend fun removeTrips(trips: Array<Trip>) {
        return tripDao.removeTrips(trips)
    }

    suspend fun updateTripStats(stats: TripStats) = with(tripDao) { updateStats(stats) }
}