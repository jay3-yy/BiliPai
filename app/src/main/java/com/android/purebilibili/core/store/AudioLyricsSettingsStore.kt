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

enum class MusicThemeColorMode(
    val value: Int,
    val label: String,
    val seedArgb: Long?,
) {
    FOLLOW_COVER(0, "跟随封面", null),
    FOLLOW_APP_THEME(1, "跟随应用主题", null),
    MIDNIGHT_BLUE(2, "午夜深蓝", 0xFF2F7DFF),
    SAKURA_ROSE(3, "樱粉玫瑰", 0xFFFB7299),
    EMERALD_MIST(4, "森屿青绿", 0xFF2ECC71),
    AMBER_GLOW(5, "琥珀暖金", 0xFFFF9F43),
    VIOLET_DUSK(6, "暮光幻紫", 0xFF9B59B6),
    OBSIDIAN_PURE(7, "纯粹曜黑", 0xFF1A1C20),
    CUSTOM(8, "自定义颜色", null);

    companion object {
        fun fromValue(value: Int): MusicThemeColorMode =
            entries.firstOrNull { it.value == value } ?: FOLLOW_COVER
    }
}

object AudioLyricsSettingsStore {
    const val DEFAULT_CUSTOM_THEME_COLOR_HEX = "#2F7DFF"
    private val KEY_ARC_TILT_INTENSITY = intPreferencesKey("music_lyrics_arc_tilt_intensity")
    private val KEY_ARC_CURVE_DIRECTION = intPreferencesKey("music_lyrics_arc_curve_direction")
    private val KEY_TRANSLATION_FONT_SCALE = intPreferencesKey("music_lyrics_translation_font_scale")
    private val KEY_MUSIC_THEME_COLOR_MODE = intPreferencesKey("music_theme_color_mode")
    private val KEY_MUSIC_CUSTOM_THEME_COLOR_HEX =
        androidx.datastore.preferences.core.stringPreferencesKey("music_custom_theme_color_hex")

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

    fun observeThemeColorMode(context: Context) = context.settingsDataStore.data
        .map { MusicThemeColorMode.fromValue(it[KEY_MUSIC_THEME_COLOR_MODE] ?: MusicThemeColorMode.FOLLOW_COVER.value) }
        .distinctUntilChanged()

    suspend fun setThemeColorMode(context: Context, mode: MusicThemeColorMode) {
        context.settingsDataStore.edit { it[KEY_MUSIC_THEME_COLOR_MODE] = mode.value }
    }

    fun observeCustomThemeColorHex(context: Context) = context.settingsDataStore.data
        .map { normalizeHexColor(it[KEY_MUSIC_CUSTOM_THEME_COLOR_HEX]) ?: DEFAULT_CUSTOM_THEME_COLOR_HEX }
        .distinctUntilChanged()

    suspend fun setCustomThemeColorHex(context: Context, hex: String) {
        val normalized = normalizeHexColor(hex) ?: return
        context.settingsDataStore.edit {
            it[KEY_MUSIC_CUSTOM_THEME_COLOR_HEX] = normalized
            it[KEY_MUSIC_THEME_COLOR_MODE] = MusicThemeColorMode.CUSTOM.value
        }
    }

    fun normalizeHexColor(raw: String?): String? {
        val cleaned = raw?.trim()?.removePrefix("#")?.uppercase() ?: return null
        if (cleaned.length != 6 || !cleaned.all { it in '0'..'9' || it in 'A'..'F' }) return null
        return "#$cleaned"
    }

    fun parseHexColorArgb(raw: String?): Long? {
        val normalized = normalizeHexColor(raw) ?: return null
        return 0xFF000000L or normalized.substring(1).toLong(16)
    }
}
