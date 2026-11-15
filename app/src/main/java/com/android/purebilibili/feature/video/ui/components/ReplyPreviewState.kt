package com.android.purebilibili.feature.video.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.core.ui.components.LocalListAuxiliaryWorkAllowed
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.loadCommentReplyPreview
import kotlinx.coroutines.flow.Flow

internal data class ReplyPreviewState(
    val account: AccountSessionIdentity,
    val collapsedLimit: Int,
    val replies: List<ReplyItem>,
    val visibilityModifier: Modifier,
)

/** Cache only supplemented rows; server sample/count changes invalidate the entry. */
internal class ReplyPreviewCache(private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    private data class Key(val account: AccountSessionIdentity, val oid: Long, val type: Int, val rpid: Long)
    private data class Entry(
        val source: List<ReplyItem>?, val count: Int, val rcount: Int,
        val replies: List<ReplyItem>, val atMs: Long,
    )
    private val entries = LinkedHashMap<Key, Entry>()
    val requests = Semaphore(2)

    fun get(account: AccountSessionIdentity, item: ReplyItem): List<ReplyItem>? {
        val entry = entries[Key(account, item.oid, item.replyType, item.rpid)] ?: return null
        return entry.replies.takeIf {
            entry.source == item.replies && entry.count == item.count && entry.rcount == item.rcount &&
                nowMs() - entry.atMs < 60_000L
        }
    }

    fun put(account: AccountSessionIdentity, item: ReplyItem, replies: List<ReplyItem>) {
        if (replies.size <= item.replies.orEmpty().size) return
        val key = Key(account, item.oid, item.replyType, item.rpid)
        entries.remove(key)
        entries[key] = Entry(item.replies, item.count, item.rcount, replies, nowMs())
        while (entries.size > 128) entries.remove(entries.keys.first())
    }
}

private val replyPreviewCache = ReplyPreviewCache()

/** Only visible, settled rows supplement previews; leaving cancels queued and active work. */
@Composable
internal fun rememberReplyPreviewState(
    item: ReplyItem,
    enabled: Boolean,
): ReplyPreviewState {
    val context = LocalContext.current
    val account by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    var visible by remember(account, item.oid, item.replyType, item.rpid) { mutableStateOf(false) }
    val visibilityModifier = remember(account, item.oid, item.replyType, item.rpid) {
        Modifier.onVisibilityChanged(minFractionVisible = 0.01f, minDurationMs = 0L) { visible = it }
    }
    val workAllowed = rememberVisualWorkActive() && LocalListAuxiliaryWorkAllowed.current && visible
    val settingFlow: Flow<Int?> = remember(context) {
        SettingsManager.getCommentCollapsedReplyPreviewLimit(context)
    }
    val configuredLimit by settingFlow.collectAsStateWithLifecycle(initialValue = null)
    val collapsedLimit = configuredLimit ?: SettingsManager.DEFAULT_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT
    var replies by remember(account, item.oid, item.replyType, item.rpid, item.replies, item.count, item.rcount) {
        mutableStateOf(replyPreviewCache.get(account, item) ?: item.replies.orEmpty())
    }
    LaunchedEffect(item.oid, item.replyType, item.rpid, item.replies, item.count, item.rcount, configuredLimit, enabled, workAllowed, account) {
        // Wait for DataStore rather than fetching the default before the real preference arrives.
        val limit = configuredLimit
        if (enabled && limit != null && workAllowed) {
            delay(180L)
            replies = replyPreviewCache.requests.withPermit {
                val seed = replyPreviewCache.get(account, item) ?: replies
                loadCommentReplyPreview(item, limit, seed)
            }
            currentCoroutineContext().ensureActive()
            replyPreviewCache.put(account, item, replies)
        }
    }
    return remember(account, collapsedLimit, replies, visibilityModifier) {
        ReplyPreviewState(account, collapsedLimit, replies, visibilityModifier)
    }
}
