package com.android.purebilibili.feature.home.components

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.android.purebilibili.core.store.home.GlassMotionStyle
import com.android.purebilibili.core.ui.motion.GlassMorphSprings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassDockMotionTest {
    @Test
    fun physicalResponseIsPreservedWhenAdaptingMenuSpringsToComposeDockGeometry() {
        for (expanding in listOf(true, false)) {
            val response = if (expanding) GlassMorphSprings.Expand else GlassMorphSprings.Return
            val animation = TargetBasedAnimation(glassDockMorphSpring<Float>(GlassMotionStyle.ELASTIC, expanding),
                Float.VectorConverter, 40f, 280f, initialVelocity = -30f)
            for (millis in listOf(16, 40, 80, 120)) {
                assertEquals(response.sample(millis / 1000f, 40f, 280f, -30f).value,
                    animation.getValueFromNanos(millis * 1_000_000L), 0.01f)
            }
            assertEquals(280f, animation.getValueFromNanos(animation.durationNanos))
        }
    }

    @Test
    fun linkedDockChoosesOneDirectionForAllGeometryInTheSameTransaction() {
        assertTrue(!linkedDockMotionExpanding(LinkedDockPhase.Expanded, LinkedDockPhase.Compact))
        assertTrue(!linkedDockMotionExpanding(LinkedDockPhase.Expanded, LinkedDockPhase.Playback))
        assertTrue(linkedDockMotionExpanding(LinkedDockPhase.Compact, LinkedDockPhase.Expanded))
        assertTrue(linkedDockMotionExpanding(LinkedDockPhase.Playback, LinkedDockPhase.Search))
        assertTrue(!linkedDockMotionExpanding(LinkedDockPhase.Search, LinkedDockPhase.Playback))
    }
}
