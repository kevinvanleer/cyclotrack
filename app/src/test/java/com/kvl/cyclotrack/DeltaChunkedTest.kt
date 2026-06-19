package com.kvl.cyclotrack

import org.junit.Assert.assertEquals
import org.junit.Test

class DeltaChunkedTest {
    // Simulate how the delta dataset builders consume a chunk: skip the first
    // element (it only seeds `last`), then emit a (prev, curr) pair for each
    // remaining element. Collect those pairs across every chunk.
    private fun emittedPairs(n: Int, size: Int): List<Pair<Int, Int>> =
        (0 until n).toList().deltaChunked(size).flatMap { chunk ->
            chunk.zipWithNext()
        }

    private fun expectedPairs(n: Int): List<Pair<Int, Int>> =
        (1 until n).map { it - 1 to it }

    @Test
    fun coversEveryConsecutivePairExactlyOnce_acrossBoundaries() {
        for (n in listOf(0, 1, 2, 999, 1000, 1001, 1999, 2000, 2001, 5500)) {
            assertEquals(
                "n=$n must yield every consecutive pair once with no boundary drop/dup",
                expectedPairs(n),
                emittedPairs(n, 1000),
            )
        }
    }

    @Test
    fun smallList_isSingleChunk() {
        assertEquals(listOf((0 until 500).toList()), (0 until 500).toList().deltaChunked(1000))
    }

    @Test
    fun chunksAreBounded() {
        (0 until 5500).toList().deltaChunked(1000).forEach { assertEquals(true, it.size <= 1000) }
    }
}
