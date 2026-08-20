package com.commutecheck.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationFallbackTest {

    private val current = GeoPoint(39.32, -120.18)
    private val lastKnown = GeoPoint(39.33, -120.19)
    private val saved = GeoPoint(39.50, -119.81)

    @Test
    fun pick_prefersCurrentThenLastThenSaved() {
        assertEquals(current, LocationFallback.pick(current, lastKnown, saved))
        assertEquals(lastKnown, LocationFallback.pick(null, lastKnown, saved))
        assertEquals(saved, LocationFallback.pick(null, null, saved))
        assertNull(LocationFallback.pick(null, null, null))
    }
}
