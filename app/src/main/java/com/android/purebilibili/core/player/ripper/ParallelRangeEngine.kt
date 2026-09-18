package com.android.purebilibili.core.player.ripper

import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class ParallelRangeConfig(
    /** 同一资源最多同时在途的块数。 */
    val concurrency: Int = 8,
    /** 稳态块大小。 */
    val chunkBytes: Long = 512L * 1024L,
    /** 前几块用更小的尺寸，让首帧尽快可用。 */
    val firstChunkBytes: Long = 256L * 1024L,
    /** 已下载完成但播放器尚未读走的块数上限，超过后暂停调度，避免无限占用内存。 */
    val maxBufferedChunks: Int = concurrency * 2,
    /** 单个块最多重试次数（每次换一条路线）。 */
    val maxAttemptsPerChunk: Int = 4,
    /** 同一节点连续几次 0 字节失败后，本次会话不再使用它。 */
    val hostBanThreshold: Int = 2
) {
    fun normalized(): ParallelRangeConfig = copy(
        concurrency = clampRipperConcurrency(concurrency),
        chunkBytes = clampRipperChunkBytes(chunkBytes),
        firstChunkBytes = clampRipperChunkBytes(minOf(firstChunkBytes, chunkBytes)),
        maxBufferedChunks = maxBufferedChunks.coerceAtLeast(1),
        maxAttemptsPerChunk = maxAttemptsPerChunk.coerceAtLeast(1),
        hostBanThreshold = hostBanThreshold.coerceAtLeast(1)
    )
}

/**
 * IDM 式并发 Range 下载引擎：把 [position, position + length) 切成多个字节块，
 * 用多条连接同时拉取，再按原顺序通过 [read] 交给调用方。
 *
 * 线程模型：[open]/[read]/[close] 由播放器的加载线程顺序调用；块任务跑在 [executor] 上，
 * 所有共享状态都由 [lock] 保护。不依赖 Android 或 media3 类型，便于在 JVM 上单测。
 *
 * @param urls 同一资源在不同节点上的地址，首个为首选；块会在未被停用的节点间轮转。
 * @param length 需要读取的字节数，null 表示直到文件末尾。
 */
