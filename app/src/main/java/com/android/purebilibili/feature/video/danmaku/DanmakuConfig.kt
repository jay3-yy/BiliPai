// 文件路径: feature/video/danmaku/DanmakuConfig.kt
package com.android.purebilibili.feature.video.danmaku

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import com.android.purebilibili.danmaku.engine.DanmakuRenderConfig

/**
 * 弹幕配置管理
 * 
 * 管理弹幕的样式、速度、透明度等设置
 * 适配 ByteDance DanmakuRenderEngine
 */
class DanmakuConfig {
    
    // 弹幕开关
    var isEnabled = true
    
    // 透明度 (0.0 - 1.0)
    var opacity = 0.85f
    
    // 字体缩放 (0.5 - 2.0)
    var fontScale = 1.0f

    // 字重（1-9；Android 9+ 映射到 100-900，旧系统降级为 normal/bold）
    var fontWeight = 5
    
    // 滚动速度因子 (数值越大弹幕越慢)
    var speedFactor = 1.0f

    // 明确的滚动时长（秒）
    var scrollDurationSeconds = 7.0f
    
    // 显示区域比例 (0.125 至 1.0，每档增加 0.125)
    var displayAreaRatio = 0.5f

    // 行高倍率
    var lineHeight = 1.6f
    
    // [问题9修复] 描边设置
    var strokeEnabled = true  // 默认开启描边
    var strokeWidth = 1.5f  // 描边宽度（dp），与字号使用同一系统密度

    // 静态弹幕停留时长（秒）
    var staticDurationSeconds = 4.0f

    // 固定速度模式：视口越宽，滚动时长越长，保持像素速度稳定
    var scrollFixedVelocity = false

    // 将顶部/底部弹幕视为滚动弹幕的占位配置
    var staticDanmakuToScroll = false

    // 海量模式：适当增加轨道数量
    var massiveMode = false
    
    // [新增] 合并重复弹幕
    var mergeDuplicates = true
    var duplicateMergeWindowMs = 15_000
    var duplicateMergeCountThreshold = 2

    // [新增] 类型屏蔽（与 B 站 blockxxx 语义对齐，true=显示/不屏蔽）
    var allowScroll = true
    var allowTop = true
    var allowBottom = true
    var allowColorful = true
    var allowSpecial = true
    /** 智能云屏蔽等级 (0=关闭)；低于该权重且非自己发送的弹幕会被过滤 */
    var weightFilterLevel = 0
    var blockedRules: List<String> = emptyList()

    // 服务端遮罩开关；不参与显示区域与轨道预算计算。
    var smartOcclusionEnabled = false
    
    // 顶部边距（像素）
    var topMarginPx = 0
    /** 普通模式为顶部计数条预留空间；海量模式只叠加浮层，不改变轨道。 */
    var hotBarReservedHeightPx = 0f
    
