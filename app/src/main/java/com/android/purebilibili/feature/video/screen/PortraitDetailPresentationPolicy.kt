package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.store.PortraitPlayerCollapseMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PortraitInlinePlayerLayoutSpec(
    val widthDp: Float,
    val heightDp: Float
)

internal data class StandalonePortraitPagerMotionSpec(
    val enterDurationMillis: Int,
    val exitDurationMillis: Int,
    val exitScaleTarget: Float,
    val exitTranslateUpFraction: Float,
    val inlineReturnDurationMillis: Int,
    val inlineReturnInitialScale: Float
)

internal enum class PortraitFullscreenButtonAction {
    ENTER_PORTRAIT_FULLSCREEN
}

internal fun shouldUseOfficialInlinePortraitDetailExperience(
    useTabletLayout: Boolean,
    isVerticalVideo: Boolean,
    portraitExperienceEnabled: Boolean,
    directPortraitEntry: Boolean = false
): Boolean {
    // 「竖屏直达」走 card→全屏 morph，不能先落官方内联详情再二次跳进 pager。
    if (directPortraitEntry) return false
    return portraitExperienceEnabled && !useTabletLayout && isVerticalVideo
}

internal fun shouldUseSharedPlayerForPortraitFullscreen(): Boolean {
    return true
}

internal fun shouldShowStandalonePortraitPager(
    portraitExperienceEnabled: Boolean,
    isPortraitFullscreen: Boolean,
    useOfficialInlinePortraitDetailExperience: Boolean,
    hasPlayableState: Boolean
): Boolean {
    return portraitExperienceEnabled &&
        isPortraitFullscreen &&
        hasPlayableState
}

internal fun shouldActivatePortraitFullscreenState(
    portraitExperienceEnabled: Boolean
): Boolean {
    return portraitExperienceEnabled
}

internal fun resolveStandalonePortraitPagerMotionSpec(): StandalonePortraitPagerMotionSpec {
    return StandalonePortraitPagerMotionSpec(
        enterDurationMillis = 240,
        exitDurationMillis = 220,
        exitScaleTarget = 0.96f,
        exitTranslateUpFraction = 0.08f,
        inlineReturnDurationMillis = 240,
        inlineReturnInitialScale = 0.985f
    )
}

internal fun shouldHidePausedInlinePlayerForComments(
    selectedTabIndex: Int,
    isPlaybackPaused: Boolean,
    isCommentThreadVisible: Boolean = false,
): Boolean = isPlaybackPaused && (selectedTabIndex in 0..1 || isCommentThreadVisible)

/** Restore the whole player before giving a downward comment drag to refresh. */
internal fun shouldEnableCommentPullToRefresh(playerCollapseProgress: Float): Boolean =
    playerCollapseProgress <= 0f

/** 暂停画面收向工具栏终点时提前挂载，显隐跟随折叠进度；紧凑预览仍保留播放器控件。 */
internal fun shouldShowCollapsedPlayerPlayToolbar(
    isPlaybackPaused: Boolean,
    collapseProgress: Float,
    collapsedViewportHeightDp: Float,
    isFullscreen: Boolean,
): Boolean = !isFullscreen && isPlaybackPaused && collapseProgress > 0f &&
    collapsedViewportHeightDp in 0f..56.5f

/** 渐显中的工具栏不抢暂停画面的点击与无障碍焦点。 */
internal fun canInteractWithCollapsedPlayerPlayToolbar(collapseProgress: Float): Boolean =
    collapseProgress >= 0.98f

internal fun shouldEnableInlinePortraitScrollTransform(
    collapseMode: PortraitPlayerCollapseMode,
    selectedTabIndex: Int,
    isVerticalVideo: Boolean = true,
    isPlaybackPaused: Boolean = false,
    isCommentThreadVisible: Boolean = false,
): Boolean {
    if (shouldHidePausedInlinePlayerForComments(selectedTabIndex, isPlaybackPaused, isCommentThreadVisible)) {
        return true
    }
    if (!collapseMode.enablesVideoOrientation(isVerticalVideo)) return false
    if (collapseMode == PortraitPlayerCollapseMode.PAUSED_ONLY && !isPlaybackPaused) return false
    return when (selectedTabIndex) {
        0 -> collapseMode.enablesIntro
        1 -> collapseMode.enablesComment
        else -> true
    }
}

