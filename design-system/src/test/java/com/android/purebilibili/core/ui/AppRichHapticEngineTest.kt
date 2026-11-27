package com.android.purebilibili.core.ui

import android.os.Build
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppRichHapticEngineTest {

    private val allPrimitives = setOf(
        VibrationEffect.Composition.PRIMITIVE_CLICK,
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
        VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
        VibrationEffect.Composition.PRIMITIVE_THUD,
    )

    @Test
    fun emptySupportedPrimitivesFallsBackToViewHapticsForEveryPattern() {
        AppRichHapticPattern.entries.forEach { pattern ->
            assertTrue(resolveRichHapticSteps(pattern, emptySet()).isEmpty())
        }
    }

    @Test
    fun seekTickPrefersLowTickAndFallsBackToTick() {
        val lowTickSteps = resolveRichHapticSteps(AppRichHapticPattern.SEEK_TICK, allPrimitives)
        assertEquals(1, lowTickSteps.size)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, lowTickSteps[0].primitiveId)

        val tickOnlySteps = resolveRichHapticSteps(
            AppRichHapticPattern.SEEK_TICK,
            setOf(VibrationEffect.Composition.PRIMITIVE_TICK),
        )
        assertEquals(1, tickOnlySteps.size)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_TICK, tickOnlySteps[0].primitiveId)
    }

    @Test
    fun tripleChargeScalesIncreaseMonotonicallyAcrossStages() {
        val low = resolveRichHapticSteps(AppRichHapticPattern.TRIPLE_CHARGE_LOW, allPrimitives).single()
        val mid = resolveRichHapticSteps(AppRichHapticPattern.TRIPLE_CHARGE_MID, allPrimitives).single()
        val high = resolveRichHapticSteps(AppRichHapticPattern.TRIPLE_CHARGE_HIGH, allPrimitives).single()
        assertTrue(low.scale < mid.scale)
        assertTrue(mid.scale < high.scale)
    }

    @Test
    fun tripleCompleteAndGlassSnapEmitCompoundWaveformsWhenPrimitivesAvailable() {
        val tripleComplete = resolveRichHapticSteps(AppRichHapticPattern.TRIPLE_COMPLETE, allPrimitives)
        assertEquals(2, tripleComplete.size)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, tripleComplete[0].primitiveId)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_THUD, tripleComplete[1].primitiveId)
        assertTrue(tripleComplete[1].delayMs > 0)

        val glassSnap = resolveRichHapticSteps(AppRichHapticPattern.GLASS_SNAP, allPrimitives)
        assertEquals(2, glassSnap.size)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, glassSnap[0].primitiveId)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_THUD, glassSnap[1].primitiveId)
        assertTrue(glassSnap[1].delayMs > 0)
    }

    @Test
    fun fallbackConstantsDowngradeCleanlyBelowApi34AndApi30() {
        assertEquals(
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK,
            resolveRichHapticFallbackConstant(AppRichHapticPattern.SEEK_TICK, Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
        assertEquals(
            HapticFeedbackConstants.CLOCK_TICK,
            resolveRichHapticFallbackConstant(AppRichHapticPattern.SEEK_TICK, Build.VERSION_CODES.TIRAMISU),
        )
        assertEquals(
            HapticFeedbackConstants.CONFIRM,
            resolveRichHapticFallbackConstant(AppRichHapticPattern.TRIPLE_COMPLETE, Build.VERSION_CODES.R),
        )
        assertEquals(
            HapticFeedbackConstants.LONG_PRESS,
            resolveRichHapticFallbackConstant(AppRichHapticPattern.TRIPLE_COMPLETE, Build.VERSION_CODES.Q),
        )
    }
}
