package com.android.purebilibili.feature.video.screen

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.EmotePackage
import com.android.purebilibili.data.model.response.MentionSearchUser
import com.android.purebilibili.feature.video.ui.components.CommentInputDialog
import com.android.purebilibili.feature.video.ui.components.DanmakuSendDialog
import com.android.purebilibili.feature.video.ui.components.shouldUseInlineDanmakuComposer
import com.android.purebilibili.feature.video.viewmodel.CommentComposerDraft
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import com.android.purebilibili.feature.video.viewmodel.commentComposerDraftKey
import kotlinx.coroutines.launch

@Immutable
internal data class VideoDetailInputOverlayLayoutInfo(
    val screenHeightPx: Int,
    val topReservedPx: Int,
)

@Immutable
private data class DanmakuInputSnapshot(
    val visible: Boolean,
    val isSending: Boolean,
    val initialColor: Int,
    val initialMode: Int,
    val initialFontSize: Int,
    val initialText: String,
    val initialAttentionCommand: Boolean,
)

@Immutable
private data class DanmakuInputActions(
    val dismiss: () -> Unit,
    val send: (String, Int, Int, Int, Boolean) -> Unit,
    val updateDraft: (String, Boolean) -> Unit,
    val updateSelection: (Int, Int, Int) -> Unit,
)

@Immutable
private data class CommentInputSnapshot(
    val visible: Boolean,
    val draftKey: String,
    val isSending: Boolean,
    val replyToName: String?,
    val inputHint: String,
    val canInputComment: Boolean,
    val emotePackages: List<EmotePackage>,
    val mentionUsers: List<MentionSearchUser>,
    val isMentionSearching: Boolean,
    val mentionSearchError: String?,
    val draft: CommentComposerDraft,
)

@Immutable
private data class CommentInputActions(
    val dismiss: () -> Unit,
    val searchMentions: (String) -> Unit,
    val updateDraft: (String, List<Uri>, Boolean) -> Unit,
    val send: (String, List<Uri>, Boolean) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoDetailInputOverlayAdapter(
    context: Context,
    configuration: Configuration,
    viewModel: VideoPlaybackViewModel,
    commentState: CommentUiState,
    showCommentInput: Boolean,
    isLandscape: Boolean,
    isFullscreenMode: Boolean,
    isPortraitFullscreen: Boolean,
    videoPlayerRootBottomPx: Int,
    hideStatusBars: Boolean,
    immersiveStatusBarBackdropEnabled: Boolean,
    currentVideoPositionMsProvider: () -> Long,
): VideoDetailInputOverlayLayoutInfo {
    val showDanmakuDialog by viewModel.showDanmakuDialog.collectAsStateWithLifecycle()
    val isSendingDanmaku by viewModel.isSendingDanmaku.collectAsStateWithLifecycle()
    val composerDrafts by viewModel.composerDrafts.collectAsStateWithLifecycle()
    val rememberedDanmakuSendColor by SettingsManager.getDanmakuSendColor(context)
        .collectAsStateWithLifecycle(initialValue = 16_777_215)
    val rememberedDanmakuSendMode by SettingsManager.getDanmakuSendMode(context)
        .collectAsStateWithLifecycle(initialValue = 1)
    val rememberedDanmakuSendFontSize by SettingsManager.getDanmakuSendFontSize(context)
        .collectAsStateWithLifecycle(initialValue = 25)
    val preferenceScope = rememberCoroutineScope()
    val fallbackPlayerBottomPx = with(LocalDensity.current) {
        val playerHeight = configuration.screenWidthDp.dp * 9f / 16f
        val stableStatusBar = resolveVideoDetailStableStatusBarHeightDp(
            visibleStatusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value,
            statusBarIgnoringVisibilityHeightDp = WindowInsets.statusBarsIgnoringVisibility
                .asPaddingValues().calculateTopPadding().value,
            hideStatusBars = hideStatusBars,
        )
        val playerTopInset = resolveVideoDetailPortraitPlayerTopInsetDp(
            stableStatusBarHeightDp = stableStatusBar,
            hideStatusBars = hideStatusBars,
            immersiveStatusBarBackdropEnabled = immersiveStatusBarBackdropEnabled,
        ).dp
        (playerHeight + playerTopInset).toPx().toInt()
    }
    val topReservedPx = remember(
        isLandscape,
        isFullscreenMode,
        isPortraitFullscreen,
        videoPlayerRootBottomPx,
        fallbackPlayerBottomPx,
        immersiveStatusBarBackdropEnabled,
    ) {
        resolveDanmakuDialogTopReservePx(
            isLandscape = isLandscape,
            isFullscreenMode = isFullscreenMode,
            isPortraitFullscreen = isPortraitFullscreen,
            playerBottomPx = videoPlayerRootBottomPx.takeIf { it > 0 },
            fallbackPlayerBottomPx = fallbackPlayerBottomPx,
        )
    }
    val topReservedDp = with(LocalDensity.current) { topReservedPx.toDp() }

    VideoDetailDanmakuInputOverlayContent(
        snapshot = DanmakuInputSnapshot(
            visible = showDanmakuDialog && !shouldUseInlineDanmakuComposer(isFullscreenMode),
            isSending = isSendingDanmaku,
            initialColor = rememberedDanmakuSendColor,
            initialMode = rememberedDanmakuSendMode,
            initialFontSize = rememberedDanmakuSendFontSize,
            initialText = composerDrafts.danmaku.text,
            initialAttentionCommand = composerDrafts.danmaku.attentionCommand,
        ),
        actions = DanmakuInputActions(
            dismiss = viewModel::hideDanmakuSendDialog,
            send = viewModel::sendDanmaku,
            updateDraft = viewModel::updateDanmakuDraft,
            updateSelection = { color, mode, fontSize ->
                preferenceScope.launch {
                    SettingsManager.setDanmakuSendColor(context, color)
                    SettingsManager.setDanmakuSendMode(context, mode)
                    SettingsManager.setDanmakuSendFontSize(context, fontSize)
                }
            },
        ),
        topReservedSpace = topReservedDp,
    )

    val isSendingComment by viewModel.isSendingComment.collectAsStateWithLifecycle()
    val replyingToComment by viewModel.replyingToComment.collectAsStateWithLifecycle()
    val emotePackages by viewModel.emotePackages.collectAsStateWithLifecycle()
    val mentionSearchState by viewModel.commentMentionSearchState.collectAsStateWithLifecycle()
    val draftVideoId = composerDrafts.videoId
    val draftSessionId = composerDrafts.comments.sessionId
    val draftReplyRpid = replyingToComment?.rpid
    val draftKey = remember(draftVideoId, draftSessionId, draftReplyRpid) {
        "$draftVideoId:$draftReplyRpid:$draftSessionId"
    }
    val commentDraft = composerDrafts.comments.drafts[commentComposerDraftKey(draftReplyRpid)]
        ?: CommentComposerDraft()
    VideoDetailCommentInputOverlayContent(
        snapshot = CommentInputSnapshot(
            visible = showCommentInput,
            draftKey = draftKey,
            isSending = isSendingComment,
            replyToName = replyingToComment?.member?.uname,
            inputHint = if (replyingToComment != null) commentState.childInputHint else commentState.rootInputHint,
            canInputComment = commentState.canInputComment,
            emotePackages = emotePackages,
            mentionUsers = mentionSearchState.users,
            isMentionSearching = mentionSearchState.isLoading,
            mentionSearchError = mentionSearchState.errorMessage,
            draft = commentDraft,
        ),
        actions = CommentInputActions(
            dismiss = {
                val current = viewModel.composerDrafts.value
                if (current.videoId == draftVideoId && current.comments.sessionId == draftSessionId) {
                    viewModel.hideCommentInputDialog()
                }
            },
            searchMentions = viewModel::searchCommentMentionUsers,
            updateDraft = { text, images, sync ->
                viewModel.updateCommentDraft(draftVideoId, draftSessionId, draftReplyRpid, text, images, sync)
            },
            send = { message, imageUris, syncToDynamic ->
                val current = viewModel.composerDrafts.value
                if (current.videoId == draftVideoId && current.comments.sessionId == draftSessionId &&
                    viewModel.replyingToComment.value?.rpid == draftReplyRpid &&
                    viewModel.showCommentDialog.value
                ) {
                    viewModel.sendComment(message, imageUris, syncToDynamic)
                }
            },
        ),
        currentVideoPositionMsProvider = currentVideoPositionMsProvider,
    )
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.roundToPx() }
    return VideoDetailInputOverlayLayoutInfo(screenHeightPx, topReservedPx)
}

