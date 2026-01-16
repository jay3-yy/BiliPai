// 文件路径: feature/video/screen/TabletVideoLayout.kt
package com.android.purebilibili.feature.video.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp // Add this back
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.android.purebilibili.core.ui.AdaptiveSplitLayout
import com.android.purebilibili.core.util.rememberSplitLayoutRatio
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.ui.components.*
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState
import com.android.purebilibili.feature.video.viewmodel.PlayerViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator

//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope

/**
 * 🖥️ 平板端视频详情页布局
 * 
 * 左右分栏布局：
 * - 左侧：视频播放器 + 视频信息
 * - 右侧：评论 / 相关推荐（可切换）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TabletVideoLayout(
    playerState: VideoPlayerState,
    uiState: PlayerUiState,
    commentState: CommentUiState,
    viewModel: PlayerViewModel,
    commentViewModel: VideoCommentViewModel,
    configuration: Configuration,
    isVerticalVideo: Boolean,
    sleepTimerMinutes: Int?,
    viewPoints: List<ViewPoint>,
    bvid: String,
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit,
    onNavigateToAudioMode: () -> Unit,
    onToggleFullscreen: () -> Unit,  // 📺 全屏切换回调
    isInPipMode: Boolean,
    onPipClick: () -> Unit,
    isPortraitFullscreen: Boolean = false,

    // [New] Codec & Audio Params
    currentCodec: String = "hev1", 
    onCodecChange: (String) -> Unit = {},
    currentAudioQuality: Int = -1,
    onAudioQualityChange: (Int) -> Unit = {},
    transitionEnabled: Boolean = false //  卡片过渡动画开关
) {
    val splitRatio = rememberSplitLayoutRatio()
    
    // 🖥️ [修复] 使用 LocalContext 获取 Activity，而非 playerState.context
    val context = LocalContext.current
    val activity = remember(context) {
        (context as? android.app.Activity)
            ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
    }
    
    AdaptiveSplitLayout(
        primaryContent = {
            // 📹 左侧：播放器 + 视频信息（可滚动）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 视频播放器（固定高度，不参与滚动）
                val screenWidthDp = configuration.screenWidthDp.dp
                val videoHeight = (screenWidthDp * splitRatio) * 9f / 16f
                
                //  尝试获取共享元素作用域
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                
                //  为播放器容器添加共享元素标记（受开关控制）
                val playerContainerModifier = if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "video_cover_$bvid"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                //  添加回弹效果的 spring 动画
                                boundsTransform = { _, _ ->
                                    spring(
                                        dampingRatio = 0.7f,   // 轻微回弹
                                        stiffness = 300f       // 适中速度
                                    )
                                },
                                clipInOverlayDuringTransition = OverlayClip(
                                    RoundedCornerShape(0.dp)  //  播放器无圆角
                                )
                            )
                    }
                } else {
                    Modifier
                }

                Box(
                    modifier = playerContainerModifier
                        .fillMaxWidth()
                        .height(videoHeight)
                        .background(Color.Black)
                ) {
                    VideoPlayerSection(
                        playerState = playerState,
                        uiState = uiState,
                        isFullscreen = false,
                        isInPipMode = isInPipMode,
                        onToggleFullscreen = onToggleFullscreen,
                        onQualityChange = { qid, pos -> viewModel.changeQuality(qid, pos) },
                        onBack = onBack,
                        bvid = bvid,
                        onDoubleTapLike = { viewModel.toggleLike() },
                        onReloadVideo = { viewModel.reloadVideo() },
                        cdnCount = (uiState as? PlayerUiState.Success)?.cdnCount ?: 1,
                        onSwitchCdn = { viewModel.switchCdn() },
                        onSwitchCdnTo = { viewModel.switchCdnTo(it) },
                        isAudioOnly = false,
                        onAudioOnlyToggle = { 
                            viewModel.setAudioMode(true)
                            onNavigateToAudioMode()
                        },
                        sleepTimerMinutes = sleepTimerMinutes,
                        onSleepTimerChange = { viewModel.setSleepTimer(it) },
                        videoshotData = (uiState as? PlayerUiState.Success)?.videoshotData,
                        viewPoints = viewPoints,
                        isVerticalVideo = isVerticalVideo,
                        onPortraitFullscreen = { playerState.setPortraitFullscreen(true) },
                        isPortraitFullscreen = isPortraitFullscreen,

                        onPipClick = onPipClick,
                        // [New] Codec & Audio
                        currentCodec = currentCodec,
                        onCodecChange = onCodecChange,
                        currentAudioQuality = currentAudioQuality,
                        onAudioQualityChange = onAudioQualityChange
                    )
                }
                
                // 📜 视频信息区域（可滚动）
                if (uiState is PlayerUiState.Success) {
                    val success = uiState as PlayerUiState.Success
                    val currentPageIndex = success.info.pages.indexOfFirst { it.cid == success.info.cid }.coerceAtLeast(0)
                    val downloadProgress by viewModel.downloadProgress.collectAsState()
                    
                    ScrollableVideoInfoSection(
                        info = success.info,
                        isFollowing = success.isFollowing,
                        isFavorited = success.isFavorited,
                        isLiked = success.isLiked,
                        coinCount = success.coinCount,
                        currentPageIndex = currentPageIndex,
                        downloadProgress = downloadProgress,
                        isInWatchLater = success.isInWatchLater,
                        videoTags = success.videoTags,
                        relatedVideos = success.related,
                        onFollowClick = { viewModel.toggleFollow() },
                        onFavoriteClick = { viewModel.toggleFavorite() },
                        onLikeClick = { viewModel.toggleLike() },
                        onCoinClick = { viewModel.openCoinDialog() },
                        onTripleClick = { viewModel.doTripleAction() },
                        onPageSelect = { viewModel.switchPage(it) },
                        onUpClick = onUpClick,
                        onDownloadClick = { viewModel.openDownloadDialog() },
                        onWatchLaterClick = { viewModel.toggleWatchLater() },
                        onRelatedVideoClick = { viewModel.loadVideo(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }
            }
        },
        secondaryContent = {
            // 📝 右侧：评论 / 相关推荐
            if (uiState is PlayerUiState.Success) {
                val success = uiState as PlayerUiState.Success
                
                TabletSecondaryContent(
                    success = success,
                    commentState = commentState,
                    commentViewModel = commentViewModel,
                    viewModel = viewModel,
                    playerState = playerState,
                    onUpClick = onUpClick
                )
            }
        },
        primaryRatio = splitRatio
    )
}

/**
 * 📝 平板右侧内容区域（评论/推荐切换）
 */
