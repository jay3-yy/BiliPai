// 文件路径: core/theme/Theme.kt
package com.android.purebilibili.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- 扩展颜色定义 ---
private val DarkSurfaceVariant = Color(0xFF2A2A2A) // 深色模式下的搜索框背景
private val LightSurfaceVariant = Color(0xFFF1F2F3) // 浅色模式下的搜索框背景

// 深色模式配色
private val DarkColorScheme = darkColorScheme(
    primary = BiliPink,
    onPrimary = White,
    secondary = BiliPinkDim,
    background = DarkBackground,  // Scaffold 背景 (深黑)
    surface = DarkSurface,        // Card 背景 (深灰)
    onSurface = TextPrimaryDark,  // 主要文字 (浅白)
    surfaceVariant = DarkSurfaceVariant, // 搜索框/次级背景
    onSurfaceVariant = TextSecondaryDark // 次要文字
)

// 浅色模式配色
private val LightColorScheme = lightColorScheme(
    primary = BiliPink,
    onPrimary = White,
    secondary = BiliPinkDim,
    background = BiliBackground, // Scaffold 背景 (浅灰)
    surface = White,             // Card 背景 (白)
    onSurface = TextPrimary,     // 主要文字 (黑)
    surfaceVariant = LightSurfaceVariant, // 搜索框背景
    onSurfaceVariant = TextSecondary // 次要文字
)

@Composable
fun PureBiliBiliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}