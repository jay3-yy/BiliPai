package com.android.purebilibili.feature.home.components

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `pixel sampling is lifecycle bound and serialized`() {
        val root = listOf(File("."), File("..")).first { File(it, "app/src/main").exists() }
        val source = File(
            root,
            "app/src/main/java/com/android/purebilibili/feature/home/components/" +
                "LiquidGlassAdaptiveReadability.kt",
        ).readText()

        assertTrue(source.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertTrue(source.contains("adaptiveReadabilityPixelCopyMutex.withLock"))
        assertEquals(1, Regex("Handler\\(Looper.getMainLooper\\(\\)\\)").findAll(source).count())
        assertTrue(source.contains("by lazy(LazyThreadSafetyMode.NONE)"))
    }
}
