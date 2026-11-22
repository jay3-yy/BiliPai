package com.android.purebilibili.feature.profile

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/** 页头按展开高度裁切，背景原点跟随头像位置，同时保留壁纸裁切与缩放。 */
@Stable
internal class ProfileBackgroundMotion(
    val scrollOffsetPx: () -> Float,
    val pullOffsetPx: () -> Float,
    val avatarShiftYPx: () -> Float,
    val wallpaperBoundaryPx: (() -> Float)? = null,
)

internal val LocalProfileBackgroundMotion = staticCompositionLocalOf<ProfileBackgroundMotion?> { null }

internal data class ProfileBackgroundFrame(val sourceHeight: Int, val visibleHeight: Int)

internal fun resolveProfileBackgroundFrame(baseHeight: Float, pull: Float, scroll: Float, maxHeight: Int): ProfileBackgroundFrame {
    val source = (baseHeight + pull).coerceIn(0f, maxHeight.toFloat())
    val visible = (source - scroll).coerceIn(0f, maxHeight.toFloat())
    return ProfileBackgroundFrame(source.roundToInt(), visible.roundToInt())
}

@Composable
internal fun Modifier.profileHeaderBackgroundMotion(heroHeight: Dp): Modifier {
    val motion = LocalProfileBackgroundMotion.current ?: return height(heroHeight)
    return clipToBounds().layout { measurable, constraints ->
        val pull = motion.pullOffsetPx()
        val scroll = motion.scrollOffsetPx()
        val frame = resolveProfileBackgroundFrame(heroHeight.toPx(), pull, scroll, constraints.maxHeight)
        // Keep the wallpaper plane intact while clipping the visible header, just as
        // the header clips its gradient. Scroll must not repeatedly recrop the bitmap.
        val child = measurable.measure(constraints.copy(minHeight = frame.sourceHeight, maxHeight = frame.sourceHeight))
        val visibleHeight = motion.wallpaperBoundaryPx?.let { boundary ->
            val headerBottom = boundary() + pull - scroll
            headerBottom.coerceIn(0f, frame.visibleHeight.toFloat()).roundToInt()
        } ?: frame.visibleHeight
        layout(child.width, visibleHeight) {
            child.placeRelative(0, motion.avatarShiftYPx().roundToInt())
        }
    }
}
