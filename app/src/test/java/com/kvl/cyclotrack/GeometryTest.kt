package com.kvl.cyclotrack

import com.kvl.cyclotrack.util.rotate
import org.junit.Assert
import org.junit.Test
import kotlin.math.PI

class GeometryTest {
    @Test
    fun rotate_basic() {
        Assert.assertEquals(rotate((PI / 2).toFloat(), Pair(1f, 1f), Pair(0f, 0f)), Pair(-1f, -1f))
    }
}
