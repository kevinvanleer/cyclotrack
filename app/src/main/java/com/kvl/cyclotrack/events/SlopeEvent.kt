package com.kvl.cyclotrack.events

// Grade (rise/run fraction) update, driven by the gravity sensor independently of GPS.
data class SlopeEvent(val slope: Double)
