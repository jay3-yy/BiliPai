package com.android.purebilibili.feature.comment

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.model.response.EmotePackage
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.resolveCommentFraudLightMessage
import com.android.purebilibili.data.repository.shouldShowCommentFraudResultDialog
import com.android.purebilibili.feature.video.ui.components.CommentFraudResultDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.message.feed.MessageFeedError
import com.android.purebilibili.feature.video.ui.components.CommentInputDialog
import com.android.purebilibili.feature.video.ui.components.SubReplyDetailContent
import com.android.purebilibili.feature.video.ui.components.rememberVideoCommentAppearance
import com.android.purebilibili.feature.video.viewmodel.commentComposerDraftKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentDetailScreen(
    oid: Long,
    rootId: Long,
    targetId: Long = 0L,
    type: Int = 1,
    enterUri: String = "",
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: CommentDetailViewModel = viewModel()
) {
    val subReplyState by viewModel.subReplyState.collectAsStateWithLifecycle()
    val likedComments by viewModel.likedComments.collectAsStateWithLifecycle()
    val hatedComments by viewModel.hatedComments.collectAsStateWithLifecycle()
    val showCommentInput by viewModel.showCommentInput.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val composerDrafts by viewModel.composerDrafts.collectAsStateWithLifecycle()
    val fraudResult by viewModel.fraudResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var emotePackages by remember { mutableStateOf<List<EmotePackage>>(emptyList()) }
    LaunchedEffect(showCommentInput) {
        if (showCommentInput && emotePackages.isEmpty()) {
            emotePackages = CommentRepository.getEmotePackages().getOrDefault(emptyList())
        }
    }

    LaunchedEffect(fraudResult) {
        val result = fraudResult ?: return@LaunchedEffect
        resolveCommentFraudLightMessage(result.status)?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.dismissFraudResult()
        }
    }

    fraudResult?.let { result ->
        if (shouldShowCommentFraudResultDialog(result.status)) {
            CommentFraudResultDialog(
                status = result.status,
                onDismiss = viewModel::dismissFraudResult,
                onDeleteComment = if (result.status == CommentFraudStatus.SHADOW_BANNED) {
                    { viewModel.startDissolve(result.rpid) }
                } else null
            )
        }
    }

    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var previewSourceRect by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    var showImagePreview by remember { mutableStateOf(false) }

    val currentMid = remember { TokenManager.midCache ?: 0L }
    val appearance = rememberVideoCommentAppearance()

    LaunchedEffect(oid, rootId, type, targetId) {
        viewModel.loadInitial(
            oid = oid,
            rootId = rootId,
            type = type,
            targetReplyId = targetId
        )
    }

    BackHandler(enabled = true) {
        if (subReplyState.conversationAnchor != null) {
            viewModel.closeConversation()
        } else {
            onBack()
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "评论详情",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                actions = {
                    if (enterUri.isNotBlank()) {
                        AppIconButton(
                            onClick = { onOpenLink(enterUri) }
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = "前往"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(appearance.panelColor)
        ) {
            val rootReply = subReplyState.rootReply
            when {
                subReplyState.isLoading && rootReply == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AdaptiveLoadingIndicator()
                    }
                }
                subReplyState.error != null && rootReply == null -> {
                    MessageFeedError(
                        text = subReplyState.error ?: "加载失败",
                        onRetry = {
                            viewModel.loadInitial(
                                oid = oid,
                                rootId = rootId,
                                type = type,
                                targetReplyId = targetId
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                rootReply != null -> {
                    SubReplyDetailContent(
                        rootReply = rootReply,
                        subReplies = subReplyState.items,
                        sortMode = subReplyState.sortMode,
                        error = subReplyState.error,
                        isLoading = subReplyState.isLoading,
                        isRefreshing = subReplyState.isRefreshing,
                        isEnd = subReplyState.isEnd,
                        emoteMap = emptyMap(),
                        onLoadMore = viewModel::loadMore,
                        onRefresh = viewModel::refresh,
                        onSortModeChange = viewModel::setSortMode,
                        onDismiss = onBack,
                        applyStatusBarPadding = false,
                        onRootCommentClick = { viewModel.showReplyInput(rootReply) },
                        onReplyClick = { reply -> viewModel.showReplyInput(reply) },
                        onConversationClick = viewModel::openConversation,
                        onConversationBack = viewModel::closeConversation,
                        isConversationMode = subReplyState.conversationAnchor != null,
                        conversationAnchorId = subReplyState.conversationAnchor?.rpid ?: 0L,
                        dissolvingIds = subReplyState.dissolvingIds,
                        currentMid = currentMid,
                        onDissolveStart = viewModel::startDissolve,
                        onDeleteComment = viewModel::deleteComment,
                        onCheckCommentFraud = if (type == 1) viewModel::checkCommentFraud else null,
                        onCommentLike = viewModel::likeComment,
                        onCommentHate = viewModel::hateComment,
                        likedComments = likedComments,
                        hatedComments = hatedComments,
                        onUrlClick = onOpenLink,
                        showIdentityDecorations = true,
                        onAvatarClick = { midStr ->
                            midStr.toLongOrNull()?.let(onUserClick)
                        },
                        remoteReplyCount = subReplyState.totalCount,
                        targetReplyId = targetId,
                        onImagePreview = { images, index, rect, textContent ->
                            previewImages = images
                            previewInitialIndex = index
                            previewSourceRect = rect
                            previewTextContent = textContent
                            showImagePreview = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (showImagePreview && previewImages.isNotEmpty()) {
                ImagePreviewDialog(
                    images = previewImages,
                    initialIndex = previewInitialIndex,
                    sourceRect = previewSourceRect?.rect,
                    sourceRects = previewSourceRect?.galleryRects.orEmpty(),
                    sourceCornerRadiusDp = previewSourceRect?.cornerRadiusDp
                        ?: AppShapes.containerCornerDp(ContainerLevel.Field).value,
                    textContent = previewTextContent,
                    onDismiss = {
                        showImagePreview = false
                        previewTextContent = null
                    }
                )
            }

            val composerSessionId = composerDrafts.sessionId
            val composerReplyRpid = replyingTo?.rpid
            val composerDraft = composerDrafts.drafts[commentComposerDraftKey(composerReplyRpid)]
            val composerKey = remember(oid, type, rootId, composerReplyRpid, composerSessionId) {
                "thread:$oid:$type:$rootId:$composerReplyRpid:$composerSessionId"
            }
            CommentInputDialog(
                visible = showCommentInput,
                draftKey = composerKey,
                onDismiss = {
                    if (viewModel.composerDrafts.value.sessionId == composerSessionId) {
                        viewModel.hideReplyInput()
                    }
                },
                onSend = { text, uris, sync ->
                    if (viewModel.showCommentInput.value &&
                        viewModel.composerDrafts.value.sessionId == composerSessionId &&
                        viewModel.composerDrafts.value.completedSessionId != composerSessionId &&
                        viewModel.replyingTo.value?.rpid == composerReplyRpid && !viewModel.isSending.value
                    ) {
                        viewModel.updateCommentDraft(composerSessionId, composerReplyRpid, text, uris, sync)
                        viewModel.sendReply(text, uris, sync)
                    }
                },
                isSending = isSending,
                replyToName = replyingTo?.member?.uname,
                emotePackages = emotePackages,
                initialText = composerDraft?.text.orEmpty(),
                initialImageUris = composerDraft?.imageUris.orEmpty(),
                initialSyncToDynamic = composerDraft?.syncToDynamic ?: false,
                onDraftChange = { text, images, sync ->
                    viewModel.updateCommentDraft(composerSessionId, composerReplyRpid, text, images, sync)
                }
            )
        }
    }
}
