package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SequentialDelayPolicyTest {
    @Test
    fun roundsPositiveSubMillisecondIntervalsUpToOneMillisecond() {
        assertEquals(1L, SequentialDelayPolicy.toDelayMillis(1L))
        assertEquals(1L, SequentialDelayPolicy.toDelayMillis(999_999L))
        assertEquals(1L, SequentialDelayPolicy.toDelayMillis(1_000_000L))
    }

    @Test
    fun preservesExactMillisecondBoundaries() {
        assertEquals(120L, SequentialDelayPolicy.toDelayMillis(120_000_000L))
        assertEquals(600L, SequentialDelayPolicy.toDelayMillis(600_000_000L))
        assertEquals(3_000L, SequentialDelayPolicy.toDelayMillis(3_000_000_000L))
    }

    @Test
    fun roundsFractionalMillisecondsUpRatherThanFasterThanConfigured() {
        assertEquals(2L, SequentialDelayPolicy.toDelayMillis(1_000_001L))
        assertEquals(121L, SequentialDelayPolicy.toDelayMillis(120_000_001L))
    }

    @Test
    fun allowsZeroOnlyForImmediateScheduling() {
        assertEquals(0L, SequentialDelayPolicy.toDelayMillis(0L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeDelay() {
        SequentialDelayPolicy.toDelayMillis(-1L)
    }
}
