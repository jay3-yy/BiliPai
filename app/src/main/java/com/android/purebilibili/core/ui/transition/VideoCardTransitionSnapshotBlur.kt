package com.android.purebilibili.core.ui.transition

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.drawLayer

/**
 * 来源仍挂载的容器转场复用卡片景深的来源缓存、效果纹理与半径补偿。
 * 调用方负责缩放、圆角及遮罩；此处仅处理模糊，前景实时画面不进入缓存。
 */
internal fun Modifier.videoCardTransitionSnapshotBlur(
    frameProvider: () -> VideoCardTransitionBackgroundFrame?,
    refreshSourceProvider: () -> Boolean,
): Modifier = drawWithCache {
    val snapshot = VideoCardTransitionSnapshotHandle(
        contentLayer = obtainGraphicsLayer(),
        sourceLayer = obtainGraphicsLayer(),
        sharpLayer = obtainGraphicsLayer(),
        state = VideoCardTransitionSnapshotLayerState(),
    )
    var recorded = false
    var refreshing = false
    onDrawWithContent {
        val frame = frameProvider()
        if (frame == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || frame.blurRadiusPx <= 0.01f) {
            if (recorded) snapshot.releaseSession()
            recorded = false
            refreshing = false
            drawContent()
        } else {
            val refresh = refreshSourceProvider()
            // 进入只捕获一次，返回/取消后再次返回时按当前来源刷新一次。
            if (!recorded || (refresh && !refreshing)) {
                snapshot.recordSource(this) { this@onDrawWithContent.drawContent() }
                recorded = true
            }
            refreshing = refresh
            val rasterScale = snapshot.prepareEffectLayer(this, frame.blurRadiusPx)
            applyVideoCardTransitionSnapshotFrame(
                contentLayer = snapshot.contentLayer,
                snapshotState = snapshot.state,
                frame = frame.copy(contentScale = 1f, cornerRadiusPx = 0f, shadowElevationPx = 0f),
                canvasSize = size,
                rasterScale = rasterScale,
            )
            drawLayer(snapshot.contentLayer)
        }
    }
}
