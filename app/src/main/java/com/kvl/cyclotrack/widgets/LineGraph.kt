package com.kvl.cyclotrack.widgets

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
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

data class AxisLabels(
    val labels: List<Pair<Float, String>>,
    val range: Pair<Float, Float>? = null,
    val lines: Boolean = false,
    val orientation: AxisLabelOrientation = AxisLabelOrientation.INSIDE,
    val background: Int = Color.TRANSPARENT
)

enum class AxisLabelOrientation(val value: Int) {
    INSIDE(0),
    RIGHT(1),
    LEFT(2)
}

class LineGraph(
    private val datasets: List<LineGraphDataset>,
    private val areas: List<LineGraphAreaDataset>? = null,
    private val xLabels: AxisLabels? = null,
    private val yLabels: AxisLabels? = null
) : Drawable() {
    private val greenPaint: Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 5F
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setARGB(255, 255, 0, 0)
    }

    private val textPaintFill = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        textSize = 32f
        typeface = Typeface.DEFAULT
        setARGB(255, 200, 200, 200)
    }
    private val textPaintStroke = Paint(textPaintFill).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val gridPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2F
        setARGB(100, 255, 255, 255)
    }

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
        dataset: LineGraphAreaDataset,
        width: Int,
        height: Int,
    ) {
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
        dataset: LineGraphDataset,
        width: Int,
        height: Int,
    ) {
        val xScale = width / (dataset.xAxisWidth ?: 1f)
        val yScale = height / (dataset.yAxisHeight ?: 1f)
        canvas.drawPath(
            getPath(height, dataset, yScale, xScale), dataset.paint ?: greenPaint
        )

        val fill = Paint(textPaintFill).apply { textAlign = Paint.Align.RIGHT }
        val stroke = Paint(textPaintStroke).apply { textAlign = Paint.Align.RIGHT }
        if (dataset.label != null) {
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
                stroke
            )
            canvas.drawText(
                dataset.label,
                dataLabelX,
                dataLabelY,
                fill
            )
        }
    }

    private fun drawBorder(canvas: Canvas) {
        val width: Int = bounds.width()
        val height: Int = bounds.height()

        val borderPaint: Paint = Paint().apply {
            isAntiAlias = true
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
        val width: Int =
            bounds.width() - if (yLabels != null && yLabels.orientation != AxisLabelOrientation.INSIDE) getYLabelWidth(
                yLabels.labels
            ).toInt() else 0
        val height: Int = bounds.height() - if (xLabels != null) 200 else 0

        areas?.forEach { area ->
            drawArea(
                canvas,
                area,
                width,
                height,
            )
        }
        datasets.forEach { dataset ->
            drawPath(
                canvas,
                dataset,
                width,
                height,
            )
        }
        if (yLabels != null) drawYLabels(canvas, yLabels, width, height)
    }

    private fun getYLabelWidth(labels: List<Pair<Float, String>>): Float =
        labels.map { it.second }.maxOf { textPaintFill.measureText(it) } + 24f

    private fun drawYLabels(canvas: Canvas, yLabels: AxisLabels, width: Int, height: Int) {
        val yScale = height / ((yLabels.range?.second ?: 0f) - (yLabels.range?.first ?: 0f))
        yLabels.labels.forEach { label ->
            val dataLabelX = when (yLabels.orientation) {
                AxisLabelOrientation.RIGHT -> width + 16f
                else -> width - textPaintFill.measureText(label.second) - 16f
            }
            val dataLabelY = adjustCoordinateY(
                height,
                label.first,
                yLabels.range?.first ?: 0f,
                yScale
            ) - when (yLabels.orientation) {
                AxisLabelOrientation.RIGHT ->
                    getTextMiddle(textPaintFill, label.second)

                else -> 14f
            }
            if (yLabels.orientation == AxisLabelOrientation.INSIDE) canvas.drawText(
                label.second,
                dataLabelX,
                dataLabelY,
                Paint(textPaintStroke).apply { color = yLabels.background }
            )
            canvas.drawText(
                label.second,
                dataLabelX,
                dataLabelY,
                textPaintFill
            )
            if (yLabels.lines) {
                canvas.drawPath(
                    Path().apply {
                        moveTo(
                            0f,
                            adjustCoordinateY(
                                height,
                                label.first,
                                yLabels.range?.first ?: 0f,
                                yScale
                            )
                        )
                        lineTo(
                            width.toFloat(),
                            adjustCoordinateY(
                                height,
                                label.first,
                                yLabels.range?.first ?: 0f,
                                yScale
                            )
                        )
                    }, gridPaint
                )
            }
        }
    }

    private fun getTextMiddle(paint: Paint, text: String): Float {
        val rect = Rect();
        paint.getTextBounds(text, 0, text.length, rect)
        return rect.exactCenterY();
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
