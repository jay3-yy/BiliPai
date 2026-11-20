package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationReturnLandingTest {
    @Test
    fun landingPressureCompressesAndReleasesWithoutStartingASecondBounce() {
        val animation = TargetBasedAnimation(
            animationSpec = navigationReturnLandingPressureSpec(),
            typeConverter = Float.VectorConverter,
            initialValue = 0f,
            targetValue = 0f,
        )
        val frameValues = (0..270).map { animation.getValueFromNanos(it * 1_000_000L) }
        val mostCompressed = frameValues.indexOf(frameValues.max())
        val mostReleased = frameValues.indexOf(frameValues.min())

        assertTrue(mostCompressed in 1 until mostReleased)
        assertTrue(mostReleased < frameValues.lastIndex)
        assertTrue(frameValues.take(mostCompressed + 1).zipWithNext().all { (a, b) -> a <= b })
        assertTrue(frameValues.subList(mostCompressed, mostReleased + 1).zipWithNext().all { (a, b) -> a >= b })
        assertTrue(frameValues.drop(mostReleased).zipWithNext().all { (a, b) -> a <= b && b <= 0f })
        assertEquals(0f, frameValues.last(), 0f)
    }

    @Test
    fun compactControlAndWideCardLimitTheirPhysicalEdgeTravel() {
        fun edgeTravel(width: Float, pressure: Float): Float =
            (1f - resolveNavigationReturnLandingScale(width, edgeTravelLimitPx = 1.5f, pressure = pressure)) *
                width / 2f

        assertEquals(1.2f, edgeTravel(40f, 1f), 0.01f)
        assertEquals(1.5f, edgeTravel(400f, 1f), 0.01f)
        assertEquals(-0.105f, edgeTravel(400f, -0.07f), 0.01f)
        assertEquals(0f, edgeTravel(400f, 0f), 0f)
    }

    @Test
    fun finishingAnInterruptedReturnCannotDiscardTheNextReturn() {
        val landing = NavigationReturnLanding()
        landing.request()
        val first = checkNotNull(landing.event)
        landing.request()
        val second = checkNotNull(landing.event)

        landing.complete(first)
        assertSame(second, landing.event)
        landing.complete(second)
        assertNull(landing.event)
    }
}
