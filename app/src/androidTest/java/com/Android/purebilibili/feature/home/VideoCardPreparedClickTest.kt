package com.android.purebilibili.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.transition.recordNativeVideoCardLayer
import com.android.purebilibili.feature.home.components.cards.VideoCardCaptureState
import com.android.purebilibili.feature.home.components.cards.VideoCardPreparedClickEffect
import com.android.purebilibili.feature.home.components.cards.VideoCardSourceInteraction
import com.android.purebilibili.feature.home.components.cards.rememberVideoCardCaptureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VideoCardPreparedClickTest {
    @get:Rule val rule = createComposeRule()
    private val interaction = VideoCardSourceInteraction()
    private var mounted: VideoCardCaptureState? = null
    private var clicks = 0

    @Test
    fun idleAndCancelledPressHaveNoSourceButDirectClickWaitsForBothActualRecordings() {
        var overlay by mutableStateOf(false)
        rule.setContent { Source(allowed = true, recordOverlay = overlay) }
        rule.runOnIdle { assertNull(mounted); interaction.press() }
        rule.runOnIdle { assertNotNull(mounted); assertEquals(0, clicks); interaction.release() }
        rule.runOnIdle { assertNull(mounted); interaction.requestClick() }
        rule.runOnIdle { assertEquals(0, clicks) }
        rule.runOnIdle { overlay = true }
        rule.waitUntil { clicks == 1 }
        rule.runOnIdle {
            assertFalse(interaction.preparing)
            assertNull(mounted)
            assertEquals(1, clicks)
        }
    }

    @Test
    fun losingVisibilityOrFocusWhileWaitingCancelsClickAndRequiresNewInput() {
        var allowed by mutableStateOf(true)
        var overlay by mutableStateOf(false)
        rule.setContent { Source(allowed, overlay) }
        rule.runOnIdle { interaction.requestClick() }
        rule.runOnIdle { assertNotNull(mounted); allowed = false }
        rule.runOnIdle { assertFalse(interaction.preparing); assertNull(mounted) }
        rule.runOnIdle { allowed = true; overlay = true }
        rule.runOnIdle { assertEquals(0, clicks); interaction.requestClick() }
        rule.waitUntil { clicks == 1 }
    }

    @Test
    fun replacingContentWhileWaitingDoesNotNavigateToThePreviousVideo() {
        var identity by mutableStateOf("first")
        var overlay by mutableStateOf(false)
        lateinit var input: VideoCardSourceInteraction
        rule.setContent {
            input = remember(identity) { VideoCardSourceInteraction() }
            Source(true, overlay, input, identity)
        }
        rule.runOnIdle { input.requestClick() }
        rule.runOnIdle { identity = "second" }
        rule.runOnIdle { overlay = true }
        rule.runOnIdle { assertEquals(0, clicks); assertNull(mounted); input.requestClick() }
        rule.waitUntil { clicks == 1 }
    }

    @Composable
    private fun Source(
        allowed: Boolean,
        recordOverlay: Boolean,
        input: VideoCardSourceInteraction = interaction,
        identity: String = "video",
    ) {
        val capture = if (allowed && input.preparing) rememberVideoCardCaptureState(identity) else null
        SideEffect { mounted = capture }
        if (input.preparing) {
            VideoCardPreparedClickEffect(input, capture, allowed) {
                val recorded = requireNotNull(capture)
                assertTrue(recorded.cardLayer.size.width > 1)
                assertTrue(recorded.coverOverlayLayer.size.width > 1)
                clicks++
            }
        }
        Box(Modifier.size(120.dp).recordNativeVideoCardLayer(
            capture?.cardLayer, { false }, sourceRoute = null,
            onRecorded = capture?.let { { it.onCardRecorded() } },
        ).background(Color.Blue)) {
            if (recordOverlay) {
                Box(Modifier.size(80.dp).recordNativeVideoCardLayer(
                    capture?.coverOverlayLayer, { false }, sourceRoute = null,
                    onRecorded = capture?.let { { it.onCoverRecorded() } },
                ).background(Color.Green))
            }
        }
    }
}
