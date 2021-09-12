package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.DerivedTripState
import org.junit.Assert
import org.junit.Test

class CalculateWheelCircumferenceTest {
    @Test
    fun calculateWheelCircumference_emptyList() {
        Assert.assertEquals(null, calculateWheelCircumference(emptyArray(), 50, 1e-6))
    }

    @Test
    fun calculateWheelCircumference_NaN() {
        val testArray = Array(100) { DerivedTripState(circumference = Double.NaN) }
        Assert.assertEquals(null, calculateWheelCircumference(testArray, 50, 1e-6))
    }

    @Test
    fun calculateWheelCircumference_notEnough() {
        val testArray = Array(49) { DerivedTripState(circumference = 1981.0) }
        Assert.assertEquals(null, calculateWheelCircumference(testArray, 50, 1e-6))
    }

    @Test
    fun calculateWheelCircumference_validCircumference() {
        val testArray = Array(100) { DerivedTripState(circumference = 1981.0) }
        Assert.assertEquals(
            1981f,
            calculateWheelCircumference(testArray, 50, 1e-6) ?: 0f,
            1e-6f
        )
    }
}