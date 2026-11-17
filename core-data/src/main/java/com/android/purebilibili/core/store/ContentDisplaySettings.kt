package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 内容提示与商品过滤独立保存，手机和共享数据仓库读取同一份偏好。 */
object ContentDisplaySettings {
    const val SHOW_MEMBER_SHOP = "content.show_member_shop"
    const val DYNAMIC_WARNING = "content.dynamic_warning"
    const val BLOCK_GOODS_DYNAMIC = "content.block_goods_dynamic"
    const val BLOCK_GOODS_REPLY = "content.block_goods_reply"
    const val COMMENT_BLOCKED_KEYWORDS = "content.comment_blocked_keywords"
    fun title(id: String): String = when (id) {
        SHOW_MEMBER_SHOP -> "显示 UP 主页小店标签"
        DYNAMIC_WARNING -> "显示动态警告/争议信息"
        BLOCK_GOODS_DYNAMIC -> "屏蔽带货动态"
        BLOCK_GOODS_REPLY -> "屏蔽带货评论"
        COMMENT_BLOCKED_KEYWORDS -> "评论屏蔽词"
        else -> error("Unknown content setting: $id")
    }
    fun defaultEnabled(id: String): Boolean = id == SHOW_MEMBER_SHOP

    private fun store(context: Context) = MultiProcessPreferences.get(context, "content_display_prefs")
    fun observe(context: Context, id: String) = store(context).data
        .map { it[booleanPreferencesKey(id)] ?: defaultEnabled(id) }.distinctUntilChanged()
    suspend fun enabled(context: Context, id: String): Boolean = observe(context, id).first()
    suspend fun set(context: Context, id: String, enabled: Boolean) {
        store(context).edit { it[booleanPreferencesKey(id)] = enabled }
    }

    fun observeCommentKeywords(context: Context) = ContentFilterRules.observeSettings(context)
        .map { it.keywords }.distinctUntilChanged()

    suspend fun setCommentKeywords(context: Context, text: String) {
        ContentFilterRules.save(context, text, ContentFilterRules.observeSettings(context).first().scopes)
    }
}

internal fun parseCommentKeywords(text: String): List<String> =
    text.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
