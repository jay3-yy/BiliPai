package com.android.purebilibili.core.player.ripper

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParallelRangeEngineTest {

    private val executor = Executors.newCachedThreadPool()
    private val file = Random(42).nextBytes(1_500_000)
    private val config = ParallelRangeConfig(concurrency = 4, chunkBytes = 128 * 1024, firstChunkBytes = 64 * 1024)

    @AfterTest
    fun tearDown() {
        executor.shutdownNow()
    }

    /** 内存里的“CDN”：按主机名可配置延迟、失败方式，并记录并发峰值。 */
    private inner class FakeCdn(
        private val reportTotal: Boolean = true,
        private val supportsRange: Boolean = true
    ) : RangeChunkFetcher {
        val inFlight = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)
        val requestsByHost = ConcurrentHashMap<String, AtomicInteger>()
        val latencyMsByHost = ConcurrentHashMap<String, Long>()
        val zeroByteHosts = ConcurrentHashMap.newKeySet<String>()
        val failOnceHosts = ConcurrentHashMap.newKeySet<String>()
        var blockUntil: CountDownLatch? = null

        override fun fetch(
            url: String,
            start: Long,
            length: Long,
            headers: Map<String, String>,
            cancel: CancelToken,
            listener: RangeTransferListener?
        ): ChunkResponse {
            val host = URI(url).host
            requestsByHost.getOrPut(host) { AtomicInteger(0) }.incrementAndGet()
            val current = inFlight.incrementAndGet()
            peakInFlight.updateAndGet { maxOf(it, current) }
            try {
                blockUntil?.let { latch ->
                    if (!latch.await(5, TimeUnit.SECONDS)) throw IOException("test latch timeout")
                    if (cancel.isCancelled) throw IOException("cancelled")
                }
                latencyMsByHost[host]?.let { Thread.sleep(it) }
                if (host in zeroByteHosts) throw EmptyChunkException("$host sent nothing")
                if (failOnceHosts.remove(host)) throw IOException("$host transient failure")
                if (!supportsRange) throw RangeNotSupportedException("$host ignored Range")
                if (start >= file.size) throw RangeOutOfBoundsException("$start beyond ${file.size}")
                val end = minOf(start + length, file.size.toLong()).toInt()
                val bytes = file.copyOfRange(start.toInt(), end)
                return ChunkResponse(
                    bytes = bytes,
                    contentRange = ContentRange(start, end - 1L, if (reportTotal) file.size.toLong() else null),
                    statusCode = 206
                )
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private fun readAll(engine: ParallelRangeEngine, bufferSize: Int = 7_001): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        while (true) {
            val n = engine.read(buffer, 0, buffer.size)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun engine(
        cdn: FakeCdn,
        urls: List<String> = listOf("https://a.bilivideo.com/v.m4s"),
        position: Long = 0L,
        length: Long? = null,
        config: ParallelRangeConfig = this.config
    ) = ParallelRangeEngine(urls, position, length, config, cdn, executor)

    @Test
    fun `reassembles the whole file in order when length is unknown`() {
        val cdn = FakeCdn()
        val engine = engine(cdn)
        assertEquals(file.size.toLong(), engine.open())
        assertContentEquals(file, readAll(engine))
        engine.close()
    }

    @Test
    fun `reads exactly the requested window from an offset`() {
        val cdn = FakeCdn()
        val engine = engine(cdn, position = 300_000, length = 500_000)
        assertEquals(500_000L, engine.open())
        assertContentEquals(file.copyOfRange(300_000, 800_000), readAll(engine))
        engine.close()
    }

    @Test
    fun `clamps a window that runs past the end of the file`() {
        val cdn = FakeCdn()
        val engine = engine(cdn, position = 1_400_000, length = 999_999)
        assertEquals(100_000L, engine.open())
        assertContentEquals(file.copyOfRange(1_400_000, file.size), readAll(engine))
        engine.close()
    }

    @Test
    fun `downloads chunks concurrently up to the configured limit`() {
        val cdn = FakeCdn().apply { latencyMsByHost["a.bilivideo.com"] = 30L }
        val engine = engine(cdn)
        engine.open()
        readAll(engine)
        engine.close()
        assertTrue(cdn.peakInFlight.get() in 2..4, "peak in-flight was ${cdn.peakInFlight.get()}")
    }

    @Test
    fun `spreads chunks across routes and retries a transient failure elsewhere`() {
        val cdn = FakeCdn().apply { failOnceHosts += "b.bilivideo.com" }
        val urls = listOf("https://a.bilivideo.com/v.m4s", "https://b.bilivideo.com/v.m4s")
        val engine = engine(cdn, urls = urls)
        engine.open()
        assertContentEquals(file, readAll(engine))
        engine.close()
        assertTrue(cdn.requestsByHost.getValue("a.bilivideo.com").get() > 0)
        assertTrue(cdn.requestsByHost.getValue("b.bilivideo.com").get() > 1)
    }

    @Test
    fun `bans a route that repeatedly returns zero bytes`() {
        val cdn = FakeCdn().apply { zeroByteHosts += "dead.bilivideo.com" }
        val urls = listOf("https://dead.bilivideo.com/v.m4s", "https://ok.bilivideo.com/v.m4s")
        val engine = engine(cdn, urls = urls, config = config.copy(hostBanThreshold = 2))
        engine.open()
        assertContentEquals(file, readAll(engine))
        engine.close()
        val deadRequests = cdn.requestsByHost.getValue("dead.bilivideo.com").get()
        assertTrue(deadRequests <= 3, "dead host was tried $deadRequests times")
    }

    @Test
    fun `surfaces range unsupported so the caller can fall back`() {
        val engine = engine(FakeCdn(supportsRange = false))
        assertFailsWith<RangeNotSupportedException> { engine.open() }
        engine.close()
    }

    @Test
    fun `falls back to sequential reads and detects eof when total is unknown`() {
        val cdn = FakeCdn(reportTotal = false)
        val engine = engine(cdn)
        assertNull(engine.open())
        assertContentEquals(file, readAll(engine))
        engine.close()
        assertEquals(1, cdn.peakInFlight.get())
    }

    @Test
    fun `position at or past eof yields no data instead of an error`() {
        val engine = engine(FakeCdn(), position = file.size.toLong())
        engine.open()
        assertEquals(-1, engine.read(ByteArray(16), 0, 16))
        engine.close()
    }

    @Test
    fun `close cancels in-flight chunks and unblocks readers`() {
        val cdn = FakeCdn().apply { blockUntil = CountDownLatch(1) }
        val engine = engine(cdn)
        val opener = Thread { runCatching { engine.open() } }
        opener.start()
        Thread.sleep(100)
        engine.close()
        cdn.blockUntil?.countDown()
        opener.join(2_000)
        assertTrue(!opener.isAlive, "open() did not return after close()")
        assertFailsWith<IOException> { engine.read(ByteArray(1), 0, 1) }
    }

    @Test
    fun `fails the read when every attempt on every route fails`() {
        val cdn = FakeCdn().apply { zeroByteHosts += "a.bilivideo.com" }
        val engine = engine(cdn, config = config.copy(maxAttemptsPerChunk = 2))
        assertFailsWith<IOException> { engine.open() }
        engine.close()
    }
}
