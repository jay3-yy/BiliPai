package com.android.purebilibili.feature.home.components

import kotlin.math.roundToInt

/** Shared alpha handoff for the overlapping glass navigation and compact button. */
internal fun resolveLinkedDockGlassHandoffProgress(mergeProgress: Float): Float {
    val progress = (mergeProgress / 0.34f).coerceIn(0f, 1f)
    return progress * progress * progress
}

/** 搜索图标内置于底栏时，收起/进入搜索后从胶囊右端平滑浮现为独立圆形按钮。 */
internal fun resolveLinkedDockDetachedSearchAlpha(
    mergeProgress: Float,
    searchProgress: Float,
    searchInsideDock: Boolean,
): Float {
    if (!searchInsideDock) return 1f
    val raw = maxOf(mergeProgress, searchProgress)
    val t = ((raw - 0.08f) / 0.28f).coerceIn(0f, 1f)
    val inv = 1f - t
    return 1f - inv * inv * inv
}

enum class LinkedDockPhase { Expanded, Playback, Compact, Search }

/** Search owns the first tap on the compact artwork target. */
internal fun shouldExpandPlaybackFromSearch(
    phase: LinkedDockPhase,
    hasAudio: Boolean,
): Boolean = hasAudio && phase == LinkedDockPhase.Search

/** Accumulate one direction before changing chrome; tiny reversals must not cause flicker. */
internal fun accumulateDockScroll(previous: Float, delta: Float): Float =
    if (previous * delta < 0f) delta else previous + delta

/** Retained playback owns the dock until its exit animation has released the slot. */
internal fun shouldUseScrollCompactNavigation(
    enabled: Boolean,
    hasAudio: Boolean,
    retainingAudio: Boolean,
    searchMergeEnabled: Boolean = false,
): Boolean = enabled && !hasAudio && !retainingAudio && !searchMergeEnabled

/** Positive offset browses down; a full opposite-direction threshold expands again. */
internal fun resolveScrollCompactIntent(accumulatedPx: Float, thresholdPx: Float): Boolean? = when {
    accumulatedPx >= thresholdPx -> true
    accumulatedPx <= -thresholdPx -> false
    else -> null
}

/** 下滑合体由功能开关决定，上滑仍可恢复；搜索输入态由调用方保持。 */
internal fun resolveLinkedDockScrollPhase(
    accumulatedPx: Float,
    thresholdPx: Float,
    hasAudio: Boolean,
    searchEnabled: Boolean,
    mergeOnScrollDownEnabled: Boolean,
): LinkedDockPhase? = when {
    accumulatedPx <= -thresholdPx -> LinkedDockPhase.Expanded
    accumulatedPx >= thresholdPx && mergeOnScrollDownEnabled && (hasAudio || searchEnabled) ->
        resolveLinkedDockRestingPhase(true, hasAudio, searchEnabled)
    else -> null
}

internal fun resolveLinkedDockRestingPhase(
    collapseRequested: Boolean,
    hasAudio: Boolean,
    searchMergeEnabled: Boolean = false,
): LinkedDockPhase = when {
    !collapseRequested -> LinkedDockPhase.Expanded
    hasAudio -> LinkedDockPhase.Playback
    // 搜索合体拥有独立于播放条的紧凑态，不触发键盘或占用输入焦点。
    searchMergeEnabled -> LinkedDockPhase.Compact
    else -> LinkedDockPhase.Expanded
}

fun resolveLinkedDockPhaseOnAudioChange(
    currentPhase: LinkedDockPhase,
    hasAudio: Boolean,
    searchMergeEnabled: Boolean = false,
): LinkedDockPhase = when {
    !hasAudio && currentPhase == LinkedDockPhase.Playback -> LinkedDockPhase.Expanded
    !hasAudio && currentPhase == LinkedDockPhase.Compact && !searchMergeEnabled -> LinkedDockPhase.Expanded
    hasAudio && currentPhase == LinkedDockPhase.Compact -> LinkedDockPhase.Playback
    else -> currentPhase
}

fun resolveLinkedDockInitialPhase(
    currentItem: BottomNavItem,
    collapseRequested: Boolean,
    hasAudio: Boolean,
    savedPhase: LinkedDockPhase? = null,
    searchMergeEnabled: Boolean = false,
): LinkedDockPhase {
    if (savedPhase != null) {
        return resolveLinkedDockPhaseOnAudioChange(savedPhase, hasAudio, searchMergeEnabled)
    }
    return if (currentItem == BottomNavItem.HOME) {
        LinkedDockPhase.Expanded
    } else {
        resolveLinkedDockRestingPhase(collapseRequested, hasAudio, searchMergeEnabled)
    }
}

fun shouldEnableLinkedDockBackHandler(
    phase: LinkedDockPhase,
    isTopLevelDestination: Boolean,
): Boolean = isTopLevelDestination && phase == LinkedDockPhase.Search

