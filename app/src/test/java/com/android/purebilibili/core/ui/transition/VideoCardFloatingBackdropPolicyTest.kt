package com.android.purebilibili.core.ui.transition

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCardFloatingBackdropPolicyTest {
    @Test
    fun glassStopsRecordingWhenNoChromeConsumesTheSource() {
        for (exposure in VideoCardTransitionExposure.entries) {
            for (video in listOf(false, true)) {
                assertFalse(record(video = video, exposure = exposure))
            }
        }
    }

    @Test
    fun bottomBarRetainsRecordingDuringMotionExceptWhenFullyCovered() {
        assertTrue(record(bottomBar = true))
        for (exposure in VideoCardTransitionExposure.entries) {
            val records = record(video = true, exposure = exposure, bottomBar = true)
            if (exposure == VideoCardTransitionExposure.SettledHidden) {
                assertFalse(records)
            } else {
                assertTrue(records, "底栏在 $exposure 仍需要背景")
            }
        }
    }

    @Test
    fun independentPlaybackAndFloatingContentKeepTheirBackgroundWithoutTheBottomBar() {
        for (exposure in VideoCardTransitionExposure.entries) {
            assertTrue(record(video = true, exposure = exposure, audio = true))
            assertTrue(record(video = true, exposure = exposure, consumer = true))
        }
    }

    @Test
    fun nativeMaterialsKeepTheirExistingRecordingPolicy() {
        for (exposure in VideoCardTransitionExposure.entries) {
            assertTrue(record(glass = false, video = true, exposure = exposure))
        }
    }

    private fun record(
        glass: Boolean = true,
        video: Boolean = false,
        exposure: VideoCardTransitionExposure = VideoCardTransitionExposure.Idle,
        bottomBar: Boolean = false,
        audio: Boolean = false,
        consumer: Boolean = false,
    ) = shouldRecordVideoCardFloatingBackdrop(
        liquidGlassEnabled = glass,
        isVideoDetailDestination = video,
        exposure = exposure,
        hasBottomBarConsumer = bottomBar,
        hasAudioBarConsumer = audio,
        hasFloatingConsumer = consumer,
    )
}
