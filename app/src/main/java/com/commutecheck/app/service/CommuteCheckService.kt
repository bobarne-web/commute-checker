package com.commutecheck.app.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.commutecheck.app.BuildConfig
import com.commutecheck.app.api.DirectionsApiClient
import com.commutecheck.app.data.DetectedLocation
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.notification.NotificationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CommuteCheckService : Service() {

    companion object {
        private const val TAG = "CommuteCheckService"
        const val ACTION_CHECK_COMMUTE = "com.commutecheck.app.CHECK_COMMUTE"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefsManager: PreferencesManager
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        prefsManager = PreferencesManager(this)
        notificationHelper = NotificationHelper(this)

        startForeground(
            NotificationHelper.NOTIFICATION_SERVICE_ID,
            notificationHelper.createServiceNotification("Checking your commute...")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHECK_COMMUTE) {
            performCommuteCheck()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun performCommuteCheck() {
        serviceScope.launch {
            try {
                val location = getCurrentLocation()
                if (location == null) {
                    Log.e(TAG, "Could not get current location")
                    notificationHelper.showErrorNotification("Could not determine your location")
                    stopSelf()
                    return@launch
                }

                val detectedLocation = detectLocation(location.latitude, location.longitude)
                Log.i(TAG, "Detected location: $detectedLocation")

                when (detectedLocation) {
                    DetectedLocation.WORK -> handleAtWork(location.latitude, location.longitude)
                    DetectedLocation.HOME -> handleAtHome(location.latitude, location.longitude)
                    DetectedLocation.UNKNOWN -> {
                        Log.i(TAG, "Not at home or work, skipping")
                        notificationHelper.showSkippedNotification(
                            "Not at home or work location"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during commute check", e)
                notificationHelper.showErrorNotification("Error: ${e.message}")
            } finally {
                stopSelf()
            }
        }
    }

    private suspend fun getCurrentLocation(): android.location.Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        return try {
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    private fun detectLocation(currentLat: Double, currentLng: Double): DetectedLocation {
        val homeLocation = prefsManager.getHomeLocation()
        val workLocation = prefsManager.getWorkLocation()
        val radius = prefsManager.getGeofenceRadius()

        val homeDistance = homeLocation?.distanceTo(currentLat, currentLng) ?: Float.MAX_VALUE
        val workDistance = workLocation?.distanceTo(currentLat, currentLng) ?: Float.MAX_VALUE

        Log.d(TAG, "Distance to home: ${homeDistance}m, work: ${workDistance}m, radius: ${radius}m")

        return when {
            workDistance <= radius -> DetectedLocation.WORK
            homeDistance <= radius -> DetectedLocation.HOME
            else -> DetectedLocation.UNKNOWN
        }
    }

    private suspend fun handleAtWork(currentLat: Double, currentLng: Double) {
        val homeLocation = prefsManager.getHomeLocation() ?: return

        if (prefsManager.isWorkAlwaysCheck()) {
            Log.i(TAG, "At work — checking travel time to home (always-on)")
            fetchAndNotifyTravelTime(
                currentLat, currentLng,
                homeLocation.latitude, homeLocation.longitude,
                DetectedLocation.WORK
            )
        } else {
            notificationHelper.showSkippedNotification(
                "At work but always-check is disabled"
            )
        }
    }

    private suspend fun handleAtHome(currentLat: Double, currentLng: Double) {
        val workLocation = prefsManager.getWorkLocation() ?: return
        val schedule = prefsManager.getScheduleConfig()

        if (schedule.isWithinSchedule()) {
            Log.i(TAG, "At home — within schedule, checking travel time to work")
            fetchAndNotifyTravelTime(
                currentLat, currentLng,
                workLocation.latitude, workLocation.longitude,
                DetectedLocation.HOME
            )
        } else {
            Log.i(TAG, "At home — outside scheduled check window, skipping")
            notificationHelper.showSkippedNotification(
                "Outside scheduled check window for home → work"
            )
        }
    }

    private suspend fun fetchAndNotifyTravelTime(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        detectedLocation: DetectedLocation
    ) {
        val apiKey = prefsManager.getMapsApiKey().ifEmpty { BuildConfig.MAPS_API_KEY }
        if (apiKey.isEmpty()) {
            notificationHelper.showErrorNotification("Google Maps API key not configured")
            return
        }

        val client = DirectionsApiClient(apiKey)
        val result = client.getTravelTime(originLat, originLng, destLat, destLng)

        result.fold(
            onSuccess = { travelTime ->
                Log.i(TAG, "Travel time: ${travelTime.durationInTrafficText}")
                notificationHelper.showTravelTimeNotification(detectedLocation, travelTime)
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to get travel time", error)
                notificationHelper.showErrorNotification(
                    "Could not get travel time: ${error.message}"
                )
            }
        )
    }
}
