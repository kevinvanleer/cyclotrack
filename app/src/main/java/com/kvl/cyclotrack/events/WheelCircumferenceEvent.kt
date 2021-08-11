package com.kvl.cyclotrack.events

data class WheelCircumferenceEvent constructor(
    val circumference: Float?,
    val variance: Double? = null,
)