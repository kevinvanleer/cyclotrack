package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.OnboardSensorMeasurement
import com.kvl.cyclotrack.data.OnboardSensorMeasurementDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardSensorsRepository @Inject constructor(
    private val measurementDao: OnboardSensorMeasurementDao,
) {
    suspend fun insertSensorMeasurements(measurements: List<OnboardSensorMeasurement>) =
        measurementDao.save(measurements)

    suspend fun get(tripId: Long): Array<OnboardSensorMeasurement> =
        measurementDao.load(tripId).toTypedArray()

    suspend fun changeTrip(tripId: Long, newTripId: Long) =
        measurementDao.changeTrip(tripId, newTripId)
}
