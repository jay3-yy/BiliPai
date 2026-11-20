package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.rememberFollowActionWithConfirmation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.ui.AppDialogBackState
import com.android.purebilibili.core.ui.AppDialogPredictiveBackHandler
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.rememberAppBottomSheetMotion
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 竖屏详情点 UP 头像：半屏预览（关注 / 进入空间 / 近期投稿网格）。
 * 深浅色走 [resolveUpPreviewSheetSurfaceColors]。
 *
 * [onVisibilityProgressChange]：1=完全展开（播放器应上缩），0=收起；下拉和返回时跟手回位。
 */
@Composable
fun UpPreviewSheet(
    visible: Boolean,
    owner: Owner,
    isFollowing: Boolean,
    followStatusKnown: Boolean,
    followerCount: Int? = null,
    videoCount: Int? = null,
    likeCount: Int? = null,
    seedVideos: List<RelatedVideo> = emptyList(),
    onDismiss: () -> Unit,
    onFollowClick: () -> Unit,
    onEnterSpace: (Long) -> Unit,
    onVideoClick: (bvid: String, cid: Long) -> Unit,
    onVisibilityProgressChange: (Float) -> Unit = {},
) {
    if (!visible && owner.mid <= 0L) return

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetMotion = rememberAppBottomSheetMotion()
    val colors = resolveUpPreviewSheetSurfaceColors(MaterialTheme.colorScheme)
    val sheetShape = AppShapes.container(ContainerLevel.Sheet)

    var loading by remember(owner.mid) { mutableStateOf(false) }
    var videos by remember(owner.mid) {
        mutableStateOf(
            seedVideos
                .filter { it.owner.mid == owner.mid || it.owner.mid <= 0L }
                .mapNotNull { it.toUpPreviewVideoItem() }
                .take(12)
        )
    }
    var fetchedFollower by remember(owner.mid) { mutableStateOf<Int?>(null) }
    var fetchedVideoCount by remember(owner.mid) { mutableStateOf<Int?>(null) }
    var fetchedLikeCount by remember(owner.mid) { mutableStateOf<Int?>(null) }

    LaunchedEffect(visible, owner.mid) {
        if (!visible || owner.mid <= 0L) return@LaunchedEffect
        loading = videos.isEmpty()
        // Network on IO; Compose state only on Main (writing Snapshot state off-Main can crash).
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                var nextVideos: List<UpPreviewVideoItem>? = null
                var nextVideoCount: Int? = null
                var nextFollower: Int? = null
                var nextLikes: Int? = null
                val nav = NetworkModule.api.getNavInfo()
                val wbi = nav.data?.wbi_img
                val imgKey = wbi?.img_url?.substringAfterLast("/")?.substringBefore(".")
                val subKey = wbi?.sub_url?.substringAfterLast("/")?.substringBefore(".")
                if (!imgKey.isNullOrBlank() && !subKey.isNullOrBlank()) {
                    val videoParams = WbiUtils.sign(
                        mapOf(
                            "mid" to owner.mid.toString(),
                            "pn" to "1",
                            "ps" to "12",
                            "order" to "pubdate",
                        ),
                        imgKey,
                        subKey,
                    )
                    val videoResp = NetworkModule.spaceApi.getSpaceVideos(videoParams)
                    if (videoResp.code == 0) {
                        val list = videoResp.data?.list?.vlist.orEmpty()
                            .map { it.toUpPreviewVideoItem() }
                        if (list.isNotEmpty()) nextVideos = list
                        val count = videoResp.data?.page?.count
                        if (count != null && count >= 0) nextVideoCount = count
                    }
                }
                val relation = NetworkModule.spaceApi.getRelationStat(owner.mid)
                if (relation.code == 0) {
                    relation.data?.follower?.let { nextFollower = it }
                }
                val upStat = NetworkModule.spaceApi.getUpStat(owner.mid)
                if (upStat.code == 0) {
                    upStat.data?.likes?.let {
                        nextLikes = it.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    }
                }
                UpPreviewFetchResult(
                    videos = nextVideos,
                    videoCount = nextVideoCount,
                    follower = nextFollower,
                    likes = nextLikes,
                )
            }.getOrNull()
        }
        if (fetched != null) {
            fetched.videos?.let { videos = it }
            fetched.follower?.let { fetchedFollower = it }
            fetched.videoCount?.let { fetchedVideoCount = it }
            fetched.likes?.let { fetchedLikeCount = it }
        }
        loading = false
    }

    val back = remember(owner.mid) { AppDialogBackState() }
    AppDialogPredictiveBackHandler(
        state = back,
        enabled = visible,
        windowScoped = false,
        animateCompletion = false,
        // 关闭动画从当前手势位置继续，重新打开时才清除手势位移。
        retainPreview = { true },
        onBackCompleted = onDismiss,
    )
    LaunchedEffect(visible, back) {
        if (visible) back.reset()
    }

    // Header + grid share one LazyVerticalGrid so the top band is not a dead zone.
    val gridState = rememberLazyGridState()
    val flingBehavior = ScrollableDefaults.flingBehavior()
    val sheetHeightDp = screenHeight * UP_PREVIEW_SHEET_HEIGHT_FRACTION
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val maxSheetDragPx = with(density) { sheetHeightDp.toPx() }
    var sheetDragOffsetPx by remember(owner.mid) { mutableFloatStateOf(0f) }
    val settleScope = rememberCoroutineScope()
    var settleJob by remember(owner.mid) { mutableStateOf<Job?>(null) }
    val latestSheetMotion by rememberUpdatedState(sheetMotion)
    val latestVisible by rememberUpdatedState(visible)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetOffsetPx = {
        val dragOffset = sheetDragOffsetPx
        dragOffset + (maxSheetDragPx - dragOffset) * back.progress
    }
    val hostVisibilityProgress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) {
            sheetMotion.contentEnterFadeDurationMillis
        } else {
            sheetMotion.contentExitFadeDurationMillis
        }),
        label = "upPreviewHostVisibility",
    )
    val latestOnVisibilityProgressChange by rememberUpdatedState(onVisibilityProgressChange)
    LaunchedEffect(visible, back, maxSheetDragPx) {
        snapshotFlow {
            resolveUpPreviewSheetVisibilityProgress(
                hostVisible = visible,
                hostVisibilityProgress = hostVisibilityProgress.value,
                sheetDragOffsetPx = sheetOffsetPx(),
                sheetHeightPx = maxSheetDragPx,
            )
        }.collect { latestOnVisibilityProgressChange(it) }
    }
    val sheetNestedScrollConnection = remember(
        owner.mid,
        dismissThresholdPx,
        maxSheetDragPx,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    source != NestedScrollSource.UserInput ||
                    available.y >= 0f ||
                    sheetDragOffsetPx <= 0f
                ) {
                    return Offset.Zero
                }

                settleJob?.cancel()
                val consumedY = available.y.coerceAtLeast(-sheetDragOffsetPx)
                sheetDragOffsetPx = (sheetDragOffsetPx + consumedY).coerceAtLeast(0f)
                return Offset(x = 0f, y = consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) {
                    return Offset.Zero
                }
                if (available.y > 0f) {
                    settleJob?.cancel()
                    sheetDragOffsetPx =
                        (sheetDragOffsetPx + available.y).coerceAtMost(maxSheetDragPx)
                }
                // Keep the modal isolated from the detail page at every grid boundary.
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetDragOffsetPx <= 0f) return Velocity.Zero
                val shouldDismiss = shouldDismissUpPreviewSheet(
                    dragOffsetPx = sheetDragOffsetPx,
                    velocityYPxPerSecond = available.y,
                    dismissThresholdPx = dismissThresholdPx,
                )
                settleJob?.cancel()
                settleJob = settleScope.launch {
                    animate(
                        initialValue = sheetDragOffsetPx,
                        targetValue = if (shouldDismiss) maxSheetDragPx else 0f,
                        initialVelocity = available.y,
                        animationSpec = latestSheetMotion.dragSettleSpec,
                    ) { value, _ ->
                        sheetDragOffsetPx = value.coerceIn(0f, maxSheetDragPx)
                    }
                    if (shouldDismiss && latestVisible) latestOnDismiss()
                }
                return Velocity(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(x = 0f, y = available.y)
        }
    }

    LaunchedEffect(visible, owner.mid, maxSheetDragPx) {
        settleJob?.cancel()
        if (visible) sheetDragOffsetPx = 0f
    }
    androidx.compose.runtime.DisposableEffect(owner.mid) {
        onDispose { settleJob?.cancel() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .zIndex(if (visible || hostVisibilityProgress.value > 0.001f) 8f else 0f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = sheetMotion.scrimEnter,
            exit = sheetMotion.scrimExit,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - back.progress }
                    .background(colors.scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = sheetMotion.contentEnter,
            exit = sheetMotion.contentExit,
        ) {
            // clip before elevation/translation so left/right top corners stay circular
            // (Surface shape alone can still let grid content paint square edges under shadow).
            AppSurface(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .height(sheetHeightDp)
                    .clip(sheetShape)
                    .nestedScroll(sheetNestedScrollConnection)
                    .graphicsLayer { translationY = sheetOffsetPx() },
                shape = sheetShape,
                color = colors.sheetColor,
                shadowElevation = 12.dp,
            ) {
                val bottomPad = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
                val alignVideos = !com.android.purebilibili.core.ui.LocalFullVideoCardContentVisible.current
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    flingBehavior = flingBehavior,
                    userScrollEnabled = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPad),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(
                        key = "up_preview_header",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        UpPreviewSheetHeader(
                            owner = owner,
                            isFollowing = isFollowing,
                            followStatusKnown = followStatusKnown,
                            followerCount = fetchedFollower ?: followerCount,
                            videoCount = fetchedVideoCount ?: videoCount,
                            likeCount = fetchedLikeCount ?: likeCount,
                            colors = colors,
                            onFollowClick = onFollowClick,
                            onEnterSpace = { onEnterSpace(owner.mid) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    when {
                        loading && videos.isEmpty() -> {
                            item(
                                key = "up_preview_loading",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppCircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.followFillColor,
                                    )
                                }
                            }
                        }
                        videos.isEmpty() -> {
                            item(
                                key = "up_preview_empty",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppText(
                                        text = "暂无投稿",
                                        color = colors.supportingColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        else -> {
                            val videoKeys = videos.mapIndexed { index, item ->
                                resolveIndexedVideoLazyKey(namespace = "up_preview_video", index = index, bvid = item.bvid)
                            }
                            val renderVideo: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                                val video = videos[index]
                                UpPreviewVideoCard(
                                    item = video,
                                    modifier = cardModifier,
                                    colors = colors,
                                    onClick = {
                                        resolveUpPreviewVideoClickTarget(video.bvid)?.let {
                                            onVideoClick(it.first, it.second)
                                        }
                                    },
                                )
                            }
                            if (!alignVideos) {
                                itemsIndexed(videos, key = { index, _ -> videoKeys[index] }) { index, _ -> renderVideo(index, Modifier) }
                            } else {
                                com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(videos.size, 2).forEach { row ->
                                    item(key = "up_preview_row_${videoKeys[row.first]}", span = { GridItemSpan(maxLineSpan) }) {
                                        com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                            indices = row,
                                            itemKeys = videoKeys,
                                            columns = 2,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            content = renderVideo,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class UpPreviewFetchResult(
    val videos: List<UpPreviewVideoItem>?,
    val videoCount: Int?,
    val follower: Int?,
    val likes: Int?,
)

@Composable
private fun UpPreviewSheetHeader(
    owner: Owner,
    isFollowing: Boolean,
    followStatusKnown: Boolean,
    followerCount: Int?,
    videoCount: Int?,
    likeCount: Int?,
    colors: UpPreviewSheetSurfaceColors,
    onFollowClick: () -> Unit,
    onEnterSpace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val followClick = rememberFollowActionWithConfirmation(
        targetKey = owner.mid,
        userName = owner.name,
        isFollowing = isFollowing,
        onFollowClick = onFollowClick,
    )
    val useNeutralFollow = !followStatusKnown || isFollowing
    val actionPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 10.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(colors.handleColor, CircleShape)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(owner.face),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.coverPlaceholderColor),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            AppText(
                text = owner.name.ifBlank { "UP主" },
                color = colors.titleColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpPreviewStat("粉丝", followerCount, colors, Modifier.weight(1f))
            UpPreviewStat("投稿", videoCount, colors, Modifier.weight(1f))
            UpPreviewStat("获赞", likeCount, colors, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppButton(
                onClick = followClick,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        if (!followStatusKnown) onClick(label = "重新确认状态并关注", action = null)
                    },
                shape = CircleShape,
                containerColor = if (useNeutralFollow) colors.followingFillColor else colors.followFillColor,
                contentColor = if (useNeutralFollow) colors.followingContentColor else colors.followContentColor,
                contentPadding = actionPadding,
            ) {
                AppText(
                    text = when {
                        !followStatusKnown -> "待确认"
                        isFollowing -> "已关注"
                        else -> "+ 关注"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    tapToCopyEnabled = false,
                )
            }
            AppButton(
                onClick = onEnterSpace,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = CircleShape,
                containerColor = Color.Transparent,
                contentColor = colors.titleColor,
                border = BorderStroke(1.dp, colors.dividerColor),
                contentPadding = actionPadding,
            ) {
                AppText(
                    text = "进入空间",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    tapToCopyEnabled = false,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.dividerColor)
        )
    }
}

@Composable
private fun UpPreviewStat(
    label: String,
    count: Int?,
    colors: UpPreviewSheetSurfaceColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = formatUpPreviewStat(count),
            color = colors.titleColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppText(
            text = label,
            color = colors.supportingColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun UpPreviewVideoCard(
    item: UpPreviewVideoItem,
    colors: UpPreviewSheetSurfaceColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Field))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(AppShapes.container(ContainerLevel.Field))
                .background(colors.coverPlaceholderColor)
        ) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(item.coverUrl),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 左下播放量 / 右下时长
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = formatUpPreviewCount(item.playCount),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                if (item.durationText.isNotBlank()) {
                    AppText(
                        text = item.durationText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        AppText(
            text = item.title,
            color = colors.titleColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = videoCardTitleMaxLines(),
            overflow = videoCardTitleOverflow(),
        )
        if (item.createdAtSeconds > 0L) {
            Spacer(Modifier.height(2.dp))
            AppText(
                text = formatUpPreviewRelativeDate(item.createdAtSeconds),
                color = colors.supportingColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

private fun RelatedVideo.toUpPreviewVideoItem(): UpPreviewVideoItem? {
    if (bvid.isBlank()) return null
    return UpPreviewVideoItem(
        bvid = bvid,
        title = title,
        coverUrl = pic,
        playCount = stat.view,
        durationText = FormatUtils.formatDuration(duration),
        createdAtSeconds = 0L,
    )
}

private fun SpaceVideoItem.toUpPreviewVideoItem(): UpPreviewVideoItem {
    return UpPreviewVideoItem(
        bvid = bvid,
        title = title,
        coverUrl = pic,
        playCount = play,
        durationText = length,
        createdAtSeconds = created,
    )
}

private fun formatUpPreviewRelativeDate(createdAtSeconds: Long): String {
    if (createdAtSeconds <= 0L) return ""
    val nowSec = System.currentTimeMillis() / 1000L
    val delta = (nowSec - createdAtSeconds).coerceAtLeast(0L)
    val days = TimeUnit.SECONDS.toDays(delta)
    return when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        days < 30L -> "${days}天前"
        days < 365L -> "${days / 30}个月前"
        else -> "${days / 365}年前"
    }
}
