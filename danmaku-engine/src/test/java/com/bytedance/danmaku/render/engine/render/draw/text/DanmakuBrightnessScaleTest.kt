package com.bytedance.danmaku.render.engine.render.draw.text

import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuBrightnessScaleTest {
    @Test
    fun unityOrInvalidBrightnessReturnsOriginalColorUnchanged() {
        val color = 0xCCFF8844.toInt()
        assertEquals(color, scaleDanmakuColorRgb(color, 1f))
        assertEquals(color, scaleDanmakuColorRgb(color, 1.5f))
        assertEquals(color, scaleDanmakuColorRgb(color, Float.NaN))
    }

    @Test
    fun scalesRgbChannelsWhilePreservingAlphaAndClampingFloor() {
        val whiteSemiTransparent = 0x80FFFFFF.toInt()
        val softened = scaleDanmakuColorRgb(whiteSemiTransparent, 0.78f)
        assertEquals(0x80, softened ushr 24)
        assertEquals(199, (softened ushr 16) and 0xFF)
        assertEquals(199, (softened ushr 8) and 0xFF)
        assertEquals(199, softened and 0xFF)

        val clampedFloor = scaleDanmakuColorRgb(0xFF64C832.toInt(), 0.1f)
        assertEquals(0xFF, clampedFloor ushr 24)
        assertEquals(40, (clampedFloor ushr 16) and 0xFF)
        assertEquals(80, (clampedFloor ushr 8) and 0xFF)
        assertEquals(20, clampedFloor and 0xFF)
    }
}
