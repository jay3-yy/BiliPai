package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class LiquidGlassAdaptiveReadabilityTest {

    @Test
    fun `initial tone chooses dark foreground for a light backdrop`() {
        assertEquals(
            LiquidGlassAdaptiveForegroundTone.DARK,
            resolveLiquidGlassAdaptiveForegroundTone(
                previous = null,
                backgroundLuminance = 0.72f,
            ),
        )
    }

    @Test
    fun `initial tone chooses light foreground for a dark backdrop`() {
        assertEquals(
            LiquidGlassAdaptiveForegroundTone.LIGHT,
            resolveLiquidGlassAdaptiveForegroundTone(
                previous = null,
                backgroundLuminance = 0.28f,
            ),
        )
    }

    @Test
    fun `hysteresis keeps the current tone through the middle band`() {
        assertEquals(
            LiquidGlassAdaptiveForegroundTone.DARK,
            resolveLiquidGlassAdaptiveForegroundTone(
                previous = LiquidGlassAdaptiveForegroundTone.DARK,
                backgroundLuminance = 0.50f,
            ),
        )
        assertEquals(
            LiquidGlassAdaptiveForegroundTone.LIGHT,
            resolveLiquidGlassAdaptiveForegroundTone(
                previous = LiquidGlassAdaptiveForegroundTone.LIGHT,
                backgroundLuminance = 0.50f,
            ),
        )
    }

    @Test
    fun `tone changes only after crossing the outer threshold`() {
        assertEquals(
            LiquidGlassAdaptiveForegroundTone.LIGHT,
            resolveLiquidGlassAdaptiveForegroundTone(
                previous = LiquidGlassAdaptiveForegroundTone.DARK,
                backgroundLuminance = 0.30f,
            ),
        )
        assertEquals(
            LiquidGlassAdaptiveForegroundTone.DARK,
            resolveLiquidGlassAdaptiveForegroundTone(
                previous = LiquidGlassAdaptiveForegroundTone.LIGHT,
                backgroundLuminance = 0.70f,
            ),
        )
    }

    @Test
    fun `dark glass rejects black foreground from a stale light sample`() {
        assertEquals(
            Color.White,
            resolveLiquidGlassContrastGuardedForeground(
                sampledColor = Color.Black,
                stableColor = Color.White,
                backgroundColor = Color(0xFF171821),
            ),
        )
    }

    @Test
    fun `light glass keeps readable black adaptive foreground`() {
        assertEquals(
            Color.Black,
            resolveLiquidGlassContrastGuardedForeground(
                sampledColor = Color.Black,
                stableColor = Color.White,
                backgroundColor = Color(0xFFF2F2F4),
            ),
        )
    }
}
