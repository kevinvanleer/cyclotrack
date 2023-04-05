package com.kvl.cyclotrack.util

import kotlin.math.*

fun rotate(
    radians: Float,
    point: Pair<Float, Float>,
    center: Pair<Float, Float>
): Pair<Float, Float> {
    val deltaX = point.first - center.first
    val deltaY = point.second - center.second
    val distance = sqrt(deltaX.pow(2f) + deltaY.pow(2f))
    val startAngle = atan(deltaY / deltaX)
    val newAngle = when (startAngle + radians) {
        in (-PI / 2).toFloat()..(PI / 2).toFloat() -> startAngle + radians
        else -> startAngle + radians
    }

    return Pair(distance * cos(newAngle) + center.first, distance * sin(newAngle) + center.second)
}
