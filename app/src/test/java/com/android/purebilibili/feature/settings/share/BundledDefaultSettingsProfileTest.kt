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
    fun firstInstallKeepsRequestedTransitionsAndDisablesPlaybackPrompts() {
        val profile = readProfile()
        listOf(
            "card_transition_enabled",
            "live_surface_card_transition_enabled",
        ).forEach { key ->
            assertEquals(JsonPrimitive(true), profile.sections.appearance[key], key)
        }
        listOf(
            "resume_playback_prompt_enabled",
            "space_played_video_locate_prompt_enabled",
            "next_watch_hint_enabled",
            "background_playback_enabled",
            "auto_play",
        ).forEach { key ->
            assertEquals(JsonPrimitive(false), profile.sections.playback[key], key)
        }
        assertEquals(JsonPrimitive(true), profile.sections.playback["click_to_play"])
        assertEquals(JsonPrimitive(true), profile.sections.playback["stop_playback_on_exit"])
        assertEquals(JsonPrimitive(1), profile.sections.playback["playback_completion_behavior"])
    }
}
