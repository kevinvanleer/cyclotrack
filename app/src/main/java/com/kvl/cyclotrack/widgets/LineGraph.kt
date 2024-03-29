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
import android.util.Log

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
    val ticks: Boolean = false,
    val orientation: AxisLabelOrientation = AxisLabelOrientation.INSIDE,
    val background: Int = Color.TRANSPARENT
)

enum class AxisLabelOrientation(val value: Int) {
    INSIDE(0),
    RIGHT(1),
    LEFT(2),
    TOP(3),
    BOTTOM(4)
}

enum class BordersEnum(val value: Int) {
    TOP(1),
    BOTTOM(2),
    LEFT(4),
    RIGHT(8)
}

class LineGraph(
    private val datasets: List<LineGraphDataset>,
    private val areas: List<LineGraphAreaDataset>? = null,
    private val xLabels: AxisLabels? = null,
    private val yLabels: AxisLabels? = null,
    private val borders: Int? = null,
    private val step: Boolean = false
) : Drawable() {

    private val xLabelTextSize = 28f
    private val yLabelTextSize = 32f

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
        textSize = yLabelTextSize
        typeface = Typeface.DEFAULT
        setARGB(150, 255, 255, 255)
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
    private val borderPaint = Paint(gridPaint).apply { alpha = 80 }

    private fun adjustCoordinate(
        point: Float,
        offset: Float,
        scale: Float
    ) = (point * scale) + (offset * scale)

    private fun adjustCoordinateY(
        height: Int,
        point: Float,
        offset: Float,
        yScale: Float
    ) = height - (point * yScale) + offset * yScale

    private fun getLinearPath(
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

    private fun getStepPath(
        height: Int,
        dataset: LineGraphDataset,
        yScale: Float,
        xScale: Float
    ): Path = Path().apply {
        var last = Pair(
            0f,
            adjustCoordinateY(
                height,
                dataset.points.first().second,
                dataset.yRange?.first ?: 0f,
                yScale
            )
        )
        moveTo(
            last.first,
            last.second
        )
        dataset.points.forEach { point ->
            lineTo(
                point.first * xScale,
                last.second,
            )
            last = Pair(
                point.first * xScale,
                adjustCoordinateY(
                    height,
                    point.second,
                    dataset.yRange?.first ?: 0f,
                    yScale
                )
            )
            lineTo(
                last.first,
                last.second
            )
        }
    }

    private fun getPath(
        height: Int,
        dataset: LineGraphDataset,
        yScale: Float,
        xScale: Float,
        step: Boolean
    ) = when (step) {
        true -> getStepPath(height, dataset, yScale, xScale)
        else -> getLinearPath(height, dataset, yScale, xScale)
    }

    private fun drawArea(
        canvas: Canvas,
        dataset: LineGraphAreaDataset,
        width: Int,
        height: Int,
        step: Boolean
    ) {
        val xScale = width / (dataset.xAxisWidth ?: 1f)
        val yScale = height / (dataset.yAxisHeight ?: 1f)

        val endPoint =
            dataset.xRange?.first?.plus(dataset.xAxisWidth ?: (width * xScale)) ?: (width * xScale)
        try {
            canvas.drawPath(
                getPath(
                    height,
                    LineGraphDataset(
                        dataset.points1.plus(Pair(endPoint, dataset.points1.last().second)).plus(
                            dataset.points2.plus(Pair(endPoint, dataset.points2.last().second))
                                .reversed()
                        ),
                        dataset.xRange,
                        dataset.yRange,
                        dataset.xAxisWidth,
                        dataset.yAxisHeight,
                        dataset.paint
                    ),
                    yScale,
                    xScale,
                    step
                ), dataset.paint ?: greenPaint
            )
        } catch (e: Exception) {
            Log.e("LineGraph", "Could not draw area", e)
        }
    }

    private fun drawPath(
        canvas: Canvas,
        dataset: LineGraphDataset,
        width: Int,
        height: Int,
        step: Boolean
    ) {
        val xScale = width / (dataset.xAxisWidth ?: 1f)
        val yScale = height / (dataset.yAxisHeight ?: 1f)
        canvas.drawPath(
            getPath(height, dataset, yScale, xScale, step), dataset.paint ?: greenPaint
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

    private fun drawBorders(canvas: Canvas, borders: Int, width: Int, height: Int) {

        if ((borders and BordersEnum.TOP.value) == BordersEnum.TOP.value) canvas.drawPath(
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

        if ((borders and BordersEnum.LEFT.value) == BordersEnum.LEFT.value) canvas.drawPath(
            Path().apply {
                moveTo(
                    0f,
                    0f
                )
                lineTo(
                    0f,
                    height.toFloat()
                )
            }, borderPaint
        )

        if ((borders and BordersEnum.RIGHT.value) == BordersEnum.RIGHT.value) canvas.drawPath(
            Path().apply {
                moveTo(
                    width.toFloat(),
                    0f
                )
                lineTo(
                    width.toFloat(),
                    height.toFloat()
                )
            }, borderPaint
        )

        if ((borders and BordersEnum.BOTTOM.value) == BordersEnum.BOTTOM.value) canvas.drawPath(
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
        val width: Int =
            bounds.width() - if (yLabels != null && yLabels.orientation != AxisLabelOrientation.INSIDE) getYLabelWidth(
                yLabels.labels
            ).toInt() else 0
        val height: Int =
            bounds.height() - if (xLabels != null) getXLabelHeight(xLabels).toInt() + 2 else 0

        areas?.forEach { area ->
            drawArea(
                canvas,
                area,
                width,
                height,
                step
            )
        }
        datasets.forEach { dataset ->
            drawPath(
                canvas,
                dataset,
                width,
                height,
                step
            )
        }
        if (yLabels != null) drawYLabels(canvas, yLabels, width, height)
        if (xLabels != null) drawXLabels(canvas, xLabels, width, height)
        if (borders != null) drawBorders(canvas, borders, width, height)
    }

    private val xLabelTickSize = 16f

    private fun drawXLabels(canvas: Canvas, xLabels: AxisLabels, width: Int, height: Int) {
        val xLabelFillPaint = Paint(textPaintFill).apply {
            textAlign = Paint.Align.CENTER
            textSize = xLabelTextSize
        }
        val xLabelStrokePaint = Paint(textPaintStroke).apply {
            textAlign = Paint.Align.CENTER
            textSize = xLabelTextSize
        }
        val xScale = width / ((xLabels.range?.second ?: 0f) - (xLabels.range?.first ?: 0f))
        xLabels.labels.forEach { label ->
            val dataLabelY = height + getXLabelHeight(xLabels)
            val dataLabelX = adjustCoordinate(
                label.first,
                xLabels.range?.first ?: 0f,
                xScale
            )

            if (xLabels.orientation == AxisLabelOrientation.INSIDE) canvas.drawText(
                label.second,
                dataLabelX,
                dataLabelY,
                Paint(xLabelStrokePaint).apply { color = xLabels.background }
            )
            canvas.drawText(
                label.second,
                dataLabelX,
                dataLabelY,
                xLabelFillPaint
            )
            if (xLabels.ticks) {
                val x = adjustCoordinate(
                    label.first,
                    xLabels.range?.first ?: 0f,
                    xScale
                )
                canvas.drawPath(
                    Path().apply {
                        moveTo(
                            x,
                            height.toFloat() + 2f
                        )
                        lineTo(
                            x,
                            height + xLabelTickSize
                        )
                    }, Paint(borderPaint).apply { strokeJoin = Paint.Join.BEVEL }
                )
            }
            if (xLabels.lines) {
                canvas.drawPath(
                    Path().apply {
                        moveTo(
                            dataLabelX,
                            0f
                        )
                        lineTo(
                            dataLabelX,
                            height.toFloat()
                        )
                    }, gridPaint
                )
            }
        }
    }

    private fun getXLabelHeight(xLabels: AxisLabels) =
        when (xLabels.orientation) {
            AxisLabelOrientation.BOTTOM -> xLabelTextSize + xLabelTickSize + 4f
            else -> xLabelTextSize + xLabelTickSize + 4f
        }

    private fun getYLabelWidth(labels: List<Pair<Float, String>>): Float =
        labels.map { it.second }.maxOf { textPaintFill.measureText(it) } + 24f

    private fun drawYLabels(canvas: Canvas, yLabels: AxisLabels, width: Int, height: Int) {
        val yScale = height / ((yLabels.range?.second ?: 0f) - (yLabels.range?.first ?: 0f))
        yLabels.labels.forEach { label ->
            val dataLabelX = when (yLabels.orientation) {
                AxisLabelOrientation.RIGHT -> width + 16f
                AxisLabelOrientation.INSIDE -> width - textPaintFill.measureText(label.second) - 16f
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

                AxisLabelOrientation.INSIDE -> 14f
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
