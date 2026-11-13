package com.android.purebilibili.feature.download

import com.android.purebilibili.core.store.DownloadPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadPreferencePolicyTest {
    @Test
    fun noRememberedQuality_keepsExistingDefault() {
        assertEquals(80, resolvePreferredDownloadQuality(listOf(120, 80, 64), null, 80))
    }

    @Test
    fun availableRememberedQuality_isSelectedRegardlessOfListOrder() {
        assertEquals(80, resolvePreferredDownloadQuality(listOf(64, 120, 80), 80, 120))
    }

    @Test
    fun unavailableRememberedQuality_usesHighestAvailableAtOrBelowPreference() {
        assertEquals(64, resolvePreferredDownloadQuality(listOf(32, 120, 64), 80, 120))
    }

    @Test
    fun allQualitiesExceedPreference_usesLowestAvailable() {
        assertEquals(64, resolvePreferredDownloadQuality(listOf(120, 80, 64), 32, 120))
    }

    @Test
    fun rememberedSelection_restoresDanmakuDisabled() {
        val state = DownloadPreferenceState(
            DownloadPreferences(rememberSelection = true, quality = 80, includeDanmaku = false),
            initialQuality = 80,
        )
        assertTrue(state.rememberSelection)
        assertFalse(state.includeDanmaku)
        assertEquals(80, state.selectedQuality)
    }

    @Test
    fun memoryDisabled_keepsOriginalDanmakuDefault() {
        val state = DownloadPreferenceState(
            DownloadPreferences(rememberSelection = false, includeDanmaku = false),
            initialQuality = 120,
        )
        assertFalse(state.rememberSelection)
        assertTrue(state.includeDanmaku)
    }
}
