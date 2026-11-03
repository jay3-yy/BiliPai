package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.navigation3.BiliPaiNavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiEntryNavigationTest {
    @Test
    fun `detail card enters playback directly and keeps requested episode`() {
        assertEquals(
            BiliPaiNavKey.BangumiPlayer(seasonId = 12L, epId = 34L),
            resolveBangumiPlaybackEntry(BiliPaiNavKey.BangumiDetail(seasonId = 12L, epId = 34L)),
        )
    }

    @Test
    fun `media only card keeps its lookup identity in playback`() {
        assertEquals(
            BiliPaiNavKey.BangumiPlayer(seasonId = 0L, epId = 0L, mediaId = 56L),
            resolveBangumiPlaybackEntry(BiliPaiNavKey.BangumiDetail(seasonId = 0L, mediaId = 56L)),
        )
    }
}
