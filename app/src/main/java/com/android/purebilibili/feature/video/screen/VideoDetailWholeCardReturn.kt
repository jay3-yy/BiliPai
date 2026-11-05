package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.normalizeSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.resolveVideoCardWholeCardReturnHandoffAlpha
import com.android.purebilibili.core.util.CardPositionManager

internal fun resolveVideoDetailWholeCardReturnAlpha(
    depth: Float,
    returning: Boolean,
    committedIdle: Boolean,
): Float {
    if (committedIdle) return 1f
    if (!returning) return 0f
    return resolveVideoCardWholeCardReturnHandoffAlpha(depth)
}

internal class VideoDetailWholeCardReturn(
    val modifier: Modifier,
)

/**
 * 手机、大屏及所有材质共用同一份点击时整卡像素快照。只做等比缩放，不重新布局封面和文字。
 * 录制资源按详情 entry 的视频与来源身份保留，直到 entry 退出；嵌套父页不得消费子页图层。
 */
@Composable
internal fun rememberVideoDetailWholeCardReturn(
    bvid: String,
    sourceRoute: String?,
    ownsTransition: Boolean,
    committedReturn: Boolean,
    keepLoadedContentForBackPreview: Boolean,
    sourceBounds: Rect?,
    sourceLayout: VideoCardSourceLayout,
    isNowPlayingBar: Boolean,
    depthProvider: () -> Float,
    phaseProvider: () -> VideoCardTransitionBackgroundPhase,
    gestureProvider: () -> Boolean,
): VideoDetailWholeCardReturn {
    val expectedKey = normalizeSharedElementSourceRoute(sourceRoute)?.let { "$it:${bvid.trim()}" }
    // Bind once per entry, before an asynchronous capture is ready. Mutable navigation keys
    // cannot grant an already retained parent ownership of a later child's pixels.
    val pixelOwner = remember(bvid, sourceRoute) {
        CardPositionManager.videoCardPixelOwnerFor(expectedKey)
    }
    var retained by remember(bvid, sourceRoute) { mutableStateOf<ImageBitmap?>(null) }
    val captured = CardPositionManager.lastClickedWholeCardReturnSnapshot
    val bitmap = captured?.bitmap
    val candidate: ImageBitmap? = if (ownsTransition && !isNowPlayingBar &&
        sourceLayout != VideoCardSourceLayout.COVER_ONLY && pixelOwner != null &&
        captured?.owner === pixelOwner &&
        bitmap != null && bitmap.width > 1 && bitmap.height > 1 &&
        sourceBounds != null && sourceBounds.width > 1f && sourceBounds.height > 1f
    ) bitmap else null
    SideEffect { if (retained == null && candidate != null) retained = candidate }
    val snapshot: ImageBitmap? = retained ?: candidate
    val active: () -> Boolean = {
        snapshot != null && !keepLoadedContentForBackPreview &&
            (ownsTransition || committedReturn) &&
            (committedReturn || gestureProvider() ||
                phaseProvider() == VideoCardTransitionBackgroundPhase.RETURNING)
    }
    val modifier = Modifier.videoDetailWholeCardReturnLayer(bitmap = snapshot) {
        if (!active()) 0f else resolveVideoDetailWholeCardReturnAlpha(
            depth = depthProvider(),
            returning = true,
            committedIdle = committedReturn && !gestureProvider() &&
                phaseProvider() == VideoCardTransitionBackgroundPhase.IDLE,
        )
    }
    return VideoDetailWholeCardReturn(modifier)
}

/** 整卡像素在同一透明度组中绘制，避免封面、角标和信息区各自裁切或各自叠化。 */
internal fun Modifier.videoDetailWholeCardReturnLayer(
    bitmap: ImageBitmap?,
    alphaProvider: () -> Float,
): Modifier {
    // 无有效快照的正常播放入口不额外建立全屏合成层。
    if (bitmap == null || bitmap.width <= 1 || bitmap.height <= 1) return this
    return drawWithContent {
        val alpha = alphaProvider().coerceIn(0f, 1f)
        if (alpha <= 0f) {
            drawContent()
        } else {
            // 内层持有实时内容的绘制指令，只有实际内容失效才重新录制。
            // 叠化只改整组图层属性，快照完全不透明时才跳过实时内容。
            if (alpha < 1f) drawContent()
            // 快照本身已是单张合成位图，直接调制透明度与整组叠化等价。
            // 无需再创建全屏 saveLayer；实时内容由内层整组透明度承接，避免重叠组件变色。
            // FillWidthTop 的导航补偿已统一两轴比例，整卡只按宽度缩放一次。
            val sourcePixelScale = size.width / bitmap.width
            clipRect {
                scale(sourcePixelScale, sourcePixelScale, pivot = Offset.Zero) {
                    drawImage(bitmap, alpha = alpha)
                }
            }
        }
    }.graphicsLayer {
        // 只在图层阶段读进度，不让进度每帧驱动详情正文重新执行 drawContent。
        // Auto 保留重叠内容的整组混合语义，不能改成逐绘制指令调制 alpha。
        alpha = 1f - alphaProvider().coerceIn(0f, 1f)
    }
}
