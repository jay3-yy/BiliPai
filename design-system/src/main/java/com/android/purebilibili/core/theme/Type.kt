package com.android.purebilibili.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 历史 iOS 风格定制字阶（已废弃）。
 * 
 * 请统一使用：
 * - Material 3 标准规范：[Md3Typography]
 * - Miuix 紧凑规范：[BiliMiuixTypography]
 * - 业务层直接消费：MaterialTheme.typography（根据当前主题自动适配）
 */
@Deprecated(
    message = "BiliTypography 为历史 iOS 规格字阶，已废弃。请在 Material 3 模式下使用 Md3Typography，在 Miuix 模式下使用 BiliMiuixTypography，或统一通过 MaterialTheme.typography 消费自适应字阶。",
    replaceWith = ReplaceWith("Md3Typography")
)
val BiliTypography = Typography(
    // === Headline 大标题 ===
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp // SF Pro Display
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp
    ),

    // === Title 标题 ===
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.38.sp
    ),
    titleMedium = TextStyle( // Used for "Components" headers often
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, // Classical iOS body/headline size
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp // Tight tracking for 17pt
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    ),

    // === Body 正文 ===
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp, // Standard iOS Body
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp, // Subhead / Secondary body
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp, // Footnote
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp
    ),

    // === Label 标签/按钮 ===
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp, // Caption 1
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp, // Caption 2
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.06.sp
    )
)

/** Material 3's unmodified typography scale. */
val Md3Typography = Typography()

/**
 * Miuix-owned compact typography. Its density intentionally matches the MD3 baseline, but it is
 * a separate theme value so Miuix pages never depend on the active Material typography object.
 */
val CompactMiuixTypography = Typography()

/**
 * Miuix 字号映射到 Material 文本槽位；显式行高与字距支持页面的相对尺寸运算。
 * 原生 Miuix 组件仍使用自己的默认样式，不经过此桥接。
 */
val BiliMiuixTypography = top.yukonga.miuix.kmp.theme.defaultTextStyles().let { native ->
    Typography(
        displayLarge = native.title1.copy(lineHeight = 38.sp, letterSpacing = 0.sp),
        displayMedium = native.title2.copy(lineHeight = 30.sp, letterSpacing = 0.sp),
        displaySmall = native.title3.copy(lineHeight = 26.sp, letterSpacing = 0.sp),
        headlineLarge = native.title2.copy(lineHeight = 30.sp, letterSpacing = 0.sp),
        headlineMedium = native.title3.copy(lineHeight = 26.sp, letterSpacing = 0.sp),
        headlineSmall = native.title4.copy(lineHeight = 24.sp, letterSpacing = 0.sp),
        titleLarge = native.title4.copy(lineHeight = 24.sp, letterSpacing = 0.sp),
        titleMedium = native.headline1.copy(lineHeight = 22.sp, letterSpacing = 0.sp),
        titleSmall = native.headline2.copy(lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyLarge = native.main.copy(lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = native.body1.copy(lineHeight = 21.sp, letterSpacing = 0.sp),
        bodySmall = native.body2.copy(lineHeight = 19.sp, letterSpacing = 0.sp),
        labelLarge = native.button.copy(lineHeight = 22.sp, letterSpacing = 0.sp),
        labelMedium = native.footnote1.copy(lineHeight = 18.sp, letterSpacing = 0.sp),
        labelSmall = native.footnote2.copy(lineHeight = 14.sp, letterSpacing = 0.sp),
    )
}