internal class ParallelRangeEngine(
    urls: List<String>,
    private val position: Long,
    private val length: Long?,
    config: ParallelRangeConfig,
    private val fetcher: RangeChunkFetcher,
    private val executor: ExecutorService,
    private val listener: RangeTransferListener? = null,
    private val headers: Map<String, String> = emptyMap()
) {
    private val config = config.normalized()
    private val routes = urls.filter { it.isNotBlank() }.distinct().map { Route(it) }

    private val lock = ReentrantLock()
    private val stateChanged = lock.newCondition()

    /** 按字节顺序排列的块，队头是播放器接下来要读的块。 */
    private val queue = ArrayDeque<ChunkTask>()
    private var nextIndex = 0
    private var nextPosition = position
    /** 距离本次读取结束还剩多少字节；null 表示总长度尚未知晓。 */
    private var remaining: Long? = length
    private var exhausted = false
    private var closed = false
    private var inFlight = 0
    private var routeCursor = 0

    /** 文件总长度（来自首个响应的 Content-Range），未知为 null。 */
    @Volatile
    var totalLength: Long? = null
        private set

    val activeConnections: Int get() = lock.withLock { inFlight }

    init {
        require(routes.isNotEmpty()) { "ParallelRangeEngine needs at least one url" }
        require(position >= 0L) { "position must be >= 0" }
    }

    /**
     * 提交第一批块并等待首块完成，以便确定实际可读长度。
     *
     * @return 本次可读的字节数；总长度未知时返回 null。
     * @throws RangeNotSupportedException 服务端不支持 Range，调用方应退回单连接。
     */
    @Throws(IOException::class)
    fun open(): Long? {
        lock.withLock {
            check(!closed) { "engine already closed" }
            scheduleLocked()
            val first = queue.firstOrNull() ?: return 0L
            awaitLocked(first)
            first.error?.let { throw it }
            val total = totalLength ?: return length
            val available = (total - position).coerceAtLeast(0L)
            return length?.let { minOf(it, available) } ?: available
        }
    }

    /**
     * 读取最多 [length] 字节到 [target]，没有数据时阻塞。返回 -1 表示已到末尾。
     */
    @Throws(IOException::class)
    fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        lock.withLock {
            while (true) {
                if (closed) throw InterruptedIOException("engine closed")
                val head = queue.firstOrNull()
                if (head == null) {
                    if (exhausted) return -1
                    scheduleLocked()
                    if (queue.isEmpty()) {
                        exhausted = true
                        return -1
                    }
                    continue
                }
                head.error?.let { throw it }
                val bytes = head.bytes
                if (bytes == null) {
                    awaitLocked(head)
                    continue
                }
                val count = minOf(length, head.unread.toInt())
                if (count > 0) {
                    System.arraycopy(bytes, head.readOffset, target, offset, count)
                    head.readOffset += count
                }
                if (head.unread == 0L) {
                    queue.removeFirst()
                    scheduleLocked()
                }
                if (count > 0) return count
            }
        }
    }

    fun close() {
        val tasks: List<ChunkTask>
        lock.withLock {
            if (closed) return
            closed = true
            tasks = queue.toList()
            queue.clear()
            stateChanged.signalAll()
        }
        tasks.forEach { task ->
            task.cancel.cancel()
            task.future?.cancel(true)
        }
    }

    // ---- 调度 ----

    private fun scheduleLocked() {
        if (closed || exhausted) return
        val bufferedLimit = config.concurrency + config.maxBufferedChunks
        while (inFlight < config.concurrency && queue.size < bufferedLimit) {
            val chunk = planNextChunk(
                index = nextIndex,
                position = nextPosition,
                remaining = remaining,
                chunkBytes = config.chunkBytes,
                firstChunkBytes = config.firstChunkBytes
            )
            if (chunk == null) {
                exhausted = true
                return
            }
            // 总长度未知时，先只放一块出去探明 Content-Range，避免一堆请求同时撞到 416。
            if (remaining == null && queue.isNotEmpty()) return
            val task = ChunkTask(chunk)
            queue.addLast(task)
            nextIndex += 1
            nextPosition += chunk.length
            remaining = remaining?.minus(chunk.length)
            inFlight += 1
            task.future = executor.submit { runTask(task) }
        }
    }

    private fun runTask(task: ChunkTask) {
        var lastError: IOException? = null
        repeat(config.maxAttemptsPerChunk) { attempt ->
            if (task.cancel.isCancelled) return@repeat
            val route = pickRoute(attempt) ?: return@repeat
            try {
                val response = fetcher.fetch(route.url, task.chunk.start, task.chunk.length, headers, task.cancel, listener)
                onChunkFetched(task, route, response)
                return
            } catch (error: IOException) {
                if (task.cancel.isCancelled) return
                lastError = error
                onChunkFailed(route, error)
                if (error is RangeOutOfBoundsException) {
                    // 请求窗口比文件长时会有块落在末尾之后（总长度可能还没从首块响应里拿到），
                    // 这种 416 就是文件末尾；只有明知起点在文件内还 416 才算错误。
                    val total = totalLength
                    if (total == null || task.chunk.start >= total) {
                        completeEmpty(task)
                    } else {
                        fail(task, error)
                    }
                    return
                }
                if (error is RangeNotSupportedException) {
                    fail(task, error)
                    return
                }
            } catch (error: InterruptedException) {
                return
            } catch (error: RuntimeException) {
                lastError = IOException("chunk ${task.chunk.index} failed on ${route.host}: ${error.message}", error)
            }
        }
        fail(task, lastError ?: InterruptedIOException("chunk ${task.chunk.index} cancelled"))
    }

    private fun onChunkFetched(task: ChunkTask, route: Route, response: ChunkResponse) {
        val expected = task.chunk.length
        val received = response.bytes.size.toLong()
        val contentRange = response.contentRange
        if (contentRange != null && contentRange.start != task.chunk.start) {
            fail(task, IOException("chunk ${task.chunk.index}: server returned range starting at ${contentRange.start}, expected ${task.chunk.start}"))
            return
        }
        lock.withLock {
            if (closed) return
            route.zeroByteFailures = 0
            if (totalLength == null) contentRange?.total?.let { totalLength = it }
            val total = totalLength
            if (total != null) {
                // 得知总长度后把还没发出去的部分收口到文件末尾，避免后续块撞到 416。
                val untilEnd = (total - nextPosition).coerceAtLeast(0L)
                remaining = remaining?.let { minOf(it, untilEnd) } ?: untilEnd
            }
            // 总长度已知时按它判断是否到底；未知时，短读本身就意味着文件结束。
            val reachedEnd = if (total != null) task.chunk.start + received >= total else received < expected
            if (received < expected && !reachedEnd) {
                fail(task, IOException("chunk ${task.chunk.index}: received $received of $expected bytes from ${route.host}"))
                return
            }
            if (received > expected) {
                fail(task, IOException("chunk ${task.chunk.index}: received $received bytes, more than requested $expected"))
                return
            }
            task.bytes = response.bytes
            task.host = route.host
            if (reachedEnd && received < expected) exhausted = true
            inFlight -= 1
            scheduleLocked()
            stateChanged.signalAll()
        }
    }

    private fun completeEmpty(task: ChunkTask) {
        lock.withLock {
            if (closed || task.error != null || task.bytes != null) return
            task.bytes = ByteArray(0)
            exhausted = true
            inFlight -= 1
            stateChanged.signalAll()
        }
    }

    private fun onChunkFailed(route: Route, error: IOException) {
        lock.withLock {
            if (error is EmptyChunkException) {
                route.zeroByteFailures += 1
                if (route.zeroByteFailures >= config.hostBanThreshold && routes.count { !it.banned } > 1) {
                    route.banned = true
                }
            }
        }
    }

    private fun fail(task: ChunkTask, error: IOException) {
        lock.withLock {
            if (task.error == null && task.bytes == null) {
                task.error = error
                inFlight -= 1
            }
            stateChanged.signalAll()
        }
    }

    /** 轮转选择一条未被停用的路线；重试时从下一条开始，避免连续撞同一个坏节点。 */
    private fun pickRoute(attempt: Int): Route? = lock.withLock {
        val candidates = routes.filter { !it.banned }.ifEmpty { routes }
        if (candidates.isEmpty()) return null
        val index = (routeCursor + attempt) % candidates.size
        if (attempt == 0) routeCursor = (routeCursor + 1) % candidates.size
        candidates[index]
    }

    private fun awaitLocked(task: ChunkTask) {
        while (!closed && task.bytes == null && task.error == null) {
            try {
                stateChanged.await()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("interrupted while waiting for chunk ${task.chunk.index}")
            }
        }
        if (closed) throw InterruptedIOException("engine closed")
    }

    private class Route(val url: String) {
        val host: String = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        var zeroByteFailures = 0
        var banned = false
    }

    private class ChunkTask(val chunk: RangeChunk) {
        val cancel = CancelToken()
        var future: Future<*>? = null
        var bytes: ByteArray? = null
        var host: String = ""
        var readOffset = 0
        var error: IOException? = null
        val unread: Long get() = (bytes?.size ?: 0).toLong() - readOffset
    }
}
