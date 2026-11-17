// 文件路径: feature/video/ui/components/VideoAspectRatio.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntRect
import androidx.media3.ui.AspectRatioFrameLayout
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.feature.anime4k.gl.Anime4KDisplayScaleMode
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
//  已改用 AppContentColors.accent()

/**
 * 视频比例枚举
 * `resizeMode` 保留给旧偏好映射，真实渲染使用 `playerResizeMode`。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
enum class VideoAspectRatio(
    val displayName: String,
    val resizeMode: Int,
    val targetAspectRatio: Float? = null,
    val playerResizeMode: Int = resizeMode
) {
    FIT("自动", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("裁剪", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    /**
     * Fixed 16:9 frame: outer viewport is letterboxed to 16:9, content [FIT]s inside.
     * (Using ZOOM would crop non-16:9 sources and look like accidental FILL.)
     */
    RATIO_16_9(
        "16:9",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        targetAspectRatio = 16f / 9f,
        playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    ),
    /**
     * Fixed 4:3 frame: outer viewport is letterboxed to 4:3, content [FIT]s inside.
     */
    RATIO_4_3(
        "4:3",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        targetAspectRatio = 4f / 3f,
        playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    ),
    STRETCH("拉伸", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FIT_WIDTH("等宽", 100, playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FIT_HEIGHT("等高", 101, playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ORIGINAL("原始", 102, playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT),
    SCALE_DOWN("限制", 103, playerResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT);

    companion object {
        fun fromResizeMode(mode: Int): VideoAspectRatio {
            return entries.find { it.resizeMode == mode } ?: FIT
        }
    }
}

internal data class VideoViewportLayout(
    val width: Int,
    val height: Int
)

/** Reference fit choices remain available for portrait and landscape source videos alike. */
internal fun resolveSafeVideoAspectRatio(
    preferred: VideoAspectRatio,
    @Suppress("UNUSED_PARAMETER") isVerticalVideo: Boolean,
): VideoAspectRatio = preferred

internal fun resolveVideoViewportLayout(
    containerWidth: Int,
    containerHeight: Int,
    aspectRatio: VideoAspectRatio,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
): VideoViewportLayout {
    val safeWidth = containerWidth.coerceAtLeast(1)
    val safeHeight = containerHeight.coerceAtLeast(1)
    if (videoWidth > 0 && videoHeight > 0) {
        val sourceRatio = videoWidth.toDouble() / videoHeight
        when (aspectRatio) {
            VideoAspectRatio.FIT_WIDTH -> return VideoViewportLayout(
                safeWidth, (safeWidth / sourceRatio).roundToInt().coerceAtLeast(1))
            VideoAspectRatio.FIT_HEIGHT -> return VideoViewportLayout(
                (safeHeight * sourceRatio).roundToInt().coerceAtLeast(1), safeHeight)
            VideoAspectRatio.ORIGINAL -> return VideoViewportLayout(videoWidth, videoHeight)
            VideoAspectRatio.SCALE_DOWN -> {
                val scale = minOf(1.0, safeWidth.toDouble() / videoWidth, safeHeight.toDouble() / videoHeight)
                return VideoViewportLayout((videoWidth * scale).roundToInt().coerceAtLeast(1),
                    (videoHeight * scale).roundToInt().coerceAtLeast(1))
            }
            else -> Unit
        }
    }
    val targetAspectRatio = aspectRatio.targetAspectRatio
        ?.takeIf { it.isFinite() && it > 0f }
        ?: return VideoViewportLayout(width = safeWidth, height = safeHeight)

    val containerAspectRatio = safeWidth.toFloat() / safeHeight.toFloat()
    return if (containerAspectRatio > targetAspectRatio) {
        VideoViewportLayout(
            width = (safeHeight * targetAspectRatio).roundToInt().coerceIn(1, safeWidth),
            height = safeHeight
        )
    } else {
        VideoViewportLayout(
            width = safeWidth,
            height = (safeWidth / targetAspectRatio).roundToInt().coerceIn(1, safeHeight)
        )
    }
}

/**
 * Native-size and source-axis modes also need an explicitly sized, centered viewport.
 */
internal fun shouldUseFillMaxPlayerViewport(aspectRatio: VideoAspectRatio): Boolean {
    return aspectRatio == VideoAspectRatio.FIT || aspectRatio == VideoAspectRatio.FILL ||
        aspectRatio == VideoAspectRatio.STRETCH
}

/**
 * Apply the selected ratio. Recovery runs after Compose layout: remeasure the native children
 * against the current viewport instead of relying on another AndroidView parent traversal.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun applyPlayerViewResizeMode(
    playerView: androidx.media3.ui.PlayerView,
    resizeMode: Int,
    forceRelayout: Boolean,
    expectedWidth: Int = 0,
    expectedHeight: Int = 0,
) {
    val current = playerView.resizeMode
    if (current == resizeMode && !forceRelayout) return
    if (current != resizeMode) {
        playerView.resizeMode = resizeMode
    }
    val contentFrame = playerView.findViewById<android.view.View>(
        androidx.media3.ui.R.id.exo_content_frame,
    )
    playerView.requestLayout()
    contentFrame?.requestLayout()
    val viewportWidth = if (expectedWidth > 0) expectedWidth else playerView.width
    val viewportHeight = if (expectedHeight > 0) expectedHeight else playerView.height
    if (forceRelayout && viewportWidth > 0 && viewportHeight > 0) {
        // 使用 Compose 已测量的视口；外框尚未同步时，不能拿旧分栏尺寸再次重测。
        // 同时清除子树测量缓存，让画面、快门及字幕按同一视口布局。
        forcePlayerViewportLayout(playerView)
        playerView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(
                viewportWidth, android.view.View.MeasureSpec.EXACTLY,
            ),
            android.view.View.MeasureSpec.makeMeasureSpec(
                viewportHeight, android.view.View.MeasureSpec.EXACTLY,
            ),
        )
        playerView.layout(
            playerView.left, playerView.top,
            playerView.left + viewportWidth, playerView.top + viewportHeight,
        )
    }
    playerView.invalidate()
}

private fun forcePlayerViewportLayout(view: android.view.View) {
    view.forceLayout()
    if (view is android.view.ViewGroup) {
        for (index in 0 until view.childCount) {
            forcePlayerViewportLayout(view.getChildAt(index))
        }
    }
}

internal fun shouldRefreshMeasuredPlayerViewport(
    expectedWidth: Int,
    expectedHeight: Int,
    measuredWidth: Int,
    measuredHeight: Int
): Boolean {
    if (expectedWidth <= 0 || expectedHeight <= 0) return true
    return expectedWidth != measuredWidth || expectedHeight != measuredHeight
}

/** Coalesce viewport recovery on the View, without retaining disposed players globally. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class PlayerViewportRefreshController(
    private val playerView: androidx.media3.ui.PlayerView,
) : android.view.View.OnAttachStateChangeListener, android.view.View.OnLayoutChangeListener {
    private data class Request(
        val resizeMode: Int,
        val width: Int,
        val height: Int,
        val videoWidth: Int,
        val videoHeight: Int,
        val player: androidx.media3.common.Player?,
    )

    private var requested: Request? = null
    private var completed: Request? = null
    private var pending: Runnable? = null
    private var pendingPreDraw: androidx.core.view.OneShotPreDrawListener? = null

    init {
        playerView.addOnAttachStateChangeListener(this)
        playerView.addOnLayoutChangeListener(this)
    }

    fun schedule(mode: Int, width: Int, height: Int, videoWidth: Int, videoHeight: Int) {
        val request = Request(mode, width, height, videoWidth, videoHeight, playerView.player)
        if (requested == request && (pending != null || pendingPreDraw != null)) return
        // Pager callers may omit expected dimensions: do not cache those as a known
        // completed viewport, since their next request can represent a new View size.
        if (completed == request && playerView.resizeMode == mode && !playerView.isLayoutRequested &&
            width > 0 && height > 0 && !shouldRefreshMeasuredPlayerViewport(
                width, height, playerView.width, playerView.height,
            )
        ) return
        cancelPending()
        requested = request
        // A real mode change is applied immediately; same-mode recovery waits for layout.
        applyPlayerViewResizeMode(playerView, mode, forceRelayout = false)
        if (playerView.isAttachedToWindow) enqueue(request, retry = false)
    }

    private fun enqueue(request: Request, retry: Boolean) {
        val callback = Runnable {
            pending = null
            if (requested != request || !playerView.isAttachedToWindow) return@Runnable
            applyPlayerViewResizeMode(
                playerView, request.resizeMode, forceRelayout = true,
                expectedWidth = request.width, expectedHeight = request.height,
            )
            // postOnAnimation 早于本帧布局；保留一次绘制前校正，避免随后原生
            // 父布局只改外框，内部画面继续保留分栏尺寸。此回调不逐帧循环。
            pendingPreDraw = androidx.core.view.OneShotPreDrawListener.add(playerView) {
                pendingPreDraw = null
                if (requested == request && playerView.isAttachedToWindow) {
                    applyPlayerViewResizeMode(
                        playerView, request.resizeMode, forceRelayout = true,
                        expectedWidth = request.width, expectedHeight = request.height,
                    )
                }
            }
            // At most one follow-up, and only when there is a known size mismatch.
            if (!retry && request.width > 0 && request.height > 0 &&
                shouldRefreshMeasuredPlayerViewport(
                    request.width, request.height, playerView.width, playerView.height,
                )
            ) {
                enqueue(request, retry = true)
            } else {
                completed = request
            }
        }
        pending = callback
        playerView.postOnAnimation(callback)
    }

    private fun cancelPending() {
        pending?.let(playerView::removeCallbacks)
        pending = null
        pendingPreDraw?.removeListener()
        pendingPreDraw = null
    }

    fun cancel() {
        cancelPending()
        requested = null
        completed = null
    }

    override fun onLayoutChange(
        view: android.view.View,
        left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
    ) {
        if (right - left == oldRight - oldLeft && bottom - top == oldBottom - oldTop) return
        val request = requested ?: return
        completed = null
        // 原生布局回调只触发恢复，不能覆盖 Compose 已提交的目标视口。
        // 未提供目标尺寸的调用保持 0，恢复时继续读取当前 View，避免把旧布局固化为目标。
        schedule(
            request.resizeMode,
            request.width, request.height,
            request.videoWidth, request.videoHeight,
        )
    }

    override fun onViewDetachedFromWindow(view: android.view.View) {
        cancelPending()
        completed = null
    }

    override fun onViewAttachedToWindow(view: android.view.View) {
        requested?.let { enqueue(it, retry = false) }
    }
}

/** The latest viewport replaces queued recovery for older sizes or video formats. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun schedulePlayerViewViewportRefresh(
    playerView: androidx.media3.ui.PlayerView,
    resizeMode: Int,
    expectedWidth: Int = 0,
    expectedHeight: Int = 0,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
) {
    val tagId = com.android.purebilibili.R.id.video_player_viewport_refresh_controller
    val controller = playerView.getTag(tagId) as? PlayerViewportRefreshController
        ?: PlayerViewportRefreshController(playerView).also { playerView.setTag(tagId, it) }
    controller.schedule(resizeMode, expectedWidth, expectedHeight, videoWidth, videoHeight)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun cancelPlayerViewViewportRefresh(playerView: androidx.media3.ui.PlayerView) {
    (playerView.getTag(com.android.purebilibili.R.id.video_player_viewport_refresh_controller)
        as? PlayerViewportRefreshController)?.cancel()
}

/**
 * 为直接绘制到 GL Surface 的视频计算内容视口。
 * PlayerView 会在内部处理 FIT/ZOOM，GL 输出必须显式保持源视频比例。
 */
internal fun VideoAspectRatio.toAnime4KDisplayScaleMode(): Anime4KDisplayScaleMode {
    return when (this) {
        VideoAspectRatio.FILL -> Anime4KDisplayScaleMode.CROP
        VideoAspectRatio.STRETCH -> Anime4KDisplayScaleMode.STRETCH
        VideoAspectRatio.FIT,
        VideoAspectRatio.FIT_WIDTH,
        VideoAspectRatio.FIT_HEIGHT,
        VideoAspectRatio.ORIGINAL,
        VideoAspectRatio.SCALE_DOWN,
        VideoAspectRatio.RATIO_16_9,
        VideoAspectRatio.RATIO_4_3 -> Anime4KDisplayScaleMode.FIT
    }
}

/**
 * 视频比例选择菜单
 */
@Composable
fun AspectRatioMenu(
    currentRatio: VideoAspectRatio,
    onRatioSelected: (VideoAspectRatio) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    placement: PlayerListPopupPlacement = PlayerListPopupPlacement.END,
    anchorBounds: IntRect? = null,
) {
    PlayerSelectionPopup(
        title = "画面比例",
        showTitle = false,
        onDismissRequest = onDismiss,
        modifier = modifier,
        placement = placement,
        anchorBounds = anchorBounds,
        minWidth = 160.dp,
    ) {
        val options = listOf(VideoAspectRatio.STRETCH, VideoAspectRatio.FIT, VideoAspectRatio.FILL,
            VideoAspectRatio.FIT_WIDTH, VideoAspectRatio.FIT_HEIGHT, VideoAspectRatio.ORIGINAL,
            VideoAspectRatio.SCALE_DOWN, VideoAspectRatio.RATIO_4_3, VideoAspectRatio.RATIO_16_9)
        options.forEachIndexed { index, ratio ->
            val selected = ratio == currentRatio
            PlayerSelectionItem(
                modifier = Modifier.playerPopupSelectedOption(selected),
                text = ratio.displayName,
                index = index, optionCount = options.size,
                selected = selected,
                enabled = !selected,
                onClick = {
                    onRatioSelected(ratio)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * 比例按钮（用于底部控制栏）
 */
@Composable
fun AspectRatioButton(
    currentRatio: VideoAspectRatio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppSurface(
        onClick = onClick,
        modifier = modifier,
        shape = AppShapes.container(ContainerLevel.Chip),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            AppIcon(
                Icons.Outlined.Star,
                contentDescription = "画面比例",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            AppText(
                text = currentRatio.displayName,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
