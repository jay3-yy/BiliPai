// 文件路径: core/util/WindowSizeUtils.kt
package com.android.purebilibili.core.util

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 🖥️ 窗口宽度尺寸类型
 * 基于 Material 3 响应式设计规范
 */
enum class WindowWidthSizeClass {
    /** 手机竖屏 (< 600dp) */
    Compact,
    /** 平板竖屏/手机横屏 (600dp - 840dp) */
    Medium,
    /** 平板横屏/大屏设备 (> 840dp) */
    Expanded
}

/**
 * 🖥️ 窗口高度尺寸类型
 */
enum class WindowHeightSizeClass {
    /** 紧凑高度 (< 480dp) */
    Compact,
    /** 中等高度 (480dp - 900dp) */
    Medium,
    /** 展开高度 (> 900dp) */
    Expanded
}

/**
 * 📐 窗口尺寸类信息
 */
data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val widthDp: Dp,
    val heightDp: Dp
) {
    /** 是否为平板设备 */
    val isTablet: Boolean
        get() = widthSizeClass != WindowWidthSizeClass.Compact
    
    /** 是否为大屏设备（平板横屏） */
    val isExpandedScreen: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Expanded
    
    /** 是否应该使用分栏布局 */
    val shouldUseSplitLayout: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Expanded
    
    /** 是否应该使用侧边导航栏（仅大屏） */
    val shouldUseSideNavigation: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Expanded
}

/**
 * 📦 CompositionLocal 提供全局 WindowSizeClass 访问
 */
val LocalWindowSizeClass = compositionLocalOf { 
    WindowSizeClass(
        widthSizeClass = WindowWidthSizeClass.Compact,
        heightSizeClass = WindowHeightSizeClass.Medium,
        widthDp = 360.dp,
        heightDp = 800.dp
    )
}

/**
 * 📏 计算当前窗口尺寸类型
 */
@Composable
fun calculateWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    val heightDp = configuration.screenHeightDp.dp
    
    val widthSizeClass = when {
        widthDp < 600.dp -> WindowWidthSizeClass.Compact
        widthDp < 840.dp -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }
    
    val heightSizeClass = when {
        heightDp < 480.dp -> WindowHeightSizeClass.Compact
        heightDp < 900.dp -> WindowHeightSizeClass.Medium
        else -> WindowHeightSizeClass.Expanded
    }
    
    return remember(widthDp, heightDp) {
        WindowSizeClass(
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }
}

/**
 * 🎯 响应式值选择器
 * 根据当前窗口尺寸选择合适的值
 * 
 * @param compact 紧凑模式值（手机）
 * @param medium 中等模式值（平板竖屏），默认使用 compact 值
 * @param expanded 展开模式值（平板横屏），默认使用 medium 值
 */
@Composable
fun <T> rememberResponsiveValue(
    compact: T,
    medium: T = compact,
    expanded: T = medium
): T {
    val windowSizeClass = LocalWindowSizeClass.current
    return remember(windowSizeClass.widthSizeClass, compact, medium, expanded) {
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> compact
            WindowWidthSizeClass.Medium -> medium
            WindowWidthSizeClass.Expanded -> expanded
        }
    }
}

/**
 * 📊 计算自适应网格列数
 * 
 * @param minColumnWidth 最小列宽
 * @param maxColumns 最大列数限制
 */
@Composable
fun rememberAdaptiveGridColumns(
    minColumnWidth: Dp = 160.dp,
    maxColumns: Int = 6
): Int {
    val windowSizeClass = LocalWindowSizeClass.current
    return remember(windowSizeClass.widthDp, minColumnWidth, maxColumns) {
        val columns = (windowSizeClass.widthDp / minColumnWidth).toInt()
        columns.coerceIn(1, maxColumns)
    }
}

/**
 * 📐 计算分栏布局比例
 * 返回主内容区域占屏幕宽度的比例 (0.0 - 1.0)
 */
@Composable
fun rememberSplitLayoutRatio(): Float {
    val windowSizeClass = LocalWindowSizeClass.current
    return remember(windowSizeClass.widthSizeClass, windowSizeClass.widthDp) {
        when {
            !windowSizeClass.shouldUseSplitLayout -> 1f  // 不分栏，全宽
            windowSizeClass.widthDp > 1200.dp -> 0.6f     // 超宽屏，主内容 60%
            else -> 0.65f                                  // 平板横屏，主内容 65%
        }
    }
}

/**
 * 🧭 是否使用侧边导航
 */
@Composable
fun shouldUseSideNavigation(): Boolean {
    val windowSizeClass = LocalWindowSizeClass.current
    return windowSizeClass.shouldUseSideNavigation
}

