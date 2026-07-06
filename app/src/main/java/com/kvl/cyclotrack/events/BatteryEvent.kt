package com.kvl.cyclotrack.events

import com.kvl.cyclotrack.data.OnboardSensorMeasurement
import com.kvl.cyclotrack.data.OnboardSensorType
import com.kvl.cyclotrack.util.celsiusToKelvin

sealed interface BatteryEvent : OnboardSensorEvent {

    data class Temperature(
        val celsius: Float,
        override val timestamp: Long,
    ) : BatteryEvent {
        // stored in kelvin: DB convention is SI, matching Weather.temperature
        override fun toMeasurement(tripId: Long) = OnboardSensorMeasurement(
            tripId, timestamp, OnboardSensorType.BATTERY_TEMPERATURE, celsiusToKelvin(celsius)
        )
    }

    data class Level(
        val percent: Float,
        override val timestamp: Long,
    ) : BatteryEvent {
        override fun toMeasurement(tripId: Long) = OnboardSensorMeasurement(
            tripId, timestamp, OnboardSensorType.BATTERY_LEVEL, percent
        )
    }

    data class ThermalStatus(
        val status: Int,
        override val timestamp: Long,
    ) : BatteryEvent {
        override fun toMeasurement(tripId: Long) = OnboardSensorMeasurement(
            tripId, timestamp, OnboardSensorType.THERMAL_STATUS, status.toFloat()
        )
    }
}
