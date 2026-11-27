package com.android.purebilibili.core.player

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VideoDecoderDiagnosticsTest {
    @Test fun `classification uses reported capabilities rather than a software looking name`() {
        VideoDecoderCapabilities.record("c2.android.test", hardware = true, software = false)
        assertEquals(VideoDecoderKind.HARDWARE, VideoDecoderCapabilities.kind("c2.android.test"))
        VideoDecoderCapabilities.record("vendor.test", hardware = false, software = true)
        assertEquals(VideoDecoderKind.SOFTWARE, VideoDecoderCapabilities.kind("vendor.test"))
        assertEquals(VideoDecoderKind.UNKNOWN, VideoDecoderCapabilities.kind("not-enumerated"))
    }
    @Test fun `opposite preference without an error remains explicitly unconfirmed`() {
        val reason = decoderSelectionReason(true, VideoDecoderKind.SOFTWARE, emptyList())
        assertEquals("当前使用软件解码；未能确认为什么没有使用硬件解码。", reason)
        assertFalse(reason.contains("启动失败"))
        assertEquals("当前使用软件解码，符合播放设置。", decoderSelectionReason(false, VideoDecoderKind.SOFTWARE, emptyList()))
    }
    @Test fun `successful fallback preserves exact failed candidate even within the same decoder type`() {
        val reason = decoderSelectionReason(true, VideoDecoderKind.HARDWARE, listOf("vendor-a · error-code"))
        assertTrue(reason.contains("原解码器启动失败，已换用其他解码器"))
        assertTrue(reason.contains("vendor-a · error-code"))
    }

    @Test fun `dropped frames warning distinguishes software overload from low drop rates`() {
        assertEquals("", resolveDroppedFramesWarning(VideoDecoderKind.SOFTWARE, droppedFrames = 12, elapsedMs = 1_000L))
        assertEquals("", resolveDroppedFramesWarning(VideoDecoderKind.SOFTWARE, droppedFrames = 30, elapsedMs = 20_000L))

        val softwareWarning = resolveDroppedFramesWarning(VideoDecoderKind.SOFTWARE, droppedFrames = 36, elapsedMs = 2_000L)
        assertTrue(softwareWarning.contains("软件解码负载较高"))
        assertTrue(softwareWarning.contains("36"))

        val hardwareWarning = resolveDroppedFramesWarning(VideoDecoderKind.HARDWARE, droppedFrames = 36, elapsedMs = 2_000L)
        assertTrue(hardwareWarning.contains("持续掉帧"))
        assertFalse(hardwareWarning.contains("软件解码"))
    }
}
