package com.android.purebilibili.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.resolveAndroidNativeChromeTokens
import com.android.purebilibili.core.theme.resolveCornerRadiusScale
import top.yukonga.miuix.kmp.squircle.addSquircleRect

/** Uses the same upstream path geometry as native Miuix controls. */
internal data class MiuixContainerShape(val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply {
            addSquircleRect(size.width, size.height, with(density) { radius.toPx() })
        })
}

/**
 * 两值主题的形状 tokens。使用 [AppShapes.container] 而非手写 `RoundedCornerShape(N.dp)`。
 * 基础值按主题风格缩放（MIUIX 更大、MATERIAL3 更紧凑）；Pill 级直接取 chrome tokens，
 * 使各风格保留原生胶囊曲率。
 */
object AppShapes {

    /** 窗口转场使用像素圆角；原生连续曲率轮廓也保留同一名义半径。 */
    fun transitionCornerRadiusPx(
        shape: Shape,
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Float = when (shape) {
        is MiuixContainerShape -> with(density) { shape.radius.toPx() }
        else -> when (val outline = shape.createOutline(size, layoutDirection, density)) {
            is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
            else -> 0f
        }
    }

    /** 导航与分类选中态共用胶囊轮廓，随实际槽位高度适配字体与窗口尺寸。 */
    val SelectionIndicator: Shape = RoundedCornerShape(percent = 50)

    fun resolveContainerCornerDp(
        level: ContainerLevel,
        uiStyle: AppUiStyle,
        liquidGlassEnabled: Boolean = true,
    ): Dp {
        if (uiStyle == AppUiStyle.MIUIX) {
            when (level) {
                ContainerLevel.Card -> return 16.dp
                ContainerLevel.MediaCover -> return 12.dp
                ContainerLevel.ProminentCard -> return 20.dp
                ContainerLevel.Chip -> return 10.dp
                ContainerLevel.Dialog -> return 32.dp
                else -> Unit
            }
        }
        if (level == ContainerLevel.Pill) {
            return resolveAndroidNativeChromeTokens(uiStyle).pillCornerRadiusDp.dp
        }
        val scale = resolveCornerRadiusScale(uiStyle)
        return (AppShapeTokens.baseCornerDp(level) * scale).dp
    }

    fun resolveContainerShape(
        level: ContainerLevel,
        uiStyle: AppUiStyle,
        liquidGlassEnabled: Boolean = true,
    ): Shape {
        val dp = resolveContainerCornerDp(level, uiStyle, liquidGlassEnabled)
        if (uiStyle == AppUiStyle.MIUIX && !liquidGlassEnabled && level != ContainerLevel.Sheet) {
            return MiuixContainerShape(dp)
        }
        return if (level == ContainerLevel.Sheet) {
            topRounded(dp)
        } else {
            RoundedCornerShape(dp)
        }
    }

    /** Fill, clipping and border must share the same silhouette. */
    fun resolveBorderedContainerShape(
        level: ContainerLevel,
        uiStyle: AppUiStyle,
        liquidGlassEnabled: Boolean = true,
    ): Shape = resolveContainerShape(level, uiStyle, liquidGlassEnabled)

    /** Top corners only (sheet / stacked cover plate). Bottom stays square. */
    fun topRounded(radius: Dp): Shape =
        RoundedCornerShape(topStart = radius, topEnd = radius)

    /** Bottom corners only (stacked info plate under cover). Top stays square. */
    fun bottomRounded(radius: Dp): Shape =
        RoundedCornerShape(bottomStart = radius, bottomEnd = radius)

    /** Leading vertical edge rounded (e.g. mini-player stashed on the right). */
    fun startRounded(radius: Dp): Shape =
        RoundedCornerShape(topStart = radius, bottomStart = radius)

    /** Trailing vertical edge rounded (e.g. mini-player stashed on the left). */
    fun endRounded(radius: Dp): Shape =
        RoundedCornerShape(topEnd = radius, bottomEnd = radius)

    /** Single-corner badge (duration chip on cover). */
    fun topStartRounded(radius: Dp): Shape =
        RoundedCornerShape(topStart = radius)

    /** Diagonal corners (resize grip on mini-player). */
    fun diagonalTopStartBottomEnd(topStart: Dp, bottomEnd: Dp): Shape =
        RoundedCornerShape(topStart = topStart, bottomEnd = bottomEnd)

    /**
     * Chat / message bubble: large outer corners, small tail corner on the
     * near side of the speaker.
     */
    fun messageBubble(
        isOutgoing: Boolean,
        large: Dp,
        small: Dp,
    ): Shape = if (isOutgoing) {
        RoundedCornerShape(
            topStart = large,
            topEnd = large,
            bottomStart = large,
            bottomEnd = small,
        )
    } else {
        RoundedCornerShape(
            topStart = large,
            topEnd = large,
            bottomStart = small,
            bottomEnd = large,
        )
    }

    @Composable
    fun container(level: ContainerLevel): Shape = resolveContainerShape(
        level = level,
        uiStyle = LocalAppUiStyle.current,
        liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled,
    )

    @Composable
    fun borderedContainer(level: ContainerLevel): Shape = resolveBorderedContainerShape(
        level = level,
        uiStyle = LocalAppUiStyle.current,
        liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled,
    )

    @Composable
    fun containerCornerDp(level: ContainerLevel): Dp = resolveContainerCornerDp(
        level = level,
        uiStyle = LocalAppUiStyle.current,
        liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled,
    )

    /** Uses the 12dp media role in Miuix and preserves each legacy shape in MD3. */
    @Composable
    fun mediaCover(
        legacyLevel: ContainerLevel = ContainerLevel.Card,
    ): Shape = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        container(ContainerLevel.MediaCover)
    } else {
        container(legacyLevel)
    }

    @Composable
    fun borderedMediaCover(
        legacyLevel: ContainerLevel = ContainerLevel.Card,
    ): Shape = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        borderedContainer(ContainerLevel.MediaCover)
    } else {
        borderedContainer(legacyLevel)
    }

    @Composable
    fun mediaCoverCornerDp(
        legacyLevel: ContainerLevel = ContainerLevel.Card,
    ): Dp = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        containerCornerDp(ContainerLevel.MediaCover)
    } else {
        containerCornerDp(legacyLevel)
    }

    /** Scale a semantic radius (e.g. long-press hint size multiplier). */
    @Composable
    fun scaledContainer(level: ContainerLevel, scale: Float): Shape {
        val base = containerCornerDp(level)
        return RoundedCornerShape(base * scale.coerceAtLeast(0.1f))
    }

    @Composable
    fun messageBubble(isOutgoing: Boolean): Shape = messageBubble(
        isOutgoing = isOutgoing,
        large = containerCornerDp(ContainerLevel.Card),
        small = containerCornerDp(ContainerLevel.Tag),
    )
}
