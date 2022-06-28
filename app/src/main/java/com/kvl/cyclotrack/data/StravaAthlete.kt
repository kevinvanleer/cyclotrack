package com.kvl.cyclotrack.data

import androidx.annotation.Keep

@Keep
data class StravaAthlete(
    val id: Long,
    val firstname: String,
    val lastname: String,
    val sex: String,
    val ftp: Integer,
    val weight: Float
)
