package com.commutecheck.app.data

import android.content.Context
import android.content.SharedPreferences
import com.commutecheck.app.BuildConfig
import com.commutecheck.app.domain.GeoPoint
import com.commutecheck.app.domain.TypicalTime
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "commute_checker_prefs"
        private const val KEY_PLACES = "places"
        private const val KEY_WATCHES = "watches"
        private const val KEY_APP_ENABLED = "app_enabled"
        private const val KEY_MAPS_API_KEY = "maps_api_key"
        private const val KEY_DELAY_THRESHOLD_MIN = "delay_threshold_minutes"
        private const val KEY_LAST_RESULTS = "last_results"
        private const val KEY_LAST_RESULTS_PLACE = "last_results_place"
        private const val KEY_LAST_RESULTS_TIME = "last_results_time"
        private const val KEY_USB_TRIGGER_ENABLED = "usb_trigger_enabled"
        private const val KEY_LAST_TRIGGER_TIME = "last_trigger_time"
        private const val KEY_ROUTE_HISTORY = "route_history"
        private const val KEY_ROUTE_CACHE = "route_cooldown_cache"
        private const val KEY_LAST_ORIGIN_LAT_BITS = "last_origin_lat_bits"
        private const val KEY_LAST_ORIGIN_LNG_BITS = "last_origin_lng_bits"
        private const val KEY_LAST_ORIGIN_SET = "last_origin_set"

        const val DEFAULT_DELAY_THRESHOLD_MIN = 3
    }

    // --- Places ---

    fun getPlaces(): List<Place> {
        val json = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        val type = object : TypeToken<List<Place>>() {}.type
        val places: List<Place> = gson.fromJson(json, type) ?: emptyList()
        return places.map { place ->
            val name = (place.name as String?)?.takeIf { it.isNotBlank() } ?: "Place"
            val address = (place.address as String?) ?: ""
            if (name != place.name || address != place.address) {
                place.copy(name = name, address = address)
            } else {
                place
            }
        }
    }

    fun savePlaces(places: List<Place>) {
        prefs.edit().putString(KEY_PLACES, gson.toJson(places)).apply()
    }

    fun getPlace(id: String): Place? = getPlaces().firstOrNull { it.id == id }

    fun upsertPlace(place: Place) {
        val places = getPlaces().toMutableList()
        val index = places.indexOfFirst { it.id == place.id }
        if (index >= 0) places[index] = place else places.add(place)
        savePlaces(places)
    }

    fun deletePlace(id: String) {
        savePlaces(getPlaces().filter { it.id != id })
        // Remove watches that pointed at this place
        saveWatches(getWatches().filter { it.destinationPlaceId != id })
    }

    // --- Watches ---

    fun getWatches(): List<Watch> {
        val json = prefs.getString(KEY_WATCHES, null) ?: return emptyList()
        val type = object : TypeToken<List<Watch>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveWatches(watches: List<Watch>) {
        prefs.edit().putString(KEY_WATCHES, gson.toJson(watches)).apply()
    }

    fun getWatch(id: String): Watch? = getWatches().firstOrNull { it.id == id }

    fun upsertWatch(watch: Watch) {
        val watches = getWatches().toMutableList()
        val index = watches.indexOfFirst { it.id == watch.id }
        if (index >= 0) watches[index] = watch else watches.add(watch)
        saveWatches(watches)
    }

    fun deleteWatch(id: String) {
        saveWatches(getWatches().filter { it.id != id })
    }

    // --- App Enabled ---

    fun isAppEnabled(): Boolean = prefs.getBoolean(KEY_APP_ENABLED, true)

    fun setAppEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_ENABLED, enabled).apply()
    }

    // --- Maps API Key ---

    fun getMapsApiKey(): String = prefs.getString(KEY_MAPS_API_KEY, "") ?: ""

    fun saveMapsApiKey(key: String) {
        prefs.edit().putString(KEY_MAPS_API_KEY, key).apply()
    }

    // --- Delay threshold (minutes) ---

    fun getDelayThresholdMinutes(): Int =
        prefs.getInt(KEY_DELAY_THRESHOLD_MIN, DEFAULT_DELAY_THRESHOLD_MIN)

    fun setDelayThresholdMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DELAY_THRESHOLD_MIN, minutes).apply()
    }

    // --- Last results cache (so the car screen can show the most recent check) ---

    fun saveLastResults(placeName: String, results: List<RouteCheckResult>) {
        prefs.edit()
            .putString(KEY_LAST_RESULTS, gson.toJson(results))
            .putString(KEY_LAST_RESULTS_PLACE, placeName)
            .putLong(KEY_LAST_RESULTS_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getLastResults(): List<RouteCheckResult> {
        val json = prefs.getString(KEY_LAST_RESULTS, null) ?: return emptyList()
        val type = object : TypeToken<List<RouteCheckResult>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun getLastResultsPlace(): String = prefs.getString(KEY_LAST_RESULTS_PLACE, "") ?: ""

    fun getLastResultsTime(): Long = prefs.getLong(KEY_LAST_RESULTS_TIME, 0L)

    // --- Typical-time history (successful checks only) ---

    fun getRouteHistory(): List<RouteHistoryEntry> {
        val json = prefs.getString(KEY_ROUTE_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<RouteHistoryEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun appendRouteHistory(entry: RouteHistoryEntry) {
        val next = (getRouteHistory() + entry).takeLast(TypicalTime.MAX_HISTORY)
        prefs.edit().putString(KEY_ROUTE_HISTORY, gson.toJson(next)).apply()
    }

    // --- Directions cooldown cache ---

    fun getCachedRoutes(): List<CachedRoute> {
        val json = prefs.getString(KEY_ROUTE_CACHE, null) ?: return emptyList()
        val type = object : TypeToken<List<CachedRoute>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun findCachedRoute(destinationPlaceId: String): CachedRoute? =
        getCachedRoutes().lastOrNull { it.destinationPlaceId == destinationPlaceId }

    fun upsertCachedRoute(entry: CachedRoute) {
        val next = getCachedRoutes()
            .filterNot { it.destinationPlaceId == entry.destinationPlaceId } + entry
        prefs.edit().putString(KEY_ROUTE_CACHE, gson.toJson(next.takeLast(12))).apply()
    }

    // --- Last origin that produced a usable location ---

    fun saveLastSuccessfulOrigin(latitude: Double, longitude: Double) {
        prefs.edit()
            .putLong(KEY_LAST_ORIGIN_LAT_BITS, latitude.toRawBits())
            .putLong(KEY_LAST_ORIGIN_LNG_BITS, longitude.toRawBits())
            .putBoolean(KEY_LAST_ORIGIN_SET, true)
            .apply()
    }

    fun getLastSuccessfulOrigin(): GeoPoint? {
        if (!prefs.getBoolean(KEY_LAST_ORIGIN_SET, false)) return null
        return GeoPoint(
            latitude = Double.fromBits(prefs.getLong(KEY_LAST_ORIGIN_LAT_BITS, 0L)),
            longitude = Double.fromBits(prefs.getLong(KEY_LAST_ORIGIN_LNG_BITS, 0L))
        )
    }

    // --- USB last-resort trigger ---

    fun isUsbTriggerEnabled(): Boolean = prefs.getBoolean(KEY_USB_TRIGGER_ENABLED, false)

    fun setUsbTriggerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USB_TRIGGER_ENABLED, enabled).apply()
    }

    // --- Automatic-trigger cooldown ---

    fun getLastTriggerTime(): Long = prefs.getLong(KEY_LAST_TRIGGER_TIME, 0L)

    fun markCheckTriggered(atMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_TRIGGER_TIME, atMs).apply()
    }

    // --- Configuration check ---

    /** Configured once there is at least one place and one watch. */
    fun isConfigured(): Boolean = getPlaces().isNotEmpty() && getWatches().isNotEmpty()

    fun hasUsableApiKey(): Boolean =
        getMapsApiKey().isNotEmpty() || BuildConfig.MAPS_API_KEY.isNotEmpty()

    fun needsSetup(): Boolean = !hasUsableApiKey() || !isConfigured()
}
