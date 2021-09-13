package com.kvl.cyclotrack

import android.location.Location

data class LocationData(
    val accuracy: Float,
    val altitude: Double,
    val bearing: Float,
    val elapsedRealtimeNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val time: Long,
    var bearingAccuracyDegrees: Float? = null,
    var elapsedRealtimeUncertaintyNanos: Double? = null,
    var speedAccuracyMetersPerSecond: Float? = null,
    var verticalAccuracyMeters: Float? = null,
) {
    constructor(location: Location) : this(location.accuracy,
        location.altitude,
        location.bearing,
        location.elapsedRealtimeNanos,
        location.latitude,
        location.longitude,
        location.speed,
        location.time) {

        this.bearingAccuracyDegrees = location.bearingAccuracyDegrees
        this.speedAccuracyMetersPerSecond = location.speedAccuracyMetersPerSecond
        this.verticalAccuracyMeters = location.verticalAccuracyMeters

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            this.elapsedRealtimeUncertaintyNanos = location.elapsedRealtimeUncertaintyNanos
        }
    }
}