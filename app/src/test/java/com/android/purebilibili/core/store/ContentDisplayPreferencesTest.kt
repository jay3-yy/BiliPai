package com.android.purebilibili.core.store

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentDisplayPreferencesTest {
    @Test
    fun `legacy retention preserves actual behavior including missing defaults`() {
        assertEquals(true, resolveLegacyRecommendRetention(null, null))
        assertEquals(true, resolveLegacyRecommendRetention(false, true))
        assertEquals(true, resolveLegacyRecommendRetention(true, false))
        assertEquals(false, resolveLegacyRecommendRetention(false, false))
    }

    @Test
    fun `invalid imported display values fall back to existing defaults`() {
        listOf(-1, 1, 55, 101, Int.MAX_VALUE).forEach {
            assertEquals(0, ContentDisplayPreferences.normalizeQuality(it))
        }
        listOf(0, 40, 70, 90, 100).forEach { assertEquals(it, ContentDisplayPreferences.normalizeQuality(it)) }
        listOf(-10, 15, 100).forEach { assertEquals(0, ContentDisplayPreferences.normalizeDim(it)) }
        assertEquals(ScrollFeedbackPreset.STANDARD, ScrollFeedbackPreset.fromValue(99))
    }
}
