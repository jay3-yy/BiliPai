package com.android.purebilibili.core.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.CompletableDeferred
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** A source and its recording modifier; attach [modifier] to exactly one content container. */
@Stable
class ChromeBackdropSource internal constructor(
    val backdrop: Backdrop,
    private val recordingModifier: Modifier,
    private val pausedModifier: Modifier,
    private val recordingEnabled: State<Boolean>,
    private val recorded: State<Boolean>,
) {
    val modifier: Modifier get() = if (recordingEnabled.value) recordingModifier else pausedModifier
    val isReady: Boolean get() = recordingEnabled.value && recorded.value
}

/**
 * Miuix's source draws its children to the screen, then invokes onDraw to record them again.
 * Record the children once and let both destinations replay the same display list instead.
 * This matters for nested home/navigation sources and the dock's hidden glass/label capture.
 *
 * This is not a bitmap snapshot or a frame cache: child invalidations still record fresh
 * content. Keep the replay layer separate from the backdrop layer to avoid recording itself.
 * By default the replay layer has no extra offscreen raster pass.
 * Miuix still owns coordinates and backdrop lifecycle.
 * Set [drawContentToScreen] to false for hidden captures: record without an alpha-zero layer
 * that can cause the renderer to skip drawing the source content.
 * [isolateContentDrawing] explicitly isolates live child commands for navigation/home sources;
 * other callers retain their existing drawing structure by default.
 * [recordingEnabled] 暂停时移除录制节点而保留源身份；普通内容照常显示，隐藏采样仍不显示。
 * [rasterizeContent] 仅供已有视口边界的导航宿主在玻璃转场时显式启用：
 * 屏幕与背景消费者复用合成纹理，子层失效仍更新画面，不冻结视频或动画。
 * 离屏纹理按源尺寸裁切，不能用于需要越界绘制的图标或隐藏光学采样。
 */
@Composable
fun rememberChromeBackdropSource(
    drawContentToScreen: Boolean = true,
    recordingBackground: Color = Color.Unspecified,
    isolateContentDrawing: Boolean = false,
    recordingEnabled: Boolean = LocalChromeBackdropRecordingAllowed.current,
    rasterizeContent: Boolean = false,
): ChromeBackdropSource {
    val contentLayer = rememberGraphicsLayer()
    SideEffect {
        contentLayer.compositingStrategy = if (rasterizeContent && recordingEnabled) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    // Theme frames update the recording in draw, without replacing the source passed
    // to consumers or rebuilding its modifier chain. Child-layer invalidations still
    // record fresh content; this is not a frozen backdrop cache.
    val recordingBackgroundState = rememberUpdatedState(recordingBackground)
    val recordingEnabledState = rememberUpdatedState(recordingEnabled)
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
    return remember(backdrop, contentLayer, drawContentToScreen, isolateContentDrawing) {
        ChromeBackdropSource(
            backdrop = ContinuousBackdrop(backdrop),
            recordingEnabled = recordingEnabledState,
            recorded = recorded,
            pausedModifier = if (drawContentToScreen) Modifier else Modifier.drawWithContent { },
            recordingModifier = Modifier
                .layerBackdrop(backdrop)
                .drawWithContent {
                    contentLayer.record {
                        // An explicit animated background invalidates this recording even
                        // when the page updates only its own child graphics layers. Keep
                        // the backdrop/layer identities stable throughout theme changes.
                        val background = recordingBackgroundState.value
                        if (background != Color.Unspecified) drawRect(background)
                        // Page viewports own clipping. Hidden optical captures need their
                        // overflow intact; a global clip also changes glass rim sampling.
                        this@drawWithContent.drawContent()
                    }
                    if (drawContentToScreen) drawLayer(contentLayer)
                }
                // 显式用于首页/导航的采样壳：背景色或外层录制失效时只引用内层指令。
                // Auto 图层不强制离屏纹理；子内容自行失效仍刷新，不冻结图片或视频。
                .then(if (isolateContentDrawing) Modifier.graphicsLayer() else Modifier),
        )
    }
}
