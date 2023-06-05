package com.kvl.cyclotrack.widgets

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

data class LineGraphAreaDataset(
    val points1: List<Pair<Float, Float>>,
    val points2: List<Pair<Float, Float>>,
    val xRange: Pair<Float, Float>? = null,
    val yRange: Pair<Float, Float>? = null,
    val xAxisWidth: Float? = null,
    val yAxisHeight: Float? = null,
    val paint: Paint? = null,
)

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
    private val areas: List<LineGraphAreaDataset>? = null,
) : Drawable() {

    private fun adjustCoordinateY(
        height: Int,
        point: Float,
        offset: Float,
        yScale: Float
    ) = height - (point * yScale) + offset * yScale

    private fun getPath(
        height: Int,
        dataset: LineGraphDataset,
        yScale: Float,
        xScale: Float
    ): Path = Path().apply {
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
    }

    private fun drawArea(
        canvas: Canvas,
        dataset: LineGraphAreaDataset
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
        val borderPaint: Paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeWidth = 2F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setARGB(150, 0, 0, 0)
        }
        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val xScale = width / (dataset.xAxisWidth ?: 1f)
        val yScale = height / (dataset.yAxisHeight ?: 1f)
        /*adjustCoordinateY(
            height,
            dataset.points1.first().second,
            dataset.yRange?.first ?: 0f,
            yScale
        )*/
        val path1 = getPath(
            height,
            LineGraphDataset(
                dataset.points1.toMutableList().apply {
                    add(Pair(dataset.xRange!!.second, 0f))
                    add(Pair(0f, 0f))
                },
                dataset.xRange,
                dataset.yRange,
                dataset.xAxisWidth,
                dataset.yAxisHeight,
                dataset.paint
            ),
            yScale,
            xScale
        )
        val path2 = getPath(
            height,
            LineGraphDataset(
                dataset.points2.toMutableList().apply {
                    add(Pair(dataset.xRange!!.second, dataset.yRange!!.second))
                    add(Pair(0f, dataset.yRange!!.second))
                },
                dataset.xRange,
                dataset.yRange,
                dataset.xAxisWidth,
                dataset.yAxisHeight,
                dataset.paint
            ),
            yScale,
            xScale
        )
        path1.op(path2, Path.Op.INTERSECT)
        canvas.drawPath(path1, dataset.paint ?: greenPaint)

    }

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
        val borderPaint: Paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeWidth = 2F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setARGB(150, 0, 0, 0)
        }
        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val xScale = width / (dataset.xAxisWidth ?: 1f)
        val yScale = height / (dataset.yAxisHeight ?: 1f)
        /*adjustCoordinateY(
            height,
            dataset.points.first().second,
            dataset.yRange?.first ?: 0f,
            yScale
        )*/
        canvas.drawPath(
            getPath(height, dataset, yScale, xScale), dataset.paint ?: greenPaint
        )
    }

    override fun draw(canvas: Canvas) {
        //canvas.drawColor(Color.argb(25, 0, 0, 0))
        areas?.forEach { area ->
            drawArea(
                canvas,
                area
            )
        }
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