@Composable
private fun VideoDetailDanmakuInputOverlayContent(
    snapshot: DanmakuInputSnapshot,
    actions: DanmakuInputActions,
    topReservedSpace: Dp,
) {
    DanmakuSendDialog(
        visible = snapshot.visible,
        onDismiss = actions.dismiss,
        onSend = actions.send,
        isSending = snapshot.isSending,
        initialColor = snapshot.initialColor,
        initialMode = snapshot.initialMode,
        initialFontSize = snapshot.initialFontSize,
        initialText = snapshot.initialText,
        initialAttentionCommand = snapshot.initialAttentionCommand,
        onDraftChange = actions.updateDraft,
        onSelectionChange = actions.updateSelection,
        topReservedSpace = topReservedSpace,
    )
}

@Composable
private fun VideoDetailCommentInputOverlayContent(
    snapshot: CommentInputSnapshot,
    actions: CommentInputActions,
    currentVideoPositionMsProvider: () -> Long,
) {
    CommentInputDialog(
        visible = snapshot.visible,
        draftKey = snapshot.draftKey,
        onDismiss = actions.dismiss,
        isSending = snapshot.isSending,
        replyToName = snapshot.replyToName,
        inputHint = snapshot.inputHint,
        canInputComment = snapshot.canInputComment,
        emotePackages = snapshot.emotePackages,
        mentionUsers = snapshot.mentionUsers,
        isMentionSearching = snapshot.isMentionSearching,
        mentionSearchError = snapshot.mentionSearchError,
        onMentionSearchQueryChange = actions.searchMentions,
        initialText = snapshot.draft.text,
        initialImageUris = snapshot.draft.imageUris,
        initialSyncToDynamic = snapshot.draft.syncToDynamic,
        onDraftChange = actions.updateDraft,
        currentVideoPositionMsProvider = currentVideoPositionMsProvider,
        onSend = actions.send,
    )
}
