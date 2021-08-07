package com.kvl.cyclotrack

data class TripProgress(
    val measurements: Measurements?,
    val accuracy: Float,
    val bearing: Float,
    val speed: Float,
    val maxSpeed: Float,
    val acceleration: Float,
    val maxAcceleration: Float,
    val distance: Double,
    val slope: Double,
    val duration: Double,
    val tracking: Boolean,
)
