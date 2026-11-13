package com.android.purebilibili.navigation

import com.android.purebilibili.core.util.AppFoldPosture
import com.android.purebilibili.core.util.VideoDetailPresentationInput
import com.android.purebilibili.core.util.VideoDetailPresentationMode
import com.android.purebilibili.core.util.WindowHeightSizeClass
import com.android.purebilibili.core.util.WindowSizeClass
import com.android.purebilibili.core.util.resolveVideoDetailPresentation
import com.android.purebilibili.feature.audio.screen.isAudioNowPlayingPlayerDestination
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.toLegacyRoute

/** 列表、搜索和空间等来源共用资格；播放器内继续沿用原有导航。 */
internal fun shouldOpenVideoCompanion(
    key: BiliPaiNavKey,
    origin: BiliPaiNavKey?,
    enabled: Boolean,
    window: WindowSizeClass,
    posture: AppFoldPosture,
    shouldAvoidHinge: Boolean,
): Boolean {
    if (!enabled || key !is BiliPaiNavKey.VideoDetail ||
        isAudioNowPlayingPlayerDestination(origin?.toLegacyRoute())
    ) return false
    return resolveVideoDetailPresentation(
        VideoDetailPresentationInput(
            isExpandedWindow = window.isExpandedScreen && window.heightSizeClass != WindowHeightSizeClass.Compact,
            foldPosture = posture,
            isVerticalVideo = key.initialVertical || key.directPortraitEntry,
            requestsLandscapeFullscreen = key.fullscreen,
            shouldAvoidHinge = shouldAvoidHinge,
            isStartingAudio = key.startAudio,
        ),
    ) == VideoDetailPresentationMode.COMPANION_PANE
}
