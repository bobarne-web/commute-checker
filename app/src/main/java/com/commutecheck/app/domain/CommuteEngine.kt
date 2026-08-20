package com.commutecheck.app.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.commutecheck.app.BuildConfig
import com.commutecheck.app.api.DirectionsApiClient
import com.commutecheck.app.data.CachedRoute
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.RouteCheckResult
import com.commutecheck.app.data.RouteHistoryEntry
import com.commutecheck.app.data.TravelTimeResult
import com.commutecheck.app.data.Watch
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Shared logic for running commute checks. Used by both the foreground service
 * (for notifications) and the Android Auto car screen (for the dashboard).
 */
class CommuteEngine(private val context: Context) {

    private val prefs = PreferencesManager(context)
    private var cachedApiKey: String? = null
    private var cachedDirections: DirectionsApiClient? = null

    sealed class EngineResult {
        data class Success(
            val currentPlaceName: String?,
            val results: List<RouteCheckResult>
        ) : EngineResult()

        data class Failure(val message: String) : EngineResult()
    }

    suspend fun runChecks(): EngineResult {
        val apiKey = prefs.getMapsApiKey().ifEmpty { BuildConfig.MAPS_API_KEY }
        if (apiKey.isEmpty()) {
            return EngineResult.Failure("Google Maps API key not configured")
        }

        val places = prefs.getPlaces()
        val watches = prefs.getWatches()
        if (places.isEmpty() || watches.isEmpty()) {
            return EngineResult.Failure("No places or watches configured")
        }

        val location = resolveLocation()
            ?: return EngineResult.Failure("Could not determine your location")
        prefs.saveLastSuccessfulOrigin(location.latitude, location.longitude)

        val currentPlace = detectCurrentPlace(location.latitude, location.longitude, places)
        val now = Calendar.getInstance()
        val nowMs = now.timeInMillis
        val thresholdMin = prefs.getDelayThresholdMinutes()
        val history = prefs.getRouteHistory()

        // Pick the watches that should run right now.
        val activeWatches = watches.filter { watch ->
            if (!watch.isActiveNow(now)) return@filter false
            // Skip a watch whose destination is exactly where we already are.
            if (watch.skipIfAtDestination && currentPlace?.id == watch.destinationPlaceId) {
                return@filter false
            }
            true
        }

        if (activeWatches.isEmpty()) {
            prefs.saveLastResults(currentPlace?.name ?: "On the road", emptyList())
            return EngineResult.Success(currentPlace?.name, emptyList())
        }

        val client = directionsClient(apiKey)
        val results = coroutineScope {
            activeWatches.map { watch ->
                async {
                    evaluateWatch(
                        client = client,
                        watch = watch,
                        origin = location,
                        places = places,
                        thresholdMin = thresholdMin,
                        currentPlaceName = currentPlace?.name,
                        nowMs = nowMs,
                        history = history
                    )
                }
            }.awaitAll()
        }.filterNotNull()

        results.forEach { result ->
            if (result.hasError || result.durationInTrafficSeconds <= 0L) return@forEach
            val entry = RouteHistoryEntry(
                durationSeconds = result.durationSeconds,
                durationInTrafficSeconds = result.durationInTrafficSeconds,
                delayMinutes = result.delayMinutes,
                originPlaceName = currentPlace?.name,
                destinationName = result.destinationName,
                destinationPlaceId = result.destinationPlaceId,
                timestampMs = nowMs
            )
            if (TypicalTime.shouldRecord(prefs.getRouteHistory(), entry)) {
                prefs.appendRouteHistory(entry)
            }
        }

        prefs.saveLastResults(currentPlace?.name ?: "On the road", results)
        return EngineResult.Success(currentPlace?.name, results)
    }

    private fun directionsClient(apiKey: String): DirectionsApiClient {
        val existing = cachedDirections
        if (existing != null && cachedApiKey == apiKey) return existing
        return DirectionsApiClient(apiKey).also {
            cachedDirections = it
            cachedApiKey = apiKey
        }
    }

