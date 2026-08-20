package com.commutecheck.app.domain

import com.commutecheck.app.data.CachedRoute
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Short-lived reuse of the last successful Directions result so a car Refresh
 * mash does not hit the network every time.
 */
object RouteCooldown {

    const val TTL_MS = 60_000L
    const val ORIGIN_RADIUS_METERS = 250.0

    fun isFresh(cachedAtMs: Long, nowMs: Long, ttlMs: Long = TTL_MS): Boolean {
        if (cachedAtMs <= 0L) return false
        val age = nowMs - cachedAtMs
        return age in 0 until ttlMs
    }

    fun originIsClose(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
        maxMeters: Double = ORIGIN_RADIUS_METERS
    ): Boolean = distanceMeters(lat1, lng1, lat2, lng2) <= maxMeters

    fun canReuse(
        cached: CachedRoute?,
        destinationPlaceId: String,
        originLat: Double,
        originLng: Double,
        nowMs: Long
    ): Boolean {
        if (cached == null) return false
        if (cached.destinationPlaceId != destinationPlaceId) return false
        if (!isFresh(cached.cachedAtMs, nowMs)) return false
        return originIsClose(cached.originLat, cached.originLng, originLat, originLng)
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * asin(min(1.0, sqrt(a)))
        return earthRadius * c
    }
}
