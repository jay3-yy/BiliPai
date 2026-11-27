package com.android.purebilibili.feature.video.playback.policy

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoHdrPresentationPolicyTest {

    @Test
    fun recognizesPqAndHlgAsHdrColorTransfers() {
        assertTrue(isHdrColorTransfer(C.COLOR_TRANSFER_ST2084))
        assertTrue(isHdrColorTransfer(C.COLOR_TRANSFER_HLG))
        assertFalse(isHdrColorTransfer(C.COLOR_TRANSFER_SDR))
        assertFalse(isHdrColorTransfer(0))
    }

    @Test
    fun sdrOrDisabledSofteningLeavesHeadroomAndDanmakuUnchanged() {
        val sdr = resolveVideoHdrPresentationState(
            isHdrContent = false,
            controlsVisible = true,
            danmakuVisible = true,
        )
        assertEquals(0f, sdr.desiredHdrHeadroom, 0.001f)
        assertEquals(1f, sdr.danmakuBrightnessFactor, 0.001f)

        val disabled = resolveVideoHdrPresentationState(
            isHdrContent = true,
            controlsVisible = true,
            danmakuVisible = true,
            softenEnabled = false,
        )
        assertEquals(0f, disabled.desiredHdrHeadroom, 0.001f)
        assertEquals(1f, disabled.danmakuBrightnessFactor, 0.001f)
    }

    @Test
    fun hdrSoftensDanmakuAndReleasesHeadroomDuringPureCinemaPlayback() {
        val withDanmaku = resolveVideoHdrPresentationState(
            isHdrContent = true,
            controlsVisible = false,
            danmakuVisible = true,
        )
        assertTrue(withDanmaku.danmakuBrightnessFactor < 1f)
        assertTrue(withDanmaku.desiredHdrHeadroom > 1f)

        val pureCinema = resolveVideoHdrPresentationState(
            isHdrContent = true,
            controlsVisible = false,
            danmakuVisible = false,
        )
        assertEquals(0f, pureCinema.desiredHdrHeadroom, 0.001f)
        assertEquals(1f, pureCinema.danmakuBrightnessFactor, 0.001f)
    }
}
