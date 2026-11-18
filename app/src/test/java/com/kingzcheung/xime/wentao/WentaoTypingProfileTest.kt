package com.kingzcheung.xime.wentao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WentaoTypingProfileTest {
    @Test
    fun exposesNinePresetSpeedsFromSixToThirtyThousand() {
        val presets = WentaoTypingProfile.presets()
        assertEquals(9, presets.size)
        assertEquals(6_000L, presets.first().charactersPerHour)
        assertEquals(30_000L, presets.last().charactersPerHour)
        assertEquals((6_000L..30_000L step 3_000L).toList(), presets.map { it.charactersPerHour })
        assertFalse(presets.first().isCustom)
    }

    @Test
    fun customSpeedHasNoPresetUpperLimit() {
        val profile = WentaoTypingProfile.custom(10_000_000L)
        assertTrue(profile.isCustom)
        assertEquals(10_000_000L, profile.charactersPerHour)
        assertTrue(profile.intervalNanos >= 1_000_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroCustomSpeed() {
        WentaoTypingProfile.custom(0L)
    }

    @Test
    fun computesExpectedIntervals() {
        assertEquals(600_000_000L, WentaoTypingProfile.preset(WentaoTypingProfile.Preset.LEVEL_1).intervalNanos)
        assertEquals(120_000_000L, WentaoTypingProfile.preset(WentaoTypingProfile.Preset.LEVEL_9).intervalNanos)
    }

    @Test
    fun jitterIsClampedAndNeverSchedulesBelowOneMillisecond() {
        val profile = WentaoTypingProfile.custom(10_000_000L)
        assertEquals(1_000_000L, profile.intervalWithJitterNanos(-0.25))
        assertTrue(profile.intervalWithJitterNanos(99.0) >= 1_000_000L)
    }

    @Test
    fun punctuationAddsGraduatedPauses() {
        val profile = WentaoTypingProfile.preset(WentaoTypingProfile.Preset.LEVEL_1)
        assertEquals(600_000_000L, WentaoTypingPacing.nextDelayNanos("字", profile))
        assertEquals(1_200_000_000L, WentaoTypingPacing.nextDelayNanos("，", profile))
        assertEquals(1_800_000_000L, WentaoTypingPacing.nextDelayNanos("；", profile))
        assertEquals(2_400_000_000L, WentaoTypingPacing.nextDelayNanos("。", profile))
        assertEquals(3_000_000_000L, WentaoTypingPacing.nextDelayNanos("\\n", profile))
    }
}
