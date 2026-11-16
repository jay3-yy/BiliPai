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
