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
import okio.BufferedSource
import okio.Timeout
import okio.buffer
import okio.source
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.ResponseBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Test
    fun `canceling delegate transport interrupts enqueued OkHttpDataSource while waiting for headers`() {
        val callEnqueued = CountDownLatch(1)
        val canceledCount = AtomicInteger(0)
        val cancelableFactory = com.android.purebilibili.core.player.CancelableCallFactory(
            Call.Factory { request ->
                FakeCall(
                    request = request,
                    responder = null,
                    queued = callEnqueued,
                    onCancel = { canceledCount.incrementAndGet() },
                )
            }
        )
        val okHttpSource = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(cancelableFactory)
            .createDataSource()
        val source = CdnPlaybackDataSource(
            upstream = okHttpSource,
            background = true,
            onCancelDelegate = cancelableFactory::cancelAll,
        )
        val finished = CountDownLatch(1)
        val openThread = thread(isDaemon = true) {
            try {
                source.open(DataSpec.Builder().setUri(Uri.parse(url)).setLength(128_000L).build())
            } catch (_: IOException) {
                // Expected cancellation while OkHttpDataSource.open() waits for response headers.
            } finally {
                finished.countDown()
            }
        }
        try {
            assertTrue(callEnqueued.await(2, TimeUnit.SECONDS))
            source.cancelPendingRequests()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(1, canceledCount.get())
        } finally {
            source.close()
            openThread.join(2_000)
        }
    }

    @Test
    fun `canceling delegate transport interrupts enqueued OkHttpDataSource while body read is stalled`() {
        val bodyReadStarted = CountDownLatch(1)
        val bodyCanceled = CountDownLatch(1)
        val canceledCount = AtomicInteger(0)
        val stalledBody = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 128_000L
            override fun source(): BufferedSource = object : okio.Source {
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    bodyReadStarted.countDown()
                    assertTrue(bodyCanceled.await(2, TimeUnit.SECONDS))
                    throw IOException("Canceled during body read")
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {
                    bodyCanceled.countDown()
                }
            }.buffer()
        }
        val cancelableFactory = com.android.purebilibili.core.player.CancelableCallFactory(
            Call.Factory { request ->
                FakeCall(
                    request = request,
                    responder = { req ->
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(stalledBody)
                            .build()
                    },
                    onCancel = {
                        canceledCount.incrementAndGet()
                        bodyCanceled.countDown()
                    },
                )
            }
        )
        val okHttpSource = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(cancelableFactory)
            .createDataSource()
        val source = CdnPlaybackDataSource(
            upstream = okHttpSource,
            background = true,
            onCancelDelegate = cancelableFactory::cancelAll,
        )
        val finished = CountDownLatch(1)
        val readThread = thread(isDaemon = true) {
            try {
                source.open(DataSpec.Builder().setUri(Uri.parse(url)).setLength(128_000L).build())
                source.read(ByteArray(4_096), 0, 4_096)
            } catch (_: IOException) {
                // Expected cancellation while reading stalled response body after onResponse().
            } finally {
                finished.countDown()
            }
        }
        try {
            assertTrue(bodyReadStarted.await(2, TimeUnit.SECONDS))
            source.cancelPendingRequests()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(1, canceledCount.get())
        } finally {
            source.close()
            readThread.join(2_000)
        }
    }

    @Test
    fun `cancelable call factory cancels safely under concurrent registration and removal`() {
        val canceledCalls = AtomicInteger(0)
        val cancelableFactory = com.android.purebilibili.core.player.CancelableCallFactory(
            Call.Factory { request ->
                FakeCall(
                    request = request,
                    responder = null,
                    onCancel = { canceledCalls.incrementAndGet() },
                )
            }
        )
        val request = Request.Builder().url(url).build()
        val first = cancelableFactory.newCall(request)
        // 并发移除单个元素后再 cancelAll，验证不会触发 toList() 的 NoSuchElementException。
        first.cancel()
        cancelableFactory.cancelAll()
        assertEquals(1, canceledCalls.get())

        // cancelAll 之后新注册的 Call 仍会被立即取消，不漏取消。
        val late = cancelableFactory.newCall(request)
        assertTrue(late.isCanceled())
        assertEquals(2, canceledCalls.get())
    }

    @Test
    fun `streaming delivery returns head piece before trailing piece finishes`() {
        val trailingGate = CountDownLatch(1)
        val uri = Uri.parse(url)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            val start = request.header("Range")!!.substringAfter("bytes=").substringBefore('-').toLong()
            if (start > 137L) {
                DelayedFakeCall(request, trailingGate) { successful(request) }
            } else {
                FakeCall(request, ::successful)
            }
        }
        CdnTransferRuntime.configure(
            CdnParallelConfig(
                active = true,
                parallel = true,
                mirrorMode = CdnParallelMirrorMode.SAFE_SIGNED,
                concurrencyPreset = CdnConcurrencyPreset.THREADS_8,
            )
        )
        CdnTransferRuntime.register(listOf(url), emptyList())
        CdnTransferRuntime.playback(0, 0)
        val upstream = MemorySource()
        val source = CdnPlaybackDataSource(upstream, client = client, isWifi = { true })
        try {
            source.open(DataSpec.Builder().setUri(uri).setPosition(137).setLength(700_000).build())
            val firstBuf = ByteArray(4_096)
            val n = source.read(firstBuf, 0, firstBuf.size)
            assertEquals(4_096, n)
            assertContentEquals(bytes.copyOfRange(137, 137 + 4_096), firstBuf)
            trailingGate.countDown()
            val rest = ByteArrayOutputStream().apply { write(firstBuf) }
            rest.write(readAll(source))
            assertContentEquals(bytes.copyOfRange(137, 700_137), rest.toByteArray())
            assertEquals(0, upstream.opens)
        } finally {
            trailingGate.countDown()
            source.close()
            CdnTransferRuntime.configure(false, false)
        }
        assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
    }

    @Test
    fun `mainland mirror mode fans out single akamai portable url across multiple upos hosts`() {
        val akamaiUrl = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/2/video.m4s?sig=a"
        val requestedHosts = CopyOnWriteArrayList<String>()
        val uri = Uri.parse(akamaiUrl)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            requestedHosts += request.url.host
            FakeCall(request, ::successful)
        }
        CdnTransferRuntime.configure(
            CdnParallelConfig(
                active = true,
                parallel = true,
                mirrorMode = CdnParallelMirrorMode.MAINLAND_UPOS,
                concurrencyPreset = CdnConcurrencyPreset.THREADS_16,
            )
        )
        CdnTransferRuntime.register(listOf(akamaiUrl), emptyList())
        CdnTransferRuntime.playback(0, 0)
        val upstream = MemorySource()
        val source = CdnPlaybackDataSource(upstream, client = client, isWifi = { true })
        try {
            source.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(bytes.size.toLong()).build())
            assertContentEquals(bytes, readAll(source))
            assertEquals(0, upstream.opens)
            val distinctMainland = requestedHosts.distinct().filter { it in BTR_MAINLAND_UPOS_HOSTS }
            assertTrue(distinctMainland.size >= 3, "Expected fan-out across mainland hosts, saw $requestedHosts")
            assertEquals(16, CdnTransferRuntime.permits.availablePermits())
        } finally {
            source.close()
            CdnTransferRuntime.configure(false, false)
        }
        assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
    }

    @Test
    fun `stalled primary triggers hedge rescue with partial prefix resume`() {
        val primaryUrl = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/hedge.m4s?sig=a"
        val requestedRangesByHost = CopyOnWriteArrayList<Pair<String, String>>()
        val stalledReleased = CountDownLatch(1)
        val stallAttemptCount = AtomicInteger(0)
        val uri = Uri.parse(primaryUrl)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            val rangeHeader = request.header("Range").orEmpty()
            val host = request.url.host
            requestedRangesByHost += host to rangeHeader
            val parsed = rangeHeader.substringAfter("bytes=").split('-').map { it.toInt() }
            if (parsed[0] == 0 && stallAttemptCount.getAndIncrement() == 0) {
                StallingPrefixFakeCall(
                    request = request,
                    prefixBytes = bytes.copyOfRange(0, 40_960),
                    totalSize = bytes.size,
                    end = parsed[1],
                    releaseGate = stalledReleased,
                )
            } else {
                FakeCall(request, ::successful)
            }
        }
        CdnTransferRuntime.configure(
            CdnParallelConfig(
                active = true,
                parallel = true,
                mirrorMode = CdnParallelMirrorMode.MAINLAND_UPOS,
                concurrencyPreset = CdnConcurrencyPreset.THREADS_8,
            )
        )
        CdnTransferRuntime.register(listOf(primaryUrl), emptyList())
        CdnTransferRuntime.playback(5_000L, 0L)
        val upstream = MemorySource()
        val source = CdnPlaybackDataSource(upstream, client = client, isWifi = { true })
        try {
            source.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(600_000).build())
            assertContentEquals(bytes.copyOfRange(0, 600_000), readAll(source))
            assertEquals(0, upstream.opens)
            assertTrue(CdnTransferRuntime.state.value.rescueCount > 0)
            assertTrue(CdnTransferRuntime.state.value.resumedCount >= 0)
            assertTrue(
                requestedRangesByHost.any { (_, range) -> range.startsWith("bytes=40960-") },
                "Expected hedge rescue starting at partial prefix 40960, saw $requestedRangesByHost"
            )
            assertEquals(8, CdnTransferRuntime.permits.availablePermits())
        } finally {
            stalledReleased.countDown()
            source.close()
            CdnTransferRuntime.configure(false, false)
        }
        assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
    }

    @Test
    fun `open ended head probe falls back to upstream on http 200`() {
        val portableUrl = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/norange.m4s?sig=a"
        val uri = Uri.parse(portableUrl)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            FakeCall(request, { response(request, 200, "", ByteArray(0)) })
        }
        CdnTransferRuntime.configure(
            CdnParallelConfig(
                active = true,
                parallel = true,
                mirrorMode = CdnParallelMirrorMode.MAINLAND_UPOS,
                concurrencyPreset = CdnConcurrencyPreset.THREADS_8,
            )
        )
        CdnTransferRuntime.register(listOf(portableUrl), emptyList())
        CdnTransferRuntime.playback(0, 0)
        val upstream = MemorySource()
        val source = CdnPlaybackDataSource(upstream, client = client, isWifi = { true })
        try {
            source.open(
                DataSpec.Builder().setUri(uri).setPosition(200).setLength(C.LENGTH_UNSET.toLong()).build()
            )
            assertContentEquals(bytes.copyOfRange(200, bytes.size), readAll(source))
            assertEquals(1, upstream.opens)
            assertEquals(200L, upstream.openPosition)
            assertEquals(8, CdnTransferRuntime.permits.availablePermits())
        } finally {
            source.close()
            CdnTransferRuntime.configure(false, false)
        }
        assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
    }

    @Test
    fun `open ended stream probes head and streams full length in enhanced mode`() {
        val portableUrl = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/prog.m4s?sig=a"
        val uri = Uri.parse(portableUrl)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers { FakeCall(firstArg(), ::successful) }
        CdnTransferRuntime.configure(
            CdnParallelConfig(
                active = true,
                parallel = true,
                mirrorMode = CdnParallelMirrorMode.MAINLAND_UPOS,
                concurrencyPreset = CdnConcurrencyPreset.THREADS_8,
            )
        )
        CdnTransferRuntime.register(listOf(portableUrl), emptyList())
        CdnTransferRuntime.playback(0, 0)
        val upstream = MemorySource()
        val source = CdnPlaybackDataSource(upstream, client = client, isWifi = { true })
        try {
            val openedLength = source.open(
                DataSpec.Builder().setUri(uri).setPosition(100).setLength(C.LENGTH_UNSET.toLong()).build()
            )
            assertEquals((bytes.size - 100).toLong(), openedLength)
            assertContentEquals(bytes.copyOfRange(100, bytes.size), readAll(source))
            assertEquals(0, upstream.opens)
        } finally {
            source.close()
            CdnTransferRuntime.configure(false, false)
        }
        assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
    }

    @Test
    fun `cellular gate disables parallel unless allowCellular is enabled`() {
        val uri = Uri.parse(url)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers { FakeCall(firstArg(), ::successful) }

        CdnTransferRuntime.configure(
            CdnParallelConfig(
                active = true,
                parallel = true,
                mirrorMode = CdnParallelMirrorMode.SAFE_SIGNED,
                concurrencyPreset = CdnConcurrencyPreset.THREADS_8,
                allowCellular = false,
            )
        )
        CdnTransferRuntime.register(listOf(url), emptyList())
        val blockedUpstream = MemorySource()
        val blockedSource = CdnPlaybackDataSource(blockedUpstream, client = client, isWifi = { false })
        try {
            blockedSource.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(600_000).build())
            assertContentEquals(bytes.copyOfRange(0, 600_000), readAll(blockedSource))
            assertEquals(1, blockedUpstream.opens)
        } finally {
            blockedSource.close()
        }

        CdnTransferRuntime.configure(
            CdnParallelConfig(
                active = true,
                parallel = true,
                mirrorMode = CdnParallelMirrorMode.SAFE_SIGNED,
                concurrencyPreset = CdnConcurrencyPreset.THREADS_8,
                allowCellular = true,
            )
        )
        CdnTransferRuntime.register(listOf(url), emptyList())
        val allowedUpstream = MemorySource()
        val allowedSource = CdnPlaybackDataSource(allowedUpstream, client = client, isWifi = { false })
        try {
            allowedSource.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(600_000).build())
            assertContentEquals(bytes.copyOfRange(0, 600_000), readAll(allowedSource))
            assertEquals(0, allowedUpstream.opens)
        } finally {
            allowedSource.close()
            CdnTransferRuntime.configure(false, false)
        }
        assertEquals(CDN_MAX_CONNECTIONS, CdnTransferRuntime.permits.availablePermits())
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

    private class DelayedFakeCall(
        private val request: Request,
        private val gate: CountDownLatch,
        private val responder: (Request) -> Response,
    ) : Call {
        private var callback: Callback? = null
        @Volatile private var canceled = false
        private val computedTags = ConcurrentHashMap<Class<*>, Any>()
        override fun addEventListener(eventListener: okhttp3.EventListener) = Unit
        override fun request(): Request = request
        override fun execute(): Response = error("Only asynchronous transport is supported")
        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
            thread(isDaemon = true) {
                gate.await(3, TimeUnit.SECONDS)
                if (canceled) responseCallback.onFailure(this, IOException("canceled"))
                else responseCallback.onResponse(this, responder(request))
            }
        }
        override fun cancel() {
            canceled = true
        }
        override fun isExecuted(): Boolean = callback != null
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout()
        override fun <T : Any> tag(type: KClass<T>): T? = tag(type.java)
        override fun <T> tag(type: Class<out T>): T? = type.cast(computedTags[type] ?: request.tag(type))
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = tag(type.java, computeIfAbsent)
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T {
            tag(type)?.let { return it }
            val computed = computeIfAbsent()
            return requireNotNull(type.cast(computedTags.putIfAbsent(type, computed) ?: computed))
        }
        override fun clone(): Call = DelayedFakeCall(request, gate, responder)
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
        private val eventListeners = java.util.concurrent.CopyOnWriteArrayList<okhttp3.EventListener>()
        override fun addEventListener(eventListener: okhttp3.EventListener) {
            eventListeners.add(eventListener)
        }
        override fun request(): Request = request
        override fun execute(): Response = error("Only asynchronous transport is supported")
        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
            eventListeners.forEach { it.callStart(this) }
            if (canceled) responseCallback.onFailure(this, IOException("canceled"))
            else if (responder != null) responseCallback.onResponse(this, responder.invoke(request))
            queued?.countDown()
        }
        override fun cancel() {
            if (!canceled) {
                canceled = true
                eventListeners.forEach { it.canceled(this) }
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

    private class StallingPrefixFakeCall(
        private val request: Request,
        private val prefixBytes: ByteArray,
        private val totalSize: Int,
        private val end: Int,
        private val releaseGate: CountDownLatch,
    ) : Call {
        private var callback: Callback? = null
        @Volatile private var canceled = false
        private val computedTags = ConcurrentHashMap<Class<*>, Any>()
        override fun addEventListener(eventListener: okhttp3.EventListener) = Unit
        override fun request(): Request = request
        override fun execute(): Response = error("Only asynchronous transport is supported")
        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
            thread(isDaemon = true) {
                val stream = object : InputStream() {
                    private var offset = 0
                    override fun read(): Int {
                        val b = ByteArray(1)
                        val n = read(b, 0, 1)
                        return if (n < 0) -1 else b[0].toInt() and 0xff
                    }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (canceled) throw IOException("canceled")
                        if (offset < prefixBytes.size) {
                            val n = minOf(len, prefixBytes.size - offset)
                            prefixBytes.copyInto(b, off, offset, offset + n)
                            offset += n
                            return n
                        }
                        releaseGate.await(5, TimeUnit.SECONDS)
                        throw IOException("stalled")
                    }
                }
                val source = stream.source().buffer()
                val body = object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength(): Long = (end + 1).toLong()
                    override fun source(): BufferedSource = source
                }
                val response = Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial")
                    .header("Content-Range", "bytes 0-$end/$totalSize")
                    .body(body)
                    .build()
                responseCallback.onResponse(this, response)
            }
        }
        override fun cancel() {
            canceled = true
            releaseGate.countDown()
        }
        override fun isExecuted(): Boolean = callback != null
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout()
        override fun <T : Any> tag(type: KClass<T>): T? = tag(type.java)
        override fun <T> tag(type: Class<out T>): T? = type.cast(computedTags[type] ?: request.tag(type))
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = tag(type.java, computeIfAbsent)
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T {
            tag(type)?.let { return it }
            val computed = computeIfAbsent()
            return requireNotNull(type.cast(computedTags.putIfAbsent(type, computed) ?: computed))
        }
        override fun clone(): Call = StallingPrefixFakeCall(request, prefixBytes, totalSize, end, releaseGate)
    }
}
