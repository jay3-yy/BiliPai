package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.android.purebilibili.core.ui.transition.normalizeSharedElementSourceRoute
import com.android.purebilibili.core.util.CardPositionManager

internal enum class VideoDetailEntryWorkPhase {
    Content,
    Playback,
    Comments,
    Related,
}

/** 转场后按帧错峰放行；评论与推荐不等待视频首帧或缓冲完成。 */
internal suspend fun runVideoDetailEntryWorkHandoff(
    onPlaybackReady: () -> Unit,
    onCommentsReady: () -> Unit,
    onRelatedReady: () -> Unit,
    startPhase: VideoDetailEntryWorkPhase = VideoDetailEntryWorkPhase.Content,
) {
    // A frame callback runs before composition/draw. Two boundaries give the preceding
    // phase a frame to render; a single boundary can batch both phases into one frame.
    if (startPhase < VideoDetailEntryWorkPhase.Playback) {
        repeat(2) { withFrameNanos { } }
        onPlaybackReady()
    }
    if (startPhase < VideoDetailEntryWorkPhase.Comments) {
        repeat(2) { withFrameNanos { } }
        onCommentsReady()
    }
    if (startPhase < VideoDetailEntryWorkPhase.Related) {
        repeat(2) { withFrameNanos { } }
        onRelatedReady()
    }
}

@Composable
internal fun rememberVideoDetailEntryWorkPhase(
    staggerWork: Boolean,
    contentReady: Boolean,
    isVisible: Boolean,
): VideoDetailEntryWorkPhase {
    // Entry lifetime, not BVID lifetime: changing episode or returning from a child must
    // not close an already-open playback gate or replay the initial handoff.
    var phase by remember {
        mutableStateOf(if (staggerWork) VideoDetailEntryWorkPhase.Content else VideoDetailEntryWorkPhase.Related)
    }
    LaunchedEffect(staggerWork, contentReady, isVisible) {
        if (!staggerWork) {
            phase = VideoDetailEntryWorkPhase.Related
            return@LaunchedEffect
        }
        if (!contentReady || !isVisible || phase == VideoDetailEntryWorkPhase.Related) {
            return@LaunchedEffect
        }
        runVideoDetailEntryWorkHandoff(
            onPlaybackReady = {
                if (phase < VideoDetailEntryWorkPhase.Playback) phase = VideoDetailEntryWorkPhase.Playback
            },
            onCommentsReady = {
                if (phase < VideoDetailEntryWorkPhase.Comments) phase = VideoDetailEntryWorkPhase.Comments
            },
            onRelatedReady = { phase = VideoDetailEntryWorkPhase.Related },
            startPhase = phase,
        )
    }
    return phase
}

/** 首轮分阶段工作完成后再放行来源像素读回；许可属于 entry 创建时的点击身份。 */
@Composable
internal fun ReleaseVideoCardCaptureAfterEntryWork(
    enabled: Boolean,
    bvid: String,
    sourceRoute: String?,
    ready: Boolean,
    isVisible: Boolean,
) {
    val owner = remember(enabled, bvid, sourceRoute) {
        val sourceKey = normalizeSharedElementSourceRoute(sourceRoute)?.let { "$it:${bvid.trim()}" }
        if (enabled) CardPositionManager.videoCardPixelOwnerFor(sourceKey) else null
    }
    LaunchedEffect(owner, ready, isVisible) {
        if (owner == null || !ready || !isVisible || owner.entryWorkReleased) return@LaunchedEffect
        // 推荐许可刚变为 true 时还未保证绘制完成；先留出完整绘制帧，再让来源排队读回。
        repeat(2) { withFrameNanos { } }
        if (CardPositionManager.isActiveVideoCardPixelOwner(owner)) owner.releaseEntryWork()
    }
}
