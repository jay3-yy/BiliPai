package com.android.purebilibili.core.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.CompletableDeferred
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** A source and its recording modifier; attach [modifier] to exactly one content container. */
@Stable
class ChromeBackdropSource internal constructor(
    val backdrop: LayerBackdrop,
    val modifier: Modifier,
    private val recorded: State<Boolean>,
) {
    val isReady: Boolean get() = recorded.value
}

/**
 * Miuix's source draws its children to the screen, then invokes onDraw to record them again.
 * Record the children once and let both destinations replay the same display list instead.
 * This matters for nested home/navigation sources and the dock's hidden glass/label capture.
 *
 * This is not a bitmap snapshot or a frame cache: child invalidations still record fresh
 * content. Keep the replay layer separate from the backdrop layer to avoid recording itself.
 * No RenderEffect, alpha or clipping is applied to the replay layer, so it does not request
 * another offscreen raster pass. Miuix still owns coordinates and backdrop lifecycle.
 * Set [drawContentToScreen] to false for hidden captures: record without an alpha-zero layer
 * that can cause the renderer to skip drawing the source content.
 */
@Composable
fun rememberChromeBackdropSource(
    drawContentToScreen: Boolean = true,
    recordingBackground: Color = Color.Unspecified,
): ChromeBackdropSource {
    val contentLayer = rememberGraphicsLayer()
    // Theme frames update the recording in draw, without replacing the source passed
    // to consumers or rebuilding its modifier chain. Child-layer invalidations still
    // record fresh content; this is not a frozen backdrop cache.
    val recordingBackgroundState = rememberUpdatedState(recordingBackground)
    val recorded = remember(contentLayer) { mutableStateOf(false) }
    val firstRecording = remember(contentLayer) { CompletableDeferred<Unit>() }
    LaunchedEffect(firstRecording) {
        firstRecording.await()
        recorded.value = true
    }
    val backdrop = rememberLayerBackdrop(onDraw = {
        drawLayer(contentLayer)
        // Publish once, after the source has actually recorded; never guess using a delay.
        firstRecording.complete(Unit)
    })
    return remember(backdrop, contentLayer, drawContentToScreen) {
        ChromeBackdropSource(
            backdrop = backdrop,
            recorded = recorded,
            modifier = Modifier
                .layerBackdrop(backdrop)
                .drawWithContent {
                    contentLayer.record {
                        // An explicit animated background invalidates this recording even
                        // when the page updates only its own child graphics layers. Keep
                        // the backdrop/layer identities stable throughout theme changes.
                        val background = recordingBackgroundState.value
                        if (background != Color.Unspecified) drawRect(background)
                        this@drawWithContent.drawContent()
                    }
                    if (drawContentToScreen) drawLayer(contentLayer)
                },
        )
    }
}
