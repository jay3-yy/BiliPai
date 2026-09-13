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
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.theme.resolveAdaptivePrimaryAccentColors
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
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onFollowStatusSelect: (Int) -> Unit
) {
    val isFollowing = isBangumiFollowed(detail.userStatus)
    val followedIcon = rememberAppCheckCircleIcon()
    val followIcon = rememberAppProfileAddIcon()
    var showFollowStatusDialog by remember { mutableStateOf(false) }
    val tabs = listOf("简介", "评论")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val selectionBackdrop = rememberLayerBackdrop()
    val indicatorPositionProvider = remember(pagerState) {
        { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val subReplyState by commentViewModel.subReplyState.collectAsStateWithLifecycle()
    val commentState by commentViewModel.commentState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(selectionBackdrop)
                .background(MaterialTheme.colorScheme.background),
        )
        Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppThemeAdaptiveTabRow(
                options = tabs.mapIndexed { index, label -> AppSegmentOption(index, label) },
                selectedValue = pagerState.currentPage,
                onSelectionChange = { index ->
                    scope.launch { animatePagerSelection(pagerState, index) }
                },
                modifier = Modifier.fillMaxWidth(0.4f),
                height = 44.dp,
                indicatorHeight = com.android.purebilibili.core.ui
                    .roundMatchedLiquidIndicatorHeightDp(44f).dp,
                labelFontSize = 15.sp,
                dragSelectionEnabled = tabs.size > 1,
                tapPressRefractionEnabled = false,
                miuixBackdrop = selectionBackdrop,
                indicatorPositionProvider = indicatorPositionProvider,
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
            )
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f)
                .verticalPriorityHorizontalPagerSwipe(
                    state = pagerState,
                    enabled = true,
                )
        ) { page ->
            when (page) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
        // 标题和信息
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                AppText(
                    text = detail.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                AppText(
                    text = "正在播放：${currentEpisode.title} ${currentEpisode.longTitle}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                detail.stat?.let { stat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            text = "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.danmakus)}弹幕",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 追番操作
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppButton(
                    onClick = {
                        if (isFollowing) {
                            showFollowStatusDialog = true
                        } else {
                            onFollowStatusSelect(BANGUMI_FOLLOW_STATUS_WATCHING)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (isFollowing) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),
contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme))
                    }
                ) {
                    AppIcon(
                        if (isFollowing) followedIcon else followIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    AppText(resolveBangumiFollowStatusLabel(detail.userStatus))
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
                        text = "选集 (${detail.episodes.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    // 当集数超过 50 时显示快速跳转
                    if (detail.episodes.size > 50) {
                        var showJumpDialog by remember { mutableStateOf(false) }
                        
                        AppSurface(
                            onClick = { showJumpDialog = true },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = AppShapes.container(ContainerLevel.Sheet)
                        ) {
                            AppText(
                                text = "跳转",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // 快速跳转对话框
                        if (showJumpDialog) {
                            EpisodeJumpDialog(
                                totalEpisodes = detail.episodes.size,
                                onJump = { epNumber ->
                                    val targetEpisode = detail.episodes.getOrNull(epNumber - 1)
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
            if (detail.episodes.size > 50) {
                item {
                    val episodesPerPage = 50
                    val totalPages = (detail.episodes.size + episodesPerPage - 1) / episodesPerPage
                    var selectedPage by remember { mutableIntStateOf(0) }
                    
                    // 当前集所在的页
                    val currentEpisodeIndex = detail.episodes.indexOfFirst { it.id == currentEpisode.id }
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
                            val end = minOf((page + 1) * episodesPerPage, detail.episodes.size)
                            val isCurrentPage = page == selectedPage
                            
                            AppSurface(
                                onClick = { selectedPage = page },
                                color = if (isCurrentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = AppShapes.container(ContainerLevel.Dialog)
                            ) {
                                AppText(
                                    text = "$start-$end",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    color = if (isCurrentPage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // 当前页的剧集
                    val pageStart = selectedPage * episodesPerPage
                    val pageEnd = minOf(pageStart + episodesPerPage, detail.episodes.size)
                    val pageEpisodes = detail.episodes.subList(pageStart, pageEnd)
                    
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageEpisodes, key = { it.id }) { episode ->
                            EpisodeChipSelectable(
                                episode = episode,
                                isSelected = episode.id == currentEpisode.id,
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
                        items(detail.episodes, key = { it.id }) { episode ->
                            EpisodeChipSelectable(
                                episode = episode,
                                isSelected = episode.id == currentEpisode.id,
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                AppText(
                    text = detail.evaluate,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
                }

                1 -> {
                    if (currentEpisode.aid > 0L) {
                        VideoCommentMainList(
                            viewModel = commentViewModel,
                            showIdentityDecorations = false,
                            onRootCommentClick = {},
                            onReplyClick = {},
                            onUserClick = {},
                            onCommentUrlClick = {},
                            onTimestampClick = null,
                            maxTimestampMs = currentEpisode.duration.takeIf { it > 0L },
                            onImagePreview = { _, _, _, _ -> }
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
                                fontSize = 15.sp,
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
        onLoadMore = commentViewModel::loadMoreSubReplies,
        onSortModeChange = commentViewModel::setSubReplySortMode,
        onCommentLike = commentViewModel::likeComment,
        likedComments = commentState.likedComments,
        currentMid = commentState.currentMid,
        showUpFlag = commentState.showUpFlag,
        onReplyClick = {},
        onRootCommentClick = {}
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
    onClick: () -> Unit
) {
    val selectedColors = resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)

    AppSurface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = AppShapes.container(ContainerLevel.Chip),
        color = if (isSelected) selectedColors.backgroundColor else MaterialTheme.colorScheme.surfaceVariant
    ) {
        AppText(
            text = episode.title.ifEmpty { "第${episode.id}话" },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (isSelected) selectedColors.contentColor else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
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
                        fontSize = 12.sp,
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
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            AppText(
                text = message,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (isVipRequired) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    text = "开通大会员即可观看",
                    fontSize = 14.sp,
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
