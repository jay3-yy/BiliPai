// 文件路径: core/util/WindowSizeUtils.kt
package com.android.purebilibili.core.util

import android.app.Activity
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.window.layout.WindowMetrics
import kotlin.math.min

/**
 * 🖥️ 窗口宽度尺寸类型
 * 基于 Android WindowManager 1.5 的 V2 断点。
 */
enum class WindowWidthSizeClass {
    /** 手机竖屏 (< 600dp) */
    Compact,
    /** 平板竖屏/手机横屏 (600dp - 840dp) */
    Medium,
    /** 平板横屏/小型桌面窗口 (840dp - 1200dp) */
    Expanded,
    /** 大型平板/桌面窗口 (1200dp - 1600dp) */
    Large,
    /** 超宽桌面窗口 (>= 1600dp) */
    ExtraLarge,
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

internal fun resolveWindowWidthSizeClass(widthDp: Dp): WindowWidthSizeClass {
    return when {
        widthDp < 600.dp -> WindowWidthSizeClass.Compact
        widthDp < 840.dp -> WindowWidthSizeClass.Medium
        widthDp < 1200.dp -> WindowWidthSizeClass.Expanded
        widthDp < 1600.dp -> WindowWidthSizeClass.Large
        else -> WindowWidthSizeClass.ExtraLarge
    }
}

internal fun resolveWindowHeightSizeClass(heightDp: Dp): WindowHeightSizeClass {
    return when {
        heightDp < 480.dp -> WindowHeightSizeClass.Compact
        heightDp < 900.dp -> WindowHeightSizeClass.Medium
        else -> WindowHeightSizeClass.Expanded
    }
}

internal fun resolveWindowWidthSizeClass(
    adaptiveSizeClass: androidx.window.core.layout.WindowSizeClass,
): WindowWidthSizeClass = when {
    adaptiveSizeClass.isWidthAtLeastBreakpoint(
        androidx.window.core.layout.WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND
    ) -> WindowWidthSizeClass.ExtraLarge
    adaptiveSizeClass.isWidthAtLeastBreakpoint(
        androidx.window.core.layout.WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND
    ) -> WindowWidthSizeClass.Large
    adaptiveSizeClass.isWidthAtLeastBreakpoint(
        androidx.window.core.layout.WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    ) -> WindowWidthSizeClass.Expanded
    adaptiveSizeClass.isWidthAtLeastBreakpoint(
        androidx.window.core.layout.WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    ) -> WindowWidthSizeClass.Medium
    else -> WindowWidthSizeClass.Compact
}

internal fun resolveWindowHeightSizeClass(
    adaptiveSizeClass: androidx.window.core.layout.WindowSizeClass,
): WindowHeightSizeClass = when {
    adaptiveSizeClass.isHeightAtLeastBreakpoint(
        androidx.window.core.layout.WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND
    ) -> WindowHeightSizeClass.Expanded
    adaptiveSizeClass.isHeightAtLeastBreakpoint(
        androidx.window.core.layout.WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    ) -> WindowHeightSizeClass.Medium
    else -> WindowHeightSizeClass.Compact
}

internal fun resolveStableDeviceWidthSizeClass(
    smallestScreenWidthDp: Int
): WindowWidthSizeClass {
    return resolveWindowWidthSizeClass(smallestScreenWidthDp.dp)
}

/**
 * 📐 窗口尺寸类信息
 */
data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val widthDp: Dp,
    val heightDp: Dp,
    val deviceWidthSizeClass: WindowWidthSizeClass = widthSizeClass
) {
    /**
     * A foldable cover display can be narrow even though the maximum (inner) display is a
     * tablet-sized window.  Orientation handling must follow the currently visible display.
     */
    val isFoldableCoverScreen: Boolean
        get() = isTabletDevice && min(widthDp.value, heightDp.value) < 600f

    /** 是否为当前窗口意义上的平板宽度布局 */
    val isTablet: Boolean
        get() = widthSizeClass != WindowWidthSizeClass.Compact

    /** 是否为稳定意义上的手机宽度设备 */
    val isCompactDevice: Boolean
        get() = deviceWidthSizeClass == WindowWidthSizeClass.Compact

    /** 是否为稳定意义上的平板宽度设备 */
    val isTabletDevice: Boolean
        get() = !isCompactDevice
    
    /** 是否为大屏设备（平板横屏） */
    val isExpandedScreen: Boolean
        get() = widthSizeClass >= WindowWidthSizeClass.Expanded

    val isExtraLargeScreen: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.ExtraLarge
    
    /** 是否应该使用分栏布局 */
    val shouldUseSplitLayout: Boolean
        get() = isTablet && heightSizeClass != WindowHeightSizeClass.Compact
    
    /** 平板在中等宽度竖屏保留侧栏；紧凑分屏回退底栏，恢复宽度后自动还原。 */
    val shouldUseSideNavigation: Boolean
        get() = isTablet && (isTabletDevice || widthSizeClass >= WindowWidthSizeClass.Expanded)

    val shouldUseExpandedNavigationRail: Boolean
        get() = widthSizeClass >= WindowWidthSizeClass.Large

    val shouldUseThreePaneLayout: Boolean
        get() = isExtraLargeScreen && heightSizeClass != WindowHeightSizeClass.Compact
}

