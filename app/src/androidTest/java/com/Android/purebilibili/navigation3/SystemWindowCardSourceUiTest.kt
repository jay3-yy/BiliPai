package com.android.purebilibili.navigation3

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
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
import org.junit.Assert.assertTrue
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
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
    fun clippedDynamicCardSuppliesFullPixelsAndContainerOrigin() {
        verifySourceRegistration(BiliPaiNavKey.DynamicDetail("42"), partiallyClipped = true)
    }

    @Test
    fun clippedLiveCardSuppliesFullPixelsAndContainerOrigin() {
        verifySourceRegistration(BiliPaiNavKey.Live(roomId = "42"), partiallyClipped = true)
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
                val source = requireNotNull(rememberSystemWindowCardSource(BiliPaiNavKey.Live(roomId = "42")))
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

    private fun verifySourceRegistration(target: BiliPaiNavKey, partiallyClipped: Boolean = false) {
        val enabled = mutableStateOf(false)
        val identity = requireNotNull(resolveSystemWindowCardIdentity(target))
        val sources = SettingsContainerSources()
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalVideoCardSourceTrackingEnabled provides true,
                LocalVideoCardSharedElementSourceRoute provides "source",
                LocalLiveCardContainerOwner provides BiliPaiNavKey.LiveList,
                LocalLiveCardContainerSources provides sources,
            ) {
                val transition = rememberLiveCardContainerTransition(
                    roomId = 0L, title = "", hostName = "", color = Color.Black,
                    cornerRadius = 12.dp, containerTarget = target,
                    enabled = enabled.value, onClick = { clicks++ },
                )
                Box(Modifier.size(if (partiallyClipped) 80.dp else 120.dp)
                    .clipToBounds()
                    .onGloballyPositioned {
                        sources.hostOrigin = it.boundsInRoot().topLeft
                        sources.hostSize = it.size
                    }
                ) {
                    Box(
                        Modifier.requiredSize(120.dp)
                            .then(transition.modifier)
                            .testTag("card")
                            .clickable(onClick = transition.onClick),
                    )
                }
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
                val fullBounds = requireNotNull(anchor?.bounds(windowCoordinates = true, allowPartial = true))
                if (partiallyClipped) assertNull(anchor?.bounds(windowCoordinates = true))
                else assertNotNull(anchor?.bounds(windowCoordinates = true))
                val layer = requireNotNull(CardPositionManager.nativeCardLayerForOwner(
                    CardPositionManager.lastClickedVideoPixelOwner,
                ))
                assertEquals(fullBounds.width(), layer.size.width)
                assertEquals(fullBounds.height(), layer.size.height)
                val returnSession = VideoCardTransitionSession(
                    bvid = identity, sourceRoute = "source", sourceKey = "source:$identity",
                    cardBounds = CardPositionManager.lastClickedCardBounds, coverBounds = null,
                    sourceCornerDp = 12, cardSourceDirection = BiliPaiNavCardSourceDirection.NONE,
                    coverIdentity = null, cardFullyVisible = !partiallyClipped, isSingleColumnCard = false,
                    sourceLayout = VideoCardSourceLayout.COVER_ONLY, sourceChromeSnapshot = null,
                    sourceAnchor = anchor, hasCompleteSourcePixels = true,
                )
                // 落位后全局录制引用可释放；返回能力必须保留在该次会话，不重新要求完整可见。
                CardPositionManager.clearNativeVideoCardLayers()
                val refreshed = returnSession.refreshedForReturn()
                assertTrue(refreshed.hasUsableSourceGeometry)
                assertEquals(returnSession.cardBounds, refreshed.cardBounds)
                assertEquals(!partiallyClipped, refreshed.cardFullyVisible)
                if (partiallyClipped) {
                    assertNull(returnSession.copy(hasCompleteSourcePixels = false)
                        .refreshedForReturn().cardBounds)
                }
                if (containerEnabled) {
                    val captured = requireNotNull(sources.sources[target])
                    assertEquals(
                        requireNotNull(anchor?.boundsInRoot(allowPartial = true)).translate(-sources.hostOrigin),
                        captured.bounds,
                    )
                }
            }
        }
        rule.runOnIdle { assertEquals(2, clicks) }
    }
}
