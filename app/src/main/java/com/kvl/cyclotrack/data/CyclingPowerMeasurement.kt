package com.kvl.cyclotrack.data

import androidx.annotation.Keep
import androidx.room.*
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.util.SystemUtils

@Entity(
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("tripId"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["tripId"])]
)

@Keep
data class CyclingPowerMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val tripId: Long,
    val timestamp: Long = SystemUtils.currentTimeMillis(),
    val instantaneousPower: Int,
    val pedalPowerBalance: Float? = null,
    val accumulatedTorque: Float? = null,
    val cumulativeWheelRevolutions: Int? = null,
    val lastWheelEventTime: Int? = null,
    val cumulativeCrankRevolutions: Int? = null,
    val lastCrankEventTime: Int? = null,
    val maximumForceMagnitude: Int? = null,
    val minimumForceMagnitude: Int? = null,
    val maximumTorqueMagnitude: Float? = null,
    val minimumTorqueMagnitude: Float? = null,
    val maximumAngle: Int? = null,
    val minimumAngle: Int? = null,
    val topDeadSpotAngle: Int? = null,
    val bottomDeadSpotAngle: Int? = null,
    val accumulatedEnergy: Int? = null,
    val offsetCompensation: Boolean? = null,
    val rpm: Float? = null,
    val wheelRpm: Float? = null,
)

fun powerToCadence(power: Array<CyclingPowerMeasurement>): Array<CadenceSpeedMeasurement> {
    return power.map {
        CadenceSpeedMeasurement(
            tripId = it.tripId,
            timestamp = it.timestamp, rpm = it.rpm,
            revolutions = it.cumulativeCrankRevolutions ?: 0,
            lastEvent = it.lastCrankEventTime ?: 0,
            sensorType = SensorType.CADENCE,
            id = it.id
        )
    }.toTypedArray()
}
