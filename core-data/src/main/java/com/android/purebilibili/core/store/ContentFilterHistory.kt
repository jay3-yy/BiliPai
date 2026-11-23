package com.android.purebilibili.core.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ContentFilterReason {
    BLOCKED_USER, KEYWORD, TITLE_PATTERN, AD_TITLE, GOODS, GOODS_DYNAMIC, AI_BOT, REPEATED_REPLY,
    DURATION, PLAY_COUNT, LIKE_RATIO, CATEGORY, SPONSORED, CLICKBAIT, TAG, PLUGIN,
    DYNAMIC_FORWARD, DYNAMIC_LOTTERY, DYNAMIC_RESERVE, DYNAMIC_ARTICLE, DYNAMIC_LIVE,
}

enum class FilteredContentKind { COMMENT, VIDEO, DYNAMIC }

data class ContentFilterHit(
    val id: String,
    val text: String,
    val author: String,
    val reason: ContentFilterReason,
    val pluginName: String = "",
    val authorMid: Long = 0L,
    val matchedRule: String = "",
)

const val CONTENT_FILTER_HISTORY_LIMIT = 100

/** 只保留当前登录会话最近命中的内容，不写入存储。 */
object ContentFilterHistory {
    private val comments = LinkedHashMap<String, ContentFilterHit>()
    private val videos = LinkedHashMap<String, ContentFilterHit>()
    private val dynamics = LinkedHashMap<String, ContentFilterHit>()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private var account = TokenManager.accountIdentity.value

    @Synchronized
    fun record(
        kind: FilteredContentKind,
        hit: ContentFilterHit,
        session: AccountSessionIdentity = TokenManager.accountIdentity.value,
    ) {
        if (session != TokenManager.accountIdentity.value) return
        if (account != session) {
            account = session
            comments.clear()
            videos.clear()
            dynamics.clear()
        }
        val entries = entries(kind)
        if (entries[hit.id] == hit) return
        entries.remove(hit.id)
        entries[hit.id] = hit
        if (entries.size > CONTENT_FILTER_HISTORY_LIMIT) entries.remove(entries.keys.first())
        mutableRevision.value++
    }

    @Synchronized
    fun snapshot(kind: FilteredContentKind): List<ContentFilterHit> =
        if (account == TokenManager.accountIdentity.value) entries(kind).values.toList().asReversed()
        else emptyList()

    @Synchronized
    fun remove(kind: FilteredContentKind, id: String) {
        if (entries(kind).remove(id) != null) {
            mutableRevision.value++
        }
    }

    @Synchronized
    fun clear(kind: FilteredContentKind) {
        val entries = entries(kind)
        if (entries.isNotEmpty()) {
            entries.clear()
            mutableRevision.value++
        }
    }

    private fun entries(kind: FilteredContentKind) = when (kind) {
        FilteredContentKind.COMMENT -> comments
        FilteredContentKind.VIDEO -> videos
        FilteredContentKind.DYNAMIC -> dynamics
    }
}
