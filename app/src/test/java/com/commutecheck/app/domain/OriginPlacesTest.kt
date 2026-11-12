package com.commutecheck.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginPlacesTest {

    private val home = OriginCandidate(
        id = "home",
        name = "Home",
        latitude = 39.3280,
        longitude = -120.1833,
        radiusMeters = 500f
    )
    private val work = OriginCandidate(
        id = "work",
        name = "Work",
        latitude = 39.3500,
        longitude = -120.1833,
        radiusMeters = 500f
    )

    @Test
    fun matchesPlaceWhenFixIsInsideItsRadius() {
        val inside = home.latitude + (400.0 / 111_320.0)
        val match = OriginPlaces.nearestMatch(inside, home.longitude, listOf(home, work))
        assertEquals("home", match?.id)
        assertTrue(
            RouteCooldown.distanceMeters(inside, home.longitude, home.latitude, home.longitude) <= 500.0
        )
    }

    @Test
    fun doesNotMatchWhenFixIsOutsideEveryRadius() {
        val outside = home.latitude + (800.0 / 111_320.0)
        assertNull(OriginPlaces.nearestMatch(outside, home.longitude, listOf(home)))
    }

    @Test
    fun prefersTheCloserPlaceWhenRadiiOverlap() {
        val wideHome = home.copy(radiusMeters = 5_000f)
        val nearWork = work.copy(
            latitude = home.latitude + (200.0 / 111_320.0),
            radiusMeters = 5_000f
        )
        val atWork = nearWork.latitude
        val match = OriginPlaces.nearestMatch(atWork, home.longitude, listOf(wideHome, nearWork))
        assertEquals("work", match?.id)
    }
}
