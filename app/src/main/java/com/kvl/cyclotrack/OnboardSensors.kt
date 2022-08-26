package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
data class OnboardSensors(
    val tripId: Long,
    val timestamp: Long = SystemUtils.currentTimeMillis(),
    val gravityX: Float?,
    val gravityY: Float?,
    val gravityZ: Float?,
    val gyroscopeX: Float?,
    val gyroscopeY: Float?,
    val gyroscopeZ: Float?,
    val pressure: Float?,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
) {
    constructor(tripId: Long, sensorData: SensorModel) : this(
        tripId = tripId,
        gravityX = sensorData.gravity?.values?.get(0),
        gravityY = sensorData.gravity?.values?.get(1),
        gravityZ = sensorData.gravity?.values?.get(2),
        gyroscopeX = sensorData.gyroscope?.values?.get(0),
        gyroscopeY = sensorData.gyroscope?.values?.get(1),
        gyroscopeZ = sensorData.gyroscope?.values?.get(2),
        pressure = sensorData.pressure?.values?.get(0),
    )
}