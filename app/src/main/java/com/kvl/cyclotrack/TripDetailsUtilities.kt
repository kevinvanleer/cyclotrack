package com.kvl.cyclotrack

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurement
import kotlin.math.roundToInt

fun getElevationChange(measurements: Array<Measurements>): Pair<Double, Double> =
    accumulateAscentDescent(measurements.map {
        Pair(
            it.altitude,
            it.verticalAccuracyMeters.toDouble()
        )
    })

fun getAverageSpeedRpm(measurements: Array<CadenceSpeedMeasurement>): Float? {
    return try {
        val totalRevs = measurements.lastOrNull()?.revolutions?.let {
            getDifferenceRollover(
                it,
                measurements.first().revolutions
            )
        }
        val duration =
            (measurements.last().timestamp - measurements.first().timestamp) / 1000 / 60

        totalRevs?.toFloat()?.div(duration).takeIf { it?.isFinite() ?: false }
    } catch (e: Exception) {
        null
    }
}

fun getRotation(start: LatLng, end: LatLng): Float {
    val results = FloatArray(2)
    Location.distanceBetween(
        start.latitude,
        start.longitude,
        end.latitude,
        end.longitude,
        results
    )
    return results[1]
}

fun getAverageHeartRate(measurements: Array<HeartRateMeasurement>): Short? {
    var sum = 0f
    var count = 0
    measurements.forEach {
        sum += it.heartRate
        ++count
    }
    return if (count == 0) {
        null
    } else {
        (sum / count).roundToInt().toShort()
    }
}
