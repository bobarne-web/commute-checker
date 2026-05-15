package com.commutecheck.app.auto

import android.Manifest
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.app.ActivityCompat
import androidx.core.graphics.drawable.IconCompat
import com.commutecheck.app.BuildConfig
import com.commutecheck.app.R
import com.commutecheck.app.api.DirectionsApiClient
import com.commutecheck.app.data.DetectedLocation
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.TravelTimeResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CommuteScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefsManager = PreferencesManager(carContext)

    private var statusTitle = "Commute Checker"
    private var statusText = "Checking your commute..."
    private var travelTimeResult: TravelTimeResult? = null
    private var detectedLocation: DetectedLocation = DetectedLocation.UNKNOWN
    private var isLoading = true

    init {
        fetchCommuteInfo()
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        if (isLoading) {
            paneBuilder.setLoading(true)
        } else {
            val result = travelTimeResult
            if (result != null) {
                val destination = when (detectedLocation) {
                    DetectedLocation.WORK -> "Home"
                    DetectedLocation.HOME -> "Work"
                    DetectedLocation.UNKNOWN -> "—"
                }

                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle("Travel time to $destination")
                        .addText(result.durationInTrafficText)
                        .build()
                )

                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle("Distance")
                        .addText(result.distanceText)
                        .build()
                )

                if (result.summary.isNotEmpty()) {
                    paneBuilder.addRow(
                        Row.Builder()
                            .setTitle("Route")
                            .addText("via ${result.summary}")
                            .build()
                    )
                }

                val trafficDiff = result.durationInTrafficSeconds - result.durationSeconds
                if (trafficDiff > 60) {
                    val extraMin = trafficDiff / 60
                    paneBuilder.addRow(
                        Row.Builder()
                            .setTitle("Traffic delay")
                            .addText("+$extraMin min")
                            .build()
                    )
                }
            } else {
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle(statusTitle)
                        .addText(statusText)
                        .build()
                )
            }

            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener {
                        isLoading = true
                        travelTimeResult = null
                        invalidate()
                        fetchCommuteInfo()
                    }
                    .build()
            )
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Commute Checker")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun fetchCommuteInfo() {
        scope.launch {
            try {
                if (!prefsManager.isConfigured()) {
                    statusTitle = "Setup Required"
                    statusText = "Open the phone app to set home & work locations"
                    isLoading = false
                    invalidate()
                    return@launch
                }

                val location = getCurrentLocation()
                if (location == null) {
                    statusTitle = "Location Unavailable"
                    statusText = "Could not determine your location"
                    isLoading = false
                    invalidate()
                    return@launch
                }

                detectedLocation = detectLocation(location.latitude, location.longitude)

                when (detectedLocation) {
                    DetectedLocation.WORK -> {
                        val home = prefsManager.getHomeLocation()!!
                        fetchTravelTime(
                            location.latitude, location.longitude,
                            home.latitude, home.longitude
                        )
                    }
                    DetectedLocation.HOME -> {
                        val schedule = prefsManager.getScheduleConfig()
                        if (schedule.isWithinSchedule()) {
                            val work = prefsManager.getWorkLocation()!!
                            fetchTravelTime(
                                location.latitude, location.longitude,
                                work.latitude, work.longitude
                            )
                        } else {
                            statusTitle = "No Check Needed"
                            statusText = "Outside your scheduled home→work check window"
                            isLoading = false
                            invalidate()
                        }
                    }
                    DetectedLocation.UNKNOWN -> {
                        statusTitle = "Unknown Location"
                        statusText = "You're not near your home or work"
                        isLoading = false
                        invalidate()
                    }
                }
            } catch (e: Exception) {
                statusTitle = "Error"
                statusText = e.message ?: "An error occurred"
                isLoading = false
                invalidate()
            }
        }
    }

    private suspend fun getCurrentLocation(): android.location.Location? {
        if (ActivityCompat.checkSelfPermission(
                carContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(carContext)
            val cancellationToken = CancellationTokenSource()
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()
        } catch (e: Exception) {
            null
        }
    }

    private fun detectLocation(lat: Double, lng: Double): DetectedLocation {
        val home = prefsManager.getHomeLocation()
        val work = prefsManager.getWorkLocation()
        val radius = prefsManager.getGeofenceRadius()

        val homeDist = home?.distanceTo(lat, lng) ?: Float.MAX_VALUE
        val workDist = work?.distanceTo(lat, lng) ?: Float.MAX_VALUE

        return when {
            workDist <= radius -> DetectedLocation.WORK
            homeDist <= radius -> DetectedLocation.HOME
            else -> DetectedLocation.UNKNOWN
        }
    }

    private suspend fun fetchTravelTime(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double
    ) {
        val apiKey = prefsManager.getMapsApiKey().ifEmpty { BuildConfig.MAPS_API_KEY }
        if (apiKey.isEmpty()) {
            statusTitle = "API Key Missing"
            statusText = "Set your Google Maps API key in the phone app settings"
            isLoading = false
            invalidate()
            return
        }

        val client = DirectionsApiClient(apiKey)
        val result = client.getTravelTime(originLat, originLng, destLat, destLng)

        result.fold(
            onSuccess = { travelTime ->
                travelTimeResult = travelTime
                isLoading = false
                invalidate()
            },
            onFailure = { error ->
                statusTitle = "Error"
                statusText = "Could not get travel time: ${error.message}"
                isLoading = false
                invalidate()
            }
        )
    }
}
