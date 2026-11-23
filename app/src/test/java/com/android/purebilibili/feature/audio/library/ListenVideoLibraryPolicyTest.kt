package com.android.purebilibili.feature.audio.library

import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavFolderSource
import com.android.purebilibili.data.model.response.FavoriteData
import com.android.purebilibili.data.model.response.FavoriteUgc
import com.android.purebilibili.data.model.response.Upper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListenVideoLibraryPolicyTest {

    @Test
    fun `owned favorite folders map to playlists and invalid folders are excluded`() {
        val playlists = mapListenVideoPlaylists(
            listOf(
                FavFolder(id = 10L, title = "通勤", cover = "cover", media_count = 2),
                FavFolder(id = 0L, title = "无效"),
                FavFolder(id = 11L, title = " "),
                FavFolder(
                    id = 12L,
                    title = "收藏的合集",
                    type = 21,
                    source = FavFolderSource.SUBSCRIBED
                )
            )
        )

        assertEquals(listOf(10L), playlists.map { it.mediaId })
        assertEquals("通勤", playlists.single().title)
        assertEquals(2, playlists.single().trackCount)
    }

    @Test
    fun `collection folders and resources map to deduplicated albums`() {
        val albums = mapListenVideoAlbums(
            collectedFolders = listOf(
                FavFolder(
                    id = 30L,
                    mid = 7L,
                    title = "Album",
                    cover = "folder-cover",
                    media_count = 4,
                    type = 21,
                    upper = Upper(mid = 7L, name = "Artist"),
                    source = FavFolderSource.SUBSCRIBED
                )
            ),
            resources = listOf(
                FavoriteData(
                    id = 99L,
                    type = 21,
                    season_id = 30L,
                    title = "Duplicate",
                    cover = "resource-cover",
                    media_count = 4,
                    upper = Upper(mid = 7L, name = "Artist")
                ),
                FavoriteData(
                    id = 40L,
                    type = 21,
                    season_id = 40L,
                    title = "Second album",
                    media_count = 3,
                    upper = Upper(mid = 8L, name = "Second artist")
                )
            )
        )

        assertEquals(listOf(30L, 40L), albums.map { it.seasonId })
        assertEquals("folder-cover", albums.first().coverUrl)
        assertEquals("Artist", albums.first().artistName)
    }

    @Test
    fun `tracks group by up mid and duplicate bvid appears once`() {
        val resources = listOf(
            trackData("BV1", 7L, "Artist", "Song A"),
            trackData("BV2", 7L, "Artist", "Song B"),
            trackData("BV1", 7L, "Artist", "Song A duplicate"),
            trackData("BV3", 8L, "Second", "Song C")
        )

        val artists = mapListenVideoArtists(resources)

        assertEquals(listOf(7L, 8L), artists.map { it.mid })
        assertEquals(listOf("BV1", "BV2"), artists.first().tracks.map { it.bvid })
        assertEquals("Artist", artists.first().name)
    }

    @Test
    fun `track mapping excludes collections and missing stable identities`() {
        val valid = trackData("BV1", 7L, "Artist", "Song")
        val collection = valid.copy(type = 21, season_id = 20L)
        val missingBvid = valid.copy(bvid = "", bv_id = "")
        val missingArtist = valid.copy(upper = Upper(mid = 0L, name = "Unknown"))

        assertTrue(valid.toListenVideoTrackOrNull() != null)
        assertEquals(null, collection.toListenVideoTrackOrNull())
        assertEquals(null, missingBvid.toListenVideoTrackOrNull())
        assertEquals(null, missingArtist.toListenVideoTrackOrNull())
    }

    @Test
    fun `playback selection deduplicates bvid and preserves clicked start`() {
        val tracks = listOf(
            track("BV1", "Song A"),
            track("BV2", "Song B"),
            track("BV1", "Duplicate")
        )

        val selection = resolveListenVideoPlaybackSelection(tracks, clickedBvid = "BV2")

        assertEquals(listOf("BV1", "BV2"), selection.items.map { it.bvid })
        assertEquals(1, selection.startIndex)
        assertEquals("Artist", selection.items[1].owner)
        assertEquals("face", selection.items[1].ownerFace)
        assertEquals(70L, selection.items[1].cid)
    }

    @Test
    fun `local keyword filters match playlists albums and artists case-insensitively`() {
        val playlists = listOf(
            ListenVideoPlaylist(1L, "Lo-Fi Study", "", 10, FavFolderSource.OWNED),
            ListenVideoPlaylist(2L, "粤语经典", "", 20, FavFolderSource.OWNED)
        )
        val albums = listOf(
            ListenVideoAlbum(10L, 100L, "夜间电台", "", 8, "DJ Alpha"),
            ListenVideoAlbum(11L, 101L, "古典钢琴", "", 5, "Beta")
        )
        val artists = listOf(
            ListenVideoArtist(100L, "Miku", "", listOf(track("BV1", "Tell Your World"))),
            ListenVideoArtist(101L, "周深", "", listOf(track("BV2", "大鱼")))
        )

        assertEquals(listOf(1L), filterListenVideoPlaylists(playlists, "  lofi".replace("lofi", "lo-fi")).map { it.mediaId })
        assertEquals(listOf(10L), filterListenVideoAlbums(albums, "alpha").map { it.seasonId })
        assertEquals(listOf(100L), filterListenVideoArtists(artists, "world").map { it.mid })
        assertEquals(playlists, filterListenVideoPlaylists(playlists, "   "))
    }

    private fun trackData(
        bvid: String,
        artistMid: Long,
        artistName: String,
        title: String
    ) = FavoriteData(
        id = bvid.hashCode().toLong(),
        bvid = bvid,
        title = title,
        cover = "https://example.com/$bvid.jpg",
        duration = 180,
        upper = Upper(mid = artistMid, name = artistName, face = "face-$artistMid"),
        ugc = FavoriteUgc(first_cid = artistMid * 10)
    )

    private fun track(bvid: String, title: String) = ListenVideoTrack(
        bvid = bvid,
        cid = 70L,
        title = title,
        coverUrl = "cover-$bvid",
        durationMs = 180_000L,
        artistId = 7L,
        artistName = "Artist",
        artistAvatarUrl = "face"
    )
}
