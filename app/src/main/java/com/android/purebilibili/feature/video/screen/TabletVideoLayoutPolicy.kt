package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.store.TabletCommentPanelWidthPreset
import com.android.purebilibili.core.store.TabletSecondaryDefaultTab
import com.android.purebilibili.core.util.AppFoldPosture
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

internal data class TabletTheaterModeController(
    val isActive: Boolean,
    val toggle: () -> Unit,
)

internal val LocalTabletTheaterModeController = compositionLocalOf<TabletTheaterModeController?> { null }

internal fun resolveTabletTheaterModeActive(
    requested: Boolean,
    companionPane: Boolean,
    useTabletopLayout: Boolean,
): Boolean = requested && !companionPane && !useTabletopLayout

internal fun resolveTabletTheaterPlayerSizeDp(
    availableWidthDp: Float,
    screenHeightDp: Int,
    isTheaterMode: Boolean,
    useTabletopLayout: Boolean,
    policyMaxWidthDp: Int,
): Pair<Float, Float> {
    val safeWidth = availableWidthDp.coerceAtLeast(1f)
    if (!isTheaterMode) {
        val width = minOf(safeWidth, policyMaxWidthDp.toFloat())
        return width to (width * 9f / 16f)
    }
    // 影院大舞台：窗口内横向最大化，同时将高度上限控制在屏高 68%，保证下方简介/互动区一屏可见。
    val maxStageHeight = (screenHeightDp * 0.68f).coerceAtLeast(240f)
    val width = minOf(safeWidth, maxStageHeight * 16f / 9f)
    return width to (width * 9f / 16f)
}

internal fun resolvePhoneTheaterModeActive(
    requested: Boolean,
    useTabletLayout: Boolean,
    isFullscreenMode: Boolean,
    isInPipMode: Boolean,
    isPortraitFullscreen: Boolean,
): Boolean = requested &&
    !useTabletLayout &&
    !isFullscreenMode &&
    !isInPipMode &&
    !isPortraitFullscreen

/** 手机竖屏影院模式下的暗场舞台目标高度：在不挤压评论区的前提下放大舞台至屏高 44% 上限。 */
internal fun resolvePhoneTheaterStageHeightDp(
    baseInlineHeightDp: Float,
    screenHeightDp: Int,
    isTheaterMode: Boolean,
): Float {
    val safeBase = baseInlineHeightDp.coerceAtLeast(1f)
    if (!isTheaterMode) return safeBase
    val targetCap = (screenHeightDp * 0.44f).coerceAtLeast(safeBase)
    return minOf(targetCap, safeBase * 1.42f)
}

internal const val PHONE_THEATER_BODY_DIM_ALPHA = 0.42f

data class TabletVideoLayoutPolicy(
    val primaryRatio: Float,
    val playerMaxWidthDp: Int,
    val infoMaxWidthDp: Int,
    val useTabletopLayout: Boolean = false
)

data class TabletCinemaLayoutPolicy(
    val curtainPeekWidthDp: Int,
    val curtainOpenWidthDp: Int,
    val horizontalPaddingDp: Int,
    val playerMaxWidthDp: Int,
    val useTabletopLayout: Boolean = false
)

internal enum class CinemaMetaPanelBlock {
    ACTIONS,
    INTRO,
    COLLECTION,
    PAGES
}

internal enum class TabletSideCurtainState {
    HIDDEN,
    PEEK,
    OPEN
}

internal enum class TabletSecondaryPaneMode {
    EXPANDED,
    COMPACT,
    COLLAPSED
}

internal fun shouldHideTabletSecondaryPane(
    paneMode: TabletSecondaryPaneMode,
    useThreePaneLayout: Boolean,
    useTabletopLayout: Boolean,
): Boolean = paneMode == TabletSecondaryPaneMode.COLLAPSED &&
    !useThreePaneLayout &&
    !useTabletopLayout

internal fun nextTabletSecondaryPaneMode(
    current: TabletSecondaryPaneMode
): TabletSecondaryPaneMode {
    return when (current) {
        TabletSecondaryPaneMode.EXPANDED -> TabletSecondaryPaneMode.COMPACT
        TabletSecondaryPaneMode.COMPACT -> TabletSecondaryPaneMode.COLLAPSED
        TabletSecondaryPaneMode.COLLAPSED -> TabletSecondaryPaneMode.EXPANDED
    }
}

