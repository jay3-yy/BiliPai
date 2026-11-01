package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.HomeHeroCarousel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeHeroClickHandoffUiTest {
    @get:Rule val rule = createComposeRule()
    private var clicks = 0
    private var previews = 0

    @Before fun resetSource() { CardPositionManager.clear() }
    @After fun clearSource() { CardPositionManager.clear() }

    @Test
    fun semanticClickRecordsWholeCardAndFullSizeOverlayBeforeNavigation() {
        showHero()
        rule.onNodeWithTag("home_hero_video_card").performClick()
        rule.waitUntil { clicks == 1 }
    }

    @Test
    fun cancelledPressDoesNotNavigateAndNextShortClickStillWorks() {
        showHero()
        rule.onNodeWithTag("home_hero_video_card").performTouchInput { down(center); cancel() }
        rule.runOnIdle {
            assertEquals(0, clicks)
            assertNull(CardPositionManager.lastClickedNativeCardLayer)
        }
        rule.onNodeWithTag("home_hero_video_card").performTouchInput { click(center) }
        rule.waitUntil { clicks == 1 }
    }

    @Test
    fun disabledSharedTransitionNavigatesWithoutSourceCapture() {
        showHero(sharedEnabled = false)
        rule.onNodeWithTag("home_hero_video_card").performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun longPressOpensPreviewWithoutVideoNavigation() {
        showHero()
        rule.onNodeWithTag("home_hero_video_card").performTouchInput { longClick(center) }
        rule.runOnIdle {
            assertEquals(1, previews)
            assertEquals(0, clicks)
            assertNull(CardPositionManager.lastClickedNativeCardLayer)
        }
    }

    private fun showHero(sharedEnabled: Boolean = true) {
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalSharedTransitionEnabled provides sharedEnabled,
                    LocalPageImageLoadingAllowed provides true,
                    LocalVideoCardSharedElementSourceRoute provides "home:recommend",
                ) {
                    Box(Modifier.width(360.dp)) {
                        HomeHeroCarousel(
                            videos = listOf(VideoItem(bvid = "BVhero", cid = 7, title = "横幅来源")),
                            autoplayEnabled = false,
                            onGetPreviewUrl = { _, _ -> error("Preview is disabled") },
                            onLongPress = { previews++ },
                            onVideoClick = {
                                assertEquals("BVhero", it.bvid)
                                assertEquals(7L, it.cid)
                                val card = CardPositionManager.lastClickedNativeCardLayer
                                val overlay = CardPositionManager.lastClickedNativeCoverOverlayLayer
                                if (sharedEnabled) {
                                    val cardSize = requireNotNull(card).size
                                    val overlaySize = requireNotNull(overlay).size
                                    assertTrue(cardSize.width > 1 && cardSize.height > 1)
                                    assertEquals(cardSize, overlaySize)
                                } else {
                                    assertNull(card)
                                    assertNull(overlay)
                                }
                                clicks++
                            },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }
}
