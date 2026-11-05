package com.android.purebilibili.feature.comment

import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.ReplyItem

/** Subject-scoped send results. Main previews and detail pagination deliberately use different orders. */
internal class SentCommentReplyStore {
    private data class Preview(val replies: List<ReplyItem>, val count: Int, val rcount: Int)

    private val roots = linkedMapOf<Long, ReplyItem>()
    private val previews = mutableMapOf<Long, Preview>()
    private val knownRoots = mutableMapOf<Long, ReplyItem>()
    private val removedRoots = mutableSetOf<Long>()
    private val removedReplies = mutableSetOf<Long>()

    fun clear() {
        roots.clear()
        previews.clear()
        knownRoots.clear()
        removedRoots.clear()
        removedReplies.clear()
    }

    fun record(reply: ReplyItem, currentReplies: List<ReplyItem>): Boolean {
        if (reply.rpid in removedReplies || reply.rpid in removedRoots || reply.root in removedRoots) return false
        if (reply.root == 0L) {
            val alreadyLoaded = currentReplies.any { it.rpid == reply.rpid }
            return roots.put(reply.rpid, reply) == null && !alreadyLoaded
        }
        val root = currentReplies.firstOrNull { it.rpid == reply.root }
        if (root != null) knownRoots[reply.root] = root
        val previous = previews[reply.root]
        val alreadySent = previous?.replies?.any { it.rpid == reply.rpid } == true
        val alreadyLoaded = root?.replies?.any { it.rpid == reply.rpid } == true
        val increment = if (alreadySent || alreadyLoaded) 0 else 1
        previews[reply.root] = Preview(
            replies = listOf(reply) + previous?.replies.orEmpty().filterNot { it.rpid == reply.rpid },
            count = maxOf(root?.count ?: 0, previous?.count ?: 0) + increment,
            rcount = maxOf(root?.rcount ?: 0, previous?.rcount ?: 0) + increment
        )
        return increment > 0
    }

    fun merge(replies: List<ReplyItem>): List<ReplyItem> {
        if (roots.isEmpty() && previews.isEmpty() && removedRoots.isEmpty() && removedReplies.isEmpty()) return replies
        val remoteIds = replies.mapTo(hashSetOf()) { it.rpid }
        val combined = roots.values.toList().asReversed().filterNot { it.rpid in remoteIds } + replies
        return combined.filterNot { it.rpid in removedRoots }.map { root ->
            val local = previews[root.rpid]
            val remotePreview = root.replies.orEmpty()
            val removedCount = remotePreview.count { it.rpid in removedReplies }
            if (local == null && removedCount == 0) return@map root
            root.copy(
                replies = (local?.replies.orEmpty() + remotePreview)
                    .filterNot { it.rpid in removedReplies }.distinctBy { it.rpid },
                count = maxOf((root.count - removedCount).coerceAtLeast(0), local?.count ?: 0),
                rcount = maxOf((root.rcount - removedCount).coerceAtLeast(0), local?.rcount ?: 0)
            )
        }
    }

    fun isReplyRemoved(rpid: Long): Boolean = rpid in removedReplies

    fun isRootRemoved(rpid: Long): Boolean = rpid in removedRoots

    /** Oldest local send first; never seed detail pages from the newest-first preview sample. */
    fun repliesFor(rootId: Long): List<ReplyItem> = previews[rootId]?.replies.orEmpty().asReversed()

    fun rootFor(rootId: Long): ReplyItem? {
        if (isRootRemoved(rootId)) return null
        val root = knownRoots[rootId] ?: roots[rootId] ?: return null
        return merge(listOf(root)).firstOrNull { it.rpid == rootId }
    }

    /** A just-published rpid may not yet be readable by the server's target lookup. */
    fun remoteTargetReplyId(rootId: Long, targetReplyId: Long): Long =
        if (previews[rootId]?.replies?.any { it.rpid == targetReplyId } == true) 0L else targetReplyId

    /** Keep locally sent copies in sync when a consumer changes a reply's reaction. */
    fun updateReaction(rpid: Long, action: Int, like: Int) {
        fun update(reply: ReplyItem): ReplyItem {
            val updated = if (reply.rpid == rpid) reply.copy(action = action, like = like) else reply
            val nested = updated.replies
            return if (nested.isNullOrEmpty()) updated
            else updated.copy(replies = nested.map(::update))
        }
        roots.replaceAll { _, reply -> update(reply) }
        knownRoots.replaceAll { _, reply -> update(reply) }
        previews.replaceAll { _, preview -> preview.copy(replies = preview.replies.map(::update)) }
    }

    fun removeRoot(rpid: Long) {
        removedRoots += rpid
        roots.remove(rpid)
        previews.remove(rpid)
        knownRoots.remove(rpid)
    }

    fun removeReply(rootId: Long, rpid: Long) {
        if (!removedReplies.add(rpid)) return
        knownRoots[rootId]?.let { root ->
            knownRoots[rootId] = root.copy(
                replies = root.replies?.filterNot { it.rpid == rpid },
                count = (root.count - 1).coerceAtLeast(0),
                rcount = (root.rcount - 1).coerceAtLeast(0)
            )
        }
        previews[rootId]?.let { preview ->
            previews[rootId] = preview.copy(
                replies = preview.replies.filterNot { it.rpid == rpid },
                count = (preview.count - 1).coerceAtLeast(0),
                rcount = (preview.rcount - 1).coerceAtLeast(0)
            )
        }
    }
}

/** Shares pending sends across routes such as BGM's main list and the independent thread screen. */
internal object SentCommentReplyStores {
    private data class Subject(val oid: Long, val type: Int)
    private var account: AccountSessionIdentity? = null
    private val subjects = LinkedHashMap<Subject, SentCommentReplyStore>(12, 0.75f, true)

    @Synchronized
    fun forSubject(oid: Long, type: Int): SentCommentReplyStore {
        val currentAccount = TokenManager.accountIdentity.value
        if (account != currentAccount) {
            subjects.clear()
            account = currentAccount
        }
        val key = Subject(oid, type)
        subjects[key]?.let { return it }
        if (subjects.size >= 12) {
            subjects.entries.iterator().run { next(); remove() }
        }
        return SentCommentReplyStore().also { subjects[key] = it }
    }

    @Synchronized
    fun clear() {
        subjects.clear()
        account = null
    }
}
