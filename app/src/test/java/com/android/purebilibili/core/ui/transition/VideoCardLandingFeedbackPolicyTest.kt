package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoCardLandingFeedbackPolicyTest {
    private val stacked = VideoCardSourceLayout.STACKED
    private val horizontal = VideoCardSourceLayout.SIDE_BY_SIDE

    @Test
    fun horizontalMotionIsIndependentAndOtherProfilesKeepTheirValues() {
        val row = resolveVideoCardLandingMotion(horizontal)
        assertEquals(0.84f, row.dampingRatio)
        assertEquals(320f, row.stiffness)
        for (layout in listOf(stacked, VideoCardSourceLayout.COVER_ONLY)) {
            val motion = resolveVideoCardLandingMotion(layout)
            assertEquals(0.80f, motion.dampingRatio)
            assertEquals(400f, motion.stiffness)
        }
        for (density in listOf(1f, 2.75f, 3f, 4f)) {
            val scale = resolveVideoCardLandingScale(380f * density, 110f * density, density, 1.25f, horizontal)
            assertEquals(1.25f, (1f - scale) * 380f / 2f, 0.0001f)
            assertEquals(0.3618421f, (1f - scale) * 110f / 2f, 0.0001f)
        }
    }

    @Test
    fun dualColumnTileHasPerceptibleTravelWhileWideCardsStayRestrained() {
        val tile = resolveVideoCardLandingScale(180f, 240f, 1f, 1.1f, stacked)
        val wide = resolveVideoCardLandingScale(400f, 120f, 1f, 1.25f, horizontal)
        val tablet = resolveVideoCardLandingScale(800f, 200f, 1f, 1.25f, horizontal)
        assertEquals(0.9908333f, tile, 0.00001f)
        assertEquals(1.25f, (1f - wide) * 400f / 2f, 0.0001f)
        assertEquals(1.25f, (1f - tablet) * 800f / 2f, 0.0001f)
        assertTrue(tile < wide && wide < tablet)
    }

    @Test
    fun densityAndGeometryDoNotChangeTheLayoutSpecificTravelBudget() {
        val expected = resolveVideoCardLandingScale(180f, 240f, 1f, 1.1f, stacked)
        for (density in listOf(1f, 2f, 2.75f, 3f, 4f)) {
            assertEquals(expected, resolveVideoCardLandingScale(180f * density, 240f * density, density, 1.1f, stacked))
        }
        assertEquals(0.988f, resolveVideoCardLandingScale(100f, 60f, 1f, 1.1f, stacked), 0.0001f)
        assertEquals(0.991f, resolveVideoCardLandingScale(100f, 60f, 1f, 1.25f, horizontal), 0.0001f)
    }

    @Test
    fun compressionImpulseKeepsItsOriginalBudgetAndDecay() {
        for (layout in VideoCardSourceLayout.entries) {
            val motion = resolveVideoCardLandingMotion(layout)
            val compressionSpring = FloatSpringSpec(
                dampingRatio = motion.dampingRatio, stiffness = motion.stiffness,
            )
            for (width in listOf(100f, 180f, 400f, 800f)) {
                val height = if (layout == stacked) width * 4f / 3f else width / 3f
                val budget = resolveVideoCardLandingEdgeCompressionDp(width, height, 1f, layout)
                val velocity = resolveVideoCardLandingImpulseVelocity(budget, layout)
                var peak = 0f
                for (ms in 0..500) {
                    val edge = compressionSpring.getValueFromNanos(ms * 1_000_000L, 0f, 0f, velocity)
                    peak = maxOf(peak, edge)
                    val scale = resolveVideoCardLandingScale(width, height, 1f, edge.coerceAtLeast(0f), layout)
                    assertTrue(scale in 0.988f..1f)
                    if (ms >= 300) assertTrue(abs(edge) < 0.02f)
                }
                assertEquals(budget, peak, 0.001f)
            }
        }
    }

    @Test
    fun combinedMotionReleasesOutwardOnceAndFinishesAtOriginalSize() {
        for (layout in VideoCardSourceLayout.entries) {
            val width = if (layout == stacked) 180f else 380f
            val height = if (layout == stacked) 240f else 110f
            val motion = resolveVideoCardLandingMotion(layout)
            val compression = resolveVideoCardLandingEdgeCompressionDp(width, height, 1f, layout)
            val release = resolveVideoCardLandingReleaseDp(width, height, 1f, layout)
            val releaseAnimation = TargetBasedAnimation(
                animationSpec = videoCardLandingReleaseSpec(release),
                typeConverter = Float.VectorConverter,
                initialValue = 0f,
                targetValue = 0f,
            )
            val velocity = resolveVideoCardLandingImpulseVelocity(compression, layout)
            val compressionSpring = FloatSpringSpec(
                dampingRatio = motion.dampingRatio, stiffness = motion.stiffness,
            )
            var outwardIntervals = 0
            var wasOutward = false
            var peakOutwardDp = 0f
            for (ms in 0..500) {
                val spring = compressionSpring.getValueFromNanos(ms * 1_000_000L, 0f, 0f, velocity)
                val releaseValue = releaseAnimation.getValueFromNanos(ms * 1_000_000L)
                val travel = spring.coerceAtLeast(0f) - releaseValue
                val scale = resolveVideoCardLandingScale(width, height, 1f, travel, layout)
                val outward = scale > 1.000001f
                if (outward && !wasOutward) outwardIntervals++
                wasOutward = outward
                peakOutwardDp = maxOf(peakOutwardDp, -travel)
                assertTrue(scale in 0.988f..1.003001f)
                if (ms <= 100) assertEquals(0f, releaseValue)
                if (ms >= 350) assertEquals(1f, scale, 0.00001f)
            }
            assertEquals(1, outwardIntervals)
            assertTrue(peakOutwardDp > compression * 0.10f)
            assertTrue(peakOutwardDp <= release)
        }
    }

    @Test
    fun invalidGeometryStaysAtIdentityAndOutwardTravelIsBounded() {
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(1f, resolveVideoCardLandingScale(invalid, 100f, 1f, 1.1f, stacked))
            assertEquals(1f, resolveVideoCardLandingScale(200f, 100f, invalid, 1.1f, stacked))
        }
        val releaseCap = resolveVideoCardLandingReleaseDp(200f, 100f, 1f, stacked)
        assertEquals(1f + 2f * releaseCap / 200f, resolveVideoCardLandingScale(200f, 100f, 1f, -100f, stacked))
        assertEquals(1f, resolveVideoCardLandingScale(200f, 100f, 1f, Float.NaN, stacked))
    }

    @Test
    fun systemInterfaceLandingResolvesTheSamePerLayoutTravelAsTheEngine() {
        for (layout in VideoCardSourceLayout.entries) {
            for (longestEdgeDp in listOf(120f, 180f, 360f, 800f)) {
                assertEquals(
                    resolveVideoCardLandingEdgeCompressionDp(longestEdgeDp, longestEdgeDp, 1f, layout),
                    resolveVideoCardLandingEdgeTravelDp(longestEdgeDp, layout),
                    0.0001f,
                )
            }
        }
        // 双列瓦片在 360dp 最长边下取 1.1dp 布局预算，而不是旧系统接口的固定 3dp。
        assertEquals(1.1f, resolveVideoCardLandingEdgeTravelDp(360f, stacked), 0.0001f)
    }

    @Test
    fun impulseScaleFollowsReleaseVelocityInsideFixedBounds() {
        assertEquals(1f, resolveVideoCardLandingImpulseScale(0f), 0.0001f)
        assertEquals(1.1f, resolveVideoCardLandingImpulseScale(1f), 0.0001f)
        assertEquals(1.4f, resolveVideoCardLandingImpulseScale(6f), 0.0001f)
        assertEquals(0.85f, resolveVideoCardLandingImpulseScale(-4f), 0.0001f)
        assertEquals(1f, resolveVideoCardLandingImpulseScale(Float.NaN), 0.0001f)
        var previous = -1f
        for (velocity in listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f)) {
            val scale = resolveVideoCardLandingImpulseScale(velocity)
            assertTrue(scale >= previous)
            previous = scale
        }
    }

    @Test
    fun scaledImpulseProportionallyScalesTheCompressionPeak() {
        val motion = resolveVideoCardLandingMotion(stacked)
        val spring = FloatSpringSpec(
            dampingRatio = motion.dampingRatio,
            stiffness = motion.stiffness,
        )
        val budget = resolveVideoCardLandingEdgeCompressionDp(360f, 480f, 1f, stacked)
        val baseVelocity = resolveVideoCardLandingImpulseVelocity(budget, stacked)
        var peak = 0f
        for (ms in 0..500) {
            peak = maxOf(peak, spring.getValueFromNanos(ms * 1_000_000L, 0f, 0f, baseVelocity))
        }
        assertEquals(budget, peak, 0.001f)
        val fastVelocity = baseVelocity * resolveVideoCardLandingImpulseScale(3f)
        var fastPeak = 0f
        for (ms in 0..500) {
            fastPeak = maxOf(fastPeak, spring.getValueFromNanos(ms * 1_000_000L, 0f, 0f, fastVelocity))
        }
        assertEquals(budget * resolveVideoCardLandingImpulseScale(3f), fastPeak, 0.001f)
    }
}
