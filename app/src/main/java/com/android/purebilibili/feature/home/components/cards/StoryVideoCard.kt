// 文件路径: feature/home/components/cards/StoryVideoCard.kt
package com.android.purebilibili.feature.home.components.cards

import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.FeedTitleHierarchy
import com.android.purebilibili.core.ui.feedContentTypography

import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.feature.home.LocalHomeCardCoverLoadingAllowed
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.iOSCardTapEffect
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchEntry
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchRegistry
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween

import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.adaptiveCardHoverEffect
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.resolveUpStatsText
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalMiuixVideoCardTransitionState
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.feature.home.HomeCoverRequestSpec
import com.android.purebilibili.feature.home.resolveHomeCoverImageSource
import com.android.purebilibili.feature.home.resolveHomeCardEnterAnimationEnabledAtMount
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import kotlin.math.roundToInt

/**
 *  故事卡片 - 影院海报风格
 *
 * 特点：
 * - 封面比例由首页卡片样式统一配置
 * - 大圆角 (24dp)
 * - 标题叠加在封面底部
 * - 沉浸电影感
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
internal fun StoryVideoCard(
    video: VideoItem,
    index: Int = 0,  //  [新增] 索引用于动画延迟
    animationEnabled: Boolean = true,  //  卡片动画开关
    motionTier: MotionTier = MotionTier.Normal,
    transitionEnabled: Boolean = false, //  卡片过渡动画开关
    sharedElementSourceRoute: String? = null,
    isReturningFromVideoDetail: Boolean = false,
    isQuickReturningFromVideoDetail: Boolean = false,
    scrollLiteModeEnabled: Boolean = false,
    isDataSaverActive: Boolean = false,
    preferLowQualityCover: Boolean = false,
    coverRequestSpec: HomeCoverRequestSpec? = null,
    showCoverGlassBadges: Boolean = false,
    showInfoGlassBadges: Boolean = false,
    showUpBadge: Boolean? = null,
    showUpAvatar: Boolean? = null,
    homeDurationStyle: HomeDurationStyle = HomeDurationStyle.OUTSIDE_COVER,
    coverAspectRatio: Float = com.android.purebilibili.core.ui.videoCardCoverAspectRatio(),
    cardHorizontalPadding: Dp = AppSpacingTokens.None,
    compactMetadata: Boolean = true,
    titleMinLines: Int = 1,
    titleMaxLines: Int = 2,
    showOnlineCount: Boolean = false,
    showPublishTime: Boolean = false,
    upFollowerCount: Int? = null,
    upVideoCount: Int? = null,
    onDismiss: (() -> Unit)? = null,    //  [新增] 删除/过滤回调（长按触发）
    onUpClick: ((Long) -> Unit)? = null,
    onLongClick: ((VideoItem) -> Unit)? = null, // [修复] 长按预览回调
    modifier: Modifier = Modifier,
    onClick: (String, Long) -> Unit
) {
    val contentTypography = feedContentTypography(
        titleHierarchy = if (compactMetadata) {
            FeedTitleHierarchy.Compact
        } else {
            FeedTitleHierarchy.Standard
        },
    )
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)
    val longPressHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.LONG_PRESS)

    val isLongPressEnabled = com.android.purebilibili.core.ui.LocalVideoCardLongPressEnabled.current
    val cardCornerRadius = AppShapes.containerCornerDp(ContainerLevel.ProminentCard)
    val cardShape = AppShapes.container(ContainerLevel.ProminentCard)
    val coverShape = cardShape
    val durationText = remember(video.duration) { FormatUtils.formatDuration(video.duration) }
    val showDurationOnCover = homeDurationStyle == HomeDurationStyle.OVERLAY_TEXT_ONLY
    val coverOverlayTextStyle = remember {
        TextStyle(shadow = resolveVideoCardCoverOverlayTextShadow())
    }
    val showDurationOutside = homeDurationStyle == HomeDurationStyle.OUTSIDE_COVER
    val scrollLitePolicy = remember {
        resolveStoryVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = false
        )
    }
    val badgeStylePolicy = remember(showCoverGlassBadges, showInfoGlassBadges) {
        resolveHomeVideoGlassBadgeStylePolicy(
            showCoverGlassBadges = showCoverGlassBadges,
            showInfoGlassBadges = showInfoGlassBadges
        )
    }
    val premiumBadgeLabel = remember(video.rights) {
        resolveVideoPremiumBadgeLabel(video.rights)
    }
    var cardVisibleInWindow by remember(video.bvid, video.cid) { mutableStateOf(false) }
    val onlineCount = rememberVideoCardOnlineCount(
        video = video,
        showOnlineCount = showOnlineCount,
        isVisibleInWindow = cardVisibleInWindow,
    )
    val useLowQualityCover = isDataSaverActive && preferLowQualityCover
    val coverSource = remember(video.bvid, video.id, video.cid, video.title, video.pic, useLowQualityCover, coverRequestSpec) {
        resolveHomeCoverImageSource(video, useLowQualityCover, coverRequestSpec)
    }
    val coverUrl = coverSource.url
    val coverCacheKey = coverSource.cacheKey
    var measuredCoverSize by remember(coverCacheKey) {
        mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    }
    val publishTimeRowText = rememberVideoCardPublishTimeText(video, showPublishTime, cardVisibleInWindow)
    val emphasizePublishTime = false

    //  [新增] 长按删除菜单状态
    var showDismissMenu by remember { mutableStateOf(false) }

    //  获取屏幕尺寸用于计算归一化坐标
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val localSharedElementSourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val effectiveSharedElementSourceRoute = remember(sharedElementSourceRoute, localSharedElementSourceRoute) {
        sharedElementSourceRoute ?: localSharedElementSourceRoute
    }

    //  记录卡片位置
    val cardCoordinates = remember { object { var value: LayoutCoordinates? = null } }
    val coverCoordinates = remember { object { var value: LayoutCoordinates? = null } }
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(video.bvid)
    val triggerCardClick by rememberUpdatedState(appActionWithFeedback {
        if (CardPositionManager.isVideoNavigationDebounceActive()) {
            // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
            return@appActionWithFeedback
        }
        cardCoordinates.value?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()?.let { bounds ->
            CardPositionManager.recordVideoCardPosition(
                bvid = video.bvid,
                sourceRoute = effectiveSharedElementSourceRoute,
                bounds = bounds,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                isSingleColumn = !transitionEnabled,
                sourceCornerDp = cardCornerRadius.value.roundToInt(),
                coverBounds = coverCoordinates.value?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot(),
                sourceLayout = com.android.purebilibili.core.ui.transition.VideoCardSourceLayout.STACKED,
                sourceChromeSnapshot = com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot(
                    title = video.title,
                    ownerName = video.owner.name,
                    ownerFaceUrl = video.owner.face,
                    recommendationReason = video.recommendationReason,
                    followed = video.isFollowed,
                    viewText = FormatUtils.formatStat(video.stat.view.toLong()),
                    danmakuText = FormatUtils.formatStat(video.stat.danmaku.toLong()),
                    durationText = FormatUtils.formatDuration(video.duration),
                    infoPresentation = com.android.purebilibili.core.ui.transition
                        .resolveVideoCardSourceInfoPresentation(
                            publishTimeText = "",
                            showStatsInInfo = false,
                        ),
                    coverUrl = coverUrl,
                    coverCacheKey = coverCacheKey,
                    coverDecodeWidthPx = coverRequestSpec?.widthPx ?: 0,
                    coverDecodeHeightPx = coverRequestSpec?.heightPx ?: 0,
                ),
            )
            nativeCardSnapshot.capture()
        }
        onClick(video.bvid, 0)
    })

    //  尝试获取共享元素作用域
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val effectiveTransitionEnabled = transitionEnabled
    val coverSharedEnabled = shouldEnableVideoCoverSharedTransition(
        transitionEnabled = effectiveTransitionEnabled && LocalSharedTransitionEnabled.current,
        hasSharedTransitionScope = sharedTransitionScope != null,
        hasAnimatedVisibilityScope = animatedVisibilityScope != null
    )
    val isQuickReturnLimited = isReturningFromVideoDetail && isQuickReturningFromVideoDetail
    val useCardShellSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = effectiveSharedElementSourceRoute,
        transitionEnabled = coverSharedEnabled
    )
    val isSharedReturnTarget = remember(
        video.bvid,
        effectiveSharedElementSourceRoute,
        CardPositionManager.lastClickedVideoSourceKey,
    ) {
        isVideoCardSharedReturnTarget(
            bvid = video.bvid,
            sourceRoute = effectiveSharedElementSourceRoute,
            lastClickedVideoSourceKey = CardPositionManager.lastClickedVideoSourceKey,
        )
    }
    val coverCrossfadeEnabled = shouldEnableVideoCardCoverCrossfade(
        isScrollInProgress = false,
        isReturningFromDetail = isReturningFromVideoDetail,
        useCoverSharedBounds = useCardShellSharedBounds ||
            (LocalMiuixVideoCardTransitionState.current.enabled && isSharedReturnTarget),
        isSharedReturnTarget = isSharedReturnTarget,
    )
    val context = LocalContext.current
    val coverImageRequest = remember(context, coverUrl, coverCacheKey, coverRequestSpec, coverCrossfadeEnabled) {
        ImageRequest.Builder(context)
            .data(coverUrl)
            .apply { coverRequestSpec?.let { size(it.widthPx, it.heightPx) } }
            .crossfade(coverCrossfadeEnabled)
            .memoryCacheKey(coverCacheKey)
            .diskCacheKey(coverCacheKey)
            .build()
    }
    val coverWorkAllowed = LocalPageImageLoadingAllowed.current && cardVisibleInWindow
    LaunchedEffect(video.bvid, coverUrl, coverCacheKey, coverRequestSpec, measuredCoverSize, coverWorkAllowed) {
        if (!coverWorkAllowed) return@LaunchedEffect
        HomeCoverReturnPrefetchRegistry.onCardVisible(
            HomeCoverReturnPrefetchEntry(
                bvid = video.bvid.trim(),
                url = coverUrl,
                cacheKey = coverCacheKey,
                decodeWidthPx = coverRequestSpec?.widthPx ?: measuredCoverSize.width,
                decodeHeightPx = coverRequestSpec?.heightPx ?: measuredCoverSize.height,
            ),
        )
    }
    val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
        .LocalVideoTransitionAdaptiveInfo.current
    val cardSharedTransitionMotionSpec = remember(
        effectiveSharedElementSourceRoute,
        effectiveTransitionEnabled,
        sharedTransitionSpeedSettings,
        transitionAdaptiveInfo,
    ) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = effectiveSharedElementSourceRoute,
            transitionEnabled = effectiveTransitionEnabled,
            speedSettings = sharedTransitionSpeedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }

    val cardShellShape = cardShape
    val homeCardScrolling = LocalHomeCardScrolling.current
    val homeCardTransitionAtMount = LocalHomeCardTransitionRunning.current
    val enterAnimationEnabledAtMount = remember(video.bvid) {
        resolveHomeCardEnterAnimationEnabledAtMount(
            baseAnimationEnabled = animationEnabled,
            // Mount-only sampling must not subscribe every neighboring card to transition changes.
            isReturningFromDetail = isReturningFromVideoDetail ||
                Snapshot.withoutReadObservation { homeCardTransitionAtMount?.value == true },
            isSwitchingCategory = CardPositionManager.isSwitchingCategory,
            // Sample once; a card mounted while scrolling stays visible after settling.
            isScrollInProgress = scrollLiteModeEnabled || homeCardScrolling?.value == true
        )
    }
    val coordinateEnterWithTransition = remember(animationEnabled, transitionEnabled) {
        animationEnabled && transitionEnabled
    }

    Column(
        modifier = modifier
            .onVisibilityChanged(minFractionVisible = 0.01f, minDurationMs = 0L) {
                cardVisibleInWindow = it
            }
            .fillMaxWidth()
            .adaptiveCardHoverEffect(shape = cardShellShape)
            .videoCardShellSharedBoundsOrEmpty(
                enabled = useCardShellSharedBounds,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                bvid = video.bvid,
                sourceRoute = effectiveSharedElementSourceRoute,
                motionSpec = cardSharedTransitionMotionSpec,
                clipShape = cardShellShape
            )
            .padding(horizontal = cardHorizontalPadding, vertical = AppSpacingTokens.Small)
            .animateEnter(
                index = index,
                key = Unit,
                animationEnabled = enterAnimationEnabledAtMount,
                motionTier = motionTier,
                coordinateWithSharedTransition = coordinateEnterWithTransition
            )
            //  [新增] 记录卡片位置
            .then(nativeCardSnapshot.modifier)
            .onGloballyPositioned { coordinates ->
                cardCoordinates.value = coordinates
            }
            .pointerInput(isLongPressEnabled, onDismiss, onLongClick) {
                 val hasLongPressAction = isLongPressEnabled && (onDismiss != null || onLongClick != null)
                 if (hasLongPressAction) {
                     detectTapGestures(
                         onLongPress = {
                             if (onLongClick != null) {
                                 longPressHaptic(HapticType.HEAVY)
                                 onLongClick(video)
                             } else if (onDismiss != null) {
                                 longPressHaptic(HapticType.HEAVY)
                                 showDismissMenu = true
                             }
                         },
                         onTap = {
                             triggerCardClick()
                         }
                     )
                 }
            }
            .then(
                 if (!isLongPressEnabled || (onDismiss == null && onLongClick == null)) {
                     Modifier.iOSCardTapEffect(
                         pressScale = 1f,
                         pressTranslationY = 0f,
                         hapticEnabled = true
                     ) {
                         triggerCardClick()
                     }
                 } else Modifier
            )
    ) {
        // 卡片容器 (封面)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .videoCardShellReturnCoverAlpha(
                    enabled = useCardShellSharedBounds,
                    bvid = video.bvid,
                    sourceRoute = effectiveSharedElementSourceRoute,
                    isReturningFromDetail = isReturningFromVideoDetail,
                )
                .testTag("home_story_video_cover")
                .aspectRatio(coverAspectRatio)
                .onGloballyPositioned { coordinates ->
                    coverCoordinates.value = coordinates
                    if (coverRequestSpec == null) measuredCoverSize = coordinates.size
                }
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant) // 封面占位色
        ) {
            // 封面比例由首页卡片样式统一配置。
            PageAwareAsyncImage(
                model = coverImageRequest,
                dimInDarkTheme = true,
                loadingAllowed = LocalHomeCardCoverLoadingAllowed.current
                    ?: LocalPageImageLoadingAllowed.current,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(nativeCardSnapshot.coverOverlayModifier),
            ) {
            com.android.purebilibili.core.ui.components.VideoStatusBadges(
                labels = video.statusLabels.ifEmpty { listOfNotNull(premiumBadgeLabel) },
                modifier = Modifier.align(Alignment.TopEnd).padding(AppSpacingTokens.Small),
            )
            //  时长标签 (保留在封面上)
            if (showDurationOnCover) {
                AppText(
                    text = durationText,
                    color = MediaContrastPalette.Foreground,
                    style = contentTypography.coverBadge
                        .copy(fontWeight = FontWeight.Medium)
                        .merge(coverOverlayTextStyle),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(AppSpacingTokens.Small + AppSpacingTokens.Micro)
                )
            }
            }
        }

        Column(
            modifier = Modifier.videoCardShellReturnChromeAlpha(
                enabled = useCardShellSharedBounds,
                bvid = video.bvid,
                sourceRoute = effectiveSharedElementSourceRoute,
                isReturningFromDetail = isReturningFromVideoDetail,
                isQuickReturnFromDetail = isQuickReturningFromVideoDetail,
            )
        ) {
        AppText(
            text = video.title,
            color = AppContentColors.primaryText(),
            style = contentTypography.title,
            minLines = titleMinLines,
            maxLines = videoCardTitleMaxLines(titleMaxLines),
            overflow = videoCardTitleOverflow(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(if (compactMetadata) AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro else AppSpacingTokens.Small))

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (scrollLitePolicy.showSecondaryStatsRow && video.stat.view > 0) {
                FlowRow(
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HORIZONTAL_VIDEO_STAT_ROW_SPACING_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(HORIZONTAL_VIDEO_STAT_WRAP_SPACING_DP.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HorizontalVideoStatRow(
                        playText = FormatUtils.formatStat(video.stat.view.toLong()),
                        danmakuText = if (video.stat.danmaku > 0) {
                            FormatUtils.formatStat(video.stat.danmaku.toLong())
                        } else {
                            ""
                        },
                    )
                    if (publishTimeRowText.isNotBlank()) {
                        AppText(
                            text = publishTimeRowText,
                            color = AppContentColors.secondaryText(),
                            style = contentTypography.statistic,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
            }

            val upClickMid = video.owner.mid.takeIf { it > 0L && onUpClick != null }
            val upNameModifier = if (upClickMid != null) {
                Modifier.fillMaxWidth().appActionClickable { onUpClick?.invoke(upClickMid) }
            } else {
                Modifier.fillMaxWidth()
            }

            UpBadgeName(
                name = video.owner.name,
                metaText = resolveUpStatsText(
                    followerCount = upFollowerCount,
                    videoCount = upVideoCount
                ),
                leadingContent = if ((showUpAvatar ?: com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current.showAvatars) && video.owner.face.isNotEmpty()) {
                    {
                        val avatarContext = LocalContext.current
                        val face = video.owner.face
                        val avatarRequest = remember(avatarContext, face) {
                            ImageRequest.Builder(avatarContext)
                                .data(FormatUtils.fixImageUrl(face))
                                .size(96, 96)
                                .crossfade(100)
                                .build()
                        }
                        PageAwareAsyncImage(
                            model = avatarRequest,
                            contentDescription = null,
                            modifier = Modifier
                                .size(AppSpacingTokens.ExtraLarge)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else null,
                nameLeadingContent = if (video.recommendationReason.isNotBlank() || video.isFollowed) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            VideoRecommendationBadge(video.recommendationReason)
                            if (video.isFollowed) VideoRecommendationBadge("已关注")
                        }
                    }
                } else null,
                nameStyle = contentTypography.author.copy(fontWeight = FontWeight.Medium),
                nameColor = AppContentColors.secondaryText(),
                metaColor = AppContentColors.accent(),
                badgeTextColor = AppContentColors.secondaryText().copy(alpha = 0.85f),
                badgeBackgroundColor = AppContentColors.secondaryText().copy(alpha = 0.12f),
                showUpBadge = showUpBadge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                metaMaxLines = Int.MAX_VALUE,
                metaOverflow = TextOverflow.Visible,
                modifier = upNameModifier
            )

            if (!scrollLitePolicy.showSecondaryStatsRow) {
                VideoCardDurationPublishRow(
                    durationText = "",
                    publishTimeText = publishTimeRowText,
                    emphasizePublishTime = emphasizePublishTime,
                    publishTimeColor = AppContentColors.secondaryText().copy(alpha = 0.72f),
                    topSpacing = if (compactMetadata) AppSpacingTokens.ExtraSmall else AppSpacingTokens.Small
                )
            }

        }
        }
    }

    //  [新增] 长按删除菜单
    AppDropdownMenu(
        expanded = showDismissMenu,
        onDismissRequest = { showDismissMenu = false }
    ) {
        AppDropdownMenuItem(
            text = {
                AppText(
                    "不感兴趣",
                    color = AppContentColors.primaryText()
                )
            },
            onClick = {
                showDismissMenu = false
                onDismiss?.invoke()
            }
        )
    }
}
