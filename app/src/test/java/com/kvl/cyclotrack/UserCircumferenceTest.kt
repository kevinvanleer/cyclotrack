package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class UserCircumferenceTest {
    @Test
    fun metersToUserCircumference_null() {
        Assert.assertEquals("2.032", metersToUserCircumference(2.032f, null))
        Assert.assertEquals("1.030", metersToUserCircumference(1.03f, null))
        Assert.assertEquals("1.040", metersToUserCircumference(1.04f, null))
    }

    @Test
    fun metersToUserCircumference_empty() {
        Assert.assertEquals("2.032", metersToUserCircumference(2.032f, ""))
        Assert.assertEquals("1.030", metersToUserCircumference(1.03f, ""))
        Assert.assertEquals("1.040", metersToUserCircumference(1.04f, ""))
    }

    @Test
    fun metersToUserCircumference_meters() {
        Assert.assertEquals("2.032", metersToUserCircumference(2.032f, "2.037"))
        Assert.assertEquals("1.030", metersToUserCircumference(1.03f, "2.037"))
        Assert.assertEquals("1.040", metersToUserCircumference(1.04f, "1"))
    }

    @Test
    fun metersToUserCircumference_mm() {
        Assert.assertEquals("2032", metersToUserCircumference(2.032f, "2037"))
        Assert.assertEquals("1030", metersToUserCircumference(1.03f, "2037"))
        Assert.assertEquals("1040", metersToUserCircumference(1.04f, "1000"))
        Assert.assertEquals("2040", metersToUserCircumference(2.04f, "3300"))
    }

    @Test
    fun metersToUserCircumference_in() {
        Assert.assertEquals("80.00", metersToUserCircumference(2.032f, "80"))
        Assert.assertEquals("40.55", metersToUserCircumference(1.03f, "80.125"))
        Assert.assertEquals("40.94", metersToUserCircumference(1.04f, "40"))
        Assert.assertEquals("80.31", metersToUserCircumference(2.04f, "119"))
    }

    @Test
    fun userCircumferenceToMeters_empty() {
        Assert.assertEquals(null, userCircumferenceToMeters(""))
    }

    @Test
    fun userCircumferenceToMeters_invalid() {
        Assert.assertEquals(null, userCircumferenceToMeters("abc"))
        Assert.assertEquals(null, userCircumferenceToMeters("12"))
        Assert.assertEquals(null, userCircumferenceToMeters("20000"))
    }

    @Test
    fun userCircumferenceToMeters_meters() {
        Assert.assertEquals(2.037f, userCircumferenceToMeters("2.037"))
        Assert.assertEquals(1.1f, userCircumferenceToMeters("1.1"))
        Assert.assertEquals(5.2f, userCircumferenceToMeters("5.2"))
    }

    @Test
    fun userCircumferenceToMeters_mm() {
        Assert.assertEquals(2.037f, userCircumferenceToMeters("2037"))
        Assert.assertEquals(1.1f, userCircumferenceToMeters("1100"))
    }

    @Test
    fun userCircumferenceToMeters_in() {
        Assert.assertEquals(2.032f, userCircumferenceToMeters("80")!!, 1e-3f)
        Assert.assertEquals(2.037f, userCircumferenceToMeters("80.19")!!, 1e-3f)
        Assert.assertEquals(0.762f, userCircumferenceToMeters("30")!!, 1e-3f)
    }
}