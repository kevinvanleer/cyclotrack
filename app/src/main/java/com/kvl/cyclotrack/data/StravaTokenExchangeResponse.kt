package com.kvl.cyclotrack.data

import androidx.annotation.Keep

@Keep
data class StravaTokenExchangeResponse(
    val expires_at: Long,
    val expires_in: Int,
    val refresh_token: String,
    val athlete: String
)
