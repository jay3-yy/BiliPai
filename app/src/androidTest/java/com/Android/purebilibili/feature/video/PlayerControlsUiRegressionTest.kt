package com.android.purebilibili.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.store.PlayerProgressPlacement
import com.android.purebilibili.feature.video.ui.overlay.BottomControlBar
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.overlay.TopControlBar
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControlsUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fullscreenGlassSwitch_routesBottomActionsAndPreservesPortraitControls() {
        val glass = mutableStateOf(false)
        val fullscreen = mutableStateOf(true)
        var sends = 0
        var settings = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass.value)) {
                MaterialTheme {
                    BottomControlBar(
                        isPlaying = false,
                        isFullscreen = fullscreen.value,
                        viewportWidthDpOverride = 960,
                        fullscreenDanmakuActionsInDedicatedDocks = fullscreen.value,
                        onPlayPauseClick = {},
                        onSeek = {},
                        onToggleFullscreen = {},
                        onDanmakuInputClick = { sends++ },
                        onDanmakuSettingsClick = { settings++ },
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("player_play_pause").assertExists()
        composeTestRule.onNodeWithTag("player_danmaku_settings").performClick()
        composeTestRule.onNodeWithTag("player_danmaku_send").performClick()
        composeTestRule.runOnIdle {
            assertTrue(sends == 1 && settings == 1)
            glass.value = true
        }
        composeTestRule.onNodeWithTag("player_play_pause").assertDoesNotExist()
        composeTestRule.onNodeWithTag("player_danmaku_settings").assertDoesNotExist()
        composeTestRule.onNodeWithTag("player_danmaku_send").performClick()
        composeTestRule.runOnIdle {
            assertTrue(sends == 2 && settings == 1)
            fullscreen.value = false
        }
        composeTestRule.onNodeWithTag("player_play_pause").assertDoesNotExist()
        composeTestRule.onNodeWithTag("player_danmaku_settings").performClick()
        composeTestRule.runOnIdle { glass.value = false }
        composeTestRule.onNodeWithTag("player_play_pause").assertDoesNotExist()
        composeTestRule.onNodeWithTag("player_danmaku_settings").performClick()
        composeTestRule.runOnIdle { assertTrue(sends == 2 && settings == 3) }
    }

    @Test
    fun glassFullscreenTopDanmakuAction_opensSettings() {
        var settings = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = true)) {
                MaterialTheme {
                    TopControlBar(
                        title = "测试视频",
                        isFullscreen = true,
                        likeStatusKnown = false,
                        showCurrentTime = false,
                        showInteractiveActions = false,
                        onBack = {},
                        onDanmakuSettingsClick = { settings++ },
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("player_top_danmaku_settings").performClick()
        composeTestRule.runOnIdle { assertTrue(settings == 1) }
    }

    @Test
    fun castButton_respectsVisibilitySetting() {
        val castVisible = mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme {
                TopControlBar(
                    title = "测试视频",
                    isFullscreen = false,
                    likeStatusKnown = false,
                    showCurrentTime = false,
                    showInteractiveActions = false,
                    showCastButton = castVisible.value,
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("投屏").assertDoesNotExist()

        composeTestRule.runOnIdle { castVisible.value = true }
        composeTestRule.onNodeWithContentDescription("投屏").assertExists()
    }

    @Test
    fun likeButton_unknownStateKeepsDisabledSlotUntilKnown() {
        val known = mutableStateOf(false)
        var clicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                TopControlBar(
                    title = "测试视频",
                    isFullscreen = true,
                    statusBarVisible = false,
                    showCurrentTime = false,
                    likeStatusKnown = known.value,
                    isLiked = true,
                    onLikeClick = { clicks += 1 },
                    onBack = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("点赞状态尚未确认")
            .assertExists().assertIsNotEnabled().performClick()
        composeTestRule.runOnIdle {
            assertTrue(clicks == 0)
            known.value = true
        }
        composeTestRule.onNodeWithContentDescription("点赞").assertIsEnabled().performClick()
        composeTestRule.runOnIdle { assertTrue(clicks == 1) }
    }

    @Test
    fun progressPlacement_movesProgressAcrossControlRow() {
        val placement = mutableStateOf(PlayerProgressPlacement.ABOVE_CONTROLS)
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 800.dp, height = 180.dp)
                        .background(Color.Black)
                ) {
                    BottomControlBar(
                        isPlaying = true,
                        progress = PlayerProgress(current = 30_000, duration = 120_000),
                        isFullscreen = false,
                        progressPlacement = placement.value,
                        onPlayPauseClick = {},
                        onSeek = {},
                        onToggleFullscreen = {}
                    )
                }
            }
        }
        assertProgressIsAboveControls()

        composeTestRule.runOnIdle { placement.value = PlayerProgressPlacement.BOTTOM_EDGE }
        val progress = composeTestRule.onNodeWithTag("player_progress").fetchSemanticsNode().boundsInRoot
        val controls = composeTestRule.onNodeWithTag("player_control_row").fetchSemanticsNode().boundsInRoot
        assertTrue(progress.top >= controls.bottom)
    }

    @Test
    fun progressProviders_updatePausedTimeAndDurationWithSeekOverride() {
        val progress = mutableStateOf(PlayerProgress(current = 30_000, duration = 120_000))
        val seekOverride = mutableStateOf<Long?>(null)
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 800.dp, height = 180.dp)) {
                    BottomControlBar(
                        isPlaying = false,
                        progressProvider = { progress.value },
                        seekPositionProvider = { seekOverride.value ?: progress.value.current },
                        isFullscreen = false,
                        onPlayPauseClick = {},
                        onSeek = {},
                        onToggleFullscreen = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("00:30 / 02:00").assertIsDisplayed()

        // Progress must remain live even though neither the host nor the play button changes.
        composeTestRule.runOnIdle {
            progress.value = PlayerProgress(current = 45_000, duration = 180_000, buffered = 90_000)
        }
        composeTestRule.onNodeWithText("00:45 / 03:00").assertIsDisplayed()

        composeTestRule.runOnIdle { seekOverride.value = 91_000L }
        composeTestRule.onNodeWithText("01:31 / 03:00").assertIsDisplayed()

        composeTestRule.runOnIdle {
            progress.value = progress.value.copy(current = 46_000)
        }
        composeTestRule.onNodeWithText("01:31 / 03:00").assertIsDisplayed()

        composeTestRule.runOnIdle { seekOverride.value = null }
        composeTestRule.onNodeWithText("00:46 / 03:00").assertIsDisplayed()
    }

    @Test
    fun fullscreenControls_keepPrimaryActionsPinnedAndSpacingStableWhenWidthChanges() {
        val widthFraction = mutableStateOf(1f)
        val fontScale = mutableStateOf(1f)
        composeTestRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale.value),
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = false),
            ) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        BottomControlBar(
                            isPlaying = false,
                            progress = PlayerProgress(current = 30_000, duration = 120_000),
                            isFullscreen = true,
                            viewportWidthDpOverride = 960,
                            currentQualityLabel = "1080P",
                            hasEpisodeEntry = true,
                            onPlayPauseClick = {},
                            onSeek = {},
                            onToggleFullscreen = {},
                            modifier = Modifier.align(Alignment.BottomStart)
                                .fillMaxWidth(widthFraction.value).height(180.dp),
                        )
                    }
                }
            }
        }
        fun assertPinnedControls() {
            val row = composeTestRule.onNodeWithTag("player_control_row").fetchSemanticsNode().boundsInRoot
            val play = composeTestRule.onNodeWithTag("player_play_pause").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val toggle = composeTestRule.onNodeWithTag("player_danmaku_toggle").assertIsDisplayed()
                .fetchSemanticsNode()
            val settings = composeTestRule.onNodeWithTag("player_danmaku_settings").assertIsDisplayed()
                .fetchSemanticsNode()
            val send = composeTestRule.onNodeWithTag("player_danmaku_send").assertIsDisplayed()
                .fetchSemanticsNode()
            val exit = composeTestRule.onNodeWithTag("player_fullscreen_toggle").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue(play.left >= row.left)
            assertTrue(toggle.boundsInRoot.left >= play.right)
            assertTrue(settings.boundsInRoot.left >= toggle.boundsInRoot.right)
            assertTrue(send.boundsInRoot.left >= settings.boundsInRoot.right)
            assertTrue(send.boundsInRoot.right <= exit.left)
            listOf(toggle, settings, send).forEach { node ->
                assertTrue(node.boundsInRoot.width + 1f >= node.layoutInfo.width)
            }
            assertTrue(exit.right <= row.right)
        }
        fun actionGap(): Float {
            val ratio = composeTestRule.onNodeWithText("适应").getUnclippedBoundsInRoot()
            val quality = composeTestRule.onNodeWithText("1080P").getUnclippedBoundsInRoot()
            val episode = composeTestRule.onNodeWithText("分集").getUnclippedBoundsInRoot()
            org.junit.Assert.assertEquals(
                (quality.left - ratio.right).value,
                (episode.left - quality.right).value,
                0.5f,
            )
            return (quality.left - ratio.right).value
        }
        assertPinnedControls()
        val wideGap = actionGap()
        composeTestRule.runOnIdle { widthFraction.value = 0.65f }
        assertPinnedControls()
        org.junit.Assert.assertEquals(wideGap, actionGap(), 0.5f)
        composeTestRule.onNodeWithText("更多").performScrollTo().assertIsDisplayed()
        assertPinnedControls()
        composeTestRule.runOnIdle { fontScale.value = 1.5f }
        assertPinnedControls()
        assertTrue(actionGap() > wideGap)
        composeTestRule.runOnIdle { widthFraction.value = 0.4f }
        assertPinnedControls()
    }

    private fun assertProgressIsAboveControls() {
        val progress = composeTestRule.onNodeWithTag("player_progress").fetchSemanticsNode().boundsInRoot
        val controls = composeTestRule.onNodeWithTag("player_control_row").fetchSemanticsNode().boundsInRoot
        assertTrue(progress.bottom <= controls.top)
    }
}
