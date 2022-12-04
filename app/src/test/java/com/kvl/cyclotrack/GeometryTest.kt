package com.kvl.cyclotrack

import com.kvl.cyclotrack.util.rotate
import org.junit.Assert
import org.junit.Test
import kotlin.math.PI

class GeometryTest {
    @Test
    fun rotate_basic() {
        var result = rotate((PI / 2).toFloat(), Pair(1f, 1f), Pair(0f, 0f))
        Assert.assertEquals(-1f, result.first, 0.001f)
        Assert.assertEquals(1f, result.second, 0.001f)

        result = rotate((PI / 2).toFloat(), Pair(1f, 1f), Pair(0f, 1f))
        Assert.assertEquals(0f, result.first, 0.001f)
        Assert.assertEquals(2f, result.second, 0.001f)

        result = rotate((PI / 2).toFloat(), Pair(1f, 1f), Pair(1f, 0f))
        Assert.assertEquals(0f, result.first, 0.001f)
        Assert.assertEquals(0f, result.second, 0.001f)

        result = rotate((PI / 2).toFloat(), Pair(0.6f, 0.8f), Pair(0.1f, 0.7f))
        Assert.assertEquals(0f, result.first, 0.001f)
        Assert.assertEquals(1.2f, result.second, 0.001f)

        result = rotate((PI / 2).toFloat(), Pair(-0.6f, 0.8f), Pair(0.1f, 0.7f))
        Assert.assertEquals(0f, result.first, 0.001f)
        Assert.assertEquals(1.2f, result.second, 0.001f)
    }
}
