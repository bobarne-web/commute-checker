package com.commutecheck.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WatchScheduleTest {

    private fun calendar(
        year: Int = 2026,
        month: Int = Calendar.AUGUST,
        day: Int = 18,
        hour: Int,
        minute: Int
    ): Calendar = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun watch(
        enabled: Boolean = true,
        alwaysCheck: Boolean = false,
        days: Set<Int> = Watch.ALL_DAYS,
        months: Set<Int> = Watch.ALL_MONTHS,
        startHour: Int = 7,
        startMinute: Int = 0,
        endHour: Int = 10,
        endMinute: Int = 0
    ) = Watch(
        id = "watch",
        name = "To Work",
        destinationPlaceId = "work",
        enabledDays = days,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        enabled = enabled,
        activeMonths = months,
        alwaysCheck = alwaysCheck
    )

    @Test
    fun disabledWatchIsNeverActive() {
        val now = calendar(hour = 8, minute = 0)
        assertFalse(watch(enabled = false).isActiveNow(now))
        assertFalse(watch(enabled = false, alwaysCheck = true).isActiveNow(now))
    }

    @Test
    fun alwaysCheckIgnoresDayTimeAndSeason() {
        val sundayNight = calendar(day = 16, hour = 23, minute = 0)
        assertTrue(sundayNight.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
        val offSeason = calendar(month = Calendar.JANUARY, day = 15, hour = 23, minute = 0)
        assertTrue(
            watch(
                alwaysCheck = true,
                days = Watch.WEEKDAYS,
                months = Watch.SUMMER_MONTHS,
                startHour = 7,
                endHour = 9
            ).isActiveNow(sundayNight)
        )
        assertTrue(watch(alwaysCheck = true, months = Watch.SUMMER_MONTHS).isActiveNow(offSeason))
    }

    @Test
    fun weekdayFilter() {
        val tuesday = calendar(hour = 8, minute = 0)
        val saturday = calendar(day = 22, hour = 8, minute = 0)
        assertTrue(tuesday.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY)
        assertTrue(saturday.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY)
        val weekdays = watch(days = Watch.WEEKDAYS)
        assertTrue(weekdays.isActiveNow(tuesday))
        assertFalse(weekdays.isActiveNow(saturday))
    }

    @Test
    fun summerSeasonFilter() {
        val august = calendar(hour = 8, minute = 0)
        val january = calendar(month = Calendar.JANUARY, day = 15, hour = 8, minute = 0)
        val summer = watch(months = Watch.SUMMER_MONTHS)
        assertTrue(summer.isActiveNow(august))
        assertFalse(summer.isActiveNow(january))
    }

    @Test
    fun timeWindowIncludesBoundaries() {
        val window = watch(startHour = 7, startMinute = 0, endHour = 10, endMinute = 0)
        assertTrue(window.isActiveNow(calendar(hour = 7, minute = 0)))
        assertTrue(window.isActiveNow(calendar(hour = 10, minute = 0)))
        assertFalse(window.isActiveNow(calendar(hour = 6, minute = 59)))
        assertFalse(window.isActiveNow(calendar(hour = 10, minute = 1)))
    }

    @Test
    fun timeWindowWrapsMidnight() {
        val overnight = watch(startHour = 22, startMinute = 0, endHour = 2, endMinute = 0)
        assertTrue(overnight.isActiveNow(calendar(hour = 23, minute = 0)))
        assertTrue(overnight.isActiveNow(calendar(hour = 1, minute = 0)))
        assertTrue(overnight.isActiveNow(calendar(hour = 22, minute = 0)))
        assertTrue(overnight.isActiveNow(calendar(hour = 2, minute = 0)))
        assertFalse(overnight.isActiveNow(calendar(hour = 21, minute = 0)))
        assertFalse(overnight.isActiveNow(calendar(hour = 3, minute = 0)))
    }

    @Test
    fun coveringNowIncludesCurrentTime() {
        val morning = calendar(hour = 8, minute = 30)
        val late = calendar(hour = 23, minute = 10)
        val midnight = calendar(hour = 0, minute = 15)
        assertTrue(Watch.coveringNow("To Work", "work", morning).isActiveNow(morning))
        assertTrue(Watch.coveringNow("To Work", "work", late).isActiveNow(late))
        assertTrue(Watch.coveringNow("To Work", "work", midnight).isActiveNow(midnight))
    }
}