@Composable
private fun TabletSecondaryContent(
    success: PlayerUiState.Success,
    commentState: CommentUiState,
    commentViewModel: VideoCommentViewModel,
    viewModel: PlayerViewModel,
    playerState: VideoPlayerState,
    onUpClick: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("评论 ${if (commentState.replyCount > 0) "(${commentState.replyCount})" else ""}", "相关推荐")
    
    // 评论图片预览状态
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var sourceRect by remember { mutableStateOf<Rect?>(null) }
    
    // 图片预览对话框
    if (showImagePreview && previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewInitialIndex,
            sourceRect = sourceRect,
            onDismiss = { showImagePreview = false }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab 栏
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        // 内容区域
        when (selectedTab) {
            0 -> {
                // 评论列表
                val listState = rememberLazyListState()
                
                // 加载更多检测
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val totalItems = layoutInfo.totalItemsCount
                        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleItemIndex >= totalItems - 3 && !commentState.isRepliesLoading
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) commentViewModel.loadComments()
                }
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    // 排序/筛选栏
                    item {
                        CommentSortFilterBar(
                            count = commentState.replyCount,
                            sortMode = commentState.sortMode,
                            upOnlyFilter = commentState.upOnlyFilter,
                            onSortModeChange = { commentViewModel.setSortMode(it) },
                            onUpOnlyToggle = { commentViewModel.toggleUpOnly() }
                        )
                    }
                    
                    // 评论列表
                    items(
                        items = commentState.replies,
                        key = { "reply_${it.rpid}" }
                    ) { reply ->
                        ReplyItemView(
                            item = reply,
                            emoteMap = success.emoteMap,
                            upMid = success.info.owner.mid,
                            onClick = {},
                            onSubClick = { commentViewModel.openSubReply(it) },
                            onTimestampClick = { positionMs ->
                                playerState.player.seekTo(positionMs)
                                playerState.player.play()
                            },
                            onImagePreview = { images, index, rect ->
                                previewImages = images
                                previewInitialIndex = index
                                sourceRect = rect
                                showImagePreview = true
                            }
                        )
                    }
                    
                    // 加载指示器
                    if (commentState.isRepliesLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CupertinoActivityIndicator()
                            }
                        }
                    }
                }
            }
            1 -> {
                // 相关推荐列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(
                        items = success.related,
                        key = { "related_${it.bvid}" }
                    ) { video ->
                        // 简单的水平布局推荐卡片
                        RelatedVideoItem(
                            video = video,
                            isFollowed = video.owner.mid in success.followingMids,
                            onClick = { viewModel.loadVideo(video.bvid) }
                        )
                    }
                }
            }
        }
    }
}


