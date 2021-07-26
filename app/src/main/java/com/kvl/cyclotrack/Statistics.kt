package com.kvl.cyclotrack

import android.util.Log

fun exponentialSmoothing(alpha: Double, current: Double, last: Double) =
    (alpha * current) + ((1 - alpha) * last)

fun doubleExponentialSmoothing(
    alpha: Double,
    current: Double,
    smoothLast: Double,
    trendLast: Double,
) =
    alpha * current + ((1 - alpha) * (smoothLast + trendLast))

fun doubleExponentialSmoothingTrend(
    beta: Double,
    smooth: Double,
    smoothLast: Double,
    trendLast: Double,
) =
    beta * (smooth - smoothLast) + (1 - beta) * trendLast

fun smooth(alpha: Double, data: Array<Pair<Double, Double>>): List<Pair<Double, Double>> {
    var smoothedFirst = data[0].first
    var smoothedSecond = data[0].second
    return data.map { datum ->
        smoothedFirst = exponentialSmoothing(alpha, datum.first, smoothedFirst)
        smoothedSecond = exponentialSmoothing(alpha, datum.second, smoothedSecond)
        Pair(smoothedFirst!!, smoothedSecond!!)
    }
}

fun doubleSmooth(alpha: Double, beta: Double, data: Array<Double>): List<Double> {
    var smoothed: Double = data[0]
    var trend: Double = data[1] - data[0]
    var smoothedLast = smoothed
    return data.map { datum ->
        smoothed = doubleExponentialSmoothing(alpha, datum, smoothedLast, trend)
        trend = doubleExponentialSmoothingTrend(beta, smoothed, smoothedLast, trend)
        smoothedLast = smoothed
        smoothed
    }
}

fun accumulateAscentDescent(elevationData: List<Pair<Double, Double>>): Pair<Double, Double> {
    val logTag = "accumulateAscentDescent"
    var totalAscent = 0.0
    var totalDescent = 0.0
    var lastAltitudeTurningPoint = elevationData[0].first
    var altitudeCursor = lastAltitudeTurningPoint
    var ascending = false
    var descending = false
    elevationData.forEach { pair ->
        val sample = pair.first
        val threshold = pair.second * 2

        if (!descending && (sample - lastAltitudeTurningPoint > threshold)) ascending = true
        if (!ascending && (lastAltitudeTurningPoint - sample > threshold)) descending = true
        //descending
        if (descending) {
            if (sample < altitudeCursor) altitudeCursor = sample
            if (sample > altitudeCursor + threshold) {
                Log.d(logTag,
                    "Accumulating descent ${altitudeCursor} - ${lastAltitudeTurningPoint} =  ${altitudeCursor - lastAltitudeTurningPoint}")
                totalDescent += altitudeCursor - lastAltitudeTurningPoint
                lastAltitudeTurningPoint = altitudeCursor
                Log.d(logTag, "Total descent = ${totalDescent}")
                altitudeCursor = sample
                descending = false
            }
        }
        //ascending
        if (ascending) {
            if (sample > altitudeCursor) altitudeCursor = sample
            if (sample < altitudeCursor - threshold) {
                Log.d(logTag,
                    "Accumulating ascent ${altitudeCursor} - ${lastAltitudeTurningPoint} =  ${
                        altitudeCursor - lastAltitudeTurningPoint
                    }")
                totalAscent += altitudeCursor - lastAltitudeTurningPoint
                lastAltitudeTurningPoint = altitudeCursor
                Log.d(logTag, "Total ascent = ${totalAscent}")
                altitudeCursor = sample
                ascending = false
            }
        }
    }
    if (altitudeCursor < lastAltitudeTurningPoint) totalDescent += altitudeCursor - lastAltitudeTurningPoint
    if (altitudeCursor > lastAltitudeTurningPoint) totalAscent += altitudeCursor - lastAltitudeTurningPoint
    return Pair(totalAscent, totalDescent)
}