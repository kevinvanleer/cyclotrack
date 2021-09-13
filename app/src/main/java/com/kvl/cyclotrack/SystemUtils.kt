package com.kvl.cyclotrack

import java.time.Clock
import java.time.Instant

class SystemUtils private constructor() {
    companion object {
        fun currentTimeMillis(clock: Clock = Clock.systemUTC()): Long {
            return Instant.now(clock).toEpochMilli()

        }
    }

    init {
        throw AssertionError()
    }
}