package com.kvl.cyclotrack.data

import java.time.ZonedDateTime

data class DailySummary(
    val date: ZonedDateTime,
    val distance: Double? = null,
    val duration: Double? = null,
)
