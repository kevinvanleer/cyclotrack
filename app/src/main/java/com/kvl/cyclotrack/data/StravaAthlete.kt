package com.kvl.cyclotrack.data

import androidx.annotation.Keep

@Keep
data class StravaAthlete(
    val id: Long,
    val firstname: String? = null,
    val lastname: String? = null,
    val sex: String? = null,
    val ftp: Int? = null,
    val weight: Float? = null,
)
