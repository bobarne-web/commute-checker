package com.commutecheck.app.domain

import com.commutecheck.app.data.RouteHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TypicalTimeTest {

    private val tz: TimeZone = TimeZone.getTimeZone("America/Los_Angeles")

    @Test
    fun minutesFromSeconds_roundsNearest() {
        assertEquals(0, TypicalTime.minutesFromSeconds(0))
        assertEquals(24, TypicalTime.minutesFromSeconds(24 * 60))
        assertEquals(25, TypicalTime.minutesFromSeconds(24 * 60 + 30))
        assertEquals(24, TypicalTime.minutesFromSeconds(24 * 60 + 29))
    }

    @Test
    fun typicalMinutes_needsTwoSamples() {
        assertNull(TypicalTime.typicalMinutes(emptyList()))
        assertNull(TypicalTime.typicalMinutes(listOf(24)))
        assertEquals(24, TypicalTime.typicalMinutes(listOf(24, 24)))
        assertEquals(24, TypicalTime.typicalMinutes(listOf(20, 24, 30)))
        assertEquals(23, TypicalTime.typicalMinutes(listOf(20, 26)))
    }

    @Test
    fun comparisonLine_typicalWorseOrBetter() {
        assertNull(TypicalTime.comparisonLine(24, null))
        assertEquals("typical 24 min", TypicalTime.comparisonLine(24, 24))
        assertEquals("typical 24 min", TypicalTime.comparisonLine(25, 24))
        assertEquals("typical 24 min", TypicalTime.comparisonLine(22, 24))
        assertEquals("3 min worse than usual", TypicalTime.comparisonLine(27, 24))
        assertEquals("15 min worse than usual", TypicalTime.comparisonLine(39, 24))
        assertEquals("5 min better than usual", TypicalTime.comparisonLine(19, 24))
    }

    @Test
    fun leaveByLine_isNowPlusTravel() {
        val now = calendar(hour = 8, minute = 0).timeInMillis
        assertEquals("there by 8:29 AM", TypicalTime.leaveByLine(now, 29 * 60L, tz))
        assertEquals("there by 8:41 AM", TypicalTime.leaveByLine(now, 41 * 60L, tz))
        assertEquals("there by 12:10 PM", TypicalTime.leaveByLine(calendar(hour = 11, minute = 40).timeInMillis, 30 * 60L, tz))
        assertEquals("there by 12:05 AM", TypicalTime.leaveByLine(calendar(hour = 23, minute = 50).timeInMillis, 15 * 60L, tz))
        assertNull(TypicalTime.leaveByLine(now, 0, tz))
    }

    @Test
    fun glanceLine_prefersTypicalThenLeaveBy() {
        val now = calendar(hour = 8, minute = 12).timeInMillis
        assertEquals(
            "typical 24 min · there by 8:36 AM",
            TypicalTime.glanceLine(24, 24, now, 24 * 60L, tz)
        )
        assertEquals(
            "15 min worse than usual · there by 8:41 AM",
            TypicalTime.glanceLine(41, 26, now, 29 * 60L, tz)
        )
        assertEquals(
            "there by 8:41 AM",
            TypicalTime.glanceLine(29, null, now, 29 * 60L, tz)
        )
    }

    @Test
    fun matchingDurations_prefersSameOriginWhenEnoughSamples() {
        val now = 1_700_000_000_000L
        val history = listOf(
            entry("Work", "work", "Home", 20 * 60, now - 3_600_000),
            entry("Work", "work", "Home", 22 * 60, now - 2_600_000),
            entry("Work", "work", "Truckee", 40 * 60, now - 1_600_000),
            entry("Truckee", "truckee", "Home", 41 * 60, now - 600_000)
        )
        assertEquals(
            listOf(20, 22),
            TypicalTime.matchingDurations(history, "Work", "work", "Home", now)
        )
        assertEquals(
            listOf(41),
            TypicalTime.matchingDurations(history, "Truckee", "truckee", "Home", now)
        )
    }

    @Test
    fun matchingDurations_fallsBackToDestinationWhenOriginIsSparse() {
        val now = 1_700_000_000_000L
        val history = listOf(
            entry("Work", "work", "Home", 20 * 60, now - 3_600_000),
            entry("Work", "work", "On the road", 28 * 60, now - 2_600_000)
        )
        assertEquals(
            listOf(20, 28),
            TypicalTime.matchingDurations(history, "Work", "work", "Home", now)
        )
    }

    @Test
    fun matchingDurations_ignoresStaleSamples() {
        val now = 1_700_000_000_000L
        val stale = now - TypicalTime.RECENT_WINDOW_MS - 1
        val history = listOf(
            entry("Work", "work", "Home", 90 * 60, stale),
            entry("Work", "work", "Home", 24 * 60, now - 60_000)
        )
        assertEquals(
            listOf(24),
            TypicalTime.matchingDurations(history, "Work", "work", "Home", now)
        )
    }

    @Test
    fun shouldRecord_skipsRefreshSpamDuplicate() {
        val now = 1_700_000_000_000L
        val first = entry("Work", "work", "Home", 29 * 60, now)
        assertTrue(TypicalTime.shouldRecord(emptyList(), first))
        assertFalse(TypicalTime.shouldRecord(listOf(first), first.copy(timestampMs = now + 15_000)))
        assertTrue(TypicalTime.shouldRecord(listOf(first), first.copy(timestampMs = now + 61_000)))
        assertTrue(
            TypicalTime.shouldRecord(
                listOf(first),
                first.copy(durationInTrafficSeconds = 40 * 60, timestampMs = now + 15_000)
            )
        )
    }

    private fun calendar(hour: Int, minute: Int): Calendar =
        Calendar.getInstance(tz).apply {
            clear()
            set(2026, Calendar.AUGUST, 20, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun entry(
        destinationName: String,
        destinationPlaceId: String,
        originPlaceName: String?,
        durationInTrafficSeconds: Long,
        timestampMs: Long
    ) = RouteHistoryEntry(
        durationSeconds = durationInTrafficSeconds - 60,
        durationInTrafficSeconds = durationInTrafficSeconds,
        delayMinutes = 1,
        originPlaceName = originPlaceName,
        destinationName = destinationName,
        destinationPlaceId = destinationPlaceId,
        timestampMs = timestampMs
    )
}
