package com.android.purebilibili.feature.video.usecase

import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.PlayerInfoData
import com.android.purebilibili.data.model.response.ViewInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmedPlaybackDataTest {
    private val info = ViewInfo(bvid = "BV1confirmed", cid = 22L)

    @Test
    fun historyOverlapsPlaybackAndPreservesConfirmedPageAndZeroProgress() = runTest {
        val playData = PlayUrlData(quality = 80)
        val history = PlayerInfoData(lastPlayCid = info.cid, lastPlayTime = 0L)
        val stages = mutableListOf<Pair<PlaybackDataLoadStage, Long>>()
        val result = loadConfirmedPlaybackData(
            info = info,
            loadPlayData = { delay(100); playData },
            loadHistory = { confirmed ->
                assertSame(info, confirmed)
                delay(300)
                history
            },
            onStage = { stages += it to testScheduler.currentTime },
        ).getOrThrow()

        // 串行读取需要 400ms；这里验证调度依赖，不代表实际网络性能。
        assertEquals(300L, testScheduler.currentTime)
        assertSame(playData, result.playData)
        assertSame(history, result.history)
        assertEquals(0L, result.history?.resumePositionMs(info.cid))
        assertEquals(
            listOf(
                PlaybackDataLoadStage.HISTORY_REQUEST_STARTED to 0L,
                PlaybackDataLoadStage.PLAY_URL_READY to 100L,
                PlaybackDataLoadStage.HISTORY_REQUEST_FINISHED to 300L,
            ),
            stages,
        )
    }

    @Test
    fun unavailableHistoryKeepsPlayableDataAndUnknownProgress() = runTest {
        val playData = PlayUrlData(quality = 64)
        val result = loadConfirmedPlaybackData(
            info = info,
            loadPlayData = { delay(100); playData },
            loadHistory = { null },
        ).getOrThrow()

        assertSame(playData, result.playData)
        assertNull(result.history)
    }

    @Test
    fun missingPlayUrlCancelsUnfinishedHistory() = runTest {
        val historyStarted = CompletableDeferred<Unit>()
        var historyCancelled = false
        val result = loadConfirmedPlaybackData(
            info = info,
            loadPlayData = { historyStarted.await(); null },
            loadHistory = {
                try {
                    historyStarted.complete(Unit)
                    awaitCancellation()
                } finally {
                    historyCancelled = true
                }
            },
        )

        assertTrue(result.isFailure)
        assertTrue(historyCancelled)
    }

    @Test
    fun cancellingVideoLoadCancelsBothRequests() = runTest {
        val playStarted = CompletableDeferred<Unit>()
        val historyStarted = CompletableDeferred<Unit>()
        var playCancelled = false
        var historyCancelled = false
        val load = async {
            loadConfirmedPlaybackData(
                info = info,
                loadPlayData = {
                    try {
                        playStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        playCancelled = true
                    }
                },
                loadHistory = {
                    try {
                        historyStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        historyCancelled = true
                    }
                },
            )
        }
        playStarted.await()
        historyStarted.await()
        load.cancelAndJoin()

        assertTrue(playCancelled)
        assertTrue(historyCancelled)
        assertTrue(load.isCancelled)
    }
}
