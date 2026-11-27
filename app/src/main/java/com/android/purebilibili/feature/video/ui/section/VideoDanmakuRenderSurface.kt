package com.android.purebilibili.feature.video.ui.section

import android.os.Build
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
import com.android.purebilibili.feature.video.playback.policy.createPlatformDanmakuBrightnessRenderEffect

@Composable
internal fun VideoDanmakuRenderSurface(
    manager: DanmakuManager,
    viewport: DanmakuViewport,
    useFullscreenTextSize: Boolean,
    renderingPaused: Boolean,
    brightnessFactor: Float = 1f,
    modifier: Modifier = Modifier,
) {
    key(manager) {
        val viewRef = remember { mutableStateOf<DanmakuRenderView?>(null) }
        val lastBrightnessRef = remember { floatArrayOf(1f) }
        AndroidView(
            factory = { context ->
                DanmakuRenderView(context).apply {
                    viewRef.value = this
                    manager.useFullscreenTextSize = useFullscreenTextSize
                    manager.updateViewport(viewport)
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        if (view.width > 0 && view.height > 0) manager.attachView(this)
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    configureAsPassiveDanmakuOverlay()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRenderEffect(createPlatformDanmakuBrightnessRenderEffect(brightnessFactor))
                        lastBrightnessRef[0] = brightnessFactor
                    }
                    manager.setRenderingPaused(this, renderingPaused)
                    manager.attachView(this)
                }
            },
            // 半屏和全屏移动同一播放器时复用渲染器，保留在屏弹幕与时间线。
            // 临时离开布局只暂停；真正离开播放宿主才由 onRelease 释放。
            onReset = { view ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lastBrightnessRef[0] != 1f) {
                    view.setRenderEffect(null)
                    lastBrightnessRef[0] = 1f
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lastBrightnessRef[0] != brightnessFactor) {
                    view.setRenderEffect(createPlatformDanmakuBrightnessRenderEffect(brightnessFactor))
                    lastBrightnessRef[0] = brightnessFactor
                }
                manager.setRenderingPaused(view, renderingPaused)
            },
            onRelease = { view ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    view.setRenderEffect(null)
                    lastBrightnessRef[0] = 1f
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
