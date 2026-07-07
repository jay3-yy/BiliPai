package com.android.purebilibili.feature.home.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
//  Cupertino Icons
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.theme.LocalCornerRadiusScale
import com.android.purebilibili.core.theme.iOSCornerRadius
import com.android.purebilibili.core.util.HapticType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.components.resolveUpStatsText
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionVisualSpec
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionOwnership
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionPlaybackIntent
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionVisualSpec
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.feature.home.resolveHomeCardEnterAnimationEnabledAtMount
import com.android.purebilibili.feature.home.resolveHomeCardInfoSurfaceAppearance
import com.android.purebilibili.feature.home.HomeGlassPillStyle
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.feature.home.resolveHomeGlassCoverPillBaseColor
import com.android.purebilibili.feature.home.resolveHomeGlassPillStyle
import com.android.purebilibili.feature.video.controller.PlaybackProgressManager
import com.android.purebilibili.feature.video.ui.section.resolveCompactPublishTimeRowText
//  [预览播放] 相关引用已移除

// 显式导入 collectAsState 以避免 ambiguity 或 missing reference
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

internal fun shouldOpenLongPressMenu(
    hasPreviewAction: Boolean,
    hasMenuAction: Boolean
): Boolean = !hasPreviewAction && hasMenuAction

internal fun resolveVideoCardMenuOffset(
    rootBoundsInRoot: androidx.compose.ui.geometry.Rect?,
    anchorBoundsInRoot: androidx.compose.ui.geometry.Rect?,
    density: Float,
    pressOffsetInAnchorPx: Offset? = null
): DpOffset {
    if (rootBoundsInRoot == null || anchorBoundsInRoot == null || density <= 0f) {
        return DpOffset.Zero
    }

    val anchorPointInRoot = if (pressOffsetInAnchorPx != null) {
        Offset(
            x = anchorBoundsInRoot.left + pressOffsetInAnchorPx.x,
            y = anchorBoundsInRoot.top + pressOffsetInAnchorPx.y
        )
    } else {
        Offset(
            x = anchorBoundsInRoot.left,
            y = anchorBoundsInRoot.bottom
        )
    }

    val localX = (anchorPointInRoot.x - rootBoundsInRoot.left).coerceAtLeast(0f)
    val localY = (anchorPointInRoot.y - rootBoundsInRoot.top).coerceAtLeast(0f)
    return DpOffset(
        x = (localX / density).dp,
        y = (localY / density).dp
    )
}

internal fun resolveVideoCardCoverCacheKey(
    video: VideoItem,
    useLowQualityCover: Boolean
): String {
    val normalizedIdentity = video.bvid.trim().ifEmpty {
        video.pic.trim().ifEmpty {
            "fallback_${video.id.coerceAtLeast(0L)}_${video.cid.coerceAtLeast(0L)}_${video.title.hashCode()}"
        }
    }
    val qualityTag = if (useLowQualityCover) "s" else "n"
    return "cover_${normalizedIdentity}_${qualityTag}"
}

private data class VideoCardTexts(
    val durationText: String,
    val primaryStatText: String,
    val secondaryStatText: String?,
    val durationBadgeMinWidth: androidx.compose.ui.unit.Dp
)

private data class VideoCardPillColors(
    val cover: HomeGlassResolvedColors,
    val emphasizedCover: HomeGlassResolvedColors,
    val inline: HomeGlassResolvedColors
)

private data class VideoCardSharedTransitionSpecs(
    val motion: VideoSharedTransitionMotionSpec,
    val visual: VideoSharedTransitionVisualSpec
)

internal data class HomeVideoCardMetadataColors(
    val upNameColor: Color,
    val upMetaColor: Color,
    val upBadgeTextColor: Color,
    val upBadgeBackgroundColor: Color,
    val publishTimeColor: Color
)

internal fun resolveHomeVideoCardMetadataColors(
    onSurfaceColor: Color
): HomeVideoCardMetadataColors {
    return HomeVideoCardMetadataColors(
        upNameColor = onSurfaceColor,
        upMetaColor = onSurfaceColor.copy(alpha = 0.82f),
        upBadgeTextColor = onSurfaceColor.copy(alpha = 0.68f),
        upBadgeBackgroundColor = onSurfaceColor.copy(alpha = 0.10f),
        publishTimeColor = onSurfaceColor.copy(alpha = 0.72f)
    )
}

