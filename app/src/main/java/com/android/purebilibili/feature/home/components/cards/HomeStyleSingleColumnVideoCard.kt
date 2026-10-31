package com.android.purebilibili.feature.home.components.cards

import com.android.purebilibili.core.ui.components.VideoStatusBadges
import com.android.purebilibili.core.ui.components.wallpaperAwareMediaCardBackground
import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade

import com.android.purebilibili.core.ui.MediaContrastPalette

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.FeedTitleHierarchy
import com.android.purebilibili.core.ui.feedContentTypography
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.adaptive.adaptiveCardHoverEffect
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.core.ui.transition.withMeasuredCoverDecodeSize
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.resolveHomeCoverRequestSpec
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun HomeStyleSingleColumnVideoCard(
    video: VideoItem,
    sourceRoute: String,
    coverAspectRatio: Float,
    transitionEnabled: Boolean,
    sharedTransitionEnabled: Boolean = transitionEnabled,
    isFollowing: Boolean = false,
    showUpBadge: Boolean = true,
    durationStyle: com.android.purebilibili.core.store.HomeDurationStyle =
        com.android.purebilibili.core.store.HomeDurationStyle.OVERLAY_TEXT_ONLY,
    showPublishTime: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    highlightedTitle: androidx.compose.ui.text.AnnotatedString? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    transparentCardBackground: Boolean = false,
    compactPartition: Boolean = false,
) {
    val context = LocalContext.current
    var cardVisibleInWindow by remember(video.bvid, video.aid) { mutableStateOf(false) }
    val publishTimeText = rememberVideoCardPublishTimeText(video, showPublishTime, cardVisibleInWindow)
    val isLongPressEnabled = com.android.purebilibili.core.ui.LocalVideoCardLongPressEnabled.current
    val contentTypography = horizontalVideoCardTypography()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val densityValue = density.density
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val latestOnClick = rememberUpdatedState(onClick)
    val clickScope = rememberCoroutineScope()
    var forceSharedTransitionForClick by remember { mutableStateOf(false) }
    val effectiveTransitionEnabled = transitionEnabled || forceSharedTransitionForClick
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedReady = effectiveTransitionEnabled &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    val motionSettings = LocalVideoSharedTransitionSpeedSettings.current
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val motionSpec = remember(
        sourceRoute,
        effectiveTransitionEnabled,
        motionSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = effectiveTransitionEnabled,
            speedSettings = motionSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val cardBounds = remember { object { var value: Rect? = null } }
    val coverBounds = remember { object { var value: Rect? = null } }
    val infoCoordinates = remember { object { var value: LayoutCoordinates? = null } }
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(
        video.bvid, enabled = sharedTransitionEnabled,
    )
    // 透明横卡的圆角属于封面，不能再叠加整行外框裁剪；有底板的紧凑卡保留原形状。
    val cardShape = if (compactPartition) AppShapes.container(ContainerLevel.Card)
        else androidx.compose.ui.graphics.RectangleShape
    val cardCornerDp = if (compactPartition) AppShapes.containerCornerDp(ContainerLevel.Card) else 0.dp
    val coverCornerDp = AppShapes.mediaCoverCornerDp(
        if (compactPartition) ContainerLevel.Card else ContainerLevel.MediaCover,
    )
    val useCardShellSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRoute,
        transitionEnabled = sharedReady,
    )
    val coverRequestSpec = remember(densityValue) {
        resolveHomeCoverRequestSpec(
            cardWidthDp = HORIZONTAL_VIDEO_CARD_COVER_WIDTH_DP.toFloat(),
            density = densityValue,
            useLowQualityCover = false,
        )
    }
    val stationaryCoverUrl = remember(video.pic, coverRequestSpec) {
        coverRequestSpec.resolveUrl(video.pic)
    }
    val coverRequest = remember(stationaryCoverUrl, coverRequestSpec) {
        ImageRequest.Builder(context)
            .data(stationaryCoverUrl)
            .size(coverRequestSpec.widthPx, coverRequestSpec.heightPx)
            .crossfade(false)
            .memoryCacheKey(stationaryCoverUrl)
            .diskCacheKey(stationaryCoverUrl)
            .build()
    }
    val triggerClick by rememberUpdatedState(appActionWithFeedback {
        if (CardPositionManager.isVideoNavigationDebounceActive()) {
            // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
            return@appActionWithFeedback
        }
        cardBounds.value?.let { bounds ->
            val sourceCoverBounds = coverBounds.value
            CardPositionManager.recordVideoCardPosition(
                bvid = video.bvid,
                sourceRoute = sourceRoute,
                bounds = bounds,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                density = density.density,
                sourceCornerDp = cardCornerDp.value.roundToInt(),
                coverBounds = sourceCoverBounds,
                sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                infoBounds = infoCoordinates.value?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot(),
                sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                    title = video.title,
                    ownerName = video.owner.name,
                    ownerFaceUrl = video.owner.face,
                    recommendationReason = video.recommendationReason,
                    viewText = FormatUtils.formatStat(video.stat.view.toLong()),
                    danmakuText = FormatUtils.formatStat(video.stat.danmaku.toLong()),
                    durationText = FormatUtils.formatDuration(video.duration),
                    followed = isFollowing || video.isFollowed,
                    // Single-column paints play/danmaku in the info column (not on cover only).
                    infoPresentation = com.android.purebilibili.core.ui.transition
                        .resolveVideoCardSourceInfoPresentation(
                            publishTimeText = publishTimeText,
                            showStatsInInfo = true,
                            showOverflowMenu = onMoreClick != null || trailingContent != null,
                        ),
                    coverPresentation = VideoCardSourceCoverPresentation(
                        cornerRadiusDp = coverCornerDp.value,
                        showDurationOnCover = durationStyle !=
                            com.android.purebilibili.core.store.HomeDurationStyle.HIDDEN,
                    ),
                    coverUrl = stationaryCoverUrl,
                    coverCacheKey = stationaryCoverUrl,
                ).withMeasuredCoverDecodeSize(sourceCoverBounds),
            )
            nativeCardSnapshot.capture()
        }
        if (sharedTransitionEnabled && !transitionEnabled) {
            forceSharedTransitionForClick = true
            clickScope.launch {
                withFrameNanos { }
                withFrameNanos { }
                latestOnClick.value()
            }
        } else {
            latestOnClick.value()
        }
        Unit
    })
    val moreHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)
    HorizontalVideoCardFrame(
        plainHorizontal = !compactPartition,
        fillInfoHeight = true,
        coverAspectRatio = coverAspectRatio,
        compactPartition = compactPartition,
        modifier = modifier
            .padding(vertical = if (compactPartition) 0.dp else 5.dp)
            .onVisibilityChanged(minFractionVisible = 0.01f, minDurationMs = 0L) {
                cardVisibleInWindow = it
            }
            .adaptiveCardHoverEffect(shape = cardShape)
            .onGloballyPositioned { coordinates ->
                cardBounds.value = coordinates.videoCardSourceBoundsInRoot()
            }
            .videoCardShellSharedBoundsOrEmpty(
                enabled = useCardShellSharedBounds,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                bvid = video.bvid,
                sourceRoute = sourceRoute,
                motionSpec = motionSpec,
                clipShape = cardShape,
                crossfadeSourceContent = true,
            )
            .then(nativeCardSnapshot.modifier)
            .clip(cardShape)
            .then(
                if (transparentCardBackground || !compactPartition) Modifier else Modifier.wallpaperAwareMediaCardBackground(
                    AppSurfaceTokens.videoCardContainer(
                        glassEnabled = LocalHomeCardDynamicTintEnabled.current || LocalHomeCardFrostedGlassEnabled.current,
                    ),
                    cardShape,
                )
            )
            .combinedClickable(
                onClick = triggerClick,
                onLongClick = if (isLongPressEnabled) onLongClick else null,
            ),
        coverModifier = Modifier.onGloballyPositioned { coordinates ->
            coverBounds.value = coordinates.videoCardSourceBoundsInRoot()
        },
        infoModifier = Modifier.onGloballyPositioned { coordinates ->
            infoCoordinates.value = coordinates
        },
        coverOverlayModifier = nativeCardSnapshot.coverOverlayModifier,
        coverContent = {
            com.android.purebilibili.core.ui.components.PageAwareAsyncImage(
                dimInDarkTheme = true,
                loadingAllowed = com.android.purebilibili.feature.home.LocalHomeCardCoverLoadingAllowed.current
                    ?: com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed.current,
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
        coverOverlayContent = {
            VideoStatusBadges(
                labels = video.statusLabels,
                modifier = Modifier.align(Alignment.TopEnd).padding(AppSpacingTokens.ExtraSmall),
            )
            if (durationStyle == com.android.purebilibili.core.store.HomeDurationStyle.HIDDEN) {
                return@HorizontalVideoCardFrame
            }
            VideoCardCoverDurationText(
                text = FormatUtils.formatDuration(video.duration),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        },
        infoContent = {
            AppText(
                text = highlightedTitle ?: androidx.compose.ui.text.AnnotatedString(video.title),
                modifier = Modifier.fillMaxWidth(),
                style = contentTypography.title,
                maxLines = videoCardTitleMaxLines(),
                minLines = if (compactPartition) 1 else minOf(2, videoCardTitleMaxLines()),
                overflow = videoCardTitleOverflow(),
                color = AppContentColors.primaryText(),
            )

            if (!compactPartition) Spacer(Modifier.weight(1f))
            if (compactPartition) {
                // 与 PiliPlus 一致：标题占据上方弹性空间，UP 主与播放量等元信息固定在卡片底部。
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (publishTimeText.isNotBlank()) {
                        AppText(
                            text = publishTimeText + "  ",
                            style = contentTypography.author,
                            color = AppContentColors.secondaryText(),
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    UpBadgeName(
                        name = video.owner.name,
                        leadingContent = if (com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current.showAvatars && video.owner.face.isNotBlank()) {
                            {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(FormatUtils.fixImageUrl(
                                        video.owner.face
                                    )).crossfade(false).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(16.dp).clip(CircleShape),
                                )
                            }
                        } else null,
                        nameStyle = contentTypography.author,
                        nameColor = AppContentColors.secondaryText(),
                        showUpBadge = showUpBadge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (publishTimeText.isNotBlank()) {
                    AppText(
                        text = "$publishTimeText  ",
                        style = contentTypography.author,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                UpBadgeName(
                    name = video.owner.name,
                    leadingContent = if (
                        com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current.showAvatars &&
                        video.owner.face.isNotBlank()
                    ) {
                        {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(FormatUtils.fixImageUrl(
                                        video.owner.face
                                    ))
                                    .crossfade(false)
                                    .build(),
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
                    nameLeadingContent = if (isFollowing || video.isFollowed || video.recommendationReason.isNotBlank()) {
                        {
                            Row(
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VideoRecommendationBadge(video.recommendationReason)
                                if (isFollowing || video.isFollowed) {
                                    AppText(
                                        text = "已关注",
                                        style = contentTypography.coverBadge,
                                        color = AppContentColors.accent(),
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
                    nameStyle = contentTypography.author,
                    nameColor = AppContentColors.secondaryText(),
                    badgeTextColor = AppContentColors.secondaryText().copy(alpha = 0.85f),
                    badgeBorderColor = AppContentColors.secondaryText().copy(alpha = 0.35f),
                    showUpBadge = showUpBadge,
                    // The info column is a single horizontal-card row; keep the owner on one
                    // measured line so a long name cannot grow past the cover bounds.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalVideoStatRow(
                playText = FormatUtils.formatStat(video.stat.view.toLong()),
                danmakuText = FormatUtils.formatStat(video.stat.danmaku.toLong()),
            )
        },
        trailingContent = when {
            trailingContent != null -> {
                {
                    trailingContent()
                }
            }
            onMoreClick != null -> {
                {
                    Box(
                        modifier = Modifier
                            .size(AppChromeSizeTokens.MinimumTouchTarget)
                            .clip(CircleShape)
                            .semantics { contentDescription = "更多操作" }
                            .appActionClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                moreHaptic(HapticType.LIGHT)
                                onMoreClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            else -> null
        },
    )
}
