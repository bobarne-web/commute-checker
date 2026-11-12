package com.commutecheck.app.domain

/**
 * Which saved place the user is standing in. Pure math so a check from Home
 * does not get labeled "On the road" when the fix is inside that place's radius.
 */
data class OriginCandidate(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float
)

object OriginPlaces {

    fun nearestMatch(
        latitude: Double,
        longitude: Double,
        places: List<OriginCandidate>
    ): OriginCandidate? {
        return places
            .map { place ->
                place to RouteCooldown.distanceMeters(
                    latitude,
                    longitude,
                    place.latitude,
                    place.longitude
                )
            }
            .filter { (place, distance) -> distance <= place.radiusMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }
}