/**
 * Whether the standalone portrait pager should cross-fade on entry.
 *
 * The detail player and fullscreen pager share one player. Cross-fading the old inline
 * viewport with the new pager makes swipe-to-fullscreen visibly pause in a centered frame.
 * 不交叉淡化播放面；内嵌进入时由视口边界展开承接，直接入场继续由路由负责。
 */
internal fun shouldAnimateStandalonePortraitPager(
    useSharedPlayer: Boolean,
    directPortraitEntry: Boolean = false
): Boolean {
    @Suppress("UNUSED_PARAMETER")
    val ignored = useSharedPlayer
    @Suppress("UNUSED_PARAMETER")
    val ignoredDirectEntry = directPortraitEntry
    return false
}

/**
 * When true, keep the phone detail body fully suppressed from the first frame of a
 * direct-portrait morph so only the full-bleed shell + entry cover are visible.
 */
internal fun shouldSuppressPhoneDetailBodyForDirectPortraitEntry(
    directPortraitEntry: Boolean,
    isPortraitFullscreen: Boolean
): Boolean {
    return directPortraitEntry && isPortraitFullscreen
}

/**
 * Standalone portrait pager covers the phone detail body. Suppress that body so inline
 * VideoPlayerSection does not dual-host the shared player under the pager.
 */
internal fun shouldSuppressPhoneDetailBodyUnderStandalonePortraitPager(
    portraitExperienceEnabled: Boolean,
    isPortraitFullscreen: Boolean,
    hasPlayableState: Boolean,
): Boolean {
    return portraitExperienceEnabled &&
        isPortraitFullscreen &&
        hasPlayableState
}

internal fun resolvePortraitFullscreenButtonAction(
    useOfficialInlinePortraitDetailExperience: Boolean
): PortraitFullscreenButtonAction {
    return PortraitFullscreenButtonAction.ENTER_PORTRAIT_FULLSCREEN
}

/**
 * Switching tabs never requests a compact player, including short windows and foldable covers.
 * Collapse belongs to the scroll gesture and its orientation/pause setting. A viewport-space
 * fallback here bypasses OFF and also skips nested-scroll consumption, locking comments compact.
 */
@Suppress("UNUSED_PARAMETER")
internal fun shouldUseCompactInlinePortraitPlayerForCommentTab(
    useOfficialInlinePortraitDetailExperience: Boolean,
    selectedTabIndex: Int,
    isPortraitFullscreen: Boolean,
    isCommentThreadVisible: Boolean = false,
    collapseMode: PortraitPlayerCollapseMode = PortraitPlayerCollapseMode.BOTH,
    isVerticalVideo: Boolean = true,
    isPlaybackPaused: Boolean = false,
): Boolean = false

internal fun shouldUseCompactInlinePortraitPlayerForIntroScroll(
    useOfficialInlinePortraitDetailExperience: Boolean,
    selectedTabIndex: Int,
    isPortraitFullscreen: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    collapseMode: PortraitPlayerCollapseMode = PortraitPlayerCollapseMode.BOTH,
    isVerticalVideo: Boolean = true,
    isPlaybackPaused: Boolean = false,
    introScrollThresholdPx: Int = 56
): Boolean {
    if (!useOfficialInlinePortraitDetailExperience || isPortraitFullscreen) return false
    if (!collapseMode.enablesVideoOrientation(isVerticalVideo)) return false
    if (collapseMode == PortraitPlayerCollapseMode.PAUSED_ONLY && !isPlaybackPaused) return false
    if (!collapseMode.enablesIntro) return false
    if (selectedTabIndex != 0) return false
    return isVideoDetailIntroScrollPastCollapseThreshold(
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        thresholdPx = introScrollThresholdPx
    )
}

internal fun isVideoDetailIntroScrollPastCollapseThreshold(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    thresholdPx: Int = 56
): Boolean {
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset >= thresholdPx
}

