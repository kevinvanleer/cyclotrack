package com.kvl.cyclotrack.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.kvl.cyclotrack.OnboardSensors
import com.kvl.cyclotrack.Trip

enum class OnboardSensorType(val value: Int) {
    GRAVITY(0),
    GYROSCOPE(1),
    ACCELEROMETER(2),
    PRESSURE(3),
}

class OnboardSensorTypeConverter {
    @TypeConverter
    fun fromOnboardSensorType(value: OnboardSensorType): Int = value.value

    @TypeConverter
    fun toOnboardSensorType(value: Int): OnboardSensorType =
        OnboardSensorType.entries.first { it.value == value }
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
data class OnboardSensorMeasurement(
    val tripId: Long,
    val timestamp: Long,
    val sensorType: OnboardSensorType,
    val v0: Float,
    val v1: Float? = null,
    val v2: Float? = null,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)

fun reconstructSnapshots(measurements: List<OnboardSensorMeasurement>): Array<OnboardSensors> {
    val last = arrayOfNulls<OnboardSensorMeasurement>(OnboardSensorType.entries.size)
    return measurements.map { m ->
        last[m.sensorType.value] = m
        OnboardSensors(
            tripId = m.tripId,
            timestamp = m.timestamp,
            gravityX = last[OnboardSensorType.GRAVITY.value]?.v0,
            gravityY = last[OnboardSensorType.GRAVITY.value]?.v1,
            gravityZ = last[OnboardSensorType.GRAVITY.value]?.v2,
            gyroscopeX = last[OnboardSensorType.GYROSCOPE.value]?.v0,
            gyroscopeY = last[OnboardSensorType.GYROSCOPE.value]?.v1,
            gyroscopeZ = last[OnboardSensorType.GYROSCOPE.value]?.v2,
            accelerometerX = last[OnboardSensorType.ACCELEROMETER.value]?.v0,
            accelerometerY = last[OnboardSensorType.ACCELEROMETER.value]?.v1,
            accelerometerZ = last[OnboardSensorType.ACCELEROMETER.value]?.v2,
            pressure = last[OnboardSensorType.PRESSURE.value]?.v0,
            id = m.id,
        )
    }.toTypedArray()
}

class OnboardSensorsPivot {
    private val last = arrayOfNulls<List<Float?>>(OnboardSensorType.entries.size)

    var lastId: Long = 0
        private set

    fun pivot(rows: List<OnboardSensors>): List<OnboardSensorMeasurement> {
        val out = ArrayList<OnboardSensorMeasurement>(rows.size)
        rows.forEach { row ->
            row.id?.let { lastId = maxOf(lastId, it) }
            emit(out, row, OnboardSensorType.GRAVITY, row.gravityX, row.gravityY, row.gravityZ)
            emit(
                out,
                row,
                OnboardSensorType.GYROSCOPE,
                row.gyroscopeX,
                row.gyroscopeY,
                row.gyroscopeZ
            )
            emit(
                out,
                row,
                OnboardSensorType.ACCELEROMETER,
                row.accelerometerX,
                row.accelerometerY,
                row.accelerometerZ
            )
            emit(out, row, OnboardSensorType.PRESSURE, row.pressure)
        }
        return out
    }

    private fun emit(
        out: MutableList<OnboardSensorMeasurement>,
        row: OnboardSensors,
        sensorType: OnboardSensorType,
        v0: Float?,
        v1: Float? = null,
        v2: Float? = null,
    ) {
        if (v0 == null) return
        val values = listOf(v0, v1, v2)
        if (last[sensorType.value] == values) return
        last[sensorType.value] = values
        out.add(
            OnboardSensorMeasurement(
                tripId = row.tripId,
                timestamp = row.timestamp,
                sensorType = sensorType,
                v0 = v0,
                v1 = v1,
                v2 = v2,
            )
        )
    }
}
