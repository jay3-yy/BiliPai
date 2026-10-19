// 文件路径: feature/bangumi/ui/player/BangumiPlayerContent.kt
package com.android.purebilibili.feature.bangumi.ui.player

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.rememberAppCheckCircleIcon
import com.android.purebilibili.core.ui.rememberAppProfileAddIcon
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.resolveAdaptivePrimaryAccentColors
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.data.model.response.EmotePackage
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.feature.bangumi.BANGUMI_FOLLOW_STATUS_OPTIONS
import com.android.purebilibili.feature.bangumi.BANGUMI_FOLLOW_STATUS_UNFOLLOW
import com.android.purebilibili.feature.bangumi.BANGUMI_FOLLOW_STATUS_WATCHING
import com.android.purebilibili.feature.bangumi.isBangumiFollowed
import com.android.purebilibili.feature.bangumi.resolveBangumiFollowStatusLabel
import com.android.purebilibili.feature.video.ui.components.CommentFraudFeedbackHost
import com.android.purebilibili.feature.video.screen.VideoContentTabBar
import com.android.purebilibili.feature.video.ui.components.SubReplySheet
import com.android.purebilibili.feature.video.ui.components.commentThreadNavigationBlur
import com.android.purebilibili.feature.video.ui.components.CommentInputDialog
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.viewmodel.commentComposerDraftKey
import androidx.compose.ui.geometry.Rect
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 番剧播放内容区域
 */
