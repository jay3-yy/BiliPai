package com.android.purebilibili.feature.audio.player

import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioLibraryPolicyTest {
    private val first = PlaylistItem("BV1", 11L, "First", "", "Author")
    private val saved = BackgroundAudioSnapshot(listOf(first), 0, 12_000L, true, 1.5f, 7L,
        PlayMode.REPEAT_ALL, audioUrl = "https://example.com/expired")

    @Test
    fun resumeKeepsTheSelectedPartAndTransportButRefreshesTheStream() {
        val result = selectAudioLibraryRequest(saved, listOf(first), 0, 7L)
        assertEquals(12_000L, result.positionMs)
        assertEquals(1.5f, result.speed)
        assertEquals(PlayMode.REPEAT_ALL, result.playMode)
        assertEquals("", result.audioUrl)
        assertFalse(result.playing)
    }

    @Test
    fun choosingAnotherPartOfTheSameVideoDoesNotReuseItsPosition() {
        val part = first.copy(cid = 12L)
        val result = selectAudioLibraryRequest(saved, listOf(first, part), 1, 7L)
        assertEquals(part, result.current)
        assertEquals(0L, result.positionMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anAccountSwitchCannotReuseAnotherAccountsQueue() {
        selectAudioLibraryRequest(saved, listOf(first), 0, 8L)
    }

    @Test
    fun watchLaterCanStartWithoutAPreviousAudioSession() {
        val result = selectAudioLibraryRequest(null, listOf(first), 0, 7L)
        assertEquals(first, result.current)
        assertEquals(7L, result.accountMid)
        assertEquals(0L, result.positionMs)
        assertEquals(PlayMode.SEQUENTIAL, result.playMode)
    }
}
