package com.kvl.cyclotrack

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kvl.cyclotrack.events.BatteryEvent
import com.kvl.cyclotrack.events.ThermalTierEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import javax.inject.Inject
import javax.inject.Singleton

enum class ThermalTier(val enterC: Float, val level: DisplayLevel) {
    // NEGATIVE_INFINITY, not MIN_VALUE: MIN_VALUE is the smallest POSITIVE float,
    // which would leave sub-zero temperatures matching no tier at all
    NORMAL(Float.NEGATIVE_INFINITY, DisplayLevel.NORMAL),
    WARM(38f, DisplayLevel.OUTLINE),
    HOT(42f, DisplayLevel.DIM),
    EXTREME(45f, DisplayLevel.SLEEP),
}

// 5s in dev builds so tier walks are testable without waiting out the dwell
val THERMAL_ENTRY_DWELL_MS = if (FeatureFlags.devBuild) 5_000L else 5 * 60_000L

data class ThermalTierState(
    val tier: ThermalTier = ThermalTier.NORMAL,
    val candidateTier: ThermalTier = ThermalTier.NORMAL,
    val candidateSinceMs: Long = 0L,
    val lastTempC: Float = Float.NaN,
)

// Escalation to the target requires the dwell spent continuously in the tier's band —
// the clock resets only when the target tier changes, NOT on cooling ticks. Ride 846:
// battery sat >=38°C for 97 min but ±0.1°C airflow jitter meant the longest
// monotonic run was the one stationary sun soak, so a reset-on-cooling rule made
// WARM/DIM unreachable while riding. Cooling transients (trip 844's mount-soak
// cooldown) are still rejected: they fall through each band faster than the dwell.
// One tier below target applies immediately, so the dwell gates only the final step.
// De-escalation latches until temp falls into normal range.
fun nextThermalTierState(
    state: ThermalTierState,
    batteryTempC: Float,
    nowMs: Long,
    dwellMs: Long = THERMAL_ENTRY_DWELL_MS,
): ThermalTierState {
    val target = ThermalTier.entries.last { batteryTempC >= it.enterC }
    val candidateSinceMs =
        if (target != state.candidateTier) nowMs else state.candidateSinceMs
    val immediate = ThermalTier.entries[maxOf(0, target.ordinal - 1)]
    val tier = when {
        batteryTempC < ThermalTier.WARM.enterC - 2f -> ThermalTier.NORMAL
        target > state.tier && nowMs - candidateSinceMs >= dwellMs -> target
        else -> maxOf(state.tier, immediate)
    }
    return ThermalTierState(tier, target, candidateSinceMs, batteryTempC)
}

// Trip-scoped tier machine: started/stopped by TripInProgressService alongside
// BatterySource. Publishes the current tier as a sticky event so late subscribers
// (a recreated dashboard ViewModel after navigation) seed immediately.
@Singleton
class ThermalMonitor @Inject constructor() {
    private var state = ThermalTierState()
    private var started = false
    private val handler = Handler(Looper.getMainLooper())

    // steady temp emits no events (source dedupe): when an escalation is pending,
    // re-evaluate exactly when the dwell expires instead of waiting for the next event
    private val dwellCallback = Runnable {
        state.lastTempC.takeIf { !it.isNaN() }?.let { evaluate(it) }
    }

    fun start() {
        if (started) return
        started = true
        EventBus.getDefault().register(this)
    }

    fun stop() {
        if (!started) return
        started = false
        EventBus.getDefault().unregister(this)
        handler.removeCallbacks(dwellCallback)
        state = ThermalTierState()
        EventBus.getDefault().removeStickyEvent(ThermalTierEvent::class.java)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBatteryEvent(event: BatteryEvent) {
        val tempC = (event as? BatteryEvent.Temperature)?.celsius ?: state.lastTempC
        if (!tempC.isNaN()) evaluate(tempC)
    }

    private fun evaluate(batteryTempC: Float) {
        // monotonic clock: a wall-clock adjustment mid-dwell must not satisfy or
        // postpone the escalation window
        val now = SystemClock.elapsedRealtime()
        val previousTier = state.tier
        state = nextThermalTierState(state, batteryTempC, now)
        handler.removeCallbacks(dwellCallback)
        if (state.candidateTier > state.tier) {
            handler.postDelayed(
                dwellCallback,
                (state.candidateSinceMs + THERMAL_ENTRY_DWELL_MS - now).coerceAtLeast(0) + 100
            )
        }
        if (state.tier != previousTier ||
            EventBus.getDefault().getStickyEvent(ThermalTierEvent::class.java) == null
        ) {
            EventBus.getDefault().postSticky(ThermalTierEvent(state.tier, batteryTempC))
        }
    }
}
