package com.android.purebilibili.feature.video.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayerBackgroundPlaybackSessionTest {
    @Test
    fun `temporary false remains distinct from no override across backend and menu reads`() {
        val player = Any()
        val menu = PlayerBackgroundPlaybackSessions.forContent(player, "video:a")
        assertNull(menu.selection.value)
        assertTrue(PlayerBackgroundPlaybackSessions.effective(player, "video:a", true))
        menu.select(false)
        assertEquals(false, PlayerBackgroundPlaybackSessions.selection(player, "video:a"))
        assertFalse(PlayerBackgroundPlaybackSessions.effective(player, "video:a", true))
        assertSame(menu, PlayerBackgroundPlaybackSessions.forContent(player, "video:a"))
        assertTrue(PlayerBackgroundPlaybackSessions.effective(player, "video:b", true))
        assertTrue(PlayerBackgroundPlaybackSessions.effective(Any(), "video:a", true))
    }

    @Test
    fun `ending playback clears overrides in state objects still observed by retained pages`() {
        val player = Any()
        val page = PlayerBackgroundPlaybackSessions.forContent(player, "live:12")
        page.select(true)
        assertTrue(PlayerBackgroundPlaybackSessions.effective(player, "live:12", false))
        PlayerBackgroundPlaybackSessions.clear(player)
        assertNull(page.selection.value)
        assertFalse(PlayerBackgroundPlaybackSessions.effective(player, "live:12", false))
    }
}
