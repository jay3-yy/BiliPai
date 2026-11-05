package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale

/** 封面内部共享布局保留原有缩放策略；普通视频的整卡开合由窗口转场负责。 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun resolveVideoCardSharedBoundsResizeMode(
    fillFullscreenShell: Boolean = false,
): SharedTransitionScope.ResizeMode {
    return if (fillFullscreenShell) {
        scaleToBounds(ContentScale.Crop, Alignment.Center)
    } else {
        scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter)
    }
}

/**
 * 详情侧是否还要挂「封面 ↔ 播放器」cover sharedBounds。
 * shell 已接管整卡 morph 时再挂 cover 会与默认 Center 路径抢戏，中间态往屏幕中心飞。
 */
internal fun shouldAttachVideoDetailCoverSharedBounds(
    coverSharedBoundsEnabled: Boolean,
    detailShellSharedBoundsEnabled: Boolean,
    immediatePlayback: Boolean,
    forceCoverOnlyForReturn: Boolean,
): Boolean {
    if (!coverSharedBoundsEnabled) return false
    if (detailShellSharedBoundsEnabled) return false
    if (!immediatePlayback) return false
    if (forceCoverOnlyForReturn) return false
    return true
}

internal const val VIDEO_CARD_SHELL_SOURCE_EXIT_FADE_RATIO = 0.28f
