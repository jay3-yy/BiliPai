package com.android.purebilibili.feature.video.viewmodel

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.CommentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val LocalReplyPreviewReactions = compositionLocalOf<ReplyPreviewReactionViewModel?> { null }

internal data class PreviewReplyReaction(val action: Int, val likeCount: Int)

/** 根层持有；预览和回复详情共用操作结果，离开单条评论不会取消已提交的点赞。 */
internal class ReplyPreviewReactionViewModel : ViewModel() {
    private data class Key(val account: AccountSessionIdentity, val oid: Long, val type: Int, val rpid: Long)
    private val reactions = mutableStateMapOf<Key, PreviewReplyReaction>()
    private val pending = mutableSetOf<Key>()

    fun reaction(item: ReplyItem, account: AccountSessionIdentity): PreviewReplyReaction? =
        reactions[Key(account, item.oid, item.replyType, item.rpid)]

    fun react(item: ReplyItem, account: AccountSessionIdentity, like: Boolean, onError: (String) -> Unit) {
        if (TokenManager.accountIdentity.value != account) return
        val key = Key(account, item.oid, item.replyType, item.rpid)
        if (!pending.add(key)) return
        val before = reactions[key] ?: PreviewReplyReaction(item.action, item.like)
        val target = if (like) 1 else 2
        val action = if (before.action == target) 0 else target
        val count = before.likeCount + (if (action == 1) 1 else 0) - (if (before.action == 1) 1 else 0)
        reactions[key] = PreviewReplyReaction(action, count)
        viewModelScope.launch {
            try {
                val result = if (like) {
                    CommentRepository.likeCommentForSubject(item.oid, item.replyType, item.rpid, action == 1)
                } else {
                    CommentRepository.hateCommentForSubject(item.oid, item.replyType, item.rpid, action == 2)
                }
                result.onFailure { error ->
                    reactions[key] = before
                    if (TokenManager.accountIdentity.value == account) onError(error.message ?: "操作失败，请重试")
                }
            } catch (cancelled: CancellationException) {
                reactions[key] = before
                throw cancelled
            } finally {
                pending.remove(key)
            }
        }
    }
}
