package com.commutecheck.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceTextTest {

    @Test
    fun radiusPhrase_isFriendlyMeters() {
        assertEquals("within 500 m", PlaceText.radiusPhrase(500f))
        assertEquals("within 500 m", PlaceText.radiusPhrase(500.4f))
        assertEquals("within 0 m", PlaceText.radiusPhrase(-5f))
    }
}
