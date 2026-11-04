package com.android.purebilibili.feature.dynamic

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.FormatUtils
import kotlin.math.roundToInt

internal enum class DynamicVideoCardLayoutMode {
    VERTICAL,
    HORIZONTAL
}

internal fun resolveDynamicFeedMaxWidth(): Dp = 480.dp

internal fun resolveDynamicTimelineMaxWidth(): Dp = 1840.dp

internal fun resolveDynamicTimelineMinColumnWidth(): Dp = 360.dp

internal fun resolveDynamicTimelineHorizontalSpacing(): Dp = 18.dp

internal fun resolveDynamicTimelineVerticalSpacing(): Dp = 6.dp

internal fun shouldUseDynamicManualPrependAnchor(
    feedLayoutMode: SettingsManager.DynamicFeedLayoutMode,
): Boolean = feedLayoutMode == SettingsManager.DynamicFeedLayoutMode.LIST

internal fun resolveDynamicVideoCardLayoutMode(containerWidthDp: Int): DynamicVideoCardLayoutMode {
    return DynamicVideoCardLayoutMode.VERTICAL
}

internal fun resolveDynamicHorizontalUserListHorizontalPadding(): Dp = 10.dp

internal fun resolveDynamicHorizontalUserListSpacing(): Dp = 10.dp

internal fun resolveDynamicTopBarHorizontalPadding(): Dp = 14.dp

internal fun resolveDynamicTopBarTabEndPadding(): Dp = 20.dp

internal fun resolveDynamicTopBarHeightDp(): Int = 50

internal fun resolveDynamicSidebarReturnHeaderHeightDp(): Int = resolveDynamicTopBarHeightDp()

internal fun resolveDynamicSidebarDividerTopOffset(topPadding: Dp): Dp {
    return topPadding + resolveDynamicSidebarReturnHeaderHeightDp().dp
}

// Shared requested geometry, not a glass-only override: tabs and toggle grow together.
internal fun resolveDynamicTopBarTabHeightDp(fontScale: Float): Float =
    com.android.purebilibili.core.ui.AppChromeSizeTokens.TopBarIconVisualSize.value *
        fontScale.coerceAtLeast(1f)

internal fun resolveDynamicTabIndicatorPosition(
    selectedIndex: Int,
    externalPosition: Float?,
    itemCount: Int,
): Float {
    if (itemCount <= 0) return 0f
    val lastIndex = itemCount - 1
    return externalPosition
        ?.takeIf { it.isFinite() }
        ?.coerceIn(0f, lastIndex.toFloat())
        ?: selectedIndex.coerceIn(0, lastIndex).toFloat()
}

internal fun resolveDynamicSidebarWidth(isExpanded: Boolean): Dp {
    return if (isExpanded) 68.dp else 60.dp
}

internal fun shouldShowDynamicUserLiveBadge(isLive: Boolean): Boolean = isLive

internal fun resolveDynamicUserLiveBadgeLabel(): String = "直播"

internal fun resolveDynamicCardOuterPadding(): Dp = 0.dp

internal fun resolveDynamicCardContentPadding(): Dp = 12.dp

internal fun resolveDynamicActionButtonSlotWeight(): Float = 1f

internal fun resolveDynamicActionButtonSpacing(): Dp = 8.dp

internal fun resolveDynamicActionButtonText(
    label: String,
    count: Int,
    slotWidthDp: Int? = null
): String? {
    val countText = if (count > 0) formatDynamicActionCount(count) else null
    return when (label) {
        "评论" -> countText ?: label
        "转发" -> listOfNotNull(label, countText).joinToString(separator = " ")
        "点赞" -> countText ?: label
        else -> countText ?: label
    }
}

private fun formatDynamicActionCount(count: Int): String = FormatUtils.formatStat(count.toLong())

internal const val DYNAMIC_SIDEBAR_MAX_CASCADE_ITEMS = 8
internal const val DYNAMIC_SIDEBAR_AVATAR_PREFETCH_COUNT = 6
internal const val DYNAMIC_SIDEBAR_PREFETCH_IDLE_COUNT = 6
internal const val DYNAMIC_SIDEBAR_PREFETCH_SCROLLING_COUNT = 15
internal const val DYNAMIC_SIDEBAR_FLING_VELOCITY_MULTIPLIER = 0.70f

internal fun shouldAnimateSidebarItemCascade(
    index: Int,
    hasScrolled: Boolean,
    initialEntranceActive: Boolean
): Boolean {
    return initialEntranceActive && !hasScrolled && index < DYNAMIC_SIDEBAR_MAX_CASCADE_ITEMS
}

internal fun resolveDynamicSidebarBeyondBoundsItemCount(
    isScrollInProgress: Boolean
): Int {
    return if (isScrollInProgress) {
        DYNAMIC_SIDEBAR_PREFETCH_SCROLLING_COUNT
    } else {
        DYNAMIC_SIDEBAR_PREFETCH_IDLE_COUNT
    }
}

internal fun resolveDynamicSidebarFlingDampingFactor(): Float {
    return DYNAMIC_SIDEBAR_FLING_VELOCITY_MULTIPLIER
}

internal fun resolveDynamicSidebarUserAvatarUrl(face: String): String {
    val raw = face.trim()
    return when {
        raw.isEmpty() -> ""
        raw.startsWith("file://") || raw.startsWith("content://") -> raw
        raw.startsWith("https://") -> raw
        raw.startsWith("http://") -> raw.replace("http://", "https://")
        raw.startsWith("//") -> "https:$raw"
        else -> "https://$raw"
    }
}

internal fun resolveDynamicSidebarAvatarPrefetchUrls(
    users: List<SidebarUser>,
    startIndex: Int = 0,
    limit: Int = DYNAMIC_SIDEBAR_AVATAR_PREFETCH_COUNT
): List<String> {
    if (startIndex < 0 || startIndex >= users.size || limit <= 0) return emptyList()
    val endIndex = startIndex + minOf(limit, users.size - startIndex)
    return users.subList(startIndex, endIndex).mapNotNull { user ->
        resolveDynamicSidebarUserAvatarUrl(user.face).ifEmpty { null }
    }.distinct()
}

internal fun resolveDynamicUserAvatarDecodeSizePx(density: Float): Int =
    (48f * (density.takeIf { it.isFinite() && it > 0f } ?: 1f))
        .coerceIn(48f, 256f).roundToInt()

/** Headers have no user identity; prefetch only three neighbors on each side of real visible users. */
internal fun resolveDynamicSidebarNeighborAvatarUrls(
    users: List<SidebarUser>,
    visibleUserIds: List<Long>,
): List<String> {
    val visibleIds = visibleUserIds.toSet()
    val first = users.indexOfFirst { it.uid in visibleIds }
    val last = users.indexOfLast { it.uid in visibleIds }
    if (first < 0) return emptyList()
    val before = resolveDynamicSidebarAvatarPrefetchUrls(
        users, (first - 3).coerceAtLeast(0), minOf(first, 3),
    )
    val after = resolveDynamicSidebarAvatarPrefetchUrls(users, last + 1, 3)
    return (after + before.asReversed()).distinct()
}
