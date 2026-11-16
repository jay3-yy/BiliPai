package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal enum class AboutBackgroundMode(val label: String) {
    DEFAULT("默认渐变"),
    THEME("跟随主题"),
    CUSTOM("自定义颜色"),
    NONE("纯色背景"),
}

internal data class AboutBackgroundSettings(
    val mode: AboutBackgroundMode = AboutBackgroundMode.DEFAULT,
    val topColor: Int = 0xFFC5B8F5.toInt(),
    val bottomColor: Int = 0xFFF6D3E6.toInt(),
    val glowColor: Int = 0xFFD9E5FC.toInt(),
)

internal object AboutAppearanceStore {
    val modeKey = stringPreferencesKey("about_miuix_background_mode")
    val topColorKey = intPreferencesKey("about_miuix_background_top")
    val bottomColorKey = intPreferencesKey("about_miuix_background_bottom")
    val glowColorKey = intPreferencesKey("about_miuix_background_glow")

    fun observe(context: Context) = context.settingsDataStore.data.map { preferences ->
        val defaults = AboutBackgroundSettings()
        AboutBackgroundSettings(
            mode = AboutBackgroundMode.entries.firstOrNull { it.name == preferences[modeKey] }
                ?: defaults.mode,
            topColor = preferences[topColorKey] ?: defaults.topColor,
            bottomColor = preferences[bottomColorKey] ?: defaults.bottomColor,
            glowColor = preferences[glowColorKey] ?: defaults.glowColor,
        )
    }.distinctUntilChanged()

    suspend fun save(context: Context, settings: AboutBackgroundSettings) {
        context.settingsDataStore.edit {
            it[modeKey] = settings.mode.name
            it[topColorKey] = settings.topColor
            it[bottomColorKey] = settings.bottomColor
            it[glowColorKey] = settings.glowColor
        }
    }
}
