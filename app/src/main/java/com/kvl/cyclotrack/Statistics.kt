package com.kvl.cyclotrack

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

fun smooth(alpha: Double, data: Array<Pair<Double, Double>>): List<Pair<Double, Double>> {
    var smoothedFirst = data[0].first
    var smoothedSecond = data[0].second
    return data.map { datum ->
        smoothedFirst = exponentialSmoothing(alpha, datum.first, smoothedFirst)
        smoothedSecond = exponentialSmoothing(alpha, datum.second, smoothedSecond)
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
    val leftRange = Pair(left.first - left.second, left.first + left.second)
    val rightRange = Pair(right.first - right.second, right.first + right.second)
    return leftRange.first > rightRange.second
}

fun isRangeLessThan(left: Pair<Double, Double>, right: Pair<Double, Double>): Boolean {
    val leftRange = Pair(left.first - left.second, left.first + left.second)
    val rightRange = Pair(right.first - right.second, right.first + right.second)
    return leftRange.second < rightRange.first
}

fun leastSquaresFitSlope(data: List<Pair<Double, Double>>): Double {
    //https://stats.libretexts.org/Bookshelves/Introductory_Statistics/Book%3A_Introductory_Statistics_(Shafer_and_Zhang)/10%3A_Correlation_and_Regression/10.04%3A_The_Least_Squares_Regression_Line

    var sumx = 0.0
    var sumy = 0.0
    var sumxsq = 0.0
    var sumxy = 0.0

    data.forEach {
        sumx += it.first
        sumy += it.second
        sumxsq += it.first * it.first
        sumxy += it.first * it.second
    }

    val ssxy = sumxy - ((1.0 / data.size) * sumx * sumy)
    val ssxx = sumxsq - ((1.0 / data.size) * sumx * sumx)

    return ssxy / ssxx
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