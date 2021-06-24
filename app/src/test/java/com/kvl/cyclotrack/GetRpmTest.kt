package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class GetRpmTest {
    @Test
    fun getRpm_divideByZero() {
        Assert.assertEquals(Float.POSITIVE_INFINITY, getRpm(10, 0, 0, 0))
        Assert.assertEquals(Float.POSITIVE_INFINITY, getRpm(10, 5, 0, 0))
        Assert.assertEquals(Float.POSITIVE_INFINITY, getRpm(10, 5, 5, 5))
    }

    @Test
    fun getRpm_stationary() {
        Assert.assertEquals(0f, getRpm(0, 0, 0, 0))
        Assert.assertEquals(0f, getRpm(0, 0, 5, 5))
        Assert.assertEquals(0f, getRpm(123123, 123123, 9834, 9834))
        Assert.assertEquals(0f, getRpm(123123, 123123, 9834, 8234))
        Assert.assertEquals(0f, getRpm(123123, 123123, 2834, 8234))
    }

    @Test
    fun getRpm_valid() {
        Assert.assertEquals(10f, getRpm(20, 10, 2 * 60 * 1024, 1 * 60 * 1024))
    }

    @Test
    fun getRpm_rollover() {
        Assert.assertEquals(10f, getRpm(5, 65536 - 5, 2 * 60 * 1024, 1 * 60 * 1024))
        Assert.assertEquals(10f, getRpm(3251234, 3251224, 2 * 60 * 1024, 1 * 60 * 1024))
        Assert.assertEquals(10f, getRpm(20, 10, 0, 65536 - (60 * 1024)))
        Assert.assertEquals(10f, getRpm(20, 10, 1024, 65536 - (59 * 1024)))
        Assert.assertEquals(1f, getRpm(0, 65535, 60 * 1024, 0))
    }
}