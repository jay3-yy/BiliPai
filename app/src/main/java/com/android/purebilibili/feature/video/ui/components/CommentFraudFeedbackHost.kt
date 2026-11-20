package com.android.purebilibili.feature.video.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.feature.comment.CommentDetailFraudResult
import com.android.purebilibili.feature.comment.visibility.CommentVisibilityResultDialog
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import kotlinx.coroutines.flow.Flow

/** Shared feedback for ordinary videos, episode comments and comment sheets. */
@Composable
internal fun CommentFraudFeedbackHost(
    fraudEvents: Flow<CommentDetailFraudResult>,
    onDismissResult: () -> Unit = {},
    onDeleteShadowBanned: ((Long) -> Unit)? = null,
    enabled: Boolean = true,
) {
    var dialogResult by remember(fraudEvents, enabled) { mutableStateOf<CommentDetailFraudResult?>(null) }
    LaunchedEffect(fraudEvents, enabled) {
        if (!enabled) return@LaunchedEffect
        fraudEvents.collect { dialogResult = it }
    }
    dialogResult?.let { result ->
        CommentVisibilityResultDialog(
            oid = result.oid,
            type = result.type,
            status = result.status,
            message = result.message,
            onDismiss = {
                dialogResult = null
                onDismissResult()
            },
            onDeleteComment = if (result.status == CommentFraudStatus.SHADOW_BANNED && onDeleteShadowBanned != null) {
                { onDeleteShadowBanned(result.rpid) }
            } else null,
        )
    }
}

@Composable
internal fun CommentFraudFeedbackHost(
    commentViewModel: VideoCommentViewModel,
    enabled: Boolean = true,
    onDeleteShadowBanned: ((Long) -> Unit)? = null,
) {
    CommentFraudFeedbackHost(
        fraudEvents = commentViewModel.fraudEvent,
        onDismissResult = commentViewModel::dismissFraudResult,
        onDeleteShadowBanned = onDeleteShadowBanned ?: commentViewModel::startDissolve,
        enabled = enabled,
    )
}
