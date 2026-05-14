package com.commutecheck.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "commute_checker_prefs"
        private const val KEY_HOME_LOCATION = "home_location"
        private const val KEY_WORK_LOCATION = "work_location"
        private const val KEY_SCHEDULE_ENABLED_DAYS = "schedule_enabled_days"
        private const val KEY_SCHEDULE_START_HOUR = "schedule_start_hour"
        private const val KEY_SCHEDULE_START_MINUTE = "schedule_start_minute"
        private const val KEY_SCHEDULE_END_HOUR = "schedule_end_hour"
        private const val KEY_SCHEDULE_END_MINUTE = "schedule_end_minute"
        private const val KEY_GEOFENCE_RADIUS = "geofence_radius_meters"
        private const val KEY_APP_ENABLED = "app_enabled"
        private const val KEY_WORK_ALWAYS_CHECK = "work_always_check"
        private const val KEY_MAPS_API_KEY = "maps_api_key"

        private const val DEFAULT_GEOFENCE_RADIUS = 500f // meters
    }

    // --- Home Location ---

    fun saveHomeLocation(location: SavedLocation) {
        prefs.edit().putString(KEY_HOME_LOCATION, gson.toJson(location)).apply()
    }

    fun getHomeLocation(): SavedLocation? {
        val json = prefs.getString(KEY_HOME_LOCATION, null) ?: return null
        return gson.fromJson(json, SavedLocation::class.java)
    }

    // --- Work Location ---

    fun saveWorkLocation(location: SavedLocation) {
        prefs.edit().putString(KEY_WORK_LOCATION, gson.toJson(location)).apply()
    }

    fun getWorkLocation(): SavedLocation? {
        val json = prefs.getString(KEY_WORK_LOCATION, null) ?: return null
        return gson.fromJson(json, SavedLocation::class.java)
    }

    // --- Schedule ---

    fun saveScheduleConfig(config: ScheduleConfig) {
        prefs.edit().apply {
            putString(KEY_SCHEDULE_ENABLED_DAYS, gson.toJson(config.enabledDays.toList()))
            putInt(KEY_SCHEDULE_START_HOUR, config.startHour)
            putInt(KEY_SCHEDULE_START_MINUTE, config.startMinute)
            putInt(KEY_SCHEDULE_END_HOUR, config.endHour)
            putInt(KEY_SCHEDULE_END_MINUTE, config.endMinute)
            apply()
        }
    }

    fun getScheduleConfig(): ScheduleConfig {
        val daysJson = prefs.getString(KEY_SCHEDULE_ENABLED_DAYS, null)
        val days: Set<Int> = if (daysJson != null) {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(daysJson, type).toSet()
        } else {
            setOf(
                java.util.Calendar.MONDAY,
                java.util.Calendar.TUESDAY,
                java.util.Calendar.WEDNESDAY,
                java.util.Calendar.THURSDAY,
                java.util.Calendar.FRIDAY
            )
        }

        return ScheduleConfig(
            enabledDays = days,
            startHour = prefs.getInt(KEY_SCHEDULE_START_HOUR, 5),
            startMinute = prefs.getInt(KEY_SCHEDULE_START_MINUTE, 0),
            endHour = prefs.getInt(KEY_SCHEDULE_END_HOUR, 10),
            endMinute = prefs.getInt(KEY_SCHEDULE_END_MINUTE, 0)
        )
    }

    // --- Geofence Radius ---

    fun getGeofenceRadius(): Float {
        return prefs.getFloat(KEY_GEOFENCE_RADIUS, DEFAULT_GEOFENCE_RADIUS)
    }

    fun saveGeofenceRadius(radiusMeters: Float) {
        prefs.edit().putFloat(KEY_GEOFENCE_RADIUS, radiusMeters).apply()
    }

    // --- App Enabled ---

    fun isAppEnabled(): Boolean = prefs.getBoolean(KEY_APP_ENABLED, true)

    fun setAppEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_ENABLED, enabled).apply()
    }

    // --- Work Always Check ---

    fun isWorkAlwaysCheck(): Boolean = prefs.getBoolean(KEY_WORK_ALWAYS_CHECK, true)

    fun setWorkAlwaysCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORK_ALWAYS_CHECK, enabled).apply()
    }

    // --- Maps API Key ---

    fun getMapsApiKey(): String {
        return prefs.getString(KEY_MAPS_API_KEY, "") ?: ""
    }

    fun saveMapsApiKey(key: String) {
        prefs.edit().putString(KEY_MAPS_API_KEY, key).apply()
    }

    // --- Check if configured ---

    fun isConfigured(): Boolean {
        return getHomeLocation() != null && getWorkLocation() != null
    }
}
