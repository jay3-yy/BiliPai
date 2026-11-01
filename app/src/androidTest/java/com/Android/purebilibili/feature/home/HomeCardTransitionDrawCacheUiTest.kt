package com.android.purebilibili.feature.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.transition.LocalVideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundState
import com.android.purebilibili.core.ui.transition.VideoCardTransitionExposure
import com.android.purebilibili.core.ui.transition.VideoCardTransitionSnapshotHandle
import com.android.purebilibili.core.ui.transition.captureNativeVideoCardImage
import com.android.purebilibili.core.ui.transition.recordNativeVideoCardLayer
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardLayer
import com.android.purebilibili.core.ui.transition.rememberVideoCardTransitionSnapshotHandle
import com.android.purebilibili.core.ui.transition.videoCardTransitionBackgroundEffect
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.feature.home.components.cards.VideoCardCaptureState
import com.android.purebilibili.feature.home.components.cards.rememberVideoCardCaptureState
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class HomeCardTransitionDrawCacheUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun clickCaptureFreezesPixelsUntilAnotherSourceTakesOwnership() {
        val color = mutableStateOf(Color.Blue)
        lateinit var source: VideoCardCaptureState
        lateinit var other: GraphicsLayer
        rule.setContent {
            source = rememberVideoCardCaptureState("BVcapture")
            other = rememberNativeVideoCardLayer()
            Row {
                Box(Modifier.size(80.dp).testTag("captured_card")
                    .recordNativeVideoCardLayer(source.cardLayer, { source.freezeCard() }, sourceRoute = null)
                    .background(color.value))
                Box(Modifier.size(80.dp)
                    .recordNativeVideoCardLayer(other, { false }, sourceRoute = null)
                    .background(Color.Red))
            }
        }
        fun pixel() = rule.onNodeWithTag("captured_card").captureToImage().toPixelMap()[5, 5]
        assertTrue(pixel().blue > 0.9f)
        rule.runOnIdle {
            CardPositionManager.recordVideoCardPosition(
                bvid = "BVcapture",
                sourceRoute = "home:recommend",
                bounds = Rect(0f, 0f, 80f, 80f),
                screenWidth = 360f,
                screenHeight = 640f,
            )
            source.freezeOnClick()
            color.value = Color.Green
        }
        assertTrue("Click must preserve the source pixels before navigation", pixel().blue > 0.9f)
        rule.runOnIdle { captureNativeVideoCardImage(other) }
        assertTrue("A previous source must resume after losing pixel ownership", pixel().green > 0.9f)
        rule.runOnIdle { CardPositionManager.clear() }
    }

    @Test
    fun attachingAndReleasingTransitionLayersKeepsTheCurrentCardPixels() {
        val prepared = mutableStateOf(false)
        val color = mutableStateOf(Color.Blue)
        rule.setContent {
            val layer = if (prepared.value) rememberNativeVideoCardLayer() else null
            Box(
                Modifier.size(80.dp).testTag("preparation_card")
                    .recordNativeVideoCardLayer(
                        layer = layer,
                        freezeProvider = { false },
                        bvid = "BVpreparation",
                        sourceRoute = "home:recommend",
                        enabled = prepared.value,
                    )
                    .background(color.value),
            )
        }
        fun pixel() = rule.onNodeWithTag("preparation_card").captureToImage().toPixelMap()[5, 5]
        assertTrue(pixel().blue > 0.9f)
        rule.runOnIdle {
            prepared.value = true
            color.value = Color.Green
        }
        assertTrue(pixel().green > 0.9f)
        rule.runOnIdle {
            prepared.value = false
            color.value = Color.Red
        }
        assertTrue(pixel().red > 0.9f)
    }

    @Test
    fun refreshingSnapshotMidReturnDrawsSourceOnceAndKeepsLiveHandoffReversible() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val progress = mutableFloatStateOf(0.3f)
        val exposure = mutableStateOf(VideoCardTransitionExposure.BackPreview)
        val color = mutableStateOf(Color.Blue)
        val passes = AtomicInteger()
        val sourceDraws = AtomicInteger()
        rule.setContent {
            Box(Modifier.size(120.dp)
                .drawWithContent { passes.incrementAndGet(); drawContent() }
                .videoCardTransitionBackgroundEffect(
                    progressProvider = { progress.floatValue },
                    phaseProvider = {
                        if (exposure.value == VideoCardTransitionExposure.Returning) {
                            VideoCardTransitionBackgroundPhase.RETURNING
                        } else VideoCardTransitionBackgroundPhase.HELD
                    },
                    exposureProvider = { exposure.value },
                    realtimeBlurEnabledProvider = { true },
                    reuseJustRecordedSourceForHandoff = true,
                )
                .drawWithContent {
                    sourceDraws.incrementAndGet()
                    drawRect(color.value)
                }
                .testTag("refresh-return"))
        }
        fun pixel(): Color {
            val pixels = rule.onNodeWithTag("refresh-return").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        assertTrue(pixel().blue > 0.8f)
        rule.runOnIdle {
            assertEquals("Capture and live underlay must share one source draw per pass", passes.get(), sourceDraws.get())
            color.value = Color.Green
            exposure.value = VideoCardTransitionExposure.Returning
        }
        val blended = pixel()
        assertTrue("Following frames still show updated live content", blended.green > 0.1f && blended.blue > 0.1f)
        rule.runOnIdle {
            exposure.value = VideoCardTransitionExposure.Restoring
            progress.floatValue = 1f
        }
        val cancelled = pixel()
        assertTrue("Reversal must keep the original frozen scene", cancelled.blue > 0.8f && cancelled.green < 0.1f)
        rule.runOnIdle {
            exposure.value = VideoCardTransitionExposure.BackPreview
            progress.floatValue = 0f
        }
        assertTrue("Landing must restore current live pixels", pixel().green > 0.9f)
    }

    @Test
    fun effectResolutionChangesWithoutRecapturingOrChangingTheSourceScene() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val progress = mutableFloatStateOf(0f)
        val sourceDraws = AtomicInteger()
        lateinit var snapshot: VideoCardTransitionSnapshotHandle
        rule.setContent {
            snapshot = rememberVideoCardTransitionSnapshotHandle()
            Box(Modifier.size(120.dp)
                .videoCardTransitionBackgroundEffect(
                    progressProvider = { progress.floatValue },
                    phaseProvider = { VideoCardTransitionBackgroundPhase.OPENING },
                    exposureProvider = { VideoCardTransitionExposure.Opening },
                    realtimeBlurEnabledProvider = { true },
                    snapshotHandle = snapshot,
                )
                .drawWithContent {
                    sourceDraws.incrementAndGet()
                    drawRect(Color.Blue)
                }
                .testTag("raster-home"))
        }
        fun pixel(): Color {
            val pixels = rule.onNodeWithTag("raster-home").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        assertTrue(pixel().blue > 0.9f)
        val fullSize = snapshot.contentLayer.size
        val frozenDraws = sourceDraws.get()
        for (depth in listOf(1f, 0f, 0.8f, 0f)) {
            rule.runOnIdle { progress.floatValue = depth }
            assertTrue(pixel().blue > 0.5f)
            assertEquals("Resolution switches cannot redraw the source scene", frozenDraws, sourceDraws.get())
            rule.runOnIdle {
                val expectedWidth = if (depth == 0f) fullSize.width else (fullSize.width + 1) / 2
                val expectedHeight = if (depth == 0f) fullSize.height else (fullSize.height + 1) / 2
                assertEquals(expectedWidth, snapshot.contentLayer.size.width)
                assertEquals(expectedHeight, snapshot.contentLayer.size.height)
            }
        }
    }

    @Test
    fun blurredSnapshotSurvivesLiveHandoffCancellationAndNextSession() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val progress = mutableFloatStateOf(0f)
        val exposure = mutableStateOf(VideoCardTransitionExposure.Idle)
        val color = mutableStateOf(Color.Blue)
        val draws = AtomicInteger()
        lateinit var snapshot: VideoCardTransitionSnapshotHandle
        fun phase() = when (exposure.value) {
            VideoCardTransitionExposure.Idle -> VideoCardTransitionBackgroundPhase.IDLE
            VideoCardTransitionExposure.Opening -> VideoCardTransitionBackgroundPhase.OPENING
            VideoCardTransitionExposure.Returning -> VideoCardTransitionBackgroundPhase.RETURNING
            else -> VideoCardTransitionBackgroundPhase.HELD
        }
        rule.setContent {
            snapshot = rememberVideoCardTransitionSnapshotHandle()
            Box(Modifier.size(120.dp)
                .videoCardTransitionBackgroundEffect(
                    progressProvider = { progress.floatValue },
                    phaseProvider = { phase() },
                    exposureProvider = { exposure.value },
                    realtimeBlurEnabledProvider = { true },
                    snapshotHandle = snapshot,
                )
                .drawWithContent {
                    draws.incrementAndGet()
                    drawRect(color.value)
                }
                .testTag("blurred-home"))
        }
        fun pixel(): Color {
            val pixels = rule.onNodeWithTag("blurred-home").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        rule.runOnIdle {
            exposure.value = VideoCardTransitionExposure.Opening
            progress.floatValue = 0.5f
        }
        assertTrue(pixel().blue > 0.5f)
        val capturedDraws = draws.get()
        for (depth in listOf(0.65f, 0.8f, 1f)) {
            rule.runOnIdle { progress.floatValue = depth }
            assertTrue(pixel().blue > 0.5f)
            assertEquals("Blur updates must reuse captured source commands", capturedDraws, draws.get())
        }
        rule.runOnIdle {
            color.value = Color.Green
            exposure.value = VideoCardTransitionExposure.BackPreview
            progress.floatValue = 0f
        }
        assertTrue("Live source must take over near landing", pixel().green > 0.9f)
        rule.runOnIdle {
            exposure.value = VideoCardTransitionExposure.Restoring
            progress.floatValue = 1f
        }
        val restored = pixel()
        assertTrue("Cancel must retain the original frozen scene", restored.blue > 0.5f && restored.green < 0.1f)
        rule.runOnIdle {
            exposure.value = VideoCardTransitionExposure.Idle
            progress.floatValue = 0f
            snapshot.releaseSession()
        }
        assertTrue(pixel().green > 0.9f)
        rule.runOnIdle {
            exposure.value = VideoCardTransitionExposure.Opening
            progress.floatValue = 0.8f
        }
        val next = pixel()
        assertTrue("Next session must capture current content", next.green > 0.5f && next.blue < 0.1f)
    }

    @Test
    fun scrimProgressReusesSourceDrawingAndContentChangesStillAppear() {
        val progress = mutableFloatStateOf(0f)
        val color = mutableStateOf(Color.Blue)
        val draws = AtomicInteger()
        rule.setContent {
            Box(Modifier.size(120.dp)
                .videoCardTransitionBackgroundEffect(
                    progressProvider = { progress.floatValue },
                    phaseProvider = { VideoCardTransitionBackgroundPhase.OPENING },
                    exposureProvider = { VideoCardTransitionExposure.Opening },
                    realtimeBlurEnabledProvider = { false },
                )
                .drawWithContent {
                    draws.incrementAndGet()
                    drawRect(color.value)
                }
                .testTag("cached-home"))
        }
        fun pixel(): Color {
            val pixels = rule.onNodeWithTag("cached-home").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        val initialBlue = pixel().blue
        val initialDraws = draws.get()
        var darkestBlue = initialBlue
        for (depth in listOf(0.25f, 0.5f, 0.9f, 0.5f, 0f)) {
            rule.runOnIdle { progress.floatValue = depth }
            val currentBlue = pixel().blue
            darkestBlue = minOf(darkestBlue, currentBlue)
            assertEquals("Scrim must reuse the unchanged content layer", initialDraws, draws.get())
        }
        assertTrue("Scrim itself must still animate", darkestBlue < initialBlue - 0.02f)
        assertEquals(initialBlue, pixel().blue, 0.02f)
        rule.runOnIdle { color.value = Color.Green }
        val updated = pixel()
        assertTrue(updated.green > 0.9f && updated.blue < 0.05f)
        assertTrue("Image/data changes must refresh the live layer", draws.get() > initialDraws)
    }

    @Test
    fun switchingCardOwnershipAndCancelledReturnNeverLeaveAnEmptySlot() {
        val exposure = mutableStateOf(VideoCardTransitionExposure.Idle)
        val sourceKey = mutableStateOf<String?>(null)
        lateinit var leftLayer: GraphicsLayer
        lateinit var rightLayer: GraphicsLayer
        fun phase() = when (exposure.value) {
            VideoCardTransitionExposure.Idle -> VideoCardTransitionBackgroundPhase.IDLE
            VideoCardTransitionExposure.Opening -> VideoCardTransitionBackgroundPhase.OPENING
            VideoCardTransitionExposure.Returning -> VideoCardTransitionBackgroundPhase.RETURNING
            else -> VideoCardTransitionBackgroundPhase.HELD
        }
        val background = VideoCardTransitionBackgroundState(
            sourceKeyProvider = { sourceKey.value },
            exposureProvider = { exposure.value },
            phaseProvider = { phase() },
        )
        rule.setContent {
            leftLayer = rememberNativeVideoCardLayer()
            rightLayer = rememberNativeVideoCardLayer()
            CompositionLocalProvider(LocalVideoCardTransitionBackgroundState provides background) {
                Row(Modifier.background(Color.White).testTag("cards")
                    .videoCardTransitionBackgroundEffect(
                        progressProvider = { 0.5f },
                        phaseProvider = { phase() },
                        exposureProvider = { exposure.value },
                        realtimeBlurEnabledProvider = { false },
                    )) {
                    Box(Modifier.size(80.dp).recordNativeVideoCardLayer(leftLayer, { false }, "BVleft", "home")
                        .drawWithContent { drawRect(Color.Blue) })
                    Box(Modifier.size(80.dp).recordNativeVideoCardLayer(rightLayer, { false }, "BVright", "home")
                        .drawWithContent { drawRect(Color.Green) })
                }
            }
        }
        fun assertSlots(leftVisible: Boolean, rightVisible: Boolean) {
            val pixels = rule.onNodeWithTag("cards").captureToImage().toPixelMap()
            val left = pixels[pixels.width / 4, pixels.height / 2]
            val right = pixels[pixels.width * 3 / 4, pixels.height / 2]
            assertEquals("Left card visibility", leftVisible, left.red < 0.1f && left.blue > 0.5f)
            assertEquals("Right card visibility", rightVisible, right.red < 0.1f && right.green > 0.5f)
        }
        fun click(bvid: String, layer: GraphicsLayer) {
            CardPositionManager.recordVideoCardPosition(
                bvid, "home", Rect(0f, 0f, 80f, 80f), 400f, 800f,
            )
            captureNativeVideoCardImage(layer)
            sourceKey.value = "home:$bvid"
            exposure.value = VideoCardTransitionExposure.Opening
        }
        try {
            assertSlots(true, true)
            rule.runOnIdle { click("BVleft", leftLayer) }
            rule.runOnIdle { exposure.value = VideoCardTransitionExposure.SettledHidden }
            assertSlots(false, true)
            // A new click can take over before the previous return reaches Idle.
            rule.runOnIdle { click("BVright", rightLayer) }
            assertSlots(true, true)
            for (state in listOf(
                VideoCardTransitionExposure.SettledHidden,
                VideoCardTransitionExposure.BackPreview,
                VideoCardTransitionExposure.Restoring,
                VideoCardTransitionExposure.SettledHidden,
                VideoCardTransitionExposure.Returning,
            )) {
                rule.runOnIdle { exposure.value = state }
                assertSlots(true, false)
            }
            rule.runOnIdle { exposure.value = VideoCardTransitionExposure.Idle }
            assertSlots(true, true)
            rule.runOnIdle { click("BVleft", leftLayer) }
            rule.runOnIdle { exposure.value = VideoCardTransitionExposure.Returning }
            assertSlots(false, true)
            rule.runOnIdle {
                exposure.value = VideoCardTransitionExposure.Idle
                CardPositionManager.clearNativeVideoCardLayers()
            }
            assertSlots(true, true)
        } finally {
            rule.runOnIdle { CardPositionManager.clear() }
        }
    }
}
