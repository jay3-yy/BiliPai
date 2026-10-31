package com.android.purebilibili.feature.home.components.cards

import com.android.purebilibili.core.ui.common.copyPlainTextToClipboard
import com.android.purebilibili.core.ui.components.VideoStatusBadges
import com.android.purebilibili.core.ui.AppContentColors


import android.os.Build
import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton

import com.android.purebilibili.core.ui.MediaContrastPalette

import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import com.android.purebilibili.core.ui.wallpaper.sharedGlobalWallpaperBackground
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.store.HomeWallpaperEffectMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.feature.home.LocalHomeCardCoverLoadingAllowed
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchEntry
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchRegistry
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.data.model.resolveVideoCardStatsTexts
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.store.HomeCardBadgeEffectMode
import com.android.purebilibili.core.store.HomeCardInfoGlassMode
import com.android.purebilibili.core.store.HomeDurationStyle
import com.android.purebilibili.core.ui.LocalWallpaperHazeState
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.util.HapticType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.tween
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.videoCardTitleMaxLines
import com.android.purebilibili.core.ui.videoCardTitleOverflow
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.feedContentTypography
import com.android.purebilibili.feature.home.LocalHomeWallpaperBackdrop
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.feature.home.LocalHomeWallpaperBackdropReady
import com.android.purebilibili.feature.home.LocalHomeWallpaperIsStatic
import com.android.purebilibili.feature.home.HomeCoverRequestSpec
import com.android.purebilibili.feature.home.resolveHomeCoverImageSource
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.adaptiveCardHoverEffect
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.components.resolveUpStatsText
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalMiuixVideoCardTransitionState
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.captureNativeCoverOverlayLayer
import com.android.purebilibili.core.ui.transition.captureNativeVideoCardImage
import com.android.purebilibili.core.ui.transition.captureWholeCardReturnBitmap
import com.android.purebilibili.core.ui.transition.captureVideoCardPixelsAfterEntry
import com.android.purebilibili.core.ui.transition.rememberVideoCardPixelCaptureAllowed
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.transition.recordNativeVideoCardLayer
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardLayer
import com.android.purebilibili.core.ui.transition.LocalClickToPlayEnabled
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionVisualSpec
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.resolveVideoSharedCoverCacheKey
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionOwnership
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionPlaybackIntent
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionVisualSpec
import com.android.purebilibili.core.store.SettingsManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedBoundsResizeMode
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.videoCardShellSharedBoundsOrEmpty
import com.android.purebilibili.core.ui.transition.videoCoverSharedElementKey
import com.android.purebilibili.core.ui.transition.videoSharedElementBoundsTransformSpec
import com.android.purebilibili.feature.home.resolveHomeCardEnterAnimationEnabledAtMount
import com.android.purebilibili.feature.home.resolveHomeCardInfoSurfaceAppearance
import com.android.purebilibili.feature.home.resolveHomeCardWallpaperSurfaceMode
import com.android.purebilibili.feature.home.HomeCardWallpaperSurfaceMode
import com.android.purebilibili.feature.home.HomeGlassPillStyle
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.feature.home.resolveHomeGlassCoverPillBaseColor
import com.android.purebilibili.feature.home.resolveHomeGlassPillStyle
import com.android.purebilibili.core.player.PlaybackProgressManager
//  [预览播放] 相关引用已移除

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
    useLowQualityCover: Boolean,
    requestSpec: HomeCoverRequestSpec? = null,
): String {
    val normalizedIdentity = video.bvid.trim().ifEmpty {
        video.pic.trim().ifEmpty {
            "fallback_${video.id.coerceAtLeast(0L)}_${video.cid.coerceAtLeast(0L)}_${video.title.hashCode()}"
        }
    }
    val baseKey = resolveVideoSharedCoverCacheKey(normalizedIdentity, useLowQualityCover)
    return requestSpec?.let { "${baseKey}_${it.cacheKeySuffix}" } ?: baseKey
}

private data class VideoCardPillColors(
    val cover: HomeGlassResolvedColors,
    val emphasizedCover: HomeGlassResolvedColors,
    val inline: HomeGlassResolvedColors
)

private data class VideoCardSharedTransitionSpecs(
    val motion: VideoSharedTransitionMotionSpec,
    val visual: VideoSharedTransitionVisualSpec
)

private data class VideoCardScreenMetrics(
    val widthPx: Float,
    val heightPx: Float,
    val density: Float
)

internal data class HomeVideoCardMetadataColors(
    val upNameColor: Color,
    val upMetaColor: Color,
    val upBadgeTextColor: Color,
    val upBadgeBackgroundColor: Color,
    val publishTimeColor: Color
)

internal fun resolveHomeVideoCardMetadataColors(
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
): HomeVideoCardMetadataColors {
    return HomeVideoCardMetadataColors(
        // 作者信息降为次级语义色，让标题保持卡片内的第一视觉层级。
        upNameColor = onSurfaceVariantColor,
        upMetaColor = onSurfaceColor.copy(alpha = 0.82f),
        upBadgeTextColor = onSurfaceColor.copy(alpha = 0.68f),
        upBadgeBackgroundColor = onSurfaceColor.copy(alpha = 0.10f),
        publishTimeColor = onSurfaceColor.copy(alpha = 0.72f)
    )
}

private fun resolveVideoCardMetadataModifier(
    trailingActionCount: Int
): Modifier {
    return Modifier
        .fillMaxWidth()
        .then(
            if (trailingActionCount > 0) {
                // Each bottom-end action owns a full touch target, including when both are present.
                Modifier.padding(end = AppChromeSizeTokens.MinimumTouchTarget * trailingActionCount)
            } else {
                Modifier
            }
        )
}

private fun resolveVideoCardMetadataRowModifier(): Modifier = Modifier.fillMaxWidth()

