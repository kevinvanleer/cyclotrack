package com.kvl.cyclotrack

import javax.inject.Inject

class OnboardSensorsRepository @Inject constructor(private val onboardSensorsDao: OnboardSensorsDao) {
    suspend fun insertMeasurements(tripId: Long, measurements: SensorModel) =
        onboardSensorsDao.save(OnboardSensors(tripId, measurements))
}