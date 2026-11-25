package com.android.purebilibili.feature.audio.screen

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.emphasizedExitTween

/**
 * 小横条（听视频 NowPlaying 条）presence 出入场动效 token。
 *
 * 出入场采用非对称节奏（enter 减速落定 / exit 加速离场），与 MD3 emphasized 曲线体系一致；
 * 小横条首次出现使用弹簧，退出使用加速曲线。
 */
internal const val AUDIO_NOW_PLAYING_PRESENCE_ENTER_DURATION_MILLIS = 300
internal const val AUDIO_NOW_PLAYING_PRESENCE_EXIT_DURATION_MILLIS = 220
internal const val AUDIO_NOW_PLAYING_PRESENCE_REDUCED_MOTION_DURATION_MILLIS = 160

/** 会话激活（进场）时 presence 进度动画规格。 */
internal fun resolveAudioNowPlayingPresenceAnimationSpec(
    active: Boolean,
    reduceMotion: Boolean,
): TweenSpec<Float> = when {
    reduceMotion -> tween(AUDIO_NOW_PLAYING_PRESENCE_REDUCED_MOTION_DURATION_MILLIS)
    active -> emphasizedEnterTween(AUDIO_NOW_PLAYING_PRESENCE_ENTER_DURATION_MILLIS)
    else -> emphasizedExitTween(AUDIO_NOW_PLAYING_PRESENCE_EXIT_DURATION_MILLIS)
}

/** presence 几何（独立挂载路径的高度展开/收起）动画规格。 */
internal fun resolveAudioNowPlayingPresenceGeometrySpec(
    active: Boolean,
    reduceMotion: Boolean,
): TweenSpec<IntSize> = when {
    reduceMotion -> tween(AUDIO_NOW_PLAYING_PRESENCE_REDUCED_MOTION_DURATION_MILLIS)
    active -> emphasizedEnterTween(AUDIO_NOW_PLAYING_PRESENCE_ENTER_DURATION_MILLIS)
    else -> emphasizedExitTween(AUDIO_NOW_PLAYING_PRESENCE_EXIT_DURATION_MILLIS)
}

/** 首次出现的上滑距离：presence 0→1 时小横条从下方此位移上滑落位。 */
internal const val AUDIO_NOW_PLAYING_PRESENCE_ENTER_SLIDE_DP = 28f

/**
 * 首次出现的上滑入场弹簧：复用液态玻璃展开阻尼与响应，轻快落位且无明显果冻回弹。
 */
internal fun resolveAudioNowPlayingPresenceEnterSpringSpec(): SpringSpec<Float> =
    spring(
        dampingRatio = com.android.purebilibili.core.ui.motion.GlassMorphSprings.Expand.dampingRatio,
        stiffness = com.android.purebilibili.core.ui.motion.GlassMorphSprings.Expand.stiffness,
    )

/** 独立挂载路径的高度展开入场弹簧（expandVertically 自下而上柔和落位）。 */
internal fun resolveAudioNowPlayingPresenceEnterGeometrySpringSpec(): SpringSpec<IntSize> =
    spring(
        dampingRatio = com.android.purebilibili.core.ui.motion.GlassMorphSprings.Expand.dampingRatio,
        stiffness = com.android.purebilibili.core.ui.motion.GlassMorphSprings.Expand.stiffness,
    )

/**
 * presence alpha 窗口：几何先展开，alpha 在后半段跟上，避免空壳提前可见。
 * 与 [AudioNowPlayingBarRowMetrics] 体系的阈值窗口风格一致。
 */
internal fun resolveAudioNowPlayingPresenceAlpha(progress: Float): Float =
    ((progress.coerceIn(0f, 1f) - 0.25f) / 0.75f).coerceIn(0f, 1f)

/** presence 几何乘数：驱动 audio 槽宽度（Compact）或高度（Expanded）的展开量。 */
internal fun resolveAudioNowPlayingPresenceGeometryFactor(progress: Float): Float =
    progress.coerceIn(0f, 1f)
