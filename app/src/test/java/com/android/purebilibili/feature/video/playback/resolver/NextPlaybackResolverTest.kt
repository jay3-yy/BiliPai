package com.android.purebilibili.feature.video.playback.resolver

import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NextPlaybackResolverTest {

    @Test
    fun `external playlist should switch to playlist-first audio continuation`() {
        assertEquals(
            AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST,
            resolveAudioNextPlaybackStrategy(
                isExternalPlaylist = true,
                externalPlaylistSource = ExternalPlaylistSource.FAVORITE
            )
        )
    }

    @Test
    fun `non external session should keep page season related continuation`() {
        assertEquals(
            AudioNextPlaybackStrategy.PAGE_THEN_SEASON_THEN_RELATED,
            resolveAudioNextPlaybackStrategy(
                isExternalPlaylist = false,
                externalPlaylistSource = ExternalPlaylistSource.NONE
            )
        )
    }

    @Test
    fun `play in order should prefer page or season before playlist`() {
        assertEquals(
            PlayInOrderNextSource.PAGE_OR_SEASON,
            resolvePlayInOrderNextSource(
                hasNextPage = false,
                hasNextSeasonEpisode = true,
                hasNextPlaylistItem = true
            )
        )
    }

    @Test
    fun `play in order should fallback to playlist when collection has no next`() {
        assertEquals(
            PlayInOrderNextSource.PLAYLIST,
            resolvePlayInOrderNextSource(
                hasNextPage = false,
                hasNextSeasonEpisode = false,
                hasNextPlaylistItem = true
            )
        )
    }

    @Test
    fun `play in order should stop when no source has next`() {
        assertEquals(
            PlayInOrderNextSource.NONE,
            resolvePlayInOrderNextSource(
                hasNextPage = false,
                hasNextSeasonEpisode = false,
                hasNextPlaylistItem = false
            )
        )
    }

    @Test
    fun `play in order previous should prefer page or season before playlist`() {
        assertEquals(
            PlayInOrderNextSource.PAGE_OR_SEASON,
            resolvePlayInOrderPreviousSource(
                hasPreviousPage = true,
                hasPreviousSeasonEpisode = false,
                hasPreviousPlaylistItem = true
            )
        )
    }

    @Test
    fun `play in order previous should fallback to playlist when collection has no previous`() {
        assertEquals(
            PlayInOrderNextSource.PLAYLIST,
            resolvePlayInOrderPreviousSource(
                hasPreviousPage = false,
                hasPreviousSeasonEpisode = false,
                hasPreviousPlaylistItem = true
            )
        )
    }

    @Test
    fun `play in order previous should stop when no source has previous`() {
        assertEquals(
            PlayInOrderNextSource.NONE,
            resolvePlayInOrderPreviousSource(
                hasPreviousPage = false,
                hasPreviousSeasonEpisode = false,
                hasPreviousPlaylistItem = false
            )
        )
    }

    @Test
    fun `non external navigation should try page season then playlist then direct queue fallback`() {
        assertContentEquals(
            listOf(
                PlaybackNavigationTarget.PAGE_OR_SEASON,
                PlaybackNavigationTarget.PLAYLIST,
                PlaybackNavigationTarget.DIRECT_QUEUE
            ),
            resolvePlaybackNavigationTargets(
                strategy = AudioNextPlaybackStrategy.PAGE_THEN_SEASON_THEN_RELATED,
                hasPageOrSeasonTarget = true,
                hasPlaylistTarget = true
            )
        )
    }

    @Test
    fun `non external navigation should still expose direct queue fallback when no earlier source exists`() {
        assertContentEquals(
            listOf(PlaybackNavigationTarget.DIRECT_QUEUE),
            resolvePlaybackNavigationTargets(
                strategy = AudioNextPlaybackStrategy.PAGE_THEN_SEASON_THEN_RELATED,
                hasPageOrSeasonTarget = false,
                hasPlaylistTarget = false
            )
        )
    }

    @Test
    fun `external playlist navigation should follow queue instead of page or season`() {
        assertContentEquals(
            listOf(
                PlaybackNavigationTarget.PLAYLIST,
                PlaybackNavigationTarget.DIRECT_QUEUE
            ),
            resolvePlaybackNavigationTargets(
                strategy = AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST,
                hasPageOrSeasonTarget = true,
                hasPlaylistTarget = true
            )
        )
    }

    @Test
    fun `external playlist navigation should use queue when no page season target exists`() {
        assertContentEquals(
            listOf(
                PlaybackNavigationTarget.PLAYLIST,
                PlaybackNavigationTarget.DIRECT_QUEUE
            ),
            resolvePlaybackNavigationTargets(
                strategy = AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST,
                hasPageOrSeasonTarget = false,
                hasPlaylistTarget = true
            )
        )
        assertContentEquals(
            listOf(PlaybackNavigationTarget.DIRECT_QUEUE),
            resolvePlaybackNavigationTargets(
                strategy = AudioNextPlaybackStrategy.PLAY_EXTERNAL_PLAYLIST,
                hasPageOrSeasonTarget = false,
                hasPlaylistTarget = false
            )
        )
    }
}
