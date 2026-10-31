package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade

import android.widget.Toast
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.common.copyPlainTextToClipboard
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppGlassMenuSource
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.store.HomeFeedCardStyle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.LocalVideoTransitionAdaptiveInfo
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.core.ui.transition.withMeasuredCoverDecodeSize
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.feature.home.HomeFeedCardLayout
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoStatRow
import com.android.purebilibili.feature.home.resolveHomeFeedCardLayout
import com.android.purebilibili.feature.video.ui.FollowBadgeTone
import com.android.purebilibili.feature.video.ui.resolveVideoFollowVisualPolicy
import com.android.purebilibili.navigation.VideoRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PiliPlus horizontal cards use a 16:10 cover.
 */
internal const val RELATED_VIDEO_CARD_COVER_ASPECT_RATIO = 16f / 10f

internal const val RELATED_VIDEO_GRID_COLUMNS = 1

/**
 * Related Videos Header
 */
@Composable
fun RelatedVideosHeader() {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = "\u66f4\u591a\u63a8\u8350",
                style = MaterialTheme.typography.titleMedium,
                color = AppContentColors.primaryText()
            )
        }
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveRelatedVideoCardPressScaleTarget(
    isPressed: Boolean,
    transitionEnabled: Boolean
): Float = 1f

@Suppress("UNUSED_PARAMETER")
internal fun shouldEnableRelatedVideoCoverCrossfade(
    transitionEnabled: Boolean
): Boolean = false

@Suppress("UNUSED_PARAMETER")
internal fun shouldTriggerRelatedVideoPressHaptic(
    isPressed: Boolean,
    transitionEnabled: Boolean
): Boolean = false

internal fun resolveRelatedVideoSharedElementSourceRoute(sourceRoute: String?): String {
    return sourceRoute
        ?.substringBefore("?")
        ?.takeIf { it.isNotBlank() }
        ?: VideoRoute.base
}

/** 同页切集只改变播放内容；来源身份始终属于承载相关推荐的导航 entry。 */
internal fun resolveRelatedVideoCardHostRoute(entryRoute: String?, displayedBvid: String): String {
    val route = entryRoute?.substringBefore('?')?.trim()
    return route?.takeIf { it.startsWith("video/") && it.removePrefix("video/").isNotBlank() }
        ?: "video/${displayedBvid.trim()}"
}

internal fun chunkRelatedVideosForHomeStyleGrid(
    videos: List<RelatedVideo>,
): List<List<RelatedVideo>> {
    if (videos.isEmpty()) return emptyList()
    return videos.chunked(RELATED_VIDEO_GRID_COLUMNS)
}

@Composable
internal fun rememberRelatedVideoCardLayout(): HomeFeedCardLayout {
    LocalRelatedVideoCardLayout.current?.let { return it }
    val context = LocalContext.current
    val homeFeedCardStyle by SettingsManager
        .getHomeFeedCardStyle(context)
        .collectAsStateWithLifecycle(initialValue = HomeFeedCardStyle.BILIPAI)
    return remember(homeFeedCardStyle) {
        resolveHomeFeedCardLayout(homeFeedCardStyle)
    }
}

/** 详情加载、占位交接和真实推荐共用页面已读取的卡片样式。 */
internal val LocalRelatedVideoCardLayout = androidx.compose.runtime.staticCompositionLocalOf<HomeFeedCardLayout?> { null }

internal const val RELATED_VIDEO_SKELETON_ROW_COUNT = 3

/** 点击前同步读取父页的跳转锁，避免被拒绝的连点先覆盖来源卡片快照。 */
internal val LocalRelatedVideoNavigationAllowed = compositionLocalOf<() -> Boolean> { { true } }

