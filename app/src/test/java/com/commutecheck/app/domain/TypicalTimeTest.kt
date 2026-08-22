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
    fun matchingDurations_usesOnlySameOriginWhenEnoughSamples() {
        val now = 1_700_000_000_000L
        val history = listOf(
            entry("Work", "work", "Home", 20 * 60, now - 3_600_000, originPlaceId = "home"),
            entry("Work", "work", "Home", 22 * 60, now - 2_600_000, originPlaceId = "home"),
            entry("Work", "work", "Home", 21 * 60, now - 2_000_000, originPlaceId = "home"),
            entry("Work", "work", "Truckee", 40 * 60, now - 1_600_000, originPlaceId = "truckee"),
            entry("Truckee", "truckee", "Home", 41 * 60, now - 600_000, originPlaceId = "home")
        )
        assertEquals(
            listOf(20, 22, 21),
            TypicalTime.matchingDurations(history, "Work", "work", "Home", now, "home")
        )
        assertTrue(
            TypicalTime.matchingDurations(history, "Truckee", "truckee", "Home", now, "home").isEmpty()
        )
    }

    @Test
    fun matchingDurations_doesNotFallBackToAnyOriginWhenSparse() {
        val now = 1_700_000_000_000L
        val history = listOf(
            entry("Work", "work", "Home", 20 * 60, now - 3_600_000, originPlaceId = "home"),
            entry("Work", "work", TypicalTime.UNMATCHED_ORIGIN_NAME, 28 * 60, now - 2_600_000)
        )
        assertTrue(
            TypicalTime.matchingDurations(history, "Work", "work", "Home", now, "home").isEmpty()
        )
    }

    @Test
    fun matchingDurations_ignoresStaleSamples() {
        val now = 1_700_000_000_000L
        val stale = now - TypicalTime.RECENT_WINDOW_MS - 1
        val history = listOf(
            entry("Work", "work", "Home", 90 * 60, stale, originPlaceId = "home"),
            entry("Work", "work", "Home", 24 * 60, now - 60_000, originPlaceId = "home"),
            entry("Work", "work", "Home", 25 * 60, now - 50_000, originPlaceId = "home"),
            entry("Work", "work", "Home", 26 * 60, now - 40_000, originPlaceId = "home")
        )
        assertEquals(
            listOf(24, 25, 26),
            TypicalTime.matchingDurations(history, "Work", "work", "Home", now, "home")
        )
    }

    @Test
    fun matchingDurations_needsThreeSameOriginSamples() {
        val now = 1_700_000_000_000L
        val two = listOf(
            entry("Home", "home", TypicalTime.UNMATCHED_ORIGIN_NAME, 2 * 60, now - 3_600_000),
            entry("Home", "home", TypicalTime.UNMATCHED_ORIGIN_NAME, 3 * 60, now - 2_600_000)
        )
        assertTrue(
            TypicalTime.matchingDurations(
                two, "Home", "home", TypicalTime.UNMATCHED_ORIGIN_NAME, now
            ).isEmpty()
        )

        val three = two + entry(
            "Home", "home", TypicalTime.UNMATCHED_ORIGIN_NAME, 4 * 60, now - 1_600_000
        )
        assertEquals(
            listOf(2, 3, 4),
            TypicalTime.matchingDurations(
                three, "Home", "home", TypicalTime.UNMATCHED_ORIGIN_NAME, now
            )
        )
    }

    @Test
    fun matchingDurations_treatsNullAndOnTheRoadAsSameOrigin() {
        val now = 1_700_000_000_000L
        val history = listOf(
            entry("Home", "home", null, 2 * 60, now - 3_600_000),
            entry("Home", "home", TypicalTime.UNMATCHED_ORIGIN_NAME, 3 * 60, now - 2_600_000),
            entry("Home", "home", "", 4 * 60, now - 1_600_000)
        )
        assertEquals(
            listOf(2, 3, 4),
            TypicalTime.matchingDurations(history, "Home", "home", null, now)
        )
        assertEquals(
            listOf(2, 3, 4),
            TypicalTime.matchingDurations(
                history, "Home", "home", TypicalTime.UNMATCHED_ORIGIN_NAME, now
            )
        )
    }

    @Test
    fun matchingDurations_prefersOriginPlaceIdOverName() {
        val now = 1_700_000_000_000L
        val history = listOf(
            entry("Home", "home", "Office", 20 * 60, now - 3_600_000, originPlaceId = "work"),
            entry("Home", "home", "Work", 21 * 60, now - 2_600_000, originPlaceId = "work"),
            entry("Home", "home", "Work", 22 * 60, now - 1_600_000, originPlaceId = "work"),
            entry("Home", "home", "Work", 40 * 60, now - 600_000, originPlaceId = "other-work")
        )
        assertEquals(
            listOf(20, 21, 22),
            TypicalTime.matchingDurations(history, "Home", "home", "Work", now, "work")
        )
    }

    @Test
    fun matchingDurations_legacyNameOnlySamplesStillMatchSamePlace() {
        val now = 1_700_000_000_000L
        val history = listOf(
            entry("Home", "home", "Work", 20 * 60, now - 3_600_000),
            entry("Home", "home", "Work", 21 * 60, now - 2_600_000),
            entry("Home", "home", "Work", 22 * 60, now - 1_600_000)
        )
        assertEquals(
            listOf(20, 21, 22),
            TypicalTime.matchingDurations(history, "Home", "home", "Work", now, "work")
        )
    }

    @Test
    fun glanceLine_onTheRoadHomeDoesNotUseWorkHomeUsual() {
        val now = calendar(hour = 15, minute = 16).timeInMillis
        val history = listOf(
            entry("Home", "home", "Work", 21 * 60, now - 3_600_000, originPlaceId = "work"),
            entry("Home", "home", "Work", 21 * 60, now - 2_600_000, originPlaceId = "work"),
            entry("Home", "home", "Work", 20 * 60, now - 1_600_000, originPlaceId = "work")
        )
        val samples = TypicalTime.matchingDurations(
            history = history,
            destinationName = "Home",
            destinationPlaceId = "home",
            originPlaceName = TypicalTime.UNMATCHED_ORIGIN_NAME,
            nowMs = now
        )
        val typical = TypicalTime.typicalMinutes(samples)

        assertTrue(samples.isEmpty())
        assertNull(typical)
        assertNull(TypicalTime.comparisonLine(2, typical))
        // Work→Home ~21 min would have produced "19 min better than usual" if origins mixed.
        assertEquals(
            "there by 3:18 PM",
            TypicalTime.glanceLine(2, typical, now, 2 * 60L, tz)
        )
    }

    @Test
    fun shouldRecord_skipsRefreshSpamDuplicate() {
        val now = 1_700_000_000_000L
        val first = entry("Work", "work", "Home", 29 * 60, now, originPlaceId = "home")
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

    @Test
    fun shouldRecord_keepsDifferentOriginForSameDestination() {
        val now = 1_700_000_000_000L
        val fromWork = entry("Home", "home", "Work", 21 * 60, now, originPlaceId = "work")
        val fromRoad = entry(
            "Home",
            "home",
            TypicalTime.UNMATCHED_ORIGIN_NAME,
            2 * 60,
            now + 15_000
        )
        assertTrue(TypicalTime.shouldRecord(listOf(fromWork), fromRoad))
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
        timestampMs: Long,
        originPlaceId: String? = null
    ) = RouteHistoryEntry(
        durationSeconds = durationInTrafficSeconds - 60,
        durationInTrafficSeconds = durationInTrafficSeconds,
        delayMinutes = 1,
        originPlaceName = originPlaceName,
        destinationName = destinationName,
        destinationPlaceId = destinationPlaceId,
        timestampMs = timestampMs,
        originPlaceId = originPlaceId
    )
}
