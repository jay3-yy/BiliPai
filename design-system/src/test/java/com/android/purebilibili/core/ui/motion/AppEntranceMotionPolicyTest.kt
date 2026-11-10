package com.android.purebilibili.core.ui.motion

import com.android.purebilibili.core.ui.adaptive.MotionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppEntranceMotionPolicyTest {

    @Test
    fun `disabled toggle forces instant non-animated spec`() {
        val spec = resolveEffectiveEntranceMotionSpec(
            deviceTier = MotionTier.Enhanced,
            appEntranceEnabled = false,
            systemReduceMotion = false
        )
        assertFalse(spec.animate)
        assertEquals(1f, spec.initialScale)
        assertEquals(0f, spec.offsetDp)
    }

    @Test
    fun `system reduce motion forces instant spec even when toggle on`() {
        val spec = resolveEffectiveEntranceMotionSpec(
            deviceTier = MotionTier.Normal,
            appEntranceEnabled = true,
            systemReduceMotion = true
        )
        assertFalse(spec.animate)
    }

    @Test
    fun `enabled without reduce motion animates per device tier`() {
        val spec = resolveEffectiveEntranceMotionSpec(
            deviceTier = MotionTier.Normal,
            appEntranceEnabled = true,
            systemReduceMotion = false
        )
        assertTrue(spec.animate)
        assertEquals(resolveEntranceMotionSpec(MotionTier.Normal), spec)
    }

    @Test
    fun `enhanced tier is more expressive than normal than reduced`() {
        val reduced = resolveEntranceMotionSpec(MotionTier.Reduced)
        val normal = resolveEntranceMotionSpec(MotionTier.Normal)
        val enhanced = resolveEntranceMotionSpec(MotionTier.Enhanced)

        assertTrue(reduced.offsetDp < normal.offsetDp)
        assertTrue(normal.offsetDp < enhanced.offsetDp)
        // 起始缩放越小动势越大:Enhanced < Normal < Reduced
        assertTrue(enhanced.initialScale < normal.initialScale)
        assertTrue(normal.initialScale < reduced.initialScale)
    }

    @Test
    fun `stagger delay is zero for first item and capped at max`() {
        val spec = resolveEntranceMotionSpec(MotionTier.Normal)
        assertEquals(0, resolveEntranceStaggerDelayMs(0, spec))
        assertEquals(spec.staggerStepMs, resolveEntranceStaggerDelayMs(1, spec))
        assertEquals(spec.maxStaggerMs, resolveEntranceStaggerDelayMs(10_000, spec))
    }

    @Test
    fun `non animated spec never staggers`() {
        val spec = resolveEffectiveEntranceMotionSpec(
            deviceTier = MotionTier.Enhanced,
            appEntranceEnabled = false,
            systemReduceMotion = false
        )
        assertEquals(0, resolveEntranceStaggerDelayMs(5, spec))
    }

    @Test
    fun `frame starts hidden and below, settles to terminal`() {
        val spec = resolveEntranceMotionSpec(MotionTier.Normal)

        val start = resolveEntranceFrame(0f, spec)
        assertEquals(0f, start.alpha)
        assertEquals(spec.offsetDp, start.translationYDp)
        assertEquals(spec.initialScale, start.scale)

        val end = resolveEntranceFrame(1f, spec)
        assertEquals(1f, end.alpha)
        assertEquals(0f, end.translationYDp)
        assertEquals(1f, end.scale)
    }

    @Test
    fun `alpha reaches full before progress completes`() {
        val spec = resolveEntranceMotionSpec(MotionTier.Normal)
        val frame = resolveEntranceFrame(spec.alphaLeadFraction, spec)
        assertEquals(1f, frame.alpha)
        // 此时位移尚未归零,体现「先看清、后落位」
        assertTrue(frame.translationYDp > 0f)
    }
    @Test
    fun `spring overshoot moves geometry past target while alpha stays opaque`() {
        val spec = resolveEntranceMotionSpec(MotionTier.Enhanced)
        val overshoot = resolveEntranceFrame(1.02f, spec)
        assertEquals(1f, overshoot.alpha)
        assertTrue(overshoot.translationYDp < 0f)
        assertTrue(overshoot.scale > 1f)
        val settled = resolveEntranceFrame(1f, spec)
        assertEquals(0f, settled.translationYDp)
        assertEquals(1f, settled.scale)
    }

}
