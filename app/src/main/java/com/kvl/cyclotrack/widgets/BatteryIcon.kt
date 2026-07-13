package com.kvl.cyclotrack.widgets

import com.kvl.cyclotrack.R

fun batteryLevelDrawable(level: Int): Int = when {
    (level > 99) -> R.drawable.ic_baseline_battery_full_24
    (level >= 90) -> R.drawable.ic_baseline_battery_6_bar_24
    (level >= 75) -> R.drawable.ic_baseline_battery_5_bar_24
    (level >= 50) -> R.drawable.ic_baseline_battery_4_bar_24
    (level >= 25) -> R.drawable.ic_baseline_battery_3_bar_24
    (level >= 15) -> R.drawable.ic_baseline_battery_2_bar_24
    (level >= 0) -> R.drawable.ic_battery_alert_24
    else -> R.drawable.ic_baseline_battery_unknown_24
}
