package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class FullscreenActionCount(val title: String, internal val preferenceName: String) {
    LIKE("点赞数", "show_fullscreen_like_count"),
    COIN("投币数", "show_fullscreen_coin_count"),
    FAVORITE("收藏数", "show_fullscreen_favorite_count"),
    SHARE("分享数", "show_fullscreen_share_count"),
    COMMENT("评论数", "show_fullscreen_comment_count"),
}

object FullscreenActionCountSettings {
    const val TITLE = "全屏互动数量"

    fun getVisibleCounts(context: Context): Flow<Set<FullscreenActionCount>> =
        context.settingsDataStore.data.map { preferences ->
            FullscreenActionCount.entries.filterTo(mutableSetOf()) {
                preferences[booleanPreferencesKey(it.preferenceName)] ?: true
            }
        }

    suspend fun setVisible(context: Context, count: FullscreenActionCount, visible: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(count.preferenceName)] = visible }
    }
}
