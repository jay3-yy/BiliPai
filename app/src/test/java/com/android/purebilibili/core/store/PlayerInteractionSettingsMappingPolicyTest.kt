package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.store.player.longPressSpeedPreferenceKey
import com.android.purebilibili.core.store.player.playbackSpeedOptionsPreferenceKey
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerInteractionSettingsMappingPolicyTest {

    @Test
    fun emptyPreferences_useExpectedInteractionDefaults() {
        val prefs = mutablePreferencesOf()

        val result = mapPlayerInteractionSettingsFromPreferences(prefs)

        assertEquals(1.0f, result.gestureSensitivity)
        assertTrue(result.doubleTapLikeEnabled)
        assertFalse(result.doubleTapSeekEnabled)
        assertEquals(30, result.inlineSwipeSeekSeconds)
        assertEquals(15, result.fullscreenSwipeSeekSeconds)
        assertEquals(FullscreenAspectRatio.FIT, result.fixedFullscreenAspectRatio)
        assertEquals(SubtitleAutoPreference.OFF, result.subtitleAutoPreference)
        assertEquals(2.0f, result.longPressSpeed)
        assertFalse(result.longPressSpeedLockEnabled)
        assertFalse(result.longPressSpeedLockHintShown)
        assertEquals(0.0f, result.subtitleVerticalOffsetFraction)
        assertTrue(result.subtitlePositionLocked)
        assertEquals(0.0f, result.subtitlePortraitVerticalOffsetFraction)
        assertFalse(result.hideVideoPageStatusBar)
        assertEquals(TabletCommentPanelWidthPreset.STANDARD, result.tabletCommentPanelWidthPreset)
        assertFalse(result.hiResLongPressCompatHintShown)
        assertFalse(result.directPortraitStoryEntry)
        assertFalse(result.launchToPortraitFeedOnStartup)
    }

    @Test
    fun populatedPreferences_mapAndNormalizeInteractionSettings() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("gesture_sensitivity") to 2.8f,
            booleanPreferencesKey("exp_double_tap_like") to false,
            booleanPreferencesKey("double_tap_seek_enabled") to false,
            intPreferencesKey("inline_swipe_seek_seconds") to 12,
            intPreferencesKey("fullscreen_swipe_seek_seconds") to 14,
            intPreferencesKey("fullscreen_aspect_ratio") to FullscreenAspectRatio.RATIO_4_3.value,
            intPreferencesKey("subtitle_auto_preference") to SubtitleAutoPreference.ON.ordinal,
            booleanPreferencesKey("hide_video_page_status_bar") to true,
            intPreferencesKey("tablet_comment_panel_width_preset") to TabletCommentPanelWidthPreset.ULTRA_WIDE.value,
            floatPreferencesKey("long_press_speed") to 4.6f,
            stringPreferencesKey("playback_speed_options") to "1,1.5,2",
            booleanPreferencesKey("long_press_speed_lock_enabled") to true,
            booleanPreferencesKey("long_press_speed_lock_hint_shown") to true,
            floatPreferencesKey("subtitle_vertical_offset_fraction") to -0.42f,
            booleanPreferencesKey("subtitle_position_locked") to false,
            booleanPreferencesKey("two_finger_vertical_speed_enabled") to true,
            booleanPreferencesKey("hi_res_long_press_compat_hint_shown") to true
        )

        val result = mapPlayerInteractionSettingsFromPreferences(prefs)

        assertEquals(2.0f, result.gestureSensitivity)
        assertFalse(result.doubleTapLikeEnabled)
        assertFalse(result.doubleTapSeekEnabled)
        assertEquals(10, result.inlineSwipeSeekSeconds)
        assertEquals(15, result.fullscreenSwipeSeekSeconds)
        assertEquals(FullscreenAspectRatio.RATIO_4_3, result.fixedFullscreenAspectRatio)
        assertEquals(SubtitleAutoPreference.ON, result.subtitleAutoPreference)
        assertTrue(result.hideVideoPageStatusBar)
        assertEquals(TabletCommentPanelWidthPreset.ULTRA_WIDE, result.tabletCommentPanelWidthPreset)
        assertEquals(4.6f, result.longPressSpeed)
        assertTrue(result.longPressSpeedLockEnabled)
        assertTrue(result.longPressSpeedLockHintShown)
        assertEquals(-0.30f, result.subtitleVerticalOffsetFraction)
        assertFalse(result.subtitlePositionLocked)
        assertTrue(result.twoFingerVerticalSpeedEnabled)
        assertTrue(result.hiResLongPressCompatHintShown)
    }

    @Test
    fun removingPlaybackOption_doesNotChangeIndependentLongPressSpeed() {
        val defaultKey = floatPreferencesKey("default_playback_speed")
        val lastKey = floatPreferencesKey("last_playback_speed")
        val prefs = mutablePreferencesOf(
            playbackSpeedOptionsPreferenceKey to "1,1.5,2",
            defaultKey to 1.5f,
            lastKey to 2f,
            longPressSpeedPreferenceKey to 2.75f
        )

        prefs[playbackSpeedOptionsPreferenceKey] = "1,1.5"
        PlayerSettingsStore.reconcilePlaybackSpeedSelections(prefs)

        assertEquals(1.5f, prefs[lastKey])
        assertEquals(2.75f, prefs[longPressSpeedPreferenceKey])
    }

    @Test
    fun savedDoubleTapSeekPreference_keepsUserChoice() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("double_tap_seek_enabled") to true
        )

        val result = mapPlayerInteractionSettingsFromPreferences(prefs)

        assertTrue(result.doubleTapSeekEnabled)
    }

    @Test
    fun emptyPortraitCollapsePreference_defaultsToPortraitOnly() {
        val result = SettingsManager.resolvePortraitPlayerCollapseModePreference(
            rawMode = null,
            legacySwipeHide = null
        )

        assertEquals(PortraitPlayerCollapseMode.INTRO_ONLY, result)
        assertTrue(
            SettingsManager.resolveSwipeHidePlayerEnabledPreference(
                rawMode = null,
                legacySwipeHide = null
            )
        )
    }

    @Test
    fun legacySwipeHidePreference_keepsSavedUserChoice() {
        assertEquals(
            PortraitPlayerCollapseMode.OFF,
            SettingsManager.resolvePortraitPlayerCollapseModePreference(
                rawMode = null,
                legacySwipeHide = false
            )
        )
        assertFalse(
            SettingsManager.resolveSwipeHidePlayerEnabledPreference(
                rawMode = null,
                legacySwipeHide = false
            )
        )
    }

    @Test
    fun portraitCollapseModePreference_takesPriorityOverLegacySwitch() {
        assertEquals(
            PortraitPlayerCollapseMode.BOTH,
            SettingsManager.resolvePortraitPlayerCollapseModePreference(
                rawMode = PortraitPlayerCollapseMode.BOTH.value,
                legacySwipeHide = false
            )
        )
        assertTrue(
            SettingsManager.resolveSwipeHidePlayerEnabledPreference(
                rawMode = PortraitPlayerCollapseMode.BOTH.value,
                legacySwipeHide = false
            )
        )
    }

    @Test
    fun pausedOnlyPortraitCollapseModePreference_keepsSavedUserChoice() {
        assertEquals(
            PortraitPlayerCollapseMode.PAUSED_ONLY,
            SettingsManager.resolvePortraitPlayerCollapseModePreference(
                rawMode = PortraitPlayerCollapseMode.PAUSED_ONLY.value,
                legacySwipeHide = false
            )
        )
        assertTrue(
            SettingsManager.resolveSwipeHidePlayerEnabledPreference(
                rawMode = PortraitPlayerCollapseMode.PAUSED_ONLY.value,
                legacySwipeHide = false
            )
        )
    }

    @Test
    fun invalidTabletCommentPanelWidthPresetFallsBackToStandard() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("tablet_comment_panel_width_preset") to 99
        )

        val result = mapPlayerInteractionSettingsFromPreferences(prefs)

        assertEquals(TabletCommentPanelWidthPreset.STANDARD, result.tabletCommentPanelWidthPreset)
    }
}
