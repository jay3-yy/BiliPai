package com.android.purebilibili.core.store.home

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.android.purebilibili.core.store.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val dynamicCommentPreviewVisiblePreferencesKey =
    booleanPreferencesKey("dynamic_comment_preview_visible")

internal val dynamicUpListVisiblePreferencesKey = booleanPreferencesKey("dynamic_up_list_visible")

data class DynamicFeedTypeFilterPreferences(
    val hideForward: Boolean = false,
    val hideLottery: Boolean = false,
    val hideGoods: Boolean = false,
    val hideReserve: Boolean = false,
    val hideArticle: Boolean = false,
    val hideLiveRcmd: Boolean = false,
)

object DynamicSettingsStore {

    private val sidebarLiveUsersVisibleKey = booleanPreferencesKey("dynamic_sidebar_live_users_visible")
    private val filterHideForwardKey = booleanPreferencesKey("dynamic_filter_hide_forward")
    private val filterHideLotteryKey = booleanPreferencesKey("dynamic_filter_hide_lottery")
    private val filterHideGoodsKey = booleanPreferencesKey("dynamic_filter_hide_goods")
    private val filterHideReserveKey = booleanPreferencesKey("dynamic_filter_hide_reserve")
    private val filterHideArticleKey = booleanPreferencesKey("dynamic_filter_hide_article")
    private val filterHideLiveRcmdKey = booleanPreferencesKey("dynamic_filter_hide_live_rcmd")

    fun observeSidebarLiveUsersVisible(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { it[sidebarLiveUsersVisibleKey] ?: true }
            .distinctUntilChanged()

    suspend fun toggleSidebarLiveUsersVisible(context: Context) {
        context.settingsDataStore.edit { preferences ->
            preferences[sidebarLiveUsersVisibleKey] = !(preferences[sidebarLiveUsersVisibleKey] ?: true)
        }
    }

    fun observeUpListVisible(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { it[dynamicUpListVisiblePreferencesKey] ?: true }.distinctUntilChanged()

    suspend fun setUpListVisible(context: Context, visible: Boolean) {
        context.settingsDataStore.edit { it[dynamicUpListVisiblePreferencesKey] = visible }
    }

    internal fun resolveCommentPreviewVisible(preferences: Preferences): Boolean =
        preferences[dynamicCommentPreviewVisiblePreferencesKey] ?: false

    fun observeCommentPreviewVisible(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map(::resolveCommentPreviewVisible)
            .distinctUntilChanged()

    suspend fun setCommentPreviewVisible(context: Context, visible: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[dynamicCommentPreviewVisiblePreferencesKey] = visible
        }
    }

    fun observeFeedTypeFilters(context: Context): Flow<DynamicFeedTypeFilterPreferences> =
        context.settingsDataStore.data
            .map { prefs ->
                DynamicFeedTypeFilterPreferences(
                    hideForward = prefs[filterHideForwardKey] ?: false,
                    hideLottery = prefs[filterHideLotteryKey] ?: false,
                    hideGoods = prefs[filterHideGoodsKey] ?: false,
                    hideReserve = prefs[filterHideReserveKey] ?: false,
                    hideArticle = prefs[filterHideArticleKey] ?: false,
                    hideLiveRcmd = prefs[filterHideLiveRcmdKey] ?: false,
                )
            }
            .distinctUntilChanged()

    suspend fun setFeedTypeFilters(context: Context, filters: DynamicFeedTypeFilterPreferences) {
        context.settingsDataStore.edit { prefs ->
            prefs[filterHideForwardKey] = filters.hideForward
            prefs[filterHideLotteryKey] = filters.hideLottery
            prefs[filterHideGoodsKey] = filters.hideGoods
            prefs[filterHideReserveKey] = filters.hideReserve
            prefs[filterHideArticleKey] = filters.hideArticle
            prefs[filterHideLiveRcmdKey] = filters.hideLiveRcmd
        }
    }
}
