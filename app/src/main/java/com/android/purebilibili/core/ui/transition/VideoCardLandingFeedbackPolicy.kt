package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.core.FloatSpringSpec
import kotlin.math.atan
import kotlin.math.roundToLong
import kotlin.math.sqrt

internal const val VIDEO_CARD_LANDING_DAMPING = 0.80f
internal const val VIDEO_CARD_LANDING_STIFFNESS = 400f
internal class VideoCardLandingMotion(
    val dampingRatio: Float,
    val stiffness: Float,
    val maxEdgeCompressionDp: Float,
    val maxScaleCompression: Float,
) {
    // Cache the unit-velocity spring response once per layout profile, not per card/frame.
    val peakResponseSeconds: Float = run {
        val omega = sqrt(stiffness)
        val dampedOmega = omega * sqrt(1f - dampingRatio * dampingRatio)
        val decay = dampingRatio * omega
        val peakTime = atan(dampedOmega / decay) / dampedOmega
        FloatSpringSpec(dampingRatio = dampingRatio, stiffness = stiffness).getValueFromNanos(
            playTimeNanos = (peakTime * 1_000f).roundToLong() * 1_000_000L,
            initialValue = 0f,
            targetValue = 0f,
            initialVelocity = 1f,
        )
    }
}

private val stackedLandingMotion = VideoCardLandingMotion(
    VIDEO_CARD_LANDING_DAMPING, VIDEO_CARD_LANDING_STIFFNESS, 1.1f, 0.012f,
)
private val horizontalLandingMotion = VideoCardLandingMotion(
    dampingRatio = 0.84f,
    stiffness = 320f,
    maxEdgeCompressionDp = 1.25f,
    maxScaleCompression = 0.009f,
)
private val coverLandingMotion = VideoCardLandingMotion(
    VIDEO_CARD_LANDING_DAMPING, VIDEO_CARD_LANDING_STIFFNESS, 0.65f, 0.008f,
)

/** Custom card-morph profiles only: native material defaults and stacked cards stay unchanged. */
internal fun resolveVideoCardLandingMotion(sourceLayout: VideoCardSourceLayout): VideoCardLandingMotion =
    when (sourceLayout) {
        VideoCardSourceLayout.STACKED -> stackedLandingMotion
        VideoCardSourceLayout.SIDE_BY_SIDE -> horizontalLandingMotion
        VideoCardSourceLayout.COVER_ONLY -> coverLandingMotion
    }

/** Keep stacked feed tiles perceptible; wide cards retain a smaller physical travel budget. */
internal fun resolveVideoCardLandingEdgeCompressionDp(
    widthPx: Float,
    heightPx: Float,
    density: Float,
    sourceLayout: VideoCardSourceLayout,
): Float {
    if (!widthPx.isFinite() || !heightPx.isFinite() || widthPx <= 0f || heightPx <= 0f ||
        !density.isFinite() || density <= 0f
    ) return 0f
    val longestEdgeDp = maxOf(widthPx, heightPx) / density
    if (!longestEdgeDp.isFinite()) return 0f
    val motion = resolveVideoCardLandingMotion(sourceLayout)
    return minOf(motion.maxEdgeCompressionDp, longestEdgeDp * motion.maxScaleCompression / 2f)
}

internal fun resolveVideoCardLandingImpulseVelocity(
    edgeCompressionDp: Float,
    sourceLayout: VideoCardSourceLayout,
): Float {
    if (!edgeCompressionDp.isFinite() || edgeCompressionDp <= 0f) return 0f
    val motion = resolveVideoCardLandingMotion(sourceLayout)
    return edgeCompressionDp.coerceAtMost(motion.maxEdgeCompressionDp) / motion.peakResponseSeconds
}

/**
 * 冲量随释放速度缩放：松手越果断，到达卡槽越快，压缩越深。
 *
 * [releaseVelocity] 为导航驱动的提交速度（进度/秒）：预测返回甩手约 2–4/s，
 * 返回键/程序化返回为 0（保持基线 1x 手感）。基准增益 0.1/s，区间 [0.85, 1.4]。
 */
internal fun resolveVideoCardLandingImpulseScale(releaseVelocity: Float): Float {
    if (!releaseVelocity.isFinite()) return 1f
    return (1f + releaseVelocity * 0.1f).coerceIn(0.85f, 1.4f)
}

/**
 * 系统接口落位与引擎落位共用的压缩行程：同一布局在同一卡片宽度下
 * 必须得到同一边缘行程，切「一镜到底接口」不改变落位手感。
 */
internal fun resolveVideoCardLandingEdgeTravelDp(
    longestEdgeDp: Float,
    sourceLayout: VideoCardSourceLayout,
): Float {
    return resolveVideoCardLandingEdgeCompressionDp(
        widthPx = longestEdgeDp,
        heightPx = longestEdgeDp,
        density = 1f,
        sourceLayout = sourceLayout,
    )
}

/** Outward release is deliberately smaller than compression, with a separate 0.3% scale cap. */
internal fun resolveVideoCardLandingReleaseDp(
    widthPx: Float,
    heightPx: Float,
    density: Float,
    sourceLayout: VideoCardSourceLayout,
): Float {
    val compression = resolveVideoCardLandingEdgeCompressionDp(widthPx, heightPx, density, sourceLayout)
    if (compression <= 0f) return 0f
    val edgeCap = when (sourceLayout) {
        VideoCardSourceLayout.STACKED -> 0.30f
        VideoCardSourceLayout.SIDE_BY_SIDE -> 0.34f
        VideoCardSourceLayout.COVER_ONLY -> 0.18f
    }
    return minOf(edgeCap, compression * 0.30f, maxOf(widthPx, heightPx) / density * 0.0015f)
}

internal fun resolveVideoCardLandingScale(
    widthPx: Float,
    heightPx: Float,
    density: Float,
    edgeCompressionDp: Float,
    sourceLayout: VideoCardSourceLayout,
): Float {
    if (!widthPx.isFinite() || !heightPx.isFinite() || widthPx <= 0f || heightPx <= 0f ||
        !density.isFinite() || density <= 0f ||
        !edgeCompressionDp.isFinite()
    ) return 1f
    val longestEdgePx = maxOf(widthPx, heightPx)
    val limit = resolveVideoCardLandingEdgeCompressionDp(widthPx, heightPx, density, sourceLayout)
    val releaseLimit = resolveVideoCardLandingReleaseDp(widthPx, heightPx, density, sourceLayout)
    // Signed travel: positive compresses, negative briefly releases beyond the original size.
    // Bound outward motion separately so a small card never receives a large text-scale pulse.
    val edgeTravel = edgeCompressionDp.coerceIn(-releaseLimit, limit)
    return 1f - 2f * edgeTravel * density / longestEdgePx
}
