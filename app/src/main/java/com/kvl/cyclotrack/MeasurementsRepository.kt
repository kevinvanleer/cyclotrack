package com.kvl.cyclotrack

import javax.inject.Inject

class MeasurementsRepository @Inject constructor(private val measurementsDao: MeasurementsDao) {
    suspend fun get(tripId: Long) = measurementsDao.load(tripId)
    suspend fun getLatest(tripId: Long) = measurementsDao.loadLatest(tripId)
    suspend fun getLatestAccurate(tripId: Long, accuracyThreshold: Float) =
        measurementsDao.loadLatestAccurate(tripId, accuracyThreshold)

    fun observe(tripId: Long) = measurementsDao.subscribe(tripId)

    fun observeCritical(tripId: Long) = measurementsDao.subscribeCritical(tripId)
    suspend fun getCritical(tripId: Long) = measurementsDao.loadCritical(tripId)

    suspend fun insertMeasurements(measurements: Measurements) = measurementsDao.save(measurements)

    fun observeLatest(tripId: Long) = measurementsDao.subscribeLatest(tripId)

    fun update(measurements: Measurements) = measurementsDao.update(measurements)

    suspend fun changeTrip(tripId: Long, newTripId: Long) =
        measurementsDao.changeTrip(tripId, newTripId)
}