@Composable
internal fun VideoCardDurationPublishRow(
    durationText: String,
    publishTimeText: String,
    emphasizePublishTime: Boolean,
    publishTimeColor: Color,
    topSpacingDp: Int
) {
    if (durationText.isBlank() && publishTimeText.isBlank()) return

    Spacer(modifier = Modifier.height(topSpacingDp.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (publishTimeText.isNotBlank()) {
            VideoCardPublishTime(
                text = publishTimeText,
                emphasized = emphasizePublishTime,
                color = publishTimeColor,
                modifier = Modifier.weight(1f, fill = !emphasizePublishTime)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (durationText.isNotBlank()) {
            Text(
                text = durationText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VideoCardPublishTime(
    text: String,
    emphasized: Boolean,
    color: Color,
    modifier: Modifier
) {
    if (emphasized) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = AppShapes.container(ContainerLevel.Pill),
            modifier = modifier
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    } else {
        Text(
            text = text,
            fontSize = 11.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

private fun resolveVideoCardPillColors(
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    inlineBaseColor: Color
): VideoCardPillColors {
    val coverBaseColor = resolveHomeGlassCoverPillBaseColor()
    return VideoCardPillColors(
        cover = resolveVideoCardPillColors(
            style = resolveHomeGlassPillStyle(
                glassEnabled = glassEnabled,
                blurEnabled = blurEnabled,
                emphasized = false
            ),
            baseColor = coverBaseColor
        ),
        emphasizedCover = resolveVideoCardPillColors(
            style = resolveHomeGlassPillStyle(
                glassEnabled = glassEnabled,
                blurEnabled = blurEnabled,
                emphasized = true
            ),
            baseColor = coverBaseColor
        ),
        inline = resolveVideoCardPillColors(
            style = resolveHomeGlassPillStyle(
                glassEnabled = glassEnabled,
                blurEnabled = blurEnabled,
                emphasized = false
            ),
            baseColor = inlineBaseColor
        )
    )
}

private fun resolveVideoCardPillColors(
    style: HomeGlassPillStyle,
    baseColor: Color
): HomeGlassResolvedColors {
    return HomeGlassResolvedColors(
        containerColor = baseColor.copy(alpha = style.containerAlpha),
        borderColor = Color.White.copy(alpha = style.borderAlpha),
        highlightColor = Color.White.copy(alpha = style.highlightAlpha)
    )
}

/**
 *  官方 B 站风格视频卡片
 * 采用与 Bilibili 官方 App 一致的设计：
 * - 封面比例由首页卡片样式统一配置
 * - 左下角：播放量 + 弹幕数
 * - 右下角：时长
 * - 标题：2行
 * - 底部：「已关注」标签 + UP主名称
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ElegantVideoCard(
    video: VideoItem,
    index: Int,
    refreshKey: Long = 0L,
    isFollowing: Boolean = false,  //  是否已关注该 UP 主
    animationEnabled: Boolean = true,   //  卡片进场动画开关
    motionTier: MotionTier = MotionTier.Normal,
    transitionEnabled: Boolean = false, //  卡片过渡动画开关
    sharedElementSourceRoute: String? = null,
    isReturningFromVideoDetail: Boolean = false,
    isQuickReturningFromVideoDetail: Boolean = false,
    scrollLiteModeEnabled: Boolean = false,
    showPublishTime: Boolean = false,   //  是否显示发布时间（搜索结果用）
    isDataSaverActive: Boolean = false, // 🚀 [性能优化] 从父级传入，避免每个卡片重复计算
    preferLowQualityCover: Boolean = false,
    glassEnabled: Boolean = true,
    blurEnabled: Boolean = true,
    compactStatsOnCover: Boolean = true, // 播放量/评论数是否贴在封面底部
    showCoverGlassBadges: Boolean = true,
    showInfoGlassBadges: Boolean = true,
    wallpaperTintEnabled: Boolean = false,
    wallpaperEffectMode: HomeWallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
    showUpBadge: Boolean = true,
    homeDurationStyle: HomeDurationStyle = HomeDurationStyle.OUTSIDE_COVER,
    coverAspectRatio: Float = 4f / 3f,
    compactMetadata: Boolean = true,
    titleMinLines: Int = 2,
    highlightedTitle: AnnotatedString? = null,
    showOnlineCount: Boolean = false,
    upFollowerCount: Int? = null,
    upVideoCount: Int? = null,
    onDismiss: (() -> Unit)? = null,    //  [新增] 删除/过滤回调（长按触发）
    onWatchLater: (() -> Unit)? = null,  //  [新增] 稍后再看回调
    onUnfavorite: (() -> Unit)? = null,  //  [新增] 取消收藏回调
    dismissMenuText: String = "\uD83D\uDEAB 不感兴趣", //  [新增] 自定义长按菜单删除文案
    onLongClick: ((VideoItem) -> Unit)? = null, // [Feature] Long Press Preview
    onUpClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: (String, Long) -> Unit
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playbackProgressManager = remember(context) {
        PlaybackProgressManager.getInstance(context)
    }
    
    //  [HIG] 动态圆角 - 12dp 标准
    val cornerRadiusScale = LocalCornerRadiusScale.current
    val cardCornerRadius = 12.dp * cornerRadiusScale  // HIG 标准圆角
    val smallCornerRadius = iOSCornerRadius.Tiny * cornerRadiusScale  // 4.dp * scale
    val durationBadgeStyle = remember { resolveVideoCardDurationBadgeVisualStyle() }
    val cardTexts = remember(video.duration, video.stat.view, video.stat.reply, video.stat.danmaku, video.progress) {
        val durationText = FormatUtils.formatDuration(video.duration)
        val primaryStatText = if (video.stat.view > 0) {
            FormatUtils.formatStat(video.stat.view.toLong())
        } else {
            FormatUtils.formatProgress(video.progress, video.duration)
        }
        val commentCount = video.stat.reply.takeIf { it > 0 } ?: video.stat.danmaku
        val secondaryStatText = commentCount.takeIf { it > 0 }?.let { FormatUtils.formatStat(it.toLong()) }
        val durationBadgeMinWidth = resolveVideoCardDurationBadgeMinWidthDp(
            durationText = durationText,
            style = durationBadgeStyle
        ).dp
        VideoCardTexts(durationText, primaryStatText, secondaryStatText, durationBadgeMinWidth)
    }
    val durationText = cardTexts.durationText
    val primaryStatText = cardTexts.primaryStatText
    val secondaryStatText = cardTexts.secondaryStatText
    val durationBadgeMinWidth = cardTexts.durationBadgeMinWidth
    val showDurationOnCover = homeDurationStyle == HomeDurationStyle.OVERLAY_TEXT_ONLY
    val showDurationOutside = homeDurationStyle == HomeDurationStyle.OUTSIDE_COVER
    val inlinePillBaseColor = AppSurfaceTokens.cardContainer()
    val pillColors = remember(glassEnabled, blurEnabled, inlinePillBaseColor) {
        resolveVideoCardPillColors(
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled,
            inlineBaseColor = inlinePillBaseColor
        )
    }
    val coverPillColors = pillColors.cover
    val inlinePillColors = pillColors.inline
    val isDarkCardTheme = AppSurfaceTokens.chromeBackground().luminance() < 0.5f
    val infoSurfaceAppearance = remember(wallpaperTintEnabled, wallpaperEffectMode, isDarkCardTheme, isDataSaverActive) {
        resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = wallpaperTintEnabled,
            wallpaperEffectMode = wallpaperEffectMode,
            isDarkTheme = isDarkCardTheme,
            isDataSaverActive = isDataSaverActive
        )
    }
    val scrollLitePolicy = remember(scrollLiteModeEnabled, compactStatsOnCover) {
        resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = scrollLiteModeEnabled,
            compactStatsOnCover = compactStatsOnCover
        )
    }
    val badgeStylePolicy = remember(showCoverGlassBadges, showInfoGlassBadges) {
        resolveHomeVideoGlassBadgeStylePolicy(
            showCoverGlassBadges = showCoverGlassBadges,
            showInfoGlassBadges = showInfoGlassBadges
        )
    }
    val historyProgressState = remember(video.bvid, video.cid, video.view_at, video.duration, video.progress, refreshKey) {
        val localPositionMs = if (video.bvid.isNotBlank()) {
            playbackProgressManager.getCachedPosition(video.bvid, video.cid)
        } else {
            0L
        }
        resolveVideoCardHistoryProgressState(
            viewAt = video.view_at,
            durationSec = video.duration,
            progressSec = video.progress,
            localPositionMs = localPositionMs
        )
    }
    val showHistoryProgressBar = historyProgressState.showProgressBar
    val historyProgressFraction = historyProgressState.progressFraction
    val historyProgressBarColor = resolveVideoCardHistoryProgressBarColor(
        themePrimary = MaterialTheme.colorScheme.primary
    )
    val coverOverlayBottomLayout = remember(scrollLitePolicy.showHistoryProgressBar, showHistoryProgressBar) {
        resolveVideoCardCoverOverlayBottomLayout(
            showHistoryProgressBar = scrollLitePolicy.showHistoryProgressBar && showHistoryProgressBar
        )
    }
    
    //  [新增] 长按删除菜单状态
    var showDismissMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    //  [新增] 确认对话框状态
    var showUnfavoriteDialog by remember { mutableStateOf(false) }
    
    val useLowQualityCover = isDataSaverActive && preferLowQualityCover
    val coverCacheKey: String
    val coverUrl: String
    val premiumBadgeLabel: String?
    remember(video, useLowQualityCover) {
        Triple(
            resolveVideoCardCoverCacheKey(video = video, useLowQualityCover = useLowQualityCover),
            FormatUtils.resolveVideoCoverUrl(
                if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic,
                useLowQuality = useLowQualityCover
            ),
            resolveVideoPremiumBadgeLabel(video.rights)
        )
    }.let { (cache, url, badge) ->
        coverCacheKey = cache
        coverUrl = url
        premiumBadgeLabel = badge
    }
    val onlineCount = rememberVideoCardOnlineCount(
        video = video,
        showOnlineCount = showOnlineCount
    )
    val publishTimeRowText: String
    val emphasizePublishTime: Boolean
    remember(showPublishTime, video.pubdate) {
        if (!showPublishTime) {
            "" to false
        } else {
            resolveCompactPublishTimeRowText(pubdate = video.pubdate) to false
        }
    }.let { (text, emphasize) ->
        publishTimeRowText = text
        emphasizePublishTime = emphasize
    }
    
    //  判断是否为竖屏视频（通过封面图 URL 中的尺寸信息或默认不显示）
    // B站封面 URL 通常包含尺寸信息，如 width=X&height=Y
    // 简单方案：暂不显示竖屏标签（因推荐API不提供视频尺寸信息）

    //  获取屏幕尺寸用于计算归一化坐标
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx: Float
    val screenHeightPx: Float
    val densityValue: Float
    remember(configuration.screenWidthDp, configuration.screenHeightDp, density) {
        Triple(
            with(density) { configuration.screenWidthDp.dp.toPx() },
            with(density) { configuration.screenHeightDp.dp.toPx() },
            density.density
        )
    }.let { (w, h, d) ->
        screenWidthPx = w
        screenHeightPx = h
        densityValue = d
    }
    
    //  记录卡片位置（非 Compose State，避免滚动时触发高频重组）
    //  [性能优化] 存储 LayoutCoordinates 引用而非 Rect，boundsInRoot() 仅在交互时惰性计算，
    //  避免滚动期间每帧 4 次坐标树遍历开销。
    val cardCoordsRef = remember { object { var value: LayoutCoordinates? = null } }
    val coverCoordsRef = remember { object { var value: LayoutCoordinates? = null } }
    val titleCoordsRef = remember { object { var value: LayoutCoordinates? = null } }
    val menuButtonCoordsRef = remember { object { var value: LayoutCoordinates? = null } }
    val localSharedElementSourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val effectiveSharedElementSourceRoute = remember(sharedElementSourceRoute, localSharedElementSourceRoute) {
        sharedElementSourceRoute ?: localSharedElementSourceRoute
    }

    val openDismissMenu: (LayoutCoordinates?, Offset?) -> Unit = { anchorCoords, pressOffset ->
        menuOffset = resolveVideoCardMenuOffset(
            rootBoundsInRoot = cardCoordsRef.value?.takeIf { it.isAttached }?.boundsInRoot(),
            anchorBoundsInRoot = anchorCoords?.takeIf { it.isAttached }?.boundsInRoot(),
            density = densityValue,
            pressOffsetInAnchorPx = pressOffset
        )
        showDismissMenu = true
    }
    
    val triggerCardClick = {
        cardCoordsRef.value?.takeIf { it.isAttached }?.boundsInRoot()?.let { bounds ->
            CardPositionManager.recordVideoCardPosition(
                bvid = video.bvid,
                sourceRoute = effectiveSharedElementSourceRoute,
                bounds = bounds,
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx,
                density = densityValue,
                sourceCornerDp = cardCornerRadius.value.roundToInt()
            )
        }
        onClick(video.bvid, video.cid)
    }
    val enterAnimationEnabledAtMount = remember(video.bvid) {
        resolveHomeCardEnterAnimationEnabledAtMount(
            baseAnimationEnabled = animationEnabled,
            isReturningFromDetail = isReturningFromVideoDetail,
            isSwitchingCategory = CardPositionManager.isSwitchingCategory
        )
    }
    Box(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            //  [修复] 进场动画 - 使用 Unit 作为 key，只在首次挂载时播放
            // 原问题：使用 video.bvid 作为 key，分类切换时所有卡片重新触发动画（缩放收缩效果）
            .animateEnter(
                index = index, 
                key = Unit, 
                animationEnabled = enterAnimationEnabledAtMount,
                motionTier = motionTier
            )
            //  [新增] 记录卡片位置（仅存引用，boundsInRoot() 在交互时惰性计算）
            .onGloballyPositioned { coordinates ->
                cardCoordsRef.value = coordinates
            }
            .padding(bottom = 12.dp)
    ) {
        //  尝试获取共享元素作用域。首页点击视频时，由卡片主容器承载整体放大/回收。
        val sharedTransitionScope = LocalSharedTransitionScope.current
        val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
        val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
        val effectiveTransitionEnabled = transitionEnabled && LocalSharedTransitionEnabled.current
        val coverSharedEnabled = shouldEnableVideoCoverSharedTransition(
            transitionEnabled = effectiveTransitionEnabled,
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null
        )
        val isQuickReturnLimited = isReturningFromVideoDetail && isQuickReturningFromVideoDetail
        val sharedTransitionOwnership = resolveVideoSharedTransitionOwnership(
            sourceRoute = effectiveSharedElementSourceRoute,
            coverSharedEnabled = coverSharedEnabled,
            isQuickReturnLimited = isQuickReturnLimited
        )
        val videoSharedPlaybackIntent = remember(context) {
            resolveVideoSharedTransitionPlaybackIntent(
                clickToPlayEnabled = SettingsManager.getClickToPlaySync(context)
            )
        }
        val homeSharedTransitionSpecs = remember(
            effectiveSharedElementSourceRoute,
            effectiveTransitionEnabled,
            cardCornerRadius,
            sharedTransitionSpeedSettings,
            videoSharedPlaybackIntent
        ) {
            VideoCardSharedTransitionSpecs(
                motion = resolveVideoCardSharedTransitionMotionSpec(
                    sourceRoute = effectiveSharedElementSourceRoute,
                    transitionEnabled = effectiveTransitionEnabled,
                    speedSettings = sharedTransitionSpeedSettings
                ),
                visual = resolveVideoSharedTransitionVisualSpec(
                    sourceRoute = effectiveSharedElementSourceRoute,
                    sourceCornerDp = cardCornerRadius.value.roundToInt(),
                    playbackIntent = videoSharedPlaybackIntent
                )
            )
        }
        val homeSharedTransitionMotionSpec = homeSharedTransitionSpecs.motion
        val homeSharedTransitionVisualSpec = homeSharedTransitionSpecs.visual
        val useCardShellSharedBounds = sharedTransitionOwnership.useCardContainerSharedBounds
        val thisCardVideoSourceKey = remember(video.bvid, effectiveSharedElementSourceRoute) {
            val normalizedBvid = video.bvid.trim()
            val normalizedRoute = normalizeVideoCardSourceRouteForKey(effectiveSharedElementSourceRoute)
            if (normalizedBvid.isNotEmpty() && normalizedRoute != null) {
                "$normalizedRoute:$normalizedBvid"
            } else {
                null
            }
        }
        val isCoverSharedReturnTarget = thisCardVideoSourceKey != null &&
            thisCardVideoSourceKey == CardPositionManager.lastClickedVideoSourceKey
        val coverCrossfadeEnabled = shouldEnableVideoCardCoverCrossfade(
            isReturningFromDetail = isReturningFromVideoDetail,
            useCoverSharedBounds = useCardShellSharedBounds,
            isSharedReturnTarget = isCoverSharedReturnTarget
        )
        val cardShellShape = remember(cardCornerRadius) {
            RoundedCornerShape(cardCornerRadius)
        }
        val cardContainerModifier = Modifier
            .fillMaxWidth()
            .videoCardShellSharedBoundsOrEmpty(
                enabled = useCardShellSharedBounds,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                bvid = video.bvid,
                sourceRoute = effectiveSharedElementSourceRoute,
                motionSpec = homeSharedTransitionMotionSpec,
                clipShape = cardShellShape
            )
        Column(
            modifier = cardContainerModifier
        ) {
        //  [性能优化] 封面圆角形状缓存（避免重组时重复创建）
        val coverShape = remember(
            cardCornerRadius,
            infoSurfaceAppearance.useTintedSurface,
            homeSharedTransitionVisualSpec
        ) {
            if (infoSurfaceAppearance.useTintedSurface) {
                RoundedCornerShape(
                    topStart = cardCornerRadius,
                    topEnd = cardCornerRadius,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp
                )
            } else {
                RoundedCornerShape(homeSharedTransitionVisualSpec.sourceCornerDp.dp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_video_cover")
                .aspectRatio(coverAspectRatio)
                .clip(coverShape)
                .onGloballyPositioned { coordinates ->
                    coverCoordsRef.value = coordinates
                }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                //  [交互优化] 封面区域：点击跳转
                .pointerInput(onLongClick, onDismiss, onWatchLater, onUnfavorite) {
                    val hasPreviewAction = onLongClick != null
                    val hasLongPressMenu = onDismiss != null || onWatchLater != null || onUnfavorite != null
                    detectTapGestures(
                        onLongPress = { pressOffset ->
                            if (hasPreviewAction) {
                                haptic(HapticType.HEAVY)
                                onLongClick(video)
                            } else if (shouldOpenLongPressMenu(hasPreviewAction, hasLongPressMenu)) {
                                haptic(HapticType.HEAVY)
                                if (onUnfavorite != null && onDismiss == null && onWatchLater == null) {
                                    showUnfavoriteDialog = true
                                } else {
                                    openDismissMenu(coverCoordsRef.value, pressOffset)
                                }
                            }
                        },
                        onTap = {
                            triggerCardClick()
                        }
                    )
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(coverCrossfadeEnabled)
                    .memoryCacheKey(coverCacheKey)
                    .diskCacheKey(coverCacheKey)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (premiumBadgeLabel != null) {
                HomeVideoBadgePill(
                    style = badgeStylePolicy.coverStyle,
                    shape = RoundedCornerShape(smallCornerRadius),
                    containerColor = BiliPink.copy(alpha = if (badgeStylePolicy.coverStyle == HomeVideoBadgeStyle.GLASS) 0.78f else 1f),
                    borderColor = Color.White.copy(alpha = 0.24f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = premiumBadgeLabel,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            //  底部渐变遮罩

            if (scrollLitePolicy.showCoverGradientMask) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
            }

            if (scrollLitePolicy.showHistoryProgressBar && showHistoryProgressBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(coverOverlayBottomLayout.historyProgressBarHeightDp.dp)
                        .background(Color.White.copy(alpha = 0.24f))
                )
                if (historyProgressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(historyProgressFraction)
                            .height(coverOverlayBottomLayout.historyProgressBarHeightDp.dp)
                            .background(historyProgressBarColor)
                    )
                }
            }

            if (scrollLitePolicy.showCompactStatsOnCover) {
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = 8.dp,
                            top = 6.dp,
                            end = 8.dp,
                            bottom = coverOverlayBottomLayout.compactStatsBottomPaddingDp.dp
                        )
                ) {
                    val compactStatsLayout = remember(
                        maxWidth,
                        primaryStatText,
                        secondaryStatText,
                        onlineCount,
                        showDurationOnCover,
                        durationBadgeMinWidth
                    ) {
                        resolveVideoCardCompactCoverStatsLayout(
                            availableWidthDp = maxWidth.value,
                            primaryStatText = primaryStatText,
                            secondaryStatText = secondaryStatText,
                            hasOnlineCount = onlineCount.isNotEmpty(),
                            durationBadgeMinWidthDp = if (showDurationOnCover) {
                                durationBadgeMinWidth.value
                            } else {
                                0f
                            }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = compactStatsLayout.statsEndPaddingDp.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val compactViewsModifier = Modifier.widthIn(min = compactStatsLayout.primaryMinWidthDp.dp)
                        HomeVideoBadgePill(
                            modifier = compactViewsModifier,
                            style = badgeStylePolicy.coverStyle,
                            shape = AppShapes.container(ContainerLevel.Pill),
                            containerColor = coverPillColors.containerColor,
                            borderColor = coverPillColors.borderColor
                        ) {
                            Icon(
                                imageVector = CupertinoIcons.Outlined.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = Color.White.copy(alpha = 0.94f)
                            )
                            Text(
                                text = primaryStatText,
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (compactStatsLayout.showSecondaryStat && secondaryStatText != null) {
                            val compactDanmakuModifier = Modifier.widthIn(min = compactStatsLayout.secondaryMinWidthDp.dp)
                            HomeVideoBadgePill(
                                modifier = compactDanmakuModifier,
                                style = badgeStylePolicy.coverStyle,
                                shape = AppShapes.container(ContainerLevel.Pill),
                                containerColor = coverPillColors.containerColor,
                                borderColor = coverPillColors.borderColor
                            ) {
                                Icon(
                                    imageVector = CupertinoIcons.Outlined.BubbleLeft,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = Color.White.copy(alpha = 0.90f)
                                )
                                Text(
                                    text = secondaryStatText,
                                    color = Color.White.copy(alpha = 0.90f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (compactStatsLayout.showOnlineCount) {
                            HomeVideoBadgePill(
                                modifier = Modifier.weight(1f, fill = false),
                                style = badgeStylePolicy.coverStyle,
                                shape = AppShapes.container(ContainerLevel.Pill),
                                containerColor = coverPillColors.containerColor,
                                borderColor = coverPillColors.borderColor
                            ) {
                                Icon(
                                    imageVector = CupertinoIcons.Outlined.Eye,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = Color.White.copy(alpha = 0.90f)
                                )
                                Text(
                                    text = onlineCount,
                                    color = Color.White.copy(alpha = 0.90f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    //  时长标签 (与播放量/评论数同行对齐)
                    if (showDurationOnCover) {
                        Text(
                            text = durationText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            } else {
                //  非贴封面模式时，时长标签仍独立显示在右下角
                if (showDurationOnCover) {
                    Text(
                        text = durationText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 10.dp,
                                bottom = coverOverlayBottomLayout.floatingDurationBottomPaddingDp.dp
                            )
                    )
                }
            }
            
        }
        
        val infoSurfaceShape = remember(cardCornerRadius) {
            RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = cardCornerRadius,
                bottomEnd = cardCornerRadius
            )
        }
        val infoContainerModifier = if (infoSurfaceAppearance.useTintedSurface) {
            Modifier
                .fillMaxWidth()
                .background(
                    color = AppSurfaceTokens.cardContainer().copy(alpha = infoSurfaceAppearance.containerAlpha),
                    shape = infoSurfaceShape
                )
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = infoSurfaceAppearance.borderAlpha),
                    shape = infoSurfaceShape
                )
                .padding(
                    horizontal = 10.dp,
                    vertical = if (compactMetadata) 6.dp else 8.dp
                )
        } else {
            Modifier.fillMaxWidth()
        }

        Column(modifier = infoContainerModifier) {
        if (!infoSurfaceAppearance.useTintedSurface) {
            Spacer(modifier = Modifier.height(if (compactMetadata) 6.dp else 8.dp))
        }
        
        // 标题行：标题 + 更多按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            //  [HIG] 标题 - 15sp Medium, 行高 20sp
            //  共享元素过渡 - 标题
            val titleModifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "视频标题: ${video.title}" }

            Text(
                text = highlightedTitle ?: AnnotatedString(video.title),
                maxLines = 2,
                minLines = titleMinLines,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = if (compactMetadata) 14.sp else 15.sp,
                    lineHeight = if (compactMetadata) 18.sp else 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = titleModifier
                    .onGloballyPositioned { coordinates ->
                        titleCoordsRef.value = coordinates
                    }
                    //  [交互优化] 标题区域：长按弹出菜单，点击跳转
                    .pointerInput(onDismiss, onWatchLater, onUnfavorite) {
                        val hasPreviewAction = onLongClick != null
                        val hasLongPressMenu = onDismiss != null || onWatchLater != null || onUnfavorite != null
                        detectTapGestures(
                            onLongPress = { pressOffset ->
                                if (hasPreviewAction) {
                                  haptic(HapticType.HEAVY)
                                  onLongClick(video)
                                } else if (shouldOpenLongPressMenu(hasPreviewAction, hasLongPressMenu)) {
                                    haptic(HapticType.HEAVY)
                                    if (onUnfavorite != null && onDismiss == null && onWatchLater == null) {
                                        showUnfavoriteDialog = true
                                    } else {
                                        openDismissMenu(titleCoordsRef.value, pressOffset)
                                    }
                                }
                            },
                            onTap = {
                                triggerCardClick()
                            }
                        )
                    }
            )

            //  [新增] 更多按钮 / 取消收藏按钮
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 如果提供了取消收藏回调，直接显示取消按钮 (优先于更多菜单显示，或者并存)
                if (onUnfavorite != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp, top = 2.dp)
                            .size(24.dp)
                            .clickable { 
                                haptic(HapticType.MEDIUM)
                                // onUnfavorite.invoke() -> 改为弹窗确认
                                showUnfavoriteDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CupertinoIcons.Filled.HandThumbsup,
                            contentDescription = "取消收藏",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                val hasMenu = onDismiss != null || onWatchLater != null
                if (hasMenu) {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp, top = 2.dp) // 微调位置对齐第一行文字
                            .size(20.dp)
                            .onGloballyPositioned { coordinates ->
                                menuButtonCoordsRef.value = coordinates
                            }
                            .clickable { 
                                haptic(HapticType.LIGHT)
                                openDismissMenu(menuButtonCoordsRef.value, null)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⋮",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(if (compactMetadata) 4.dp else 6.dp))
        
        val metadataColors = resolveHomeVideoCardMetadataColors(
            onSurfaceColor = MaterialTheme.colorScheme.onSurface
        )

        //  底部信息行 - 官方 B 站风格
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            //  [HIG] UP主名称 - 13sp footnote 标准
            //  共享元素过渡 - UP主名称
            val upClickMid = video.owner.mid.takeIf { it > 0L && onUpClick != null }
            var upNameModifier = Modifier.weight(1f, fill = false)
            if (upClickMid != null) {
                upNameModifier = upNameModifier.clickable { onUpClick?.invoke(upClickMid) }
            }
            
            val followBadgeModifier = Modifier.wrapContentSize()

            UpBadgeName(
                name = video.owner.name,
                metaText = resolveUpStatsText(
                    followerCount = upFollowerCount,
                    videoCount = upVideoCount
                ),
                badgeTrailingContent = if (isFollowing) {
                    {
                        if (badgeStylePolicy.infoStyle == HomeVideoBadgeStyle.GLASS) {
                            Surface(
                                modifier = followBadgeModifier,
                                shape = AppShapes.container(ContainerLevel.Pill),
                                color = inlinePillColors.containerColor,
                                border = BorderStroke(0.8.dp, inlinePillColors.borderColor)
                            ) {
                                Text(
                                    text = "已关注",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "已关注",
                                modifier = followBadgeModifier,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else null,
                leadingContent = if (video.owner.face.isNotEmpty()) {
                    {
                        val avatarModifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)

                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(FormatUtils.fixImageUrl(video.owner.face))
                                .crossfade(100)
                                .size(32, 32)
                                .memoryCacheKey("avatar_${video.owner.face.hashCode()}")
                                .build(),
                            contentDescription = null,
                            modifier = avatarModifier,
                            contentScale = ContentScale.Crop
                        )
                    }
                } else null,
                nameStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                nameColor = metadataColors.upNameColor,
                metaColor = metadataColors.upMetaColor,
                badgeTextColor = metadataColors.upBadgeTextColor,
                badgeBackgroundColor = metadataColors.upBadgeBackgroundColor,
                reserveTrailingSlot = true,
                trailingSlotMinHeight = 20.dp,
                showUpBadge = showUpBadge,
                modifier = upNameModifier
            )
            
        }

        VideoCardDurationPublishRow(
            durationText = durationText.takeIf { showDurationOutside }.orEmpty(),
            publishTimeText = publishTimeRowText,
            emphasizePublishTime = emphasizePublishTime,
            publishTimeColor = metadataColors.publishTimeColor,
            topSpacingDp = if (compactMetadata) 4 else 6
        )

        if (scrollLitePolicy.showSecondaryStatsRow) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val viewsRowModifier = Modifier.wrapContentSize()
                Box(modifier = viewsRowModifier) {
                    HomeVideoBadgePill(
                        style = badgeStylePolicy.infoStyle,
                        shape = AppShapes.container(ContainerLevel.Pill),
                        containerColor = inlinePillColors.containerColor,
                        borderColor = inlinePillColors.borderColor
                    ) {
                        Icon(
                            imageVector = CupertinoIcons.Outlined.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = primaryStatText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (secondaryStatText != null) {
                    val danmakuModifier = Modifier.wrapContentSize()
                    HomeVideoBadgePill(
                        modifier = danmakuModifier,
                        style = badgeStylePolicy.infoStyle,
                        shape = AppShapes.container(ContainerLevel.Pill),
                        containerColor = inlinePillColors.containerColor,
                        borderColor = inlinePillColors.borderColor
                    ) {
                        Icon(
                            imageVector = CupertinoIcons.Outlined.BubbleLeft,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = secondaryStatText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (onlineCount.isNotEmpty()) {
                    HomeVideoBadgePill(
                        style = badgeStylePolicy.infoStyle,
                        shape = AppShapes.container(ContainerLevel.Pill),
                        containerColor = inlinePillColors.containerColor,
                        borderColor = inlinePillColors.borderColor
                    ) {
                        Icon(
                            imageVector = CupertinoIcons.Outlined.Eye,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = onlineCount,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        }

    }
        
        // 菜单需要挂在一个本地小锚点上，避免 DropdownMenu 在整张卡片根节点右侧 fallback 时反向偏移。
        Box(
            modifier = Modifier
                .offset(x = menuOffset.x, y = menuOffset.y)
                .size(1.dp)
        ) {
            DropdownMenu(
                expanded = showDismissMenu,
                onDismissRequest = { showDismissMenu = false },
                offset = DpOffset.Zero
            ) {
                // 稍后再看
                if (onWatchLater != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "🕐 稍后再看",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showDismissMenu = false
                            onWatchLater.invoke()
                        }
                    )
                }

                // 取消收藏 (仅在收藏页显示)
                if (onUnfavorite != null) {
                     DropdownMenuItem(
                        text = {
                            Text(
                                "💔 取消收藏",
                                color = MaterialTheme.colorScheme.error  // 使用错误色强调删除操作
                            )
                        },
                        onClick = {
                            showDismissMenu = false
                            // onUnfavorite.invoke() -> 改为弹窗确认
                            showUnfavoriteDialog = true
                        }
                    )
                }

                // 不感兴趣 (放第一位，方便操作) -> 改回下方
                if (onDismiss != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                dismissMenuText,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showDismissMenu = false
                            onDismiss.invoke()
                        }
                    )
                }
            }
        }
    }
    
    
    if (showUnfavoriteDialog) {
        AlertDialog(
            onDismissRequest = { showUnfavoriteDialog = false },
            title = { Text("取消收藏") },
            text = { Text("确定要将此视频从收藏夹中移除吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnfavoriteDialog = false
                        onUnfavorite?.invoke()
                    }
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfavoriteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

}

@Composable
internal fun HomeVideoBadgePill(
    style: HomeVideoBadgeStyle,
    shape: Shape,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    if (style == HomeVideoBadgeStyle.GLASS) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = BorderStroke(0.8.dp, borderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

private fun normalizeVideoCardSourceRouteForKey(sourceRoute: String?): String? {
    val normalized = sourceRoute?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (normalized.startsWith("home?category=")) {
        normalized
    } else {
        normalized.substringBefore("?")
    }
}

/**
 * 简化版视频网格项 (用于搜索结果等)
 * 注意: onClick 只接收 bvid，不接收 cid
 */
@Composable
fun VideoGridItem(video: VideoItem, index: Int, onLongClick: ((VideoItem) -> Unit)? = null, onClick: (String) -> Unit) {
    ElegantVideoCard(video, index, onLongClick = onLongClick) { bvid, _ -> onClick(bvid) }
}
