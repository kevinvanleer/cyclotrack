package com.kvl.cyclotrack

import android.util.Log
import kotlin.math.abs
import kotlin.math.pow

fun List<Double>.average(): Double =
    this.reduce { acc, d -> acc + d } / this.size

fun average(newValue: Double, sampleSize: Int, lastAverage: Double): Double =
    (lastAverage * (sampleSize - 1) + newValue) / sampleSize

fun List<Double>.sampleVariance(): Double {
    val avg = this.average()
    return this.fold(0.0, { acc, d -> acc + (d - avg).pow(2.0) }) / (this.size - 1)
}

fun sampleVariance(
    newValue: Double,
    oldVariance: Double,
    sampleSize: Int,
    oldAverage: Double,
): Double {
    return (oldVariance * (sampleSize - 2) / (sampleSize - 1)) + ((newValue - oldAverage).pow(
        2.0) / sampleSize)
}

fun List<Double>.populationVariance(): Double {
    val avg = this.average()
    return this.fold(0.0, { acc, d -> acc + (d - avg).pow(2.0) }) / this.size
}

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

fun smooth(alpha: Double, data: Array<Double>): List<Double> {
    var smoothed = data[0]
    return data.map { datum ->
        smoothed = exponentialSmoothing(alpha, datum, smoothed)
        smoothed
    }
}

