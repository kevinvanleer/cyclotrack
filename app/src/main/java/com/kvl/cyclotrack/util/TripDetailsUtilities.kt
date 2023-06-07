package com.kvl.cyclotrack.util

import android.content.Context
import android.util.Log
import com.kvl.cyclotrack.Measurements
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.accumulateTime
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.getRpm
import com.kvl.cyclotrack.getUserSpeed
import com.kvl.cyclotrack.widgets.Entry

private const val logTag = "TripDetailsUtilities"

fun useBleSpeedData(
    speedMeasurements: Array<CadenceSpeedMeasurement>,
    locationMeasurements: Array<Measurements>
): Boolean {
    Log.d(logTag, "useBleSpeedData")
    if (speedMeasurements.isEmpty() || locationMeasurements.isEmpty()) return false
    Log.d(logTag, "Passed empty check")

    val bleDuration = speedMeasurements.last().timestamp - speedMeasurements.first().timestamp
    Log.d(logTag, "size check ${speedMeasurements.size.toFloat() / bleDuration}")

    if (speedMeasurements.size.toFloat() / bleDuration < 0.0001) return false
    Log.d(logTag, "Passed size check")

    val gpsDuration = locationMeasurements.last().time - locationMeasurements.first().time
    Log.d(logTag, "Duration check: ${bleDuration.toFloat() / gpsDuration.toFloat()}")

    if (bleDuration.toFloat() / gpsDuration.toFloat() < 0.9) return false
    Log.d(logTag, "Passed duration check")

    return true
}

/*fun formatRawTrendLineData(
    resources: Resources,
    entries: ArrayList<Entry>,
    trend: ArrayList<Entry>
): Pair<LineDataSet, LineDataSet> {
    val dataset = LineDataSet(entries, "Speed")
    dataset.setDrawCircles(false)
    dataset.setDrawValues(false)
    val trendData = LineDataSet(trend, "Trend")
    trendData.setDrawCircles(false)
    trendData.setDrawValues(false)
    dataset.color =
        ResourcesCompat.getColor(
            resources,
            R.color.secondaryGraphColor,
            null
        )
    dataset.lineWidth = 10f
    trendData.color =
        ResourcesCompat.getColor(
            resources,
            R.color.accentColor,
            null
        )
    trendData.lineWidth = 3f
    return Pair(dataset, trendData)
}*/

val getSpeedDataFromSensor: (
    context: Context,
    overview: Trip,
    effectiveCircumference: Float?,
    measurementsList: Array<CadenceSpeedMeasurement>,
    intervals: Array<LongRange>,
    avgSpeed: Float
) -> (
    entries: ArrayList<Entry>,
    trend: ArrayList<Entry>,
    hi: ArrayList<Entry>,
    lo: ArrayList<Entry>,
) -> Unit = { context, overview, effectiveCircumference, measurementsList, intervals, avgSpeed ->
    { entries, trend, hi, lo ->
        val circumference =
            effectiveCircumference ?: overview.autoWheelCircumference
            ?: overview.userWheelCircumference
        Log.d(logTag, "Using circumference: $circumference")

        val intervalStart = intervals.last().first
        val accumulatedTime = accumulateTime(intervals)
        var trendLast =
            getUserSpeed(
                context,
                measurementsList[0].rpm?.times(circumference!!)?.div(60)?.toDouble() ?: 0.0
            )
        var hiLast: Float? = null;
        var loLast: Float? = null;
        var trendAlpha = 0.5f
        var lastMeasurement: CadenceSpeedMeasurement? = null

        measurementsList.forEach { measurements ->
            lastMeasurement
                ?.let { last ->
                    if (measurements.lastEvent != last.lastEvent) {
                        try {
                            getRpm(
                                rev = measurements.revolutions,
                                revLast = last.revolutions,
                                time = measurements.lastEvent,
                                timeLast = last.lastEvent,
                                delta = measurements.timestamp - last.timestamp
                            ).takeIf { it.isFinite() }
                                ?.let { getUserSpeed(context, it * circumference!! / 60) }
                                ?.let { speed ->
                                    val timestamp =
                                        (accumulatedTime + (measurements.timestamp - intervalStart) / 1e3).toFloat()
                                    entries.add(Entry(timestamp, speed))
                                    /*(trendLast =
                                        (trendAlpha * speed) + ((1 - trendAlpha) * trendLast)
                                    trend.add(Entry(timestamp, trendLast))*/
                                    getTrendData(
                                        speed,
                                        trendAlpha,
                                        avgSpeed,
                                        trendLast,
                                        hiLast,
                                        loLast
                                    ).let { (trendNew, hiNew, loNew) ->
                                        trend.add(Entry(timestamp, trendNew))
                                        trendLast = trendNew
                                        hiNew?.let {
                                            hi.add(Pair(timestamp, it))
                                            hiLast = it
                                        }
                                        loNew?.let {
                                            lo.add(Pair(timestamp, it))
                                            loLast = it
                                        }
                                    }
                                    if (trendAlpha > 0.01f) trendAlpha -= 0.005f
                                    if (trendAlpha < 0.01f) trendAlpha = 0.01f
                                }
                        } catch (e: Exception) {
                            Log.e(
                                logTag,
                                "Could not calculate speed for time ${measurements.timestamp}"
                            )
                        }
                    }
                }
            lastMeasurement = measurements
        }
    }
}

val getSpeedDataFromGps: (
    context: Context,
    measurements: Array<Measurements>,
    intervals: Array<LongRange>,
    avgSpeed: Float,
) -> (
    entries: ArrayList<Entry>,
    trend: ArrayList<Entry>,
    hi: ArrayList<Entry>,
    lo: ArrayList<Entry>,
) -> Unit = { context, measurements, intervals, avgSpeed ->
    { entries, trend, hi, lo ->
        Log.v(logTag, "getSpeedFromGps")
        val intervalStart = intervals.last().first
        val accumulatedTime = accumulateTime(intervals)
        var trendLast =
            getUserSpeed(context, measurements[0].speed.toDouble())
        val trendAlpha = 0.01f
        var hiLast: Float? = null
        var loLast: Float? = null

        measurements.forEach {
            Log.v(logTag, "GPS speed: ${it.speed}")
            val speed = getUserSpeed(
                context,
                it.speed.toDouble()
            )
            val timestamp =
                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
            entries.add(Entry(timestamp, speed))
            getTrendData(
                speed,
                trendAlpha,
                avgSpeed,
                trendLast,
                hiLast,
                loLast
            ).let { (trendNew, hiNew, loNew) ->
                trend.add(Entry(timestamp, trendNew))
                trendLast = trendNew
                hiNew?.let {
                    hi.add(Pair(timestamp, it))
                    hiLast = it
                }
                loNew?.let {
                    lo.add(Pair(timestamp, it))
                    loLast = it
                }
            }
        }
    }
}

fun getTrendData(
    yValue: Float,
    alpha: Float,
    average: Float,
    trendLast: Float?,
    hiLast: Float?,
    loLast: Float?
): Triple<Float, Float?, Float?> {
    var hi: Float? = null
    var lo: Float? = null
    val trend =
        (alpha * yValue) + ((1 - alpha) * (trendLast
            ?: yValue))
    if (yValue >= average) {
        hi = (alpha * yValue) + ((1 - alpha) * (hiLast
            ?: yValue))
    } else {
        lo = (alpha * yValue) + ((1 - alpha) * (loLast
            ?: yValue))
    }
    return Triple(
        trend,
        hi,
        lo
    )
}