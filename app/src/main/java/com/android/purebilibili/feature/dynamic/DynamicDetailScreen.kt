package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppSpacingTokens

import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackBlurFrame
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppRadioButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextField
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.imageLoader
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppSplitLayout
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppGridLayoutIcon
import com.android.purebilibili.core.ui.rememberAppListLayoutIcon
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.feature.dynamic.components.DynamicCardV2
import com.android.purebilibili.feature.dynamic.components.DynamicCardActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardInteractionActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardNavigationActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardPresentation
import com.android.purebilibili.feature.dynamic.components.DynamicManageAction
import com.android.purebilibili.feature.dynamic.components.dispatchDynamicManageAction
import com.android.purebilibili.feature.dynamic.components.DynamicPublishComposer
import com.android.purebilibili.feature.dynamic.components.shouldShowDynamicDetailImageLayoutToggle
import com.android.purebilibili.feature.dynamic.components.toggleDynamicDetailImageLayout
import com.android.purebilibili.feature.dynamic.components.resolveDynamicReportReasons
import com.android.purebilibili.feature.dynamic.components.saveDynamicImageToGallery
import com.android.purebilibili.feature.dynamic.components.DynamicShareToMessageDialog
import com.android.purebilibili.feature.dynamic.components.DynamicCommentEntryBar
import com.android.purebilibili.feature.dynamic.components.DynamicCommentInputHost
import com.android.purebilibili.feature.dynamic.components.DynamicInlineCommentHeader
import com.android.purebilibili.feature.dynamic.components.DynamicSubReplyPreviewHost
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.dynamic.components.dynamicInlineCommentItems
import com.android.purebilibili.feature.dynamic.components.RepostDialog
import com.android.purebilibili.feature.video.ui.components.resolveBottomInputBarContentBottomPadding
import com.android.purebilibili.feature.video.ui.components.shouldUseFloatingLiquidBottomInputBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.wallpaper.sharedGlobalWallpaperBackground

