package com.kvl.cyclotrack

import com.kvl.cyclotrack.util.workToCalories
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkToCaloriesTest {
    // A real ride: 967 kJ at the pedals over 1h 26m. At 24% gross efficiency that is
    // roughly 963 kcal, and it cross-checks the heart-rate estimate for the same ride
    // (993 kcal) to within a few percent.
    @Test
    fun aRidesWorkConvertsToItsMetabolicCost() {
        assertEquals(963, workToCalories(967_000))
    }

    // Gross efficiency near 24% is why cyclists treat a kJ of work as a kcal burned.
    @Test
    fun kilojoulesAndCaloriesStayWithinAFewPercent() {
        listOf(200_000, 500_000, 1_000_000, 2_000_000).forEach { joules ->
            val kilojoules = joules / 1000
            val calories = workToCalories(joules)
            assertEquals(
                "work of $kilojoules kJ should read close to $kilojoules kcal",
                kilojoules.toDouble(),
                calories.toDouble(),
                kilojoules * 0.05,
            )
        }
    }

    @Test
    fun noWorkMeansNoCalories() {
        assertEquals(0, workToCalories(0))
    }
}
