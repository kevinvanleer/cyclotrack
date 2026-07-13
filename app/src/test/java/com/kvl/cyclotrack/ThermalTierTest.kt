package com.kvl.cyclotrack

import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalTierTest {
    // explicit dwell: THERMAL_ENTRY_DWELL_MS is 5s in dev/debug builds
    private fun walk(
        samples: List<Pair<Long, Float>>,
        start: ThermalTierState = ThermalTierState(),
    ): ThermalTierState =
        samples.fold(start) { state, (t, temp) ->
            nextThermalTierState(state, temp, t, dwellMs = 5 * 60_000L)
        }

    private val min = 60_000L

    @Test
    fun subZeroTemperaturesResolveToNormal() {
        // Float.MIN_VALUE regression: winter temps at or below 0°C must match NORMAL
        assertEquals(ThermalTier.NORMAL, walk(listOf(0L to 0.0f)).tier)
        assertEquals(ThermalTier.NORMAL, walk(listOf(0L to -12.5f)).tier)
    }

    @Test
    fun sustainedHeatEscalatesAfterDwell() {
        val state = walk(
            listOf(
                0L to 38.5f,
                2 * min to 38.6f,
                4 * min to 38.7f,
                5 * min to 38.8f,
            )
        )
        assertEquals(ThermalTier.WARM, state.tier)
    }

    @Test
    fun doesNotEscalateBeforeDwell() {
        val state = walk(listOf(0L to 38.5f, 4 * min to 38.6f))
        assertEquals(ThermalTier.NORMAL, state.tier)
    }

    @Test
    fun coolingTransientFallsThroughBandsBeforeDwell() {
        // a cooldown spends less than the dwell in each band on its way through, so
        // it never escalates; the immediate floor (one below the initial HOT target)
        // applies from sample one
        val state = walk(
            listOf(
                0L to 42.1f,
                3 * min to 41.0f,
                6 * min to 40.0f,
                9 * min to 39.0f,
            )
        )
        assertEquals(ThermalTier.WARM, state.tier)
    }

    @Test
    fun oscillationAboveThresholdStillEscalates() {
        // ride 846: >=38°C for 97 min but ±0.1°C airflow jitter — cooling ticks must
        // NOT reset the dwell while temp stays in the tier's band
        val state = walk(
            listOf(
                0L to 38.0f,
                1 * min to 38.5f,
                2 * min to 38.3f,
                3 * min to 38.7f,
                4 * min to 38.2f,
                5 * min to 38.4f,
            )
        )
        assertEquals(ThermalTier.WARM, state.tier)
    }

    @Test
    fun preRideSoakReleasesOnceAirflowCoolsToNormal() {
        // trip 844 profile: mount soak to 42°C rides the immediate WARM floor
        // through the cooldown, releasing when temp reaches normal range
        val state = walk(
            listOf(
                0L to 42.1f,
                1 * min to 41.0f,
                3 * min to 38.5f,
                5 * min to 36.7f,
                10 * min to 35.4f,
            )
        )
        assertEquals(ThermalTier.NORMAL, state.tier)
    }

    @Test
    fun startAboveSleepThresholdAppliesDimImmediately() {
        // ride starts at 46.5°C: HOT (DIM) at once, EXTREME only after the dwell
        val first = walk(listOf(0L to 46.5f))
        assertEquals(ThermalTier.HOT, first.tier)
        val after = walk(listOf(5 * min to 46.6f), start = first)
        assertEquals(ThermalTier.EXTREME, after.tier)
    }

    @Test
    fun secondThresholdCrossingBumpsTierImmediately() {
        // WARM candidate mid-dwell, then temp crosses the HOT threshold:
        // tier bumps to WARM (the crossed tier) at once; HOT still waits its dwell
        val state = walk(
            listOf(
                0L to 39.0f,
                2 * min to 43.0f,
            )
        )
        assertEquals(ThermalTier.WARM, state.tier)
        val after = walk(listOf(8 * min to 43.5f), start = state)
        assertEquals(ThermalTier.HOT, after.tier)
    }

    @Test
    fun steadyTempCarriesDwellClock() {
        // dedupe means steady temp arrives as repeated equal samples (via Level events):
        // equal is not cooling, the clock must keep running
        val state = walk(
            listOf(
                0L to 43.0f,
                2 * min to 43.0f,
                5 * min to 43.0f,
            )
        )
        assertEquals(ThermalTier.HOT, state.tier)
    }

    @Test
    fun latchesUntilNormalRange() {
        val hot = walk(listOf(0L to 43.0f, 5 * min to 43.0f))
        assertEquals(ThermalTier.HOT, hot.tier)
        val still = walk(listOf(10 * min to 39.0f), start = hot)
        assertEquals(ThermalTier.HOT, still.tier)
        val released = walk(listOf(20 * min to 35.9f), start = hot)
        assertEquals(ThermalTier.NORMAL, released.tier)
    }

    @Test
    fun escalatesFurtherFromLatchedTier() {
        val warm = walk(listOf(0L to 39.0f, 5 * min to 39.5f))
        assertEquals(ThermalTier.WARM, warm.tier)
        val extreme = walk(
            listOf(10 * min to 45.5f, 16 * min to 45.6f),
            start = warm,
        )
        assertEquals(ThermalTier.EXTREME, extreme.tier)
    }
}
