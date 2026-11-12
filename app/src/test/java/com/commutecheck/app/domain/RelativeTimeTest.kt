package com.commutecheck.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    @Test
    fun justNow_replacesZeroMinutesAgo() {
        val now = 1_700_000_000_000L
        assertEquals("Just now", RelativeTime.label(now, now) { "0 minutes ago" })
        assertEquals("Just now", RelativeTime.label(now - 30_000L, now) { "0 minutes ago" })
        assertEquals("Just now", RelativeTime.label(now - 59_000L, now) { "0 minutes ago" })
        assertEquals("2 minutes ago", RelativeTime.label(now - 120_000L, now) { "2 minutes ago" })
    }
}
