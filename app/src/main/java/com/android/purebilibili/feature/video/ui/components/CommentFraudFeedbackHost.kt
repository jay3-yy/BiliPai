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

/** Shared feedback for ordinary videos, episode comments and comment sheets. */
@Composable
internal fun CommentFraudFeedbackHost(
    commentViewModel: VideoCommentViewModel,
    enabled: Boolean = true,
) {
    var dialogResult by remember(commentViewModel, enabled) { mutableStateOf<CommentDetailFraudResult?>(null) }
    LaunchedEffect(commentViewModel, enabled) {
        if (!enabled) return@LaunchedEffect
        commentViewModel.fraudEvent.collect { dialogResult = it }
    }
    dialogResult?.let { result ->
        CommentVisibilityResultDialog(
            oid = result.oid,
            type = result.type,
            status = result.status,
            message = result.message,
            onDismiss = {
                dialogResult = null
                commentViewModel.dismissFraudResult()
            },
            onDeleteComment = if (result.status == CommentFraudStatus.SHADOW_BANNED) {
                { commentViewModel.startDissolve(result.rpid) }
            } else null,
        )
    }
}
