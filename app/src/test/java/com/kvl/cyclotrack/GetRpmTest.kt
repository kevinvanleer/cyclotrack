package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class GetRpmTest {
    @Test
    fun getRpm_divideByZero() {
        Assert.assertEquals(Float.NaN, getRpm(0, 0, 0, 0))
        Assert.assertEquals(Float.POSITIVE_INFINITY, getRpm(10, 0, 0, 0))
        Assert.assertEquals(Float.POSITIVE_INFINITY, getRpm(10, 5, 0, 0))
        Assert.assertEquals(Float.NaN, getRpm(0, 0, 5, 5))
        Assert.assertEquals(Float.POSITIVE_INFINITY, getRpm(10, 5, 5, 5))
    }

    @Test
    fun getRpm_valid() {
        Assert.assertEquals(10f, getRpm(20, 10, 2 * 60 * 1024, 1 * 60 * 1024))
    }

    @Test
    fun getRpm_rollover() {
        Assert.assertEquals(10f, getRpm(5, 65535 - 5, 2 * 60 * 1024, 1 * 60 * 1024))
        Assert.assertEquals(10f, getRpm(3251234, 3251224, 2 * 60 * 1024, 1 * 60 * 1024))
        Assert.assertEquals(10f, getRpm(20, 10, 0, 65535 - (60 * 1024)))
        Assert.assertEquals(10f, getRpm(20, 10, 1024, 65535 - (59 * 1024)))
    }
}