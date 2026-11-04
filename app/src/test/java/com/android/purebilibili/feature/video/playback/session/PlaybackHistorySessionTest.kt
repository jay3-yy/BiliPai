package com.android.purebilibili.feature.video.playback.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackHistorySessionTest {
    @Test
    fun `opening and leaving an unplayed detail never captures new or restored progress`() {
        val session = PlaybackHistorySession()
        session.bindMedia("BV1video", 11L)
        session.onIsPlayingChanged(false) // Prepared and paused, including restore/seek callbacks.
        listOf(0L, 90_000L, 150_000L).forEach { restoredOrSeekPosition ->
            assertNull(session.captureProgress("BV1video", 11L, restoredOrSeekPosition, 300_000L, false))
            assertFalse(session.canWriteHistory("BV1video", 11L))
        }
    }

    @Test
    fun `playing then pausing still captures the correct exit position`() {
        val session = PlaybackHistorySession()
        session.bindMedia("BV1video", 11L)
        session.onIsPlayingChanged(true)
        session.onIsPlayingChanged(false)
        assertTrue(session.canWriteHistory("BV1video", 11L))
        assertEquals(
            PlaybackHistoryProgress("BV1video", 11L, 94_200L, 300_000L),
            session.captureProgress("BV1video", 11L, 94_200L, 300_000L, false)
        )
    }

    @Test
    fun `switching parts cannot report restored progress using the previous parts playback evidence`() {
        val session = PlaybackHistorySession()
        session.bindMedia("BV1video", 11L)
        session.onIsPlayingChanged(true)
        val oldPartExit = session.captureProgress("BV1video", 11L, 40_000L, 300_000L, false)
        session.bindMedia("BV1video", 22L, preserveStarted = true)
        session.onIsPlayingChanged(false)
        assertNull(session.captureProgress("BV1video", 22L, 180_000L, 300_000L, false))
        assertFalse(session.canWriteHistory("BV1video", 11L))
        assertEquals(PlaybackHistoryProgress("BV1video", 11L, 40_000L, 300_000L), oldPartExit)
        session.onIsPlayingChanged(true)
        assertEquals(181_000L, session.captureProgress("BV1video", 22L, 181_000L, 300_000L, false)?.positionMs)
    }

    @Test
    fun `returning to a paused shared player preserves evidence but a fresh load of the same video does not`() {
        val session = PlaybackHistorySession()
        session.bindMedia("BV1video", 11L)
        session.onIsPlayingChanged(true)
        session.onIsPlayingChanged(false)
        session.bindMedia("BV1video", 11L, preserveStarted = true)
        assertTrue(session.canWriteHistory("BV1video", 11L))
        session.bindMedia("BV1video", 11L)
        assertNull(session.captureProgress("BV1video", 11L, 90_000L, 300_000L, false))
    }

    @Test
    fun `a different video and invalid identities cannot inherit playback evidence`() {
        val session = PlaybackHistorySession()
        session.bindMedia("BV1old", 11L)
        session.onIsPlayingChanged(true)
        session.bindMedia("BV1new", 11L, preserveStarted = true)
        assertFalse(session.canWriteHistory("BV1new", 11L))
        session.bindMedia("", 0L)
        session.onIsPlayingChanged(true)
        assertFalse(session.canWriteHistory("", 0L))
    }

    @Test
    fun `a restored end state is not watched but a naturally finished playback records full duration`() {
        val session = PlaybackHistorySession()
        session.bindMedia("BV1video", 11L)
        assertNull(session.captureProgress("BV1video", 11L, 299_500L, 300_000L, true))
        session.onIsPlayingChanged(true)
        session.onIsPlayingChanged(false)
        assertEquals(300_000L, session.captureProgress("BV1video", 11L, 299_500L, 300_000L, true)?.positionMs)
    }
}
