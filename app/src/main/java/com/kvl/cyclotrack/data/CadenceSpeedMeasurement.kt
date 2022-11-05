package com.kvl.cyclotrack.data

import androidx.annotation.Keep
import androidx.room.*
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.util.SystemUtils

enum class SensorType(val value: Int) {
    SPEED(1),
    CADENCE(2),
}

class SensorTypeConverter {
    @TypeConverter
    fun fromSensorType(value: SensorType): Int {
        return value.value
    }

    @TypeConverter
    fun toSensorType(value: Int): SensorType {
        return when (value) {
            1 -> SensorType.SPEED
            2 -> SensorType.CADENCE
            else -> SensorType.SPEED
        }
    }
}


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
data class CadenceSpeedMeasurement(
    val tripId: Long,
    val timestamp: Long = SystemUtils.currentTimeMillis(),
    val revolutions: Int,
    val lastEvent: Int,
    val rpm: Float?,
    val sensorType: SensorType,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)
