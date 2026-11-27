package com.android.purebilibili.core.util

import android.view.Display
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaUtilsHdrCapabilityTest {

    @Test
    fun `hdr10 playback accepts hdr10 display`() {
        assertTrue(
            MediaUtils.supportsHdr10Types(
                intArrayOf(
                    Display.HdrCapabilities.HDR_TYPE_HDR10,
                    Display.HdrCapabilities.HDR_TYPE_HLG
                )
            )
        )
    }

    @Test
    fun `hdr10 playback rejects hlg only and unknown displays`() {
        assertFalse(MediaUtils.supportsHdr10Types(intArrayOf(Display.HdrCapabilities.HDR_TYPE_HLG)))
        assertFalse(MediaUtils.supportsHdr10Types(null))
        assertFalse(MediaUtils.supportsHdr10Types(intArrayOf()))
    }

    @Test
    fun `supportsDolbyVisionType returns true when dolby type exists`() {
        assertTrue(
            MediaUtils.supportsDolbyVisionType(
                intArrayOf(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
            )
        )
    }

    @Test
    fun `supportsDolbyVisionType returns false when dolby type missing`() {
        assertFalse(
            MediaUtils.supportsDolbyVisionType(
                intArrayOf(Display.HdrCapabilities.HDR_TYPE_HDR10)
            )
        )
    }

    @Test
    fun `isHardwareVideoDecoderInfo uses api29 flag when available and name heuristic on api28`() {
        assertTrue(isHardwareVideoDecoderInfo("c2.qti.av1.decoder", sdkInt = 29, isHardwareAcceleratedApi29 = true))
        assertFalse(isHardwareVideoDecoderInfo("c2.qti.av1.decoder", sdkInt = 29, isHardwareAcceleratedApi29 = false))
        assertFalse(isHardwareVideoDecoderInfo("c2.android.av1-dav1d.decoder", sdkInt = 28))
        assertFalse(isHardwareVideoDecoderInfo("OMX.google.hevc.decoder", sdkInt = 28))
        assertTrue(isHardwareVideoDecoderInfo("OMX.qcom.video.decoder.hevc", sdkInt = 28))
    }
}
