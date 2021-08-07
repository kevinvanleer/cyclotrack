package com.kvl.cyclotrack

data class CircumferenceState constructor(
    val measuring: Boolean,
    val initialCircDistance: Double,
    val initialCircRevs: Int,
    val circumference: Float?,
)