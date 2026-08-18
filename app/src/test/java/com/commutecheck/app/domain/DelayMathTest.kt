package com.commutecheck.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayMathTest {

    @Test
    fun delayMinutes_roundsNearestAndNeverNegative() {
        assertEquals(0, DelayMath.delayMinutes(600, 600))
        assertEquals(3, DelayMath.delayMinutes(780, 600))
        assertEquals(1, DelayMath.delayMinutes(689, 600))
        assertEquals(2, DelayMath.delayMinutes(690, 600))
        assertEquals(0, DelayMath.delayMinutes(500, 600))
    }

    @Test
    fun isDelayed_usesInclusiveThreshold() {
        assertFalse(DelayMath.isDelayed(2, 3))
        assertTrue(DelayMath.isDelayed(3, 3))
        assertTrue(DelayMath.isDelayed(14, 3))
        assertTrue(DelayMath.isDelayed(0, 0))
        assertFalse(DelayMath.isDelayed(0, 1))
    }

    @Test
    fun statusText_isNotColorOnly() {
        assertEquals("ON TIME", DelayMath.statusText(false, 0))
        assertEquals("DELAY  +14 min", DelayMath.statusText(true, 14))
    }

    @Test
    fun cooldown_elapsedAfterGap() {
        val now = 1_000_000L
        assertTrue(CommuteTrigger.isCooldownElapsed(0L, now))
        assertFalse(CommuteTrigger.isCooldownElapsed(now - 60_000L, now, 10 * 60 * 1000L))
        assertTrue(CommuteTrigger.isCooldownElapsed(now - 11 * 60 * 1000L, now, 10 * 60 * 1000L))
    }
}
