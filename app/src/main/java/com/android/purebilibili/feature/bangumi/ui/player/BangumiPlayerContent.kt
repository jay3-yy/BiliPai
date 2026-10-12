// 文件路径: feature/bangumi/ui/player/BangumiPlayerContent.kt
package com.android.purebilibili.feature.bangumi.ui.player

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
import androidx.compose.foundation.lazy.items
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
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
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.feature.bangumi.BANGUMI_FOLLOW_STATUS_OPTIONS
import com.android.purebilibili.feature.bangumi.BANGUMI_FOLLOW_STATUS_UNFOLLOW
import com.android.purebilibili.feature.bangumi.BANGUMI_FOLLOW_STATUS_WATCHING
import com.android.purebilibili.feature.bangumi.isBangumiFollowed
import com.android.purebilibili.feature.bangumi.resolveBangumiFollowStatusLabel
import com.android.purebilibili.feature.video.ui.components.VideoCommentMainList
import com.android.purebilibili.feature.video.ui.components.SubReplySheet
import com.android.purebilibili.feature.video.ui.components.commentThreadNavigationBlur
import com.android.purebilibili.feature.video.ui.components.CommentInputDialog
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import androidx.compose.ui.geometry.Rect
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    onTimestampClick: ((Long) -> Unit)? = null,
    currentVideoPositionMsProvider: () -> Long = { 0L }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val showIdentityDecorations by com.android.purebilibili.core.store.SettingsManager
        .getCommentMemberDecorationsEnabled(context).collectAsStateWithLifecycle(initialValue = false)
    val isCourse = detail.seasonType == 10
    val isFollowing = isBangumiFollowed(detail.userStatus)
    val followedIcon = rememberAppCheckCircleIcon()
    val followIcon = rememberAppProfileAddIcon()
    var showFollowStatusDialog by remember { mutableStateOf(false) }
    val tabs = if (commentsOnly) listOf("评论") else listOf("简介", "评论")
    val useCapsuleTabs = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val selectionBackdrop = rememberLayerBackdrop()
    val indicatorPositionProvider = remember(pagerState) {
        { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val subReplyState by commentViewModel.subReplyState.collectAsStateWithLifecycle()
    val subReplyCoveredBlurProgress = remember(currentEpisode.id) { mutableFloatStateOf(0f) }
    val commentState by commentViewModel.commentState.collectAsStateWithLifecycle()
    var commentInputVisible by rememberSaveable(currentEpisode.id) { mutableStateOf(false) }
    var sendPending by remember(currentEpisode.id) { mutableStateOf(false) }
    var previewImages by remember(currentEpisode.id) { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember(currentEpisode.id) { mutableIntStateOf(0) }
    var previewSourceRect by remember(currentEpisode.id) { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember(currentEpisode.id) { mutableStateOf<ImagePreviewTextContent?>(null) }

    LaunchedEffect(currentEpisode.id, commentState.isSending, commentState.sendError) {
        if (sendPending && !commentState.isSending) {
            if (commentState.sendError == null) commentInputVisible = false
            sendPending = false
        }
    }

    fun openRootCommentComposer() {
        commentViewModel.cancelReply()
        commentInputVisible = true
    }

    fun openReplyComposer(reply: com.android.purebilibili.data.model.response.ReplyItem) {
        commentViewModel.replyTo(reply)
        commentInputVisible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(selectionBackdrop)
                .background(MaterialTheme.colorScheme.background),
        )
        Column(
            modifier = Modifier.fillMaxSize().commentThreadNavigationBlur {
                if (subReplyState.visible) subReplyCoveredBlurProgress.floatValue else 0f
            }
        ) {
        if (!commentsOnly) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppThemeAdaptiveTabRow(
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                options = tabs.mapIndexed { index, label -> AppSegmentOption(index, label) },
                selectedValue = pagerState.currentPage,
                onSelectionChange = { index ->
                    scope.launch { animatePagerSelection(pagerState, index) }
                },
                modifier = Modifier.fillMaxWidth(0.4f),
                height = 44.dp,
                indicatorHeight = com.android.purebilibili.core.ui
                    .roundMatchedLiquidIndicatorHeightDp(44f).dp,
                labelFontSize = MaterialTheme.typography.titleSmall.fontSize,
                compactMiuixWhenTwoOptions = useCapsuleTabs,
                dragSelectionEnabled = tabs.size > 1,
                tapPressRefractionEnabled = false,
                miuixBackdrop = if (liquidGlassEnabled) selectionBackdrop else null,
                indicatorPositionProvider = indicatorPositionProvider,
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
            )
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.primary,
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
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        AppText("下载当前集")
                    }
                    AppButton(
                        onClick = onShareClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = if (isCurrentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = AppShapes.container(ContainerLevel.Dialog)
                            ) {
                                AppText(
                                    text = "$start-$end",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrentPage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        VideoCommentMainList(
                            viewModel = commentViewModel,
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
                            onRefresh = commentViewModel::refreshComments,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AppText(
                                text = "当前剧集暂无评论区",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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

    CommentInputDialog(
        visible = commentInputVisible,
        onDismiss = {
            commentInputVisible = false
            commentViewModel.cancelReply()
        },
        onSend = { message, imageUris, syncToDynamic ->
            sendPending = true
            commentViewModel.sendComment(message, imageUris, syncToDynamic)
        },
        isSending = commentState.isSending,
        replyToName = commentState.replyTarget?.member?.uname,
        inputHint = if (commentState.replyTarget == null) commentState.rootInputHint else commentState.childInputHint,
        canInputComment = commentState.canInputComment,
        currentVideoPositionMsProvider = currentVideoPositionMsProvider
    )
}
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
                        color = if (isSelected) selectedColors.contentColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (episode.badge.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val isPreview = episode.playable || episode.episodeCanView
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isPreview) {
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
                                color = if (isPreview) {
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
                            MaterialTheme.colorScheme.onSurfaceVariant
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
                color = if (isSelected) selectedColors.contentColor else MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.error,
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
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (isVipRequired) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    text = "开通大会员即可观看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
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
