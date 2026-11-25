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

    @Test
    fun liquidGlassDockSeparatesHorizontalAndVerticalArcsWhileNativeKeepsSharedSpring() {
        val collapseX = glassDockHorizontalMorphSpring<Float>(glassEnabled = true, GlassMotionStyle.STANDARD, expanding = false)
        val collapseY = glassDockVerticalMorphSpring<Float>(glassEnabled = true, GlassMotionStyle.STANDARD, expanding = false)
        assertTrue(collapseX.stiffness > collapseY.stiffness)

        val expandX = glassDockHorizontalMorphSpring<Float>(glassEnabled = true, GlassMotionStyle.STANDARD, expanding = true)
        val expandY = glassDockVerticalMorphSpring<Float>(glassEnabled = true, GlassMotionStyle.STANDARD, expanding = true)
        assertTrue(expandY.stiffness > expandX.stiffness)

        val nativeX = glassDockHorizontalMorphSpring<Float>(glassEnabled = false, GlassMotionStyle.STANDARD, expanding = false)
        val nativeY = glassDockVerticalMorphSpring<Float>(glassEnabled = false, GlassMotionStyle.STANDARD, expanding = false)
        assertEquals(nativeX, nativeY)

        assertTrue(GlassMorphSprings.Expand.dampingRatio in 0.72f..0.80f)
        assertTrue(GlassMorphSprings.Return.dampingRatio in 0.72f..0.80f)
        assertTrue(GlassMorphSprings.DockExpandX.dampingRatio >= 0.85f)
        assertEquals(
            GlassMorphSprings.Expand.stiffness,
            glassTopTabCapsuleSpringSpec<Float>().stiffness,
            0.01f,
        )
    }
}
