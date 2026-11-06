package com.Android.purebilibili.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.video.ui.overlay.BottomControlBar
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.screen.ContinuousPlayerOrientationRequest
import com.android.purebilibili.feature.video.screen.ContinuousPlayerTransitionEvent
import com.android.purebilibili.feature.video.screen.ContinuousPlayerTransitionPhase
import com.android.purebilibili.feature.video.screen.reduceContinuousPlayerTransition
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class BottomControlBarUiRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun seekPreviewStaysAboveTrackWithoutMovingControlsAcrossOrientationsAndMaterials() {
        val scrubbing = mutableStateOf(false)
        val landscape = mutableStateOf(false)
        val glass = mutableStateOf(false)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val withImage = mutableStateOf(true)
        val shot = com.android.purebilibili.data.model.response.VideoshotData(
            image = listOf(""), index = listOf(0L),
        )
        rule.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current.density * maxWidth.value / 900f
                CompositionLocalProvider(
                    LocalDensity provides Density(density, 1f),
                    LocalAppUiStyle provides style.value,
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass.value),
                ) {
                    MaterialTheme { MiuixTheme {
                        Box(Modifier.fillMaxSize()) {
                            BottomControlBar(
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .width(if (landscape.value) 840.dp else 320.dp),
                                progress = PlayerProgress(52_000, 269_000),
                                isFullscreen = landscape.value,
                                viewportIsLandscape = landscape.value,
                                viewportWidthDpOverride = if (landscape.value) 840 else 320,
                                isSeekScrubbing = scrubbing.value,
                                videoshotData = shot.takeIf { withImage.value },
                                onSeek = {}, onToggleFullscreen = {},
                            )
                        }
                    } }
                }
            }
        }
        for (uiStyle in AppUiStyle.entries) {
            for (wide in listOf(false, true)) {
                for (useGlass in listOf(false, true)) {
                    rule.runOnIdle {
                        style.value = uiStyle; landscape.value = wide
                        glass.value = useGlass; scrubbing.value = false
                    }
                    val controls = rule.onNodeWithTag("player_fullscreen_toggle")
                        .fetchSemanticsNode().boundsInRoot
                    for (image in listOf(false, true)) {
                        rule.runOnIdle { withImage.value = image; scrubbing.value = true }
                        val preview = rule.onNodeWithTag("player_seek_preview")
                            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                        val progress = rule.onNodeWithTag("player_progress")
                            .fetchSemanticsNode().boundsInRoot
                        val updatedControls = rule.onNodeWithTag("player_fullscreen_toggle")
                            .fetchSemanticsNode().boundsInRoot
                        assertTrue("Preview must end above track", preview.bottom <= progress.top)
                        assertEquals(controls.top, updatedControls.top, 1f)
                        assertEquals(controls.bottom, updatedControls.bottom, 1f)
                    }
                }
            }
        }
    }

    @Test
    fun referenceGateAndEpisodeAvailability_doNotDependOnMaterialOrOrientation() {
        val width = mutableStateOf(499)
        val fullscreen = mutableStateOf(false)
        val landscape = mutableStateOf(false)
        val episodes = mutableStateOf(true)
        val glass = mutableStateOf(false)
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        rule.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current.density * maxWidth.value / 900f
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale = 1.5f),
                    LocalAppUiStyle provides style.value,
                    LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = glass.value),
                ) {
                    MaterialTheme { MiuixTheme {
                        Box(Modifier.width(width.value.dp)) {
                            BottomControlBar(
                                progress = PlayerProgress(500, 3000),
                                isFullscreen = fullscreen.value,
                                viewportIsLandscape = landscape.value,
                                viewportWidthDpOverride = width.value,
                                hasEpisodeEntry = episodes.value,
                                currentQualityLabel = "720P",
                                onSeek = {}, onToggleFullscreen = {},
                            )
                        }
                    } }
                }
            }
        }
        for (uiStyle in AppUiStyle.entries) {
            for (isLandscape in listOf(false, true)) {
                for (isFullscreen in listOf(false, true)) {
                    for (widthDp in listOf(320, 499, 500, 840)) {
                        rule.runOnIdle {
                            style.value = uiStyle; landscape.value = isLandscape
                            fullscreen.value = isFullscreen; width.value = widthDp
                            episodes.value = true; glass.value = false
                        }
                        rule.onNodeWithTag("player_play_pause").assertDoesNotExist()
                        rule.onNodeWithTag("player_episode_action").assertIsDisplayed()
                        rule.onNodeWithTag("player_more_actions").assertDoesNotExist()
                        rule.onNodeWithTag("player_danmaku_toggle").assertDoesNotExist()
                        val tags = mutableListOf("player_previous_episode", "player_next_episode", "player_episode_action")
                        if (isFullscreen || widthDp >= 500) {
                            rule.onNodeWithTag("player_ratio_action").assertIsDisplayed()
                            rule.onNodeWithTag("player_quality_action").assertIsDisplayed()
                            tags += listOf("player_ratio_action", "player_speed_action", "player_quality_action")
                        } else {
                            rule.onNodeWithTag("player_ratio_action").assertDoesNotExist()
                            rule.onNodeWithTag("player_quality_action").assertDoesNotExist()
                            tags += "player_speed_action"
                        }
                        tags += "player_fullscreen_toggle"
                        val before = tags.associateWith { rule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
                        before.values.zipWithNext().forEach { (left, right) ->
                            assertTrue("Actions must not overlap at $widthDp dp", left.right <= right.left + 1f)
                        }
                        rule.runOnIdle { glass.value = true }
                        rule.onNodeWithTag("player_play_pause").assertDoesNotExist()
                        val glassBounds = tags.map { rule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
                        glassBounds.zipWithNext().forEach { (left, right) ->
                            assertTrue("Glass actions must not overlap at $widthDp dp", left.right <= right.left + 1f)
                        }
                        rule.runOnIdle { episodes.value = false }
                        rule.onNodeWithTag("player_episode_action").assertDoesNotExist()
                        rule.onNodeWithTag("player_next_episode").assertDoesNotExist()
                        rule.onNodeWithTag("player_previous_episode").assertDoesNotExist()
                    }
                }
            }
        }
    }

    @Test
    fun portraitAndLandscapeActionsRemainClickableAcrossRepeatedRoundTrips() {
        val phase = mutableStateOf(ContinuousPlayerTransitionPhase.Inline)
        val portrait = mutableStateOf(false)
        val requestedOrientation = mutableStateOf(ContinuousPlayerOrientationRequest.None)
        fun dispatch(event: ContinuousPlayerTransitionEvent) {
            val decision = reduceContinuousPlayerTransition(phase.value, event)
            phase.value = decision.phase
            requestedOrientation.value = decision.orientationRequest
        }
        rule.setContent {
            MaterialTheme {
                BottomControlBar(
                    progress = PlayerProgress(500, 3000),
                    isFullscreen = phase.value == ContinuousPlayerTransitionPhase.Fullscreen,
                    viewportWidthDpOverride = 320,
                    onSeek = {},
                    onPortraitFullscreen = {
                        portrait.value = true
                        dispatch(ContinuousPlayerTransitionEvent.EnterPortraitFullscreen)
                    },
                    onToggleFullscreen = {
                        if (portrait.value) {
                            portrait.value = false
                            dispatch(ContinuousPlayerTransitionEvent.EnterFullscreen)
                        } else {
                            dispatch(ContinuousPlayerTransitionEvent.Toggle)
                        }
                    },
                )
            }
        }
        repeat(2) {
            rule.onNodeWithTag("player_portrait_fullscreen").assertIsDisplayed().performClick()
            rule.runOnIdle {
                assertTrue(portrait.value)
                assertEquals(ContinuousPlayerOrientationRequest.Portrait, requestedOrientation.value)
            }
            // Click before portrait Configuration has arrived: this must enter, not toggle
            // the old landscape exit phase into another portrait request.
            rule.onNodeWithTag("player_fullscreen_toggle").assertIsDisplayed().performClick()
            rule.runOnIdle {
                assertTrue(!portrait.value)
                assertEquals(ContinuousPlayerOrientationRequest.Landscape, requestedOrientation.value)
                dispatch(ContinuousPlayerTransitionEvent.OrientationChanged(true))
            }
            rule.onNodeWithTag("player_fullscreen_toggle").assertIsDisplayed().performClick()
            rule.runOnIdle {
                assertEquals(ContinuousPlayerOrientationRequest.Portrait, requestedOrientation.value)
                dispatch(ContinuousPlayerTransitionEvent.OrientationChanged(false))
                dispatch(ContinuousPlayerTransitionEvent.CollapseFinished)
                assertEquals(ContinuousPlayerTransitionPhase.Inline, phase.value)
            }
        }
    }
}
