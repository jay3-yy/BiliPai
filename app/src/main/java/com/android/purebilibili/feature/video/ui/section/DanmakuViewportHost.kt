package com.android.purebilibili.feature.video.ui.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.android.purebilibili.feature.video.danmaku.DanmakuViewport
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuViewport

/**
 * Supplied by the detail layout, not device orientation: a tablet's embedded player shares
 * fullscreen styling even though its controls and measured viewport remain embedded.
 */
internal val LocalUseTabletDanmakuStyle = staticCompositionLocalOf { false }

/** One measured surface for ordinary, authored and interactive danmaku. */
@Composable
internal fun DanmakuViewportHost(
    modifier: Modifier,
    content: @Composable BoxScope.(DanmakuViewport) -> Unit
) {
    val density = LocalDensity.current
    val systemDensity = LocalContext.current.resources.displayMetrics.density
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val viewport = remember(constraints.maxWidth, constraints.maxHeight, systemDensity) {
            resolveDanmakuViewport(
                constraints.maxWidth,
                constraints.maxHeight,
                systemDensity
            )
        }
        if (viewport != null) {
            Box(
                Modifier
                    .size(
                        with(density) { viewport.widthPx.toDp() },
                        with(density) { viewport.heightPx.toDp() }
                    ),
                content = { content(viewport) }
            )
        }
    }
}