fun smooth(alpha: Double, data: Array<Pair<Double, Double>>): List<Pair<Double, Double>> {
    var smoothedFirst = data[0].first
    var smoothedSecond = data[0].second
    return data.map { datum ->
        smoothedFirst = exponentialSmoothing(alpha, datum.first, smoothedFirst)
        //smoothedSecond = exponentialSmoothing(alpha, datum.second, smoothedSecond)
        smoothedSecond = datum.second
        Pair(smoothedFirst, smoothedSecond)
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

fun isRangeGreaterThan(left: Pair<Double, Double>, right: Double): Boolean {
    val leftRange = Pair(left.first - left.second, left.first + left.second)
    return leftRange.first > right
}

fun isRangeLessThan(left: Pair<Double, Double>, right: Double): Boolean {
    val leftRange = Pair(left.first - left.second, left.first + left.second)
    return leftRange.second < right
}

fun isRangeGreaterThan(left: Pair<Double, Double>, right: Pair<Double, Double>): Boolean {
    val factor = 1.0
    val leftRange = Pair(left.first - left.second / factor, left.first + left.second / factor)
    val rightRange = Pair(right.first - right.second / factor, right.first + right.second / factor)
    return leftRange.first > rightRange.second
}

fun isRangeLessThan(left: Pair<Double, Double>, right: Pair<Double, Double>): Boolean {
    val factor = 1.0
    val leftRange = Pair(left.first - left.second / factor, left.first + left.second / factor)
    val rightRange = Pair(right.first - right.second / factor, right.first + right.second / factor)
    return leftRange.second < rightRange.first
}

fun accumulateAscentDescent_kvlRange(
    elevationData: List<Pair<Double, Double>>,
    threshold: Double,
): Pair<Double, Double> {
    val logTag = "accumulateAscentDescent"
    var totalAscent = 0.0
    var totalDescent = 0.0
    var lastAltitudeTurningPoint = elevationData[0]
    var altitudeCursor = lastAltitudeTurningPoint
    var ascending = false
    var descending = false
    elevationData.forEach { sample ->
        if (!descending && isRangeGreaterThan(sample, lastAltitudeTurningPoint)) ascending = true
        if (!ascending && isRangeLessThan(lastAltitudeTurningPoint, sample)) descending = true
        //descending
        if (descending) {
            if (isRangeLessThan(sample, altitudeCursor)) altitudeCursor = sample
            if (isRangeGreaterThan(sample, altitudeCursor)) {
                Log.d(logTag,
                    "Accumulating descent ${altitudeCursor.first} - ${lastAltitudeTurningPoint.first} =  ${altitudeCursor.first - lastAltitudeTurningPoint.first}")
                totalDescent += altitudeCursor.first - lastAltitudeTurningPoint.first
                lastAltitudeTurningPoint = altitudeCursor
                Log.d(logTag, "Total descent = ${totalDescent}")
                altitudeCursor = sample
                descending = false
            }
        }
        //ascending
        if (ascending) {
            if (isRangeGreaterThan(sample, altitudeCursor)) altitudeCursor = sample
            if (isRangeLessThan(sample, altitudeCursor)) {
                Log.d(logTag,
                    "Accumulating ascent ${altitudeCursor.first} - ${lastAltitudeTurningPoint.first} =  ${
                        altitudeCursor.first - lastAltitudeTurningPoint.first
                    }")
                totalAscent += altitudeCursor.first - lastAltitudeTurningPoint.first
                lastAltitudeTurningPoint = altitudeCursor
                Log.d(logTag, "Total ascent = ${totalAscent}")
                altitudeCursor = sample
                ascending = false
            }
        }
    }
    if (isRangeLessThan(altitudeCursor,
            lastAltitudeTurningPoint)
    ) totalDescent += altitudeCursor.first - lastAltitudeTurningPoint.first
    if (isRangeGreaterThan(altitudeCursor,
            lastAltitudeTurningPoint)
    ) totalAscent += altitudeCursor.first - lastAltitudeTurningPoint.first
    return Pair(totalAscent, totalDescent)
}

fun accumulateAscentDescent_kvl(
    elevationData: List<Double>,
    threshold: Double,
): Pair<Double, Double> {
    val logTag = "accumulateAscentDescent"
    var totalAscent = 0.0
    var totalDescent = 0.0
    var lastAltitudeTurningPoint = elevationData[0]
    var altitudeCursor = lastAltitudeTurningPoint
    var ascending = false
    var descending = false
    elevationData.forEach { sample ->
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

//const val elevationAlpha = 0.05
//const val elevationBeta = 0.99

fun accumulateAscentDescent_early(
    elevationData: List<Pair<Double, Double>>,
    elevationAlpha: Double,
): Pair<Double, Double> {
    var totalAscent = 0.0
    var totalDescent = 0.0
    var previous = elevationData[0].first

    smooth(elevationAlpha, elevationData.toTypedArray()).forEach { smoothedAltitude ->
        when (smoothedAltitude.first > previous) {
            true -> totalAscent += smoothedAltitude.first - previous
            else -> totalDescent += smoothedAltitude.first - previous
        }
        previous = smoothedAltitude.first
    }

    return Pair(totalAscent, totalDescent)
}

fun accumulateAscentDescent_doubleSmooth(
    elevationData: List<Pair<Double, Double>>,
    elevationAlpha: Double,
    elevationBeta: Double,
): Pair<Double, Double> {
    var totalAscent = 0.0
    var totalDescent = 0.0
    var previous = elevationData[0].first

    doubleSmooth(elevationAlpha, elevationBeta,
        elevationData.map { it.first }.toTypedArray()).forEach { smoothedAltitude ->
        when (smoothedAltitude > previous) {
            true -> totalAscent += smoothedAltitude - previous
            else -> totalDescent += smoothedAltitude - previous
        }
        previous = smoothedAltitude
    }

    /*
    var smoothed: Double = measurements[0].altitude
    var trend: Double = measurements[1].altitude - measurements[0].altitude
    var smoothedLast = smoothed
    measurements.forEach {
        smoothed =
            doubleExponentialSmoothing(elevationAlpha,
                it.altitude,
                smoothedLast, trend)
        trend =
            doubleExponentialSmoothingTrend(elevationBeta,
                smoothed,
                smoothedLast,
                trend)
        when (smoothed > smoothedLast) {
            true -> totalAscent += smoothed - smoothedLast
            else -> totalDescent += smoothed - smoothedLast
        }
        smoothedLast = smoothed
    }
*/
    return Pair(totalAscent, totalDescent)
}

fun accumulateAscentDescent_rangeCompare(elevationData: List<Pair<Double, Double>>): Pair<Double, Double> {
    var totalAscent = 0.0
    var totalDescent = 0.0
    var altitudeCursor = elevationData[0]

    elevationData.forEach { sample ->
        if (isRangeGreaterThan(sample, altitudeCursor)) {
            if (sample.first - altitudeCursor.first > 0.5) {
                totalAscent += sample.first - altitudeCursor.first
                altitudeCursor = sample
            }
        }
        if (isRangeLessThan(sample, altitudeCursor)) {
            if (sample.first - altitudeCursor.first < -0.5) {
                totalDescent += sample.first - altitudeCursor.first
                altitudeCursor = sample
            }
        }
    }

    return Pair(totalAscent, totalDescent)
}

data class DerivedMeasurements(
    val timestamp: Long,
    val duration: Long,
    val latitude: Double,
    val longitude: Double,
    val distanceDelta: Double,
    val distanceTotal: Double,
    val slopeDelta: Double,
    val slope: Double,
    val slopeSlope: Double,
    val altitudeDelta: Double,
    val altitude: Double,
    val accuracy: Double,
    val verticalAccuracyMeters: Double,
)

fun getDerivedMeasurements(
    /*lat: Double,
    lng: Double,
    altitude: Double,
    accuracy: Double,
    verticalAccuracyMeters: Double,*/
    measurements: CriticalMeasurements,
    previous: DerivedMeasurements?,
): DerivedMeasurements {
    if (previous == null) return DerivedMeasurements(
        timestamp = measurements.time,
        duration = 0L,
        latitude = measurements.latitude,
        longitude = measurements.longitude,
        distanceDelta = 0.0,
        distanceTotal = 0.0,
        slopeDelta = 0.0,
        slope = 0.0,
        slopeSlope = 0.0,
        altitude = measurements.altitude,
        altitudeDelta = 0.0,
        accuracy = measurements.accuracy.toDouble(),
        verticalAccuracyMeters = measurements.verticalAccuracyMeters?.toDouble() ?: 0.0
    )

    val distanceDelta = getDistance(
        Coordinate(latitude = measurements.latitude, longitude = measurements.longitude),
        Coordinate(latitude = previous.latitude, longitude = previous.longitude)
    )

    val distanceTotal = previous.distanceTotal + distanceDelta

    val altitude = exponentialSmoothing(0.05, measurements.altitude, previous.altitude)
    val altitudeDelta = altitude - previous.altitude

    val slope = altitudeDelta / distanceDelta
    val slopeDelta = slope - previous.slope
    val slopeSlope = slopeDelta / distanceDelta

    return DerivedMeasurements(
        timestamp = measurements.time,
        duration = measurements.time - previous.timestamp,
        latitude = measurements.latitude,
        longitude = measurements.longitude,
        distanceDelta = distanceDelta.toDouble(),
        distanceTotal = distanceTotal,
        slopeDelta = slopeDelta,
        slope = slope,
        slopeSlope = slopeSlope,
        altitude = altitude,
        altitudeDelta = altitudeDelta,
        accuracy = measurements.accuracy.toDouble(),
        verticalAccuracyMeters = measurements.verticalAccuracyMeters?.toDouble() ?: 0.0
    )
}

fun accumulateAscentDescent_tryTryAgain(
    measurements: List<DerivedMeasurements>,
    slopeThreshold: Double,
): Pair<Double, Double> {
/* filter out "high frequency noise"
   establish a window
   slide window over data find first last max min altitudes
   determine noise threshold within window
 */
    return Pair(0.0, 0.0)
}

fun accumulateAscentDescent_slope(
    measurements: List<DerivedMeasurements>,
    slopeThreshold: Double,
): Pair<Double, Double> {
    var last = measurements.first()

    return measurements.fold(Pair(0.0, 0.0), { acc, derivedMeasurements ->
        var ascent = acc.first
        var descent = acc.second
        if (abs((derivedMeasurements.slope - last.slope) / (derivedMeasurements.distanceTotal - last.distanceTotal)) < 5e-1) {
            if (derivedMeasurements.altitudeDelta >= 0.11) ascent += derivedMeasurements.altitude - last.altitude
            if (derivedMeasurements.altitudeDelta <= -0.11) descent += derivedMeasurements.altitude - last.altitude
            last = derivedMeasurements
        }
        return@fold Pair(ascent, descent)
    })
}

fun getAlpha(elevationData: List<Pair<Double, Double>>) =
    elevationData.map { it.first }.let {
        val min = it.minOrNull()!!
        val max = it.maxOrNull()!!
        println((max - min).toString())
        println(max).toString()
        println(min).toString()

        //val avg = elevationData.map { d -> d.first }.average()
        //(1.0 / (max - min).pow(2)).coerceIn(0.0, 1.0)
        max - min
    }

fun accumulateAscentDescent_production(elevationData: List<Pair<Double, Double>>) =
    accumulateAscentDescent_rangeCompare(smooth(0.05, elevationData.toTypedArray()))

fun accumulateAscentDescent_0818(
    elevationData: List<Pair<Double, Double>>,
): Pair<Double, Double> {
    var totalAscent = 0.0
    var totalDescent = 0.0
    var previous = elevationData[0].first

    var localAscent = 0.0
    var localDescent = 0.0

    var avg = elevationData[0].first
    elevationData.map {
        val weight = (it.second / 10 - 1).pow(8)
        avg = exponentialSmoothing(weight, it.first, avg)
        avg
    }.forEach { smoothedAltitude ->
        when (smoothedAltitude > previous) {
            true -> localAscent += smoothedAltitude - previous
            else -> localDescent += smoothedAltitude - previous
        }
        if (localAscent > 5.0) {
            totalAscent += localAscent
            localAscent = 0.0
            localDescent = 0.0
        }
        if (localDescent < -5.0) {
            totalDescent += localDescent
            localAscent = 0.0
            localDescent = 0.0
        }
        previous = smoothedAltitude
    }

    return Pair(totalAscent, totalDescent)
}

fun accumulateAscentDescent(elevationData: List<Pair<Double, Double>>): Pair<Double, Double> =
//accumulateAscentDescent_kvl(smooth(0.05,
//    elevationData.map { it.first }.toTypedArray()),
//    5.0)
//    accumulateAscentDescent_kvlRange(elevationData, 0.0)
//accumulateAscentDescent_early(elevationData, 0.21)
    //accumulateAscentDescent_rangeCompare(elevationData)
    accumulateAscentDescent_0818(elevationData)


/*
So far rangeCompare is the overall best but overshoots by quite bit on flat rides.
Smoothing the data improves flat rides as one would expect, but not to the desired values.
Additionally smoothing has a negative impact on hilly rides, reducing the total.
 */

