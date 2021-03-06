package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import javax.inject.Inject

class TripsRepository @Inject constructor(private val tripDao: TripDao) {
    fun observe(id: Long): LiveData<Trip> {
        return tripDao.subscribe(id)
    }

    suspend fun get(id: Long): Trip {
        return tripDao.load(id)
    }

    suspend fun getNewest(): Trip = tripDao.getNewestTrip()

    fun observeAll(): LiveData<Array<Trip>> {
        return tripDao.susbscribeAll()
    }

    suspend fun getAll() = tripDao.loadAll()
    suspend fun getAfter(tripId: Long) = tripDao.loadAfter(tripId)

    fun observeRealTrips(): LiveData<Array<Trip>> {
        return tripDao.subscribeRealTrips()
    }

    suspend fun getCleanupTrips(): Array<Trip> {
        return tripDao.getCleanupTrips()
    }

    fun createNewTrip(): Long {
        return tripDao.save(Trip())
    }

    suspend fun removeTrip(tripId: Long) {
        return tripDao.removeTrip(TripId(tripId))
    }

    suspend fun removeTrips(trips: Array<Trip>) {
        return tripDao.removeTrips(trips)
    }

    suspend fun endTrip(tripId: Long) {
        tripDao.updateInProgress(TripInProgress(id = tripId, inProgress = false))
    }

    suspend fun updateTripStats(stats: TripStats) = with(tripDao) { updateStats(stats) }
    suspend fun updateTripStuff(stuff: TripStuff) = with(tripDao) { updateStuff(stuff) }
    suspend fun updateWheelCircumference(circ: TripWheelCircumference) =
        with(tripDao) { updateCircumference(circ) }

    suspend fun updateBiometrics(biometrics: Biometrics) {
        tripDao.updateBiometrics(biometrics)
    }

    suspend fun getDefaultBiometrics(tripId: Long) = tripDao.getDefaultBiometrics(tripId)

    suspend fun setGoogleFitSyncStatus(
        tripId: Long,
        googleFitSyncStatus: GoogleFitSyncStatusEnum,
    ) =
        tripDao.updateGoogleFitSyncStatus(TripGoogleFitSync(id = tripId,
            googleFitSyncStatus = googleFitSyncStatus))

    suspend fun getGoogleFitUnsynced() = tripDao.loadGoogleFitUnsyncedTrips()
}