internal fun resolveDynamicDetailBottomBarColor(
    uiStyle: AppUiStyle,
    colorScheme: ColorScheme,
): Color = when (uiStyle) {
    AppUiStyle.MIUIX -> colorScheme.background
    AppUiStyle.MATERIAL3 -> colorScheme.surface
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicDetailScreen(
    dynamicId: String,
    openCommentRootRpid: Long = 0L,
    openCommentTargetRpid: Long = 0L,
    openComments: Boolean = false,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit = { _, _ -> },
    onUserClick: (Long) -> Unit,
    onTopicClick: (Long) -> Unit = {},
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onArticleClick: (articleId: Long, title: String) -> Unit = { _, _ -> },
    onLiveClick: (roomId: Long, title: String, uname: String) -> Unit = { _, _, _ -> },
    onMusicClick: ((Long) -> Unit)? = null,
    onCollectionClick: ((Long, Long, String, String) -> Unit)? = null,
    onCourseClick: ((String, String) -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null,
    onDynamicDetailClick: (String) -> Unit = {},
    onMatchClick: ((Long) -> Unit)? = null,
    onShareToMessageClick: ((DynamicItem) -> Unit)? = null,
    contentTransitionReady: Boolean = true,
) {
    val interactionViewModel: DynamicViewModel = viewModel()
    val detailViewModel: DynamicDetailViewModel = viewModel(key = "dynamic-detail:$dynamicId")
    val screenTitle = stringResource(R.string.dynamic_detail_title)
    val backLabel = stringResource(R.string.common_back)
    val retryLabel = stringResource(R.string.common_retry)
    val loadFailedMessage = stringResource(R.string.dynamic_detail_load_failed)
    val loadedUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(detailViewModel, dynamicId) {
        detailViewModel.load(dynamicId, loadFailedMessage)
    }

    val uiState = if (contentTransitionReady) loadedUiState else DynamicDetailUiState.Loading
    androidx.compose.runtime.CompositionLocalProvider(
        com.android.purebilibili.core.ui.transition.LocalDynamicCommentPreviewVisible provides false,
        com.android.purebilibili.feature.share.LocalReplyShareContext provides
            com.android.purebilibili.feature.share.ReplyShareContext("https://t.bilibili.com/$dynamicId", title = "动态"),
    ) {
    val contentRevealAlpha = com.android.purebilibili.core.ui.transition.rememberSharedCardDetailRevealAlpha(
        entryKey = dynamicId,
        contentTransitionReady = contentTransitionReady,
        contentLoaded = uiState !is DynamicDetailUiState.Loading,
    )
    val context = LocalContext.current
    val homeSettings by SettingsManager.getHomeSettings(context)
        .collectAsStateWithLifecycle(initialValue = HomeSettings())
    val liquidGlassEnabled = homeSettings.commentBarGlassEnabled
    // Only scrolling content captures this backdrop. The comment controls remain sibling
    // overlays, matching video detail and preventing a RenderNode backdrop cycle.
    // Record a complete viewport, including its background. Capturing only the transparent
    // LazyColumn leaves sparse glyph/image bounds as filter input; edge-clamped sampling
    // can then stretch those bounds into rectangular bands inside the glass.
    // The composer stays outside this source to prevent sampling itself.
    val detailCommentSource = if (liquidGlassEnabled) rememberChromeBackdropSource() else null
    val detailCommentBackdrop = detailCommentSource?.backdrop
    val detailCommentSourceModifier = if (detailCommentSource != null) {
        detailCommentSource.modifier.sharedGlobalWallpaperBackground(MaterialTheme.colorScheme.background)
    } else {
        Modifier
    }
    val gifImageLoader = context.imageLoader
    val defaultDetailImageLayout by SettingsManager.getDynamicDetailImageLayout(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.peekDynamicDetailImageLayout(context)
        )
    var detailImageLayoutOverrideName by rememberSaveable(dynamicId) { mutableStateOf<String?>(null) }
    val effectiveDetailImageLayout = remember(
        detailImageLayoutOverrideName,
        defaultDetailImageLayout,
    ) {
        detailImageLayoutOverrideName
            ?.let { name -> SettingsManager.DynamicDetailImageLayout.entries.firstOrNull { it.name == name } }
            ?: defaultDetailImageLayout
    }
    val likedDynamics by interactionViewModel.likedDynamics.collectAsStateWithLifecycle()
    val likeOverrides by interactionViewModel.likeOverrides.collectAsStateWithLifecycle()
    // 评论区一次性读取单一状态：以前 5 个 flow 分别发射，加载完成时会出现中间帧。
    val commentUiState by interactionViewModel.commentUiState.collectAsStateWithLifecycle()
    val comments = remember(
        commentUiState.comments,
        commentUiState.pinnedRootReplyId,
        commentUiState.pinnedReply,
    ) { resolveDynamicDetailDisplayComments(commentUiState) }
    val commentsLoading = commentUiState.isLoading
    val commentsRefreshing = commentUiState.isRefreshing
    val commentsRefreshError = commentUiState.pinnedReplyError ?: commentUiState.refreshError
    val commentsLoadingMore = commentUiState.isLoadingMore
    val commentTotalCount = commentUiState.totalCount
    // 骨架槽位数按可视高度估算：铺满首屏，数据到达时列表不会被撑长或缩短。
    val commentSkeletonSlotCount = resolveDynamicCommentSkeletonSlotCount(
        viewportHeightDp = LocalConfiguration.current.screenHeightDp,
    )
    val commentSortMode by interactionViewModel.dynamicCommentSortMode.collectAsStateWithLifecycle()
    val subReplyState by interactionViewModel.subReplyState.collectAsStateWithLifecycle()
    var showRepostDialog by remember { mutableStateOf<String?>(null) }
    var pendingReport by remember { mutableStateOf<DynamicManageAction.Report?>(null) }
    var editingAction by remember { mutableStateOf<DynamicManageAction.Edit?>(null) }
    var pendingMessageShare by remember { mutableStateOf<DynamicItem?>(null) }
    var reactionItem by remember(dynamicId) { mutableStateOf<DynamicItem?>(null) }
    reactionItem?.let { item ->
        com.android.purebilibili.feature.dynamic.components.DynamicReactionSheet(
            item = item,
            onDismiss = { reactionItem = null },
            onUserClick = onUserClick,
        )
    }
    var forwardCountDelta by remember(dynamicId) { mutableIntStateOf(0) }
    var subReplyCoveredBlurProgress by remember { mutableFloatStateOf(0f) }
    val avatarPushEnabled = com.android.purebilibili.feature.video.ui.components.rememberCommentAvatarPushEnabled()
    val detailListState = rememberLazyListState(
        initialFirstVisibleItemIndex =
            if (openComments || openCommentRootRpid > 0L || openCommentTargetRpid > 0L) 1 else 0,
    )
    //  [新增] 大屏/横屏分栏：右栏评论列表
    val commentListState = rememberLazyListState()
    val useSplitLayout = LocalWindowSizeClass.current.shouldUseSplitLayout
    val detailScrollScope = rememberCoroutineScope()
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var previewSourceRect by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    LaunchedEffect(commentsRefreshError) {
        commentsRefreshError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    AppScaffold(
        modifier = Modifier.graphicsLayer {
            alpha = if (uiState is DynamicDetailUiState.Loading) 1f else contentRevealAlpha.value
        },
        blurContentReady = uiState !is DynamicDetailUiState.Loading,
        topBar = {
            val canToggleImageLayout = (uiState as? DynamicDetailUiState.Success)
                ?.let { shouldShowDynamicDetailImageLayoutToggle(it.item) } == true
            AppTopBar(
                title = screenTitle,
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                },
                actions = {
                    if (canToggleImageLayout) {
                        val nextImageLayout = toggleDynamicDetailImageLayout(effectiveDetailImageLayout)
                        AppIconButton(
                            onClick = {
                                detailImageLayoutOverrideName = nextImageLayout.name
                            }
                        ) {
                            AppIcon(
                                imageVector = if (effectiveDetailImageLayout ==
                                    SettingsManager.DynamicDetailImageLayout.EXPANDED
                                ) {
                                    rememberAppGridLayoutIcon()
                                } else {
                                    rememberAppListLayoutIcon()
                                },
                                contentDescription = "切换图片展示（当前：${effectiveDetailImageLayout.label}）",
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            DynamicDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    com.android.purebilibili.core.ui.CutePersonLoadingIndicator()
                }
            }

            is DynamicDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)
                    ) {
                        AppText(
                            text = state.message,
                            color = AppContentColors.secondaryText()
                        )
                        AppButton(onClick = { detailViewModel.load(dynamicId, loadFailedMessage, force = true) }) {
                            AppText(retryLabel)
                        }
                    }
                }
            }

            is DynamicDetailUiState.Success -> {
                val commentTargetKey = remember(state.item) {
                    val basic = state.item.basic
                    "${state.item.id_str}_${basic?.comment_id_str}_${basic?.comment_type}"
                }
                LaunchedEffect(
                    commentTargetKey,
                    openCommentRootRpid,
                    openCommentTargetRpid
                ) {
                    detailViewModel.initializeCommentsOnce(
                        targetKey = commentTargetKey,
                        rootReplyId = openCommentRootRpid,
                        targetReplyId = openCommentTargetRpid,
                    ) {
                        interactionViewModel.openCommentSheet(
                            item = state.item,
                            rootReplyId = openCommentRootRpid,
                            targetReplyId = openCommentTargetRpid,
                            pinRootReply = true,
                        )
                        if (openComments || openCommentRootRpid > 0L || openCommentTargetRpid > 0L) {
                            detailScrollScope.launch {
                                val listState = if (useSplitLayout) commentListState else detailListState
                                val headerIndex = if (useSplitLayout) 0 else 1
                                snapshotFlow {
                                    val current = commentUiState
                                    val pinReady = (openCommentRootRpid <= 0L && openCommentTargetRpid <= 0L) ||
                                        current.pinnedReply?.rpid == current.pinnedRootReplyId ||
                                        current.pinnedReplyError != null
                                    current.dynamicId == state.item.id_str && !current.isLoading && pinReady &&
                                        listState.layoutInfo.totalItemsCount > headerIndex
                                }.first { it }
                                listState.scrollToItem(headerIndex)
                            }
                        }
                    }
                }

                LaunchedEffect(detailListState, commentListState, useSplitLayout) {
                    snapshotFlow {
                        // 分栏时右栏列表负责触底加载，竖屏时主列表负责。
                        // 加载中/条数变化必须在 snapshotFlow 内读取，不能当 LaunchedEffect key，
                        // 否则 loading footer 插进列表会反复重启 effect，底部评论框跟着闪。
                        val activeState = if (useSplitLayout) commentListState else detailListState
                        val lastVisibleIndex = activeState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val itemCount = activeState.layoutInfo.totalItemsCount
                        // 在 snapshotFlow 内直接读最新 State，避免捕获入场时的 loading/列表快照。
                        val current = commentUiState
                        if (shouldLoadMoreDynamicDetailComments(
                                lastVisibleIndex = lastVisibleIndex,
                                itemCount = itemCount,
                                hasMore = current.hasMore,
                                isLoading = current.isLoading || current.isRefreshing || current.refreshError != null,
                                isLoadingMore = current.isLoadingMore,
                            )) current.nextPage else null
                    }
                        .distinctUntilChanged()
                        .filterNotNull()
                        .collect {
                            interactionViewModel.loadMoreComments()
                        }
                }

                val showCommentsInPlace: () -> Unit = {
                    detailScrollScope.launch {
                        if (useSplitLayout) {
                            commentListState.animateScrollToItem(0)
                        } else {
                            detailListState.animateScrollToItem(1)
                        }
                    }
                }

                //  [新增] 卡片与评论内容提取，供分栏/单列两种布局复用
                val cardContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                    item {
                        DynamicCardV2(
                            item = state.item,
                            gifImageLoader = gifImageLoader,
                            actions = DynamicCardActions(
                                navigation = DynamicCardNavigationActions(
                                    onVideoClick = onVideoClick,
                                    onBangumiClick = onBangumiClick,
                                    onUserClick = onUserClick,
                                    onTopicClick = onTopicClick,
                                    onTopicKeywordClick = onTopicKeywordClick,
                                    onArticleClick = onArticleClick,
                                    onLiveClick = onLiveClick,
                                    onMusicClick = onMusicClick,
                                    onCollectionClick = onCollectionClick,
                                    onCourseClick = onCourseClick,
                                    onOpenLink = onOpenLink,
                                    onDynamicDetailClick = onDynamicDetailClick,
                                    onMatchClick = onMatchClick,
                                ),
                                interaction = DynamicCardInteractionActions(
                                    onCommentClick = { showCommentsInPlace() },
                                    onRepostClick = { showRepostDialog = it },
                                    onLikeClickWithState = { targetDynamicId, isLiked ->
                                        interactionViewModel.likeDynamic(
                                            dynamicId = targetDynamicId,
                                            knownIsLiked = isLiked,
                                        ) { _, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onWatchLaterClick = { aid ->
                                        interactionViewModel.addToWatchLater(aid) { _, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onSaveDynamicClick = {
                                        detailScrollScope.launch {
                                            val saved = saveDynamicImageToGallery(context, state.item)
                                            android.widget.Toast.makeText(
                                                context,
                                                if (saved) "已保存动态图片" else "保存动态失败",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    onShareToMessageClick = {
                                        onShareToMessageClick?.invoke(state.item) ?: run { pendingMessageShare = state.item }
                                    },
                                    onCheckDynamicClick = {
                                        interactionViewModel.checkDynamic(state.item.id_str) { _, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onReserveClick = interactionViewModel::toggleDynamicReserve,
                                    onManageAction = { action ->
                                        dispatchDynamicManageAction(
                                            action = action,
                                            onReport = { pendingReport = it },
                                            onEdit = { editingAction = it },
                                            onNotInterested = {
                                                interactionViewModel.handleManageAction(it) { success, msg ->
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                    if (success) onBack()
                                                }
                                            },
                                            onOther = {
                                                interactionViewModel.handleManageAction(it) { _, msg ->
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        )
                                    },
                                    onLoadReplyInteractionStatus = { oid, type, onLoaded ->
                                        interactionViewModel.loadReplyInteractionStatus(oid, type, onLoaded)
                                    },
                                    onDeleteClick = { action ->
                                        interactionViewModel.deleteDynamic(action) { success, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            if (success) onBack()
                                        }
                                    },
                                ),
                            ),
                            presentation = DynamicCardPresentation(
                                isDetail = true,
                                isLiked = likedDynamics.contains(state.item.id_str),
                                likeOverride = likeOverrides[state.item.id_str],
                                forwardCountDelta = forwardCountDelta,
                                detailImageLayout = effectiveDetailImageLayout,
                            ),
                        )
                    }
                }
                val commentContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                    item(key = "dynamic_detail_comment_header") {
                        DynamicInlineCommentHeader(
                            totalCount = commentTotalCount,
                            onShowReactions = { reactionItem = state.item },
                            sortMode = commentSortMode,
                            onSortModeChange = interactionViewModel::setDynamicCommentSortMode,
                        )
                    }
                    dynamicInlineCommentItems(
                        avatarListState = (if (useSplitLayout) commentListState else detailListState)
                            .takeIf { avatarPushEnabled },
                        comments = comments,
                        isLoading = commentsLoading,
                        isLoadingMore = commentsLoadingMore,
                        onViewReplies = { reply, targetReplyId ->
                            val target = if (targetReplyId > 0L || reply.rpid != commentUiState.pinnedRootReplyId) {
                                targetReplyId
                            } else {
                                commentUiState.pinnedTargetReplyId
                            }
                            interactionViewModel.openSubReply(reply, target)
                        },
                        onReply = { reply -> interactionViewModel.startCommentReply(reply) },
                        onLike = { reply -> interactionViewModel.likeComment(reply.rpid) },
                        onHate = { reply ->
                            interactionViewModel.hateComment(reply.rpid) { _, message ->
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        dynamicAuthorMid = state.item.modules.module_author?.mid ?: 0L,
                        currentUserMid = com.android.purebilibili.core.store.TokenManager.midCache,
                        onDelete = { reply ->
                            interactionViewModel.deleteDynamicComment(reply.rpid) { _, message ->
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onToggleTop = { reply ->
                            interactionViewModel.toggleDynamicCommentTop(reply) { _, message ->
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onReport = { reply, reason ->
                            interactionViewModel.reportDynamicComment(reply.rpid, reason) { _, message ->
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUserClick = onUserClick,
                        onImagePreview = { images, index, sourceRect, textContent ->
                            previewImages = images
                            previewInitialIndex = index
                            previewSourceRect = sourceRect
                            previewTextContent = textContent
                            showImagePreview = true
                        },
                        skeletonSlotCount = commentSkeletonSlotCount,
                    )
                }
                val commentComposer: @Composable (Modifier) -> Unit = { modifier ->
                    DynamicCommentEntryBar(
                        onClick = interactionViewModel::openCommentComposer,
                        liquidGlassEnabled = liquidGlassEnabled,
                        backdrop = detailCommentBackdrop,
                        modifier = modifier,
                    )
                }
                val floatingCommentComposer = shouldUseFloatingLiquidBottomInputBar(
                    androidNativeLiquidGlassEnabled = liquidGlassEnabled,
                )
                val commentContentBottomPadding = resolveBottomInputBarContentBottomPadding(
                    showBar = true,
                    floatingLiquidGlass = floatingCommentComposer,
                    showActionButtonsFallback = false,
                )

                val coveredBlurProgress = if (subReplyState.visible) subReplyCoveredBlurProgress else 0f
                val coveredBlurEffectCache = remember { BlurRenderEffectCache() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            renderEffect = null
                            if (coveredBlurProgress > 0f &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ) {
                                val blurFrame = resolvePredictiveBackBlurFrame(
                                    progress = coveredBlurProgress,
                                )
                                renderEffect = if (blurFrame.blurRadiusPx > 0.5f) {
                                    coveredBlurEffectCache.resolve(blurFrame.blurRadiusPx)
                                } else {
                                    null
                                }
                            }
                        }
                ) {
                    if (useSplitLayout) {
                        //  [新增] 大屏/横屏：左卡片 + 右评论（对齐 BiliPai 横屏分栏）
                        AppSplitLayout(
                            primaryRatio = 0.5f,
                            modifier = Modifier
                                .padding(bottom = paddingValues.calculateBottomPadding())
                                .consumeWindowInsets(paddingValues),
                            primaryContent = {
                                LazyColumn(
                                    state = detailListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .responsiveContentWidth(maxWidth = resolveDynamicFeedMaxWidth()),
                                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall)
                                ) {
                                    cardContent()
                                }
                            },
                            secondaryContent = {
                                if (floatingCommentComposer) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AdaptivePullToRefreshBox(
                                            isRefreshing = commentsRefreshing,
                                            onRefresh = interactionViewModel::refreshComments,
                                            modifier = Modifier.fillMaxSize(),
                                            indicatorTopInset = paddingValues.calculateTopPadding(),
                                        ) {
                                            LazyColumn(
                                                state = commentListState,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(
                                                        if (detailCommentBackdrop != null) {
                                                            detailCommentSourceModifier
                                                        } else {
                                                            Modifier
                                                        }
                                                    ),
                                                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = commentContentBottomPadding),
                                            ) {
                                                commentContent()
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .imePadding()
                                                .padding(horizontal = AppSpacingTokens.ExtraLarge)
                                                .padding(bottom = AppSpacingTokens.Medium),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            commentComposer(
                                                Modifier
                                                    .widthIn(max = 360.dp)
                                                    .fillMaxWidth(),
                                            )
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AdaptivePullToRefreshBox(
                                            isRefreshing = commentsRefreshing,
                                            onRefresh = interactionViewModel::refreshComments,
                                            modifier = Modifier.fillMaxSize(),
                                            indicatorTopInset = paddingValues.calculateTopPadding(),
                                        ) {
                                            LazyColumn(
                                                state = commentListState,
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = commentContentBottomPadding),
                                            ) {
                                                commentContent()
                                            }
                                        }
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .imePadding()
                                        ) {
                                            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            AppSurface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = resolveDynamicDetailBottomBarColor(LocalAppUiStyle.current, MaterialTheme.colorScheme),
                                                tonalElevation = 0.dp,
                                                shadowElevation = 0.dp,
                                            ) {
                                                commentComposer(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            horizontal = AppSpacingTokens.Large,
                                                            vertical = AppSpacingTokens.Medium,
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = paddingValues.calculateBottomPadding())
                                .consumeWindowInsets(paddingValues)
                                .responsiveContentWidth(maxWidth = resolveDynamicFeedMaxWidth())
                        ) {
                            AdaptivePullToRefreshBox(
                                isRefreshing = commentsRefreshing,
                                onRefresh = interactionViewModel::refreshComments,
                                modifier = Modifier.fillMaxSize(),
                                indicatorTopInset = paddingValues.calculateTopPadding(),
                            ) {
                                LazyColumn(
                                    state = detailListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (floatingCommentComposer && detailCommentBackdrop != null) {
                                                detailCommentSourceModifier
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = commentContentBottomPadding),
                                ) {
                                    cardContent()
                                    commentContent()
                                }
                            }
                            if (floatingCommentComposer) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .imePadding()
                                        .padding(horizontal = AppSpacingTokens.ExtraLarge)
                                        .padding(bottom = AppSpacingTokens.Medium),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    commentComposer(
                                        Modifier
                                            .widthIn(max = 360.dp)
                                            .fillMaxWidth(),
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .imePadding()
                                ) {
                                    AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    AppSurface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = resolveDynamicDetailBottomBarColor(LocalAppUiStyle.current, MaterialTheme.colorScheme),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                    ) {
                                        commentComposer(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = AppSpacingTokens.Large,
                                                    vertical = AppSpacingTokens.Medium,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                DynamicSubReplyPreviewHost(
                    state = subReplyState,
                    onDismiss = interactionViewModel::closeSubReply,
                    onLoadMore = interactionViewModel::loadMoreSubReplies,
                    onRefresh = interactionViewModel::refreshSubReplies,
                    onSortModeChange = interactionViewModel::setSubReplySortMode,
                    onUserClick = onUserClick,
                    onReplyClick = { reply ->
                        // 保持楼中楼可见，避免输入弹层出现前先闪回主评论列表。
                        interactionViewModel.startCommentReply(reply)
                    },
                    onCommentLike = { rpid -> interactionViewModel.likeComment(rpid) },
                    onCommentHate = { rpid ->
                        interactionViewModel.hateComment(rpid) { _, message ->
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    currentMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L,
                    onDeleteComment = { rpid ->
                        interactionViewModel.deleteDynamicComment(rpid) { _, message ->
                            android.widget.Toast.makeText(
                                context,
                                message,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onCoveredBlurProgressChange = { progress ->
                        subReplyCoveredBlurProgress = progress
                    },
                )

                val postComment: (String, List<Uri>, (Boolean) -> Unit) -> Unit = { message, images, onResult ->
                    interactionViewModel.postComment(state.item.id_str, message, images) { success, toastMessage ->
                        android.widget.Toast.makeText(context, toastMessage, android.widget.Toast.LENGTH_SHORT).show()
                        onResult(success)
                    }
                }
                DynamicCommentInputHost(
                    draftKeyPrefix = "dynamic-detail:${state.item.id_str}",
                    viewModel = interactionViewModel,
                    onPostComment = postComment,
                )

                if (showImagePreview && previewImages.isNotEmpty()) {
                    ImagePreviewDialog(
                        images = previewImages,
                        initialIndex = previewInitialIndex,
                        sourceRect = previewSourceRect?.rect,
                        sourceRects = previewSourceRect?.galleryRects.orEmpty(),
                        sourceKey = previewSourceRect?.sourceKey,
                        sourceCornerRadiusDp = previewSourceRect?.cornerRadiusDp
                            ?: AppShapes.containerCornerDp(ContainerLevel.Field).value,
                        textContent = previewTextContent,
                        onDismiss = {
                            showImagePreview = false
                            previewTextContent = null
                        },
                    )
                }

                showRepostDialog?.let { repostDynamicId ->
                    RepostDialog(
                        contentKey = repostDynamicId,
                        onDismiss = { showRepostDialog = null },
                        onRepost = { content, alsoComment, richContents, topic, onComplete ->
                            interactionViewModel.repostDynamic(repostDynamicId, content, alsoComment, richContents, topic) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (success) {
                                    forwardCountDelta++
                                    showRepostDialog = null
                                }
                                onComplete(success)
                            }
                        }
                    )
                }

                editingAction?.let { action ->
                    var submitting by remember(action.dynamicId) { mutableStateOf(false) }
                    var errorMessage by remember(action.dynamicId) { mutableStateOf<String?>(null) }
                    DynamicPublishComposer(
                        initialDraft = action.initialDraft,
                        isEditing = true,
                        submitting = submitting,
                        errorMessage = errorMessage,
                        replyOid = action.replyOid,
                        replyType = action.replyType,
                        onDismiss = { editingAction = null },
                        onSubmit = { draft ->
                            submitting = true
                            interactionViewModel.editDynamic(context, action.dynamicId, draft) { success, msg ->
                                submitting = false
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (success) {
                                    editingAction = null
                                    detailViewModel.load(dynamicId, loadFailedMessage, force = true)
                                } else {
                                    errorMessage = msg
                                }
                            }
                        },
                    )
                }

                pendingReport?.let { reportAction ->
                    var selectedReason by remember(reportAction.dynamicId) {
                        mutableStateOf(resolveDynamicReportReasons().first())
                    }
                    var otherDesc by remember(reportAction.dynamicId) { mutableStateOf("") }
                    AppAlertDialog(
                        onDismissRequest = { pendingReport = null },
                        title = { AppText("举报动态") },
                        text = {
                            Column {
                                resolveDynamicReportReasons().forEach { reason ->
                                    AppListItem(
                                        headlineContent = { AppText(reason.label) },
                                        trailingContent = {
                                            AppRadioButton(
                                                selected = reason.type == selectedReason.type,
                                                onClick = { selectedReason = reason },
                                            )
                                        },
                                        modifier = Modifier.clickable { selectedReason = reason },
                                    )
                                }
                                if (selectedReason.type == 0) {
                                    AppTextField(
                                        value = otherDesc,
                                        onValueChange = { otherDesc = it },
                                        placeholder = "补充详细说明",
                                        singleLine = false,
                                        minLines = 2,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            AppDialogAction(onClick = {
                                interactionViewModel.reportDynamic(
                                    action = reportAction,
                                    reasonType = selectedReason.type,
                                    reasonDesc = otherDesc,
                                ) { _, msg ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    pendingReport = null
                                }
                            }) { AppText("提交") }
                        },
                        dismissButton = {
                            AppDialogAction(onClick = { pendingReport = null }) { AppText("取消") }
                        },
                    )
                }

                pendingMessageShare?.let { shareItem ->
                    DynamicShareToMessageDialog(
                        item = shareItem,
                        onDismiss = { pendingMessageShare = null },
                        onResult = { _, message ->
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
    }
}
