package com.commutecheck.app.domain

import com.commutecheck.app.data.RouteHistoryEntry
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Personal typical-time and leave-now math. No calendars or multi-user logic —
 * just recent successful checks for the same destination and the same origin.
 * Unmatched / "On the road" origins only compare against other unmatched samples.
 * We never average across different origins.
 */
object TypicalTime {

    const val MIN_SAMPLES = 2
    const val MIN_COMPARISON_SAMPLES = 3
    const val NOTICEABLE_MINUTES = 3
    const val MAX_HISTORY = 40
    const val RECENT_WINDOW_MS = 21L * 24 * 60 * 60 * 1000
    const val UNMATCHED_ORIGIN_NAME = "On the road"

    fun minutesFromSeconds(seconds: Long): Int {
        if (seconds <= 0L) return 0
        return (seconds / 60.0).roundToInt().coerceAtLeast(0)
    }

    fun typicalMinutes(samples: List<Int>): Int? {
        if (samples.size < MIN_SAMPLES) return null
        val sorted = samples.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
        }
    }

    fun comparisonLine(currentMinutes: Int, typicalMinutes: Int?): String? {
        if (typicalMinutes == null) return null
        val delta = currentMinutes - typicalMinutes
        return when {
            abs(delta) < NOTICEABLE_MINUTES -> "typical $typicalMinutes min"
            delta > 0 -> "$delta min worse than usual"
            else -> "${-delta} min better than usual"
        }
    }

    fun leaveByLine(
        nowMs: Long,
        durationSeconds: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String? {
        if (durationSeconds <= 0L) return null
        val arrive = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs + durationSeconds * 1000L
        }
        val hour24 = arrive.get(Calendar.HOUR_OF_DAY)
        val minute = arrive.get(Calendar.MINUTE)
        val hour12 = hour24 % 12
        val displayHour = if (hour12 == 0) 12 else hour12
        val amPm = if (hour24 < 12) "AM" else "PM"
        return "there by $displayHour:${minute.toString().padStart(2, '0')} $amPm"
    }

    fun glanceLine(
        currentMinutes: Int,
        typicalMinutes: Int?,
        nowMs: Long,
        durationSeconds: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String? {
        val comparison = comparisonLine(currentMinutes, typicalMinutes)
        val leaveBy = leaveByLine(nowMs, durationSeconds, timeZone)
        return when {
            comparison != null && leaveBy != null -> "$comparison · $leaveBy"
            comparison != null -> comparison
            else -> leaveBy
        }
    }

    fun matchingDurations(
        history: List<RouteHistoryEntry>,
        destinationName: String,
        destinationPlaceId: String,
        originPlaceName: String?,
        nowMs: Long,
        originPlaceId: String? = null
    ): List<Int> {
        val originScoped = history.filter { entry ->
            val age = nowMs - entry.timestampMs
            age in 0..RECENT_WINDOW_MS &&
                sameDestination(entry, destinationName, destinationPlaceId) &&
                sameOrigin(entry, originPlaceId, originPlaceName)
        }
        if (originScoped.size < MIN_COMPARISON_SAMPLES) return emptyList()
        return originScoped.map { minutesFromSeconds(it.durationInTrafficSeconds) }
    }

    /**
     * Skip recording a refresh-spam duplicate so typical time stays honest.
     */
    fun shouldRecord(
        history: List<RouteHistoryEntry>,
        entry: RouteHistoryEntry,
        minGapMs: Long = RouteCooldown.TTL_MS
    ): Boolean {
        val last = history.lastOrNull { existing ->
            sameDestination(existing, entry.destinationName, entry.destinationPlaceId) &&
                sameOrigin(existing, entry.originPlaceId, entry.originPlaceName)
        } ?: return true
        val closeInTime = entry.timestampMs - last.timestampMs in 0 until minGapMs
        val sameTravel = last.durationInTrafficSeconds == entry.durationInTrafficSeconds
        return !(closeInTime && sameTravel)
    }

    private fun sameDestination(
        entry: RouteHistoryEntry,
        destinationName: String,
        destinationPlaceId: String
    ): Boolean {
        if (destinationPlaceId.isNotBlank() && entry.destinationPlaceId == destinationPlaceId) {
            return true
        }
        return entry.destinationName.equals(destinationName, ignoreCase = true)
    }

    private fun sameOrigin(
        entry: RouteHistoryEntry,
        originPlaceId: String?,
        originPlaceName: String?
    ): Boolean {
        val queryId = originPlaceId?.trim().orEmpty()
        val entryId = entry.originPlaceId?.trim().orEmpty()
        if (queryId.isNotEmpty() && entryId.isNotEmpty()) {
            return queryId == entryId
        }
        return normalizeOriginName(entry.originPlaceName) == normalizeOriginName(originPlaceName)
    }

    private fun normalizeOriginName(name: String?): String {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals(UNMATCHED_ORIGIN_NAME, ignoreCase = true)) {
            return UNMATCHED_ORIGIN_NAME.lowercase()
        }
        return trimmed.lowercase()
    }
}
