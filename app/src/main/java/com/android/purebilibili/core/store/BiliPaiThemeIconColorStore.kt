package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val customIconColorKey = intPreferencesKey("bilipai_theme_icon_custom_color")
private val customIconBackgroundKey = intPreferencesKey("bilipai_theme_icon_background")
private const val CUSTOM_COLOR_CACHE = "theme_icon_custom_color"
private const val BACKGROUND_CACHE = "theme_icon_background"

/** null 前景跟随应用主色，null 背景自动匹配明暗。 */
data class BiliPaiIconColorSettings(val foreground: Int? = null, val background: Int? = null)

fun getBiliPaiIconColorSettings(context: Context): Flow<BiliPaiIconColorSettings> =
    context.settingsDataStore.data.map {
        BiliPaiIconColorSettings(it[customIconColorKey], it[customIconBackgroundKey])
    }

fun getBiliPaiIconColorSettingsSync(context: Context): BiliPaiIconColorSettings {
    val values = context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE).all
    return BiliPaiIconColorSettings(values[CUSTOM_COLOR_CACHE] as Int?, values[BACKGROUND_CACHE] as Int?)
}

suspend fun setBiliPaiIconColorSettings(context: Context, colors: BiliPaiIconColorSettings) {
    context.settingsDataStore.edit { preferences ->
        if (colors.foreground == null) preferences.remove(customIconColorKey) else preferences[customIconColorKey] = colors.foreground
        if (colors.background == null) preferences.remove(customIconBackgroundKey) else preferences[customIconBackgroundKey] = colors.background
    }
    withContext(Dispatchers.IO) {
        val editor = context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE).edit()
        if (colors.foreground == null) editor.remove(CUSTOM_COLOR_CACHE) else editor.putInt(CUSTOM_COLOR_CACHE, colors.foreground)
        if (colors.background == null) editor.remove(BACKGROUND_CACHE) else editor.putInt(BACKGROUND_CACHE, colors.background)
        check(editor.commit())
    }
}
