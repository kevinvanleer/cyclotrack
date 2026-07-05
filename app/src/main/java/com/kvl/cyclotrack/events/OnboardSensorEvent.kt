package com.kvl.cyclotrack.events

import com.kvl.cyclotrack.data.OnboardSensorMeasurement
import com.kvl.cyclotrack.data.OnboardSensorType

sealed interface OnboardSensorEvent {
    val timestamp: Long

    fun toMeasurement(tripId: Long): OnboardSensorMeasurement

    data class Gravity(
        val x: Float,
        val y: Float,
        val z: Float,
        override val timestamp: Long,
    ) : OnboardSensorEvent {
        override fun toMeasurement(tripId: Long) =
            OnboardSensorMeasurement(tripId, timestamp, OnboardSensorType.GRAVITY, x, y, z)
    }

    data class Gyroscope(
        val x: Float,
        val y: Float,
        val z: Float,
        override val timestamp: Long,
    ) : OnboardSensorEvent {
        override fun toMeasurement(tripId: Long) =
            OnboardSensorMeasurement(tripId, timestamp, OnboardSensorType.GYROSCOPE, x, y, z)
    }

    data class Accelerometer(
        val x: Float,
        val y: Float,
        val z: Float,
        override val timestamp: Long,
    ) : OnboardSensorEvent {
        override fun toMeasurement(tripId: Long) =
            OnboardSensorMeasurement(tripId, timestamp, OnboardSensorType.ACCELEROMETER, x, y, z)
    }

    data class Pressure(
        val pressure: Float,
        override val timestamp: Long,
    ) : OnboardSensorEvent {
        override fun toMeasurement(tripId: Long) =
            OnboardSensorMeasurement(tripId, timestamp, OnboardSensorType.PRESSURE, pressure)
    }
}
