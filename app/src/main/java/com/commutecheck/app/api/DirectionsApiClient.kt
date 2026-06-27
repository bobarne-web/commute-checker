package com.commutecheck.app.api

import com.commutecheck.app.data.TravelTimeResult
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class DirectionsApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://maps.googleapis.com/maps/api/directions/json"
    }

    suspend fun getTravelTime(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<TravelTimeResult> = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append(BASE_URL)
                append("?origin=$originLat,$originLng")
                append("&destination=$destLat,$destLng")
                append("&departure_time=now")
                append("&traffic_model=best_guess")
                append("&key=$apiKey")
            }

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(
                    IOException("API request failed: ${response.code}")
                )
            }

            val json = gson.fromJson(body, JsonObject::class.java)
            val status = json.get("status")?.asString

            if (status != "OK") {
                val errorMsg = json.get("error_message")?.asString ?: "Unknown error"
                return@withContext Result.failure(
                    IOException("Directions API error: $status - $errorMsg")
                )
            }

            val routes = json.getAsJsonArray("routes")
            if (routes == null || routes.size() == 0) {
                return@withContext Result.failure(
                    IOException("No routes found")
                )
            }

            val route = routes[0].asJsonObject
            val leg = route.getAsJsonArray("legs")[0].asJsonObject
            val summary = route.get("summary")?.asString ?: ""

            val duration = leg.getAsJsonObject("duration")
            val durationText = duration.get("text").asString
            val durationSeconds = duration.get("value").asLong

            val durationInTraffic = leg.getAsJsonObject("duration_in_traffic")
            val durationInTrafficText = durationInTraffic?.get("text")?.asString ?: durationText
            val durationInTrafficSeconds = durationInTraffic?.get("value")?.asLong ?: durationSeconds

            val distance = leg.getAsJsonObject("distance")
            val distanceText = distance.get("text").asString

            Result.success(
                TravelTimeResult(
                    durationText = durationText,
                    durationSeconds = durationSeconds,
                    durationInTrafficText = durationInTrafficText,
                    durationInTrafficSeconds = durationInTrafficSeconds,
                    distanceText = distanceText,
                    summary = summary
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
