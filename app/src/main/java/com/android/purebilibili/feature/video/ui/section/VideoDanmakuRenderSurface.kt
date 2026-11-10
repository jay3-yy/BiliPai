package com.android.purebilibili.feature.video.ui.section

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier,
) {
    key(manager) {
        AndroidView(
            factory = { context ->
                DanmakuRenderView(context).apply {
                    manager.useFullscreenTextSize = useFullscreenTextSize
                    manager.updateViewport(viewport)
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        if (view.width > 0 && view.height > 0) manager.attachView(this)
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    configureAsPassiveDanmakuOverlay()
                    manager.setRenderingPaused(this, renderingPaused)
                    manager.attachView(this)
                }
            },
            // 半屏和全屏移动同一播放器时复用渲染器，保留在屏弹幕与时间线。
            // 临时离开布局只暂停；真正离开播放宿主才由 onRelease 释放。
            onReset = { view -> manager.setRenderingPaused(view, true) },
            update = { view ->
                manager.updateViewport(viewport)
                manager.useFullscreenTextSize = useFullscreenTextSize
                if (view.width > 0 && view.height > 0) {
                    val sizeTag = "${view.width}x${view.height}"
                    if (view.tag != sizeTag) {
                        view.tag = sizeTag
                        manager.attachView(view)
                    }
                }
                manager.setRenderingPaused(view, renderingPaused)
            },
            onRelease = manager::detachView,
            modifier = modifier,
        )
    }
}