    private suspend fun evaluateWatch(
        client: DirectionsApiClient,
        watch: Watch,
        origin: Location,
        places: List<Place>,
        thresholdMin: Int,
        currentPlaceName: String?,
        nowMs: Long,
        history: List<RouteHistoryEntry>
    ): RouteCheckResult? {
        val destination = places.firstOrNull { it.id == watch.destinationPlaceId } ?: return null
        val destinationName = watch.name.ifBlank { destination.name }

        val travelResult = resolveTravelTime(client, watch, origin, destination, nowMs)

        return travelResult.fold(
            onSuccess = { travel ->
                val delayMinutes = DelayMath.delayMinutes(
                    travel.durationInTrafficSeconds,
                    travel.durationSeconds
                )
                val currentMinutes = TypicalTime.minutesFromSeconds(travel.durationInTrafficSeconds)
                val typical = TypicalTime.typicalMinutes(
                    TypicalTime.matchingDurations(
                        history = history,
                        destinationName = destinationName,
                        destinationPlaceId = destination.id,
                        originPlaceName = currentPlaceName,
                        nowMs = nowMs
                    )
                )
                RouteCheckResult(
                    watchId = watch.id,
                    destinationName = destinationName,
                    durationInTrafficText = travel.durationInTrafficText,
                    distanceText = travel.distanceText,
                    summary = travel.summary,
                    delayMinutes = delayMinutes,
                    isDelayed = DelayMath.isDelayed(delayMinutes, thresholdMin),
                    destinationPlaceId = destination.id,
                    durationSeconds = travel.durationSeconds,
                    durationInTrafficSeconds = travel.durationInTrafficSeconds,
                    glanceLine = TypicalTime.glanceLine(
                        currentMinutes = currentMinutes,
                        typicalMinutes = typical,
                        nowMs = nowMs,
                        durationSeconds = travel.durationInTrafficSeconds
                    )
                )
            },
            onFailure = { error ->
                Log.e(TAG, "Watch '${watch.name}' failed", error)
                RouteCheckResult(
                    watchId = watch.id,
                    destinationName = destinationName,
                    durationInTrafficText = "",
                    distanceText = "",
                    summary = "",
                    delayMinutes = 0,
                    isDelayed = false,
                    error = error.message ?: "Failed to get travel time",
                    destinationPlaceId = destination.id
                )
            }
        )
    }

    private suspend fun resolveTravelTime(
        client: DirectionsApiClient,
        watch: Watch,
        origin: Location,
        destination: Place,
        nowMs: Long
    ): Result<TravelTimeResult> {
        val cached = prefs.findCachedRoute(destination.id)
        if (RouteCooldown.canReuse(cached, destination.id, origin.latitude, origin.longitude, nowMs)) {
            Log.i(TAG, "Reusing cached route for '${watch.name}'")
            return Result.success(cached!!.toTravelTime())
        }

        val fetched = client.getTravelTime(
            origin.latitude, origin.longitude,
            destination.latitude, destination.longitude
        )
        fetched.onSuccess { travel ->
            prefs.upsertCachedRoute(
                CachedRoute.from(
                    destinationPlaceId = destination.id,
                    originLat = origin.latitude,
                    originLng = origin.longitude,
                    travel = travel,
                    cachedAtMs = nowMs
                )
            )
        }
        return fetched
    }

    private fun detectCurrentPlace(lat: Double, lng: Double, places: List<Place>): Place? {
        return places
            .filter { it.contains(lat, lng) }
            .minByOrNull { it.distanceTo(lat, lng) }
    }

    private suspend fun resolveLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val current = try {
            val token = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
        } catch (e: Exception) {
            Log.w(TAG, "High-accuracy location failed", e)
            null
        }

        val lastKnown = if (current == null) {
            try {
                client.lastLocation.await()
            } catch (e: Exception) {
                Log.w(TAG, "Last location failed", e)
                null
            }
        } else {
            null
        }

        val picked = LocationFallback.pick(
            current?.toGeoPoint(),
            lastKnown?.toGeoPoint(),
            prefs.getLastSuccessfulOrigin()
        ) ?: return null

        return when {
            current != null -> current
            lastKnown != null &&
                lastKnown.latitude == picked.latitude &&
                lastKnown.longitude == picked.longitude -> lastKnown
            else -> Location("saved-origin").apply {
                latitude = picked.latitude
                longitude = picked.longitude
            }
        }
    }

    private fun Location.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

    companion object {
        private const val TAG = "CommuteEngine"
    }
}
