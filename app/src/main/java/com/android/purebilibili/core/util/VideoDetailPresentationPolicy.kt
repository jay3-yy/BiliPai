package com.android.purebilibili.core.util

import com.android.purebilibili.core.util.AppFoldPosture

/** 视频详情的呈现模式：整页接管（现状）或 feed 驻留的右侧伴随面板。 */
enum class VideoDetailPresentationMode {
    FULL_TAKEOVER,
    COMPANION_PANE,
}

data class VideoDetailPresentationInput(
    /** 当前窗口宽度 ≥ Expanded（840dp+）；紧凑分屏窗口不算。 */
    val isExpandedWindow: Boolean,
    val foldPosture: AppFoldPosture = AppFoldPosture.None,
    val isVerticalVideo: Boolean,
    /** 横屏全屏意图（用户显式全屏或播放器转场窗口路径）。 */
    val requestsLandscapeFullscreen: Boolean,
    val shouldAvoidHinge: Boolean,
    /** 以听视频模式起步的导航不走面板。 */
    val isStartingAudio: Boolean = false,
)

/**
 * 仅 Expanded+ 窗口内的普通横屏视频走伴随面板；
 * 竖屏/短视频、横屏全屏、听视频起步、Book/Tabletop 姿态与铰链避让窗口
 * 保持现状整页接管，转场与返回链路零改动。
 */
fun resolveVideoDetailPresentation(
    input: VideoDetailPresentationInput,
): VideoDetailPresentationMode {
    val isPanePosture = input.foldPosture == AppFoldPosture.None ||
        input.foldPosture == AppFoldPosture.Flat
    return if (
        input.isExpandedWindow &&
        isPanePosture &&
        !input.isVerticalVideo &&
        !input.requestsLandscapeFullscreen &&
        !input.shouldAvoidHinge &&
        !input.isStartingAudio
    ) {
        VideoDetailPresentationMode.COMPANION_PANE
    } else {
        VideoDetailPresentationMode.FULL_TAKEOVER
    }
}

/**
 * 伴随面板占窗口宽度比例；feed 左侧最少保留 30% 宽度以维持网格可读性。
 */
fun resolveCompanionPaneWidthFraction(windowWidthDp: Int): Float = when {
    windowWidthDp >= 1200 -> 0.58f
    else -> 0.62f
}
