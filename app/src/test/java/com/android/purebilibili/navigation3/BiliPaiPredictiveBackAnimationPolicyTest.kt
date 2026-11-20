package com.android.purebilibili.navigation3

import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackExitDirection
import com.android.purebilibili.navigation3.predictiveback.AOSP_PREDICTIVE_COMMIT_DURATION_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliPaiPredictiveBackAnimationPolicyTest {
    @Test
    fun `aosp predictive release settles without a long post-gesture tail`() {
        assertTrue(AOSP_PREDICTIVE_COMMIT_DURATION_MILLIS <= 250)
    }

    @Test
    fun `all transition storage values are stable`() {
        assertEquals(
            listOf(
                "none", "aosp", "miuix", "scale", "bilipai_classic",
                "fade", "right_to_left", "left_to_right", "up_to_down", "down_to_up",
                "right_to_left_fade", "left_to_right_fade", "zoom", "circular_reveal", "ios",
                "fade_in", "size", "shared_axis", "no_transition",
            ),
            BiliPaiPredictiveBackAnimationStyle.entries.map { it.storageValue },
        )
    }

    @Test
    fun `saved transition styles round trip without changing existing choices`() {
        BiliPaiPredictiveBackAnimationStyle.entries.forEach { style ->
            assertEquals(style, BiliPaiPredictiveBackAnimationStyle.fromStorageValue(style.storageValue))
        }
    }

    @Test
    fun `legacy and unknown transition values migrate to miuix`() {
        assertEquals(
            BiliPaiPredictiveBackAnimationStyle.MIUIX,
            BiliPaiPredictiveBackAnimationStyle.fromStorageValue("default"),
        )
        assertEquals(
            BiliPaiPredictiveBackAnimationStyle.CLASSIC,
            BiliPaiPredictiveBackAnimationStyle.fromStorageValue("classic"),
        )
        assertEquals(
            BiliPaiPredictiveBackAnimationStyle.MIUIX,
            BiliPaiPredictiveBackAnimationStyle.fromStorageValue("unknown"),
        )
    }

    @Test
    fun `scale exit direction defaults to always right`() {
        assertEquals(
            BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT,
            BiliPaiPredictiveBackExitDirection.fromStorageValue(null),
        )
    }

    @Test
    fun `related parent keeps its live surface bound while child playback stays separate`() {
        val parent = BiliPaiNavKey.VideoDetail(
            bvid = "BV_PARENT",
            sourceRoute = "home",
        )
        val child = BiliPaiNavKey.VideoDetail(
            bvid = "BV_CHILD",
            sourceRoute = "video/BV_PARENT",
        )

        assertTrue(
            shouldBindVideoDetailBackPreviewPlayer(
                currentKey = child,
                previewKey = parent,
            )
        )
        assertFalse(
            shouldBindVideoDetailBackPreviewPlayer(
                currentKey = BiliPaiNavKey.VideoDetail(
                    bvid = "BV_OTHER",
                    sourceRoute = "home",
                ),
                previewKey = parent,
            )
        )
    }

    @Test
    fun `committed related return activates retained parent playback before pop`() {
        val parent = BiliPaiNavKey.VideoDetail(bvid = "BV_PARENT")
        val child = BiliPaiNavKey.VideoDetail(
            bvid = "BV_CHILD",
            sourceRoute = "video/BV_PARENT",
        )

        assertFalse(
            shouldActivateVideoDetailPlaybackSession(
                currentKey = child,
                detailKey = parent,
                isImmediateBackPreview = true,
                activateBackPreviewPlayback = false,
            )
        )
        assertTrue(
            shouldActivateVideoDetailPlaybackSession(
                currentKey = child,
                detailKey = parent,
                isImmediateBackPreview = true,
                activateBackPreviewPlayback = true,
            )
        )
    }

    @Test
    fun `non-video child keeps covered video preview silent`() {
        val coveredVideo = BiliPaiNavKey.VideoDetail(bvid = "BV_COVERED")

        assertFalse(
            shouldActivateVideoDetailPlaybackSession(
                currentKey = BiliPaiNavKey.Search(),
                detailKey = coveredVideo,
                isImmediateBackPreview = true,
                activateBackPreviewPlayback = false,
            )
        )
        assertFalse(
            shouldActivateVideoDetailPlaybackSession(
                currentKey = BiliPaiNavKey.Search(),
                detailKey = coveredVideo,
                isImmediateBackPreview = true,
                activateBackPreviewPlayback = true,
            )
        )
    }

    @Test
    fun `audio mode keeps its source video playback session warm behind the cover`() {
        val coveredVideo = BiliPaiNavKey.VideoDetail(bvid = "BV_AUDIO_SOURCE")

        assertTrue(
            shouldActivateVideoDetailPlaybackSession(
                currentKey = BiliPaiNavKey.AudioMode(sourceBvid = "BV_AUDIO_SOURCE"),
                detailKey = coveredVideo,
                isImmediateBackPreview = true,
            )
        )
        assertFalse(
            shouldActivateVideoDetailPlaybackSession(
                currentKey = BiliPaiNavKey.AudioMode(sourceBvid = "BV_OTHER"),
                detailKey = coveredVideo,
                isImmediateBackPreview = true,
            )
        )
    }
}
