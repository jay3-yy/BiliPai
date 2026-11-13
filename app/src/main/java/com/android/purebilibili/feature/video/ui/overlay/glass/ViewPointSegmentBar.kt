package com.android.purebilibili.feature.video.ui.overlay.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.data.model.response.ViewPoint

/** 玻璃与原生共用章节填充、分隔与跳转逻辑，材质由外层控制栏提供。 */
@Composable
fun ViewPointSegmentBar(
    viewPoints: List<ViewPoint>,
    durationMs: Long,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    positionProvider: (() -> Long)? = null,
) {
    com.android.purebilibili.feature.video.ui.overlay.ViewPointSegmentBar(
        viewPoints = viewPoints,
        durationMs = durationMs,
        currentPositionMs = currentPositionMs,
        onSeek = onSeek,
        modifier = modifier,
        positionProvider = positionProvider,
    )
}
