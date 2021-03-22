package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(foreignKeys = [ForeignKey(entity = Trip::class,
    parentColumns = arrayOf("id"),
    childColumns = arrayOf("tripId"),
    onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tripId"])])
@Keep
data class OnboardSensors(
    val tripId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val accelerometerX: Float?,
    val accelerometerY: Float?,
    val accelerometerZ: Float?,
    val accelerometerAverageX: Float?,
    val accelerometerAverageY: Float?,
    val accelerometerAverageZ: Float?,
    val gyroscopeAverageX: Float?,
    val gyroscopeAverageY: Float?,
    val gyroscopeAverageZ: Float?,
    val gyroscopeX: Float?,
    val gyroscopeY: Float?,
    val gyroscopeZ: Float?,
    val tiltX: Float?,
    val tiltY: Float?,
    val tiltZ: Float?,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
) {
    constructor(tripId: Long, sensorData: SensorModel) : this(
        tripId = tripId,
        accelerometerX = sensorData.accelerometer?.values?.get(0),
        accelerometerY = sensorData.accelerometer?.values?.get(1),
        accelerometerZ = sensorData.accelerometer?.values?.get(2),
        accelerometerAverageX = sensorData.accelerometerAverage?.get(0),
        accelerometerAverageY = sensorData.accelerometerAverage?.get(1),
        accelerometerAverageZ = sensorData.accelerometerAverage?.get(2),
        gyroscopeX = sensorData.gyroscope?.values?.get(0),
        gyroscopeY = sensorData.gyroscope?.values?.get(1),
        gyroscopeZ = sensorData.gyroscope?.values?.get(2),
        gyroscopeAverageX = sensorData.gyroscopeAverage?.get(0),
        gyroscopeAverageY = sensorData.gyroscopeAverage?.get(1),
        gyroscopeAverageZ = sensorData.gyroscopeAverage?.get(2),
        tiltX = sensorData.tilt?.get(0),
        tiltY = sensorData.tilt?.get(1),
        tiltZ = sensorData.tilt?.get(2),
    )
}