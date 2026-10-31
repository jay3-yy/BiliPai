package com.android.purebilibili.feature.video.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class PlayerAspectRatioSessionTest {
    @Test
    fun `same player and video share selection across parts and window hosts`() {
        val player = Any()
        val detail = PlayerAspectRatioSessions.forContent(player, "video-a")
        detail.select(VideoAspectRatio.FILL)
        val fullscreen = PlayerAspectRatioSessions.forContent(player, "video-a")
        assertSame(detail, fullscreen)
        assertEquals(VideoAspectRatio.FILL, fullscreen.selection.value)
    }

    @Test
    fun `different content and replacement players never inherit a temporary ratio`() {
        val player = Any()
        val original = PlayerAspectRatioSessions.forContent(player, "video-a")
        original.select(VideoAspectRatio.FILL)
        val otherContent = PlayerAspectRatioSessions.forContent(player, "video-b")
        val newPlayer = PlayerAspectRatioSessions.forContent(Any(), "video-a")
        assertNotSame(original, otherContent)
        assertNotSame(original, newPlayer)
        assertNull(otherContent.selection.value)
        assertNull(newPlayer.selection.value)
    }

    @Test
    fun `long lived players bound retained content sessions`() {
        val player = Any()
        PlayerAspectRatioSessions.forContent(player, "old").select(VideoAspectRatio.FILL)
        repeat(16) { PlayerAspectRatioSessions.forContent(player, "new-$it") }
        assertNull(PlayerAspectRatioSessions.forContent(player, "old").selection.value)
    }
}
