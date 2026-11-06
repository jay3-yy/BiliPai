package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.LiveFeedHomeSnapshot
import com.android.purebilibili.data.model.response.LiveRoom
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class LiveFeedHomeLoaderTest {
    @Test
    fun roomsAreDeliveredWhileFollowPaginationIsStillWaiting() = runTest {
        val snapshot = LiveFeedHomeSnapshot(rooms = listOf(LiveRoom(roomid = 7)), hasMore = true)
        val followGate = CompletableDeferred<LivePagedResult<LiveRoom>>()
        val visible = mutableListOf<LiveFeedHomeSnapshot>()
        val pending = async {
            completeLiveFeedHome(snapshot, true, { visible += it }) {
                Result.success(followGate.await())
            }
        }
        runCurrent()
        assertSame(snapshot, visible.single())
        assertNull(visible.single().totalCount)
        assertFalse(pending.isCompleted)

        followGate.complete(LivePagedResult(listOf(LiveRoom(roomid = 9)), false, 2, 1))
        val completed = pending.await()
        assertEquals(snapshot.rooms, completed.rooms)
        assertEquals(listOf(9L), completed.followRooms.map { it.roomid })
        assertEquals(1, completed.totalCount)
        assertEquals(true, completed.hasMore)
    }

    @Test
    fun confirmedZeroAndLoggedOutSnapshotsDoNotRequestFollowSupplement() = runTest {
        for ((snapshot, loggedIn) in listOf(
            LiveFeedHomeSnapshot(hasFollowModule = true, totalCount = 0) to true,
            LiveFeedHomeSnapshot() to false,
        )) {
            val delivered = mutableListOf<LiveFeedHomeSnapshot>()
            val completed = completeLiveFeedHome(snapshot, loggedIn, { delivered += it }) {
                error("不应补取关注列表")
            }
            assertSame(snapshot, delivered.single())
            assertSame(snapshot, completed)
        }
    }

    @Test
    fun failedFollowSupplementPreservesRoomsAndUnknownCount() = runTest {
        val snapshot = LiveFeedHomeSnapshot(rooms = listOf(LiveRoom(roomid = 7)))
        val completed = completeLiveFeedHome(snapshot, true, {}) {
            Result.failure(IOException("关注请求失败"))
        }
        assertEquals(snapshot.rooms, completed.rooms)
        assertNull(completed.totalCount)
        assertEquals("关注请求失败", completed.followError)
    }
}