internal fun resolveTabletPrimaryRatio(
    basePrimaryRatio: Float,
    secondaryPaneMode: TabletSecondaryPaneMode
): Float {
    return when (secondaryPaneMode) {
        TabletSecondaryPaneMode.EXPANDED -> basePrimaryRatio
        TabletSecondaryPaneMode.COMPACT -> (basePrimaryRatio + 0.08f).coerceAtMost(0.80f)
        TabletSecondaryPaneMode.COLLAPSED -> (basePrimaryRatio + 0.14f).coerceAtMost(0.86f)
    }
}

internal fun resolveTabletPrimaryRatio(
    basePrimaryRatio: Float,
    secondaryCollapsed: Boolean
): Float {
    return resolveTabletPrimaryRatio(
        basePrimaryRatio = basePrimaryRatio,
        secondaryPaneMode = if (secondaryCollapsed) {
            TabletSecondaryPaneMode.COLLAPSED
        } else {
            TabletSecondaryPaneMode.EXPANDED
        }
    )
}

fun resolveTabletVideoLayoutPolicy(
    widthDp: Int,
    foldPosture: AppFoldPosture = AppFoldPosture.None
): TabletVideoLayoutPolicy {
    val useTabletopLayout = foldPosture == AppFoldPosture.Tabletop
    return when {
        widthDp >= 1600 -> TabletVideoLayoutPolicy(
            primaryRatio = 0.66f,
            playerMaxWidthDp = 1240,
            infoMaxWidthDp = 1160,
            useTabletopLayout = useTabletopLayout
        )
        else -> TabletVideoLayoutPolicy(
            primaryRatio = if (useTabletopLayout) 0.55f else 0.72f,
            playerMaxWidthDp = 1080,
            infoMaxWidthDp = 1000,
            useTabletopLayout = useTabletopLayout
        )
    }
}

internal fun resolveTabletSecondaryDefaultTabIndex(
    tabs: List<TabletSecondaryTab>,
    preferRelated: Boolean,
): Int {
    val preferredTab = if (preferRelated) TabletSecondaryTab.RELATED else TabletSecondaryTab.COMMENTS
    return tabs.indexOf(preferredTab).takeIf { it >= 0 } ?: 0
}

internal fun resolveTabletCommentTabIndex(tabs: List<TabletSecondaryTab>): Int =
    tabs.indexOf(TabletSecondaryTab.COMMENTS)

internal fun resolveTabletCinemaDefaultTab(tab: TabletSecondaryDefaultTab): Int =
    if (tab == TabletSecondaryDefaultTab.RELATED) 1 else 0

/** Always-visible 发弹幕 / toggle next to 评论, matching the phone content tab bar. */
internal fun shouldShowTabletSecondaryDanmakuActions(): Boolean = true

internal fun shouldShowTabletCinemaDanmakuActions(
    curtainState: TabletSideCurtainState
): Boolean = curtainState == TabletSideCurtainState.OPEN

internal fun resolveCinemaPlayerViewportWidthDp(
    availableWidthDp: Int,
    playerMaxWidthDp: Int,
): Int = minOf(
    availableWidthDp.coerceAtLeast(1),
    playerMaxWidthDp.coerceAtLeast(1),
)

fun resolveTabletCinemaLayoutPolicy(
    widthDp: Int,
    commentWidthPreset: TabletCommentPanelWidthPreset = TabletCommentPanelWidthPreset.STANDARD,
    foldPosture: AppFoldPosture = AppFoldPosture.None
): TabletCinemaLayoutPolicy {
    val useTabletopLayout = foldPosture == AppFoldPosture.Tabletop
    val normalizedWidth = widthDp.coerceIn(960, 1800)
    val baseCurtainOpenWidthDp = interpolateByWidth(
        widthDp = normalizedWidth,
        minWidthDp = 960,
        maxWidthDp = 1800,
        minValue = 320,
        maxValue = 480
    )
    val curtainPeekWidthDp = interpolateByWidth(
        widthDp = normalizedWidth,
        minWidthDp = 960,
        maxWidthDp = 1800,
        minValue = 56,
        maxValue = 74
    )
    val horizontalPaddingDp = interpolateByWidth(
        widthDp = normalizedWidth,
        minWidthDp = 960,
        maxWidthDp = 1800,
        minValue = 12,
        maxValue = 24
    )
    val playerMaxWidthDp = interpolateByWidth(
        widthDp = normalizedWidth,
        minWidthDp = 960,
        maxWidthDp = 1800,
        minValue = 980,
        maxValue = 1280
    )
    val minimumPlayerWidthDp = interpolateByWidth(
        widthDp = normalizedWidth,
        minWidthDp = 960,
        maxWidthDp = 1800,
        minValue = 600,
        maxValue = 960
    )
    val targetCurtainOpenWidthDp = when (commentWidthPreset) {
        TabletCommentPanelWidthPreset.COMPACT -> (baseCurtainOpenWidthDp * 0.85f).toInt()
        TabletCommentPanelWidthPreset.STANDARD -> baseCurtainOpenWidthDp
        TabletCommentPanelWidthPreset.WIDE -> (baseCurtainOpenWidthDp * 1.15f).toInt()
        TabletCommentPanelWidthPreset.ULTRA_WIDE -> (baseCurtainOpenWidthDp * 1.30f).toInt()
    }
    val maxCurtainWidthWithPlayerGuard =
        widthDp - horizontalPaddingDp * 2 - minimumPlayerWidthDp - 4
    val curtainOpenWidthDp = if (useTabletopLayout) {
        // Tabletop 模式下侧栏收起，避免铰链遮挡
        0
    } else {
        targetCurtainOpenWidthDp
            .coerceIn(280, 560)
            .coerceAtMost(maxCurtainWidthWithPlayerGuard.coerceAtLeast(280))
    }

    return TabletCinemaLayoutPolicy(
        curtainPeekWidthDp = if (useTabletopLayout) 0 else curtainPeekWidthDp,
        curtainOpenWidthDp = curtainOpenWidthDp,
        horizontalPaddingDp = horizontalPaddingDp,
        playerMaxWidthDp = playerMaxWidthDp,
        useTabletopLayout = useTabletopLayout
    )
}

