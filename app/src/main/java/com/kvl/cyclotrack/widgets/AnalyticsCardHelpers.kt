package com.kvl.cyclotrack.widgets

import com.kvl.cyclotrack.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

fun getThreeStatFromSplits(systemOfMeasurement: String?, splits: Array<Split>) =
    arrayOf(
        Pair("RIDES", splits.size.toString()),
        Pair(
            getUserDistanceUnitShort(systemOfMeasurement).uppercase(),
            getUserDistance(
                systemOfMeasurement,
                splits.sumOf { it.totalDistance }
            ).roundToInt()
                .toString()
        ),
        Pair(
            "HOURS",
            formatDurationHours(
                splits.sumOf { it.totalDuration }
            )
        )
    )

fun getTableFromSplits(systemOfMeasurement: String?, splits: Array<Split>) =
    splits.slice(IntRange(0, (splits.size - 1).coerceAtMost(2))).map { split ->
        listOf(
            Instant.ofEpochMilli(split.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE),
            "%.1f".format(
                getUserSpeed(
                    systemOfMeasurement,
                    split.totalDistance / split.totalDuration
                )
            ),
            formatDuration(split.totalDuration),
        )
    }
