package com.kvl.cyclotrack.data

import javax.inject.Inject

class CadenceSpeedMeasurementRepository @Inject constructor(private val cadenceSpeedMeasurementDao: CadenceSpeedMeasurementDao) {
    suspend fun save(new: CadenceSpeedMeasurement) = cadenceSpeedMeasurementDao.save(new)
    fun observeTripSpeed(tripId: Long) = cadenceSpeedMeasurementDao.subscribeSpeed(tripId)
    fun observeTripCadence(tripId: Long) = cadenceSpeedMeasurementDao.subscribeCadence(tripId)
    suspend fun getSpeedMeasurements(tripId: Long) = cadenceSpeedMeasurementDao.loadSpeed(tripId)
    suspend fun getCadenceMeasurements(tripId: Long) =
        cadenceSpeedMeasurementDao.loadCadence(tripId)

    suspend fun changeTrip(tripId: Long, newTripId: Long) =
        cadenceSpeedMeasurementDao.changeTrip(tripId, newTripId)

    suspend fun getAutoTimeStates(
        tripId: Long,
        referenceTime: Long = System.currentTimeMillis(),
        rpmThreshold: Float = 50f,
        pauseThreshold: Long = 10000,
        resumeThreshold: Long? = null,
    ) = cadenceSpeedMeasurementDao.getAutoTimeStates(
        tripId = tripId,
        referenceTime = referenceTime,
        rpmThreshold = rpmThreshold,
        pauseThreshold = pauseThreshold,
        resumeThreshold = resumeThreshold ?: pauseThreshold,
    )
}
