package com.android.purebilibili.navigation3

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoCardSourceTrackingEnabled
import com.android.purebilibili.core.ui.transition.VideoCardSourceInstances
import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.feature.live.LocalLiveCardContainerOwner
import com.android.purebilibili.feature.live.LocalLiveCardContainerSources
import com.android.purebilibili.feature.live.rememberLiveCardContainerTransition
import com.android.purebilibili.feature.settings.SettingsContainerSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SystemWindowCardSourceUiTest {
    @get:Rule val rule = createComposeRule()

    @After
    fun clearClickSource() {
        rule.runOnIdle { CardPositionManager.clear() }
    }

    @Test
    fun liveCardRegistersSourceWithContainerMotionOnAndOff() {
        verifySourceRegistration(BiliPaiNavKey.Live(roomId = "42"))
    }

    @Test
    fun bangumiCardRegistersSourceWithContainerMotionOnAndOff() {
        verifySourceRegistration(BiliPaiNavKey.BangumiPlayer(seasonId = 42L, epId = 7L))
    }

    @Test
    fun audioBarRegistersAnchorOutsideNavigationTrackingScope() {
        val target = BiliPaiNavKey.AudioMode(sourceBvid = "BV_audio", sourceCid = 7L)
        rule.setContent {
            CompositionLocalProvider(LocalVideoCardSharedElementSourceRoute provides "source") {
                val source = requireNotNull(rememberSystemWindowCardSource(target))
                Box(
                    Modifier.size(160.dp, 64.dp)
                        .videoCardSourceAnchor(source.identity, RoundedCornerShape(50), enabled = true)
                        .onGloballyPositioned { source.coordinates = it }
                        .testTag("audio_bar")
                        .clickable { source.record(0f) },
                )
            }
        }
        rule.onNodeWithTag("audio_bar").performClick()
        rule.runOnIdle {
            assertEquals("source:BV_audio", CardPositionManager.lastClickedVideoSourceKey)
            val anchor = VideoCardSourceInstances.takeAnchor("BV_audio")
            assertNotNull(anchor)
            assertNotNull(anchor?.bounds(windowCoordinates = true))
        }
    }

    @Test
    fun partiallyClippedCardKeepsFullGeometryOnlyWhenExplicitlyAllowed() {
        rule.setContent {
            CompositionLocalProvider(LocalVideoCardSharedElementSourceRoute provides "source") {
                val source = requireNotNull(rememberSystemWindowCardSource(BiliPaiNavKey.Live("42")))
                Box(Modifier.size(80.dp).clipToBounds()) {
                    Box(
                        Modifier.requiredSize(120.dp)
                            .videoCardSourceAnchor(source.identity, RoundedCornerShape(12.dp), enabled = true)
                            .onGloballyPositioned { source.coordinates = it }
                            .testTag("clipped_card")
                            .clickable { source.record(0f) },
                    )
                }
            }
        }
        rule.onNodeWithTag("clipped_card").performClick()
        rule.runOnIdle {
            val anchor = requireNotNull(VideoCardSourceInstances.takeAnchor("live_42"))
            assertNull(anchor.bounds(windowCoordinates = true))
            val full = requireNotNull(anchor.bounds(windowCoordinates = true, allowPartial = true))
            assertEquals(CardPositionManager.lastClickedCardBounds?.width?.toInt(), full.width())
            assertNotNull(anchor.boundsInRoot(allowPartial = true))
        }
    }

    private fun verifySourceRegistration(target: BiliPaiNavKey) {
        val enabled = mutableStateOf(false)
        val identity = requireNotNull(resolveSystemWindowCardIdentity(target))
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalVideoCardSourceTrackingEnabled provides true,
                LocalVideoCardSharedElementSourceRoute provides "source",
                LocalLiveCardContainerOwner provides BiliPaiNavKey.LiveList,
                LocalLiveCardContainerSources provides remember { SettingsContainerSources() },
            ) {
                val transition = rememberLiveCardContainerTransition(
                    roomId = 0L, title = "", hostName = "", color = Color.Black,
                    cornerRadius = 12.dp, containerTarget = target,
                    enabled = enabled.value, onClick = { clicks++ },
                )
                Box(
                    Modifier.size(120.dp)
                        .then(transition.modifier)
                        .testTag("card")
                        .clickable(onClick = transition.onClick),
                )
            }
        }
        for (containerEnabled in listOf(false, true)) {
            rule.runOnIdle { enabled.value = containerEnabled }
            val previousOwner = rule.runOnIdle { CardPositionManager.lastClickedVideoPixelOwner }
            rule.onNodeWithTag("card").performClick()
            rule.runOnIdle {
                assertEquals("source:$identity", CardPositionManager.lastClickedVideoSourceKey)
                assertNotSame(previousOwner, CardPositionManager.lastClickedVideoPixelOwner)
                val anchor = VideoCardSourceInstances.takeAnchor(identity)
                assertNotNull(anchor)
                assertNotNull(anchor?.bounds(windowCoordinates = true))
            }
        }
        rule.runOnIdle { assertEquals(2, clicks) }
    }
}