@Composable
private fun VideoCardOwnerMetadata(
    video: VideoItem,
    isFollowing: Boolean,
    showUpBadge: Boolean,
    showUpAvatar: Boolean,
    upFollowerCount: Int?,
    upVideoCount: Int?,
    infoBadgeStyle: HomeVideoBadgeStyle,
    inlinePillColors: HomeGlassResolvedColors,
    metadataColors: HomeVideoCardMetadataColors,
    onUpClick: ((Long) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val contentTypography = feedContentTypography()
    val upClickMid = video.owner.mid.takeIf { it > 0L && onUpClick != null }
    val ownerModifier = modifier
        .fillMaxWidth()
        .then(
            if (upClickMid != null) {
                Modifier.appActionClickable { onUpClick?.invoke(upClickMid) }
            } else {
                Modifier
            }
        )

    UpBadgeName(
        name = video.owner.name,
        metaText = resolveUpStatsText(
            followerCount = upFollowerCount,
            videoCount = upVideoCount
        ),
        nameLeadingContent = if (isFollowing || video.isFollowed || video.recommendationReason.isNotBlank()) {
            {
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoRecommendationBadge(video.recommendationReason)
                    if (isFollowing || video.isFollowed) {
                        if (infoBadgeStyle == HomeVideoBadgeStyle.GLASS) {
                            AppSurface(
                                modifier = Modifier.wrapContentSize(),
                                shape = AppShapes.container(ContainerLevel.Pill),
                                color = inlinePillColors.containerColor,
                                border = BorderStroke(
                                    AppSpacingTokens.Micro * 0.4f,
                                    inlinePillColors.borderColor,
                                ),
                            ) {
                                AppText(
                                    text = "已关注",
                                    style = contentTypography.coverBadge,
                                    color = AppContentColors.accent(),
                                    modifier = Modifier.padding(
                                        horizontal = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro,
                                        vertical = AppSpacingTokens.Micro,
                                    ),
                                )
                            }
                        } else {
                            AppText(
                                text = "已关注",
                                style = contentTypography.coverBadge,
                                color = AppContentColors.accent(),
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
        leadingContent = if (showUpAvatar && video.owner.face.isNotEmpty()) {
            {
                val avatarContext = LocalContext.current
                val face = video.owner.face
                val avatarRequest = remember(avatarContext, face) {
                    ImageRequest.Builder(avatarContext)
                        .data(FormatUtils.fixImageUrl(face))
                        .crossfade(100)
                        .size(32, 32)
                        .memoryCacheKey("avatar_${face.hashCode()}")
                        .build()
                }
                PageAwareAsyncImage(
                    model = avatarRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .size(AppSpacingTokens.Medium + AppSpacingTokens.Micro)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            null
        },
        nameStyle = contentTypography.author,
        nameColor = metadataColors.upNameColor,
        metaColor = metadataColors.upMetaColor,
        badgeTextColor = metadataColors.upBadgeTextColor,
        badgeBackgroundColor = metadataColors.upBadgeBackgroundColor,
        // 名称占满剩余宽度，省略号顶到卡片内边距附近；右侧留 6dp 空隙不与
        // 后续元素贴死，长昵称也不会被提前折叠到卡片一半宽度。
        nameEndPadding = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro,
        showUpBadge = showUpBadge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        metaMaxLines = Int.MAX_VALUE,
        metaOverflow = TextOverflow.Visible,
        modifier = ownerModifier
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun VideoCardDurationPublishRow(
    durationText: String,
    publishTimeText: String,
    emphasizePublishTime: Boolean,
    publishTimeColor: Color,
    topSpacing: Dp,
) {
    if (durationText.isBlank() && publishTimeText.isBlank()) return
    val contentTypography = feedContentTypography()

    Spacer(modifier = Modifier.height(topSpacing))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro),
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro)
    ) {
        if (publishTimeText.isNotBlank()) {
            VideoCardPublishTime(
                text = publishTimeText,
                emphasized = emphasizePublishTime,
                color = publishTimeColor,
                modifier = Modifier.wrapContentWidth(),
            )
        }

        if (durationText.isNotBlank()) {
            AppText(
                text = durationText,
                color = AppContentColors.secondaryText(),
                style = contentTypography.statistic.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }
    }
}

@Composable
private fun VideoCardPublishTime(
    text: String,
    emphasized: Boolean,
    color: Color,
    modifier: Modifier,
) {
    val contentTypography = feedContentTypography()
    if (emphasized) {
        AppSurface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = AppShapes.container(ContainerLevel.Pill),
            modifier = modifier
        ) {
            AppText(
                text = text,
                style = contentTypography.statistic.copy(fontWeight = FontWeight.Medium),
                color = color.copy(alpha = 0.92f),
                softWrap = true,
                overflow = TextOverflow.Visible,
                modifier = Modifier.padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2)
            )
        }
    } else {
        AppText(
            text = text,
            style = contentTypography.statistic,
            color = color,
            softWrap = true,
            overflow = TextOverflow.Visible,
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
        borderColor = MediaContrastPalette.Foreground.copy(alpha = style.borderAlpha),
        highlightColor = MediaContrastPalette.Foreground.copy(alpha = style.highlightAlpha)
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
internal fun ElegantVideoCard(
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
    coverRequestSpec: HomeCoverRequestSpec? = null,
    glassEnabled: Boolean = true,
    blurEnabled: Boolean = true,
    compactStatsOnCover: Boolean = false, // 播放/弹幕默认位于信息区
    showCoverGlassBadges: Boolean = false,
    showInfoGlassBadges: Boolean = false,
    badgeEffectMode: HomeCardBadgeEffectMode = HomeCardBadgeEffectMode.OFF,
    infoGlassMode: HomeCardInfoGlassMode = HomeCardInfoGlassMode.OFF,
    wallpaperTintEnabled: Boolean = false,
    wallpaperEffectMode: HomeWallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
    showUpBadge: Boolean? = null,
    showUpAvatar: Boolean? = null,
    homeDurationStyle: HomeDurationStyle = HomeDurationStyle.OUTSIDE_COVER,
    // All video cards follow the selected ratio; callers can explicitly override artwork geometry.
    coverAspectRatio: Float = com.android.purebilibili.core.ui.videoCardCoverAspectRatio(),
    compactMetadata: Boolean = true,
    titleMinLines: Int = 2,
    titleMaxLines: Int = 2,
    highlightedTitle: AnnotatedString? = null,
    showOnlineCount: Boolean = false,
    upFollowerCount: Int? = null,
    upVideoCount: Int? = null,
    onDismiss: (() -> Unit)? = null,    //  [新增] 删除/过滤回调（长按触发）
    onWatchLater: (() -> Unit)? = null,  //  [新增] 稍后再看回调
    onUnfavorite: (() -> Unit)? = null,  //  [新增] 取消收藏回调
    dismissMenuText: String = "不感兴趣", //  [新增] 自定义长按菜单删除文案
    onLongClick: ((VideoItem) -> Unit)? = null, // [Feature] Long Press Preview
    onUpClick: ((Long) -> Unit)? = null,
    singleColumn: Boolean = false,
    cardWidthDp: Float? = null,
    modifier: Modifier = Modifier,
    onClick: (String, Long) -> Unit
) {
    val globalWallpaperSource = LocalGlobalWallpaperBackdropSource.current
    val globalCardWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current &&
        LocalHomeCardFrostedGlassEnabled.current && globalWallpaperSource != null
    val resolvedWallpaperTintEnabled = wallpaperTintEnabled || globalCardWallpaperVisible
    val isLongPressEnabled = com.android.purebilibili.core.ui.LocalVideoCardLongPressEnabled.current
    // 右下角菜单的扩展操作（对齐 PiliPlus）：单列窗口菜单与网格下拉菜单共用状态与对话框。
    val context = LocalContext.current
    val cardActionScope = rememberCoroutineScope()
    var showBlockDialog by remember(video.bvid) { mutableStateOf(false) }
    var showAiSummaryDialog by remember(video.bvid) { mutableStateOf(false) }
    var aiSummaryLoading by remember(video.bvid) { mutableStateOf(false) }
    var aiSummaryData by remember(video.bvid) { mutableStateOf<com.android.purebilibili.data.model.response.AiSummaryData?>(null) }
    var aiSummaryError by remember(video.bvid) { mutableStateOf<String?>(null) }
    val launchAiSummary: () -> Unit = {
        if (!aiSummaryLoading && video.cid > 0L && video.bvid.isNotBlank()) {
            aiSummaryLoading = true
            aiSummaryData = null
            aiSummaryError = null
            showAiSummaryDialog = true
            cardActionScope.launch {
                VideoRepository.getAiSummary(video.bvid, video.cid, video.owner.mid).fold(
                    onSuccess = { response ->
                        aiSummaryData = response.data
                        aiSummaryLoading = false
                    },
                    onFailure = { error ->
                        aiSummaryError = error.message ?: "获取 AI 总结失败"
                        aiSummaryLoading = false
                    },
                )
            }
        }
    }
    if (showBlockDialog) {
        AppAlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { AppText("屏蔽 UP 主") },
            text = { AppText("屏蔽后，将不再推荐该 UP 主的视频。\n确定要屏蔽 ${video.owner.name} 吗？") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        showBlockDialog = false
                        cardActionScope.launch {
                            val result = com.android.purebilibili.data.repository.BlockedUpRepository(context)
                                .blockUpWithBilibiliSync(video.owner.mid, video.owner.name, video.owner.face)
                            android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    AppText("确认")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showBlockDialog = false }) {
                    AppText("取消")
                }
            }
        )
    }

    if (showAiSummaryDialog) {
        AppAlertDialog(
            onDismissRequest = { showAiSummaryDialog = false },
            title = { AppText("AI 总结") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when {
                        aiSummaryLoading -> AppText("正在获取 AI 总结…", color = AppContentColors.secondaryText())
                        aiSummaryError != null -> AppText(aiSummaryError!!, color = AppContentColors.error())
                        else -> AppText(
                            aiSummaryData?.modelResult?.summary?.takeIf { it.isNotBlank() } ?: "该视频暂无 AI 总结",
                            color = AppContentColors.primaryText()
                        )
                    }
                }
            },
            confirmButton = {
                AppTextButton(onClick = { showAiSummaryDialog = false }) {
                    AppText("关闭")
                }
            }
        )
    }

    if (singleColumn) {
        val actions = buildList {
            if (video.bvid.isNotBlank()) {
                add(com.android.purebilibili.core.ui.components.AppWindowAction(
                    label = "复制BV号",
                    onClick = {
                        copyPlainTextToClipboard(context, video.bvid, "BV号")
                        android.widget.Toast.makeText(context, "已复制 ${video.bvid}", android.widget.Toast.LENGTH_SHORT).show()
                    },
                ))
            }
            onUpClick?.let { visit ->
                add(com.android.purebilibili.core.ui.components.AppWindowAction(
                    label = "访问UP主", onClick = { visit(video.owner.mid) },
                ))
            }
            onWatchLater?.let {
                add(com.android.purebilibili.core.ui.components.AppWindowAction(label = "加入稍后再看", onClick = it))
            }
            if (video.cid > 0L && video.bvid.isNotBlank()) {
                add(com.android.purebilibili.core.ui.components.AppWindowAction(
                    label = "AI总结", onClick = { launchAiSummary() },
                ))
            }
            if (video.owner.mid > 0L) {
                add(com.android.purebilibili.core.ui.components.AppWindowAction(
                    label = "拉黑：${video.owner.name}", onClick = { showBlockDialog = true },
                ))
            }
            add(com.android.purebilibili.core.ui.components.AppWindowAction(
                label = "无痕模式",
                onClick = {
                    val next = !com.android.purebilibili.core.store.SettingsManager.isPrivacyModeEnabledSync(context)
                    cardActionScope.launch {
                        com.android.purebilibili.core.store.SettingsManager.setPrivacyModeEnabled(context, next)
                        android.widget.Toast.makeText(
                            context,
                            if (next) "已进入无痕模式" else "已退出无痕模式",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            ))
            onUnfavorite?.let {
                add(com.android.purebilibili.core.ui.components.AppWindowAction(label = "取消收藏", onClick = it))
            }
            onDismiss?.let {
                add(com.android.purebilibili.core.ui.components.AppWindowAction(label = dismissMenuText, onClick = it))
            }
        }
        HomeStyleSingleColumnVideoCard(
            video = video,
            sourceRoute = sharedElementSourceRoute
                ?: com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute.current.orEmpty(),
            coverAspectRatio = coverAspectRatio,
            transitionEnabled = transitionEnabled,
            isFollowing = isFollowing,
            showUpBadge = showUpBadge ?: com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current.showBadges,
            durationStyle = homeDurationStyle,
            showPublishTime = showPublishTime,
            modifier = modifier,
            highlightedTitle = highlightedTitle,
            onClick = { onClick(video.bvid, video.cid) },
            onLongClick = if (isLongPressEnabled) onLongClick?.let { callback -> { callback(video) } } else null,
            trailingContent = if (actions.isEmpty()) null else ({
                com.android.purebilibili.core.ui.components.AppWindowActionMenu(groups = listOf(actions)) {
                    AppIcon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                }
            }),
        )
        return
    }
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)
    val longPressHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.LONG_PRESS)
    val contentTypography = feedContentTypography()
    val playbackProgressManager = remember(context) {
        PlaybackProgressManager.getInstance(context)
    }

    val cardCornerRadius = AppShapes.containerCornerDp(ContainerLevel.Card)
    val durationBadgeStyle = remember { resolveVideoCardDurationBadgeVisualStyle() }
    // 统计文案组合规则已下沉 core-data（resolveVideoCardStatsTexts），与 TV 卡片共用同一实现。
    val cardTexts = remember(video.duration, video.stat.view, video.stat.reply, video.stat.danmaku, video.progress) {
        resolveVideoCardStatsTexts(video)
    }
    val durationText = cardTexts.durationText
    val primaryStatText = cardTexts.primaryStatText
    val secondaryStatText = cardTexts.secondaryStatText
    val durationBadgeMinWidth = remember(durationText) {
        resolveVideoCardDurationBadgeMinWidthDp(
            durationText = durationText,
            style = durationBadgeStyle
        ).dp
    }
    // 时长作为统计行 pill（闹钟图标 + 文本）时的最小宽度预算，供封面统计行自适应让位。
    val durationStatMinWidthDp = remember(durationText) {
        resolveVideoCardDurationStatMinWidthDp(durationText)
    }
    val showDurationOnCover = homeDurationStyle == HomeDurationStyle.OVERLAY_TEXT_ONLY
    val coverOverlayTextShadow = remember { resolveVideoCardCoverOverlayTextShadow() }
    val coverOverlayTextStyle = remember(coverOverlayTextShadow) {
        TextStyle(shadow = coverOverlayTextShadow)
    }
    val showDurationOutside = homeDurationStyle == HomeDurationStyle.OUTSIDE_COVER
    val inlinePillBaseColor = AppSurfaceTokens.cardContainer()
    val wallpaperHazeState = LocalWallpaperHazeState.current
    val homeWallpaperBackdrop = LocalHomeWallpaperBackdrop.current
        ?: globalWallpaperSource?.takeIf { globalCardWallpaperVisible && it.isReady }?.backdrop
    val homeWallpaperBackdropReady = LocalHomeWallpaperBackdropReady.current ||
        (globalCardWallpaperVisible && globalWallpaperSource?.isReady == true)
    val homeWallpaperIsStatic = LocalHomeWallpaperIsStatic.current || globalCardWallpaperVisible
    val badgeEffectVisual = remember(badgeEffectMode, wallpaperHazeState != null) {
        resolveHomeCardBadgeEffectVisual(
            mode = badgeEffectMode,
            scrollLiteModeEnabled = false,
            hasHazeState = wallpaperHazeState != null
        )
    }
    val effectiveGlassEnabled = badgeEffectVisual.glassEnabled && glassEnabled
    val effectiveBlurEnabled = badgeEffectVisual.blurEnabled && blurEnabled
    val pillColors = remember(effectiveGlassEnabled, effectiveBlurEnabled, inlinePillBaseColor) {
        resolveVideoCardPillColors(
            glassEnabled = effectiveGlassEnabled,
            blurEnabled = effectiveBlurEnabled,
            inlineBaseColor = inlinePillBaseColor
        )
    }
    val coverPillColors = pillColors.cover
    val inlinePillColors = pillColors.inline
    val isDarkCardTheme = AppSurfaceTokens.chromeBackground().luminance() < 0.5f
    val wallpaperPalette = LocalWallpaperPalette.current
    val homeCardDynamicTintEnabled = LocalHomeCardDynamicTintEnabled.current
    val homeCardFrostedGlassEnabled = LocalHomeCardFrostedGlassEnabled.current
    val lowBlurBudgetForced = isLowBlurBudgetForced()
    val homeWallpaperSurfaceMode = remember(
        homeCardDynamicTintEnabled,
        homeCardFrostedGlassEnabled,
        resolvedWallpaperTintEnabled,
        homeWallpaperIsStatic,
        homeWallpaperBackdropReady,
        blurEnabled,
        isDataSaverActive,
        lowBlurBudgetForced,
        Build.VERSION.SDK_INT,
    ) {
        resolveHomeCardWallpaperSurfaceMode(
            dynamicTintEnabled = homeCardDynamicTintEnabled,
            frostedGlassEnabled = homeCardFrostedGlassEnabled,
            wallpaperVisible = resolvedWallpaperTintEnabled,
            wallpaperIsStatic = homeWallpaperIsStatic,
            backdropReady = homeWallpaperBackdropReady,
            blurEnabled = blurEnabled,
            isDataSaverActive = isDataSaverActive,
            lowBlurBudgetForced = lowBlurBudgetForced,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }
    val useRealtimeWallpaperBackdrop =
        homeWallpaperSurfaceMode == HomeCardWallpaperSurfaceMode.REALTIME_FROSTED
    val infoLayoutCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val homeScrollTickProvider = LocalHomeScrollTickProvider.current
    // 其他列表没有首页滚动驱动，继续用位置回调保持原有壁纸跟随行为。
    val nonHomeCardYFraction = if (homeScrollTickProvider == null && resolvedWallpaperTintEnabled) {
        remember { mutableFloatStateOf(0.5f) }
    } else null
    val infoSurfaceAppearance = remember(
        resolvedWallpaperTintEnabled,
        wallpaperEffectMode,
        isDarkCardTheme,
        isDataSaverActive,
        infoGlassMode,
        wallpaperHazeState != null,
        blurEnabled
    ) {
        resolveHomeCardInfoSurfaceAppearance(
            wallpaperTintEnabled = resolvedWallpaperTintEnabled,
            wallpaperEffectMode = wallpaperEffectMode,
            isDarkTheme = isDarkCardTheme,
            isDataSaverActive = isDataSaverActive,
            infoGlassMode = infoGlassMode,
            hasWallpaperHazeState = wallpaperHazeState != null,
            hasLayerBackdrop = false,
            blurEnabled = blurEnabled
        )
    }
    val useCardEffectSurface = homeCardDynamicTintEnabled || homeCardFrostedGlassEnabled
    val useRealtimeInfoHaze = homeCardFrostedGlassEnabled &&
        infoSurfaceAppearance.useRealtimeHaze && wallpaperHazeState != null &&
        !lowBlurBudgetForced && Build.VERSION.SDK_INT >= 31 &&
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.bottomBarBlurEnabled
    val hasRealtimeInfoBlur = useRealtimeWallpaperBackdrop || useRealtimeInfoHaze
    // 模糊不可用时，关闭动态取色的卡片必须保持中性实色，不能透出壁纸伪装成模糊。
    val useWallpaperInfoBacking = resolvedWallpaperTintEnabled &&
        (homeCardDynamicTintEnabled || hasRealtimeInfoBlur)
    val scrollLitePolicy = remember(compactStatsOnCover) {
        resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = false,
            compactStatsOnCover = compactStatsOnCover
        )
    }
    val badgeStylePolicy = remember(badgeEffectVisual, showCoverGlassBadges, showInfoGlassBadges) {
        HomeVideoGlassBadgeStylePolicy(
            coverStyle = if (showCoverGlassBadges) {
                badgeEffectVisual.coverStyle
            } else {
                HomeVideoBadgeStyle.PLAIN
            },
            infoStyle = if (showInfoGlassBadges) {
                badgeEffectVisual.infoStyle
            } else {
                HomeVideoBadgeStyle.PLAIN
            }
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
        themePrimary = AppContentColors.accent()
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
    // Cover identity does not depend on playback progress or live statistics. Keep unrelated
    // VideoItem updates from rebuilding the cover URL/cache key on the UI thread.
    remember(video.bvid, video.id, video.cid, video.title, video.pic, video.rights, useLowQualityCover, coverRequestSpec) {
        val source = resolveHomeCoverImageSource(video, useLowQualityCover, coverRequestSpec)
        Triple(
            source.cacheKey,
            source.url,
            resolveVideoPremiumBadgeLabel(video.rights)
        )
    }.let { (cache, url, badge) ->
        coverCacheKey = cache
        coverUrl = url
        premiumBadgeLabel = badge
    }
    var coverTint by remember(coverCacheKey) {
        mutableStateOf(VideoCardCoverColorStore.getCachedColor(coverCacheKey))
    }
    val activeCoverCacheKey by rememberUpdatedState(coverCacheKey)
    val animatedCoverTint by animateColorAsState(
        targetValue = coverTint ?: Color.Transparent,
        animationSpec = tween(durationMillis = 350),
        label = "video_card_cover_tint"
    )
    val defaultOnSurface = AppContentColors.primaryText()
    val defaultOnSurfaceVariant = AppContentColors.secondaryText()
    val adaptiveContentColors = remember(
        wallpaperPalette,
        animatedCoverTint,
        resolvedWallpaperTintEnabled,
        isDarkCardTheme,
        defaultOnSurface,
        defaultOnSurfaceVariant,
        homeCardDynamicTintEnabled,
        homeCardFrostedGlassEnabled,
        useWallpaperInfoBacking,
    ) {
        resolveVideoCardAdaptiveContentColors(
            wallpaperPalette = wallpaperPalette,
            coverTint = if (homeCardDynamicTintEnabled && animatedCoverTint.alpha > 0f) {
                animatedCoverTint
            } else {
                null
            },
            wallpaperTintEnabled = useWallpaperInfoBacking,
            isDarkTheme = isDarkCardTheme,
            defaultOnSurface = defaultOnSurface,
            defaultOnSurfaceVariant = defaultOnSurfaceVariant,
            homeCardDynamicTintEnabled = useCardEffectSurface,
            wallpaperGlassAlpha = resolveWallpaperGlassAlpha(
                frostedGlassEnabled = homeCardFrostedGlassEnabled,
                isDarkTheme = isDarkCardTheme
            )
        )
    }
    var cardVisibleInWindow by remember(video.bvid, video.cid) { mutableStateOf(false) }
    var measuredCoverSize by remember(coverCacheKey) {
        mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    }
    val coverReturnPrefetchAllowed = LocalPageImageLoadingAllowed.current && cardVisibleInWindow
    LaunchedEffect(video.bvid, coverUrl, coverCacheKey, coverRequestSpec, measuredCoverSize, coverReturnPrefetchAllowed) {
        if (!coverReturnPrefetchAllowed) return@LaunchedEffect
        HomeCoverReturnPrefetchRegistry.onCardVisible(
            HomeCoverReturnPrefetchEntry(
                bvid = video.bvid.trim(),
                url = coverUrl,
                cacheKey = coverCacheKey,
                decodeWidthPx = coverRequestSpec?.widthPx ?: measuredCoverSize.width,
                decodeHeightPx = coverRequestSpec?.heightPx ?: measuredCoverSize.height,
            )
        )
    }
    val onlineCount = rememberVideoCardOnlineCount(
        video = video,
        showOnlineCount = showOnlineCount,
        isVisibleInWindow = cardVisibleInWindow,
    )
    val publishTimeRowText = rememberVideoCardPublishTimeText(video, showPublishTime, cardVisibleInWindow)
    val emphasizePublishTime = false

    //  判断是否为竖屏视频（通过封面图 URL 中的尺寸信息或默认不显示）
    // B站封面 URL 通常包含尺寸信息，如 width=X&height=Y
    // 简单方案：暂不显示竖屏标签（因推荐API不提供视频尺寸信息）

    //  获取屏幕尺寸用于计算归一化坐标
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenMetrics = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        density
    ) {
        VideoCardScreenMetrics(
            widthPx = with(density) { configuration.screenWidthDp.dp.toPx() },
            heightPx = with(density) { configuration.screenHeightDp.dp.toPx() },
            density = density.density
        )
    }
    val resolvedCardWidthDp = remember(cardWidthDp, screenMetrics.widthPx, screenMetrics.density) {
        cardWidthDp ?: ((screenMetrics.widthPx / screenMetrics.density - (AppSpacingTokens.Medium.value * 2f)) / 2f)
    }
    val compactStatsAvailableWidthDp = remember(resolvedCardWidthDp) {
        (resolvedCardWidthDp - (AppSpacingTokens.Small.value * 2f)).coerceAtLeast(0f)
    }

    //  记录卡片位置（非 Compose State，避免滚动时触发高频重组）
    //  [性能优化] 存储 LayoutCoordinates 引用而非 Rect，boundsInRoot() 仅在交互时惰性计算，
    //  避免滚动期间每帧 4 次坐标树遍历开销。
    val cardCoordsRef = remember { object { var value: LayoutCoordinates? = null } }
    val sharedSourceInstanceId = rememberSaveable(video.bvid) {
        CardPositionManager.newVideoCardSourceInstanceId()
    }
    val coverCoordsRef = remember { object { var value: LayoutCoordinates? = null } }
    // 封面位置只在绘制或点击时读取，不随列表滚动通过全局布局回调回写 snapshot 状态。
    fun currentCoverYFraction(): Float {
        nonHomeCardYFraction?.let { return it.floatValue }
        val coordinates = coverCoordsRef.value?.takeIf { it.isAttached }
        return if (coordinates != null && screenMetrics.heightPx > 0f) {
            (coordinates.positionInRoot().y / screenMetrics.heightPx).coerceIn(0f, 1f)
        } else 0.5f
    }
    val titleCoordsRef = remember { object { var value: LayoutCoordinates? = null } }
    val menuButtonCoordsRef = remember { object { var value: LayoutCoordinates? = null } }
    val localSharedElementSourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val effectiveSharedElementSourceRoute = remember(sharedElementSourceRoute, localSharedElementSourceRoute) {
        sharedElementSourceRoute ?: localSharedElementSourceRoute
    }

    // ⋮ 按钮入口额外提供源图层：玻璃菜单从按钮本身形变展开并跟随按住拖拽；
    // 长按入口（pressOffset 非空）不接此 source，继续走按点锚点。原生路径不读取。
    val overflowMenuSource = rememberAppGlassMenuSource()
    var menuFromOverflowButton by remember { mutableStateOf(false) }
    val openDismissMenu: (LayoutCoordinates?, Offset?) -> Unit = { anchorCoords, pressOffset ->
        menuOffset = resolveVideoCardMenuOffset(
            rootBoundsInRoot = cardCoordsRef.value?.takeIf { it.isAttached }?.boundsInRoot(),
            anchorBoundsInRoot = anchorCoords?.takeIf { it.isAttached }?.boundsInRoot(),
            density = screenMetrics.density,
            pressOffsetInAnchorPx = pressOffset
        )
        // 按钮入口 pressOffset 为 null：玻璃菜单从 ⋮ 源控件形变展开；长按走按点锚点。
        menuFromOverflowButton = pressOffset == null
        showDismissMenu = true
    }
    // 液态玻璃同窗菜单使用真实控件边界作为锚点，弹窗按设置页行为覆盖住三点按钮；
    // 长按入口没有具名控件，保持 null 走按点小锚点，原生菜单路径不读取该状态。
    var menuGlassAnchorBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val hasOverflowMenu = onDismiss != null || onWatchLater != null
    val hasTrailingCardAction = onUnfavorite != null || hasOverflowMenu
    val nativeCardLayer = rememberNativeVideoCardLayer()
    val wholeCardCaptureScope = rememberCoroutineScope()
    val wholeCardCaptureAllowed = rememberVideoCardPixelCaptureAllowed()
    val videoCardTransitionBackgroundState = LocalVideoCardTransitionBackgroundState.current
    val nativeCoverOverlayLayer = rememberNativeVideoCardLayer()
    val freezeNativeCardLayer = remember(video.bvid) { mutableStateOf(false) }

    // 玻璃底色显式选择材质分支，原生卡片不借用玻璃透明度或布局参数。
    val infoBaseContainerColor = AppSurfaceTokens.videoCardContainer(glassEnabled = useCardEffectSurface)
    fun resolvedInfoBackingColor(yFraction: Float): Color =
        if (useWallpaperInfoBacking && wallpaperPalette != null) {
            interpolateWallpaperColor(wallpaperPalette, yFraction).copy(alpha = 1f)
        } else {
            infoBaseContainerColor.copy(alpha = 1f)
        }

    val triggerCardClick by rememberUpdatedState(appActionWithFeedback {
        if (CardPositionManager.isVideoNavigationDebounceActive()) {
            // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
            return@appActionWithFeedback
        }
        cardCoordsRef.value?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()?.let { bounds ->
            val sourceCoverBounds = coverCoordsRef.value
                ?.takeIf { it.isAttached }
                ?.videoCardSourceBoundsInRoot()
            val frozenCompactStatsLayout = resolveVideoCardCompactCoverStatsLayout(
                availableWidthDp = sourceCoverBounds?.let { coverBounds ->
                    (
                        coverBounds.width / screenMetrics.density -
                            (AppSpacingTokens.Small.value * 2f)
                        ).coerceAtLeast(0f)
                } ?: Float.MAX_VALUE,
                primaryStatText = primaryStatText,
                secondaryStatText = secondaryStatText,
                hasOnlineCount = onlineCount.isNotEmpty(),
                durationBadgeMinWidthDp = if (showDurationOnCover) {
                    durationBadgeMinWidth.value
                } else {
                    0f
                },
                durationStatMinWidthDp = if (showDurationOutside) {
                    durationStatMinWidthDp
                } else {
                    0f
                },
            )
            CardPositionManager.recordVideoCardPosition(
                bvid = video.bvid,
                sourceRoute = effectiveSharedElementSourceRoute,
                bounds = bounds,
                screenWidth = screenMetrics.widthPx,
                screenHeight = screenMetrics.heightPx,
                density = screenMetrics.density,
                sourceCornerDp = cardCornerRadius.value.roundToInt(),
                coverBounds = sourceCoverBounds,
                // Dual-column home cards are cover-over-meta; freeze chrome so return text
                // survives Loading and does not wait for destination ViewInfo.
                sourceLayout = VideoCardSourceLayout.STACKED,
                sourceChromeSnapshot = VideoCardSourceChromeSnapshot(
                    infoContainerColor = if (useCardEffectSurface && resolvedWallpaperTintEnabled) {
                        val infoY = infoLayoutCoordinates.value
                            ?.takeIf { it.isAttached }?.positionInRoot()?.y
                        val fraction = if (infoY != null && screenMetrics.heightPx > 0f) {
                            (infoY / screenMetrics.heightPx).coerceIn(0f, 1f)
                        } else currentCoverYFraction()
                        val backing = resolvedInfoBackingColor(fraction)
                        val plate = if (useRealtimeWallpaperBackdrop) {
                            infoBaseContainerColor.copy(alpha = if (isDarkCardTheme) 0.44f else 0.36f)
                        } else {
                            resolveVideoCardAmbientDrawSpec(
                                wallpaperPalette = wallpaperPalette,
                                yFraction = fraction,
                                coverTint = animatedCoverTint.takeIf { it.alpha > 0f },
                                wallpaperTintEnabled = resolvedWallpaperTintEnabled,
                                isDarkTheme = isDarkCardTheme,
                                defaultContainerColor = infoBaseContainerColor,
                                defaultBorderColor = MediaContrastPalette.Foreground,
                                isDataSaverActive = isDataSaverActive,
                                frostedGlassEnabled = homeCardFrostedGlassEnabled,
                                dynamicTintEnabled = homeCardDynamicTintEnabled,
                            ).containerColor.let { color ->
                                if (!homeCardDynamicTintEnabled && !hasRealtimeInfoBlur) color.copy(alpha = 1f) else color
                            }
                        }
                        plate.compositeOver(backing)
                    } else null,
                    title = video.title,
                    ownerName = video.owner.name,
                    ownerFaceUrl = video.owner.face,
                    recommendationReason = video.recommendationReason,
                    viewText = if (video.stat.view > 0) {
                        FormatUtils.formatStat(video.stat.view.toLong())
                    } else {
                        primaryStatText
                    },
                    danmakuText = secondaryStatText
                        ?: FormatUtils.formatStat(video.stat.danmaku.toLong()),
                    durationText = durationText,
                    followed = isFollowing || video.isFollowed,
                    // Remember what the list info column actually paints (not cover badges).
                    infoPresentation = com.android.purebilibili.core.ui.transition
                        .resolveVideoCardSourceInfoPresentation(
                            publishTimeText = publishTimeRowText,
                            showStatsInInfo = scrollLitePolicy.showSecondaryStatsRow,
                            ownerBeforeStats = true,
                            showDurationInInfo = showDurationOutside,
                            useTintedInfoSurface = useCardEffectSurface,
                            showOverflowMenu = hasOverflowMenu,
                        ),
                    coverPresentation = VideoCardSourceCoverPresentation(
                        showGradientMask = scrollLitePolicy.showCoverGradientMask,
                        showStatsOnCover = scrollLitePolicy.showCompactStatsOnCover,
                        showSecondaryStatOnCover = scrollLitePolicy.showCompactStatsOnCover &&
                            frozenCompactStatsLayout.showSecondaryStat,
                        showOnlineCountOnCover = scrollLitePolicy.showCompactStatsOnCover &&
                            frozenCompactStatsLayout.showOnlineCount,
                        showDurationOnCover = showDurationOnCover,
                        showDurationAsStat = scrollLitePolicy.showCompactStatsOnCover &&
                            showDurationOutside,
                        useGlassStats = badgeStylePolicy.coverStyle == HomeVideoBadgeStyle.GLASS,
                        onlineCountText = onlineCount,
                        premiumBadgeText = premiumBadgeLabel.orEmpty(),
                        showHistoryProgressBar = scrollLitePolicy.showHistoryProgressBar &&
                            showHistoryProgressBar,
                        historyProgressFraction = historyProgressFraction,
                    ),
                    // Exact stationary list cover request (URL + key + Coil size).
                    coverUrl = coverUrl,
                    coverCacheKey = coverCacheKey,
                    coverDecodeWidthPx = coverRequestSpec?.widthPx ?: 0,
                    coverDecodeHeightPx = coverRequestSpec?.heightPx ?: 0,
                ),
                sourceInstanceId = sharedSourceInstanceId,
                // 点击时冻结的信息面矩形：重建壳的信息板按它落位，而不是「卡底 − 封面底」。
                infoBounds = infoLayoutCoordinates.value
                    ?.takeIf { it.isAttached }
                    ?.videoCardSourceBoundsInRoot(),
            )
            // The mounted draw modifier reads this latch directly, so the layer freezes
            // before OPENING can hide the stationary title and statistics.
            // 过渡关闭时 draw modifier 未挂载，冻结/捕获纯属空转（层从未 record）。
            if (transitionEnabled) {
                freezeNativeCardLayer.value = true
                captureNativeVideoCardImage(nativeCardLayer)
                captureNativeCoverOverlayLayer(nativeCoverOverlayLayer)
                val owner = CardPositionManager.videoCardPixelOwnerFor(CardPositionManager.lastClickedVideoSourceKey)
                if (owner != null) {
                    wholeCardCaptureScope.launch {
                        captureVideoCardPixelsAfterEntry(
                            phaseProvider = videoCardTransitionBackgroundState.phaseProvider,
                            isReturnGestureInProgress = {
                                videoCardTransitionBackgroundState.isReturnGestureInProgressProvider() ||
                                    videoCardTransitionBackgroundState.isGestureRestoreInProgressProvider()
                            },
                            ownsCapture = {
                                !nativeCardLayer.isReleased &&
                                    CardPositionManager.isActiveVideoCardPixelOwner(owner) &&
                                    CardPositionManager.isNativeVideoCardLayerCurrentOwner(nativeCardLayer)
                            },
                            captureAllowed = { wholeCardCaptureAllowed.value },
                            entryWorkReleased = { owner.entryWorkReleased },
                            capture = { captureWholeCardReturnBitmap(nativeCardLayer, owner) },
                        )
                    }
                }
            }
        }
        onClick(video.bvid, video.cid)
    })
    val homeCardScrolling = LocalHomeCardScrolling.current
    val homeCardTransitionAtMount = LocalHomeCardTransitionRunning.current
    val enterAnimationEnabledAtMount = remember(video.bvid) {
        resolveHomeCardEnterAnimationEnabledAtMount(
            baseAnimationEnabled = animationEnabled,
            // Mount-only sampling must not subscribe every neighboring card to transition changes.
            isReturningFromDetail = isReturningFromVideoDetail ||
                Snapshot.withoutReadObservation { homeCardTransitionAtMount?.value == true },
            isSwitchingCategory = CardPositionManager.isSwitchingCategory,
            // Read only at mount: stopping a scroll must not replay skipped entrances.
            isScrollInProgress = scrollLiteModeEnabled || homeCardScrolling?.value == true
        )
    }
    val coordinateEnterWithTransition = remember(animationEnabled, transitionEnabled) {
        animationEnabled && transitionEnabled
    }
    val adaptiveHoverShape = AppShapes.container(ContainerLevel.Card)
    Box(
        propagateMinConstraints = true,
        modifier = modifier
            .fillMaxWidth()
            .onVisibilityChanged(minFractionVisible = 0.01f, minDurationMs = 0L) {
                cardVisibleInWindow = it
            }
            .adaptiveCardHoverEffect(shape = adaptiveHoverShape)
            // 进场动画：挂载门控已含滚动/返回/切分类；与过渡并存时仅淡入不改几何
            .animateEnter(
                index = index,
                key = Unit,
                animationEnabled = enterAnimationEnabledAtMount,
                motionTier = motionTier,
                coordinateWithSharedTransition = coordinateEnterWithTransition
            )
            //  [新增] 记录卡片位置（仅存引用，boundsInRoot() 在交互时惰性计算）
            .onPlaced { coordinates ->
                cardCoordsRef.value = coordinates
            }
    ) {
        //  尝试获取共享元素作用域。首页点击视频时，由卡片主容器承载整体放大/回收。
        val sharedTransitionScope = LocalSharedTransitionScope.current
        val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
        val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
        val transitionAdaptiveInfo = com.android.purebilibili.core.ui.transition
            .LocalVideoTransitionAdaptiveInfo.current
        val effectiveTransitionEnabled = transitionEnabled
        val coverSharedEnabled = shouldEnableVideoCoverSharedTransition(
            transitionEnabled = effectiveTransitionEnabled && LocalSharedTransitionEnabled.current,
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null
        )
        val isQuickReturnLimited = isReturningFromVideoDetail && isQuickReturningFromVideoDetail
        val sharedTransitionOwnership = resolveVideoSharedTransitionOwnership(
            sourceRoute = effectiveSharedElementSourceRoute,
            coverSharedEnabled = coverSharedEnabled,
            isQuickReturnLimited = isQuickReturnLimited
        )
        val autoPlayOnOpenEnabled = LocalClickToPlayEnabled.current
        val videoSharedPlaybackIntent = remember(autoPlayOnOpenEnabled) {
            resolveVideoSharedTransitionPlaybackIntent(
                clickToPlayEnabled = autoPlayOnOpenEnabled
            )
        }
        val homeSharedTransitionSpecs = remember(
            effectiveSharedElementSourceRoute,
            effectiveTransitionEnabled,
            cardCornerRadius,
            sharedTransitionSpeedSettings,
            videoSharedPlaybackIntent,
            transitionAdaptiveInfo,
        ) {
            VideoCardSharedTransitionSpecs(
                motion = resolveVideoCardSharedTransitionMotionSpec(
                    sourceRoute = effectiveSharedElementSourceRoute,
                    transitionEnabled = effectiveTransitionEnabled,
                    speedSettings = sharedTransitionSpeedSettings,
                    adaptiveInfo = transitionAdaptiveInfo,
                ),
                visual = resolveVideoSharedTransitionVisualSpec(
                    sourceRoute = effectiveSharedElementSourceRoute,
                    sourceCornerDp = cardCornerRadius.value.roundToInt(),
                    playbackIntent = videoSharedPlaybackIntent,
                    adaptiveInfo = transitionAdaptiveInfo,
                )
            )
        }
        val homeSharedTransitionMotionSpec = homeSharedTransitionSpecs.motion
        val homeSharedTransitionVisualSpec = homeSharedTransitionSpecs.visual
        val routeMatchesSharedReturnTarget = remember(
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
        val sharedSourceOwnershipAllowed = isVideoCardSharedSourceInstanceOwner(
            sourceInstanceId = sharedSourceInstanceId,
            lastClickedSourceInstanceId = CardPositionManager.lastClickedVideoSourceInstanceId,
        )
        val isCoverSharedReturnTarget = routeMatchesSharedReturnTarget && sharedSourceOwnershipAllowed
        val useCardShellSharedBounds = sharedTransitionOwnership.useCardContainerSharedBounds &&
            sharedSourceOwnershipAllowed
        val miuixCardPixelOwnership =
            LocalMiuixVideoCardTransitionState.current.enabled && isCoverSharedReturnTarget
        val coverCrossfadeEnabled = shouldEnableVideoCardCoverCrossfade(
            isScrollInProgress = false,
            isReturningFromDetail = isReturningFromVideoDetail,
            useCoverSharedBounds = useCardShellSharedBounds || miuixCardPixelOwnership,
            isSharedReturnTarget = isCoverSharedReturnTarget
        )
        // lastClicked 生命周期内钉住点击时的封面源，避免返回途中换 URL/质量触发重解码闪烁。
        val pinnedSharedReturnCover = remember(isCoverSharedReturnTarget) {
            if (shouldPinVideoCardCoverForSharedReturn(isCoverSharedReturnTarget)) {
                coverUrl to coverCacheKey
            } else {
                null
            }
        }
        val requestCoverUrl = pinnedSharedReturnCover?.first ?: coverUrl
        val requestCoverCacheKey = pinnedSharedReturnCover?.second ?: coverCacheKey
        val cardShellShape = AppShapes.container(ContainerLevel.Card)
        // sharedBounds 与卡片底色拆开：
        // - 外层 Box 量尺寸 + 画 surface（只在源布局层，不进 overlay）
        // - 内层 Column 挂 sharedBounds（封面/标题等，无 solid fill）
        // 若把 cardContainer 画进 sharedBounds，预测返回时会盖住详情壳实时视频 → 大黑块。
        Box(
            propagateMinConstraints = true,
            modifier = Modifier
                .fillMaxWidth()
                .recordNativeVideoCardLayer(
                    layer = nativeCardLayer,
                    freezeProvider = {
                        freezeNativeCardLayer.value &&
                            CardPositionManager.isNativeVideoCardLayerCurrentOwner(nativeCardLayer)
                    },
                    bvid = video.bvid,
                    sourceRoute = effectiveSharedElementSourceRoute,
                    enabled = effectiveTransitionEnabled,
                    landingFeedbackEnabled = true,
                ),
        ) {
            val cardShellBaseColor = if (useCardEffectSurface) {
                Color.Transparent
            } else {
                AppSurfaceTokens.videoCardContainer()
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(cardShellShape)
                    .background(cardShellBaseColor)
                    .videoCardShellReturnCoverAlpha(
                        enabled = useCardShellSharedBounds,
                        bvid = video.bvid,
                        sourceRoute = effectiveSharedElementSourceRoute,
                        isReturningFromDetail = isReturningFromVideoDetail,
                    )
            )
            Column(
                modifier = Modifier
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
                    .clip(cardShellShape)
            ) {
        //  [性能优化] 封面圆角形状缓存（避免重组时重复创建）
        val coverShape = remember(cardCornerRadius) {
            AppShapes.topRounded(cardCornerRadius)
        }

        val coverSharedBoundsEnabled = shouldEnableVideoCoverSharedTransition(
            transitionEnabled = sharedTransitionOwnership.useCoverSharedBounds,
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null,
        ) && !useCardShellSharedBounds && sharedSourceOwnershipAllowed
        val coverSharedBoundsModifier = if (coverSharedBoundsEnabled) {
            with(requireNotNull(sharedTransitionScope)) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(
                        key = videoCoverSharedElementKey(
                            bvid = video.bvid,
                            sourceRoute = effectiveSharedElementSourceRoute,
                        )
                    ),
                    animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                    boundsTransform = { initialBounds, targetBounds ->
                        videoSharedElementBoundsTransformSpec(
                            motion = homeSharedTransitionMotionSpec,
                            initialBounds = initialBounds,
                            targetBounds = targetBounds,
                        )
                    },
                    // 禁止默认 Center：非首页预测返回中间态会往屏幕中心飞。
                    resizeMode = resolveVideoCardSharedBoundsResizeMode(),
                    clipInOverlayDuringTransition = OverlayClip(coverShape),
                )
            }
        } else {
            Modifier
        }

        Box(
            modifier = coverSharedBoundsModifier
                .fillMaxWidth()
                .videoCardShellReturnCoverAlpha(
                    enabled = useCardShellSharedBounds,
                    bvid = video.bvid,
                    sourceRoute = effectiveSharedElementSourceRoute,
                    isReturningFromDetail = isReturningFromVideoDetail,
                )
                .testTag("home_video_cover")
                .aspectRatio(coverAspectRatio)
                .clip(coverShape)
                .onPlaced { coordinates -> coverCoordsRef.value = coordinates }
                .onSizeChanged { if (coverRequestSpec == null) measuredCoverSize = it }
                .then(
                    if (nonHomeCardYFraction != null) Modifier.onGloballyPositioned { coordinates ->
                        if (screenMetrics.heightPx > 0f) {
                            nonHomeCardYFraction.floatValue =
                                (coordinates.boundsInRoot().top / screenMetrics.heightPx).coerceIn(0f, 1f)
                        }
                    } else Modifier
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
                //  [交互优化] 封面区域：点击跳转
                .pointerInput(isLongPressEnabled, onLongClick, onDismiss, onWatchLater, onUnfavorite) {
                    val hasPreviewAction = isLongPressEnabled && onLongClick != null
                    val hasLongPressMenu = isLongPressEnabled && (onDismiss != null || onWatchLater != null || onUnfavorite != null)
                    detectTapGestures(
                        onLongPress = if (isLongPressEnabled && (hasPreviewAction || hasLongPressMenu)) {
                            { pressOffset ->
                                if (hasPreviewAction) {
                                    longPressHaptic(HapticType.HEAVY)
                                    onLongClick(video)
                                } else if (shouldOpenLongPressMenu(hasPreviewAction, hasLongPressMenu)) {
                                    longPressHaptic(HapticType.HEAVY)
                                    if (onUnfavorite != null && onDismiss == null && onWatchLater == null) {
                                        showUnfavoriteDialog = true
                                    } else {
                                        menuGlassAnchorBounds = null
                                        openDismissMenu(coverCoordsRef.value, pressOffset)
                                    }
                                }
                            }
                        } else null,
                        onTap = {
                            triggerCardClick()
                        }
                    )
                }
        ) {
            // crossfade 必须进 remember key：若 clearReturning 后才打开 crossfade，
            // 会新建 ImageRequest 导致 Coil 再跑一次淡入闪烁（快速返回尤其明显）。
            val coverImageRequest = remember(
                requestCoverUrl,
                requestCoverCacheKey,
                coverCrossfadeEnabled,
            ) {
                ImageRequest.Builder(context)
                    .data(requestCoverUrl)
                    .apply {
                        coverRequestSpec?.let { size(it.widthPx, it.heightPx) }
                    }
                    .placeholderMemoryCacheKey(requestCoverCacheKey)
                    .crossfade(coverCrossfadeEnabled)
                    .memoryCacheKey(requestCoverCacheKey)
                    .diskCacheKey(requestCoverCacheKey)
                    .build()
            }
            var loadedCoverCacheKey by remember(requestCoverCacheKey) { mutableStateOf<String?>(null) }
            val coverWorkAllowed = LocalPageImageLoadingAllowed.current &&
                LocalHomeCardAuxiliaryWorkAllowed.current
            LaunchedEffect(
                loadedCoverCacheKey,
                requestCoverCacheKey,
                requestCoverUrl,
                homeCardDynamicTintEnabled,
                coverWorkAllowed,
            ) {
                if (!coverWorkAllowed || !homeCardDynamicTintEnabled || coverTint != null ||
                    loadedCoverCacheKey != requestCoverCacheKey
                ) return@LaunchedEffect
                val extracted = VideoCardCoverColorStore.extractColor(
                    context = context,
                    cacheKey = requestCoverCacheKey,
                    coverUrl = requestCoverUrl,
                )
                if (activeCoverCacheKey == requestCoverCacheKey && extracted != null) {
                    coverTint = extracted
                }
            }
            PageAwareAsyncImage(
                model = coverImageRequest,
                dimInDarkTheme = true,
                loadingAllowed = LocalHomeCardCoverLoadingAllowed.current
                    ?: LocalPageImageLoadingAllowed.current,
                contentDescription = null,
                onSuccess = {
                    loadedCoverCacheKey = requestCoverCacheKey
                },
                modifier = Modifier
                    .fillMaxSize(),
                // 官方粉版：居中 Crop；16:9 框配 16:9 投稿封面时基本不裁
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .recordNativeVideoCardLayer(
                        layer = nativeCoverOverlayLayer,
                        freezeProvider = {
                            freezeNativeCardLayer.value &&
                                CardPositionManager.isNativeCoverOverlayLayerCurrentOwner(
                                    nativeCoverOverlayLayer
                                )
                        },
                        bvid = video.bvid,
                        sourceRoute = effectiveSharedElementSourceRoute,
                        enabled = effectiveTransitionEnabled,
                    ),
            ) {
            VideoStatusBadges(
                labels = video.statusLabels,
                modifier = Modifier.align(Alignment.TopEnd).padding(AppSpacingTokens.Small),
            )

            //  底部渐变遮罩

            if (scrollLitePolicy.showCoverGradientMask) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MediaContrastPalette.Scrim.copy(alpha = 0.3f),
                                    MediaContrastPalette.Scrim.copy(alpha = 0.78f),
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
                        .background(MediaContrastPalette.Foreground.copy(alpha = 0.24f))
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
                val compactStatsLayout = remember(
                    compactStatsAvailableWidthDp,
                    primaryStatText,
                    secondaryStatText,
                    onlineCount,
                    showDurationOnCover,
                    showDurationOutside,
                    durationBadgeMinWidth,
                    durationStatMinWidthDp
                ) {
                    resolveVideoCardCompactCoverStatsLayout(
                        availableWidthDp = compactStatsAvailableWidthDp,
                        primaryStatText = primaryStatText,
                        secondaryStatText = secondaryStatText,
                        hasOnlineCount = onlineCount.isNotEmpty(),
                        durationBadgeMinWidthDp = if (showDurationOnCover) {
                            durationBadgeMinWidth.value
                        } else {
                            0f
                        },
                        durationStatMinWidthDp = if (showDurationOutside) {
                            durationStatMinWidthDp
                        } else {
                            0f
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = AppSpacingTokens.Small,
                            top = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro,
                            end = AppSpacingTokens.Small,
                            bottom = coverOverlayBottomLayout.compactStatsBottomPaddingDp.dp
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = compactStatsLayout.statsEndPaddingDp.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)
                    ) {
                        val compactViewsModifier = Modifier.widthIn(min = compactStatsLayout.primaryMinWidthDp.dp)
                        HomeVideoBadgePill(
                            modifier = compactViewsModifier,
                            style = badgeStylePolicy.coverStyle,
                            useRealtimeHaze = badgeEffectVisual.useRealtimeHaze,
                            shape = AppShapes.container(ContainerLevel.Pill),
                            containerColor = coverPillColors.containerColor,
                            borderColor = coverPillColors.borderColor
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(AppSpacingTokens.Small + AppSpacingTokens.Micro),
                                tint = MediaContrastPalette.Foreground.copy(alpha = 0.94f)
                            )
                            AppText(
                                text = primaryStatText,
                                color = MediaContrastPalette.Foreground.copy(alpha = 0.94f),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Medium,
                                style = coverOverlayTextStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                        }

                        if (compactStatsLayout.showSecondaryStat && secondaryStatText != null) {
                            val compactDanmakuModifier = Modifier.widthIn(min = compactStatsLayout.secondaryMinWidthDp.dp)
                            HomeVideoBadgePill(
                                modifier = compactDanmakuModifier,
                                style = badgeStylePolicy.coverStyle,
                                useRealtimeHaze = badgeEffectVisual.useRealtimeHaze,
                                shape = AppShapes.container(ContainerLevel.Pill),
                                containerColor = coverPillColors.containerColor,
                                borderColor = coverPillColors.borderColor
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Subtitles,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppSpacingTokens.Small + AppSpacingTokens.Micro),
                                    tint = MediaContrastPalette.Foreground.copy(alpha = 0.90f)
                                )
                                AppText(
                                    text = secondaryStatText,
                                    color = MediaContrastPalette.Foreground.copy(alpha = 0.90f),
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    style = coverOverlayTextStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible
                                )
                            }
                        }

                        if (compactStatsLayout.showOnlineCount) {
                            HomeVideoBadgePill(
                                modifier = Modifier.weight(1f, fill = false),
                                style = badgeStylePolicy.coverStyle,
                                useRealtimeHaze = badgeEffectVisual.useRealtimeHaze,
                                shape = AppShapes.container(ContainerLevel.Pill),
                                containerColor = coverPillColors.containerColor,
                                borderColor = coverPillColors.borderColor
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppSpacingTokens.Small + AppSpacingTokens.Micro),
                                    tint = MediaContrastPalette.Foreground.copy(alpha = 0.90f)
                                )
                                AppText(
                                    text = onlineCount,
                                    color = MediaContrastPalette.Foreground.copy(alpha = 0.90f),
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    style = coverOverlayTextStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible
                                )
                            }
                        }

                        // 时长随统计行显示（OUTSIDE_COVER）：闹钟图标 + 时长，与其他统计 pill 同结构。
                        if (showDurationOutside) {
                            HomeVideoBadgePill(
                                style = badgeStylePolicy.coverStyle,
                                useRealtimeHaze = badgeEffectVisual.useRealtimeHaze,
                                shape = AppShapes.container(ContainerLevel.Pill),
                                containerColor = coverPillColors.containerColor,
                                borderColor = coverPillColors.borderColor
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Alarm,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppSpacingTokens.Small + AppSpacingTokens.Micro),
                                    tint = MediaContrastPalette.Foreground.copy(alpha = 0.90f)
                                )
                                AppText(
                                    text = durationText,
                                    color = MediaContrastPalette.Foreground.copy(alpha = 0.90f),
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    style = coverOverlayTextStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible
                                )
                            }
                        }
                    }

                    //  时长标签 (与播放量/评论数同行对齐)
                    if (showDurationOnCover) {
                        AppText(
                            text = durationText,
                            color = MediaContrastPalette.Foreground,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.Medium,
                            style = coverOverlayTextStyle,
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
                    AppText(
                        text = durationText,
                        color = MediaContrastPalette.Foreground,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        fontWeight = FontWeight.Medium,
                        style = coverOverlayTextStyle,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = AppSpacingTokens.Small + AppSpacingTokens.Micro,
                                bottom = coverOverlayBottomLayout.floatingDurationBottomPaddingDp.dp
                            )
                    )
                }
            }
            }

        }

        val infoSurfaceShape = remember(cardCornerRadius) {
            AppShapes.bottomRounded(cardCornerRadius)
        }
        val infoContainerModifier = if (useCardEffectSurface) {
            // Wallpaper-only Haze for realtime blur (never main content HazeState).
            val hazeModifier = if (useRealtimeInfoHaze) {
                Modifier.unifiedBlur(
                    hazeState = requireNotNull(wallpaperHazeState),
                    shape = infoSurfaceShape,
                    surfaceType = BlurSurfaceType.BOTTOM_BAR,
                    isScrolling = LocalHomeCardScrolling.current?.value == true,
                    isTransitionRunning = LocalHomeCardTransitionRunning.current?.value == true,
                    forceLowBudget = false
                )
            } else {
                Modifier
            }
            // Wallpaper-only Miuix blur. The source is attached to the sibling wallpaper layer,
            // so this surface never samples its own cover, text, or the feed content.
            val wallpaperBlurRadiusPx = with(density) { 24.dp.toPx() }
            val wallpaperBackdropModifier = if (
                useRealtimeWallpaperBackdrop &&
                homeWallpaperBackdrop != null
            ) {
                Modifier.drawBackdrop(
                    backdrop = homeWallpaperBackdrop,
                    shape = { infoSurfaceShape },
                    effects = {
                        blur(wallpaperBlurRadiusPx, wallpaperBlurRadiusPx)
                    }
                )
            } else {
                Modifier
            }
            val baseContainerColor = AppSurfaceTokens.videoCardContainer(glassEnabled = true)
            val baseBorderColor = MediaContrastPalette.Foreground
            Modifier
                .fillMaxWidth()
                .clip(infoSurfaceShape)
                // Include the wallpaper in the recorded native pixels before freezing on click.
                // A translucent plate alone would blend with the detail page during handoff.
                .then(
                    if (useWallpaperInfoBacking) {
                        Modifier.sharedGlobalWallpaperBackground(
                            fallbackColor = resolvedInfoBackingColor(0.5f),
                            fallbackColorProvider = {
                                // 滚动只使绘制失效，使用实时坐标保持壁纸取色对齐。
                                homeScrollTickProvider?.invoke()
                                resolvedInfoBackingColor(currentCoverYFraction())
                            },
                        )
                    } else Modifier
                )
                .then(hazeModifier)
                .then(wallpaperBackdropModifier)
                .onPlaced { coordinates ->
                    infoLayoutCoordinates.value = coordinates
                }
                .drawWithCache {
                    val cachedSpec = resolveVideoCardAmbientDrawSpec(
                        wallpaperPalette = wallpaperPalette,
                        yFraction = 0.5f,
                        coverTint = if (animatedCoverTint.alpha > 0f) animatedCoverTint else null,
                        wallpaperTintEnabled = resolvedWallpaperTintEnabled,
                        isDarkTheme = isDarkCardTheme,
                        defaultContainerColor = baseContainerColor,
                        defaultBorderColor = baseBorderColor,
                        isDataSaverActive = isDataSaverActive,
                        frostedGlassEnabled = homeCardFrostedGlassEnabled,
                        dynamicTintEnabled = homeCardDynamicTintEnabled,
                    )
                    val followsWallpaperPosition = homeCardDynamicTintEnabled &&
                        !useRealtimeWallpaperBackdrop && resolvedWallpaperTintEnabled && wallpaperPalette != null
                    val glowBrush = if (cachedSpec.coverGlowAlpha > 0f && animatedCoverTint.alpha > 0f) {
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedCoverTint.copy(alpha = cachedSpec.coverGlowAlpha),
                                animatedCoverTint.copy(alpha = cachedSpec.coverGlowAlpha * 0.35f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = size.height * 0.85f,
                        )
                    } else null
                    onDrawBehind {
                        if (homeCardDynamicTintEnabled && !useRealtimeWallpaperBackdrop) {
                            val containerColor = if (followsWallpaperPosition) {
                                // Subscribe to scrolling only when position changes wallpaper tint.
                                homeScrollTickProvider?.invoke()
                                val coords = infoLayoutCoordinates.value
                                val currentY = if (coords != null && coords.isAttached) {
                                    coords.positionInRoot().y
                                } else null
                                val yFraction = if (currentY != null && screenMetrics.heightPx > 0f) {
                                    (currentY / screenMetrics.heightPx).coerceIn(0f, 1f)
                                } else {
                                    currentCoverYFraction()
                                }
                                // Only the wallpaper RGB depends on Y; alpha/glow/border remain cached.
                                interpolateWallpaperColor(requireNotNull(wallpaperPalette), yFraction)
                                    .copy(alpha = cachedSpec.containerColor.alpha)
                            } else {
                                cachedSpec.containerColor
                            }
                            drawRect(color = containerColor)
                            if (glowBrush != null) drawRect(brush = glowBrush)
                        } else {
                            val neutralGlassAlpha = if (isDarkCardTheme) 0.38f else 0.34f
                            val realtimeAlpha = if (isDarkCardTheme) 0.44f else 0.36f
                            drawRect(
                                color = baseContainerColor.copy(
                                    alpha = if (!homeCardDynamicTintEnabled && !hasRealtimeInfoBlur) {
                                        1f
                                    } else if (useRealtimeWallpaperBackdrop) {
                                        realtimeAlpha
                                    } else if (homeCardFrostedGlassEnabled) {
                                        neutralGlassAlpha
                                    } else {
                                        infoSurfaceAppearance.containerAlpha
                                    }
                                )
                            )
                        }
                    }
                }
                .border(
                    width = 0.5.dp,
                    color = if (homeCardDynamicTintEnabled && !useRealtimeWallpaperBackdrop) {
                        val borderAlpha = if (isDarkCardTheme) 0.30f else 0.45f
                        val baseBorder = Color.White.copy(alpha = borderAlpha)
                        if (
                            shouldUseCoverTintForCard(resolvedWallpaperTintEnabled, coverTint) &&
                            animatedCoverTint.alpha > 0f
                        ) {
                            val borderBlend = if (isDarkCardTheme) 0.50f else 0.40f
                            androidx.compose.ui.graphics.lerp(baseBorder, animatedCoverTint, borderBlend).copy(alpha = borderAlpha)
                        } else {
                            baseBorder
                        }
                    } else {
                        baseBorderColor.copy(
                            alpha = if (useRealtimeWallpaperBackdrop) {
                                if (isDarkCardTheme) 0.14f else 0.22f
                            } else {
                                infoSurfaceAppearance.borderAlpha
                            }
                        )
                    },
                    shape = infoSurfaceShape
                )
        } else {
            val ambientCoverGlowModifier = if (
                homeCardDynamicTintEnabled &&
                shouldUseCoverTintForCard(resolvedWallpaperTintEnabled, coverTint) &&
                animatedCoverTint.alpha > 0f
            ) {
                Modifier.drawWithCache {
                    val glowBrush = Brush.verticalGradient(
                        colors = listOf(
                            animatedCoverTint.copy(alpha = if (isDarkCardTheme) 0.16f else 0.10f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.75f,
                    )
                    onDrawBehind { drawRect(brush = glowBrush) }
                }
            } else {
                Modifier
            }
            Modifier
                .fillMaxWidth()
                .then(ambientCoverGlowModifier)
        }

        val infoBottomPadding = if (compactMetadata) {
            AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro
        } else {
            AppSpacingTokens.Small
        }

        Box(
            modifier = infoContainerModifier.videoCardShellReturnChromeAlpha(
                enabled = useCardShellSharedBounds,
                bvid = video.bvid,
                sourceRoute = effectiveSharedElementSourceRoute,
                isReturningFromDetail = isReturningFromVideoDetail,
                isQuickReturnFromDetail = isQuickReturningFromVideoDetail,
            )
        ) {
        // Text keeps its inset; the trailing action aligns to the full info surface.
        Column(
            modifier = Modifier.padding(
                start = AppSpacingTokens.Small + AppSpacingTokens.Micro,
                top = if (useCardEffectSurface) infoBottomPadding else AppSpacingTokens.None,
                end = AppSpacingTokens.Small + AppSpacingTokens.Micro,
                bottom = infoBottomPadding,
            )
        ) {
        if (!useCardEffectSurface) {
            Spacer(modifier = Modifier.height(if (compactMetadata) AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro else AppSpacingTokens.Small))
        }

        AppText(
            text = highlightedTitle ?: AnnotatedString(video.title),
            maxLines = videoCardTitleMaxLines(titleMaxLines),
            minLines = titleMinLines,
            overflow = videoCardTitleOverflow(),
            style = contentTypography.title.copy(
                color = adaptiveContentColors.titleColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "视频标题: ${video.title}" }
                .onPlaced { coordinates ->
                    titleCoordsRef.value = coordinates
                }
                //  [交互优化] 标题区域：长按弹出菜单，点击跳转
                .pointerInput(isLongPressEnabled, onLongClick, onDismiss, onWatchLater, onUnfavorite) {
                    val hasPreviewAction = isLongPressEnabled && onLongClick != null
                    val hasLongPressMenu = isLongPressEnabled && (onDismiss != null || onWatchLater != null || onUnfavorite != null)
                    detectTapGestures(
                        onLongPress = if (isLongPressEnabled && (hasPreviewAction || hasLongPressMenu)) {
                            { pressOffset ->
                                if (hasPreviewAction) {
                                    longPressHaptic(HapticType.HEAVY)
                                    onLongClick(video)
                                } else if (shouldOpenLongPressMenu(hasPreviewAction, hasLongPressMenu)) {
                                    longPressHaptic(HapticType.HEAVY)
                                    if (onUnfavorite != null && onDismiss == null && onWatchLater == null) {
                                        showUnfavoriteDialog = true
                                    } else {
                                        menuGlassAnchorBounds = null
                                        openDismissMenu(titleCoordsRef.value, pressOffset)
                                    }
                                }
                            }
                        } else null,
                        onTap = {
                            triggerCardClick()
                        }
                    )
                }
        )

        Spacer(modifier = Modifier.height(if (compactMetadata) AppSpacingTokens.ExtraSmall else AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro))

        Column(
            modifier = resolveVideoCardMetadataModifier(
                (if (onUnfavorite != null) 1 else 0) + (if (hasOverflowMenu) 1 else 0)
            )
        ) {
        val metadataColors = resolveHomeVideoCardMetadataColors(
            onSurfaceColor = adaptiveContentColors.titleColor,
            onSurfaceVariantColor = adaptiveContentColors.subtitleColor,
        )

        val resolvedUpBadgeVisibility = com.android.purebilibili.core.ui.LocalUpBadgeVisibility.current
        VideoCardOwnerMetadata(
            video = video,
            isFollowing = isFollowing,
            showUpBadge = showUpBadge ?: resolvedUpBadgeVisibility.showBadges,
            showUpAvatar = showUpAvatar ?: resolvedUpBadgeVisibility.showAvatars,
            upFollowerCount = upFollowerCount,
            upVideoCount = upVideoCount,
            infoBadgeStyle = badgeStylePolicy.infoStyle,
            inlinePillColors = inlinePillColors,
            metadataColors = metadataColors,
            onUpClick = onUpClick,
            modifier = resolveVideoCardMetadataRowModifier()
        )

        if (scrollLitePolicy.showSecondaryStatsRow) {
            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)
            ) {
                HorizontalVideoStatRow(
                    playText = primaryStatText,
                    danmakuText = secondaryStatText.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (onlineCount.isNotEmpty()) {
                    HomeVideoBadgePill(
                        style = badgeStylePolicy.infoStyle,
                        useRealtimeHaze = badgeEffectVisual.useRealtimeHaze,
                        shape = AppShapes.container(ContainerLevel.Pill),
                        containerColor = inlinePillColors.containerColor,
                        borderColor = inlinePillColors.borderColor
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(AppSpacingTokens.Medium),
                            tint = adaptiveContentColors.subtitleColor
                        )
                        AppText(
                            text = onlineCount,
                            color = adaptiveContentColors.subtitleColor,
                            style = contentTypography.statistic.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }

        VideoCardDurationPublishRow(
            // 数据贴封面时，时长已随统计行以 pill 呈现，不再在信息区重复显示
            durationText = durationText
                .takeIf { showDurationOutside && !scrollLitePolicy.showCompactStatsOnCover }
                .orEmpty(),
            publishTimeText = publishTimeRowText,
            emphasizePublishTime = emphasizePublishTime,
            publishTimeColor = metadataColors.publishTimeColor,
            topSpacing = if (compactMetadata) {
                AppSpacingTokens.ExtraSmall
            } else {
                AppSpacingTokens.Small - AppSpacingTokens.Micro
            },
        )

        }
        }

        if (hasTrailingCardAction) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onUnfavorite != null) {
                    Box(
                        modifier = Modifier
                            .size(AppChromeSizeTokens.MinimumTouchTarget)
                            .appActionClickable {
                                haptic(HapticType.MEDIUM)
                                showUnfavoriteDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // 语义是“从收藏夹移除”，用 ✕ 而不是 ThumbUp——
                        // 后者会被理解为点赞，与行为不符。
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "从收藏夹移除",
                            modifier = Modifier.size(AppSpacingTokens.Large),
                            tint = adaptiveContentColors.subtitleColor.copy(alpha = 0.7f)
                        )
                    }
                }

                if (hasOverflowMenu) {
                    Box(
                        modifier = Modifier
                            .size(AppChromeSizeTokens.MinimumTouchTarget)
                            .semantics { contentDescription = "更多操作" }
                            .onPlaced { coordinates ->
                                menuButtonCoordsRef.value = coordinates
                            }
                            // 玻璃开启时按住即从按钮源图层展开菜单；关闭时 source 短路，仅剩 clickable。
                            .appGlassMenuSource(overflowMenuSource, onOpen = {
                                haptic(HapticType.LIGHT)
                                menuGlassAnchorBounds = null
                                openDismissMenu(menuButtonCoordsRef.value, null)
                            })
                            .appActionClickable {
                                haptic(HapticType.LIGHT)
                                menuGlassAnchorBounds = null
                                openDismissMenu(menuButtonCoordsRef.value, null)
                            },
                        // 触控区 48dp，但图标光学上贴卡片右下角，与发布时间行对齐。
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = adaptiveContentColors.subtitleColor,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = AppSpacingTokens.ExtraSmall, bottom = AppSpacingTokens.ExtraSmall)
                        )
                    }
                }
            }
        }

            }
        }
    }

        // Equal-height feed rows give this card tight height constraints. The root propagates
        // those minima, so isolate the small popup anchor in an overlay that clears them.
        // matchParentSize keeps the overlay out of the card's intrinsic height calculation.
        Box(modifier = Modifier.matchParentSize(), propagateMinConstraints = false) {
            // 菜单需要挂在一个本地小锚点上，避免 DropdownMenu 在整张卡片根节点右侧 fallback 时反向偏移。
            //
            // 这里保留非 lambda 版 offset（lint 的 UseOfNonLambdaOffsetOverload 会报）：
            // menuOffset 只在每次长按时变一次，不是逐帧动画值，lambda 版没有实际收益；
            // 而改写形式会打断 VideoCardLongPressPolicyTest 对上面那次回归修复的字面守卫。
            // 为零收益去动别人的回归守卫不划算。
            @Suppress("UseOfNonLambdaOffsetOverload")
            Box(
                modifier = Modifier
                    .offset(x = menuOffset.x, y = menuOffset.y)
                    .size(AppSpacingTokens.Micro / 2)
            ) {
                AppDropdownMenu(
                    expanded = showDismissMenu,
                    onDismissRequest = { showDismissMenu = false },
                    offset = DpOffset.Zero,
                    glassAnchorBounds = menuGlassAnchorBounds,
                    glassSource = overflowMenuSource.takeIf { menuFromOverflowButton },
                ) {
                    // 卡片菜单提供内容号、观看、作者与隐私相关操作。
                    if (video.bvid.isNotBlank()) {
                        AppDropdownMenuItem(
                            text = { AppText(video.bvid, color = AppContentColors.primaryText()) },
                            leadingIcon = { AppIcon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp), tint = AppContentColors.primaryText()) },
                            onClick = {
                                showDismissMenu = false
                                copyPlainTextToClipboard(context, video.bvid, "BV号")
                                android.widget.Toast.makeText(context, "已复制 ${video.bvid}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // 稍后再看
                    if (onWatchLater != null) {
                        AppDropdownMenuItem(
                            leadingIcon = { AppIcon(Icons.Outlined.WatchLater, contentDescription = null, modifier = Modifier.size(20.dp), tint = AppContentColors.primaryText()) },
                            text = {
                                AppText(
                                    "稍后再看",
                                    color = AppContentColors.primaryText()
                                )
                            },
                            onClick = {
                                showDismissMenu = false
                                onWatchLater.invoke()
                            }
                        )
                    }

                    // AI 总结（需要 cid 才能请求）
                    if (video.cid > 0L && video.bvid.isNotBlank()) {
                        AppDropdownMenuItem(
                            leadingIcon = { AppIcon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp), tint = AppContentColors.primaryText()) },
                            text = { AppText("AI总结", color = AppContentColors.primaryText()) },
                            onClick = {
                                showDismissMenu = false
                                launchAiSummary()
                            }
                        )
                    }

                    // 访问 UP 主
                    if (onUpClick != null && video.owner.mid > 0L) {
                        AppDropdownMenuItem(
                            leadingIcon = { AppIcon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = AppContentColors.primaryText()) },
                            text = { AppText("访问：${video.owner.name}", color = AppContentColors.primaryText()) },
                            onClick = {
                                showDismissMenu = false
                                onUpClick.invoke(video.owner.mid)
                            }
                        )
                    }

                    // 不感兴趣
                    if (onDismiss != null) {
                        AppDropdownMenuItem(
                            leadingIcon = { AppIcon(Icons.Outlined.NotInterested, contentDescription = null, modifier = Modifier.size(20.dp), tint = AppContentColors.primaryText()) },
                            text = {
                                AppText(
                                    dismissMenuText,
                                    color = AppContentColors.primaryText()
                                )
                            },
                            onClick = {
                                showDismissMenu = false
                                onDismiss.invoke()
                            }
                        )
                    }

                    // 取消收藏 (仅在收藏页显示)
                    if (onUnfavorite != null) {
                         AppDropdownMenuItem(
                            leadingIcon = { AppIcon(Icons.Outlined.BookmarkRemove, contentDescription = null, modifier = Modifier.size(20.dp), tint = AppContentColors.error()) },
                            text = {
                                AppText(
                                    "取消收藏",
                                    color = AppContentColors.error()  // 使用错误色强调删除操作
                                )
                            },
                            onClick = {
                                showDismissMenu = false
                                // onUnfavorite.invoke() -> 改为弹窗确认
                                showUnfavoriteDialog = true
                            }
                        )
                    }

                    // 拉黑 UP 主：本地屏蔽并同步 B 站关系
                    if (video.owner.mid > 0L) {
                        AppDropdownMenuItem(
                            leadingIcon = { AppIcon(Icons.Outlined.PersonOff, contentDescription = null, modifier = Modifier.size(20.dp), tint = AppContentColors.primaryText()) },
                            text = { AppText("拉黑：${video.owner.name}", color = AppContentColors.primaryText()) },
                            onClick = {
                                showDismissMenu = false
                                showBlockDialog = true
                            }
                        )
                    }

                    // 无痕模式开关
                    val privacyModeEnabled by remember {
                        com.android.purebilibili.core.store.SettingsManager.getPrivacyModeEnabled(context)
                    }.collectAsStateWithLifecycle(initialValue = false)
                    AppDropdownMenuItem(
                        leadingIcon = {
                            AppIcon(if (privacyModeEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = null, modifier = Modifier.size(20.dp), tint = AppContentColors.primaryText())
                        },
                        text = {
                            AppText(
                                if (privacyModeEnabled) "退出无痕模式" else "进入无痕模式",
                                color = AppContentColors.primaryText()
                            )
                        },
                        onClick = {
                            showDismissMenu = false
                            cardActionScope.launch {
                                val next = !privacyModeEnabled
                                com.android.purebilibili.core.store.SettingsManager.setPrivacyModeEnabled(context, next)
                                android.widget.Toast.makeText(
                                    context,
                                    if (next) "已进入无痕模式" else "已退出无痕模式",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showUnfavoriteDialog) {
        AppAlertDialog(
            onDismissRequest = { showUnfavoriteDialog = false },
            title = { AppText("取消收藏") },
            text = { AppText("确定要将此视频从收藏夹中移除吗？") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        showUnfavoriteDialog = false
                        onUnfavorite?.invoke()
                    }
                ) {
                    AppText("移除", color = AppContentColors.error())
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showUnfavoriteDialog = false }) {
                    AppText("取消")
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
    /** When true, sample [LocalMainHazeState] like the bottom bar (realtime blur). */
    useRealtimeHaze: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    if (style == HomeVideoBadgeStyle.GLASS) {
        // Wallpaper-only HazeState (sibling source), never main content HazeState —
        // badges live inside the main hazeSource and would SO the render tree otherwise.
        val hazeState = LocalWallpaperHazeState.current
        // Motion changes input sampling quality while keeping blur radius and fill stable.
        val glassModifier = if (useRealtimeHaze && hazeState != null) {
            modifier.unifiedBlur(
                hazeState = hazeState,
                shape = shape,
                surfaceType = BlurSurfaceType.BOTTOM_BAR,
                isScrolling = LocalHomeCardScrolling.current?.value == true,
                isTransitionRunning = LocalHomeCardTransitionRunning.current?.value == true,
                forceLowBudget = false
            )
        } else {
            modifier
        }
        // Slightly clearer fill when realtime haze already provides frosted backdrop.
        val surfaceColor = if (useRealtimeHaze && hazeState != null) {
            containerColor.copy(alpha = (containerColor.alpha * 0.55f).coerceIn(0.08f, 0.45f))
        } else {
            containerColor
        }
        AppSurface(
            modifier = glassModifier,
            shape = shape,
            color = surfaceColor,
            border = BorderStroke(AppSpacingTokens.Micro * 0.4f, borderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro, vertical = AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro),
                content = content
            )
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro),
            content = content
        )
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
