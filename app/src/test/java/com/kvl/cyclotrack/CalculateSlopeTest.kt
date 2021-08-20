package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.DerivedTripState
import org.junit.Assert
import org.junit.Test

class CalculateSlopeTest {
    @Test
    fun calculateSlopeTest_allZeros() {
        Assert.assertEquals(0.0, calculateSlope(newSpeed = 0f,
            newAltitude = 0.0,
            oldAltitude = 0.0,
            distanceDelta = 0f,
            speedThreshold = 0f,
            durationDelta = 0.0,
            verticalAccuracy = 0f,
            horizontalAccuracy = 0f,
            oldSlope = 0.0), 0.0)
    }

    @Test
    fun calculateSlopeTest_tooFast() {
        Assert.assertEquals(7.0, calculateSlope(
            newSpeed = 5f,
            newAltitude = 1.0,
            oldAltitude = 0.0,
            distanceDelta = 1f,
            speedThreshold = 0f,
            durationDelta = 0.1,
            verticalAccuracy = 1f,
            horizontalAccuracy = 1f,
            oldSlope = 7.0), 1e-6)
    }

    @Test
    fun calculateSlopeTest_one() {
        Assert.assertEquals(1.0, calculateSlope(
            newSpeed = 5f,
            newAltitude = 1.0,
            oldAltitude = 0.0,
            distanceDelta = 1f,
            speedThreshold = 0f,
            durationDelta = 1.0,
            verticalAccuracy = 1f,
            horizontalAccuracy = 1f,
            oldSlope = 1.0), 1e-6)
    }

    @Test
    fun calculateSlopeLeastSquares_NaN() {
        val testData = listOf(DerivedTripState(
            tripId = -1L,
            timestamp = 0L,
            duration = 0.0,
            durationDelta = 0.0,
            totalDistance = 0.0,
            distanceDelta = 0.0,
            altitude = 0.0,
            altitudeDelta = 0.0,
            circumference = 0.0,
            revTotal = 0,
            slope = 0.0,
            speedRevolutions = null))
        Assert.assertEquals(Double.NaN, calculateSlopeLeastSquaresFit(
            testData), 1e-6)
    }

    @Test
    fun calculateSlopeLeastSquares_oneItem() {
        val testData = listOf(DerivedTripState(
            tripId = -1L,
            timestamp = 0L,
            duration = 0.0,
            durationDelta = 0.0,
            totalDistance = 10.0,
            distanceDelta = 0.0,
            altitude = 10.0,
            altitudeDelta = 0.0,
            circumference = 0.0,
            revTotal = 0,
            slope = 0.0,
            speedRevolutions = null))
        Assert.assertEquals(Double.NaN, calculateSlopeLeastSquaresFit(
            testData), 1e-6)
    }

    @Test
    fun calculateSlopeLeastSquares_twoItems() {
        var templateDerivedState = DerivedTripState(
            tripId = -1L,
            timestamp = 0L,
            duration = 0.0,
            durationDelta = 0.0,
            totalDistance = 0.0,
            distanceDelta = 0.0,
            altitude = 0.0,
            altitudeDelta = 0.0,
            circumference = 0.0,
            revTotal = 0,
            slope = 0.0,
            speedRevolutions = null)
        Assert.assertEquals(1.0, calculateSlopeLeastSquaresFit(
            listOf(templateDerivedState,
                templateDerivedState.copy(totalDistance = 10.0, altitude = 10.0))
        ), 1e-6)
        Assert.assertEquals(0.04443139314, calculateSlopeLeastSquaresFit(
            listOf(templateDerivedState.copy(altitude = 122.0980225),
                templateDerivedState.copy(totalDistance = 2.197911051, altitude = 122.1956787))
        ), 1e-6)
        Assert.assertEquals(0.1, calculateSlopeLeastSquaresFit(
            listOf(templateDerivedState.copy(altitude = 100.0),
                templateDerivedState.copy(totalDistance = 50.0, altitude = 105.0))
        ), 1e-6)
    }

    @Test
    fun calculateSlopeLeastSquares_threeItems() {
        val testData = listOf(
            DerivedTripState(
                tripId = -1L,
                timestamp = 0L,
                duration = 0.0,
                durationDelta = 0.0,
                totalDistance = 0.0,
                distanceDelta = 0.0,
                altitude = 0.0,
                altitudeDelta = 0.0,
                circumference = 0.0,
                revTotal = 0,
                slope = 0.0,
                speedRevolutions = null),
            DerivedTripState(
                tripId = -1L,
                timestamp = 0L,
                duration = 0.0,
                durationDelta = 0.0,
                totalDistance = 5.0,
                distanceDelta = 0.0,
                altitude = 5.0,
                altitudeDelta = 0.0,
                circumference = 0.0,
                revTotal = 0,
                slope = 0.0,
                speedRevolutions = null),
            DerivedTripState(
                tripId = -1L,
                timestamp = 0L,
                duration = 0.0,
                durationDelta = 0.0,
                totalDistance = 10.0,
                distanceDelta = 0.0,
                altitude = 10.0,
                altitudeDelta = 0.0,
                circumference = 0.0,
                revTotal = 0,
                slope = 0.0,
                speedRevolutions = null)
        )
        Assert.assertEquals(1.0, calculateSlopeLeastSquaresFit(
            testData), 1e-6)
    }

    @Test
    fun calculateSlopeLeastSquares_leastSquares() {
        var templateDerivedState = DerivedTripState(
            tripId = -1L,
            timestamp = 0L,
            duration = 0.0,
            durationDelta = 0.0,
            totalDistance = 0.0,
            distanceDelta = 0.0,
            altitude = 0.0,
            altitudeDelta = 0.0,
            circumference = 0.0,
            revTotal = 0,
            slope = 0.0,
            speedRevolutions = null)
        val testData = listOf(
            templateDerivedState.copy(altitude = 100.0),
            templateDerivedState.copy(totalDistance = 10.0, altitude = 102.0),
            templateDerivedState.copy(totalDistance = 20.0, altitude = 101.0),
            templateDerivedState.copy(totalDistance = 30.0, altitude = 103.0),
            templateDerivedState.copy(totalDistance = 40.0, altitude = 105.0),
            templateDerivedState.copy(totalDistance = 50.0, altitude = 104.0),
        )
        Assert.assertEquals(0.0886, calculateSlopeLeastSquaresFit(
            testData), 1e-3)
    }
}