package com.android.purebilibili.feature.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding

internal enum class SettingsBackTarget {
    NONE,
    CACHE_ANIMATION,
    CACHE_DIALOG,
    PATH_DIALOG,
    IMAGE_SAVE_PATH_DIALOG,
    EASTER_EGG_DIALOG,
    DONATE_DIALOG,
    RELEASE_DISCLAIMER_DIALOG,
    UPDATE_RESULT,
    CHANGELOG_RESULT,
    BLOCKED_LIST,
    COMMENT_FRAUD_HISTORY,
}

internal data class SettingsBottomBarScrollState(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

internal data class SettingsBottomBarScrollTracker(
    val previousState: SettingsBottomBarScrollState,
    val accumulatedDeltaPx: Int = 0,
)

internal data class SettingsBottomBarScrollUpdate(
    val tracker: SettingsBottomBarScrollTracker,
    val bottomBarVisible: Boolean?,
)

internal fun reduceSettingsBottomBarScroll(
    tracker: SettingsBottomBarScrollTracker,
    currentState: SettingsBottomBarScrollState,
    topRevealThresholdPx: Int,
    directionThresholdPx: Int,
): SettingsBottomBarScrollUpdate {
    val previousState = tracker.previousState
    if (
        currentState.firstVisibleItemIndex == 0 &&
        currentState.firstVisibleItemScrollOffset <= topRevealThresholdPx
    ) {
        return SettingsBottomBarScrollUpdate(
            tracker = SettingsBottomBarScrollTracker(currentState),
            bottomBarVisible = true,
        )
    }

    val itemDirection = currentState.firstVisibleItemIndex.compareTo(previousState.firstVisibleItemIndex)
    if (itemDirection != 0) {
        return SettingsBottomBarScrollUpdate(
            tracker = SettingsBottomBarScrollTracker(currentState),
            bottomBarVisible = itemDirection < 0,
        )
    }

    val frameDeltaPx = currentState.firstVisibleItemScrollOffset - previousState.firstVisibleItemScrollOffset
    val continuesDirection = tracker.accumulatedDeltaPx == 0 ||
        frameDeltaPx == 0 ||
        (tracker.accumulatedDeltaPx > 0) == (frameDeltaPx > 0)
    val accumulatedDeltaPx = if (continuesDirection) {
        tracker.accumulatedDeltaPx + frameDeltaPx
    } else {
        frameDeltaPx
    }
    val visibility = when {
        accumulatedDeltaPx >= directionThresholdPx -> false
        accumulatedDeltaPx <= -directionThresholdPx -> true
        else -> null
    }
    return SettingsBottomBarScrollUpdate(
        tracker = SettingsBottomBarScrollTracker(
            previousState = currentState,
            accumulatedDeltaPx = if (visibility == null) accumulatedDeltaPx else 0,
        ),
        bottomBarVisible = visibility,
    )
}

internal fun resolveSettingsBackTarget(
    showCacheAnimation: Boolean = false,
    showCacheDialog: Boolean = false,
    showPathDialog: Boolean = false,
    showImageSavePathDialog: Boolean = false,
    showEasterEggDialog: Boolean = false,
    showDonateDialog: Boolean = false,
    showReleaseDisclaimerDialog: Boolean = false,
    showUpdateResult: Boolean = false,
    showChangelogResult: Boolean = false,
    showBlockedList: Boolean = false,
    showCommentFraudHistory: Boolean = false,
): SettingsBackTarget = when {
    showBlockedList -> SettingsBackTarget.BLOCKED_LIST
    showCommentFraudHistory -> SettingsBackTarget.COMMENT_FRAUD_HISTORY
    showChangelogResult -> SettingsBackTarget.CHANGELOG_RESULT
    showUpdateResult -> SettingsBackTarget.UPDATE_RESULT
    showReleaseDisclaimerDialog -> SettingsBackTarget.RELEASE_DISCLAIMER_DIALOG
    showDonateDialog -> SettingsBackTarget.DONATE_DIALOG
    showEasterEggDialog -> SettingsBackTarget.EASTER_EGG_DIALOG
    showImageSavePathDialog -> SettingsBackTarget.IMAGE_SAVE_PATH_DIALOG
    showPathDialog -> SettingsBackTarget.PATH_DIALOG
    showCacheDialog && !showCacheAnimation -> SettingsBackTarget.CACHE_DIALOG
    showCacheAnimation -> SettingsBackTarget.CACHE_ANIMATION
    else -> SettingsBackTarget.NONE
}

internal fun resolveSettingsBottomBarReservedPadding(
    bottomBarVisible: Boolean,
    isBottomBarFloating: Boolean,
    bottomBarLabelMode: Int,
    isTablet: Boolean
): Dp {
    if (!bottomBarVisible) return 0.dp

    val floatingBodyHeight = when (bottomBarLabelMode) {
        0 -> if (isTablet) 76.dp else 70.dp
        2 -> if (isTablet) 56.dp else 54.dp
        else -> if (isTablet) 68.dp else 62.dp
    }
    val dockedBodyHeight = when (bottomBarLabelMode) {
        0 -> 72.dp
        2 -> if (isTablet) 52.dp else 56.dp
        else -> 64.dp
    }
    val floatingInset = if (isBottomBarFloating) {
        if (isTablet) 20.dp else 16.dp
    } else {
        0.dp
    }

    return if (isBottomBarFloating) {
        floatingBodyHeight + floatingInset
    } else {
        dockedBodyHeight
    }
}

internal fun resolveSettingsContentBottomPadding(
    navigationBarsBottom: Dp,
    bottomBarVisible: Boolean,
    isBottomBarFloating: Boolean,
    bottomBarLabelMode: Int,
    isTablet: Boolean,
): Dp {
    return resolveBottomSafeAreaPadding(
        navigationBarsBottom = navigationBarsBottom,
        extraBottomPadding = resolveSettingsBottomBarReservedPadding(
            bottomBarVisible = bottomBarVisible,
            isBottomBarFloating = isBottomBarFloating,
            bottomBarLabelMode = bottomBarLabelMode,
            isTablet = isTablet
        )
    )
}
