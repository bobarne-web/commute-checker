package com.commutecheck.app.domain

/**
 * Short labels for "when was this check". Anything under a minute is "Just now"
 * so the home header does not say "0 minutes ago".
 */
object RelativeTime {

    const val JUST_NOW_WINDOW_MS = 60_000L

    fun label(timestampMs: Long, nowMs: Long, older: (Long) -> String): String {
        if (timestampMs <= 0L) return ""
        val delta = kotlin.math.abs(nowMs - timestampMs)
        if (delta < JUST_NOW_WINDOW_MS) return "Just now"
        return older(timestampMs)
    }
}
