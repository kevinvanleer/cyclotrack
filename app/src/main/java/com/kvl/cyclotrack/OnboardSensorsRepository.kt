package com.kvl.cyclotrack

import javax.inject.Inject

class OnboardSensorsRepository @Inject constructor(private val onboardSensorsDao: OnboardSensorsDao) {
    suspend fun insertMeasurements(tripId: Long, measurements: SensorModel) =
        onboardSensorsDao.save(OnboardSensors(tripId, measurements))

    fun update(measurements: OnboardSensors) = onboardSensorsDao.update(measurements)

    suspend fun getDecimated(tripId: Long) = onboardSensorsDao.loadDecimated(tripId)

    fun observeDecimated(tripId: Long) = onboardSensorsDao.subscribeDecimated(tripId)

    suspend fun get(tripId: Long) = onboardSensorsDao.load(tripId)

    fun observe(tripId: Long) = onboardSensorsDao.subscribe(tripId)

    suspend fun changeTrip(tripId: Long, newTripId: Long) =
        onboardSensorsDao.changeTrip(tripId, newTripId)
}