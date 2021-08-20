package com.kvl.cyclotrack.data

data class DerivedTripState constructor(
    val tripId: Long = -1L,
    val timestamp: Long = 0L,
    val duration: Double = 0.0,
    val durationDelta: Double = 0.0,
    val totalDistance: Double = 0.0,
    val distanceDelta: Double = 0.0,
    val altitude: Double = 0.0,
    val altitudeDelta: Double = 0.0,
    val circumference: Double = 0.0,
    val revTotal: Int = 0,
    val slope: Double = 0.0,
    val speedRevolutions: Int? = null,
)
