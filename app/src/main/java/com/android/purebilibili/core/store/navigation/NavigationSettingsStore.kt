package com.android.purebilibili.core.store.navigation

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.AppNavigationSettings
import com.android.purebilibili.core.store.mapAppNavigationSettingsFromPreferences
import com.android.purebilibili.core.store.resolveListenVideoBottomTabMigration
import com.android.purebilibili.core.store.settingsDataStore
import com.android.purebilibili.core.util.isLargeScreenOrFoldableConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map


internal val bottomBarItemLabelsPreferencesKey = stringPreferencesKey("bottom_bar_item_labels")
internal val liquidIndicatorWobblePreferencesKey = booleanPreferencesKey("liquid_indicator_wobble_enabled")
internal val liquidIndicatorDynamicLengthPreferencesKey = booleanPreferencesKey("liquid_indicator_dynamic_length_enabled")
internal val miuixPredictiveBackMaxProgressPercentPreferencesKey =
    intPreferencesKey("miuix_predictive_back_max_progress_percent")

private fun normalizeBottomBarLabelItemId(rawId: String): String {
    val id = rawId.trim()
    if (id.isBlank()) return ""
    return when (id.lowercase()) {
        "home" -> "HOME"
        "dynamic" -> "DYNAMIC"
        "story", "shortvideo", "short_video" -> "STORY"
        "history" -> "HISTORY"
        "listen_video" -> "LISTEN_VIDEO"
        "profile", "mine", "my" -> "PROFILE"
        "favorite", "favourite" -> "FAVORITE"
        "live" -> "LIVE"
        "watchlater", "watch_later" -> "WATCHLATER"
        "settings" -> "SETTINGS"
        "plugins", "plugin", "plugin_center" -> "PLUGINS"
        else -> id.uppercase()
    }
}

internal fun normalizeBottomBarCustomLabel(rawLabel: String): String = rawLabel
    .trim()
    .replace(Regex("\\s+"), " ")
    .take(12)

internal fun parseBottomBarItemLabels(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.split(',').mapNotNull { entry ->
        val separator = entry.indexOf('=')
        if (separator <= 0) return@mapNotNull null
        val itemId = normalizeBottomBarLabelItemId(entry.substring(0, separator))
        if (itemId.isBlank()) return@mapNotNull null
        val decoded = runCatching {
            java.net.URLDecoder.decode(
                entry.substring(separator + 1),
                java.nio.charset.StandardCharsets.UTF_8.name()
            )
        }.getOrNull() ?: return@mapNotNull null
        normalizeBottomBarCustomLabel(decoded)
            .takeIf(String::isNotBlank)
            ?.let { itemId to it }
    }.toMap()
}

object NavigationSettingsStore {
    private val keyTabletUseSidebar = booleanPreferencesKey("tablet_use_sidebar")
    private val keySidebarExpanded = booleanPreferencesKey("sidebar_expanded")
    private val keySidebarAccountSwitcherEnabled =
        booleanPreferencesKey("sidebar_account_switcher_enabled")
    private val keyPredictiveBackEnabled = booleanPreferencesKey("predictive_back_enabled")
    private val keyPredictiveBackAnimationStyle = stringPreferencesKey("predictive_back_animation_style")
    private val keyPredictiveBackExitDirection = stringPreferencesKey("predictive_back_exit_direction")
    private val keyMiuixTransitionBlurEnabled =
        booleanPreferencesKey("miuix_transition_blur_enabled")
    private val keySettingsContainerRealtimeBlurEnabled = booleanPreferencesKey("settings_container_realtime_blur_enabled")
    private val keySettingsContainerMorphEnabled =
        booleanPreferencesKey("settings_container_morph_enabled")
    private val keyFullScreenSwipeBackEnabled =
        booleanPreferencesKey("full_screen_swipe_back_enabled")
    private val keyBottomBarOrder = stringPreferencesKey("bottom_bar_order")
    private val keyBottomBarVisibleTabs = stringPreferencesKey("bottom_bar_visible_tabs")
    private val keyListenVideoMigrationComplete = booleanPreferencesKey("listen_video_bottom_tab_migration_complete")

    internal fun mapFromPreferences(
        preferences: Preferences,
        defaultTabletUseSidebar: Boolean = false
    ): AppNavigationSettings {
        return mapAppNavigationSettingsFromPreferences(
            preferences = preferences,
            defaultTabletUseSidebar = defaultTabletUseSidebar
        )
    }

    fun observe(context: Context): Flow<AppNavigationSettings> {
        val defaultTabletUseSidebar =
            context.isLargeScreenOrFoldableConfiguration()
        return context.settingsDataStore.data
            .map { preferences ->
                mapFromPreferences(
                    preferences = preferences,
                    defaultTabletUseSidebar = defaultTabletUseSidebar
                )
            }
            .distinctUntilChanged()
    }

