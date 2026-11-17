package com.android.purebilibili.core.network.grpc

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class BiliGrpcCancellationTest {
    @Test
    fun cancelledRequestCancelsCallAndClosesLateResponseWithoutReadingIt() = runTest {
        val call = PendingCall()
        val request = launch { call.awaitGrpcPayload("/test") }
        runCurrent()
        request.cancelAndJoin()
        assertTrue(call.isCanceled())
        val body = TrackingBody(ProtoWire.frame(byteArrayOf(1)))
        call.callback.onResponse(call, call.response(body))
        assertTrue(body.closed)
        assertEquals(0, body.reads)
    }

    @Test
    fun cancellationRemainsConnectedWhileReadingTheResponseBody() = runTest {
        val call = PendingCall()
        val request = launch { call.awaitGrpcPayload("/test") }
        runCurrent()
        val body = TrackingBody(ProtoWire.frame(byteArrayOf(1))) {
            request.cancel()
            assertTrue(call.isCanceled())
            throw IOException("Canceled")
        }
        call.callback.onResponse(call, call.response(body))
        request.join()
        assertTrue(request.isCancelled)
        assertTrue(body.closed)
    }

    @Test
    fun successfulResponseIsDecodedAndClosedBeforeDelivery() = runTest {
        val call = PendingCall()
        val payload = byteArrayOf(1, 2, 3)
        val request = async { call.awaitGrpcPayload("/test") }
        runCurrent()
        val body = TrackingBody(ProtoWire.frame(payload))
        call.callback.onResponse(call, call.response(body))
        assertTrue(body.closed)
        assertContentEquals(payload, request.await())
    }

    private class TrackingBody(bytes: ByteArray, onRead: () -> Unit = {}) : ResponseBody() {
        var closed = false
        var reads = 0
        private val length = bytes.size.toLong()
        private val input = object : ForwardingSource(Buffer().write(bytes)) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads++
                onRead()
                return super.read(sink, byteCount)
            }
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = length
        override fun source(): BufferedSource = input
    }

    private class PendingCall : Call {
        lateinit var callback: Callback
        private var cancelled = false
        private var executed = false
        private val request = Request.Builder().url("https://example.com/test").build()
        override fun request(): Request = request
        override fun execute(): Response = error("Only asynchronous execution is supported")
        override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
        }
        override fun cancel() { cancelled = true }
        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout.NONE
        override fun addEventListener(eventListener: EventListener) = Unit
        override fun <T : Any> tag(type: KClass<T>): T? = request.tag(type.java)
        override fun <T> tag(type: Class<out T>): T? = request.tag(type)
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
            error("Call tags are not used by this transport")
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
            error("Call tags are not used by this transport")
        override fun clone(): Call = PendingCall()
        fun response(body: ResponseBody): Response = Response.Builder()
            .request(request).protocol(Protocol.HTTP_2).code(200).message("OK").body(body).build()
    }
}
