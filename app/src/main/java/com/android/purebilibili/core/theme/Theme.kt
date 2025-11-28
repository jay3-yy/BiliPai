package com.android.purebilibili.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 深色模式配色
private val DarkColorScheme = darkColorScheme(
    primary = BiliPink,
    onPrimary = White,
    secondary = BiliPinkDim,
    background = DarkBackground,
    surface = DarkSurface
)

// 浅色模式配色
private val LightColorScheme = lightColorScheme(
    primary = BiliPink,
    onPrimary = White,
    secondary = BiliPinkDim,
    background = BiliBackground,
    surface = White,
    surfaceVariant = White,
    onSurfaceVariant = Black
)

@Composable
fun PureBiliBiliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}