    /** Resolve app settings into the renderer-neutral configuration contract. */
    fun resolveRenderConfig(viewport: DanmakuViewport, isFullscreen: Boolean): DanmakuRenderConfig {
        val viewWidth = viewport.widthPx
        val viewHeight = viewport.heightPx
        val resolvedTextSize = resolveDanmakuTextSizePx(viewport.density, fontScale, isFullscreen)
        val resolvedStrokeWidth = if (strokeEnabled) {
            resolveDanmakuStrokeWidthPx(viewport.density, strokeWidth)
        } else 0f
        val layerLineHeightPx = resolveDanmakuLayerLineHeightPx(
            fontSize = resolvedTextSize,
            lineHeightMultiplier = lineHeight
        )
        val scrollDuration = resolveDanmakuScrollDurationMillis(
            scrollDurationSeconds = scrollDurationSeconds,
            speedFactor = speedFactor,
            scrollFixedVelocity = scrollFixedVelocity,
            viewportWidthPx = viewWidth
        )
        val areaRatio = displayAreaRatio.coerceIn(0.1f, 1f)
        val hotBarSpace = resolveDanmakuHotBarReservedHeightPx(
            hotBarReservedHeightPx, viewHeight, massiveMode
        )
        val topMargin = hotBarSpace
        val visibleHeightPx = if (viewHeight > 0) {
            (viewHeight * areaRatio - topMargin).coerceAtLeast(0f)
        } else {
            0f
        }
        val maxLines = if (viewHeight > 0 && hotBarSpace > 0f && visibleHeightPx <= 0f) 0
        else resolveDanmakuVisibleLineCount(
            visibleHeightPx = visibleHeightPx,
            areaRatioHint = areaRatio,
            fontSize = resolvedTextSize,
            strokeWidth = resolvedStrokeWidth,
            strokeEnabled = strokeEnabled,
            lineHeight = lineHeight,
            massiveMode = massiveMode
        )
        val bottomInset = if (viewHeight > 0) viewHeight * (1f - areaRatio) else 0f
        val pinnedDuration = resolveDanmakuPinnedDurationMillis(staticDurationSeconds)
        topMarginPx = topMargin.toInt()

        return DanmakuRenderConfig(
            alpha = (opacity * 255).toInt(),
            textSizePx = resolvedTextSize,
            typeface = resolveDanmakuTypeface(fontWeight),
            strokeWidthPx = resolvedStrokeWidth,
            strokeColor = android.graphics.Color.BLACK,
            scrollDurationMs = scrollDuration,
            lineHeightPx = layerLineHeightPx,
            lineMarginPx = 0f,
            lineCount = maxLines,
            topMarginPx = topMargin,
            bottomMarginPx = bottomInset,
            pinnedDurationMs = pinnedDuration
        )
    }

    companion object {
        /**
         * 获取状态栏高度（像素）
         */
        fun getStatusBarHeight(context: Context): Int {
            val resourceId = context.resources.getIdentifier(
                "status_bar_height", "dimen", "android"
            )
            return if (resourceId > 0) {
                context.resources.getDimensionPixelSize(resourceId)
            } else {
                (24 * context.resources.displayMetrics.density).toInt()
            }
        }
    }
}

internal fun resolveDanmakuTypeface(fontWeight: Int): Typeface {
    val normalizedWeight = fontWeight.coerceIn(1, 9) * 100
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.DEFAULT, normalizedWeight, false)
    } else {
        val familyName = when {
            normalizedWeight <= 200 -> "sans-serif-thin"
            normalizedWeight <= 300 -> "sans-serif-light"
            normalizedWeight <= 400 -> "sans-serif"
            normalizedWeight <= 600 -> "sans-serif-medium"
            normalizedWeight <= 700 -> "sans-serif"
            else -> "sans-serif-black"
        }
        val style = if (normalizedWeight == 700) Typeface.BOLD else Typeface.NORMAL
        Typeface.create(familyName, style)
    }
}

/** Logical-pixel baselines matching PiliPlus; neither depends on the picture box or app UI scale. */
private const val DANMAKU_BASE_TEXT_SIZE_DP = 15f
private const val DANMAKU_FULLSCREEN_TEXT_SIZE_DP = 18f

internal fun resolveDanmakuTextSizePx(
    density: Float,
    fontScale: Float,
    isFullscreen: Boolean
): Float {
    val baseSizeDp = if (isFullscreen) DANMAKU_FULLSCREEN_TEXT_SIZE_DP else DANMAKU_BASE_TEXT_SIZE_DP
    return baseSizeDp * density * fontScale.coerceIn(0.5f, 2.5f)
}

internal fun resolveDanmakuStrokeWidthPx(density: Float, strokeWidth: Float): Float =
    strokeWidth.coerceAtLeast(0f) * density

