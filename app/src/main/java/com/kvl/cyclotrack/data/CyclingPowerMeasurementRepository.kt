package com.kvl.cyclotrack.data

import com.kvl.cyclotrack.PowerData
import com.kvl.cyclotrack.util.SystemUtils
import javax.inject.Inject

class CyclingPowerMeasurementRepository @Inject constructor(private val cyclingPowerMeasurementDao: CyclingPowerMeasurementDao) {
    suspend fun save(new: CyclingPowerMeasurement) = cyclingPowerMeasurementDao.save(new)
    suspend fun insertPowerData(tripId: Long, new: PowerData) = cyclingPowerMeasurementDao.save(
        CyclingPowerMeasurement(
            tripId = tripId,
            timestamp = new.timestamp ?: SystemUtils.currentTimeMillis(),
            instantaneousPower = new.instantaneousPower!!,
            pedalPowerBalance = new.pedalPowerBalance,
            accumulatedTorque = new.accumulatedTorque,
            cumulativeWheelRevolutions = new.cumulativeWheelRevolutions,
            lastWheelEventTime = new.lastWheelEventTime,
            cumulativeCrankRevolutions = new.cumulativeCrankRevolutions,
            lastCrankEventTime = new.lastCrankEventTime,
            maximumForceMagnitude = new.maximumForceMagnitude,
            minimumForceMagnitude = new.minimumForceMagnitude,
            maximumTorqueMagnitude = new.maximumTorqueMagnitude,
            minimumTorqueMagnitude = new.minimumTorqueMagnitude,
            maximumAngle = new.maximumAngle,
            minimumAngle = new.minimumAngle,
            topDeadSpotAngle = new.topDeadSpotAngle,
            bottomDeadSpotAngle = new.bottomDeadSpotAngle,
            accumulatedEnergy = new.accumulatedEnergy,
            offsetCompensation = new.offsetCompensation,
            rpm = new.rpm,
            wheelRpm = new.wheelRpm
        )
    )

    fun observeTripPowerMeasurements(tripId: Long) = cyclingPowerMeasurementDao.subscribe(tripId)
    fun observeTripCadence(tripId: Long) = cyclingPowerMeasurementDao.subscribeCadence(tripId)
    suspend fun getPowerMeasurements(tripId: Long) = cyclingPowerMeasurementDao.load(tripId)
}