/**
 * 相关推荐单列横卡：点击时冻结来源标识、几何与 chrome，供整卡 Morph 及逐层返回。
 * 与首页视频卡一致，由一个 sharedBounds 容器承载封面、标题、UP 信息和统计内容。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RelatedVideoItem(
    video: RelatedVideo,
    isFollowed: Boolean = false,
    showUpBadge: Boolean = true,
    coverAspectRatio: Float = com.android.purebilibili.core.ui.videoCardCoverAspectRatio(),
    stacked: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    moreMenuSource: AppGlassMenuSource? = null,
    moreMenu: @Composable () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val densityValue = density.density
    val sourceRoute = resolveRelatedVideoSharedElementSourceRoute(
        LocalVideoCardSharedElementSourceRoute.current
    )
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedTransitionEnabled = LocalSharedTransitionEnabled.current
    val sharedReady = sharedTransitionEnabled &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val transitionAdaptiveInfo = LocalVideoTransitionAdaptiveInfo.current
    val sharedTransitionMotionSpec = remember(
        sourceRoute,
        sharedTransitionEnabled,
        sharedTransitionSpeedSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = sharedTransitionEnabled,
            speedSettings = sharedTransitionSpeedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val useCardShellSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRoute,
        transitionEnabled = sharedReady,
    )
    val cardCoordinatesRef = remember { object { var value: LayoutCoordinates? = null } }
    val coverCoordinatesRef = remember { object { var value: LayoutCoordinates? = null } }
    val infoCoordinatesRef = remember { object { var value: LayoutCoordinates? = null } }
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(
        video.bvid, enabled = sharedTransitionEnabled,
    )
    // 透明横卡只由封面自身裁剪圆角；整行及共享转场外框不能再切掉封面左侧。
    val cardShape = androidx.compose.ui.graphics.RectangleShape
    val cardCornerRadiusDp = 0
    val coverCornerDp = com.android.purebilibili.core.ui.AppShapes.mediaCoverCornerDp(
        com.android.purebilibili.core.ui.ContainerLevel.MediaCover,
    )
    val context = LocalContext.current
    // Keep the request stable and skip image crossfade during the Miuix page transition.
    val stationaryCoverUrl = remember(video.pic) {
        FormatUtils.resolveVideoCoverUrl(video.pic, useLowQuality = false)
    }
    val coverRequest = remember(context, stationaryCoverUrl) {
        ImageRequest.Builder(context)
            .data(stationaryCoverUrl)
            .crossfade(false)
            .memoryCacheKey(stationaryCoverUrl)
            .diskCacheKey(stationaryCoverUrl)
            .build()
    }
    val relatedVideoNavigationAllowed = LocalRelatedVideoNavigationAllowed.current
    val triggerRelatedVideoClick = relatedClick@{
        if (!relatedVideoNavigationAllowed()) return@relatedClick
        if (CardPositionManager.isVideoNavigationDebounceActive()) {
            // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
            return@relatedClick
        }
        cardCoordinatesRef.value
            ?.takeIf { it.isAttached }
            ?.videoCardSourceBoundsInRoot()
            ?.let { bounds ->
                val sourceCoverBounds = coverCoordinatesRef.value
                    ?.takeIf { it.isAttached }
                    ?.videoCardSourceBoundsInRoot()
                CardPositionManager.recordVideoCardPosition(
                    bvid = video.bvid,
                    sourceRoute = sourceRoute,
                    bounds = bounds,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx,
                    density = densityValue,
                    sourceCornerDp = cardCornerRadiusDp,
                    coverBounds = sourceCoverBounds,
                    sourceLayout = if (stacked) {
                        VideoCardSourceLayout.STACKED
                    } else {
                        VideoCardSourceLayout.SIDE_BY_SIDE
                    },
                    infoBounds = infoCoordinatesRef.value
                        ?.takeIf { it.isAttached }
                        ?.videoCardSourceBoundsInRoot(),
                    sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                        title = video.title,
                        ownerName = video.owner.name,
                        ownerFaceUrl = video.owner.face,
                        viewText = FormatUtils.formatStat(video.stat.view.toLong()),
                        danmakuText = FormatUtils.formatStat(video.stat.danmaku.toLong()),
                        durationText = FormatUtils.formatDuration(video.duration),
                        followed = isFollowed,
                        // Related horizontal card keeps play/danmaku in the info column.
                        infoPresentation = com.android.purebilibili.core.ui.transition
                            .resolveVideoCardSourceInfoPresentation(
                                publishTimeText = FormatUtils.formatPublishTime(video.pubdate),
                                showStatsInInfo = true,
                                showOverflowMenu = onMoreClick != null,
                            ),
                        coverPresentation = VideoCardSourceCoverPresentation(
                            cornerRadiusDp = coverCornerDp.value,
                            showDurationOnCover = true,
                        ),
                        coverUrl = stationaryCoverUrl,
                        coverCacheKey = stationaryCoverUrl,
                    ).withMeasuredCoverDecodeSize(sourceCoverBounds),
                )
                nativeCardSnapshot.capture()
            }
        onClick()
        Unit
    }
    // 排版对齐首页单列卡片:标题用 feed 紧凑级,统计用 labelSmall。
    val contentTypography = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography()
    val moreHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .onGloballyPositioned { coordinates ->
                cardCoordinatesRef.value = coordinates
            }
            .videoCardShellSharedBoundsOrEmpty(
                enabled = useCardShellSharedBounds,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                bvid = video.bvid,
                sourceRoute = sourceRoute,
                motionSpec = sharedTransitionMotionSpec,
                clipShape = cardShape,
                crossfadeSourceContent = true,
            )
            .then(nativeCardSnapshot.modifier)
            .clip(cardShape)
            .clickable(onClick = triggerRelatedVideoClick)
    ) {
        HorizontalVideoCardFrame(
            plainHorizontal = true,
            coverAspectRatio = coverAspectRatio,
            stacked = stacked,
            fillInfoHeight = true,
            coverModifier = Modifier.onGloballyPositioned { coordinates ->
                coverCoordinatesRef.value = coordinates
            },
            infoModifier = Modifier.onGloballyPositioned { coordinates ->
                infoCoordinatesRef.value = coordinates
            },
            coverOverlayModifier = nativeCardSnapshot.coverOverlayModifier,
            coverContent = {
                AsyncImage(
                    model = coverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            coverOverlayContent = {
                com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText(
                    text = FormatUtils.formatDuration(video.duration),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            },
            infoContent = {
                AppText(
                    text = video.title,
                    style = contentTypography.title,
                    maxLines = videoCardTitleMaxLines(),
                    minLines = if (stacked) 1 else minOf(2, videoCardTitleMaxLines()),
                    overflow = videoCardTitleOverflow(),
                    color = AppContentColors.primaryText(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!stacked) {
                    Spacer(Modifier.weight(1f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                    ) {
                        val publishTime = remember(video.pubdate) {
                            FormatUtils.formatPublishTime(video.pubdate)
                        }
                        if (publishTime.isNotBlank()) {
                            AppText(
                                text = publishTime,
                                style = MaterialTheme.typography.labelMedium,
                                color = AppContentColors.secondaryText(),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 96.dp),
                            )
                        }
                        UpBadgeName(
                            name = video.owner.name,
                            nameLeadingContent = if (isFollowed) {
                                {
                                    val followVisualPolicy = resolveVideoFollowVisualPolicy(
                                        isFollowing = true,
                                        darkTheme = true,
                                    )
                                    AppText(
                                        text = "已关注",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = when (followVisualPolicy.relatedBadgeTone) {
                                            FollowBadgeTone.PRIMARY -> AppContentColors.accent()
                                            null -> AppContentColors.secondaryText()
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                            leadingContent = if (
                                com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current.showAvatars &&
                                video.owner.face.isNotEmpty()
                            ) {
                                {
                                    val avatarRequest = remember(context, video.owner.face) {
                                        ImageRequest.Builder(context)
                                            .data(FormatUtils.fixImageUrl(video.owner.face))
                                            .crossfade(false)
                                            .build()
                                    }
                                    AsyncImage(
                                        model = avatarRequest,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    )
                                }
                            } else {
                                null
                            },
                            nameStyle = MaterialTheme.typography.labelMedium,
                            nameColor = AppContentColors.secondaryText(),
                            badgeTextColor = AppContentColors.secondaryText().copy(alpha = 0.85f),
                            badgeBorderColor = AppContentColors.secondaryText().copy(alpha = 0.35f),
                            showUpBadge = showUpBadge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalVideoStatRow(
                        playText = FormatUtils.formatStat(video.stat.view.toLong()),
                        danmakuText = FormatUtils.formatStat(video.stat.danmaku.toLong()),
                    )
                }
            },
            trailingContent = onMoreClick?.let { moreClick ->
                {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .then(moreMenuSource?.let { Modifier.appGlassMenuSource(it, onOpen = moreClick) } ?: Modifier)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                moreHaptic(HapticType.LIGHT)
                                moreClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "更多操作",
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(18.dp),
                        )
                        moreMenu()
                    }
                }
            },
        )
    }
}

@Composable
internal fun RelatedVideoGridRow(
    videos: List<RelatedVideo>,
    cardLayout: HomeFeedCardLayout,
    cardPresentation: RelatedVideoCardLayout,
    followingMids: Set<Long> = emptySet(),
    showUpBadge: Boolean = true,
    onVideoClick: (RelatedVideo) -> Unit,
    onUpClick: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuVideo by remember { mutableStateOf<RelatedVideo?>(null) }
    var summaryVideo by remember { mutableStateOf<RelatedVideo?>(null) }
    var summaryLoading by remember { mutableStateOf(false) }
    var summaryText by remember { mutableStateOf("") }
    val privacyEnabled by remember { SettingsManager.getPrivacyModeEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val actionMenuSource = rememberAppGlassMenuSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = cardLayout.outerPaddingDp.dp,
                vertical = if (cardPresentation == RelatedVideoCardLayout.HORIZONTAL) 1.dp else 2.dp,
            )
    ) {
        videos.firstOrNull()?.let { video ->
            RelatedVideoItem(
                video = video,
                isFollowed = video.owner.mid in followingMids,
                showUpBadge = showUpBadge,
                coverAspectRatio = cardLayout.coverAspectRatio,
                stacked = cardPresentation == RelatedVideoCardLayout.STACKED,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onVideoClick(video) },
                onMoreClick = { menuVideo = video },
                moreMenuSource = actionMenuSource,
                moreMenu = {
                    AppDropdownMenu(
                        expanded = menuVideo == video,
                        onDismissRequest = { menuVideo = null },
                        glassSource = actionMenuSource,
                    ) {
                        if (video.bvid.isNotBlank()) {
                            AppDropdownMenuItem(text = { AppText(video.bvid) }, leadingIcon = { AppIcon(Icons.Outlined.ContentCopy, null) }, onClick = {
                                menuVideo = null
                                copyPlainTextToClipboard(context, video.bvid, "BV号")
                                Toast.makeText(context, "已复制 ${video.bvid}", Toast.LENGTH_SHORT).show()
                            })
                        }
                        AppDropdownMenuItem(text = { AppText("稍后再看") }, leadingIcon = { AppIcon(Icons.Outlined.WatchLater, null) }, onClick = {
                            menuVideo = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { ActionRepository.toggleWatchLater(video.aid, true) }
                                Toast.makeText(context, result.fold({ "已添加到稍后再看" }, { it.message ?: "添加失败" }), Toast.LENGTH_SHORT).show()
                            }
                        })
                        if (video.cid > 0L && video.bvid.isNotBlank()) {
                            AppDropdownMenuItem(text = { AppText("AI总结") }, leadingIcon = { AppIcon(Icons.Outlined.AutoAwesome, null) }, onClick = {
                                menuVideo = null
                                summaryVideo = video
                                summaryLoading = true
                                summaryText = ""
                                scope.launch {
                                    VideoRepository.getAiSummary(video.bvid, video.cid, video.owner.mid).fold(
                                        { summaryText = it.data?.modelResult?.summary?.takeIf(String::isNotBlank) ?: "该视频暂无 AI 总结" },
                                        { summaryText = it.message ?: "获取 AI 总结失败" },
                                    )
                                    summaryLoading = false
                                }
                            })
                        }
                        if (video.owner.mid > 0L && onUpClick != null) {
                            AppDropdownMenuItem(text = { AppText("访问：${video.owner.name}") }, leadingIcon = { AppIcon(Icons.Outlined.Person, null) }, onClick = {
                                menuVideo = null
                                onUpClick(video.owner.mid)
                            })
                        }
                        AppDropdownMenuItem(text = { AppText(if (privacyEnabled) "退出无痕模式" else "进入无痕模式") }, leadingIcon = { AppIcon(if (privacyEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null) }, onClick = {
                            menuVideo = null
                            scope.launch {
                                SettingsManager.setPrivacyModeEnabled(context, !privacyEnabled)
                                Toast.makeText(context, if (privacyEnabled) "已退出无痕模式" else "已进入无痕模式", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                },
            )
        }
    }

    if (summaryVideo != null) {
        AppAlertDialog(
            onDismissRequest = { summaryVideo = null },
            title = { AppText("AI 总结") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { AppText(if (summaryLoading) "正在获取 AI 总结…" else summaryText) } },
            confirmButton = { AppDialogAction(onClick = { summaryVideo = null }) { AppText("关闭") } },
        )
    }

}
