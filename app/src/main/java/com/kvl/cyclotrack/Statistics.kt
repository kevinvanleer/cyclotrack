package com.kvl.cyclotrack

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

fun isRangeGreaterThan(left: Pair<Double, Double>, right: Pair<Double, Double>): Boolean {
    val leftRange = Pair(left.first - left.second, left.first + left.second)
    val rightRange = Pair(right.first - right.second, right.first + right.second)
    return leftRange.first > rightRange.second
}

fun isRangeLessThan(left: Pair<Double, Double>, right: Pair<Double, Double>): Boolean {
    val leftRange = Pair(left.first - left.second, left.first + left.second)
    val rightRange = Pair(right.first - right.second, right.first + right.second)
    return leftRange.second < rightRange.first
}

fun accumulateAscentDescent(elevationData: List<Pair<Double, Double>>): Pair<Double, Double> {
    var totalAscent = 0.0
    var totalDescent = 0.0
    var altitudeCursor = elevationData[0]

    elevationData.forEach { sample ->
        if (isRangeGreaterThan(sample, altitudeCursor)) {
            totalAscent += sample.first - altitudeCursor.first
            altitudeCursor = sample
        }
        if (isRangeLessThan(sample, altitudeCursor)) {
            totalDescent += sample.first - altitudeCursor.first
            altitudeCursor = sample
        }
    }

    return Pair(totalAscent, totalDescent)
}