internal fun resolveInlinePortraitPlayerCollapseProgress(
    manualCollapseProgress: Float,
    compactForCommentTabProgress: Float,
    restoreRequested: Boolean = false,
    hidePausedPlayerForComments: Boolean = false,
): Float {
    if (restoreRequested) return 0f
    // 评论空间保护会让手势让路给列表，画面必须同步采用紧凑进度，不能依赖暂停后再滑动。
    // 暂停时仍按手势位移隐藏，避免把原本的紧凑画面直接压成高度 0。
    val compactProgress = if (hidePausedPlayerForComments) 0f else compactForCommentTabProgress
    return max(manualCollapseProgress, compactProgress).coerceIn(0f, 1f)
}

internal fun resolveInlinePortraitPlayerCommentCollapseDurationMillis(
    tabSwitchAnimationSpec: VideoContentTabSwitchAnimationSpec
): Int {
    return tabSwitchAnimationSpec.durationMs
}

/** 暂停浏览详情时收起画面，可保留固定高度的恢复播放工具栏。 */
internal fun resolveInlinePlayerCollapsedViewportHeightDp(
    standardCollapsedHeightDp: Float,
    hidePausedPlayerForComments: Boolean,
    retainPlayToolbar: Boolean = false,
): Float = if (hidePausedPlayerForComments) {
    if (retainPlayToolbar) 56f else 0f
} else standardCollapsedHeightDp.coerceAtLeast(0f)

/**
 * Inline portrait detail player size.
 *
 * BiliPai parity for phone: expanded ≈ max(longestSide * 0.65, shortestSide),
 * so vertical videos get a tall preview without becoming full-screen cards.
 * Wide foldable portrait windows stay capped so intro/comment remain reachable.
 */
internal fun resolvePortraitInlinePlayerLayoutSpec(
    screenWidthDp: Float,
    screenHeightDp: Float,
    isCollapsed: Boolean,
    isFoldableCoverWindow: Boolean = false,
): PortraitInlinePlayerLayoutSpec {
    val width = screenWidthDp
    val standardCollapsedHeight = screenWidthDp * 9f / 16f
    val collapsedHeight = if (
        isFoldableCoverWindow &&
        screenHeightDp > 0f &&
        screenHeightDp < FOLDABLE_COVER_COMPACT_HEIGHT_MAX_DP
    ) {
        min(
            standardCollapsedHeight,
            screenHeightDp * FOLDABLE_COVER_COLLAPSED_PLAYER_HEIGHT_FRACTION,
        )
    } else {
        standardCollapsedHeight
    }
    if (isCollapsed) {
        return PortraitInlinePlayerLayoutSpec(
            widthDp = width,
            heightDp = collapsedHeight
        )
    }

    val shortestSide = min(screenWidthDp, screenHeightDp)
    val longestSide = max(screenWidthDp, screenHeightDp)
    val isWidePortraitWindow = screenWidthDp >= 600f && screenHeightDp > screenWidthDp
    val expandedHeight = if (isWidePortraitWindow) {
        // 折叠屏内屏竖屏窗口不能按手机竖屏体验撑满首屏，否则详情区入口会被播放器挤出。
        min(max(screenHeightDp * 0.52f, collapsedHeight), screenWidthDp)
    } else {
        max(longestSide * 0.65f, shortestSide)
    }
    val coverExpandedHeightLimit = if (
        isFoldableCoverWindow &&
        screenHeightDp > 0f &&
        screenHeightDp < FOLDABLE_COVER_COMPACT_HEIGHT_MAX_DP
    ) {
        screenHeightDp * FOLDABLE_COVER_COMPACT_PLAYER_HEIGHT_FRACTION
    } else {
        Float.POSITIVE_INFINITY
    }
    return PortraitInlinePlayerLayoutSpec(
        widthDp = width,
        heightDp = min(expandedHeight, coverExpandedHeightLimit)
    )
}

/**
 * 普通视频竖屏详情播放器内容高度：按真实布局宽度计算，加载封面与播放画面均为 16:9，
 * 避免 configuration.screenWidthDp 与窗口可用宽度不一致时左右黑边。
 */
internal fun resolvePhoneDetailPlayerContentHeightPx(
    layoutWidthPx: Int,
): Int {
    val width = layoutWidthPx.coerceAtLeast(1)
    return (width / PHONE_DETAIL_VIDEO_ASPECT_RATIO).roundToInt().coerceAtLeast(1)
}
