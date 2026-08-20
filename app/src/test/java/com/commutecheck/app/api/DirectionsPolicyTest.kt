package com.commutecheck.app.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionsPolicyTest {

    @Test
    fun shouldRetry_only5xxAndOverQueryLimit() {
        assertTrue(DirectionsPolicy.shouldRetry(500, null))
        assertTrue(DirectionsPolicy.shouldRetry(503, "UNKNOWN_ERROR"))
        assertTrue(DirectionsPolicy.shouldRetry(200, "OVER_QUERY_LIMIT"))
        assertFalse(DirectionsPolicy.shouldRetry(200, "OK"))
        assertFalse(DirectionsPolicy.shouldRetry(400, "INVALID_REQUEST"))
        assertFalse(DirectionsPolicy.shouldRetry(403, "REQUEST_DENIED"))
        assertTrue(DirectionsPolicy.shouldRetry(429, "OVER_QUERY_LIMIT"))
        assertFalse(DirectionsPolicy.shouldRetry(429, "UNKNOWN_ERROR"))
        assertFalse(DirectionsPolicy.shouldRetry(404, null))
        assertFalse(DirectionsPolicy.shouldRetry(null, null))
        assertFalse(DirectionsPolicy.shouldRetry(200, "ZERO_RESULTS"))
    }
}
