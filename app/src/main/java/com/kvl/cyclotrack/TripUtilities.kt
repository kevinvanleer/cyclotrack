package com.kvl.cyclotrack

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


fun formatDuration(value: Double): String {
    var formattedString = ""
    if (value < 1.0) {
        formattedString += "zero seconds"
    } else if (value < 60) {
        formattedString += "${value.roundToInt()} sec"
    } else if (value < 3600) {
        val minutes = value / 60
        val minutePart = minutes.toLong()
        val seconds = (minutes - minutePart) * 60
        val secondPart = seconds.toLong()
        formattedString += "${minutePart}m ${secondPart}s"
    } else {
        val hours = value / 3600
        val hourPart = hours.toLong()
        val minutes = (hours - hourPart) * 60
        val minutePart = minutes.toLong()
        val seconds = (minutes - minutePart) * 60
        val secondPart = seconds.roundToInt()
        formattedString += "${hourPart}h ${minutePart}m ${secondPart}s"
    }
    return formattedString
}

fun plotPath(measurements: Array<Measurements>): MapPath {
    val path = PolylineOptions()
    var northeastLat = -91.0
    var northeastLng = -181.0
    var southwestLat = 91.0
    var southwestLng = 181.0

    var totalDistance = 0.0
    var lastLat = 0.0
    var lastLng = 0.0

    var maxSpeedAccuracy = 0f
    var accSpeedAccuracy = 0f
    var sampleCount = 0

    measurements.forEach {
        if (it.accuracy < 5) {
            if (lastLat != 0.0 && lastLng != 0.0) {
                var distanceArray = floatArrayOf(0f)
                Location.distanceBetween(lastLat,
                    lastLng,
                    it.latitude,
                    it.longitude,
                    distanceArray)
                totalDistance += distanceArray[0]
            }
            lastLat = it.latitude
            lastLng = it.longitude
            path.add(LatLng(it.latitude, it.longitude))
            northeastLat = max(northeastLat, it.latitude)
            northeastLng = max(northeastLng, it.longitude)
            southwestLat = min(southwestLat, it.latitude)
            southwestLng = min(southwestLng, it.longitude)
            maxSpeedAccuracy = max(maxSpeedAccuracy, it.speedAccuracyMetersPerSecond)
            accSpeedAccuracy += it.speedAccuracyMetersPerSecond
            sampleCount++
        }
    }
    Log.d("TRIP_SUMMARIES_ADAPTER",
        "distance=${totalDistance}, maxSpeedAcc=${maxSpeedAccuracy}, avgSpeedAcc=${accSpeedAccuracy / sampleCount}")
    var bounds: LatLngBounds? = null
    try {
        bounds =
            LatLngBounds(LatLng(southwestLat, southwestLng), LatLng(northeastLat, northeastLng))
    } catch (err: IllegalArgumentException) {
        if (measurements.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            var included = false

            measurements.forEach {
                if (it.accuracy < 5) {
                    boundsBuilder.include(LatLng(it.latitude, it.longitude))
                    included = true
                }
            }
            if (included) bounds = boundsBuilder.build()
        }
        Log.d("PLOT_PATH",
            String.format("Bounds could not be calculated: path size = %d", measurements.size))
    }

    return MapPath(path, bounds)
}

const val METERS_TO_FEET = 3.28084
const val FEET_TO_MILES = 1.0 / 5280
const val SECONDS_TO_HOURS = 1.0 / 3600
fun getUserSpeed(meters: Double, seconds: Double): Double {
    val userConversionFactor = METERS_TO_FEET * FEET_TO_MILES / SECONDS_TO_HOURS
    return meters / seconds * userConversionFactor
}

fun getUserDistance(meters: Double): Double {
    val userConversionFactor = METERS_TO_FEET * FEET_TO_MILES
    return meters * userConversionFactor
}

fun crossedSplitThreshold(
    newDistance: Double,
    oldDistance: Double,
) = floor(newDistance * 0.000621371) > floor(oldDistance
        * 0.000621371)

data class MapPath(val path: PolylineOptions, val bounds: LatLngBounds?)
