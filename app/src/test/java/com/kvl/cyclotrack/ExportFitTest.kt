package com.kvl.cyclotrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFitTest {
    private fun ts(state: TimeStateEnum, secsAfterEpoch: Long): TimeState =
        TimeState(tripId = 1L, state = state, timestamp = secsAfterEpoch * 1000L)

    @Test
    fun fitSec_convertsMillisToFitSecondsSinceEpoch() {
        // Unix sec 631_065_600 == FIT sec 0 (1989-12-31 UTC)
        assertEquals(0L, fitSec(631_065_600_000L))
        assertEquals(1L, fitSec(631_065_601_000L))
        // Sub-second portion truncated
        assertEquals(10L, fitSec(631_065_610_999L))
    }

    @Test
    fun pauseIntervals_emptyOrNullReturnsEmpty() {
        assertEquals(emptyList<LongRange>(), pauseIntervals(null))
        assertEquals(emptyList<LongRange>(), pauseIntervals(emptyArray()))
    }

    @Test
    fun pauseIntervals_pauseResumePairProducesOneRange() {
        val states = arrayOf(
            ts(TimeStateEnum.START, 631_065_700L),
            ts(TimeStateEnum.PAUSE, 631_065_710L),
            ts(TimeStateEnum.RESUME, 631_065_720L),
            ts(TimeStateEnum.STOP, 631_065_730L),
        )
        assertEquals(listOf(110L until 120L), pauseIntervals(states))
    }

    @Test
    fun pauseIntervals_multiplePausesProduceMultipleRanges() {
        val states = arrayOf(
            ts(TimeStateEnum.START, 631_065_700L),
            ts(TimeStateEnum.PAUSE, 631_065_710L),
            ts(TimeStateEnum.RESUME, 631_065_715L),
            ts(TimeStateEnum.PAUSE, 631_065_720L),
            ts(TimeStateEnum.RESUME, 631_065_727L),
            ts(TimeStateEnum.STOP, 631_065_730L),
        )
        assertEquals(listOf(110L until 115L, 120L until 127L), pauseIntervals(states))
    }

    @Test
    fun pauseIntervals_pauseEndedByStopIsNotARange() {
        // Trailing PAUSE that is closed by STOP should not be treated as a pause interval
        val states = arrayOf(
            ts(TimeStateEnum.START, 631_065_700L),
            ts(TimeStateEnum.PAUSE, 631_065_710L),
            ts(TimeStateEnum.STOP, 631_065_715L),
        )
        assertEquals(emptyList<LongRange>(), pauseIntervals(states))
    }

    @Test
    fun bySecMean_emptyInputReturnsEmpty() {
        val result = bySecMean<Pair<Long, Double>>(null, { it.first }, { it.second })
        assertEquals(emptyMap<Long, Double>(), result)
    }

    @Test
    fun bySecMean_averagesSamplesWithinSecond() {
        val samples = listOf(
            Pair(1000L, 100.0),
            Pair(1500L, 200.0),  // same FIT-sec as above
            Pair(2200L, 50.0),
        )
        val result = bySecMean(samples, { it.first / 1000 }, { it.second })
        assertEquals(mapOf(1L to 150.0, 2L to 50.0), result)
    }

    @Test
    fun bySecMean_singleSamplePerSecondReturnsThatValue() {
        val samples = listOf(
            Pair(1000L, 100.0),
            Pair(2000L, 200.0),
        )
        val result = bySecMean(samples, { it.first / 1000 }, { it.second })
        assertEquals(mapOf(1L to 100.0, 2L to 200.0), result)
    }

    @Test
    fun interpolateGaps_emptyOrSingleSampleReturnsAsIs() {
        assertEquals(emptyList<Pair<Long, Double>>(), interpolateGaps(emptyMap(), emptySet()))
        assertEquals(listOf(5L to 100.0), interpolateGaps(mapOf(5L to 100.0), emptySet()))
    }

    @Test
    fun interpolateGaps_consecutiveSecondsHaveNoFill() {
        val input = mapOf(1L to 10.0, 2L to 20.0, 3L to 30.0)
        assertEquals(input.toList(), interpolateGaps(input, emptySet()))
    }

    @Test
    fun interpolateGaps_linearFillBetweenSamples() {
        // 100 at sec 1, 200 at sec 5 → fill 125, 150, 175 at secs 2, 3, 4
        val input = mapOf(1L to 100.0, 5L to 200.0)
        val result = interpolateGaps(input, emptySet())
        assertEquals(
            listOf(1L to 100.0, 2L to 125.0, 3L to 150.0, 4L to 175.0, 5L to 200.0),
            result
        )
    }

    @Test
    fun interpolateGaps_skipsPausedSeconds() {
        // Gap from 1..5; secs 2,3 paused → not filled. Sec 4 still filled
        val input = mapOf(1L to 100.0, 5L to 200.0)
        val pausedSecs = setOf(2L, 3L)
        val result = interpolateGaps(input, pausedSecs)
        assertEquals(
            listOf(1L to 100.0, 4L to 175.0, 5L to 200.0),
            result
        )
    }

    @Test
    fun interpolateGaps_doesNotExtrapolateBeyondBounds() {
        // Only sec 5 and 10 — nothing emitted at secs 1-4 or 11+
        val input = mapOf(5L to 100.0, 10L to 200.0)
        val result = interpolateGaps(input, emptySet())
        assertTrue(result.first().first == 5L)
        assertTrue(result.last().first == 10L)
    }

    @Test
    fun interpolateGaps_unsortedInputProducesSortedOutput() {
        val input = mapOf(5L to 200.0, 1L to 100.0)
        val result = interpolateGaps(input, emptySet())
        assertEquals(
            listOf(1L to 100.0, 2L to 125.0, 3L to 150.0, 4L to 175.0, 5L to 200.0),
            result
        )
    }
}
