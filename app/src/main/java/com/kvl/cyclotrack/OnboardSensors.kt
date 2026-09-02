package com.kvl.cyclotrack

import com.kvl.cyclotrack.util.SystemUtils

// No longer a Room entity: the OnboardSensors table is dropped in migration
// 33->34. This shape survives only as the input to OnboardSensorsPivot while
// that migration drains version-skippers' legacy rows.
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
    val accelerometerX: Float? = null,
    val accelerometerY: Float? = null,
    val accelerometerZ: Float? = null,
    val id: Long? = null,
)
