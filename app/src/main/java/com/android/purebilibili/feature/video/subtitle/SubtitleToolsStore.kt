package com.android.purebilibili.feature.video.subtitle

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.android.purebilibili.core.store.settingsDataStore
import com.android.purebilibili.feature.video.ui.components.PlayerSubtitleAppearance
import com.android.purebilibili.feature.video.ui.components.SubtitlePlacement
import kotlinx.coroutines.flow.map

object SubtitleToolsStore {
    private val inline = floatPreferencesKey("subtitle_tools_inline_scale")
    private val fullscreen = floatPreferencesKey("subtitle_tools_fullscreen_scale")
    private val weight = intPreferencesKey("subtitle_tools_weight")
    private val stroke = floatPreferencesKey("subtitle_tools_stroke")
    private val horizontal = intPreferencesKey("subtitle_tools_horizontal_padding")
    private val bottom = intPreferencesKey("subtitle_tools_bottom_padding")
    private val opacity = floatPreferencesKey("subtitle_tools_background_opacity")

    private val textColor = intPreferencesKey("subtitle_tools_text_color")
    private val strokeColor = intPreferencesKey("subtitle_tools_stroke_color")
    private val fontFile = stringPreferencesKey("subtitle_tools_font_file")
    private val fontName = stringPreferencesKey("subtitle_tools_font_name")
    private val placement = stringPreferencesKey("subtitle_tools_placement")

    fun appearance(context: Context) = context.settingsDataStore.data.map { prefs ->
        normalizeSubtitleAppearance(PlayerSubtitleAppearance(
            prefs[inline] ?: 1f, prefs[fullscreen] ?: 1.5f, prefs[weight] ?: 6,
            prefs[stroke] ?: 2f, prefs[horizontal] ?: 24, prefs[bottom] ?: 24, prefs[opacity] ?: 0.67f,
            prefs[textColor] ?: android.graphics.Color.WHITE, prefs[strokeColor] ?: android.graphics.Color.BLACK,
            prefs[fontFile].orEmpty(), prefs[fontName].orEmpty(),
            SubtitlePlacement.fromStored(prefs[placement]),
        ))
    }

    suspend fun setAppearance(context: Context, appearance: PlayerSubtitleAppearance) {
        val value = normalizeSubtitleAppearance(appearance)
        context.settingsDataStore.edit {
            it[inline] = value.inlineFontScale; it[fullscreen] = value.fullscreenFontScale
            it[weight] = value.fontWeight; it[stroke] = value.strokeWidth
            it[horizontal] = value.horizontalPadding; it[bottom] = value.bottomPadding
            it[opacity] = value.backgroundOpacity
            it[textColor] = value.textColor; it[strokeColor] = value.strokeColor
            it[fontFile] = value.fontFileName; it[fontName] = value.fontDisplayName
            it[placement] = value.placement.storedValue
        }
    }
}

fun normalizeSubtitleAppearance(value: PlayerSubtitleAppearance) = value.copy(
    inlineFontScale = value.inlineFontScale.takeIf { it.isFinite() }?.coerceIn(0.5f, 2.5f) ?: 1f,
    fullscreenFontScale = value.fullscreenFontScale.takeIf { it.isFinite() }?.coerceIn(0.5f, 2.5f) ?: 1.5f,
    fontWeight = value.fontWeight.coerceIn(1, 9), strokeWidth = value.strokeWidth.takeIf { it.isFinite() }?.coerceIn(0f, 5f) ?: 2f,
    horizontalPadding = value.horizontalPadding.coerceIn(0, 100), bottomPadding = value.bottomPadding.coerceIn(0, 200),
    backgroundOpacity = value.backgroundOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.67f
)
