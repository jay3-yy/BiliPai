package com.android.purebilibili.feature.home.components

import kotlin.math.roundToInt

/** Shared alpha handoff for the overlapping glass navigation and compact button. */
internal fun resolveLinkedDockGlassHandoffProgress(mergeProgress: Float): Float {
    val progress = (mergeProgress / 0.34f).coerceIn(0f, 1f)
    return progress * progress * progress
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
): Int {
    val safeMaximumWidth = maximumWidth.coerceAtLeast(0)
    val searchReservation = if (searchEnabled) button.coerceAtLeast(0) + gap.coerceAtLeast(0) else 0
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
): Int {
    val startX = resolveLinkedDockNavigationX(
        maximumWidth = maximumWidth,
        navigationWidth = navigationWidth,
        button = button,
        gap = gap,
        searchEnabled = true,
    ) + navigationWidth.coerceAtLeast(0) + gap.coerceAtLeast(0)
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
    /** 展开态小横条的宽度；默认铺满容器，dock 传入底栏整簇宽度以对齐长度。 */
    expandedAudioWidth: Int = width,
    /** 展开态小横条的左缘；dock 传入底栏整簇的起点 X 以对齐两行胶囊。 */
    expandedAudioX: Int = 0,
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
    val expandedAudio = expandedAudioWidth.coerceIn(0, width)
    val targetAudioWidth = if (hasAudio) {
        (expandedAudio + (compactAudioWidth - expandedAudio) * merge).roundToInt()
    } else {
        0
    }
    // Presence 收放以右缘为锚：出现时胶囊从右缘向左生长，消失时向右收起，
    // 避免旧实现的零宽硬切；右缘始终落在完整胶囊的右边界上。
    val audioWidth = (targetAudioWidth * presence).roundToInt()
    val audioX = (expandedAudioX +
        ((button + playbackGap) - expandedAudioX) * merge).roundToInt() +
        targetAudioWidth - audioWidth
    return LinkedDockGeometry(
        searchWidth = searchWidth,
        audioWidth = audioWidth,
        audioX = audioX,
        audioY = 0,
        top = top,
        height = top + barHeight,
    )
}
