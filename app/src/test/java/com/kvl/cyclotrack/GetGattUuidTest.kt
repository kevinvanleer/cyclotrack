package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test
import java.util.*

class GetGattUuidTest {
    @Test
    fun getGattUuid_empty() {
        Assert.assertThrows(NumberFormatException::class.java) {
            getGattUuid("")
        }
    }

    @Test
    fun getGattUuid_invalid() {
        Assert.assertThrows(NumberFormatException::class.java) {
            getGattUuid("zxcv")
        }
        Assert.assertEquals(UUID.fromString("78912345-0000-1000-8000-00805f9b34fb"),
            getGattUuid("12345678912345"))
    }

    @Test
    fun getGattUuid_valid() {
        Assert.assertEquals(UUID.fromString("00001234-0000-1000-8000-00805f9b34fb"),
            getGattUuid("1234"))
        Assert.assertEquals(UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb"),
            getGattUuid("abcd"))
        Assert.assertEquals(UUID.fromString("00002037-0000-1000-8000-00805f9b34fb"),
            getGattUuid("2037"))
        Assert.assertEquals(UUID.fromString("00000001-0000-1000-8000-00805f9b34fb"),
            getGattUuid("1"))
    }
}