enum class AppFoldPosture {
    None,
    Flat,
    Book,
    Tabletop,
}

enum class AppHingeOrientation {
    None,
    Vertical,
    Horizontal,
}

data class AppFoldingFeatureInfo(
    val posture: AppFoldPosture = AppFoldPosture.None,
    val hingeOrientation: AppHingeOrientation = AppHingeOrientation.None,
    val hingeBounds: IntRect? = null,
    val isSeparating: Boolean = false,
    val isOccluding: Boolean = false,
    val hinges: List<AppHingeFeature> = emptyList(),
) {
    val hasObstructingHinge: Boolean
        get() = isSeparating || isOccluding ||
            hinges.any { hinge -> hinge.isSeparating || hinge.isOccluding }
}

data class AppWindowAdaptiveInfo(
    val windowSizeClass: WindowSizeClass,
    val foldingFeature: AppFoldingFeatureInfo = AppFoldingFeatureInfo(),
    val displayContext: AppDisplayContext = AppDisplayContext(
        currentWindowWidthDp = windowSizeClass.widthDp.value.toInt(),
        currentWindowHeightDp = windowSizeClass.heightDp.value.toInt(),
    ),
    val precisePointerConnected: Boolean = false,
    val hardwareKeyboardConnected: Boolean = false,
) {
    val posture: AppFoldPosture
        get() = foldingFeature.posture

    val shouldAvoidHinge: Boolean
        get() = foldingFeature.hasObstructingHinge ||
            foldingFeature.layoutHinges().any { !it.isFlat }
}

/**
 * 📦 CompositionLocal 提供全局 WindowSizeClass 访问
 */
private val DefaultWindowSizeClass = WindowSizeClass(
    widthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass = WindowHeightSizeClass.Medium,
    widthDp = 360.dp,
    heightDp = 800.dp,
)

val LocalWindowSizeClass = compositionLocalOf { DefaultWindowSizeClass }

val LocalAppWindowAdaptiveInfo = compositionLocalOf {
    AppWindowAdaptiveInfo(windowSizeClass = DefaultWindowSizeClass)
}

/**
 * Returns true only while the current activity is on a foldable's fully opened inner display.
 *
 * A window's width cannot distinguish an unfolded foldable from a tablet (or a large phone), and
 * it also changes while the activity rotates. WindowManager's folding posture is therefore the
 * only input used for foldable-specific orientation behavior.
 */
@Composable
fun rememberIsFlatFoldable(): Boolean {
    return LocalAppWindowAdaptiveInfo.current.posture == AppFoldPosture.Flat
}

