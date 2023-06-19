package com.kvl.cyclotrack.widgets

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable

typealias Entry = Pair<Float, Float>

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
    val label: String? = null,
)

class LineGraph(
    private val datasets: List<LineGraphDataset>,
    private val areas: List<LineGraphAreaDataset>? = null,
) : Drawable() {

    private fun adjustCoordinate(
        size: Int,
        point: Float,
        offset: Float,
        scale: Float
    ) = size - (point * scale) + offset * scale

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
            0f,
            adjustCoordinateY(
                height,
                dataset.points.first().second,
                dataset.yRange?.first ?: 0f,
                yScale
            )
        )
        dataset.points.forEach { point ->
            lineTo(
                point.first * xScale,
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
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 5F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setARGB(255, 255, 0, 0)
        }
        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val xScale = width / (dataset.xAxisWidth ?: 1f)
        val yScale = height / (dataset.yAxisHeight ?: 1f)
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
                    add(Pair(0f, dataset.yRange.second))
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

        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val xScale = width / (dataset.xAxisWidth ?: 1f)
        val yScale = height / (dataset.yAxisHeight ?: 1f)
        canvas.drawPath(
            getPath(height, dataset, yScale, xScale), dataset.paint ?: greenPaint
        )
        val textPaint = Paint().apply {
            this.textAlign = Paint.Align.RIGHT
            this.flags = Paint.SUBPIXEL_TEXT_FLAG and Paint.LINEAR_TEXT_FLAG
            this.textSize = 32f
            this.typeface = Typeface.DEFAULT
            setARGB(200, 255, 255, 255)
        }
        if (dataset.label != null) {
            /*val dataLabelX = adjustCoordinate(
                width,
                dataset.points.last().first,
                dataset.xRange?.first ?: 0f,
                xScale
            ) - 12f*/
            val dataLabelX = width - 12f
            val dataLabelY = adjustCoordinateY(
                height,
                dataset.points.last().second,
                dataset.yRange?.first ?: 0f,
                yScale
            ) - 14f
            canvas.drawText(
                dataset.label,
                dataLabelX,
                dataLabelY,
                textPaint
            )
        }
    }

    private fun drawBorder(canvas: Canvas) {
        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val borderPaint: Paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeWidth = 2F
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setARGB(150, 255, 255, 255)
        }

        canvas.drawPath(
            Path().apply {
                moveTo(
                    0f,
                    0f
                )
                lineTo(
                    width.toFloat(),
                    0f
                )
            }, borderPaint
        )
        canvas.drawPath(
            Path().apply {
                moveTo(
                    0f,
                    height.toFloat()
                )
                lineTo(
                    width.toFloat(),
                    height.toFloat()
                )
            }, borderPaint
        )
    }

    override fun draw(canvas: Canvas) {
        //drawBorder(canvas)
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
