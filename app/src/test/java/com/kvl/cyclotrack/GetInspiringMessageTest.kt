package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class GetInspiringMessageTest {
    @Test
    fun getInspiringMessage_greatJob() {
        val expectedMessage = "Great job! Keep up the good work!"
        Assert.assertEquals(expectedMessage, getInspiringMessage(0 * 24 * 3600 * 1000))
        Assert.assertEquals(expectedMessage,
            getInspiringMessage((0.25 * 24 * 3600 * 1000).toLong()))
        Assert.assertEquals(expectedMessage, getInspiringMessage((0.5 * 24 * 3600 * 1000).toLong()))
        Assert.assertNotEquals(expectedMessage, getInspiringMessage(1 * 24 * 3600 * 1000))
        Assert.assertNotEquals(expectedMessage, getInspiringMessage(-1 * 24 * 3600 * 1000))
    }

    @Test
    fun getInspiringMessage_getSomeRest() {
        val expectedMessage =
            "Get some rest you earned it! Recovery is an important part of fitness."
        Assert.assertEquals(expectedMessage,
            getInspiringMessage((0.51 * 24 * 3600 * 1000).toLong()))
        Assert.assertEquals(expectedMessage, getInspiringMessage((1.5 * 24 * 3600 * 1000).toLong()))
        Assert.assertNotEquals(expectedMessage, getInspiringMessage(2 * 24 * 3600 * 1000))
        Assert.assertNotEquals(expectedMessage, getInspiringMessage(-1 * 24 * 3600 * 1000))
    }

    @Test
    fun getInspiringMessage_letsHitTheTrail() {
        val expectedMessage = "Alright! Let's hit the trail!"
        Assert.assertEquals(expectedMessage,
            getInspiringMessage((1.51 * 24 * 3600 * 1000).toLong()))
        Assert.assertEquals(expectedMessage, getInspiringMessage((3 * 24 * 3600 * 1000).toLong()))
        Assert.assertNotEquals(expectedMessage, getInspiringMessage(4 * 24 * 3600 * 1000))
        Assert.assertNotEquals(expectedMessage, getInspiringMessage(-1 * 24 * 3600 * 1000))
    }

    @Test
    fun getInspiringMessage_default() {
        Assert.assertNotEquals("It has been 0 days since your last ride. Let's make it happen!",
            getInspiringMessage((3.1 * 3600 * 1000).toLong()))
        Assert.assertEquals("It has been 3 days since your last ride. Let's make it happen!",
            getInspiringMessage((3.1 * 24 * 3600 * 1000).toLong()))
        Assert.assertEquals("It has been 3 days since your last ride. Let's make it happen!",
            getInspiringMessage((3.9 * 24 * 3600 * 1000).toLong()))
        Assert.assertEquals("It has been 10 days since your last ride. Let's make it happen!",
            getInspiringMessage((10.1 * 24 * 3600 * 1000).toLong()))
        Assert.assertEquals("It has been 100 days since your last ride. Let's make it happen!",
            getInspiringMessage((100.1 * 24 * 3600 * 1000).toLong()))
        Assert.assertEquals("It has been -1 days since your last ride. Let's make it happen!",
            getInspiringMessage((-1 * 24 * 3600 * 1000).toLong()))
    }
}