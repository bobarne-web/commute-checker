package com.commutecheck.app.domain

/**
 * User-facing place copy. Coordinates stay in the editor; the list shows this.
 */
object PlaceText {

    fun radiusPhrase(radiusMeters: Float): String {
        val meters = radiusMeters.toInt().coerceAtLeast(0)
        return "within $meters m"
    }
}
