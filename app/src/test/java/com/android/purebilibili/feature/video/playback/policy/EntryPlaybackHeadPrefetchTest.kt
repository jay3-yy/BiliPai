package com.android.purebilibili.feature.video.playback.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntryPlaybackHeadPrefetchTest {

    private val videoUrl = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/01/02/video.m4s?e=123"
    private val audioUrl = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/01/02/audio.m4s?e=456"

    @Test
    fun plansVideoAndAudioTargetsUsingCdnCacheKeysWhileEntryTransitionWaits() {
        val targets = resolveEntryPlaybackHeadPrefetchTargets(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            cdnCacheKeysByUrl = mapOf(videoUrl to "cdn:video-key"),
            hasProvisionalPlayer = false,
            playbackStartAllowed = false,
            isDataSaverActive = false,
            isAudioOnlyMode = false,
        )
        assertEquals(2, targets.size)
        assertEquals("cdn:video-key", targets[0].cacheKey)
        assertEquals(ENTRY_VIDEO_HEAD_PREFETCH_BYTES, targets[0].lengthBytes)
        assertEquals("https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/01/02/audio.m4s", targets[1].cacheKey)
        assertEquals(ENTRY_AUDIO_HEAD_PREFETCH_BYTES, targets[1].lengthBytes)
    }

    @Test
    fun skipsPrefetchWhenProvisionalActiveOrStartAllowedOrDataSaverOn() {
        assertTrue(
            resolveEntryPlaybackHeadPrefetchTargets(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                cdnCacheKeysByUrl = emptyMap(),
                hasProvisionalPlayer = true,
                playbackStartAllowed = false,
                isDataSaverActive = false,
                isAudioOnlyMode = false,
            ).isEmpty()
        )
        assertTrue(
            resolveEntryPlaybackHeadPrefetchTargets(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                cdnCacheKeysByUrl = emptyMap(),
                hasProvisionalPlayer = false,
                playbackStartAllowed = true,
                isDataSaverActive = false,
                isAudioOnlyMode = false,
            ).isEmpty()
        )
        assertTrue(
            resolveEntryPlaybackHeadPrefetchTargets(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                cdnCacheKeysByUrl = emptyMap(),
                hasProvisionalPlayer = false,
                playbackStartAllowed = false,
                isDataSaverActive = true,
                isAudioOnlyMode = false,
            ).isEmpty()
        )
    }

    @Test
    fun audioOnlyModeSkipsVideoAndLocalFilesAreIgnored() {
        val audioOnly = resolveEntryPlaybackHeadPrefetchTargets(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            cdnCacheKeysByUrl = emptyMap(),
            hasProvisionalPlayer = false,
            playbackStartAllowed = false,
            isDataSaverActive = false,
            isAudioOnlyMode = true,
        )
        assertEquals(1, audioOnly.size)
        assertEquals(audioUrl, audioOnly[0].url)

        val localFile = resolveEntryPlaybackHeadPrefetchTargets(
            videoUrl = "file:///storage/emulated/0/video.mp4",
            audioUrl = null,
            cdnCacheKeysByUrl = emptyMap(),
            hasProvisionalPlayer = false,
            playbackStartAllowed = false,
            isDataSaverActive = false,
            isAudioOnlyMode = false,
        )
        assertTrue(localFile.isEmpty())
    }
}
