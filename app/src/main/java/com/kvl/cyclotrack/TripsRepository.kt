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

    suspend fun getNewest(): Trip? = tripDao.getNewestTrip()
    fun observeNewest(): LiveData<Trip> = tripDao.observeNewestTrip()

    fun observeAll(): LiveData<Array<Trip>> {
        return tripDao.subscribeAll()
    }

    suspend fun getAll() = tripDao.loadAll()
    suspend fun getAfter(tripId: Long) = tripDao.loadAfter(tripId)

    fun observeDateRange(start: Long, end: Long) = tripDao.subscribeDateRange(start, end)

    fun observeRealTrips(): LiveData<Array<Trip>> {
        return tripDao.subscribeRealTrips()
    }

    suspend fun getCleanupTrips(): Array<Trip> {
        return tripDao.getCleanupTrips()
    }

    fun createNewTrip(bikeId: Long = 1): Long {
        return tripDao.save(Trip(bikeId = bikeId))
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
    suspend fun updateTripStuff(stuff: TripStuff) = with(tripDao) {
        updateStuff(stuff)
        getGoogleFitUnsynced()
    }

    suspend fun updateWheelCircumference(circ: TripWheelCircumference) =
        with(tripDao) { updateCircumference(circ) }

    suspend fun updateBiometrics(biometrics: Biometrics) {
        tripDao.updateBiometrics(biometrics)
    }

    suspend fun updateBikeId(id: Long, bikeId: Long) = tripDao.updateBikeId(id, bikeId)

    suspend fun getDefaultBiometrics(tripId: Long) = tripDao.getDefaultBiometrics(tripId)

    suspend fun setGoogleFitSyncStatus(
        tripId: Long,
        googleFitSyncStatus: GoogleFitSyncStatusEnum,
    ) =
        tripDao.updateGoogleFitSyncStatus(
            TripGoogleFitSync(
                id = tripId,
                googleFitSyncStatus = googleFitSyncStatus
            )
        )

    suspend fun getGoogleFitUnsynced() = tripDao.loadGoogleFitUnsyncedTrips()
    suspend fun getGoogleFitDirty() = tripDao.loadGoogleFitDirtyTrips()

    fun longestTrips(limit: Int = 10) = tripDao.longestTrips(limit)
    fun mostPopularDistances(conversionFactor: Double, timestamp: Long = 0, limit: Int = 3) =
        tripDao.getMostPopularDistances(conversionFactor, timestamp, limit)

    fun observeTripTotals(start: Long, end: Long) = tripDao.subscribeTotals(start, end)
    fun observeTripTotals() = tripDao.subscribeTotals()
    fun observeMonthlyTotals(limit: Int) = tripDao.subscribeMonthlyTotals(limit)
    fun observeWeeklyTotals(limit: Int) = tripDao.subscribeWeeklyTotals(limit)
    fun getTripsForBike(bikeId: Long) = tripDao.getTripsForBike(bikeId)
    fun observeBikeTotals() = tripDao.subscribeBikeTotals()
}