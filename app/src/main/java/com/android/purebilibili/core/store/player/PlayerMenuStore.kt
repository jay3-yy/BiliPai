package com.android.purebilibili.core.store.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.android.purebilibili.core.store.settingsDataStore
import kotlinx.coroutines.flow.map

internal data class PlayerMenuConfiguration(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val topShortcutId: String? = null,
    val bottomControlPlacements: Map<String, PlayerBottomControlPlacement> = emptyMap(),
)

internal enum class PlayerBottomControlPlacement(val storageValue: String) {
    BOTTOM_BAR("bottom"),
    MORE_MENU("more"),
}

internal enum class PlayerMenuStore(prefix: String) {
    FULLSCREEN("fullscreen_more_menu"),
    PORTRAIT("portrait_more_menu"),
    LISTEN_VIDEO("listen_video_more_menu");

    private val orderKey = stringPreferencesKey("${prefix}_order")
    private val hiddenKey = stringSetPreferencesKey("${prefix}_hidden")
    private val topShortcutKey = stringPreferencesKey("${prefix}_top_shortcut")
    private val bottomControlPlacementsKey = stringSetPreferencesKey("${prefix}_bottom_control_placements")

    fun configuration(context: Context) = context.settingsDataStore.data.map { preferences ->
        PlayerMenuConfiguration(
            order = preferences[orderKey]?.split(',').orEmpty(),
            hidden = preferences[hiddenKey].orEmpty(),
            topShortcutId = preferences[topShortcutKey],
            bottomControlPlacements = preferences[bottomControlPlacementsKey].orEmpty().mapNotNull { value ->
                val id = value.substringBefore(':')
                val placement = value.substringAfter(':', "")
                PlayerBottomControlPlacement.entries.firstOrNull { it.storageValue == placement }?.let { id to it }
            }.toMap(),
        )
    }

    suspend fun save(context: Context, configuration: PlayerMenuConfiguration) {
        context.settingsDataStore.edit { preferences ->
            preferences[orderKey] = configuration.order.joinToString(",")
            preferences[hiddenKey] = configuration.hidden
            if (configuration.topShortcutId != null) {
                preferences[topShortcutKey] = configuration.topShortcutId
            } else {
                preferences.remove(topShortcutKey)
            }
            if (configuration.bottomControlPlacements.isEmpty()) {
                preferences.remove(bottomControlPlacementsKey)
            } else {
                preferences[bottomControlPlacementsKey] = configuration.bottomControlPlacements.mapTo(mutableSetOf()) {
                    (id, placement) -> "$id:${placement.storageValue}"
                }
            }
        }
    }
}
