package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import javax.inject.Inject

class MeasurementsRepository @Inject constructor(private val measurementsDao: MeasurementsDao) {
    fun getTripMeasurements(tripId: Long): LiveData<Array<Measurements>> {
        return measurementsDao.load(tripId)
    }

    fun getTripCriticalMeasurements(tripId: Long): LiveData<Array<CriticalMeasurements>> {
        return measurementsDao.loadDetails(tripId)
    }

    suspend fun insertMeasurements(measurements: Measurements): Long {
        return measurementsDao.save(measurements)
    }

    fun getLatestMeasurements(tripId: Long): LiveData<Measurements> {
        return measurementsDao.getLastMeasurement(tripId)
    }
}