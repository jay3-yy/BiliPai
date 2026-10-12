package com.android.purebilibili.feature.plugin

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@OptIn(UnstableApi::class)
class CdnPlaybackDataSourceTest {
    private val url = "https://source.bilivideo.com/datasource-test/video.m4s?sig=a"
    private val bytes = ByteArray(800_000) { (it % 251).toByte() }

    @Test
    fun `multiple windows deliver each byte exactly once`() {
        withSource { request -> successful(request) }.use { fixture ->
            fixture.source.open(fixture.spec)
            assertContentEquals(bytes.copyOfRange(137, 700_137), readAll(fixture.source))
            assertEquals(0, fixture.upstream.opens)
        }
        assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
    }

    @Test
    fun `a rejected later window resumes upstream at delivered offset`() {
        withSource { request ->
            val start = request.header("Range")!!.substringAfter("bytes=").substringBefore('-').toLong()
            if (start >= 524_425) response(request, 200, "", ByteArray(0)) else successful(request)
        }.use { fixture ->
            fixture.source.open(fixture.spec)
            assertContentEquals(bytes.copyOfRange(137, 700_137), readAll(fixture.source))
            assertEquals(1, fixture.upstream.opens)
            assertEquals(524_425L, fixture.upstream.openPosition)
        }
        assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
    }

    @Test
    fun `unknown lengths use upstream without speculative ranges`() {
        withSource { request -> error("Unexpected parallel request: $request") }.use { fixture ->
            fixture.source.open(fixture.spec.buildUpon().setLength(C.LENGTH_UNSET.toLong()).build())
            assertContentEquals(bytes.copyOfRange(137, bytes.size), readAll(fixture.source))
            assertEquals(1, fixture.upstream.opens)
        }
    }

    @Test
    fun `canceling outstanding requests frees every shared slot`() {
        val queued = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val canceled = AtomicInteger()
        val uri = Uri.parse(url)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers {
            FakeCall(firstArg(), null, queued) { canceled.incrementAndGet() }
        }
        CdnTransferRuntime.configure(true, true)
        CdnTransferRuntime.register(listOf(url), emptyList())
        val source = CdnPlaybackDataSource(MemorySource(), client = client, isWifi = { true })
        source.open(DataSpec.Builder().setUri(uri).setLength(700_000).build())
        val reader = thread(isDaemon = true) {
            try { source.read(ByteArray(4_096), 0, 4_096) } catch (_: IOException) { /* expected cancellation */ }
            finally { finished.countDown() }
        }
        try {
            assertTrue(queued.await(2, TimeUnit.SECONDS))
            source.cancelPendingRequests()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertTrue(canceled.get() > 0)
            assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
        } finally {
            source.close()
            CdnTransferRuntime.configure(false, false)
            reader.join(2_000)
        }
    }

    private fun withSource(responder: (Request) -> Response): Fixture {
        val uri = Uri.parse(url)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers { FakeCall(firstArg(), responder) }
        CdnTransferRuntime.configure(true, true)
        CdnTransferRuntime.register(listOf(url), emptyList())
        CdnTransferRuntime.playback(0, 0)
        val upstream = MemorySource()
        return Fixture(
            CdnPlaybackDataSource(upstream, client = client, isWifi = { true }), upstream,
            DataSpec.Builder().setUri(uri).setPosition(137).setLength(700_000).build()
        )
    }

    private fun successful(request: Request): Response {
        val range = request.header("Range")!!.substringAfter("bytes=").split('-').map { it.toInt() }
        return response(request, 206, "bytes ${range[0]}-${range[1]}/${bytes.size}", bytes.copyOfRange(range[0], range[1] + 1))
    }

    private fun response(request: Request, code: Int, range: String, data: ByteArray): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .header("Content-Range", range).body(data.toResponseBody()).build()

    private fun readAll(source: DataSource): ByteArray {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(4_096)
        while (true) {
            val count = source.read(chunk, 0, chunk.size)
            if (count == C.RESULT_END_OF_INPUT) break
            output.write(chunk, 0, count)
        }
        return output.toByteArray()
    }

    private inner class MemorySource : DataSource {
        var opens = 0
        var openPosition = 0L
        private var spec: DataSpec? = null
        private var position = 0
        private var end = 0
        override fun open(dataSpec: DataSpec): Long {
            opens++
            openPosition = dataSpec.position
            spec = dataSpec
            position = dataSpec.position.toInt()
            end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) bytes.size else position + dataSpec.length.toInt()
            return (end - position).toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= end) return C.RESULT_END_OF_INPUT
            val count = minOf(length, end - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun getUri(): Uri? = spec?.uri
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun close() = Unit
    }

    private inner class Fixture(val source: CdnPlaybackDataSource, val upstream: MemorySource, val spec: DataSpec) : AutoCloseable {
        override fun close() { source.close(); CdnTransferRuntime.configure(false, false) }
    }

    private class FakeCall(
        private val request: Request,
        private val responder: ((Request) -> Response)?,
        private val queued: CountDownLatch? = null,
        private val onCancel: () -> Unit = {}
    ) : Call {
        private var callback: Callback? = null
        @Volatile private var canceled = false
        private val computedTags = ConcurrentHashMap<Class<*>, Any>()
        override fun request(): Request = request
        override fun execute(): Response = error("Only asynchronous transport is supported")
        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
            if (canceled) responseCallback.onFailure(this, IOException("canceled"))
            else if (responder != null) responseCallback.onResponse(this, responder.invoke(request))
            queued?.countDown()
        }
        override fun cancel() {
            if (!canceled) {
                canceled = true
                onCancel()
                callback?.onFailure(this, IOException("canceled"))
            }
        }
        override fun isExecuted(): Boolean = callback != null
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout()
        override fun <T : Any> tag(type: KClass<T>): T? = tag(type.java)
        override fun <T> tag(type: Class<out T>): T? {
            val cached = computedTags[type] ?: return request.tag(type)
            return type.cast(cached)
        }
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
            tag(type.java, computeIfAbsent)
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T {
            tag(type)?.let { return it }
            val computed = computeIfAbsent()
            return requireNotNull(type.cast(computedTags.putIfAbsent(type, computed) ?: computed))
        }
        override fun clone(): Call = FakeCall(request, responder, queued, onCancel)
    }
}
