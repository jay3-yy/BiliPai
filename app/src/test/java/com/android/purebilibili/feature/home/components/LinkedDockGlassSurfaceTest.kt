package com.android.purebilibili.feature.home.components

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkedDockGlassSurfaceTest {
    @Test
    fun nearbyCapsulesGrowANeckAndSeparateWithoutMovingTheirBodies() {
        val frame = LinkedDockGlassFrame(
            navigation = Rect(0f, 0f, 100f, 48f),
            playback = Rect(108f, 0f, 156f, 48f),
            search = null,
            smoothing = 0f,
        )
        assertTrue(linkedDockGlassDistance(104f, 24f, frame) > 0f)
        assertTrue(linkedDockGlassDistance(104f, 24f, frame.copy(smoothing = 20f)) < 0f)
        assertTrue(linkedDockGlassDistance(104f, 24f, frame.copy(smoothing = 4f)) > 0f)
        assertTrue(linkedDockGlassDistance(50f, 24f, frame) < 0f)
        assertTrue(linkedDockGlassDistance(132f, 24f, frame) < 0f)
    }

    @Test
    fun joiningDoesNotLeaveTheFacingEdgesInsideTheMaterial() {
        val frame = LinkedDockGlassFrame(
            navigation = Rect(0f, 0f, 100f, 48f),
            playback = Rect(108f, 0f, 156f, 48f),
            search = null,
            smoothing = 20f,
        )
        assertTrue(linkedDockGlassDistance(100f, 24f, frame) < -1f)
        assertTrue(linkedDockGlassDistance(108f, 24f, frame) < -1f)
    }

    @Test
    fun absentPlaybackCannotLeaveAnInvisibleBridgeToSearch() {
        val frame = LinkedDockGlassFrame(
            navigation = Rect(0f, 0f, 48f, 48f),
            playback = null,
            search = Rect(260f, 0f, 308f, 48f),
            smoothing = 20f,
        )
        assertTrue(linkedDockGlassDistance(154f, 24f, frame) > 0f)
        assertTrue(linkedDockGlassDistance(284f, 24f, frame) < 0f)
    }

    @Test
    fun densityAndRtlDoNotChangeContactTopology() {
        val original = LinkedDockGlassFrame(
            navigation = Rect(20f, 52f, 100f, 100f),
            playback = Rect(20f, 0f, 180f, 48f),
            search = Rect(108f, 52f, 156f, 100f),
            smoothing = 20f,
        )
        fun scaled(rect: Rect) = Rect(rect.left * 3f, rect.top * 3f, rect.right * 3f, rect.bottom * 3f)
        fun mirrored(rect: Rect) = Rect(200f - rect.right, rect.top, 200f - rect.left, rect.bottom)
        val scaledFrame = LinkedDockGlassFrame(scaled(original.navigation), scaled(original.playback!!),
            scaled(original.search!!), 60f)
        // Union ordering stays the same when only screen coordinates are mirrored.
        val mirroredFrame = LinkedDockGlassFrame(mirrored(original.navigation), mirrored(original.playback!!),
            mirrored(original.search!!), 20f)
        for (x in 0..200 step 5) for (y in 0..100 step 5) {
            val expected = linkedDockGlassDistance(x.toFloat(), y.toFloat(), original)
            assertEquals(expected * 3f, linkedDockGlassDistance(x * 3f, y * 3f, scaledFrame), 0.001f)
            assertEquals(expected, linkedDockGlassDistance(200f - x, y.toFloat(), mirroredFrame), 0.001f)
        }
    }
    @Test
    fun expandedDockConnectsOnPressAndSeparatesAgainAtItsRestingGap() {
        val resting = LinkedDockGlassFrame(
            navigation = Rect(0f, 0f, 100f, 48f), playback = null,
            search = Rect(108f, 0f, 156f, 48f), smoothing = 10f, contactMode = true,
        )
        assertTrue(linkedDockGlassDistance(104f, 24f, resting) > 0f)
        val pressed = resting.copy(navigation = Rect(-5f, -2.4f, 105f, 50.4f))
        assertTrue(linkedDockGlassDistance(106.5f, 24f, pressed) < 0f)
        val pulledAway = pressed.copy(search = resting.search!!.translate(androidx.compose.ui.geometry.Offset(12f, 0f)))
        assertTrue(linkedDockGlassDistance(112.5f, 24f, pulledAway) > 0f)
        assertTrue(linkedDockGlassDistance(104f, 24f, resting) > 0f)
    }

    @Test
    fun contactFieldLeavesSameFacingOuterEdgesUninflatedAndMirrorsWithRtl() {
        val frame = LinkedDockGlassFrame(Rect(0f, 0f, 100f, 48f), null,
            Rect(103f, 0f, 151f, 48f), 10f, contactMode = true)
        // 上方两块玻璃的法线同向，不额外向外鼓起一个融合厚边。
        assertTrue(linkedDockGlassDistance(100f, -1f, frame) > 0f)
        fun mirror(rect: Rect) = Rect(200f - rect.right, rect.top, 200f - rect.left, rect.bottom)
        val rtl = frame.copy(navigation = mirror(frame.navigation), search = mirror(frame.search!!))
        for (x in 0..160 step 4) for (y in -8..56 step 4) {
            assertEquals(linkedDockGlassDistance(x.toFloat(), y.toFloat(), frame),
                linkedDockGlassDistance(200f - x, y.toFloat(), rtl), 0.001f)
        }
    }

}
