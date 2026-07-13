package com.kvl.cyclotrack

import javax.inject.Inject

class MeasurementsRepository @Inject constructor(private val measurementsDao: MeasurementsDao) {
    suspend fun get(tripId: Long) = measurementsDao.load(tripId)

    // ponytail: modulo-on-id sampling; even spacing is close enough for a map thumbnail
    suspend fun getDecimated(tripId: Long, target: Long = 500): Array<Measurements> {
        val factor = (measurementsDao.count(tripId) / target).coerceAtLeast(1)
        return measurementsDao.loadDecimated(tripId, factor)
    }

    suspend fun getLatest(tripId: Long) = measurementsDao.loadLatest(tripId)
    suspend fun getLatest(tripId: Long, count: Int) = measurementsDao.loadLatestCount(tripId, count)
    suspend fun getLatestAccurate(
        tripId: Long,
        accuracyThreshold: Float,
        minTime: Long,
        maxTime: Long
    ) =
        measurementsDao.loadLatestAccurate(tripId, accuracyThreshold, minTime, maxTime)

    fun observe(tripId: Long) = measurementsDao.subscribe(tripId)

    suspend fun insertMeasurements(measurements: Measurements) = measurementsDao.save(measurements)

    fun observeLatest(tripId: Long) = measurementsDao.subscribeLatest(tripId)

    fun update(measurements: Measurements) = measurementsDao.update(measurements)

    suspend fun changeTrip(tripId: Long, newTripId: Long) =
        measurementsDao.changeTrip(tripId, newTripId)

    //fun observeCritical(tripId: Long) = measurementsDao.subscribeCritical(tripId)
    //suspend fun getCritical(tripId: Long) = measurementsDao.loadCritical(tripId)
}
