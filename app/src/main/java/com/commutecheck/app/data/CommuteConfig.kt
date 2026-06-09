package com.commutecheck.app.data

import com.google.android.gms.maps.model.LatLng
import java.util.Calendar

/**
 * A saved, named location (e.g. Home, Work, Truckee). Used both to detect where
 * the user currently is and as a destination for travel-time watches.
 */
data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = DEFAULT_RADIUS_METERS
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)

    fun distanceTo(lat: Double, lng: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(latitude, longitude, lat, lng, results)
        return results[0]
    }

    fun contains(lat: Double, lng: Double): Boolean = distanceTo(lat, lng) <= radiusMeters

    companion object {
        const val DEFAULT_RADIUS_METERS = 500f
        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * A travel-time "watch": when the car starts and the current day/time/season match,
 * the app checks the travel time from wherever you are to [destinationPlaceId].
 */
data class Watch(
    val id: String,
    val name: String,
    val destinationPlaceId: String,
    val enabledDays: Set<Int> = ALL_DAYS,
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 23,
    val endMinute: Int = 59,
    val enabled: Boolean = true,
    val activeMonths: Set<Int> = ALL_MONTHS,
    val skipIfAtDestination: Boolean = true,
    val alwaysCheck: Boolean = false
) {
    /**
     * True if this watch should run right now (master toggle on, today is an enabled
     * day, the current month is in season, and the current time is within the window).
     * If [alwaysCheck] is true, only the master [enabled] toggle is checked.
     */
    fun isActiveNow(calendar: Calendar = Calendar.getInstance()): Boolean {
        if (!enabled) return false
        if (alwaysCheck) return true

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek !in enabledDays) return false

        val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        if (month !in activeMonths) return false

        return isWithinTimeWindow(calendar)
    }

    private fun isWithinTimeWindow(calendar: Calendar): Boolean {
        val current = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val start = startHour * 60 + startMinute
        val end = endHour * 60 + endMinute
        return if (start <= end) {
            current in start..end
        } else {
            // Window wraps past midnight (e.g. 22:00 -> 02:00)
            current >= start || current <= end
        }
    }

    companion object {
        val ALL_DAYS: Set<Int> = setOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )
        val WEEKDAYS: Set<Int> = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
        val ALL_MONTHS: Set<Int> = (1..12).toSet()
        val SUMMER_MONTHS: Set<Int> = setOf(5, 6, 7, 8, 9, 10) // May–Oct
        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * Raw travel-time data parsed from the Directions API.
 */
data class TravelTimeResult(
    val durationText: String,
    val durationSeconds: Long,
    val durationInTrafficText: String,
    val durationInTrafficSeconds: Long,
    val distanceText: String,
    val summary: String
)

/**
 * The result of evaluating one watch on car start: travel time plus delay analysis
 * and color coding for display.
 */
data class RouteCheckResult(
    val watchId: String,
    val destinationName: String,
    val durationInTrafficText: String,
    val distanceText: String,
    val summary: String,
    val delayMinutes: Int,
    val isDelayed: Boolean,
    val error: String? = null
) {
    val hasError: Boolean get() = error != null
}
