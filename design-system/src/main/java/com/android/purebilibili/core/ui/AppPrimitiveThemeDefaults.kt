package com.android.purebilibili.core.ui

import androidx.compose.material3.LocalContentColor as MaterialLocalContentColor
import androidx.compose.material3.LocalTextStyle as MaterialLocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.LineBreak
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 原生组件槽位可局部提供字阶，未提供时沿用各材质自己的默认字阶。 */
internal val LocalAppTextStyleOverride = androidx.compose.runtime.staticCompositionLocalOf<TextStyle?> { null }

/** Theme-value bridge only; visible components remain isolated in renderer packages. */
@Composable
@ReadOnlyComposable
internal fun currentAppTextStyle(): TextStyle = LocalAppTextStyleOverride.current ?: when (LocalAppUiStyle.current) {
    AppUiStyle.MATERIAL3 -> MaterialLocalTextStyle.current
    AppUiStyle.MIUIX -> MiuixTheme.textStyles.main
}

/**
 * Shared text style with Android font padding turned off, for text that draws an icon glyph:
 * a font glyph and a hand-drawn Canvas glyph then share one vertical centre inside the same slot.
 */
@Composable
@ReadOnlyComposable
fun appIconGlyphTextStyle(): TextStyle = currentAppTextStyle().copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * Shared text style with the given line height style applied on top of the current
 * UI path's base style, for label texts whose line boxes must hug the glyphs
 * (for example single-line dock labels in fixed, equally divided slots).
 */
@Composable
@ReadOnlyComposable
fun appTextStyleWithLineHeightStyle(lineHeightStyle: LineHeightStyle): TextStyle =
    currentAppTextStyle().copy(lineHeightStyle = lineHeightStyle)

/** 长正文沿用当前材质的字体与字阶，仅调整段落换行。 */
@Composable
@ReadOnlyComposable
fun appParagraphTextStyle(): TextStyle =
    currentAppTextStyle().copy(lineBreak = LineBreak.Paragraph)

/** Resolves the content color from the active native component tree. */
@Composable
@ReadOnlyComposable
internal fun currentAppContentColor(): Color = when (LocalAppUiStyle.current) {
    AppUiStyle.MATERIAL3 -> MaterialLocalContentColor.current
    AppUiStyle.MIUIX -> MiuixLocalContentColor.current
}
