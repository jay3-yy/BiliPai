package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class FullscreenTopBarItemGroup(val title: String) {
    PRIMARY("顶部功能按钮"),
    ENGAGEMENT("互动与侧栏按钮"),
}

enum class FullscreenTopBarItem(
    val title: String,
    val group: FullscreenTopBarItemGroup,
    internal val preferenceName: String,
) {
    HOME("返回首页", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_home"),
    STATUS("电量与时间", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_status"),
    AUDIO("听音频", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_audio"),
    CAST("投屏", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_cast"),
    NEARBY_HANDOFF("设备接力", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_nearby_handoff"),
    SPONSOR("空降助手", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_sponsor"),
    SEND_DANMAKU("发弹幕", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_send_danmaku"),
    DANMAKU_TOGGLE("弹幕开关", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_danmaku_toggle"),
    DANMAKU_SETTINGS("弹幕设置", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_danmaku_settings"),
    PIP("画中画", FullscreenTopBarItemGroup.PRIMARY, "show_fs_top_pip"),
    COMMENT("评论", FullscreenTopBarItemGroup.ENGAGEMENT, "show_fs_top_comment"),
    RELATED("相关推荐", FullscreenTopBarItemGroup.ENGAGEMENT, "show_fs_top_related"),
    AI_SUMMARY("AI 总结", FullscreenTopBarItemGroup.ENGAGEMENT, "show_fs_top_ai_summary"),
    LIKE("点赞", FullscreenTopBarItemGroup.ENGAGEMENT, "show_fs_top_like"),
    DISLIKE("点踩", FullscreenTopBarItemGroup.ENGAGEMENT, "show_fs_top_dislike"),
    COIN("投币", FullscreenTopBarItemGroup.ENGAGEMENT, "show_fs_top_coin"),
    FAVORITE("收藏", FullscreenTopBarItemGroup.ENGAGEMENT, "show_fs_top_favorite"),
    SHARE("分享", FullscreenTopBarItemGroup.ENGAGEMENT, "show_fs_top_share"),
}

object FullscreenTopBarSettings {
    const val TITLE = "横屏顶部按钮"

    val defaultVisibleItems: Set<FullscreenTopBarItem> = FullscreenTopBarItem.entries.toSet()

    fun getVisibleItems(context: Context): Flow<Set<FullscreenTopBarItem>> =
        context.settingsDataStore.data.map { preferences ->
            FullscreenTopBarItem.entries.filterTo(mutableSetOf()) {
                preferences[booleanPreferencesKey(it.preferenceName)] ?: true
            }
        }.distinctUntilChanged()

    suspend fun setVisible(context: Context, item: FullscreenTopBarItem, visible: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(item.preferenceName)] = visible }
    }

    suspend fun resetDefaults(context: Context) {
        context.settingsDataStore.edit { preferences ->
            FullscreenTopBarItem.entries.forEach {
                preferences.remove(booleanPreferencesKey(it.preferenceName))
            }
        }
    }
}
