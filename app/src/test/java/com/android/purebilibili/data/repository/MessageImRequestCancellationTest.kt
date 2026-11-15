package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.BiliGrpcClient
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageImRequestCancellationTest {
    @Test
    fun cancellingUnreadRefreshDoesNotMarkGatewayUnavailableOrStartFallbackRequests() = runTest {
        val paths = ConcurrentLinkedQueue<String>()
        val started = CompletableDeferred<Unit>()
        mockkObject(BiliGrpcClient)
        try {
            coEvery { BiliGrpcClient.requestCancellable(any(), any(), any(), any()) } coAnswers {
                paths += firstArg<String>()
                started.complete(Unit)
                awaitCancellation()
            }
            val cancelled = async { MessageImRepository.messageCenterUnread() }
            started.await()
            cancelled.cancelAndJoin()
            assertTrue(cancelled.isCancelled)
            val gatewayPath = "/bilibili.im.gateway.interface.v1.ImGatewayApi/GetTotalUnread"
            assertEquals(listOf(gatewayPath), paths.toList())

            coEvery { BiliGrpcClient.requestCancellable(any(), any(), any(), any()) } coAnswers {
                paths += firstArg<String>()
                byteArrayOf()
            }
            assertTrue(MessageImRepository.messageCenterUnread().isSuccess)
            assertEquals(listOf(gatewayPath, gatewayPath), paths.toList())
        } finally {
            unmockkObject(BiliGrpcClient)
        }
    }
}