/**
 * 📊 平板视频信息区域（可滚动版）
 * 使用 LazyColumn 确保内容过多时可以滚动，避免布局冲突
 */
@Composable
private fun ScrollableVideoInfoSection(
    info: com.android.purebilibili.data.model.response.ViewInfo,
    isFollowing: Boolean,
    isFavorited: Boolean,
    isLiked: Boolean,
    coinCount: Int,
    currentPageIndex: Int,
    downloadProgress: Float?,
    isInWatchLater: Boolean,
    videoTags: List<com.android.purebilibili.data.model.response.VideoTag>,
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onPageSelect: (Int) -> Unit,
    onUpClick: (Long) -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onRelatedVideoClick: (String) -> Unit,
    relatedVideos: List<com.android.purebilibili.data.model.response.RelatedVideo> = emptyList(),
    modifier: Modifier = Modifier
) {
    // 合集展开状态
    var showCollectionSheet by remember { mutableStateOf(false) }

    // 合集底部弹窗
    info.ugc_season?.let { season ->
        if (showCollectionSheet) {
            CollectionSheet(
                ugcSeason = season,
                currentBvid = info.bvid,
                onDismiss = { showCollectionSheet = false },
                onEpisodeClick = { episode ->
                    showCollectionSheet = false
                    onRelatedVideoClick(episode.bvid)
                }
            )
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 12.dp)
    ) {
        // 1. 视频标题
        item {
            VideoTitleWithDesc(
                info = info,
                videoTags = videoTags
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 2. UP主信息
        item {
            UpInfoSection(
                info = info,
                isFollowing = isFollowing,
                onFollowClick = onFollowClick,
                onUpClick = onUpClick
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. 互动按钮
        item {
            ActionButtonsRow(
                info = info,
                isLiked = isLiked,
                isFavorited = isFavorited,
                coinCount = coinCount,
                isInWatchLater = isInWatchLater,
                onLikeClick = onLikeClick,
                onCoinClick = onCoinClick,
                onFavoriteClick = onFavoriteClick,
                onTripleClick = onTripleClick,
                onDownloadClick = onDownloadClick,
                onWatchLaterClick = onWatchLaterClick,
                downloadProgress = downloadProgress ?: -1f,
                onCommentClick = { /* 平板模式不需要跳转评论 */ }
            )
        }

        // 4. 合集
        item {
            info.ugc_season?.let { season ->
                Spacer(modifier = Modifier.height(12.dp))
                CollectionRow(
                    ugcSeason = season,
                    currentBvid = info.bvid,
                    onClick = { showCollectionSheet = true }
                )
            }
        }

        // 5. 分P选择器
        item {
            if (info.pages.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                PagesSelector(
                    pages = info.pages,
                    currentPageIndex = currentPageIndex,
                    onPageSelect = onPageSelect
                )
            }
        }

        // 6. 简介（展开式）
        item {
            Spacer(modifier = Modifier.height(24.dp))
            if (info.desc.isNotEmpty()) {
                Text(
                    text = "简介",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                var isExpanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), // 🎨 修复粉色背景，使用中性灰
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .clickable { isExpanded = !isExpanded }
                        .padding(12.dp)
                ) {
                    Text(
                        text = info.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                    if (info.desc.length > 50) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isExpanded) "收起" else "展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        // 7. 更多推荐 (水平滚动)
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "更多推荐",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (relatedVideos.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    items(relatedVideos.take(10)) { video ->
                        Column(
                            modifier = Modifier
                                .width(160.dp)
                                .clickable { onRelatedVideoClick(video.bvid) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.6f)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                coil.compose.AsyncImage(
                                    model = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(video.pic),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = com.android.purebilibili.core.util.FormatUtils.formatDuration(video.duration),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = video.owner.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无更多推荐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            // 底部留白，防止被圆角遮挡
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