internal fun resolveCurtainWidthDp(
    state: TabletSideCurtainState,
    policy: TabletCinemaLayoutPolicy
): Int {
    return when (state) {
        TabletSideCurtainState.HIDDEN -> 0
        TabletSideCurtainState.PEEK -> policy.curtainPeekWidthDp
        TabletSideCurtainState.OPEN -> policy.curtainOpenWidthDp
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveInitialCurtainState(widthDp: Int): TabletSideCurtainState {
    // Tablet cinema is only used on expanded widths. Always land with the
    // right pane open so comments are immediately available.
    return TabletSideCurtainState.OPEN
}

internal fun resolveCurtainStateAfterAutoBehavior(
    currentState: TabletSideCurtainState,
    isActivelyPlaying: Boolean
): TabletSideCurtainState {
    return when {
        isActivelyPlaying && currentState == TabletSideCurtainState.OPEN -> {
            TabletSideCurtainState.PEEK
        }
        !isActivelyPlaying && currentState == TabletSideCurtainState.HIDDEN -> {
            TabletSideCurtainState.PEEK
        }
        else -> currentState
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveCinemaSideCurtainSelectedTab(
    currentSelectedTab: Int,
    replyCount: Int,
    isRepliesLoading: Boolean,
    hasRelatedVideos: Boolean
): Int {
    // Comments is the default landing tab. Do not auto-switch to related
    // when replies are empty or still loading — that moved the indicator
    // off 评论 as soon as the page opened.
    return currentSelectedTab
}

internal fun resolveCinemaMetaPanelContainerColor(
    isDarkTheme: Boolean,
    surfaceColor: Color
): Color {
    return if (isDarkTheme) {
        surfaceColor.copy(alpha = 0.92f)
    } else {
        Color.White
    }
}

internal fun resolveCinemaIntroCardContainerColor(
    isDarkTheme: Boolean,
    surfaceContainerLowColor: Color
): Color {
    return if (isDarkTheme) {
        surfaceContainerLowColor.copy(alpha = 0.96f)
    } else {
        Color.White
    }
}

internal fun resolveCinemaMetaPanelBlocks(
    hasCollection: Boolean,
    hasMultiplePages: Boolean
): List<CinemaMetaPanelBlock> {
    return buildList {
        add(CinemaMetaPanelBlock.ACTIONS)
        add(CinemaMetaPanelBlock.INTRO)
        if (hasCollection) {
            add(CinemaMetaPanelBlock.COLLECTION)
        }
        if (hasMultiplePages) {
            add(CinemaMetaPanelBlock.PAGES)
        }
    }
}

private fun interpolateByWidth(
    widthDp: Int,
    minWidthDp: Int,
    maxWidthDp: Int,
    minValue: Int,
    maxValue: Int
): Int {
    if (widthDp <= minWidthDp) return minValue
    if (widthDp >= maxWidthDp) return maxValue
    val progress = (widthDp - minWidthDp).toFloat() / (maxWidthDp - minWidthDp).toFloat()
    return (minValue + (maxValue - minValue) * progress).toInt()
}
