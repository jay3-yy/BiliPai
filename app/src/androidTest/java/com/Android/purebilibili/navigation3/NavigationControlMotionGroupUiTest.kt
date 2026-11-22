package com.android.purebilibili.navigation3

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope
import com.android.purebilibili.core.ui.NavigationEntryTransition
import com.android.purebilibili.core.ui.transition.LocalVideoCardSourceTrackingEnabled
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.VideoCardSourceInstances
import com.android.purebilibili.core.util.CardPositionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class NavigationControlMotionGroupUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemCompletionNotifiesSourceAndDistantGroupMemberOnceWithoutMovingAnotherGroup() {
        lateinit var source: SystemWindowCardSource
        lateinit var peer: SystemWindowCardSource
        lateinit var unrelated: SystemWindowCardSource
        rule.setContent {
            CompositionLocalProvider(
                LocalVideoCardSourceTrackingEnabled provides true,
                LocalVideoCardSharedElementSourceRoute provides "profile",
            ) {
                Column {
                    NavigationControlMotionGroup {
                        Row {
                            val first = requireNotNull(rememberSystemWindowControlSource(BiliPaiNavKey.History))
                            val second = requireNotNull(rememberSystemWindowControlSource(BiliPaiNavKey.WatchLater))
                            SideEffect { source = first; peer = second }
                            Box(Modifier.size(48.dp).then(first.anchorModifier(CircleShape)))
                            Spacer(Modifier.width(120.dp))
                            Box(Modifier.size(48.dp).then(second.anchorModifier(CircleShape)))
                        }
                    }
                    NavigationControlMotionGroup {
                        val other = requireNotNull(rememberSystemWindowControlSource(BiliPaiNavKey.Inbox))
                        SideEffect { unrelated = other }
                        Box(Modifier.size(48.dp).then(other.anchorModifier(CircleShape)))
                    }
                }
            }
        }
        rule.waitUntil { source.neighbor.eligible && peer.neighbor.eligible && unrelated.neighbor.eligible }
        lateinit var callback: (SystemWindowMotionPhase) -> Unit
        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            source.record()
            val anchor = requireNotNull(VideoCardSourceInstances.takeAnchor(source.identity))
            callback = requireNotNull(SystemWindowControlNeighbors.capture(anchor))
            callback(SystemWindowMotionPhase.RETURNING)
            assertNull(source.neighbor.groupLanding.event)
            assertNull(peer.neighbor.groupLanding.event)
            callback(SystemWindowMotionPhase.RETURNED)
        }
        rule.mainClock.advanceTimeBy(48)
        rule.waitForIdle()
        rule.runOnIdle {
            val sourceEvent = source.neighbor.groupLanding.event
            val peerEvent = peer.neighbor.groupLanding.event
            assertNotNull(sourceEvent)
            assertNotNull(peerEvent)
            assertNull(unrelated.neighbor.groupLanding.event)
            callback(SystemWindowMotionPhase.RETURNED)
            assertSame(sourceEvent, source.neighbor.groupLanding.event)
            assertSame(peerEvent, peer.neighbor.groupLanding.event)
            CardPositionManager.clear()
        }
    }

    @Test
    fun sharedGroupWaitsForSourceAndKeepsAQuickRoundTripWithoutObservingTheCoveredFrame() {
        val top = mutableStateOf(false)
        val running = mutableStateOf(false)
        lateinit var group: NavigationControlMotionGroupState
        var delivered = 0
        val entry = object : NavigationEntryTransition {
            override val isTop: Boolean get() = top.value
            override val isRunning: Boolean get() = running.value
            override val isRemoving = false
            override val relativeDepth: Float get() = if (top.value) 0f else 1f
        }
        rule.setContent {
            CompositionLocalProvider(LocalVideoDetailNavigationTransitionScope provides entry) {
                NavigationControlMotionGroup {
                    val state = requireNotNull(LocalNavigationControlMotionGroup.current)
                    SideEffect { group = state }
                    Box(Modifier.size(48.dp))
                }
            }
        }
        rule.runOnIdle {
            group.systemReturn = ControlGroupLandingDelivery { delivered++ }
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0, delivered)
            top.value = true
        }
        rule.waitUntil { delivered == 1 }
        rule.runOnIdle {
            group.systemReturn = ControlGroupLandingDelivery { delivered++ }
            top.value = false
            top.value = true
        }
        rule.waitUntil { delivered == 2 }
        rule.runOnIdle { assertEquals(2, delivered) }
    }
}
