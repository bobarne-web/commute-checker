package com.commutecheck.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteCheckResultPersistenceTest {

    @Test
    fun glanceLine_roundTripsThroughGson() {
        val original = listOf(
            RouteCheckResult(
                watchId = "w1",
                destinationName = "To Work",
                durationInTrafficText = "29 mins",
                distanceText = "18 mi",
                summary = "I-80",
                delayMinutes = 0,
                isDelayed = false,
                destinationPlaceId = "work",
                durationSeconds = 27 * 60L,
                durationInTrafficSeconds = 29 * 60L,
                glanceLine = "there by 7:19 PM · Typical time after 3 trips from Home (1 of 3)",
                originPlaceName = "Home",
                originPlaceId = "home"
            )
        )
        val gson = Gson()
        val json = gson.toJson(original)
        val type = object : TypeToken<List<RouteCheckResult>>() {}.type
        val restored: List<RouteCheckResult> = gson.fromJson(json, type)
        assertEquals(original, restored)
    }
}