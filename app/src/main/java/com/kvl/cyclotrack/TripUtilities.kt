package com.kvl.cyclotrack

import kotlin.math.roundToInt


fun formatDuration(value: Double): String {
    var formattedString = ""
    if (value < 1.0) {
        formattedString += "zero seconds"
    } else if (value < 60) {
        formattedString += "${value.roundToInt()} sec"
    } else if (value < 3600) {
        val minutes = value / 60
        val minutePart = minutes.toLong()
        val seconds = (minutes - minutePart) * 60
        val secondPart = seconds.toLong()
        formattedString += "${minutePart}m ${secondPart}s"
    } else {
        val hours = value / 3600
        val hourPart = hours.toLong()
        val minutes = (hours - hourPart) * 60
        val minutePart = minutes.toLong()
        val seconds = (minutes - minutePart) * 60
        val secondPart = seconds.roundToInt()
        formattedString += "${hourPart}h ${minutePart}m ${secondPart}s"
    }
    return formattedString
}