/**
 * 🖥️ 是否为平板设备
 */
@Composable
fun isTabletDevice(): Boolean {
    val windowSizeClass = LocalWindowSizeClass.current
    return windowSizeClass.isTablet
}

// ═══════════════════════════════════════════════════════════════════════════
// 🖥️ 平板端深度适配工具
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 📏 响应式间距数据类
 */
data class ResponsiveSpacing(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraLarge: Dp = large * 1.5f
)

/**
 * 📏 获取响应式间距
 * 根据屏幕尺寸返回适当的间距值
 */
@Composable
fun rememberResponsiveSpacing(): ResponsiveSpacing {
    val windowSizeClass = LocalWindowSizeClass.current
    return remember(windowSizeClass.widthSizeClass) {
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> ResponsiveSpacing(
                small = 8.dp,
                medium = 12.dp,
                large = 16.dp
            )
            WindowWidthSizeClass.Medium -> ResponsiveSpacing(
                small = 12.dp,
                medium = 16.dp,
                large = 24.dp
            )
            WindowWidthSizeClass.Expanded -> ResponsiveSpacing(
                small = 16.dp,
                medium = 24.dp,
                large = 32.dp
            )
        }
    }
}

/**
 * 🔤 响应式字体大小
 * 
 * @param compactSize 紧凑模式字体大小
 * @param mediumScale 中等模式缩放比例（相对于 compact）
 * @param expandedScale 展开模式缩放比例（相对于 compact）
 */
@Composable
fun rememberResponsiveFontSize(
    compactSize: androidx.compose.ui.unit.TextUnit,
    mediumScale: Float = 1.1f,
    expandedScale: Float = 1.2f
): androidx.compose.ui.unit.TextUnit {
    val windowSizeClass = LocalWindowSizeClass.current
    return remember(windowSizeClass.widthSizeClass, compactSize) {
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> compactSize
            WindowWidthSizeClass.Medium -> compactSize * mediumScale
            WindowWidthSizeClass.Expanded -> compactSize * expandedScale
        }
    }
}

/**
 * 📐 内容最大宽度限制 Modifier
 * 用于在大屏设备上限制内容宽度并居中显示
 * 
 * @param maxWidth 最大宽度限制
 * @param centerContent 是否居中显示
 */
@Composable
fun Modifier.responsiveContentWidth(
    maxWidth: Dp = 800.dp,
    centerContent: Boolean = true
): Modifier {
    val windowSizeClass = LocalWindowSizeClass.current
    return if (windowSizeClass.widthDp > maxWidth) {
        val constrained = this.widthIn(max = maxWidth)
        if (centerContent) {
            constrained.wrapContentWidth(Alignment.CenterHorizontally)
        } else {
            constrained
        }
    } else {
        this.fillMaxWidth()
    }
}

/**
 * 📐 居中内容容器 Modifier
 * 在大屏设备上将内容居中并限制宽度
 */
@Composable
fun Modifier.centeredContent(
    maxWidth: Dp = 600.dp
): Modifier {
    val windowSizeClass = LocalWindowSizeClass.current
    return if (windowSizeClass.widthDp > maxWidth) {
        this.widthIn(max = maxWidth)
    } else {
        this
    }
}

/**
 * 🖥️ 是否为平板横屏模式
 */
@Composable
fun isTabletLandscape(): Boolean {
    val windowSizeClass = LocalWindowSizeClass.current
    return windowSizeClass.isTablet && windowSizeClass.widthDp > windowSizeClass.heightDp
}

/**
 * 🖥️ 是否为平板竖屏模式
 */
@Composable
fun isTabletPortrait(): Boolean {
    val windowSizeClass = LocalWindowSizeClass.current
    return windowSizeClass.isTablet && windowSizeClass.widthDp <= windowSizeClass.heightDp
}

/**
 * 📊 计算图片网格列数
 * 专门用于动态/图片展示的网格布局
 * 
 * @param imageCount 图片数量
 */
@Composable
fun rememberImageGridColumns(imageCount: Int): Int {
    val windowSizeClass = LocalWindowSizeClass.current
    return remember(windowSizeClass.widthSizeClass, imageCount) {
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> when {
                imageCount == 1 -> 1
                imageCount <= 4 -> 2
                else -> 3
            }
            WindowWidthSizeClass.Medium -> when {
                imageCount == 1 -> 1
                imageCount <= 4 -> 2
                else -> 3
            }
            WindowWidthSizeClass.Expanded -> when {
                imageCount == 1 -> 1
                imageCount <= 4 -> 2
                imageCount <= 6 -> 3
                else -> 4
            }
        }
    }
}