internal fun resolveDanmakuScrollDurationMillis(
    scrollDurationSeconds: Float,
    speedFactor: Float,
    scrollFixedVelocity: Boolean,
    viewportWidthPx: Int
): Long {
    val baseDurationMillis = (scrollDurationSeconds.coerceIn(1f, 50f) * 1000f).toLong()
    val scaledBySpeed = baseDurationMillis * speedFactor.coerceIn(0.5f, 2.0f)
    val viewportFactor = if (scrollFixedVelocity && viewportWidthPx > 0) {
        (viewportWidthPx / 1080f).coerceIn(0.75f, 2.5f)
    } else {
        1.0f
    }
    return (scaledBySpeed * viewportFactor).toLong().coerceAtLeast(1L)
}

internal fun resolveDanmakuLayerLineHeightPx(
    fontSize: Float,
    lineHeightMultiplier: Float
): Float {
    return fontSize * lineHeightMultiplier.coerceIn(1f, 3f)
}

internal fun resolveDanmakuPinnedDurationMillis(staticDurationSeconds: Float): Long {
    return (staticDurationSeconds.coerceIn(1f, 50f) * 1000f).toLong()
}

internal fun resolveDanmakuVisibleLineCount(
    visibleHeightPx: Float,
    areaRatioHint: Float,
    fontSize: Float,
    strokeWidth: Float,
    strokeEnabled: Boolean,
    lineHeight: Float,
    massiveMode: Boolean
): Int {
    if (visibleHeightPx <= 0f) {
        return resolveDanmakuFallbackMaxLines(areaRatioHint)
    }

    val lineHeightMultiplier = lineHeight.coerceIn(1f, 3f)
    val estimatedLineHeight =
        (fontSize + (if (strokeEnabled) strokeWidth else 0f) + 12f) * lineHeightMultiplier
    val estimatedLines = if (estimatedLineHeight > 0f) {
        (visibleHeightPx / estimatedLineHeight).toInt()
    } else {
        0
    }
    val minimumLines = resolveDanmakuMinimumVisibleLines(areaRatioHint)
    val resolvedLines = estimatedLines.coerceAtLeast(minimumLines)
    val requestedLines = if (massiveMode) {
        (resolvedLines.toLong() * 2L).coerceAtMost(40L).toInt()
    } else {
        resolvedLines
    }

    // The engine stacks rows on the line height alone; there is no extra interline margin.
    val engineLineHeight = resolveDanmakuLayerLineHeightPx(
        fontSize = fontSize,
        lineHeightMultiplier = lineHeightMultiplier
    )
    val maxLinesByBudget = if (visibleHeightPx >= engineLineHeight && engineLineHeight > 0f) {
        ((visibleHeightPx - engineLineHeight) / engineLineHeight).toInt() + 1
    } else {
        0
    }
    val budgetedLines = requestedLines.coerceAtMost(maxLinesByBudget)

    return budgetedLines.also {
        android.util.Log.i(
            "DanmakuConfig",
            "DisplayArea: visibleHeight=$visibleHeightPx, estimatedLineHeight=$estimatedLineHeight, " +
                "lineHeight=$engineLineHeight, " +
                "ratio=$areaRatioHint -> estimated=$estimatedLines, max=$maxLinesByBudget, visible=$it"
        )
    }
}

internal fun resolveDanmakuHotBarReservedHeightPx(
    requestedHeightPx: Float,
    viewportHeightPx: Int,
    massiveMode: Boolean,
): Float = if (massiveMode || !requestedHeightPx.isFinite()) 0f
else requestedHeightPx.coerceIn(0f, viewportHeightPx.coerceAtLeast(0).toFloat())

internal fun resolveDanmakuMinimumVisibleLines(displayAreaRatio: Float): Int {
    return when {
        displayAreaRatio <= 0.25f -> 2
        displayAreaRatio <= 0.5f -> 3
        displayAreaRatio <= 0.75f -> 5
        else -> 6
    }
}

internal fun resolveDanmakuFallbackMaxLines(displayAreaRatio: Float): Int {
    return when {
        displayAreaRatio <= 0.25f -> 4
        displayAreaRatio <= 0.5f -> 8
        displayAreaRatio <= 0.75f -> 12
        else -> 16
    }
}