    fun observeBottomBarItemLabels(context: Context): Flow<Map<String, String>> =
        context.settingsDataStore.data
            .map { preferences ->
                parseBottomBarItemLabels(preferences[bottomBarItemLabelsPreferencesKey].orEmpty())
            }
            .distinctUntilChanged()

    suspend fun setBottomBarItemLabel(context: Context, itemId: String, label: String) {
        context.settingsDataStore.edit { preferences ->
            val labels = parseBottomBarItemLabels(
                preferences[bottomBarItemLabelsPreferencesKey].orEmpty()
            ).toMutableMap()
            val normalizedItemId = normalizeBottomBarLabelItemId(itemId)
            if (normalizedItemId.isBlank()) return@edit
            val normalizedLabel = normalizeBottomBarCustomLabel(label)
            if (normalizedLabel.isBlank()) {
                labels.remove(normalizedItemId)
            } else {
                labels[normalizedItemId] = normalizedLabel
            }
            preferences[bottomBarItemLabelsPreferencesKey] = labels.entries
                .joinToString(",") { (id, value) ->
                    val encoded = java.net.URLEncoder.encode(
                        value,
                        java.nio.charset.StandardCharsets.UTF_8.name()
                    )
                    "$id=$encoded"
                }
        }
    }

    suspend fun clearBottomBarItemLabels(context: Context) {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(bottomBarItemLabelsPreferencesKey)
        }
    }

    suspend fun ensureListenVideoBottomTabMigration(context: Context) {
        context.settingsDataStore.edit { preferences ->
            val order = (preferences[keyBottomBarOrder]
                ?: "HOME,DYNAMIC,HISTORY,LISTEN_VIDEO,PROFILE")
                .split(',')
                .filter(String::isNotBlank)
            val visible = (preferences[keyBottomBarVisibleTabs]
                ?: "HOME,DYNAMIC,HISTORY,LISTEN_VIDEO,PROFILE")
                .split(',')
                .filter(String::isNotBlank)
                .toSet()
            val migration = resolveListenVideoBottomTabMigration(
                order = order,
                visible = visible,
                migrationComplete = preferences[keyListenVideoMigrationComplete] ?: false
            )
            if (migration.order != order) {
                preferences[keyBottomBarOrder] = migration.order.joinToString(",")
            }
            if (migration.visible != visible) {
                preferences[keyBottomBarVisibleTabs] = migration.visible.joinToString(",")
            }
            if (migration.markComplete) {
                preferences[keyListenVideoMigrationComplete] = true
            }
        }
    }

    suspend fun setTabletUseSidebar(context: Context, useSidebar: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyTabletUseSidebar] = useSidebar
        }
    }

    suspend fun setSidebarExpanded(context: Context, expanded: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keySidebarExpanded] = expanded
        }
    }

    suspend fun setSidebarAccountSwitcherEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keySidebarAccountSwitcherEnabled] = enabled
        }
    }

    suspend fun setPredictiveBackEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyPredictiveBackEnabled] = enabled
        }
    }

    suspend fun setLiquidIndicatorDynamicLengthEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[liquidIndicatorDynamicLengthPreferencesKey] = enabled }
    }

    suspend fun setLiquidIndicatorWobbleEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[liquidIndicatorWobblePreferencesKey] = enabled }
    }

    suspend fun setPredictiveBackAnimationStyle(context: Context, style: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyPredictiveBackAnimationStyle] = style
        }
    }

    suspend fun setPredictiveBackExitDirection(context: Context, direction: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyPredictiveBackExitDirection] = direction
        }
    }

    suspend fun setMiuixTransitionBlurEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyMiuixTransitionBlurEnabled] = enabled
        }
    }

    suspend fun setMiuixPredictiveBackMaxProgressPercent(context: Context, percent: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[miuixPredictiveBackMaxProgressPercentPreferencesKey] =
                percent.coerceIn(0, 100)
        }
    }

    /** 设置容器形变开关：开启时设置页入口以条目容器形变展开，关闭走默认导航转场。 */
    fun getSettingsContainerRealtimeBlurEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[keySettingsContainerRealtimeBlurEnabled] ?: false }
            .distinctUntilChanged()

    suspend fun setSettingsContainerRealtimeBlurEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[keySettingsContainerRealtimeBlurEnabled] = enabled }
    }

    fun getSettingsContainerMorphEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[keySettingsContainerMorphEnabled] ?: true }

    suspend fun setSettingsContainerMorphEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keySettingsContainerMorphEnabled] = enabled
        }
    }

    /** Miuix 全屏滑动返回（默认关闭，仅保留系统边缘预测返回）。 */
    fun getFullScreenSwipeBackEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[keyFullScreenSwipeBackEnabled] ?: false }

    suspend fun setFullScreenSwipeBackEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyFullScreenSwipeBackEnabled] = enabled
        }
    }
}
