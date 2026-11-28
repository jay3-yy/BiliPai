package com.android.purebilibili.feature.home.components

import androidx.compose.material3.Typography
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Dock 标签的行盒策略：行盒贴近字形，等宽槽位的测量不随所选字体漂移。
 * Tight 模式下，行高小于字体度量时行盒仍按行高收紧（沿用既有槽位测量），
 * 行高大于度量时首末行不再保留行高带来的额外留白；字形自然高度超出行高的
 * 部分不会被裁剪，CJK 与自定义字体均不切顶。
 */
internal val DockLabelLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
    mode = LineHeightStyle.Mode.Tight,
)

/**
 * Dock 槽宽由容器宽度均分,不随系统字体缩放放宽;sp 字号却会随 fontScale 放大,
 * 因此标签渲染尺寸在 fontScale 超过 [DOCK_LABEL_FONT_SCALE_CAP] 后封顶
 * (与 TopBar 的 resolveMd3TopTabLayoutVisibleSlots 采用同一 1.15 阈值策略)。
 */
internal const val DOCK_LABEL_FONT_SCALE_CAP = 1.15f

/** 返回 Dock 标签的 TextUnit:fontScale ≤ cap 时等于基准值,超过后按反比抵消放大。 */
private fun dockLabelSp(baseSp: Float, fontScale: Float): TextUnit {
    val safeFontScale = fontScale.coerceAtLeast(0.01f)
    val cappedScale = safeFontScale.coerceAtMost(DOCK_LABEL_FONT_SCALE_CAP)
    return (baseSp * cappedScale / safeFontScale).sp
}

private fun resolveThemeSp(unit: TextUnit?, fallbackSp: Float): Float =
    if (unit != null && unit.isSpecified && unit.isSp) unit.value else fallbackSp

internal fun resolveBottomBarSkinDockLabelFontSize(
    fontScale: Float = 1f,
    typography: Typography? = null,
): TextUnit = dockLabelSp(
    baseSp = resolveThemeSp(typography?.labelMedium?.fontSize, fallbackSp = 12f),
    fontScale = fontScale,
)

internal fun resolveBottomBarSkinDockLabelLineHeight(
    fontScale: Float = 1f,
    typography: Typography? = null,
): TextUnit = dockLabelSp(
    baseSp = resolveThemeSp(typography?.labelMedium?.lineHeight, fallbackSp = 18f),
    fontScale = fontScale,
)

/** 图标+文字：胶囊内当 caption，跟随当前主题 labelSmall（Miuix footnote2 / MD3 labelSmall）。 */
internal fun resolveFloatingDockIconAndTextLabelFontSize(
    fontScale: Float = 1f,
    typography: Typography? = null,
): TextUnit = dockLabelSp(
    baseSp = resolveThemeSp(typography?.labelSmall?.fontSize, fallbackSp = 11f),
    fontScale = fontScale,
)

/**
 * 仅文字标签：传入主题字阶时跟随当前主题 titleSmall（Miuix headline2 / MD3 titleSmall），
 * 未传入时保留 15sp 默认基准。
 */
internal fun resolveFloatingDockTextOnlyLabelFontSize(
    fontScale: Float = 1f,
    typography: Typography? = null,
): TextUnit = dockLabelSp(
    baseSp = resolveThemeSp(typography?.titleSmall?.fontSize, fallbackSp = 15f),
    fontScale = fontScale,
)

/** Keep top-dock line boxes close to their rendered glyphs so icon+label content fits its slot. */
internal fun resolveFloatingDockLabelLineHeight(
    showIcon: Boolean,
    fontScale: Float = 1f,
    typography: Typography? = null,
): TextUnit = dockLabelSp(
    baseSp = if (showIcon) {
        resolveThemeSp(typography?.labelSmall?.lineHeight, fallbackSp = 14f)
    } else {
        resolveThemeSp(typography?.titleSmall?.lineHeight, fallbackSp = 19f)
    },
    fontScale = fontScale,
)

internal fun resolveFloatingDockLabelFontSize(
    showIcon: Boolean,
    showText: Boolean,
    fontScale: Float = 1f,
    typography: Typography? = null,
): TextUnit {
    if (!showText) return 0.sp
    return if (showIcon) {
        resolveFloatingDockIconAndTextLabelFontSize(fontScale = fontScale, typography = typography)
    } else {
        resolveFloatingDockTextOnlyLabelFontSize(fontScale = fontScale, typography = typography)
    }
}
