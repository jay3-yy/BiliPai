package com.android.purebilibili.data.repository

import androidx.paging.PagingSource
import com.android.purebilibili.core.network.BuvidManager
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.grpc.BiliGrpcClient
import com.android.purebilibili.core.network.grpc.ProtoWire
import com.android.purebilibili.feature.comment.SubReplyPageChunk
import com.android.purebilibili.feature.comment.subReplyPagingSource
import com.android.purebilibili.feature.video.viewmodel.SubReplyPageRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class CommentThreadCompletenessTest {
    @Test
    fun `opening a late child retains replies before its server seek window`() = runTest {
        val client = NetworkModule.okHttpClient
        mockkObject(BiliGrpcClient, BuvidManager, NetworkModule, client)
        try {
            coEvery { BuvidManager.ensureBuvid3(any()) } returns Unit
            every { NetworkModule.appContext } returns null
            every { client.newCall(any()) } answers {
                val request = firstArg<Request>()
                check(request.url.encodedPath == "/x/v2/reply/detail") { "Unexpected HTTP request: ${request.url}" }
                val call = mockk<Call>(relaxed = true)
                every { call.request() } returns request
                every { call.enqueue(any()) } answers {
                    firstArg<Callback>().onResponse(call, Response.Builder()
                        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body("""{"code":0,"data":{"root":{"rpid":25,"oid":100,"type":1,"root":777,"parent":24}}}"""
                            .toResponseBody("application/json".toMediaType()))
                        .build())
                }
                call
            }
            every { BiliGrpcClient.request(any(), any(), any(), any()) } answers {
                val fields = ProtoWire.parseFields(secondArg())
                val anchor = fields.firstOrNull { it.number == 4 }?.varint ?: 0L
                val hasContinuation = fields.any { it.number == 8 }
                // DetailList seek returns a tail window with isEnd=true, not the whole floor.
                // This mirrors the observed 22/26 result when opening a late child.
                when {
                    anchor != 0L -> page(5L..26L, isEnd = true)
                    hasContinuation -> page(21L..26L, isEnd = true)
                    else -> page(1L..20L, isEnd = false)
                }
            }
            val source = subReplyPagingSource { request ->
                CommentRepository.getMobileSortedSubCommentsForSubject(
                    oid = 100L,
                    type = 1,
                    rootId = 777L,
                    mode = CommentGrpcRepository.MODE_TIME,
                    paginationOffset = request.offset,
                    targetReplyId = 25L,
                    supplementLocations = false,
                ).getOrThrow()
            }
            val initial = source.load(PagingSource.LoadParams.Refresh(null, 1, false))
            if (initial is PagingSource.LoadResult.Error) throw initial.throwable
            val first = assertIs<PagingSource.LoadResult.Page<SubReplyPageRequest, SubReplyPageChunk>>(initial)
            val second = assertIs<PagingSource.LoadResult.Page<SubReplyPageRequest, SubReplyPageChunk>>(
                source.load(PagingSource.LoadParams.Append(requireNotNull(first.nextKey), 1, false)),
            )
            val pages = first.data + second.data
            assertEquals((1L..26L).toList(), pages.flatMap { it.data.replies.orEmpty() }.map { it.rpid })
            assertEquals(777L, pages.last().data.root?.rpid)
            assertEquals(26, pages.last().data.getAllCount())
            assertFalse(pages.any { it.data.isTargetOnly })
            assertNull(second.nextKey)
        } finally {
            unmockkObject(BiliGrpcClient, BuvidManager, NetworkModule, client)
        }
    }

    private fun page(ids: LongRange, isEnd: Boolean): ByteArray {
        val root = ProtoWire.message(
            ProtoWire.int64(2, 777L),
            ProtoWire.int64(3, 100L),
            ProtoWire.int64(4, 1L),
            ProtoWire.int64(11, 26L),
            *ids.map { id -> ProtoWire.bytes(1, ProtoWire.message(
                ProtoWire.int64(2, id),
                ProtoWire.int64(3, 100L),
                ProtoWire.int64(4, 1L),
                ProtoWire.int64(6, 777L),
            )) }.toTypedArray(),
        )
        return ProtoWire.message(
            ProtoWire.bytes(1, ProtoWire.message(ProtoWire.bool(4, isEnd))),
            ProtoWire.bytes(3, root),
            ProtoWire.bytes(8, ProtoWire.message(ProtoWire.string(1, if (isEnd) "" else "remaining"))),
        )
    }
}
