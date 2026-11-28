package com.android.purebilibili.feature.video.playback.coordinator

import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.playback.session.PlaybackSessionStore
import com.android.purebilibili.feature.video.viewmodel.PlaybackEndAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackCoordinatorTest {

    @Test
    fun `resolvePlaybackEnded should store the selected completion action`() {
        val store = PlaybackSessionStore()
        val coordinator = PlaybackCoordinator(store)

        val action = coordinator.resolvePlaybackEnded(
            behavior = PlaybackCompletionBehavior.PLAY_IN_ORDER,
            autoPlayEnabled = false,
            isExternalPlaylist = true,
            externalPlaylistAutoContinueEnabled = true,
            hasNextPage = true,
            hasNextSeasonEpisode = true
        )

        assertEquals(PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST, action)
        assertEquals(action, store.state.value.lastCompletionAction)
    }

    @Test

    fun `refreshResumeSuggestion should store suggestion and mark prompt as shown`() {
        val store = PlaybackSessionStore()
        val coordinator = PlaybackCoordinator(store)
        val markedPromptKeys = mutableListOf<String>()
        val info = ViewInfo(
            bvid = "BV1multi",
            cid = 101L,
            pages = listOf(
                Page(cid = 101L, page = 1, part = "P1"),
                Page(cid = 205L, page = 5, part = "P5")
            )
        )

        val suggestion = coordinator.refreshResumeSuggestion(
            requestCid = 101L,
            loadedInfo = info,
            promptEnabled = true,
            hasPromptedBefore = { false },
            markPromptShown = { key -> markedPromptKeys += key },
            progressLookup = { bvid, cid ->
                when ("$bvid#$cid") {
                    "BV1multi#205" -> 15 * 60 * 1000L
                    else -> 0L
                }
            }
        )

        assertEquals(suggestion, store.state.value.resumeSuggestion)
        assertEquals(listOf("BV1multi#205"), markedPromptKeys)
    }

    @Test
    fun `refreshResumeSuggestion should clear state when prompt is disabled`() {
        val store = PlaybackSessionStore()
        val coordinator = PlaybackCoordinator(store)
        val info = ViewInfo(
            bvid = "BV1multi",
            cid = 101L,
            pages = listOf(
                Page(cid = 101L, page = 1, part = "P1"),
                Page(cid = 205L, page = 5, part = "P5")
            )
        )

        coordinator.refreshResumeSuggestion(
            requestCid = 101L,
            loadedInfo = info,
            promptEnabled = false,
            hasPromptedBefore = { false },
            markPromptShown = { error("prompt should not be marked when disabled") },
            progressLookup = { bvid, cid ->
                when ("$bvid#$cid") {
                    "BV1multi#205" -> 15 * 60 * 1000L
                    else -> 0L
                }
            }
        )

        assertNull(store.state.value.resumeSuggestion)
    }

    @Test
    fun `executePlaybackEndAction should not continue for stop`() {
        val coordinator = PlaybackCoordinator(PlaybackSessionStore())

        val continued = coordinator.executePlaybackEndAction(
            action = PlaybackEndAction.STOP,
            repeatCurrent = { error("repeat should not run for stop") },
            playNextInOrder = { _, _ -> error("next should not run for stop") },
            playNextFromPlaylistLoop = { _, _ -> error("playlist loop should not run for stop") },
            autoContinue = { _, _ -> error("auto continue should not run for stop") }
        )

        assertFalse(continued)
    }

    @Test
    fun `executePlaybackEndAction should repeat current immediately`() {
        val coordinator = PlaybackCoordinator(PlaybackSessionStore())
        var repeated = false

        val continued = coordinator.executePlaybackEndAction(
            action = PlaybackEndAction.REPEAT_CURRENT,
            repeatCurrent = { repeated = true },
            playNextInOrder = { _, _ -> false },
            playNextFromPlaylistLoop = { _, _ -> false },
            autoContinue = { _, _ -> }
        )

        assertTrue(repeated)
        assertTrue(continued)
    }

    @Test
    fun `executePlaybackEndAction should return false when next in order cannot continue`() {
        val coordinator = PlaybackCoordinator(PlaybackSessionStore())

        val continued = coordinator.executePlaybackEndAction(
            action = PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST,
            repeatCurrent = {},
            playNextInOrder = { _, _ -> false },
            playNextFromPlaylistLoop = { _, _ -> true },
            autoContinue = { _, _ -> }
        )

        assertFalse(continued)
    }

    @Test
    fun `executePlaybackEndAction should auto continue and return true`() {
        val coordinator = PlaybackCoordinator(PlaybackSessionStore())
        var autoContinued = false
        var ignoredSavedProgress = false
        var startsPlayback = false

        val continued = coordinator.executePlaybackEndAction(
            action = PlaybackEndAction.AUTO_CONTINUE,
            repeatCurrent = {},
            playNextInOrder = { _, _ -> false },
            playNextFromPlaylistLoop = { _, _ -> false },
            autoContinue = { ignoreSavedProgress, autoPlay ->
                autoContinued = true
                ignoredSavedProgress = ignoreSavedProgress
                startsPlayback = autoPlay
            }
        )

        assertTrue(autoContinued)
        assertTrue(ignoredSavedProgress)
        assertTrue(startsPlayback)
        assertTrue(continued)
    }

    @Test
    fun `ordered and looping playback start the next item after completion`() {
        val coordinator = PlaybackCoordinator(PlaybackSessionStore())
        for (action in listOf(PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST, PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST_LOOP)) {
            var nextStarted = false
            val continued = coordinator.executePlaybackEndAction(
                action = action,
                repeatCurrent = { error("unexpected repeat") },
                playNextInOrder = { ignoreProgress, autoPlay ->
                    assertEquals(PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST, action)
                    nextStarted = ignoreProgress && autoPlay
                    true
                },
                playNextFromPlaylistLoop = { ignoreProgress, autoPlay ->
                    assertEquals(PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST_LOOP, action)
                    nextStarted = ignoreProgress && autoPlay
                    true
                },
                autoContinue = { _, _ -> error("unexpected auto continuation") },
            )
            assertTrue(nextStarted)
            assertTrue(continued)
        }
    }
}
