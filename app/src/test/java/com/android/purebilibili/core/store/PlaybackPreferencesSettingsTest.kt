package com.android.purebilibili.core.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.player.DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackPreferencesSettingsTest {
    @Test
    fun absentPreferencesUsePersistedDefaults() {
        val settings = SettingsManager.mapPlaybackPreferencesSettings(mutablePreferencesOf())
        assertEquals(SettingsManager.MiniPlayerMode.OFF, settings.miniPlayerMode)
        assertTrue(settings.backgroundPlaybackEnabled)
        assertTrue(settings.audioNowPlayingBarEnabled)
        assertEquals(80, settings.wifiQuality)
        assertEquals(64, settings.mobileQuality)
        assertEquals("hev1", settings.videoCodecPreference)
        assertEquals("avc1", settings.videoSecondCodecPreference)
        assertEquals(DEFAULT_AUDIO_QUALITY_FOLLOW_LAST, settings.defaultAudioQuality)
        assertEquals(SettingsManager.DataSaverMode.MOBILE_ONLY, settings.dataSaverMode)
    }

    @Test
    fun oneSnapshotPreservesIndependentMiniPlayerAndQualityChoices() {
        val settings = SettingsManager.mapPlaybackPreferencesSettings(mutablePreferencesOf(
            intPreferencesKey("mini_player_mode") to 3,
            booleanPreferencesKey("stop_playback_on_exit") to true,
            booleanPreferencesKey("background_playback_enabled") to false,
            booleanPreferencesKey("audio_now_playing_bar_enabled") to false,
            intPreferencesKey("wifi_default_quality") to 120,
            intPreferencesKey("mobile_default_quality") to 32,
            booleanPreferencesKey("auto_highest_quality") to true,
            stringPreferencesKey("video_codec_preference") to "av01",
            intPreferencesKey("default_audio_quality") to 30280,
            intPreferencesKey("data_saver_mode") to SettingsManager.DataSaverMode.OFF.value,
        ))
        assertEquals(SettingsManager.MiniPlayerMode.IN_APP_AND_SYSTEM_PIP, settings.miniPlayerMode)
        assertTrue(settings.stopPlaybackOnExit)
        assertFalse(settings.backgroundPlaybackEnabled)
        assertFalse(settings.audioNowPlayingBarEnabled)
        assertEquals(120, settings.wifiQuality)
        assertEquals(32, settings.mobileQuality)
        assertTrue(settings.autoHighestQualityEnabled)
        assertEquals("av01", settings.videoCodecPreference)
        assertEquals(30280, settings.defaultAudioQuality)
        assertEquals(SettingsManager.DataSaverMode.OFF, settings.dataSaverMode)
    }

    @Test
    fun unsupportedModeValuesKeepExistingFallbacks() {
        val settings = SettingsManager.mapPlaybackPreferencesSettings(mutablePreferencesOf(
            intPreferencesKey("mini_player_mode") to -1,
            intPreferencesKey("music_lyrics_ui_style") to -1,
        ))
        assertEquals(SettingsManager.MiniPlayerMode.OFF, settings.miniPlayerMode)
        assertEquals(SettingsManager.MusicLyricsUiStyle.ARC, settings.musicLyricsUiStyle)
    }
}
