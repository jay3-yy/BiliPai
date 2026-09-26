package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Arrangement
import com.android.purebilibili.core.store.HomeFeedCardWidthPreset
import com.android.purebilibili.core.util.AppFoldPosture
import com.android.purebilibili.core.util.AppHingeOrientation
import com.android.purebilibili.core.util.AppWindowAdaptiveInfo
import com.android.purebilibili.core.util.WindowWidthSizeClass
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal fun resolveHomeFeedMaxContentWidth(): Dp = 1280.dp

/**
 * 当前窗口是否按"窄屏"档处理列数记忆：折叠屏外屏、手机竖屏（Compact 宽度）。
 * 宽屏（Medium/Expanded+，折叠屏内屏、平板、外屏横屏）与窄屏互不影响。
 */
internal fun isCompactHomeFeedScreen(widthSizeClass: WindowWidthSizeClass): Boolean =
    widthSizeClass == WindowWidthSizeClass.Compact

/**
 * 按屏幕分档选择生效的固定列数记忆。
 *
 * 双指缩放的记忆此前是全局单一键：在内屏捏合成 3 列后，外屏（宽度约 380–480dp）
 * 也被固定成 3 列，卡片窄到遮挡文字。窄屏走独立记忆且默认 0=自动——自动档在典型
 * 外屏宽度下解析为 2 列；宽屏沿用原有记忆，老用户内屏的捏合记忆无需迁移。
 */
internal fun resolveHomeFeedStoredColumnCount(
    widthSizeClass: WindowWidthSizeClass,
    compactColumnCount: Int,
    defaultColumnCount: Int,
): Int = if (isCompactHomeFeedScreen(widthSizeClass)) compactColumnCount else defaultColumnCount

internal fun resolveHomeFeedGridColumns(
    contentWidthDp: Int,
    displayMode: Int,
    fixedColumnCount: Int,
    cardWidthPreset: HomeFeedCardWidthPreset,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Medium
): Int {
    val isSingleColumnMode = displayMode == 1
    if (!isSingleColumnMode && fixedColumnCount > 0) {
        return fixedColumnCount
    }

    val minColumnWidthDp = if (isSingleColumnMode) {
        280
    } else {
        cardWidthPreset.minCardWidthDp ?: 180
    }
    val maxColumns = if (isSingleColumnMode) {
        2
    } else {
        when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> 4
            WindowWidthSizeClass.Medium -> 6
            WindowWidthSizeClass.Expanded -> 6
            WindowWidthSizeClass.Large -> 7
            WindowWidthSizeClass.ExtraLarge -> 8
        }
    }
    val columns = contentWidthDp / minColumnWidthDp
    val minColumns = if (!isSingleColumnMode && contentWidthDp >= 300) 2 else 1
    return columns.coerceIn(minColumns, maxColumns)
}

/**
 * 根据窗口宽度分档返回封面宽高比。
 * 大屏下从 16:10 向 16:9 靠拢，减少纵向留白。
 */
internal fun resolveHomeFeedCardAspectRatio(
    widthSizeClass: WindowWidthSizeClass
): Float {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact,
        WindowWidthSizeClass.Medium -> 16f / 10f
        WindowWidthSizeClass.Expanded,
        WindowWidthSizeClass.Large,
        WindowWidthSizeClass.ExtraLarge -> 16f / 9f
    }
}

internal data class HomeFeedBookHingeGridSpec(
    val enabled: Boolean,
    val centerGapDp: Dp = 0.dp,
)

internal fun resolveHomeFeedBookHingeGridSpec(
    adaptiveInfo: AppWindowAdaptiveInfo,
    density: Float,
): HomeFeedBookHingeGridSpec {
    val hingeBounds = adaptiveInfo.foldingFeature.hingeBounds
    if (
        adaptiveInfo.posture != AppFoldPosture.Book ||
        !adaptiveInfo.shouldAvoidHinge ||
        adaptiveInfo.foldingFeature.hingeOrientation != AppHingeOrientation.Vertical ||
        hingeBounds == null ||
        density <= 0f
    ) {
        return HomeFeedBookHingeGridSpec(enabled = false)
    }
    // Keep an 8dp safety inset on both sides even when the reported crease itself is 0px wide.
    val physicalGapDp = (hingeBounds.width / density).dp
    return HomeFeedBookHingeGridSpec(
        enabled = true,
        centerGapDp = physicalGapDp.coerceAtLeast(0.dp) + 16.dp,
    )
}

/**
 * Even-column arrangement that reserves one larger gap at the Book hinge.
 *
 * [spacing] exposes the averaged gap to LazyGrid so cell measurement already budgets for the
 * hinge. Placement then moves that budget to the middle instead of shrinking the outer padding.
 */
internal class HomeFeedBookHingeArrangement(
    private val columns: Int,
    private val baseSpacing: Dp,
    private val centerGap: Dp,
) : Arrangement.Horizontal {
    override val spacing: Dp = if (columns > 1) {
        baseSpacing + centerGap / (columns - 1).toFloat()
    } else {
        baseSpacing
    }

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        if (sizes.isEmpty()) return
        val baseSpacingPx = baseSpacing.roundToPx()
        val centerGapPx = centerGap.roundToPx()
        val centerAfterIndex = (columns / 2 - 1).coerceAtLeast(0)
        var position = 0
        sizes.indices.forEach { index ->
            val ltrPosition = position
            outPositions[index] = if (layoutDirection == LayoutDirection.Ltr) {
                ltrPosition
            } else {
                totalSize - ltrPosition - sizes[index]
            }
            position += sizes[index]
            if (index < sizes.lastIndex) {
                position += baseSpacingPx
                if (sizes.size == columns && index == centerAfterIndex) {
                    position += centerGapPx
                }
            }
        }
    }
}

internal fun resolveHomeFeedHorizontalArrangement(
    columns: Int,
    baseSpacing: Dp,
    hingeSpec: HomeFeedBookHingeGridSpec,
): Arrangement.Horizontal = if (hingeSpec.enabled && columns >= 2 && columns % 2 == 0) {
    HomeFeedBookHingeArrangement(
        columns = columns,
        baseSpacing = baseSpacing,
        centerGap = hingeSpec.centerGapDp,
    )
} else {
    Arrangement.spacedBy(baseSpacing)
}
