package com.android.purebilibili.feature.settings.share

import com.android.purebilibili.core.store.SettingsManager
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledDefaultSettingsProfileTest {
    private fun readProfile(): SettingsShareProfile {
        val asset = sequenceOf(
            File("src/main/assets/default_settings_profile.json"),
            File("app/src/main/assets/default_settings_profile.json"),
        ).first { it.isFile }
        return Json.decodeFromString(SettingsShareProfile.serializer(), asset.readText())
    }

    @Test
    fun bundledProfileHasNoSilentlySkippedSettings() {
        val profile = readProfile()
        val preview = resolveSettingsShareImportPreview(
            profile = profile,
            definitions = SettingsManager.getShareableSettingsEntryDefinitions(),
        )
        assertTrue(preview.skippedKeys.isEmpty(), "Unrecognized defaults: ${preview.skippedKeys}")
    }

    @Test
    fun firstInstallUsesLightweightEffectsAndDisablesPlaybackPrompts() {
        val profile = readProfile()
        listOf(
            "card_transition_enabled",
            "live_surface_card_transition_enabled",
            "skeleton_breathing_enabled",
            "android_native_liquid_glass_enabled",
            "header_blur_enabled",
            "video_transition_realtime_blur_enabled",
            "miuix_transition_blur_enabled",
            "home_hero_carousel_autoplay_enabled",
        ).forEach { key ->
            assertEquals(JsonPrimitive(false), profile.sections.appearance[key], key)
        }
        listOf(
            "resume_playback_prompt_enabled",
            "space_played_video_locate_prompt_enabled",
            "next_watch_hint_enabled",
            "background_playback_enabled",
            "auto_play",
            "auto_highest_quality",
            "live_auto_mini_player_enabled",
            "resilient_video_buffer",
        ).forEach { key ->
            assertEquals(JsonPrimitive(false), profile.sections.playback[key], key)
        }
        assertEquals(JsonPrimitive(true), profile.sections.playback["click_to_play"])
        assertEquals(JsonPrimitive(true), profile.sections.playback["stop_playback_on_exit"])
        assertEquals(JsonPrimitive(1), profile.sections.playback["playback_completion_behavior"])
        assertEquals(JsonPrimitive(true), profile.sections.playback["hw_decode"])
        assertEquals(JsonPrimitive(80), profile.sections.playback["wifi_default_quality"])
        assertEquals(JsonPrimitive(64), profile.sections.playback["mobile_default_quality"])
        assertEquals(JsonPrimitive(400), profile.sections.playback["live_wifi_quality"])
        assertEquals(JsonPrimitive(250), profile.sections.playback["live_mobile_quality"])
    }
}
