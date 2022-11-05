package com.kvl.cyclotrack.util

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.kvl.cyclotrack.*
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement

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

fun formatRawTrendLineData(
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
}

val getSpeedDataFromSensor: (
    context: Context,
    overview: Trip,
    effectiveCircumference: Float?,
    measurementsList: Array<CadenceSpeedMeasurement>,
    intervals: Array<LongRange>,
) -> (
    entries: ArrayList<Entry>,
    trend: ArrayList<Entry>,
) -> Unit = { context, overview, effectiveCircumference, measurementsList, intervals ->
    { entries, trend ->
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
                                ?.let { it * circumference!! / 60 }
                                ?.let { speed ->
                                    val timestamp =
                                        (accumulatedTime + (measurements.timestamp - intervalStart) / 1e3).toFloat()
                                    entries.add(
                                        Entry(
                                            timestamp,
                                            getUserSpeed(
                                                context,
                                                speed
                                            )
                                        )
                                    )
                                    trendLast =
                                        (trendAlpha * getUserSpeed(
                                            context,
                                            speed
                                        )) + ((1 - trendAlpha) * trendLast)
                                    trend.add(Entry(timestamp, trendLast))
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
) -> (
    entries: ArrayList<Entry>,
    trend: ArrayList<Entry>,
) -> Unit = { context, measurements, intervals ->
    { entries, trend ->
        Log.v(logTag, "getSpeedFromGps")
        val intervalStart = intervals.last().first
        val accumulatedTime = accumulateTime(intervals)
        var trendLast =
            getUserSpeed(context, measurements[0].speed.toDouble())
        var trendAlpha = 0.01f
        measurements.forEach {
            Log.v(logTag, "GPS speed: ${it.speed}")
            val timestamp =
                (accumulatedTime + (it.time - intervalStart) / 1e3).toFloat()
            entries.add(
                Entry(
                    timestamp,
                    getUserSpeed(context, it.speed.toDouble())
                )
            )
            trendLast = (trendAlpha * getUserSpeed(
                context,
                it.speed.toDouble()
            )) + ((1 - trendAlpha) * trendLast)
            trend.add(Entry(timestamp, trendLast))
            if (trendAlpha > 0.01f) trendAlpha -= 0.01f
        }
    }
}
