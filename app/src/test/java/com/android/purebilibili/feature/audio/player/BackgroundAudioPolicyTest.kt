package com.android.purebilibili.feature.audio.player

import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistItem
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAudioPolicyTest {
    private val request = BackgroundAudioSnapshot(
        queue = listOf(PlaylistItem("BV1", 1, "Title", "", "Author")), index = 0,
        positionMs = 5000, playing = true, speed = 1f, accountMid = 10,
        playMode = PlayMode.SEQUENTIAL, audioUrl = "https://example.com/audio.m4s"
    )

    private fun accepts(
        listening: Boolean = true, page: Boolean = false, video: Boolean = false,
        separateAccount: Boolean = false, state: BackgroundAudioSnapshot = request
    ) = canTransferBackgroundAudio(listening, page, video, separateAccount, state)

    @Test
    fun onlyDetachedListenVideoCanTransfer() {
        assertTrue(accepts())
        assertFalse(accepts(listening = false))
        assertFalse(accepts(page = true))
        assertFalse(accepts(video = true))
        assertFalse(accepts(separateAccount = true))
    }

    @Test
    fun invalidAndUnsupportedQueuesStayOnTheNativePath() {
        assertFalse(accepts(state = request.copy(queue = emptyList())))
        assertFalse(accepts(state = request.copy(index = -1)))
        assertFalse(accepts(state = request.copy(queue = request.queue + request.queue)))
        assertFalse(accepts(state = request.copy(queue = List(129) { request.queue.first() })))
        assertFalse(accepts(state = request.copy(queue = listOf(request.queue.first().copy(isBangumi = true)))))
        assertFalse(accepts(state = request.copy(audioUrl = "")))
        assertFalse(accepts(state = request.copy(audioUrl = "file:///private/audio")))
        assertFalse(accepts(state = request.copy(speed = Float.NaN)))
    }

    @Test
    fun sequentialPlaybackStopsAtTheEndAndRepeatAllWraps() {
        assertEquals(1, nextBackgroundAudioIndex(3, 0, PlayMode.SEQUENTIAL, false))
        assertNull(nextBackgroundAudioIndex(3, 2, PlayMode.SEQUENTIAL, false))
        assertEquals(0, nextBackgroundAudioIndex(3, 2, PlayMode.REPEAT_ALL, false))
        assertNull(nextBackgroundAudioIndex(0, 0, PlayMode.SEQUENTIAL, false))
    }

    @Test
    fun repeatOneAndShuffleRespectTheirTransportSemantics() {
        assertEquals(1, nextBackgroundAudioIndex(3, 1, PlayMode.REPEAT_ONE, false))
        assertEquals(0, nextBackgroundAudioIndex(1, 0, PlayMode.REPEAT_ALL, false))
        repeat(50) {
            val next = nextBackgroundAudioIndex(4, 2, PlayMode.REPEAT_ALL, true)
            assertTrue(next != null && next in 0..3)
            assertFalse(next == 2)
        }
    }

    @Test
    fun queueSnapshotRoundTripRetainsPositionAndPlaybackIntent() {
        val restored = backgroundAudioJson.decodeFromString(
            BackgroundAudioSnapshot.serializer(),
            backgroundAudioJson.encodeToString(BackgroundAudioSnapshot.serializer(), request)
        )
        assertEquals(request, restored)
        assertFalse(restored.reportHistory)
    }

    @Test
    fun acknowledgmentWaitResumesOnlyAfterTheFutureCompletes() = runTest {
        val future = SettableFuture.create<String>()
        val waiting = async { future.awaitBackgroundAudio() }
        yield()
        assertFalse(waiting.isCompleted)
        future.set("ready")
        assertEquals("ready", waiting.await())
    }

    @Test
    fun canceledHandoffCancelsItsPendingAcknowledgment() = runTest {
        val future = SettableFuture.create<String>()
        val waiting = async { future.awaitBackgroundAudio() }
        yield()
        waiting.cancelAndJoin()
        assertTrue(future.isCancelled)
    }
}
