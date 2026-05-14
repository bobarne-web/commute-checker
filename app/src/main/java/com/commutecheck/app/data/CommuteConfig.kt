package com.commutecheck.app.data

import com.google.android.gms.maps.model.LatLng

/**
 * Saved location with a name and coordinates.
 */
data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)

    fun distanceTo(lat: Double, lng: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(latitude, longitude, lat, lng, results)
        return results[0]
    }
}

/**
 * Schedule configuration for when to check commute times from home.
 */
data class ScheduleConfig(
    val enabledDays: Set<Int> = setOf(
        java.util.Calendar.MONDAY,
        java.util.Calendar.TUESDAY,
        java.util.Calendar.WEDNESDAY,
        java.util.Calendar.THURSDAY,
        java.util.Calendar.FRIDAY
    ),
    val startHour: Int = 5,
    val startMinute: Int = 0,
    val endHour: Int = 10,
    val endMinute: Int = 0
) {
    fun isWithinSchedule(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        if (dayOfWeek !in enabledDays) return false

        val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(java.util.Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return currentMinutes in startMinutes..endMinutes
    }
}

/**
 * Represents which location the user is currently near.
 */
enum class DetectedLocation {
    HOME,
    WORK,
    UNKNOWN
}

/**
 * Travel time result from the Directions API.
 */
data class TravelTimeResult(
    val durationText: String,
    val durationSeconds: Long,
    val durationInTrafficText: String,
    val durationInTrafficSeconds: Long,
    val distanceText: String,
    val summary: String
)
