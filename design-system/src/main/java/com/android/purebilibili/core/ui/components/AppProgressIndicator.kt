package com.android.purebilibili.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3CircularProgressIndicator
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3LinearProgressIndicator
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixCircularProgressIndicator
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixLinearProgressIndicator

@Composable
fun AppCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = Dp.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap? = null,
) {
    when (LocalAppUiStyle.current) {
        AppUiStyle.MATERIAL3 -> AppMaterial3CircularProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        AppUiStyle.MIUIX -> AppMiuixCircularProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
        )
    }
}

@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = Dp.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap? = null,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val gatedModifier = modifier.then(animationGate.modifier)
    if (!animationGate.active) {
        AppCircularProgressIndicator(
            progress = { 0.72f },
            modifier = gatedModifier,
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        return
    }
    when (LocalAppUiStyle.current) {
        AppUiStyle.MATERIAL3 -> AppMaterial3CircularProgressIndicator(
            modifier = gatedModifier,
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        AppUiStyle.MIUIX -> AppMiuixCircularProgressIndicator(
            modifier = gatedModifier,
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
        )
    }
}

@Composable
fun AppLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap? = null,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val gatedModifier = modifier.then(animationGate.modifier)
    if (!animationGate.active) {
        AppLinearProgressIndicator(
            progress = { 0.38f },
            modifier = gatedModifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        return
    }
    when (LocalAppUiStyle.current) {
        AppUiStyle.MATERIAL3 -> AppMaterial3LinearProgressIndicator(
            modifier = gatedModifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        AppUiStyle.MIUIX -> AppMiuixLinearProgressIndicator(
            modifier = gatedModifier,
            color = color,
            trackColor = trackColor,
        )
    }
}

@Composable
fun AppLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap? = null,
) {
    when (LocalAppUiStyle.current) {
        AppUiStyle.MATERIAL3 -> AppMaterial3LinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        AppUiStyle.MIUIX -> AppMiuixLinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    }
}