@Composable
fun BangumiPlayerContent(
    detail: BangumiDetail,
    currentEpisode: BangumiEpisode,
    commentViewModel: VideoCommentViewModel,
    onEpisodeClick: (BangumiEpisode) -> Unit,
    onFollowStatusSelect: (Int) -> Unit,
    onUserClick: ((Long) -> Unit)? = null,
    onCommentUrlClick: ((String) -> Unit)? = null,
    onDownloadClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    commentsOnly: Boolean = false,
    isVideoPlaying: Boolean = false,
    handleCommentFeedback: Boolean = true,
    onTimestampClick: ((Long) -> Unit)? = null,
    currentVideoPositionMsProvider: () -> Long = { 0L }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    CommentFraudFeedbackHost(commentViewModel = commentViewModel, enabled = handleCommentFeedback)
    val showIdentityDecorations by com.android.purebilibili.core.store.SettingsManager
        .getCommentMemberDecorationsEnabled(context).collectAsStateWithLifecycle(initialValue = false)
    val isCourse = detail.seasonType == 10
    val isFollowing = isBangumiFollowed(detail.userStatus)
    val followedIcon = rememberAppCheckCircleIcon()
    val followIcon = rememberAppProfileAddIcon()
    var showFollowStatusDialog by remember { mutableStateOf(false) }
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val pagerState = rememberPagerState(pageCount = { if (commentsOnly) 1 else 2 })
    val scope = rememberCoroutineScope()
    val selectionBackdrop = rememberLayerBackdrop()
    val indicatorPositionProvider = remember(pagerState) {
        { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val subReplyState by commentViewModel.subReplyState.collectAsStateWithLifecycle()
    val subReplyCoveredBlurProgress = remember(currentEpisode.id) { mutableFloatStateOf(0f) }
    val commentState by commentViewModel.commentState.collectAsStateWithLifecycle()
    val showCommentCount by com.android.purebilibili.core.store.SettingsManager
        .getShowVideoDetailCommentCount(context).collectAsStateWithLifecycle(initialValue = true)
    val commentTabTitle = if (showCommentCount) {
        "评论 ${FormatUtils.formatStat(commentState.replyCount.coerceAtLeast(0).toLong())}"
    } else {
        "评论"
    }
    val tabs = if (commentsOnly) listOf(commentTabTitle) else listOf("简介", commentTabTitle)
    val commentListState = key(currentEpisode.id) { rememberLazyListState() }

    var commentInputVisible by rememberSaveable(currentEpisode.id) { mutableStateOf(false) }
    var emotePackages by remember { mutableStateOf<List<EmotePackage>>(emptyList()) }
    LaunchedEffect(commentInputVisible) {
        if (commentInputVisible && emotePackages.isEmpty()) {
            emotePackages = CommentRepository.getEmotePackages().getOrDefault(emptyList())
        }
    }
    var previewImages by remember(currentEpisode.id) { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember(currentEpisode.id) { mutableIntStateOf(0) }
    var previewSourceRect by remember(currentEpisode.id) { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember(currentEpisode.id) { mutableStateOf<ImagePreviewTextContent?>(null) }

    LaunchedEffect(
        currentEpisode.id,
        commentState.composerDrafts.sessionId,
        commentState.composerDrafts.completedSessionId
    ) {
        if (commentState.composerDrafts.completedSessionId == commentState.composerDrafts.sessionId) {
            commentInputVisible = false
        }
    }

    fun openRootCommentComposer() {
        commentViewModel.cancelReply()
        commentViewModel.beginCommentDraft()
        commentInputVisible = true
    }

    fun openReplyComposer(reply: com.android.purebilibili.data.model.response.ReplyItem) {
        commentViewModel.replyTo(reply)
        commentViewModel.beginCommentDraft()
        commentInputVisible = true
    }

    val tabBarHeightPx = remember { mutableIntStateOf(0) }
    val tabBarContent: @Composable (Modifier) -> Unit = { tabModifier ->
        VideoContentTabBar(
            tabs = tabs,
            replyCount = commentState.replyCount,
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch { animatePagerSelection(pagerState, index) }
            },
            sortMode = commentState.sortMode,
            onSortModeChange = commentViewModel::setSortMode,
            onDanmakuSendClick = {},
            danmakuEnabled = false,
            onDanmakuToggle = {},
            showDanmakuActions = false,
            modifier = tabModifier,
            miuixBackdrop = if (liquidGlassEnabled) selectionBackdrop else null,
            indicatorPositionProvider = indicatorPositionProvider,
            isScrollInProgressProvider = { pagerState.isScrollInProgress },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 玻璃采样必须覆盖标签坐标并包含实色背景，且不能录入玻璃控件自身。
                // 原生 MD3/Miuix 不增加采样层，仍按 Column 顺序放置标签与分页内容。
                .then(
                    if (liquidGlassEnabled) {
                        Modifier
                            .layerBackdrop(selectionBackdrop)
                            .background(MaterialTheme.colorScheme.background)
                    } else Modifier
                )
                .commentThreadNavigationBlur {
                    if (subReplyState.visible) subReplyCoveredBlurProgress.floatValue else 0f
                }
        ) {
        if (!commentsOnly) {
            if (liquidGlassEnabled) {
                // 只保留布局占位；玻璃标签在采样层外作为兄弟覆盖层绘制。
                Spacer(Modifier.layout { _, constraints ->
                    layout(
                        constraints.maxWidth,
                        tabBarHeightPx.intValue.coerceIn(constraints.minHeight, constraints.maxHeight),
                    ) {}
                })
            } else {
                tabBarContent(Modifier.fillMaxWidth())
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !commentsOnly,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (if (commentsOnly) 1 else page) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
        // 标题和信息
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                // UP 主信息（课堂/课程或合作视频）
                detail.upInfo?.let { up ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onUserClick != null && up.mid > 0L) {
                                onUserClick?.invoke(up.mid)
                            }
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = FormatUtils.fixImageUrl(up.avatar),
                            contentDescription = up.uname,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                text = up.uname,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subText = if (up.follower > 0L) {
                                "${FormatUtils.formatStat(up.follower)}粉丝"
                            } else {
                                up.brief.orEmpty()
                            }
                            if (subText.isNotBlank()) {
                                AppText(
                                    text = subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (detail.cooperators.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(bottom = 10.dp)
                    ) {
                        items(detail.cooperators, key = { it.mid }) { cooperator ->
                            Row(
                                modifier = Modifier
                                    .sizeIn(minHeight = 48.dp)
                                    .clickable(enabled = cooperator.mid > 0L && onUserClick != null) {
                                        onUserClick?.invoke(cooperator.mid)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = FormatUtils.fixImageUrl(cooperator.avatar),
                                    contentDescription = cooperator.uname,
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    AppText(
                                        text = cooperator.uname,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    cooperator.role?.takeIf { it.isNotBlank() }?.let {
                                        AppText(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppContentColors.secondaryText()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AppText(
                    text = detail.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                if (detail.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = detail.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val currentPlayingLabel = listOf(currentEpisode.title, currentEpisode.longTitle)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                AppText(
                    text = "正在播放：$currentPlayingLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.accent(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (isCourse) {
                    val accessLabel = when {
                        currentEpisode.playable || currentEpisode.episodeCanView -> "可试看"
                        detail.hasPaid -> "已购买"
                        else -> "购买后观看"
                    }
                    AppText(
                        text = accessLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (currentEpisode.playable || currentEpisode.episodeCanView) {
                            AppContentColors.accent()
                        } else {
                            AppContentColors.secondaryText()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                detail.stat?.let { stat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statText = if (isCourse) {
                            val playText = "${FormatUtils.formatStat(stat.views)}播放"
                            val favText = if (stat.favorites > 0L) " · ${FormatUtils.formatStat(stat.favorites)}收藏" else ""
                            playText + favText
                        } else {
                            "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.danmakus)}弹幕"
                        }
                        AppText(
                            text = statText,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText()
                        )
                    }
                }
            }
        }

        // 追番/收藏操作
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppButton(
                    onClick = {
                        if (isCourse) {
                            if (isFollowing) {
                                onFollowStatusSelect(BANGUMI_FOLLOW_STATUS_UNFOLLOW)
                            } else {
                                onFollowStatusSelect(BANGUMI_FOLLOW_STATUS_WATCHING)
                            }
                        } else {
                            if (isFollowing) {
                                showFollowStatusDialog = true
                            } else {
                                onFollowStatusSelect(BANGUMI_FOLLOW_STATUS_WATCHING)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (isFollowing) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = AppContentColors.secondaryText(),
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),
                            contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                        )
                    }
                ) {
                    AppIcon(
                        if (isFollowing) followedIcon else followIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val followLabel = if (isCourse) {
                        if (isFollowing) "已收藏" else "收藏"
                    } else {
                        resolveBangumiFollowStatusLabel(detail.userStatus)
                    }
                    AppText(followLabel)
                }
            }
        }

        if (isCourse) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = AppContentColors.secondaryText()
                        )
                    ) {
                        AppText("下载当前集")
                    }
                    AppButton(
                        onClick = onShareClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = AppContentColors.secondaryText()
                        )
                    ) {
                        AppText("分享课程")
                    }
                }
            }
        }

        // 剧集选择
        if (!detail.episodes.isNullOrEmpty()) {
            item {
                AppHorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // 选集标题和快速跳转
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppText(
                        text = "选集 (${detail.episodes.orEmpty().size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // 当集数超过 50 时显示快速跳转
                    if (detail.episodes.orEmpty().size > 50) {
                        var showJumpDialog by remember { mutableStateOf(false) }

                        AppSurface(
                            onClick = { showJumpDialog = true },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = AppShapes.container(ContainerLevel.Sheet)
                        ) {
                            AppText(
                                text = "跳转",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = AppContentColors.secondaryText()
                            )
                        }

                        // 快速跳转对话框
                        if (showJumpDialog) {
                            EpisodeJumpDialog(
                                totalEpisodes = detail.episodes.orEmpty().size,
                                onJump = { epNumber ->
                                    val targetEpisode = detail.episodes.orEmpty().getOrNull(epNumber - 1)
                                    if (targetEpisode != null) {
                                        onEpisodeClick(targetEpisode)
                                    }
                                    showJumpDialog = false
                                },
                                onDismiss = { showJumpDialog = false }
                            )
                        }
                    }
                }
            }

            // 对于超长剧集，添加范围选择器
            if (detail.episodes.orEmpty().size > 50) {
                item {
                    val episodesPerPage = 50
                    val totalPages = (detail.episodes.orEmpty().size + episodesPerPage - 1) / episodesPerPage
                    var selectedPage by remember { mutableIntStateOf(0) }

                    // 当前集所在的页
                    val currentEpisodeIndex = detail.episodes.orEmpty().indexOfFirst { it.id == currentEpisode.id }
                    LaunchedEffect(currentEpisodeIndex) {
                        if (currentEpisodeIndex >= 0) {
                            selectedPage = currentEpisodeIndex / episodesPerPage
                        }
                    }

                    // 范围选择器
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(totalPages, key = { it }) { page ->
                            val start = page * episodesPerPage + 1
                            val end = minOf((page + 1) * episodesPerPage, detail.episodes.orEmpty().size)
                            val isCurrentPage = page == selectedPage

                            AppSurface(
                                onClick = { selectedPage = page },
                                color = if (isCurrentPage) AppContentColors.accent() else MaterialTheme.colorScheme.surfaceVariant,
                                shape = AppShapes.container(ContainerLevel.Dialog)
                            ) {
                                AppText(
                                    text = "$start-$end",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrentPage) AppContentColors.onAccent() else AppContentColors.secondaryText()
                                )
                            }
                        }
                    }

                    // 当前页的剧集
                    val pageStart = selectedPage * episodesPerPage
                    val pageEnd = minOf(pageStart + episodesPerPage, detail.episodes.orEmpty().size)
                    val pageEpisodes = detail.episodes.orEmpty().subList(pageStart, pageEnd)

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageEpisodes, key = { it.id }) { episode ->
                            EpisodeChipSelectable(
                                episode = episode,
                                isSelected = episode.id == currentEpisode.id,
                                isCourse = isCourse,
                                onClick = { onEpisodeClick(episode) }
                            )
                        }
                    }
                }
            } else {
                // 普通剧集列表
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detail.episodes.orEmpty(), key = { it.id }) { episode ->
                            EpisodeChipSelectable(
                                episode = episode,
                                isSelected = episode.id == currentEpisode.id,
                                isCourse = isCourse,
                                onClick = { onEpisodeClick(episode) }
                            )
                        }
                    }
                }
            }
        }

        // 简介
        if (detail.evaluate.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                AppText(
                    text = "简介",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AppText(
                    text = detail.evaluate,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText()
                )
            }
        }

        // 课程概述图片 (PUGV brief images)
        if (!detail.briefImgs.isNullOrEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                AppText(
                    text = "课程概述",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.briefImgs.orEmpty().forEach { briefImg ->
                        if (briefImg.url.isNotBlank()) {
                            val ratio = (1f / briefImg.aspectRatio.coerceAtLeast(0.1f)).coerceIn(0.2f, 5f)
                            AsyncImage(
                                model = FormatUtils.resolveVideoCoverUrl(
                                    briefImg.url,
                                    useLowQuality = false
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ratio)
                                    .clip(AppShapes.container(ContainerLevel.Card)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }
        }
                }

                1 -> {
                    val hasComments = if (isCourse) (currentEpisode.id > 0L || currentEpisode.aid > 0L) else currentEpisode.aid > 0L
                    if (hasComments) {
                        // 剧集切换后重建列表和搜索状态，不沿用上一集的滚动位置。
                        key(currentEpisode.id) {
                            BangumiCommentPanel(
                                viewModel = commentViewModel,
                                listState = commentListState,
                                modifier = Modifier.fillMaxSize(),
                                isVideoPlaying = isVideoPlaying,
                                showIdentityDecorations = showIdentityDecorations,
                                onRootCommentClick = ::openRootCommentComposer,
                                onReplyClick = ::openReplyComposer,
                                onUserClick = onUserClick ?: {},
                                onCommentUrlClick = { url -> onCommentUrlClick?.invoke(url) },
                                onTimestampClick = onTimestampClick,
                                maxTimestampMs = currentEpisode.duration.takeIf { it > 0L },
                                onImagePreview = { images, index, rect, textContent ->
                                    previewImages = images
                                    previewIndex = index
                                    previewSourceRect = rect
                                    previewTextContent = textContent
                                },
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AppText(
                                text = "当前剧集暂无评论区",
                                color = AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }
        }
        }
        // 玻璃标签不能录入自己的采样层，必须作为 Column 的兄弟节点绘制。
        if (liquidGlassEnabled && !commentsOnly) {
            tabBarContent(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .onSizeChanged { tabBarHeightPx.intValue = it.height }
                    .commentThreadNavigationBlur {
                        if (subReplyState.visible) subReplyCoveredBlurProgress.floatValue else 0f
                    }
            )
        }
    }

    if (showFollowStatusDialog) {
        AppAlertDialog(
            onDismissRequest = { showFollowStatusDialog = false },
            title = { AppText("追番状态") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BANGUMI_FOLLOW_STATUS_OPTIONS.forEach { option ->
                        AppSingleChoiceRow(
                            selected = detail.userStatus?.followStatus == option.status,
                            onClick = {
                                showFollowStatusDialog = false
                                onFollowStatusSelect(option.status)
                            },
                            shape = AppShapes.container(ContainerLevel.Chip),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppText(
                                text = option.label,
                                color = AppSurfaceTokens.onSurfaceContainerHigh(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        showFollowStatusDialog = false
                        onFollowStatusSelect(BANGUMI_FOLLOW_STATUS_UNFOLLOW)
                    }
                ) {
                    AppText("取消追番")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showFollowStatusDialog = false }) {
                    AppText("关闭")
                }
            }
        )
    }

    SubReplySheet(
        state = subReplyState,
        emoteMap = emptyMap(),
        onDismiss = commentViewModel::closeSubReply,
        onCoveredBlurProgressChange = { subReplyCoveredBlurProgress.floatValue = it },
        onLoadMore = commentViewModel::loadMoreSubReplies,
        onRefresh = commentViewModel::refreshSubReplies,
        onSortModeChange = commentViewModel::setSubReplySortMode,
        onCommentLike = commentViewModel::likeComment,
        onCommentHate = commentViewModel::hateComment,
        hatedComments = commentState.hatedComments,
        onDeleteComment = commentViewModel::deleteComment,
        onDissolveStart = commentViewModel::startDissolve,
        showIdentityDecorations = showIdentityDecorations,
        onAvatarClick = { mid -> mid.toLongOrNull()?.let { onUserClick?.invoke(it) } },
        likedComments = commentState.likedComments,
        currentMid = commentState.currentMid,
        showUpFlag = commentState.showUpFlag,
        onReplyClick = ::openReplyComposer,
        onRootCommentClick = ::openRootCommentComposer,
        onUrlClick = { url -> onCommentUrlClick?.invoke(url) },
        onTimestampClick = onTimestampClick,
        onImagePreview = { images, index, rect, textContent ->
            previewImages = images
            previewIndex = index
            previewSourceRect = rect
            previewTextContent = textContent
        }
    )

    if (previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewIndex,
            sourceRect = previewSourceRect?.rect,
            sourceRects = previewSourceRect?.galleryRects.orEmpty(),
            sourceCornerRadiusDp = previewSourceRect?.cornerRadiusDp
                ?: AppShapes.containerCornerDp(ContainerLevel.Field).value,
            textContent = previewTextContent,
            onDismiss = { previewImages = emptyList() }
        )
    }

    val composerSessionId = commentState.composerDrafts.sessionId
    val composerReplyRpid = commentState.replyTarget?.rpid
    val composerDraft = commentState.composerDrafts.drafts[commentComposerDraftKey(composerReplyRpid)]
    val composerKey = remember(currentEpisode.id, currentEpisode.aid, isCourse, composerReplyRpid, composerSessionId) {
        "bangumi:${if (isCourse) currentEpisode.id else currentEpisode.aid}:${if (isCourse) 33 else 1}:$composerReplyRpid:$composerSessionId"
    }
    CommentInputDialog(
        visible = commentInputVisible,
        draftKey = composerKey,
        onDismiss = {
            if (commentViewModel.commentState.value.composerDrafts.sessionId == composerSessionId) {
                commentInputVisible = false
                commentViewModel.cancelReply()
            }
        },
        onSend = { message, imageUris, syncToDynamic ->
            val latest = commentViewModel.commentState.value
            if (commentInputVisible && latest.composerDrafts.sessionId == composerSessionId &&
                latest.composerDrafts.completedSessionId != composerSessionId &&
                latest.replyTarget?.rpid == composerReplyRpid && !latest.isSending
            ) {
                commentViewModel.updateCommentDraft(
                    composerSessionId, composerReplyRpid, message, imageUris, syncToDynamic
                )
                commentViewModel.sendComment(message, imageUris, syncToDynamic)
            }
        },
        isSending = commentState.isSending,
        replyToName = commentState.replyTarget?.member?.uname,
        inputHint = if (commentState.replyTarget == null) commentState.rootInputHint else commentState.childInputHint,
        canInputComment = commentState.canInputComment,
        initialText = composerDraft?.text.orEmpty(),
        initialImageUris = composerDraft?.imageUris.orEmpty(),
        initialSyncToDynamic = composerDraft?.syncToDynamic ?: false,
        onDraftChange = { text, images, sync ->
            commentViewModel.updateCommentDraft(composerSessionId, composerReplyRpid, text, images, sync)
        },
        currentVideoPositionMsProvider = currentVideoPositionMsProvider,
        emotePackages = emotePackages,
    )
}

/**
 * 可选择的集数卡片
 */
@Composable
fun EpisodeChipSelectable(
    episode: BangumiEpisode,
    isSelected: Boolean,
    isCourse: Boolean = false,
    onClick: () -> Unit
) {
    val selectedColors = resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)

    if (isCourse) {
        AppSurface(
            modifier = Modifier
                .width(180.dp)
                .height(68.dp)
                .clickable(onClick = onClick),
            shape = AppShapes.container(ContainerLevel.Card),
            color = if (isSelected) {
                selectedColors.backgroundColor
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppText(
                        text = episode.title.ifEmpty { "第${episode.id}讲" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) selectedColors.contentColor else AppContentColors.primaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (episode.badge.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val isPreview = episode.playable || episode.episodeCanView
                        val brandColors = com.android.purebilibili.feature.bangumi.bangumiBrandBadgeColors(episode.badge)
                        Box(
                            modifier = Modifier
                                .background(
                                    color = brandColors?.backgroundColor ?: if (isPreview) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            AppText(
                                text = episode.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = brandColors?.contentColor ?: if (isPreview) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (episode.longTitle.isNotBlank()) {
                    AppText(
                        text = episode.longTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            selectedColors.contentColor.copy(alpha = 0.8f)
                        } else {
                            AppContentColors.secondaryText()
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        AppSurface(
            modifier = Modifier.clickable(onClick = onClick),
            shape = AppShapes.container(ContainerLevel.Chip),
            color = if (isSelected) selectedColors.backgroundColor else MaterialTheme.colorScheme.surfaceVariant
        ) {
            AppText(
                text = episode.title.ifEmpty { "第${episode.id}话" },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = if (isSelected) selectedColors.contentColor else AppContentColors.primaryText(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * 快速跳转集数对话框
 */
@Composable
fun EpisodeJumpDialog(
    totalEpisodes: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("跳转到第几集") },
        text = {
            Column {
                AppOutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it.filter { char -> char.isDigit() }
                        errorMessage = null
                    },
                    label = { AppText("集数 (1-$totalEpisodes)") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    AppText(
                        text = errorMessage!!,
                        color = AppContentColors.error(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = {
                    val epNumber = inputText.toIntOrNull()
                    if (epNumber == null || epNumber < 1 || epNumber > totalEpisodes) {
                        errorMessage = "请输入 1-$totalEpisodes 之间的数字"
                    } else {
                        onJump(epNumber)
                    }
                }
            ) {
                AppText("跳转")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("取消")
            }
        }
    )
}

/**
 * 错误内容显示
 */
@Composable
fun BangumiErrorContent(
    message: String,
    isVipRequired: Boolean,
    isLoginRequired: Boolean = false,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onLogin: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // 根据错误类型显示不同图标
            AppText(
                text = when {
                    isVipRequired -> "👑"
                    isLoginRequired -> ""
                    else -> ""
                },
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            AppText(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = AppContentColors.primaryText(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (isVipRequired) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    text = "开通大会员即可观看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.accent()
                )
            }
            // 登录按钮
            if (isLoginRequired) {
                Spacer(modifier = Modifier.height(24.dp))
                AppButton(
                    onClick = onLogin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                        contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                    )
                ) {
                    AppText("去登录")
                }
            }
            if (canRetry) {
                Spacer(modifier = Modifier.height(if (isLoginRequired) 12.dp else 24.dp))
                if (isLoginRequired) {
                    AppTextButton(onClick = onRetry) { AppText("重试") }
                } else {
                    AppButton(onClick = onRetry) { AppText("重试") }
                }
            }
        }
    }
}
