package com.commutecheck.app.domain

import kotlin.math.roundToInt

/**
 * Traffic-delay math shared by the engine, phone UI, and car template.
 *
 * A route is delayed when extra traffic minutes are **at least** the configured
 * threshold (`delayMinutes >= thresholdMinutes`).
 */
object DelayMath {

    fun delayMinutes(durationInTrafficSeconds: Long, durationSeconds: Long): Int {
        val delaySeconds = durationInTrafficSeconds - durationSeconds
        return (delaySeconds / 60.0).roundToInt().coerceAtLeast(0)
    }

    fun isDelayed(delayMinutes: Int, thresholdMinutes: Int): Boolean {
        return delayMinutes >= thresholdMinutes
    }

    fun statusText(isDelayed: Boolean, delayMinutes: Int): String {
        return if (isDelayed) {
            "DELAY  +$delayMinutes min"
        } else {
            "ON TIME"
        }
    }
}
