package com.kvl.cyclotrack.widgets

import android.graphics.*
import android.graphics.drawable.Drawable

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

    private fun adjustCoordinateY(
        height: Int,
        point: Float,
        offset: Float,
        yScale: Float
    ) = height - (point * yScale) + offset * yScale

    private fun drawPath(
        canvas: Canvas,
        dataset: LineGraphDataset
    ) {
        val greenPaint: Paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeWidth = 5F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setARGB(255, 255, 0, 0)
        }
        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val xScale = width / (dataset.xAxisWidth ?: 1f)
        val yScale = height / (dataset.yAxisHeight ?: 1f)

        canvas.drawPath(
            Path().apply {
                moveTo(
                    //(dataset.xRange?.first ?: 0f) * xScale,
                    0f,
                    adjustCoordinateY(
                        height,
                        dataset.points.first().second,
                        dataset.yRange?.first ?: 0f,
                        yScale
                    )
                    //height.toFloat()
                )
                dataset.points.forEach { point ->
                    lineTo(
                        point.first * xScale,
                        //height - (point.second * yScale) + ((dataset.yRange?.first ?: 0f) * yScale)
                        adjustCoordinateY(
                            height,
                            point.second,
                            dataset.yRange?.first ?: 0f,
                            yScale
                        )
                    )
                }
            }, dataset.paint ?: greenPaint
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        datasets.forEach { dataset ->
            drawPath(
                canvas,
                dataset
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
