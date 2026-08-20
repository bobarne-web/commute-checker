package com.commutecheck.app.domain

/**
 * GPS point used by location fallback math. Kept free of Android Location so
 * the pick order can be unit-tested.
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * If a high-accuracy fix is missing, use last known, then the last origin that
 * produced a successful check.
 */
object LocationFallback {

    fun pick(current: GeoPoint?, lastKnown: GeoPoint?, savedOrigin: GeoPoint?): GeoPoint? {
        return current ?: lastKnown ?: savedOrigin
    }
}
