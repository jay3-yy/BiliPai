package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.video.ui.components.VideoCommentSheetHost
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel

@Composable
fun PortraitCommentSheet(
    visible: Boolean,
    active: Boolean = true,
    onDismiss: () -> Unit,
    onVisibilityProgressChange: (Float) -> Unit = {},
    commentViewModel: VideoCommentViewModel,
    aid: Long,
    upMid: Long = 0,
    expectedReplyCount: Int = 0,
    emoteMap: Map<String, String> = emptyMap(),
    maxTimestampMs: Long? = null,
    onRootCommentClick: (() -> Unit)? = null,
    onReplyClick: ((ReplyItem) -> Unit)? = null,
    onUserClick: (Long) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)? = null,
) {
    if (!active) return

    val subReplyState by commentViewModel.subReplyState.collectAsStateWithLifecycle()
    val effectiveVisibility = resolvePortraitCommentSheetVisibility(
        active = true,
        commentSheetVisible = visible,
        subReplyVisible = subReplyState.visible
    )
    val hostMainSheetVisible = resolvePortraitCommentHostMainSheetVisible(
        commentSheetVisible = effectiveVisibility.commentSheetVisible,
        subReplyVisible = effectiveVisibility.subReplyVisible
    )

    VideoCommentSheetHost(
        mainSheetVisible = hostMainSheetVisible,
        onDismiss = onDismiss,
        onMainSheetVisibilityProgressChange = onVisibilityProgressChange,
        commentViewModel = commentViewModel,
        aid = aid,
        upMid = upMid,
        expectedReplyCount = expectedReplyCount,
        emoteMap = emoteMap,
        maxTimestampMs = maxTimestampMs,
        onRootCommentClick = onRootCommentClick,
        onReplyClick = onReplyClick,
        onUserClick = onUserClick,
        onOpenBilibiliLink = onOpenBilibiliLink,
        pullToRefreshFromTopOnly = true,
    )
}
