package com.android.purebilibili.feature.video.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistTransportPreferencesTest {
    @Test
    fun `temporary order never replaces saved transport used by later queue snapshots`() {
        val preferences = PlaylistTransportPreferences()
        val saved = RestoredPlayTransport(PlayMode.REPEAT_ALL, false)
        preferences.restore(saved)
        val temporary = preferences.request(RestoredPlayTransport(PlayMode.REPEAT_ONE, true))
        assertFalse(preferences.commit(temporary, remember = false))
        assertEquals(saved, preferences.saved)
        val explicit = preferences.request(RestoredPlayTransport(PlayMode.SEQUENTIAL, true))
        assertTrue(preferences.commit(explicit, remember = true))
        assertEquals(explicit.transport, preferences.saved)
    }

    @Test
    fun `late startup preference responses cannot overwrite a newer remembered selection`() {
        val preferences = PlaylistTransportPreferences()
        val older = preferences.request(RestoredPlayTransport(PlayMode.REPEAT_ONE, false))
        val newer = preferences.request(RestoredPlayTransport(PlayMode.REPEAT_ALL, true))
        assertTrue(preferences.commit(newer, remember = true))
        assertFalse(preferences.commit(older, remember = true))
        assertEquals(newer.transport, preferences.saved)
    }

    @Test
    fun `restoring a queue invalidates older unresolved preference requests`() {
        val preferences = PlaylistTransportPreferences()
        val pending = preferences.request(RestoredPlayTransport(PlayMode.REPEAT_ONE, true))
        val restored = RestoredPlayTransport(PlayMode.SEQUENTIAL, false)
        preferences.restore(restored)
        assertFalse(preferences.commit(pending, remember = true))
        assertEquals(restored, preferences.saved)
    }

    @Test
    fun `queue edits keep temporary transport but replacing the queue restores the baseline`() {
        val oldScope = com.android.purebilibili.core.store.ContentDisplayPreferences.rememberAdjustmentsSnapshot ?: true
        fun scope(remember: Boolean) {
            com.android.purebilibili.core.store.ContentDisplayPreferences.syncRememberPlayerAdjustments(
                androidx.datastore.preferences.core.preferencesOf(
                    com.android.purebilibili.core.store.ContentDisplayPreferences.rememberPlayerAdjustmentsKey to remember,
                )
            )
        }
        try {
            scope(true)
            PlaylistManager.clearPlaylist()
            PlaylistManager.setPlayMode(PlayMode.SEQUENTIAL)
            PlaylistManager.setShuffleEnabled(false)
            val items = listOf("a", "b", "c").map { PlaylistItem(bvid = it, title = it, cover = "", owner = "") }
            PlaylistManager.setPlaylist(items)
            scope(false)
            PlaylistManager.setPlayMode(PlayMode.REPEAT_ONE)
            PlaylistManager.setShuffleEnabled(true)
            PlaylistManager.moveQueueItem(2, 1)
            PlaylistManager.removeQueueItem(1)
            assertEquals(PlayMode.REPEAT_ONE, PlaylistManager.playMode.value)
            assertEquals(true, PlaylistManager.shuffleEnabled.value)
            assertEquals(RestoredPlayTransport(PlayMode.SEQUENTIAL, false), PlaylistManager.savedTransport.value)
            // Changing only the persistence scope must not promote an earlier temporary choice.
            scope(true)
            PlaylistManager.addToPlaylist(PlaylistItem(bvid = "d", title = "D", cover = "", owner = ""))
            assertEquals(RestoredPlayTransport(PlayMode.SEQUENTIAL, false), PlaylistManager.savedTransport.value)
            PlaylistManager.setPlaylist(items)
            assertEquals(PlayMode.SEQUENTIAL, PlaylistManager.playMode.value)
            assertEquals(false, PlaylistManager.shuffleEnabled.value)
        } finally {
            scope(true)
            PlaylistManager.clearPlaylist()
            PlaylistManager.setPlayMode(PlayMode.SEQUENTIAL)
            PlaylistManager.setShuffleEnabled(false)
            scope(oldScope)
        }
    }
}
