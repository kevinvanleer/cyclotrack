package com.kvl.cyclotrack.widgets

import android.graphics.*
import android.graphics.drawable.Drawable
import java.time.ZonedDateTime

class LineGraph(
    private val thisPeriod: Pair<ZonedDateTime, ZonedDateTime>,
    private val lastPeriod: Pair<ZonedDateTime, ZonedDateTime>,
    private val thisPeriodPoints: List<Pair<Long, Double>>,
    private val lastPeriodPoints: List<Pair<Long, Double>>
) : Drawable() {

    private fun drawPath(
        canvas: Canvas,
        paint: Paint,
        period: Pair<ZonedDateTime, ZonedDateTime>,
        points: List<Pair<Long, Double>>,
        _yScale: Float? = null
    ) {
        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val totalDistance = points.sumOf { point -> point.second }
        val durationStart = period.first.toInstant().toEpochMilli()
        val totalDuration = period.second.toInstant().minusMillis(durationStart)
        val xScale = width.toFloat() / totalDuration.toEpochMilli()
        val yScale = _yScale ?: (height / totalDistance.toFloat())
        canvas.drawPath(
            Path().apply {
                moveTo(0f, height.toFloat())
                var accDistance = 0f
                points.forEach { point ->
                    accDistance += point.second.toFloat() * yScale
                    lineTo(
                        (point.first - durationStart) * xScale,
                        height - accDistance
                    )
                }
            }, paint
        )
    }

    override fun draw(canvas: Canvas) {
        val greenPaint: Paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeWidth = 5F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setARGB(255, 0, 255, 0)
        }
        val lastPaint: Paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeWidth = 5F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setARGB(255, 0, 150, 0)
        }

        val height: Int = bounds.height()

        val totalDistanceThis = thisPeriodPoints.sumOf { point -> point.second }
        val totalDistanceLast = lastPeriodPoints.sumOf { point -> point.second }
        val yScale = (height / maxOf(totalDistanceLast, totalDistanceThis).toFloat())
        // Get the drawable's bounds
        drawPath(canvas, lastPaint, lastPeriod, lastPeriodPoints, yScale)
        drawPath(canvas, greenPaint, thisPeriod, thisPeriodPoints, yScale)
    }

    override fun setAlpha(p0: Int) {
        //TODO("Not yet implemented")
    }

    override fun setColorFilter(p0: ColorFilter?) {
        //TODO("Not yet implemented")
    }

    override fun getOpacity(): Int {
        //TODO("Not yet implemented")
        return PixelFormat.OPAQUE
    }
}
