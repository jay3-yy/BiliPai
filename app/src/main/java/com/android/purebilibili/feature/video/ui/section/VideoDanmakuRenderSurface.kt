package com.android.purebilibili.feature.video.ui.section

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.danmaku.DanmakuViewport
import com.android.purebilibili.feature.video.danmaku.configureAsPassiveDanmakuOverlay

@Composable
internal fun VideoDanmakuRenderSurface(
    manager: DanmakuManager,
    viewport: DanmakuViewport,
    useFullscreenTextSize: Boolean,
    renderingPaused: Boolean,
    brightnessFactor: Float = 1f,
    useIndependentSurface: Boolean = false,
    surfaceVisible: Boolean = true,
    targetFrameRateHz: Float = 0f,
    modifier: Modifier = Modifier,
) {
    key(manager, useIndependentSurface) {
        val viewRef = remember { mutableStateOf<DanmakuRenderView?>(null) }
        val lastBrightnessRef = remember { floatArrayOf(1f) }
        val lastTargetFrameRateRef = remember { floatArrayOf(0f) }
        AndroidView(
            factory = { context ->
                DanmakuRenderView(
                    context = context,
                    usesIndependentSurface = useIndependentSurface,
                ).apply {
                    viewRef.value = this
                    manager.useFullscreenTextSize = useFullscreenTextSize
                    manager.updateViewport(viewport)
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        if (view.width > 0 && view.height > 0) manager.attachView(this)
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    configureAsPassiveDanmakuOverlay()
                    setSurfaceOverlayVisible(surfaceVisible)
                    engine.setBrightnessFactor(brightnessFactor)
                    lastBrightnessRef[0] = brightnessFactor
                    engine.setTargetFrameRateHz(targetFrameRateHz)
                    lastTargetFrameRateRef[0] = targetFrameRateHz
                    manager.setRenderingPaused(this, renderingPaused)
                    manager.attachView(this)
                }
            },
            // 半屏和全屏移动同一播放器时复用渲染器，保留在屏弹幕与时间线。
            // 临时离开布局只暂停；真正离开播放宿主才由 onRelease 释放。
            onReset = { view ->
                if (lastBrightnessRef[0] != 1f) {
                    view.engine.setBrightnessFactor(1f)
                    lastBrightnessRef[0] = 1f
                }
                if (lastTargetFrameRateRef[0] != 0f) {
                    view.engine.setTargetFrameRateHz(0f)
                    lastTargetFrameRateRef[0] = 0f
                }
                manager.setRenderingPaused(view, true)
            },
            update = { view ->
                viewRef.value = view
                manager.updateViewport(viewport)
                manager.useFullscreenTextSize = useFullscreenTextSize
                if (view.width > 0 && view.height > 0) {
                    val sizeTag = "${view.width}x${view.height}"
                    if (view.tag != sizeTag) {
                        view.tag = sizeTag
                        manager.attachView(view)
                    }
                }
                view.setSurfaceOverlayVisible(surfaceVisible)
                if (lastBrightnessRef[0] != brightnessFactor) {
                    view.engine.setBrightnessFactor(brightnessFactor)
                    lastBrightnessRef[0] = brightnessFactor
                }
                if (lastTargetFrameRateRef[0] != targetFrameRateHz) {
                    view.engine.setTargetFrameRateHz(targetFrameRateHz)
                    lastTargetFrameRateRef[0] = targetFrameRateHz
                }
                manager.setRenderingPaused(view, renderingPaused)
            },
            onRelease = { view ->
                if (lastBrightnessRef[0] != 1f) {
                    view.engine.setBrightnessFactor(1f)
                    lastBrightnessRef[0] = 1f
                }
                if (lastTargetFrameRateRef[0] != 0f) {
                    view.engine.setTargetFrameRateHz(0f)
                    lastTargetFrameRateRef[0] = 0f
                }
                if (viewRef.value === view) viewRef.value = null
                manager.detachView(view)
            },
            modifier = modifier.onGloballyPositioned { coordinates ->
                viewRef.value?.let { view -> manager.updateViewCoordinates(view, coordinates) }
            },
        )
    }
}
