package com.android.purebilibili.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.store.PlayerProgressPlacement
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.video.ui.overlay.BottomControlBar
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.overlay.TopControlBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControlsUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fullscreenMaterialSwitch_preservesPrimaryActionOrderAndPortraitSettings() {
        val glass = mutableStateOf(false)
        val fullscreen = mutableStateOf(true)
        var sends = 0
        var settings = 0
        composeTestRule.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current.density * maxWidth.value / 960f
                CompositionLocalProvider(
                    LocalDensity provides Density(density),
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass.value),
                ) {
                    MaterialTheme {
                        Box(Modifier.fillMaxWidth().height(240.dp)) {
                            if (fullscreen.value) {
                                TopControlBar(
                                    title = "测试视频",
                                    isFullscreen = true,
                                    likeStatusKnown = false,
                                    showCurrentTime = false,
                                    showInteractiveActions = false,
                                    onBack = {},
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                            }
                            BottomControlBar(
                                isPlaying = false,
                                isFullscreen = fullscreen.value,
                                viewportWidthDpOverride = 960,
                                onPlayPauseClick = {},
                                onSeek = {},
                                onToggleFullscreen = {},
                                onDanmakuInputClick = { sends++ },
                                onDanmakuSettingsClick = { settings++ },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
        repeat(2) { index ->
            composeTestRule.runOnIdle { glass.value = index == 1 }
            assertFullscreenPrimaryActionOrder()
            composeTestRule.onAllNodesWithContentDescription("弹幕设置").assertCountEquals(1)
            composeTestRule.onNodeWithTag("player_danmaku_settings").performClick()
            composeTestRule.onNodeWithTag("player_danmaku_send").performClick()
            composeTestRule.runOnIdle {
                assertEquals(index + 1, sends)
                assertEquals(index + 1, settings)
            }
        }
        composeTestRule.runOnIdle { fullscreen.value = false }
        listOf(true, false).forEachIndexed { index, liquidGlassEnabled ->
            composeTestRule.runOnIdle { glass.value = liquidGlassEnabled }
            // Inline play/pause sits at the bottom-left, before the danmaku actions.
            val play = composeTestRule.onNodeWithTag("player_play_pause").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val settingsBounds = composeTestRule.onNodeWithTag("player_danmaku_settings")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Inline play/pause must stay left of danmaku actions", play.right <= settingsBounds.left)
            composeTestRule.onNodeWithTag("player_danmaku_settings").performClick()
            composeTestRule.runOnIdle {
                assertEquals(2, sends)
                assertEquals(index + 3, settings)
            }
        }
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
    fun nativeFullscreen_rightAlignedFixedToolsAndResizeKeepPrimaryActionsUsable() {
        assertFullscreenResizeKeepsActionsReachable(liquidGlassEnabled = false)
    }

    @Test
    fun glassFullscreen_rightAlignedFixedToolsAndResizeKeepPrimaryActionsUsable() {
        assertFullscreenResizeKeepsActionsReachable(liquidGlassEnabled = true)
    }

    private fun assertFullscreenResizeKeepsActionsReachable(liquidGlassEnabled: Boolean) {
        val widthFraction = mutableStateOf(1f)
        val fontScale = mutableStateOf(1f)
        val calls = IntArray(6)
        var previousGapPx: Float? = null
        composeTestRule.setContent {
            // Keep a 960dp test viewport on any host display; shrink its content to
            // 624dp and 384dp without depending on the device's orientation or size.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current.density * maxWidth.value / 960f
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale.value),
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = liquidGlassEnabled),
                ) {
                    MaterialTheme {
                        BottomControlBar(
                            isPlaying = false,
                            progress = PlayerProgress(current = 30_000, duration = 120_000),
                            isFullscreen = true,
                            viewportWidthDpOverride = 960,
                            currentQualityLabel = "1080P",
                            hasEpisodeEntry = true,
                            onPlayPauseClick = { calls[0]++ },
                            onDanmakuToggle = { calls[1]++ },
                            onDanmakuSettingsClick = { calls[2]++ },
                            onDanmakuInputClick = { calls[3]++ },
                            onToggleFullscreen = { calls[4]++ },
                            onQualityClick = { calls[5]++ },
                            onSeek = {},
                            modifier = Modifier.fillMaxWidth(widthFraction.value).height(180.dp),
                        )
                    }
                }
            }
        }
        // Fullscreen labels ignore system font scale: gaps stay fixed across widths and scales.
        listOf(1f to 1f, 0.65f to 1f, 0.65f to 1.5f, 0.4f to 1.5f).forEachIndexed { index, (width, scale) ->
            composeTestRule.runOnIdle {
                widthFraction.value = width
                fontScale.value = scale
            }
            assertFullscreenPrimaryActionOrder()
            if (liquidGlassEnabled) {
                composeTestRule.onNodeWithTag("player_time_viewport").assertIsDisplayed()
            }
            if (index < 3) {
                composeTestRule.onNodeWithText("1080P").performScrollTo()
                val tools = listOf("1080P", "分集", "倍速").map { label ->
                    composeTestRule.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                }
                val gapPx = tools[1].left - tools[0].right
                assertTrue("Tool gaps must remain positive", gapPx > 0f)
                assertEquals(gapPx, tools[2].left - tools[1].right, 1f)
                previousGapPx?.let { assertEquals(it, gapPx, 1f) }
                previousGapPx = gapPx
                if (index == 0 || index == 2) {
                    // Unused width belongs before the tools, not between them or after "更多".
                    val send = composeTestRule.onNodeWithTag("player_danmaku_send").fetchSemanticsNode().boundsInRoot
                    val more = composeTestRule.onNodeWithText("更多").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val exit = composeTestRule.onNodeWithTag("player_fullscreen_toggle").fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        "Fullscreen tools must stay right-aligned at content width",
                        exit.left - more.right < tools[0].left - send.right,
                    )
                }
            }
            listOf(
                "player_play_pause", "player_danmaku_toggle", "player_danmaku_settings",
                "player_danmaku_send", "player_fullscreen_toggle",
            ).forEach { tag ->
                val action = composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
                if (tag == "player_fullscreen_toggle") {
                    // This control consumes pointer gestures rather than exposing OnClick semantics.
                    action.performTouchInput { click() }
                } else {
                    action.performClick()
                }
            }
            composeTestRule.onNodeWithText("1080P").performScrollTo().assertIsDisplayed().performClick()
            composeTestRule.onNodeWithText("分集").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("更多").performScrollTo().assertIsDisplayed()
            // Scrolling tools must not displace the primary actions.
            listOf(
                "player_play_pause", "player_danmaku_toggle", "player_danmaku_settings",
                "player_danmaku_send", "player_fullscreen_toggle",
            ).forEach { tag -> composeTestRule.onNodeWithTag(tag).assertIsDisplayed() }
            composeTestRule.runOnIdle {
                calls.forEach { count -> assertEquals(index + 1, count) }
            }
        }
    }

    private fun assertFullscreenPrimaryActionOrder() {
        val bounds = listOf(
            "player_play_pause", "player_danmaku_toggle",
            "player_danmaku_settings", "player_danmaku_send",
        ).map { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("Fullscreen actions must stay in reading order", left.center.x < right.center.x)
        }
    }

    private fun assertProgressIsAboveControls() {
        val progress = composeTestRule.onNodeWithTag("player_progress").fetchSemanticsNode().boundsInRoot
        val controls = composeTestRule.onNodeWithTag("player_control_row").fetchSemanticsNode().boundsInRoot
        assertTrue(progress.bottom <= controls.top)
    }
}
