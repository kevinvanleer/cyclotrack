package com.kvl.cyclotrack

import android.graphics.Color
import android.location.Location
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.maps.model.LatLng
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurement
import kotlin.math.roundToInt

fun configureLineChart(chart: LineChart, yMin: Float = 0f) {
    chart.setTouchEnabled(false)
    chart.setDrawBorders(true)
    chart.setBorderColor(Color.GRAY)
    chart.setNoDataText("Looking for data...")
    chart.legend.isEnabled = false
    chart.setDrawGridBackground(false)

    chart.xAxis.setDrawLabels(true)
    chart.xAxis.axisMinimum = 0f
    chart.xAxis.setDrawGridLines(true)
    chart.xAxis.textColor = Color.WHITE
    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.valueFormatter = object : ValueFormatter() {
        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            return if (value == 0f) "" else formatDuration(value.toDouble())
        }
    }

    chart.axisLeft.setDrawLabels(true)
    chart.axisLeft.textColor = Color.WHITE
    chart.axisLeft.axisMinimum = yMin
    chart.axisLeft.setDrawGridLines(true)
    chart.axisRight.setDrawLabels(true)
    chart.axisRight.textColor = Color.WHITE
    chart.axisRight.axisMinimum = yMin
    chart.axisRight.setDrawGridLines(false)
}

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
