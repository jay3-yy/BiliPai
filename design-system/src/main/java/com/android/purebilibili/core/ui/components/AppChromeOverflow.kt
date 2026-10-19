package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Keep input and semantics in the control's visible layout bounds, without a hidden hit halo. */
@Composable
fun AppExactTouchTargets(content: @Composable () -> Unit) {
    val viewConfiguration = LocalViewConfiguration.current
    if (viewConfiguration.minimumTouchTargetSize == DpSize.Zero &&
        LocalMinimumInteractiveComponentSize.current == 0.dp
    ) {
        content()
        return
    }
    val boundedViewConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize: DpSize = DpSize.Zero
        }
    }
    CompositionLocalProvider(
        LocalViewConfiguration provides boundedViewConfiguration,
        LocalMinimumInteractiveComponentSize provides 0.dp,
        content = content,
    )
}

private class ChromeOverflowEntry(val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? = null
    var offset by mutableStateOf<Offset?>(null)
    var height by mutableIntStateOf(0)
}

private class ChromeOverflowState {
    val entries = mutableStateListOf<ChromeOverflowEntry>()
    var coordinates: LayoutCoordinates? = null

    fun updatePosition(entry: ChromeOverflowEntry) {
        val host = coordinates
        val source = entry.coordinates
        if (host != null && host.isAttached && source != null && source.isAttached) {
            // LayoutCoordinates keeps its identity while its position changes. Snapshot the
            // translation so changing action widths invalidates the host's cached draw pass.
            entry.offset = host.localPositionOf(source)
            entry.height = source.size.height
        } else {
            entry.offset = null
            entry.height = 0
        }
    }
}

private val LocalChromeOverflowState = staticCompositionLocalOf<ChromeOverflowState?> { null }

/**
 * Native app bars crop their slots to their fixed height. Replay just their control display lists
 * at the same coordinates in an unclipped sibling draw pass. Layout, input and semantics remain
 * in the native slots; titles and collapsing containers still use the native renderer.
 * This is a live display list, not a bitmap or a second composition of the controls.
 */
@Composable
internal fun AppChromeOverflowHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { ChromeOverflowState() }
    Box(
        modifier = modifier
            .onGloballyPositioned {
                state.coordinates = it
                state.entries.forEach { entry -> state.updatePosition(entry) }
            }
            .drawWithContent {
                drawContent()
                state.entries.forEach { entry ->
                    val offset = entry.offset
                    if (offset != null && entry.height > 0) {
                        // A fully collapsed/offscreen native slot must not leave a floating glyph.
                        val centerY = offset.y + entry.height / 2f
                        if (centerY >= 0f && centerY <= size.height) {
                            translate(offset.x, offset.y) { drawLayer(entry.layer) }
                        }
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalChromeOverflowState provides state, content = content)
    }
}

@Composable
internal fun AppChromeOverflowSlot(content: @Composable () -> Unit) {
    val state = LocalChromeOverflowState.current
    if (state == null) {
        content()
        return
    }
    val layer = rememberGraphicsLayer()
    val entry = remember(layer) { ChromeOverflowEntry(layer) }
    DisposableEffect(state, entry) {
        state.entries.add(entry)
        onDispose { state.entries.remove(entry) }
    }
    Box(
        modifier = Modifier
            .onGloballyPositioned {
                entry.coordinates = it
                state.updatePosition(entry)
            }
            .drawWithContent {
                // Do not draw the layer here: the native bar would crop it and duplicate the replay.
                layer.record { this@drawWithContent.drawContent() }
            },
    ) {
        content()
    }
}
