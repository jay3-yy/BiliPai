package com.android.purebilibili.core.store.home

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.android.purebilibili.core.store.LiquidGlassReadabilityMode
import com.android.purebilibili.core.store.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val liquidGlassReadabilityModePreferencesKey =
    intPreferencesKey("liquid_glass_readability_mode")

enum class GlassPopupPalette(val value: Int, val label: String) {
    DOCK(0, "原有底色"),
    THEME_FROSTED(1, "主题磨砂");

    companion object {
        fun fromValue(value: Int): GlassPopupPalette = entries.firstOrNull { it.value == value } ?: DOCK
    }
}

enum class GlassMotionStyle(val value: Int, val label: String) {
    STANDARD(0, "标准"),
    ELASTIC(1, "弹性");

    companion object {
        fun fromValue(value: Int): GlassMotionStyle = entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

internal val glassDockMotionStylePreferencesKey = intPreferencesKey("liquid_glass_dock_motion_style")

internal val glassPopupMotionStylePreferencesKey = intPreferencesKey("liquid_glass_popup_motion_style")

internal val glassPopupPalettePreferencesKey = intPreferencesKey("liquid_glass_popup_palette")
internal val progressiveBlurRendererPreferencesKey = intPreferencesKey("progressive_blur_renderer")

object LiquidGlassSettingsStore {
    fun observeProgressiveBlurRenderer(context: Context): Flow<ProgressiveBlurRenderer> =
        context.settingsDataStore.data.map {
            ProgressiveBlurRenderer.fromValue(it[progressiveBlurRendererPreferencesKey] ?: 0)
        }.distinctUntilChanged()

    suspend fun setProgressiveBlurRenderer(context: Context, renderer: ProgressiveBlurRenderer) {
        context.settingsDataStore.edit { it[progressiveBlurRendererPreferencesKey] = renderer.value }
    }

    fun observeDockMotionStyle(context: Context): Flow<GlassMotionStyle> =
        context.settingsDataStore.data.map {
            GlassMotionStyle.fromValue(it[glassDockMotionStylePreferencesKey] ?: GlassMotionStyle.STANDARD.value)
        }.distinctUntilChanged()

    suspend fun setDockMotionStyle(context: Context, style: GlassMotionStyle) {
        context.settingsDataStore.edit { it[glassDockMotionStylePreferencesKey] = style.value }
    }

    fun observePopupMotionStyle(context: Context): Flow<GlassMotionStyle> =
        context.settingsDataStore.data.map {
            GlassMotionStyle.fromValue(it[glassPopupMotionStylePreferencesKey] ?: GlassMotionStyle.STANDARD.value)
        }.distinctUntilChanged()

    suspend fun setPopupMotionStyle(context: Context, style: GlassMotionStyle) {
        context.settingsDataStore.edit { it[glassPopupMotionStylePreferencesKey] = style.value }
    }

    fun observePopupPalette(context: Context): Flow<GlassPopupPalette> =
        context.settingsDataStore.data.map {
            GlassPopupPalette.fromValue(it[glassPopupPalettePreferencesKey] ?: GlassPopupPalette.DOCK.value)
        }.distinctUntilChanged()

    suspend fun setPopupPalette(context: Context, palette: GlassPopupPalette) {
        context.settingsDataStore.edit { it[glassPopupPalettePreferencesKey] = palette.value }
    }

    fun observeReadabilityMode(context: Context): Flow<LiquidGlassReadabilityMode> =
        context.settingsDataStore.data
            .map { preferences ->
                LiquidGlassReadabilityMode.fromValue(
                    preferences[liquidGlassReadabilityModePreferencesKey]
                        ?: LiquidGlassReadabilityMode.STABLE.value
                )
            }
            .distinctUntilChanged()

    suspend fun setReadabilityMode(
        context: Context,
        mode: LiquidGlassReadabilityMode,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[liquidGlassReadabilityModePreferencesKey] = mode.value
        }
    }
}
