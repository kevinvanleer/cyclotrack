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

fun isClosed(points: List<Pair<Float, Float>>): Boolean {
    for (it in 1 until points.size) {
        val refPair: Pair<Pair<Float, Float>, Pair<Float, Float>> = Pair(points[it - 1], points[it])
        val remaining = points.slice(it until points.size)
        for (rt in remaining.size downTo 1) {
            val targetPair: Pair<Pair<Float, Float>, Pair<Float, Float>> =
                Pair(points[rt - 1], points[rt])
        }
    }
    return false
}

fun isLoop(points: List<Pair<Float, Float>>): Boolean {
    if (points.size < 10) return false

    val midpoint =
        Pair((points[5].first + points[0].first) / 2, (points[5].second + points[0].second) / 2)
    val finishLineA = rotate((PI / 2).toFloat(), points[0], midpoint)
    val finishLineB = rotate((PI / 2).toFloat(), points[5], midpoint)

    return false
}
