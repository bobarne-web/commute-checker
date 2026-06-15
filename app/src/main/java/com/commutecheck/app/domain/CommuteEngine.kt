package com.commutecheck.app.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.commutecheck.app.BuildConfig
import com.commutecheck.app.api.DirectionsApiClient
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.RouteCheckResult
import com.commutecheck.app.data.Watch
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Shared logic for running commute checks. Used by both the foreground service
 * (for notifications) and the Android Auto car screen (for the dashboard).
 */
class CommuteEngine(private val context: Context) {

    private val prefs = PreferencesManager(context)

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

        val location = getCurrentLocation()
            ?: return EngineResult.Failure("Could not determine your location")

        val currentPlace = detectCurrentPlace(location.latitude, location.longitude, places)
        val now = Calendar.getInstance()
        val thresholdMin = prefs.getDelayThresholdMinutes()

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

        val client = DirectionsApiClient(apiKey)
        val results = coroutineScope {
            activeWatches.map { watch ->
                async {
                    evaluateWatch(client, watch, location, places, thresholdMin)
                }
            }.awaitAll()
        }.filterNotNull()

        prefs.saveLastResults(currentPlace?.name ?: "On the road", results)
        return EngineResult.Success(currentPlace?.name, results)
    }

    private suspend fun evaluateWatch(
        client: DirectionsApiClient,
        watch: Watch,
        origin: Location,
        places: List<Place>,
        thresholdMin: Int
    ): RouteCheckResult? {
        val destination = places.firstOrNull { it.id == watch.destinationPlaceId } ?: return null

        val result = client.getTravelTime(
            origin.latitude, origin.longitude,
            destination.latitude, destination.longitude
        )

        return result.fold(
            onSuccess = { travel ->
                val delaySeconds = travel.durationInTrafficSeconds - travel.durationSeconds
                val delayMinutes = (delaySeconds / 60.0).roundToInt().coerceAtLeast(0)
                RouteCheckResult(
                    watchId = watch.id,
                    destinationName = watch.name.ifBlank { destination.name },
                    durationInTrafficText = travel.durationInTrafficText,
                    distanceText = travel.distanceText,
                    summary = travel.summary,
                    delayMinutes = delayMinutes,
                    isDelayed = delayMinutes >= thresholdMin
                )
            },
            onFailure = { error ->
                Log.e(TAG, "Watch '${watch.name}' failed", error)
                RouteCheckResult(
                    watchId = watch.id,
                    destinationName = watch.name.ifBlank { destination.name },
                    durationInTrafficText = "",
                    distanceText = "",
                    summary = "",
                    delayMinutes = 0,
                    isDelayed = false,
                    error = error.message ?: "Failed to get travel time"
                )
            }
        )
    }

    private fun detectCurrentPlace(lat: Double, lng: Double, places: List<Place>): Place? {
        return places
            .filter { it.contains(lat, lng) }
            .minByOrNull { it.distanceTo(lat, lng) }
    }

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val token = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    companion object {
        private const val TAG = "CommuteEngine"
    }
}
