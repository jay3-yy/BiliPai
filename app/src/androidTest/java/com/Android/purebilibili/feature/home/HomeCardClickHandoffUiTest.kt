package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.LocalVideoCardLongPressEnabled
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.components.cards.StoryVideoCard
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardAuxiliaryWorkAllowed
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardScrolling
import com.android.purebilibili.feature.home.components.cards.LocalHomeScrollTickProvider
import com.android.purebilibili.feature.home.components.cards.LocalHomeCardVisibility
import com.android.purebilibili.feature.home.components.cards.VideoCardVisibilityState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeCardClickHandoffUiTest {
    @get:Rule val rule = createComposeRule()
    private var clicks = 0
    private var previews = 0

    @Before fun resetSource() { CardPositionManager.clear() }
    @After fun clearSource() { CardPositionManager.clear() }

    @Test
    fun accessibilityClickWaitsForBothSourceLayersBeforeNavigationCallback() {
        showCard(sharedHomeVisibility = true)
        rule.onNodeWithTag("home_video_cover").performClick()
        rule.waitUntil { clicks == 1 }
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun fastTouchAndCancelledPressUseTheSameSourceHandoff() {
        showCard()
        rule.onNodeWithTag("home_video_cover").performTouchInput {
            down(center)
            cancel()
        }
        rule.runOnIdle { assertEquals(0, clicks) }
        rule.onNodeWithTag("home_video_cover").performTouchInput { click(center) }
        rule.waitUntil { clicks == 1 }
    }

    @Test
    fun disabledTransitionNavigatesWithoutPreparingSourceLayers() {
        showCard(transitionEnabled = false)
        rule.onNodeWithTag("home_video_cover").performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun storyAccessibilityClickWaitsForBothSourceLayersAndKeepsCid() {
        showCard(story = true, sharedHomeVisibility = true)
        rule.onNodeWithTag("home_story_video_card").performClick()
        rule.waitUntil { clicks == 1 }
    }

    @Test
    fun storyFastTouchAndCancelledPressShareTheSourceHandoff() {
        showCard(story = true)
        rule.onNodeWithTag("home_story_video_card").performTouchInput { down(center); cancel() }
        rule.runOnIdle { assertEquals(0, clicks) }
        rule.onNodeWithTag("home_story_video_card").performTouchInput { click(center) }
        rule.waitUntil { clicks == 1 }
    }

    @Test
    fun storyDisabledTransitionDoesNotAllocateSnapshotLayers() {
        showCard(transitionEnabled = false, story = true)
        rule.onNodeWithTag("home_story_video_card").performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun storySharedTransitionPermissionOffSkipsSourcePreparation() {
        showCard(story = true, sharedEnabled = false)
        rule.onNodeWithTag("home_story_video_card").performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun storyLongPressStillOpensPreviewWithoutStartingVideoNavigation() {
        showCard(story = true, longPressEnabled = true)
        rule.onNodeWithTag("home_story_video_card").performTouchInput { longClick(center) }
        rule.runOnIdle {
            assertEquals(1, previews)
            assertEquals(0, clicks)
            assertNull(CardPositionManager.lastClickedNativeCardLayer)
        }
    }

    private fun showCard(
        transitionEnabled: Boolean = true,
        story: Boolean = false,
        sharedEnabled: Boolean = true,
        longPressEnabled: Boolean = false,
        sharedHomeVisibility: Boolean = false,
    ) {
        val scrolling = mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                val visibility = remember { VideoCardVisibilityState() }
                CompositionLocalProvider(
                    LocalHomeScrollTickProvider provides { 0 },
                    LocalHomeCardScrolling provides scrolling,
                    LocalHomeCardAuxiliaryWorkAllowed provides false,
                    LocalHomeCardVisibility provides if (sharedHomeVisibility) visibility else null,
                    LocalPageImageLoadingAllowed provides (!sharedHomeVisibility || visibility.visible),
                    LocalSharedTransitionEnabled provides sharedEnabled,
                    LocalVideoCardLongPressEnabled provides longPressEnabled,
                ) {
                    Box(Modifier.width(240.dp)) {
                        val video = VideoItem(
                            bvid = "BVhandoff", cid = 7, title = "来源交接",
                            owner = Owner(name = "UP"), duration = 180, pubdate = 1_700_000_000L,
                        )
                        val onClick: (String, Long) -> Unit = { bvid, cid ->
                            assertEquals("BVhandoff", bvid)
                            assertEquals(7L, cid)
                            val card = CardPositionManager.lastClickedNativeCardLayer
                            val overlay = CardPositionManager.lastClickedNativeCoverOverlayLayer
                            if (transitionEnabled && sharedEnabled) {
                                assertNotNull(card)
                                assertNotNull(overlay)
                                assertTrue(requireNotNull(card).size.width > 1)
                                assertTrue(requireNotNull(overlay).size.width > 1)
                            } else {
                                assertNull(card)
                                assertNull(overlay)
                            }
                            clicks++
                        }
                        if (story) {
                            StoryVideoCard(
                                video = video,
                                animationEnabled = false,
                                transitionEnabled = transitionEnabled,
                                sharedElementSourceRoute = "home:recommend",
                                showPublishTime = false,
                                onLongClick = if (longPressEnabled) ({ previews++ }) else null,
                                onClick = onClick,
                            )
                        } else {
                            ElegantVideoCard(
                                video = video,
                                index = 0,
                                animationEnabled = false,
                                transitionEnabled = transitionEnabled,
                                sharedElementSourceRoute = "home:recommend",
                                showPublishTime = false,
                                onClick = onClick,
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }
}
