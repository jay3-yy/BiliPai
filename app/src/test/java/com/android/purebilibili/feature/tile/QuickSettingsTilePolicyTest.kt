package com.android.purebilibili.feature.tile

import android.service.quicksettings.Tile
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsTilePolicyTest {

    @Test
    fun controllerActivelyPlayingExcludesEndedAndIdleStates() {
        assertTrue(
            isAudioTileControllerActivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                hasCurrentMediaItem = true,
            )
        )
        assertTrue(
            isAudioTileControllerActivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
                hasCurrentMediaItem = true,
            )
        )
        assertFalse(
            isAudioTileControllerActivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_ENDED,
                hasCurrentMediaItem = true,
            )
        )
        assertFalse(
            isAudioTileControllerActivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_IDLE,
                hasCurrentMediaItem = true,
            )
        )
        assertFalse(
            isAudioTileControllerActivelyPlaying(
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                hasCurrentMediaItem = true,
            )
        )
        assertFalse(
            isAudioTileControllerActivelyPlaying(
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                hasCurrentMediaItem = false,
            )
        )
    }

    @Test
    fun audioTileShowsActiveStateAndCurrentTitleWhenPlaying() {
        val result = resolveAudioTilePresentation(
            isPlaying = true,
            activeTitle = "  视频标题 A  ",
            savedTitle = "历史标题",
            libraryProtected = true,
            playingFallbackSubtitle = "正在播放",
            emptySubtitle = "暂无续播队列",
            lockedSubtitle = "隐私验证已开启",
            tileLabel = "听视频续播",
        )
        assertEquals(Tile.STATE_ACTIVE, result.state)
        assertEquals("视频标题 A", result.subtitle)
        assertEquals("听视频续播 · 视频标题 A", result.contentDescription)
    }

    @Test
    fun audioTileFallsBackToPlayingSubtitleWhenActiveTitleBlank() {
        val result = resolveAudioTilePresentation(
            isPlaying = true,
            activeTitle = "   ",
            savedTitle = "历史标题",
            libraryProtected = false,
            playingFallbackSubtitle = "正在播放",
            emptySubtitle = "暂无续播队列",
            lockedSubtitle = "隐私验证已开启",
            tileLabel = "听视频续播",
        )
        assertEquals(Tile.STATE_ACTIVE, result.state)
        assertEquals("正在播放", result.subtitle)
    }

    @Test
    fun audioTileRespectsPrivacyLockOverSavedQueueWhenIdle() {
        val result = resolveAudioTilePresentation(
            isPlaying = false,
            activeTitle = null,
            savedTitle = "历史标题",
            libraryProtected = true,
            playingFallbackSubtitle = "正在播放",
            emptySubtitle = "暂无续播队列",
            lockedSubtitle = "隐私验证已开启",
            tileLabel = "听视频续播",
        )
        assertEquals(Tile.STATE_INACTIVE, result.state)
        assertEquals("隐私验证已开启", result.subtitle)
    }

    @Test
    fun audioTileShowsSavedTitleOrEmptyWhenIdleAndUnlocked() {
        val saved = resolveAudioTilePresentation(
            isPlaying = false,
            activeTitle = null,
            savedTitle = "稍后再看第一条",
            libraryProtected = false,
            playingFallbackSubtitle = "正在播放",
            emptySubtitle = "暂无续播队列",
            lockedSubtitle = "隐私验证已开启",
            tileLabel = "听视频续播",
        )
        assertEquals(Tile.STATE_INACTIVE, saved.state)
        assertEquals("稍后再看第一条", saved.subtitle)

        val empty = resolveAudioTilePresentation(
            isPlaying = false,
            activeTitle = null,
            savedTitle = null,
            libraryProtected = false,
            playingFallbackSubtitle = "正在播放",
            emptySubtitle = "暂无续播队列",
            lockedSubtitle = "隐私验证已开启",
            tileLabel = "听视频续播",
        )
        assertEquals(Tile.STATE_INACTIVE, empty.state)
        assertEquals("暂无续播队列", empty.subtitle)
    }

    @Test
    fun incognitoTileMapsEnabledState() {
        val enabled = resolveIncognitoTilePresentation(
            enabled = true,
            tileLabel = "BiliPai 无痕",
            onSubtitle = "已开启",
            offSubtitle = "已关闭",
        )
        assertEquals(Tile.STATE_ACTIVE, enabled.state)
        assertEquals("已开启", enabled.subtitle)

        val disabled = resolveIncognitoTilePresentation(
            enabled = false,
            tileLabel = "BiliPai 无痕",
            onSubtitle = "已开启",
            offSubtitle = "已关闭",
        )
        assertEquals(Tile.STATE_INACTIVE, disabled.state)
        assertEquals("已关闭", disabled.subtitle)
    }
}
