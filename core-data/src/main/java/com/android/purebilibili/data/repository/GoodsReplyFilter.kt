package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import kotlinx.serialization.json.contentOrNull
import com.android.purebilibili.core.store.ContentFilterRules
import com.android.purebilibili.core.store.ContentFilterScope
import com.android.purebilibili.core.store.ContentFilterSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun isGoodsReply(reply: ReplyItem): Boolean =
    reply.content.message.contains("https://gaoneng.bilibili.com/tetris") ||
        reply.content.urls.values.any { url ->
            url.isGoods || url.extra?.let { extra ->
                extra["goods_cm_control"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull == "1" ||
                    extra.containsKey("goods_item_id") || extra.containsKey("goods_prefetched_cache")
            } == true
        }

/** 只过滤展示内容，保留服务端总数、页码、来源和继续加载游标。 */
internal fun filterGoodsReplies(data: ReplyData): ReplyData =
    filterCommentReplies(data, emptyList(), blockGoods = true)

fun filterReplyForContent(reply: ReplyItem, rules: ContentFilterSnapshot): ReplyItem? {
    if (rules.blocksUser(reply.member.mid.toLongOrNull() ?: 0L) ||
        rules.blocksText(reply.content.message, ContentFilterScope.COMMENT) ||
        (rules.blockGoodsReplies && isGoodsReply(reply))) return null
    val children = reply.replies ?: return reply
    val filtered = children.mapNotNull { filterReplyForContent(it, rules) }
    return if (filtered == children) reply else reply.copy(replies = filtered)
}

internal fun filterCommentReplies(
    data: ReplyData,
    keywords: List<String>,
    blockGoods: Boolean = false,
    blockedMids: Set<Long> = emptySet(),
): ReplyData {
    if (keywords.isEmpty() && !blockGoods && blockedMids.isEmpty()) return data
    fun clean(reply: ReplyItem?): ReplyItem? = reply?.takeUnless { item ->
        item.member.mid.toLongOrNull() in blockedMids || (blockGoods && isGoodsReply(item)) || keywords.any {
            item.content.message.contains(it, ignoreCase = true)
        }
    }?.let {
        it.copy(replies = it.replies?.mapNotNull { child -> clean(child) })
    }
    return data.copy(
        replies = data.replies?.mapNotNull(::clean),
        hots = data.hots?.mapNotNull(::clean),
        topReplies = data.topReplies?.mapNotNull(::clean),
        root = clean(data.root),
        top = data.top?.let { it.copy(admin = clean(it.admin), upper = clean(it.upper), vote = clean(it.vote)) },
        upper = data.upper?.let { it.copy(top = clean(it.top)) },
    )
}

internal suspend fun filterCommentRepliesIfEnabled(data: ReplyData): ReplyData {
    val context = NetworkModule.appContext ?: return data
    val rules = ContentFilterRules.snapshot(context)
    return withContext(Dispatchers.Default) {
        filterCommentReplies(
            data,
            rules.keywords.takeIf {
                rules.keywordFilteringEnabled && ContentFilterScope.COMMENT in rules.scopes
            }.orEmpty(),
            blockGoods = rules.blockGoodsReplies,
            blockedMids = rules.blockedMids,
        )
    }
}
