package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class AboutCardShapeSettings(
    val topStart: Int = 48,
    val topEnd: Int = 20,
    val bottomStart: Int = 20,
    val bottomEnd: Int = 48,
)

internal object AboutCardShapeStore {
    private val topStartKey = intPreferencesKey("about_md3_card_top_start")
    private val topEndKey = intPreferencesKey("about_md3_card_top_end")
    private val bottomStartKey = intPreferencesKey("about_md3_card_bottom_start")
    private val bottomEndKey = intPreferencesKey("about_md3_card_bottom_end")

    fun observe(context: Context) = context.settingsDataStore.data.map { preferences ->
        val defaults = AboutCardShapeSettings()
        AboutCardShapeSettings(
            topStart = preferences[topStartKey] ?: defaults.topStart,
            topEnd = preferences[topEndKey] ?: defaults.topEnd,
            bottomStart = preferences[bottomStartKey] ?: defaults.bottomStart,
            bottomEnd = preferences[bottomEndKey] ?: defaults.bottomEnd,
        )
    }.distinctUntilChanged()

    suspend fun save(context: Context, settings: AboutCardShapeSettings) {
        context.settingsDataStore.edit {
            it[topStartKey] = settings.topStart
            it[topEndKey] = settings.topEnd
            it[bottomStartKey] = settings.bottomStart
            it[bottomEndKey] = settings.bottomEnd
        }
    }
}
