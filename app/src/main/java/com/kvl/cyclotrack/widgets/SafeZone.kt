package com.kvl.cyclotrack.widgets

import android.graphics.*
import android.graphics.drawable.Drawable

class SafeZone(private val touchPoints: List<List<Pair<Float, Float>>>) : Drawable() {
    override fun draw(canvas: Canvas) {
        val brush = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 48F
            setARGB(255, 0, 255, 0)
        }
        touchPoints.forEach { pointSet ->
            if (pointSet.isNotEmpty()) {
                canvas.drawPath(
                    Path().apply {
                        moveTo(pointSet[0].first, pointSet[0].second)
                        pointSet.forEach {
                            lineTo(it.first, it.second)
                        }
                    }, brush
                )
            }
        }
    }

    //fun redraw(touchPoints: List<List<Pair<Float, Float>>>)

    override fun setAlpha(alpha: Int) {
        TODO("Not yet implemented")
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        TODO("Not yet implemented")
    }

    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }
}
