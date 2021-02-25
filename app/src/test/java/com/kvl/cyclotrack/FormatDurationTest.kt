package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class FormatDurationTest {
    @Test
    fun formatDuration_lessThanOneSecond() {
        Assert.assertEquals("zero seconds", formatDuration(0.9))
        Assert.assertEquals("zero seconds", formatDuration(0.0))
        Assert.assertEquals("zero seconds", formatDuration(-1.0))
    }

    @Test
    fun formatDuration_lessThanOneMinute() {
        Assert.assertEquals("11 sec", formatDuration(10.9))
        Assert.assertEquals("1 sec", formatDuration(1.0))
        Assert.assertEquals("60 sec", formatDuration(59.9))
    }

    @Test
    fun formatDuration_lessThanOneHour() {
        Assert.assertEquals("1m 0s", formatDuration(60.0))
        Assert.assertEquals("30m 47s", formatDuration(1847.1))
        Assert.assertEquals("59m 59s", formatDuration(3599.9))
    }

    @Test
    fun formatDuration_lessThanOneDay() {
        Assert.assertEquals("1h 0m 0s", formatDuration(3600.0))
        Assert.assertEquals("1h 0m 0s", formatDuration(3600.1))
        Assert.assertEquals("1h 0m 1s", formatDuration(3601.1))
        Assert.assertEquals("1h 1m 1s", formatDuration(3661.1))
        Assert.assertEquals("2h 40m 12s", formatDuration(9612.34))
        Assert.assertEquals("961h 0m 12s", formatDuration(3459612.34))
    }
}