fun resolveLinkedDockPhaseOnSearchDismiss(
    hasAudio: Boolean,
    previousPhase: LinkedDockPhase = if (hasAudio) LinkedDockPhase.Playback else LinkedDockPhase.Expanded,
    searchMergeEnabled: Boolean = false,
): LinkedDockPhase = resolveLinkedDockPhaseOnAudioChange(previousPhase, hasAudio, searchMergeEnabled)

/** 离开搜索展开态（收起/退回其它相位）时清空底栏搜索词，避免下次展开残留。 */
internal fun shouldResetLinkedDockSearchQuery(
    phase: LinkedDockPhase,
): Boolean = phase != LinkedDockPhase.Search

internal data class LinkedDockGeometry(
    val searchWidth: Int,
    val audioWidth: Int,
    val audioX: Int,
    val audioY: Int,
    val top: Int,
    val height: Int,
)

internal fun resolveLinkedDockNavigationX(
    maximumWidth: Int,
    navigationWidth: Int,
    button: Int,
    gap: Int,
    searchEnabled: Boolean,
    searchInsideDock: Boolean = false,
): Int {
    val safeMaximumWidth = maximumWidth.coerceAtLeast(0)
    val searchReservation = if (searchEnabled && !searchInsideDock) {
        button.coerceAtLeast(0) + gap.coerceAtLeast(0)
    } else {
        0
    }
    val clusterWidth = (navigationWidth.coerceAtLeast(0) + searchReservation)
        .coerceAtMost(safeMaximumWidth)
    return ((safeMaximumWidth - clusterWidth) / 2).coerceAtLeast(0)
}

internal fun resolveLinkedDockSearchX(
    maximumWidth: Int,
    navigationWidth: Int,
    searchWidth: Int,
    button: Int,
    gap: Int,
    mergeProgress: Float,
    searchProgress: Float,
    searchInsideDock: Boolean = false,
): Int {
    val navX = resolveLinkedDockNavigationX(
        maximumWidth = maximumWidth,
        navigationWidth = navigationWidth,
        button = button,
        gap = gap,
        searchEnabled = true,
        searchInsideDock = searchInsideDock,
    )
    val startX = if (searchInsideDock) {
        (navX + navigationWidth.coerceAtLeast(0) - button.coerceAtLeast(0)).coerceAtLeast(0)
    } else {
        navX + navigationWidth.coerceAtLeast(0) + gap.coerceAtLeast(0)
    }
    val endX = (maximumWidth - searchWidth).coerceAtLeast(0)
    val expansionProgress = maxOf(mergeProgress, searchProgress).coerceIn(0f, 1f)
    return (startX + (endX - startX) * expansionProgress)
        .roundToInt()
}

internal fun resolveLinkedDockGeometry(
    width: Int,
    button: Int,
    barHeight: Int,
    gap: Int,
    hasAudio: Boolean,
    searchEnabled: Boolean,
    mergeProgress: Float,
    searchProgress: Float,
    verticalGap: Int = gap,
    presenceProgress: Float = 1f,
): LinkedDockGeometry {
    val merge = mergeProgress.coerceIn(0f, 1f)
    val search = searchProgress.coerceIn(0f, 1f)
    val presence = presenceProgress.coerceIn(0f, 1f)
    val top = ((if (hasAudio) barHeight + verticalGap else 0) * (1f - merge)).roundToInt()
    val searchWidth = if (!searchEnabled) 0 else (
        button + (width - button * (if (hasAudio) 3 else 2) - gap * (if (hasAudio) 2 else 1)) * search
    ).roundToInt().coerceAtLeast(button).coerceAtMost((width - button).coerceAtLeast(0))
    // Both playback and search retain separate capsule surfaces.
    val playbackGap = gap
    val compactAudioWidth = (width - button - searchWidth -
        playbackGap * (if (searchEnabled) 2 else 1)).coerceAtLeast(0)
    // 展开态小横条使用宿主的可用宽度，收合时才为同一行的按钮留出空间。
    val targetAudioWidth = if (hasAudio) {
        (width + (compactAudioWidth - width) * merge).roundToInt()
    } else {
        0
    }
    // Presence 收放以右缘为锚：出现时胶囊从右缘向左生长，消失时向右收起，
    // 避免旧实现的零宽硬切；右缘始终落在完整胶囊的右边界上。
    val audioWidth = (targetAudioWidth * presence).roundToInt()
    val audioX = ((button + playbackGap) * merge).roundToInt() + targetAudioWidth - audioWidth
    return LinkedDockGeometry(
        searchWidth = searchWidth,
        audioWidth = audioWidth,
        audioX = audioX,
        audioY = 0,
        top = top,
        height = top + barHeight,
    )
}

/** 其他搜索页的键盘不属于首页 dock，返回时不能推动它的落点。 */
internal fun shouldLinkedDockAvoidIme(phase: LinkedDockPhase, isTopLevelDestination: Boolean): Boolean =
    isTopLevelDestination && phase == LinkedDockPhase.Search
