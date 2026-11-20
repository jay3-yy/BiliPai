package com.android.purebilibili.feature.home

import com.android.purebilibili.navigation3.SystemWindowMotionPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeaderReturnTest {
    @Test
    fun waitingForFirstVisibleFrameDoesNotConsumeTheLanding() {
        val event = HomeHeaderReturn(HomeHeaderControl.SEARCH, landingFromSystemWindow = true)
        event.updateSystemPhase(SystemWindowMotionPhase.RETURNED)
        assertTrue(event.canLand(navigationRunning = false))
        assertFalse(event.canLand(navigationRunning = true))
        assertTrue(event.canLand(navigationRunning = false))
        assertTrue(event.consumeWhenLanded(navigationRunning = false))
        assertFalse(event.canLand(navigationRunning = false))
    }

    @Test
    fun playedLandingCannotReplayAfterSharedBoundsSettleAgain() {
        val event = HomeHeaderReturn(HomeHeaderControl.END_ACTION)
        assertTrue(event.consumeWhenLanded(navigationRunning = false))
        assertFalse(event.consumeWhenLanded(navigationRunning = true))
        assertFalse(event.consumeWhenLanded(navigationRunning = false))
    }

    @Test
    fun systemReturnCompressesOnlyAfterCompletionAndOnlyOnce() {
        val event = HomeHeaderReturn(HomeHeaderControl.SEARCH, landingFromSystemWindow = true)
        assertFalse(event.consumeWhenLanded(navigationRunning = false))
        event.updateSystemPhase(SystemWindowMotionPhase.RETURNING)
        assertFalse(event.consumeWhenLanded(navigationRunning = false))
        event.updateSystemPhase(SystemWindowMotionPhase.RETURNED)
        assertTrue(event.consumeWhenLanded(navigationRunning = false))
        assertFalse(event.consumeWhenLanded(navigationRunning = false))
    }

    @Test
    fun cancelledReturnDoesNotCauseALandingCompression() {
        val event = HomeHeaderReturn(HomeHeaderControl.AVATAR, landingFromSystemWindow = true)
        event.updateSystemPhase(SystemWindowMotionPhase.RETURNING)
        event.updateSystemPhase(SystemWindowMotionPhase.RETURN_CANCELLED)
        event.updateSystemPhase(SystemWindowMotionPhase.RETURNED)
        assertFalse(event.consumeWhenLanded(navigationRunning = false))
    }

    @Test
    fun completedSystemReturnCanLandWhenThePlatformOnlyReportsCompletion() {
        val event = HomeHeaderReturn(HomeHeaderControl.AVATAR, landingFromSystemWindow = true)
        event.updateSystemPhase(SystemWindowMotionPhase.RETURNED)
        assertFalse(event.consumeWhenLanded(navigationRunning = true))
        assertTrue(event.consumeWhenLanded(navigationRunning = false))
        assertFalse(event.consumeWhenLanded(navigationRunning = false))
    }

    @Test
    fun sharedReturnWaitsForNavigationAndBoundsToSettleWithoutConsumingTheEvent() {
        val event = HomeHeaderReturn(HomeHeaderControl.END_ACTION)
        assertFalse(event.consumeWhenLanded(navigationRunning = true))
        assertTrue(event.consumeWhenLanded(navigationRunning = false))
        assertFalse(event.consumeWhenLanded(navigationRunning = false))
    }
}