@Composable
fun rememberAppWindowAdaptiveInfo(
    windowSizeClass: WindowSizeClass,
    windowPosture: androidx.compose.material3.adaptive.Posture,
): AppWindowAdaptiveInfo {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val foldingFeatureInfo = remember(windowPosture) {
        windowPosture.toAppFoldingFeatureInfo()
    }
    val activity = remember(context) { context.findHostActivity() }
    val displayContext = remember(
        activity,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
        configuration.orientation,
        foldingFeatureInfo,
    ) {
        activity?.resolveAppDisplayContext(
            configuration = configuration,
            hasCurrentFoldingFeature = foldingFeatureInfo.posture != AppFoldPosture.None,
            foldPosture = foldingFeatureInfo.posture,
        ) ?: resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = configuration.screenWidthDp,
                currentWindowHeightDp = configuration.screenHeightDp,
                smallestScreenWidthDp = configuration.smallestScreenWidthDp,
                configurationOrientation = configuration.orientation,
                hasCurrentFoldingFeature = foldingFeatureInfo.posture != AppFoldPosture.None,
                foldPosture = foldingFeatureInfo.posture,
            )
        )
    }
    val precisePointerConnected by produceState(
        initialValue = context.hasPrecisePointer(),
        key1 = context,
    ) {
        val inputManager = context.getSystemService(InputManager::class.java)
            ?: return@produceState
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                value = context.hasPrecisePointer()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                value = context.hasPrecisePointer()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                value = context.hasPrecisePointer()
            }
        }
        inputManager.registerInputDeviceListener(listener, null)
        awaitDispose { inputManager.unregisterInputDeviceListener(listener) }
    }
    val hardwareKeyboardConnected = configuration.keyboard != Configuration.KEYBOARD_NOKEYS
    val adaptiveInfo = remember(
        windowSizeClass,
        foldingFeatureInfo,
        displayContext,
        precisePointerConnected,
        hardwareKeyboardConnected,
    ) {
        AppWindowAdaptiveInfo(
            windowSizeClass = windowSizeClass,
            foldingFeature = foldingFeatureInfo,
            displayContext = displayContext,
            precisePointerConnected = precisePointerConnected,
            hardwareKeyboardConnected = hardwareKeyboardConnected,
        )
    }
    val strategySnapshot = remember(adaptiveInfo) {
        adaptiveInfo.toAdaptiveStrategySnapshot()
    }
    LaunchedEffect(strategySnapshot) {
        Logger.d("AppDisplayContext", formatAppAdaptiveStrategySnapshot(strategySnapshot))
    }
    return adaptiveInfo
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private fun androidx.compose.material3.adaptive.Posture.toAppFoldingFeatureInfo(): AppFoldingFeatureInfo {
    val hinges = hingeList.map { hinge ->
        AppHingeFeature(
            orientation = if (hinge.isVertical) {
                AppHingeOrientation.Vertical
            } else {
                AppHingeOrientation.Horizontal
            },
            bounds = IntRect(
                hinge.bounds.left.toInt(),
                hinge.bounds.top.toInt(),
                hinge.bounds.right.toInt(),
                hinge.bounds.bottom.toInt(),
            ),
            isSeparating = hinge.isSeparating,
            isOccluding = hinge.isOccluding,
            isFlat = hinge.isFlat,
        )
    }
    val primary = selectPrimaryHingeFeature(hinges) ?: return AppFoldingFeatureInfo()
    return AppFoldingFeatureInfo(
        posture = resolveAppFoldPosture(isTabletop = isTabletop, hinges = hinges),
        hingeOrientation = primary.orientation,
        hingeBounds = primary.bounds,
        isSeparating = hinges.any { hinge -> hinge.isSeparating },
        isOccluding = hinges.any { hinge -> hinge.isOccluding },
        hinges = hinges,
    )
}

private fun Context.hasPrecisePointer(): Boolean {
    val inputManager = getSystemService(InputManager::class.java) ?: return false
    return inputManager.inputDeviceIds.any { deviceId ->
        val device = inputManager.getInputDevice(deviceId) ?: return@any false
        val deviceEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 || device.isEnabled
        deviceEnabled && (
            device.supportsSource(InputDevice.SOURCE_MOUSE) ||
                device.supportsSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
                device.supportsSource(InputDevice.SOURCE_TOUCHPAD)
            )
    }
}

/**
 * 📏 计算当前窗口尺寸类型
 */
@Composable
fun calculateWindowSizeClass(
    densityMultiplier: Float = 1f,
    metrics: WindowMetrics,
    maximumMetrics: WindowMetrics? = null,
    adaptiveWindowSizeClass: androidx.window.core.layout.WindowSizeClass,
): WindowSizeClass {
    val configuration = LocalConfiguration.current
    val widthDp = (configuration.screenWidthDp / densityMultiplier).dp
    val heightDp = (configuration.screenHeightDp / densityMultiplier).dp
    val currentDisplayMaximumMetrics = maximumMetrics ?: metrics
    val deviceWidthSizeClass = resolveStableDeviceWidthSizeClass(
        min(currentDisplayMaximumMetrics.widthDp, currentDisplayMaximumMetrics.heightDp).toInt()
    )
    
    val widthSizeClass = resolveWindowWidthSizeClass(adaptiveWindowSizeClass)
    val heightSizeClass = resolveWindowHeightSizeClass(adaptiveWindowSizeClass)
    
    return remember(widthDp, heightDp, widthSizeClass, heightSizeClass, deviceWidthSizeClass) {
        WindowSizeClass(
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass,
            widthDp = widthDp,
            heightDp = heightDp,
            deviceWidthSizeClass = deviceWidthSizeClass
        )
    }
}

