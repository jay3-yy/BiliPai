package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.android.purebilibili.feature.video.ui.components.CommentFraudFeedbackHost
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel

@Composable
internal fun VideoDetailCommentFraudOverlayAdapter(
    playbackViewModel: VideoPlaybackViewModel,
    commentViewModel: VideoCommentViewModel,
    aid: Long?,
) {
    LaunchedEffect(playbackViewModel, commentViewModel, aid) {
        if (aid == null) return@LaunchedEffect
        playbackViewModel.commentSentEvent.collect { event ->
            commentViewModel.onExternalCommentSent(
                aid = event.aid,
                newReply = event.reply,
                senderAccount = event.account,
            )
        }
    }

    CommentFraudFeedbackHost(commentViewModel = commentViewModel)
}
