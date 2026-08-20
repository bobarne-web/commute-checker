package com.commutecheck.app.domain

import com.commutecheck.app.data.CachedRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCooldownTest {

    @Test
    fun isFresh_withinSixtySecondsOnly() {
        val cachedAt = 1_000_000L
        assertTrue(RouteCooldown.isFresh(cachedAt, cachedAt + 1_000L))
        assertTrue(RouteCooldown.isFresh(cachedAt, cachedAt + 59_999L))
        assertFalse(RouteCooldown.isFresh(cachedAt, cachedAt + 60_000L))
        assertFalse(RouteCooldown.isFresh(0L, cachedAt))
        assertFalse(RouteCooldown.isFresh(cachedAt + 5_000L, cachedAt))
    }

    @Test
    fun originIsClose_usesTwoHundredFiftyMeters() {
        val workLat = 39.3270
        val workLng = -120.1830
        assertTrue(RouteCooldown.originIsClose(workLat, workLng, workLat, workLng))
        assertTrue(RouteCooldown.originIsClose(workLat, workLng, workLat + 0.001, workLng))
        assertFalse(RouteCooldown.originIsClose(workLat, workLng, workLat + 0.01, workLng))
    }

    @Test
    fun canReuse_requiresSameDestinationFreshOrigin() {
        val now = 2_000_000L
        val cached = CachedRoute(
            destinationPlaceId = "work",
            originLat = 39.3270,
            originLng = -120.1830,
            durationText = "24 mins",
            durationSeconds = 24 * 60,
            durationInTrafficText = "29 mins",
            durationInTrafficSeconds = 29 * 60,
            distanceText = "18 mi",
            summary = "CA-89",
            cachedAtMs = now - 10_000L
        )
        assertTrue(RouteCooldown.canReuse(cached, "work", 39.3270, -120.1830, now))
        assertFalse(RouteCooldown.canReuse(cached, "truckee", 39.3270, -120.1830, now))
        assertFalse(RouteCooldown.canReuse(cached, "work", 39.50, -119.81, now))
        assertFalse(RouteCooldown.canReuse(cached, "work", 39.3270, -120.1830, now + 60_000L))
        assertFalse(RouteCooldown.canReuse(null, "work", 39.3270, -120.1830, now))
    }
}
