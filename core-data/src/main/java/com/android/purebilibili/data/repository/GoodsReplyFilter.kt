package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import kotlinx.serialization.json.contentOrNull
import com.android.purebilibili.core.store.ContentFilterRules
import com.android.purebilibili.core.store.ContentFilterScope
import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.store.ContentFilterHistory
import com.android.purebilibili.core.store.ContentFilterHit
import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.core.store.FilteredContentKind
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

class ReplyContentFilter(
    private val rules: ContentFilterSnapshot,
    evidence: Sequence<ReplyItem>,
    upMid: Long = 0L,
    currentMid: Long = TokenManager.accountIdentity.value.mid ?: 0L,
    private val foldDetectedReplies: Boolean = false,
    detector: AiReplyDetector = AiReplyDetector(),
    repeatedDetector: RepeatedReplyDetector = RepeatedReplyDetector(),
    private val recordHits: Boolean = false,
) {
    private val historySession = TokenManager.accountIdentity.value
    private val aiFilterEnabled = rules.hideAiReplies && rules.keywordFilteringEnabled
    private val exemptAiAuthors = if (aiFilterEnabled) rules.aiReplyAllowedMids + upMid + currentMid else emptySet()
    private val aiAuthors = if (aiFilterEnabled) {
        detector.detectAuthors(evidence, exemptAiAuthors)
    } else {
        detector.clear()
        emptySet()
    }

    private val repeatedFilterEnabled = rules.filterRepeatedReplies && rules.keywordFilteringEnabled
    private val exemptRepeatedAuthors = setOf(upMid, currentMid)
    private val repeatedReplies = if (repeatedFilterEnabled) {
        repeatedDetector.detect(evidence, exemptRepeatedAuthors)
    } else {
        repeatedDetector.clear()
        emptySet()
    }

    fun filter(replies: List<ReplyItem>): List<ReplyItem> {
        val filtered = replies.mapNotNull { filter(it) }
        return if (filtered == replies) replies else filtered
    }

    fun filter(
        reply: ReplyItem,
        keepThreadContext: Boolean = false,
        isThreadRoot: Boolean = false,
    ): ReplyItem? {
        val hardReason = when {
            rules.blocksUser(reply.member.mid.toLongOrNull() ?: 0L) -> ContentFilterReason.BLOCKED_USER
            rules.blocksText(reply.content.message, ContentFilterScope.COMMENT) -> ContentFilterReason.KEYWORD
            rules.blockGoodsReplies && !isThreadRoot && isGoodsReply(reply) -> ContentFilterReason.GOODS
            else -> null
        }
        if (hardReason != null) {
            record(reply, hardReason)
            return null
        }

        val mid = reply.authorMid()
        val isAiBot = aiFilterEnabled && (mid in aiAuthors || (reply.aiBotFolded && mid !in exemptAiAuthors))
        val isRepeated = repeatedFilterEnabled && (
            reply.rpid in repeatedReplies || (reply.repeatedReplyFolded && mid !in exemptRepeatedAuthors)
        )
        if ((isAiBot || isRepeated) && (foldDetectedReplies || !keepThreadContext)) {
            record(reply, if (isAiBot) ContentFilterReason.AI_BOT else ContentFilterReason.REPEATED_REPLY)
            if (!foldDetectedReplies) return null
        }

        val aiFolded = foldDetectedReplies && isAiBot
        val repeatedFolded = foldDetectedReplies && isRepeated
        val children = reply.replies?.let(::filter)
        return if (aiFolded == reply.aiBotFolded && repeatedFolded == reply.repeatedReplyFolded &&
            children === reply.replies
        ) reply else reply.copy(replies = children, aiBotFolded = aiFolded, repeatedReplyFolded = repeatedFolded)
    }

    private fun record(reply: ReplyItem, reason: ContentFilterReason) {
        if (!recordHits || reply.rpid <= 0L) return
        ContentFilterHistory.record(
            FilteredContentKind.COMMENT,
            ContentFilterHit(
                id = reply.rpid.toString(),
                text = reply.content.message,
                author = reply.member.uname,
                reason = reason,
            ),
            session = historySession,
        )
    }
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
        val active = rules.blockGoodsReplies || rules.blockedMids.isNotEmpty() ||
            (rules.keywordFilteringEnabled && (rules.hideAiReplies || rules.filterRepeatedReplies ||
                (ContentFilterScope.COMMENT in rules.scopes && rules.keywords.isNotEmpty())))
        if (!active) return@withContext data
        val evidence = sequenceOf(data.root).filterNotNull() +
            data.replies.orEmpty().asSequence() + data.hots.orEmpty().asSequence() +
            data.collectTopReplies().asSequence()
        val filter = ReplyContentFilter(rules, evidence, upMid = data.upper?.mid ?: 0L, recordHits = true)
        data.copy(
            replies = data.replies?.let(filter::filter),
            hots = data.hots?.let(filter::filter),
            topReplies = data.topReplies?.let(filter::filter),
            root = data.root?.let { filter.filter(it, keepThreadContext = true, isThreadRoot = true) },
            top = data.top?.let { top ->
                top.copy(
                    admin = top.admin?.let { filter.filter(it) },
                    upper = top.upper?.let { filter.filter(it) },
                    vote = top.vote?.let { filter.filter(it) },
                )
            },
            upper = data.upper?.let { upper -> upper.copy(top = upper.top?.let { filter.filter(it) }) },
        )
    }
}
