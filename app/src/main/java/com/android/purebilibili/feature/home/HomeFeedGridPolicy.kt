package com.android.purebilibili.feature.home

import com.android.purebilibili.core.store.HomeFeedCardWidthPreset
import com.android.purebilibili.core.util.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun resolveHomeFeedMaxContentWidth(): Dp = 1280.dp

/**
 * 当前窗口是否按"窄屏"档处理列数记忆：折叠屏外屏、手机竖屏（Compact 宽度）。
 * 宽屏（Medium/Expanded+）另外按窗口横竖比例拆分，两种方向均不覆盖窄屏。
 */
internal fun isCompactHomeFeedScreen(widthSizeClass: WindowWidthSizeClass): Boolean =
    widthSizeClass == WindowWidthSizeClass.Compact

/**
 * 按屏幕分档选择生效的固定列数记忆。
 *
 * 双指缩放的记忆此前是全局单一键：在内屏捏合成 3 列后，外屏（宽度约 380–480dp）
 * 也被固定成 3 列，卡片窄到遮挡文字。窄屏走独立记忆且默认 0=自动——自动档在典型
 * 外屏宽度下解析为 2 列。调用方先按当前宽屏方向选值，窄屏仍使用自己的独立记忆。
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
        return if (contentWidthDp < 380) {
            fixedColumnCount.coerceIn(1, resolveHomeFeedPinchColumnBounds(widthSizeClass, contentWidthDp).last)
        } else fixedColumnCount
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
