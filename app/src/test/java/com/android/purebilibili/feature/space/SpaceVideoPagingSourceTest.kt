package com.android.purebilibili.feature.space

import androidx.paging.PagingSource
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.data.model.response.VideoSortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SpaceVideoPagingSourceTest {
    private fun params(number: Int? = null) = PagingSource.LoadParams.Refresh(SpaceVideoPageKey(number), 1, false)
    private fun video(id: Long) = SpaceVideoItem(aid = id, bvid = "BV$id")

    @Test fun locatedWindowContinuesFromItsServerPageWithoutFetchingFirstPageAgain() = runTest {
        val requested = mutableListOf<Int>()
        val source = spaceVideoPagingSource(SpaceVideoPageChunk(4, listOf(video(100)), 180), VideoSortOrder.PUBDATE, 30) { number ->
            requested += number
            SpaceVideoPageChunk(number, listOf(video(101)), 180)
        }
        val seed = source.load(params()) as PagingSource.LoadResult.Page<SpaceVideoPageKey, SpaceVideoPageChunk>
        assertEquals(SpaceVideoPageKey(5), seed.nextKey)
        assertTrue(requested.isEmpty())
        source.load(params(5))
        assertEquals(listOf(5), requested)
    }

    @Test fun oldestFirstDecrementsPageUntilFirstServerPage() = runTest {
        val source = spaceVideoPagingSource(SpaceVideoPageChunk(2, listOf(video(1)), 60), VideoSortOrder.OLDEST_PUBDATE, 30) { number ->
            SpaceVideoPageChunk(number, listOf(video(2)), 60)
        }
        val seed = source.load(params()) as PagingSource.LoadResult.Page<SpaceVideoPageKey, SpaceVideoPageChunk>
        assertEquals(SpaceVideoPageKey(1), seed.nextKey)
        val last = source.load(params(1)) as PagingSource.LoadResult.Page<SpaceVideoPageKey, SpaceVideoPageChunk>
        assertNull(last.nextKey)
    }

    @Test fun retryKeepsFailedPageAndDuplicateOnlyResponseEndsPagination() = runTest {
        var fail = true
        val source = spaceVideoPagingSource(SpaceVideoPageChunk(1, listOf(video(1)), 100), VideoSortOrder.PUBDATE, 30) { number ->
            if (fail) { fail = false; error("暂时失败") }
            SpaceVideoPageChunk(number, listOf(video(1)), 100)
        }
        source.load(params())
        assertTrue(source.load(params(2)) is PagingSource.LoadResult.Error)
        val retried = source.load(params(2)) as PagingSource.LoadResult.Page<SpaceVideoPageKey, SpaceVideoPageChunk>
        assertTrue(retried.data.single().items.isEmpty())
        assertNull(retried.nextKey)
    }

    @Test fun cancellationPropagatesInsteadOfBecomingLoadFailure() = runTest {
        val source = spaceVideoPagingSource(SpaceVideoPageChunk(1, listOf(video(1)), 60), VideoSortOrder.PUBDATE, 30) {
            throw CancellationException("分类已切换")
        }
        source.load(params())
        try {
            source.load(params(2))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
