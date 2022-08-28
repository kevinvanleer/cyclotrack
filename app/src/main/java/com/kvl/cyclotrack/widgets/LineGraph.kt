package com.kvl.cyclotrack.widgets

import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.Log

data class LineGraphDataset(
    val points: List<Pair<Float, Float>>,
    val xRange: Pair<Float, Float>? = null,
    val yRange: Pair<Float, Float>? = null,
    val xAxisWidth: Float? = null,
    val yAxisHeight: Float? = null,
    val paint: Paint? = null,
)

class LineGraph(
    private val datasets: List<LineGraphDataset>,
) : Drawable() {

    private fun drawPath(
        canvas: Canvas,
        paint: Paint,
        points: List<Pair<Float, Float>>,
        xAxisWidth: Float,
        yAxisHeight: Float
    ) {
        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val xScale = width / xAxisWidth
        val yScale = height / yAxisHeight

        Log.d("LineGraph", "xScale:$xScale")
        Log.d("LineGraph", "yScale:$yScale")
        canvas.drawPath(
            Path().apply {
                moveTo(0f, height.toFloat())
                points.forEach { point ->
                    lineTo(
                        point.first * xScale,
                        height - (point.second * yScale)
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
            setARGB(255, 255, 0, 0)
        }

        //canvas.drawColor(Color.BLACK)
        datasets.forEach { dataset ->
            drawPath(
                canvas,
                dataset.paint ?: greenPaint,
                dataset.points,
                dataset.xAxisWidth ?: 0f, //TODO: replace zero with xRange distance
                dataset.yAxisHeight ?: 0f //TODO: replace zero with yRange distance
            )
        }
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