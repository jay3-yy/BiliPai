package com.android.purebilibili.feature.video.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.util.FormatUtils

/**
 * UP 半屏预览在深/浅色下的表面色。
 * 浅色：干净 surface + 浅容器；深色：高对比 surfaceContainer，避免半透明糊底。
 */
internal data class UpPreviewSheetSurfaceColors(
    val sheetColor: Color,
    val scrimColor: Color,
    val cardColor: Color,
    val titleColor: Color,
    val supportingColor: Color,
    val followFillColor: Color,
    val followContentColor: Color,
    val followingFillColor: Color,
    val followingContentColor: Color,
    val enterSpaceColor: Color,
    val dividerColor: Color,
    val coverPlaceholderColor: Color,
)

internal fun resolveUpPreviewSheetSurfaceColors(
    colorScheme: ColorScheme,
): UpPreviewSheetSurfaceColors {
    val isDark = colorScheme.surface.luminance() < 0.5f
    return UpPreviewSheetSurfaceColors(
        sheetColor = if (isDark) {
            colorScheme.surfaceContainerHigh
        } else {
            colorScheme.surface
        },
        scrimColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.4f),
        cardColor = if (isDark) {
            colorScheme.surfaceContainer
        } else {
            colorScheme.surfaceContainerLowest
        },
        titleColor = colorScheme.onSurface,
        supportingColor = colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.82f else 0.75f),
        followFillColor = colorScheme.primary,
        followContentColor = colorScheme.onPrimary,
        followingFillColor = if (isDark) {
            colorScheme.surfaceVariant
        } else {
            colorScheme.surfaceContainerHigh
        },
        followingContentColor = colorScheme.onSurfaceVariant,
        enterSpaceColor = colorScheme.primary,
        dividerColor = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.5f else 0.65f),
        coverPlaceholderColor = colorScheme.surfaceVariant,
    )
}

internal fun resolveUpPreviewStatLine(
    followerCount: Int?,
    videoCount: Int?,
    likeCount: Int?,
): String {
    val parts = buildList {
        followerCount?.takeIf { it >= 0 }?.let {
            add("${formatUpPreviewCount(it)}粉丝")
        }
        videoCount?.takeIf { it >= 0 }?.let {
            add("${formatUpPreviewCount(it)}投稿")
        }
        likeCount?.takeIf { it >= 0 }?.let {
            add("${formatUpPreviewCount(it)}获赞")
        }
    }
    return parts.joinToString("  ")
}

internal fun formatUpPreviewCount(count: Int): String = FormatUtils.formatStat(count.toLong())

internal data class UpPreviewVideoItem(
    val bvid: String,
    val title: String,
    val coverUrl: String,
    val playCount: Int,
    val durationText: String,
    val createdAtSeconds: Long,
)

internal fun resolveUpPreviewVideoClickTarget(
    bvid: String,
    cid: Long = 0L,
): Pair<String, Long>? {
    val normalized = bvid.trim()
    if (normalized.isEmpty()) return null
    return normalized to cid.coerceAtLeast(0L)
}

internal fun shouldDismissUpPreviewSheet(
    dragOffsetPx: Float,
    velocityYPxPerSecond: Float,
    dismissThresholdPx: Float,
): Boolean {
    if (dragOffsetPx <= 0f || dismissThresholdPx <= 0f) return false
    return dragOffsetPx >= dismissThresholdPx || velocityYPxPerSecond >= 1_200f
}

/**
 * 与 [UpPreviewSheet] 半屏高度一致。
 * 对齐官方 UP 预览：顶区视频约占 35–38%，半屏约 62–65%（过大的 0.72 会把视频挤扁并上浮过度）。
 */
internal const val UP_PREVIEW_SHEET_HEIGHT_FRACTION = 0.64f

/**
 * 打开 UP 半屏时的可见进度：1=完全展开（视频应收缩），0=收起。
 * 与评论半屏共用 [com.android.purebilibili.feature.video.ui.pager.resolvePortraitCommentVisibilityProgress]
 * 语义：下拉 offset 越大，进度越小。
 */
internal fun resolveUpPreviewSheetVisibilityProgress(
    hostVisible: Boolean,
    hostVisibilityProgress: Float,
    sheetDragOffsetPx: Float,
    sheetHeightPx: Float,
): Float {
    if (!hostVisible && hostVisibilityProgress <= 0.001f) return 0f
    val host = hostVisibilityProgress.coerceIn(0f, 1f)
    if (sheetHeightPx <= 0f) return host
    val dragProgress = (1f - (sheetDragOffsetPx.coerceAtLeast(0f) / sheetHeightPx))
        .coerceIn(0f, 1f)
    return when {
        dragProgress <= 0.001f -> 0f
        dragProgress + 0.001f >= host -> host
        else -> (host * dragProgress).coerceIn(0f, 1f)
    }
}

/**
 * 评论 / UP 预览半屏共用播放器上缩进度：取较大者，高度比例跟随主导半屏。
 */
internal data class PortraitOverlaySheetExpansion(
    val progress: Float,
    val sheetHeightFraction: Float,
)

internal fun resolvePortraitOverlaySheetExpansion(
    commentVisibilityProgress: Float,
    upPreviewVisibilityProgress: Float,
    commentSheetHeightFraction: Float,
    upPreviewSheetHeightFraction: Float = UP_PREVIEW_SHEET_HEIGHT_FRACTION,
): PortraitOverlaySheetExpansion {
    val comment = commentVisibilityProgress.coerceIn(0f, 1f)
    val upPreview = upPreviewVisibilityProgress.coerceIn(0f, 1f)
    val progress = maxOf(comment, upPreview)
    val sheetHeightFraction = when {
        progress <= 0.001f -> commentSheetHeightFraction
        upPreview >= comment -> upPreviewSheetHeightFraction
        else -> commentSheetHeightFraction
    }
    return PortraitOverlaySheetExpansion(
        progress = progress,
        sheetHeightFraction = sheetHeightFraction.coerceIn(0f, 1f),
    )
}
