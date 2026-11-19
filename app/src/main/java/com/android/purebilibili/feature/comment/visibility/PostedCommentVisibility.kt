package com.android.purebilibili.feature.comment.visibility

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.IdUtils
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.feature.video.ui.components.CommentFraudResultDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

private data class PostedCommentResult(
    val account: AccountSessionIdentity,
    val oid: Long,
    val type: Int,
    val rpid: Long,
    val message: String,
    val status: CommentFraudStatus,
)

private val results = MutableStateFlow<List<PostedCommentResult>>(emptyList())

internal suspend fun checkPostedComment(
    context: Context,
    account: AccountSessionIdentity,
    reply: ReplyItem,
    oid: Long,
    type: Int,
    root: Long,
    parent: Long,
    message: String,
) {
    val enabled = SettingsManager.getCommentFraudDetectionEnabled(context)
    val shouldCheck = enabled.first()
    CommentFraudRepository.saveRecord(
        rpid = reply.rpid, oid = oid, type = type, root = root, parent = parent,
        uid = account.mid ?: reply.mid, message = message, status = CommentFraudStatus.UNKNOWN,
        postTime = if (reply.ctime > 0L) reply.ctime * 1000L else System.currentTimeMillis(),
        context = context,
    )
    if (!shouldCheck) return
    delay(8_000L)
    if (TokenManager.accountIdentity.value != account || !enabled.first()) return
    val status = CommentRepository.checkCommentStatus(
        aid = oid, rpid = reply.rpid, rootId = root, type = type, manual = false,
    ).getOrThrow()
    if (TokenManager.accountIdentity.value != account) return
    CommentFraudRepository.saveRecord(
        rpid = reply.rpid, oid = oid, type = type, root = root, parent = parent,
        uid = account.mid ?: reply.mid, message = message, status = status,
        context = context,
    )
    if (enabled.first() && TokenManager.accountIdentity.value == account) {
        val result = PostedCommentResult(account, oid, type, reply.rpid, message, status)
        results.update { pending -> pending.filter { it.account == account && it.rpid != reply.rpid } + result }
    }
}

internal const val VISIBILITY_APPEAL_URL = "https://www.bilibili.com/h5/comment/appeal"

@Composable
internal fun PostedCommentVisibilityHost() {
    val account by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val pending by results.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(account) { results.update { list -> list.filter { it.account == account } } }
    val result = pending.firstOrNull { it.account == account } ?: return
    if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return
    CommentVisibilityResultDialog(
        oid = result.oid,
        type = result.type,
        status = result.status,
        message = result.message,
        onDismiss = { results.update { it - result } },
        manual = false,
    )
}

@Composable
internal fun CommentVisibilityResultDialog(
    oid: Long,
    type: Int,
    status: CommentFraudStatus,
    message: String,
    onDismiss: () -> Unit,
    manual: Boolean = true,
    onDeleteComment: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val link = when (type) {
        1 -> "https://www.bilibili.com/video/${IdUtils.av2bv(oid)}"
        17 -> "https://www.bilibili.com/opus/$oid"
        else -> null
    }
    val canAppeal = status == CommentFraudStatus.SHADOW_BANNED ||
        status == CommentFraudStatus.INVISIBLE || status == CommentFraudStatus.DELETED
    CommentFraudResultDialog(
        status = status,
        onDismiss = onDismiss,
        message = message,
        dismissOnClickOutside = manual,
        onDeleteComment = onDeleteComment,
        onAppeal = if (canAppeal) {
            {
                link?.let { clipboard.setText(AnnotatedString(it)) }
                onDismiss()
                uriHandler.openUri(VISIBILITY_APPEAL_URL)
            }
        } else null,
    )
}
