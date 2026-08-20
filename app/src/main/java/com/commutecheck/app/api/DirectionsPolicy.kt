package com.commutecheck.app.api

/**
 * When a Directions call should be tried once more. Transient 5xx and the
 * documented OVER_QUERY_LIMIT status are the only retry cases.
 */
object DirectionsPolicy {

    fun shouldRetry(httpCode: Int?, apiStatus: String?): Boolean {
        if (httpCode != null && httpCode in 500..599) return true
        return apiStatus == "OVER_QUERY_LIMIT"
    }
}
