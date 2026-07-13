package com.kvl.cyclotrack.events

import com.kvl.cyclotrack.ThermalTier

data class ThermalTierEvent(val tier: ThermalTier, val tempC: Float)
