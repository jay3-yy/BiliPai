package com.android.purebilibili.core.util

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.android.purebilibili.data.model.response.DashVideo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class VideoTrackCapabilitiesTest {
    private val hardware = VideoTrackDecoderSupport(true, true, false)
    private val software = VideoTrackDecoderSupport(true, false, true)
    private val unsupported = VideoTrackDecoderSupport(false, false, false)

    private fun track(quality: Int, codec: String) = DashVideo(
        id = quality, codecs = codec, baseUrl = "https://example.com/$quality/$codec",
    )

    @Test
    fun hardwarePreferenceAvoidsSoftwareCodecAtSameQualityWithoutDowngradingQuality() {
        val av1 = track(80, "av01")
        val hevc = track(80, "hev1")
        val higherSoftware = track(120, "hev1")
        val result = selectPlayableVideoTracks(listOf(av1, hevc, higherSoftware), true) {
            if (it == hevc) hardware else software
        }
        assertEquals(listOf(hevc, higherSoftware), result)
    }

    @Test
    fun explicitSoftwarePreferenceKeepsSoftwareAndStillAllowsHardwareOnlyQuality() {
        val av1 = track(80, "av01")
        val hevc = track(80, "hev1")
        val higherHardware = track(120, "hev1")
        val result = selectPlayableVideoTracks(listOf(av1, hevc, higherHardware), false) {
            if (it == av1) software else hardware
        }
        assertEquals(listOf(av1, higherHardware), result)
    }

    @Test
    fun unsupportedTrackIsRemovedEvenWhenAnotherTrackOfSameCodecWorks() {
        val supported1080 = track(80, "hev1").copy(width = 1920, height = 1080)
        val unsupported4k = track(120, "hev1").copy(width = 3840, height = 2160)
        assertEquals(
            listOf(supported1080),
            selectPlayableVideoTracks(listOf(supported1080, unsupported4k), true) {
                if (it == supported1080) hardware else unsupported
            },
        )
        assertEquals(emptyList(), selectPlayableVideoTracks(listOf(unsupported4k), true) { unsupported })
    }

    @Test
    fun capabilityFormatPreservesHevcMain10ProfileDimensionsAndFractionalFrameRate() {
        val codec = "hev1.2.4.L153.B0"
        val format = buildVideoTrackCapabilityFormat(track(120, codec).copy(
            mimeType = "video/mp4", width = 3840, height = 2160, frameRate = "60000/1001",
        ))
        assertEquals(MimeTypes.VIDEO_H265, format.sampleMimeType)
        assertEquals(codec, format.codecs)
        assertEquals(3840, format.width)
        assertEquals(2160, format.height)
        assertEquals(60000f / 1001f, format.frameRate)
    }

    @Test
    fun missingDimensionsAndFrameRateRemainUnknown() {
        val format = buildVideoTrackCapabilityFormat(track(80, "avc1.640028"))
        assertEquals(MimeTypes.VIDEO_H264, format.sampleMimeType)
        assertEquals(Format.NO_VALUE, format.width)
        assertEquals(Format.NO_VALUE, format.height)
        assertEquals(Format.NO_VALUE.toFloat(), format.frameRate)
    }
}
