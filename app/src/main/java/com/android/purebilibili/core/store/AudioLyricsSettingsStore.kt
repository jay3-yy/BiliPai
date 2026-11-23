package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ArcLyricsTiltIntensity(
    val value: Int,
    val label: String,
    val tiltScale: Float,
) {
    SOFT(0, "柔和", 0.65f),
    STANDARD(1, "标准", 1.0f),
    VIVID(2, "立体", 1.35f);

    companion object {
        fun fromValue(value: Int): ArcLyricsTiltIntensity = when (value) {
            0 -> SOFT
            2 -> VIVID
            else -> STANDARD
        }
    }
}

enum class ArcLyricsCurveDirection(
    val value: Int,
    val label: String,
    val directionSign: Float,
) {
    LEFT(0, "向左", -1f),
    RIGHT(1, "向右", 1f);

    companion object {
        fun fromValue(value: Int): ArcLyricsCurveDirection = when (value) {
            1 -> RIGHT
            else -> LEFT
        }
    }
}

enum class LyricsTranslationFontScale(
    val value: Int,
    val label: String,
    val scale: Float,
) {
    SMALL(0, "小", 0.85f),
    STANDARD(1, "标准", 1.0f),
    LARGE(2, "大", 1.15f);

    companion object {
        fun fromValue(value: Int): LyricsTranslationFontScale = when (value) {
            0 -> SMALL
            2 -> LARGE
            else -> STANDARD
        }
    }
}

object AudioLyricsSettingsStore {
    private val KEY_ARC_TILT_INTENSITY = intPreferencesKey("music_lyrics_arc_tilt_intensity")
    private val KEY_ARC_CURVE_DIRECTION = intPreferencesKey("music_lyrics_arc_curve_direction")
    private val KEY_TRANSLATION_FONT_SCALE = intPreferencesKey("music_lyrics_translation_font_scale")

    private fun translationKey(videoAudioMode: Boolean) = booleanPreferencesKey(
        if (videoAudioMode) "audio_mode_subtitle_show_translations" else "music_lyrics_show_translations"
    )

    fun observeShowTranslations(context: Context, videoAudioMode: Boolean) = context.settingsDataStore.data
        .map { it[translationKey(videoAudioMode)] ?: !videoAudioMode }
        .distinctUntilChanged()

    suspend fun setShowTranslations(context: Context, enabled: Boolean, videoAudioMode: Boolean) {
        context.settingsDataStore.edit { it[translationKey(videoAudioMode)] = enabled }
    }

    fun observeArcTiltIntensity(context: Context) = context.settingsDataStore.data
        .map { ArcLyricsTiltIntensity.fromValue(it[KEY_ARC_TILT_INTENSITY] ?: ArcLyricsTiltIntensity.STANDARD.value) }
        .distinctUntilChanged()

    suspend fun setArcTiltIntensity(context: Context, intensity: ArcLyricsTiltIntensity) {
        context.settingsDataStore.edit { it[KEY_ARC_TILT_INTENSITY] = intensity.value }
    }

    fun observeArcCurveDirection(context: Context) = context.settingsDataStore.data
        .map { ArcLyricsCurveDirection.fromValue(it[KEY_ARC_CURVE_DIRECTION] ?: ArcLyricsCurveDirection.LEFT.value) }
        .distinctUntilChanged()

    suspend fun setArcCurveDirection(context: Context, direction: ArcLyricsCurveDirection) {
        context.settingsDataStore.edit { it[KEY_ARC_CURVE_DIRECTION] = direction.value }
    }

    fun observeTranslationFontScale(context: Context) = context.settingsDataStore.data
        .map { LyricsTranslationFontScale.fromValue(it[KEY_TRANSLATION_FONT_SCALE] ?: LyricsTranslationFontScale.STANDARD.value) }
        .distinctUntilChanged()

    suspend fun setTranslationFontScale(context: Context, scale: LyricsTranslationFontScale) {
        context.settingsDataStore.edit { it[KEY_TRANSLATION_FONT_SCALE] = scale.value }
    }
}