/**
 * 📐 单列信息流（列表/会话/搜索结果）在大屏上的内容上限。
 * 各列表页通过 [responsiveContentWidth] 套用，避免平板横屏整行拉伸。
 */
fun resolveSingleColumnFeedMaxWidth(): Dp = 840.dp

/**
 * 📐 响应式值选择器
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
    expanded: T = medium,
    large: T = expanded,
    extraLarge: T = large,
): T {
    val windowSizeClass = LocalWindowSizeClass.current
    return remember(windowSizeClass.widthSizeClass, compact, medium, expanded, large, extraLarge) {
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> compact
            WindowWidthSizeClass.Medium -> medium
            WindowWidthSizeClass.Expanded -> expanded
            WindowWidthSizeClass.Large -> large
            WindowWidthSizeClass.ExtraLarge -> extraLarge
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
        resolveAdaptiveGridColumns(
            widthDp = windowSizeClass.widthDp,
            minColumnWidth = minColumnWidth,
            maxColumns = maxColumns,
        )
    }
}

/** 按最小列宽推导列数，供 [rememberAdaptiveGridColumns] 与矩阵测试共用。 */
fun resolveAdaptiveGridColumns(
    widthDp: Dp,
    minColumnWidth: Dp,
    maxColumns: Int,
): Int {
    val columns = (widthDp / minColumnWidth).toInt()
    return columns.coerceIn(1, maxColumns)
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
    return windowSizeClass.isTabletDevice
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
            WindowWidthSizeClass.Large -> ResponsiveSpacing(
                small = 20.dp,
                medium = 28.dp,
                large = 36.dp
            )
            WindowWidthSizeClass.ExtraLarge -> ResponsiveSpacing(
                small = 24.dp,
                medium = 32.dp,
                large = 40.dp
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
    compactSize: TextUnit,
    mediumScale: Float = 1.1f,
    expandedScale: Float = 1.2f
): TextUnit {
    val windowSizeClass = LocalWindowSizeClass.current
    return remember(windowSizeClass.widthSizeClass, compactSize) {
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> compactSize
            WindowWidthSizeClass.Medium -> compactSize.scaledIfSpecified(mediumScale)
            WindowWidthSizeClass.Expanded -> compactSize.scaledIfSpecified(expandedScale)
            WindowWidthSizeClass.Large -> compactSize.scaledIfSpecified(expandedScale)
            WindowWidthSizeClass.ExtraLarge -> compactSize.scaledIfSpecified(expandedScale)
        }
    }
}

internal fun TextUnit.scaledIfSpecified(scale: Float): TextUnit {
    return if (isSpecified) this * scale else this
}

/**
 * 📐 内容最大宽度限制 Modifier
 * 用于在大屏设备上限制内容宽度并居中显示
 * 
 * @param maxWidth 最大宽度限制
 * @param centerContent 是否居中显示
 */
fun Modifier.responsiveContentWidth(
    maxWidth: Dp = 800.dp,
    centerContent: Boolean = true
): Modifier {
    val alignment = if (centerContent) {
        Alignment.CenterHorizontally
    } else {
        Alignment.Start
    }
    // 四段各自负责一件事，顺序不能调：
    // 1. fillMaxWidth 让本节点占满父容器，居中才有剩余空间可用；
    // 2. wrapContentWidth 在这段空间里按 alignment 摆放被限宽的内容；
    // 3. widthIn 把内容的上限压到 maxWidth；
    // 4. 末尾再 fillMaxWidth 把内容钉死在「min(父宽, maxWidth)」。
    //    少了第 4 段，wrapContentWidth 传给内容的 minWidth 是 0，窄屏上原本铺满
    //    父宽的内容会退化成按内容裁剪——调用方紧跟其后的 background() 也会跟着缩。
    return this
        .fillMaxWidth()
        .wrapContentWidth(alignment)
        .widthIn(max = maxWidth)
        .fillMaxWidth()
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
            WindowWidthSizeClass.Large -> when {
                imageCount == 1 -> 1
                imageCount <= 4 -> 2
                imageCount <= 6 -> 3
                else -> 4
            }
            WindowWidthSizeClass.ExtraLarge -> when {
                imageCount == 1 -> 1
                imageCount <= 4 -> 2
                imageCount <= 6 -> 3
                else -> 5
            }
        }
    }
}
