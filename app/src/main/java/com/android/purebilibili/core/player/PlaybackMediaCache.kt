package com.android.purebilibili.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.android.purebilibili.feature.plugin.CdnPlaybackDataSource
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PlaybackMediaCache"
private const val PLAYBACK_MEDIA_CACHE_DIR = "playback_media_cache"

internal data class PlaybackMediaCacheStats(
    val upstreamBytes: Long,
    val cachedBytes: Long,
    val ignoredCount: Int
)

// 128MB：跨会话分片缓存只服务「回看刚看过的片段」，不需要装下整个视频；
// 512MB 上限曾让 cache 目录轻松涨到 500MB+（用户反馈缓存过大、跳看流量浪费）。
internal fun resolvePlaybackMediaCacheMaxBytes(): Long = 128L * 1024L * 1024L

internal fun shouldUsePlaybackMediaCache(uri: Uri): Boolean {
    return shouldUsePlaybackMediaCache(uri.toString())
}

internal fun shouldUsePlaybackMediaCache(rawUri: String): Boolean {
    val scheme = runCatching { URI(rawUri).scheme }.getOrNull()
    return scheme.equals("http", ignoreCase = true) ||
        scheme.equals("https", ignoreCase = true)
}

internal fun buildPlaybackCacheKey(uri: Uri, explicitKey: String?): String {
    if (!explicitKey.isNullOrBlank()) return explicitKey
    return buildPlaybackCacheKey(rawUri = uri.toString(), explicitKey = null)
}

internal fun buildPlaybackCacheKey(rawUri: String, explicitKey: String?): String {
    if (!explicitKey.isNullOrBlank()) return explicitKey
    val parsed = runCatching { URI(rawUri) }.getOrNull()
    val scheme = parsed?.scheme
    val host = parsed?.host
    val path = parsed?.rawPath
    return if (!scheme.isNullOrBlank() && !host.isNullOrBlank() && !path.isNullOrBlank()) {
        "$scheme://$host$path"
    } else {
        rawUri
    }
}

/**
 * 追踪通过 Media3 [OkHttpDataSource] 发出的底层 OkHttp [Call]，涵盖等待响应头（`open()`）与
 * 阻塞读取响应体（`read()`）全生命周期，直到响应体读取完毕（EOF）、出错或关闭才释放句柄。
 */
internal class CancelableCallFactory(
    private val delegate: Call.Factory,
) : Call.Factory {
    private val canceled = AtomicBoolean(false)
    private val activeCalls = ConcurrentHashMap.newKeySet<Call>()

    override fun newCall(request: Request): Call {
        val raw = delegate.newCall(request)
        val tracked = TrackedCall(raw)
        activeCalls.add(tracked)
        if (canceled.get()) {
            // 无论 cancelAll() 是否已先移除该 Call，都执行幂等取消，避免注册与取消交错时漏取消。
            tracked.cancel()
        }
        return tracked
    }

    fun cancelAll() {
        canceled.set(true)
        // 直接迭代 ConcurrentHashMap 键集避免 toList() 在 size==1 并发删空时抛 NoSuchElementException。
        val iterator = activeCalls.iterator()
        while (iterator.hasNext()) {
            val call = iterator.next()
            iterator.remove()
            call.cancel()
        }
    }

    private inner class TrackedCall(
        private val raw: Call,
    ) : Call by raw {
        override fun execute(): Response {
            val response = try {
                raw.execute()
            } catch (error: Throwable) {
                activeCalls.remove(this)
                throw error
            }
            return wrapResponse(response)
        }

        override fun enqueue(responseCallback: Callback) {
            raw.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activeCalls.remove(this@TrackedCall)
                    responseCallback.onFailure(this@TrackedCall, e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val wrapped = wrapResponse(response)
                    responseCallback.onResponse(this@TrackedCall, wrapped)
                }
            })
        }

        override fun cancel() {
            activeCalls.remove(this)
            raw.cancel()
        }

        override fun clone(): Call = this@CancelableCallFactory.newCall(raw.request())

        private fun wrapResponse(response: Response): Response {
            val trackedBody = TrackingResponseBody(response.body) {
                activeCalls.remove(this@TrackedCall)
            }
            val wrapped = response.newBuilder()
                .body(trackedBody)
                .build()
            if (canceled.get()) {
                cancel()
            }
            return wrapped
        }
    }

    private class TrackingResponseBody(
        private val delegate: ResponseBody,
        private val onReleased: () -> Unit,
    ) : ResponseBody() {
        private val released = AtomicBoolean(false)
        private val trackedSource: BufferedSource by lazy {
            object : ForwardingSource(delegate.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long = try {
                    val read = super.read(sink, byteCount)
                    if (read == -1L) releaseOnce()
                    read
                } catch (error: IOException) {
                    releaseOnce()
                    throw error
                }

                override fun close() {
                    try {
                        super.close()
                    } finally {
                        releaseOnce()
                    }
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun source(): BufferedSource = trackedSource

        override fun close() {
            try {
                delegate.close()
            } finally {
                releaseOnce()
            }
        }

        private fun releaseOnce() {
            if (released.compareAndSet(false, true)) {
                onReleased()
            }
        }
    }
}

@UnstableApi
internal object PlaybackMediaCache {
    private val upstreamBytes = AtomicLong(0L)
    private val cachedBytes = AtomicLong(0L)
    private val ignoredCount = AtomicLong(0L)

    @Volatile
    private var simpleCache: SimpleCache? = null

    fun buildCachedDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): DataSource.Factory {
        val appContext = context.applicationContext
        // Building a player during entry must not open the disk cache. Resolve the
        // shared cache only when playback/prefetch first asks for a data source.
        val delegate: DataSource.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val monitoredUpstreamFactory = DataSource.Factory {
                CdnPlaybackDataSource(upstreamFactory.createDataSource()).apply {
                    addTransferListener(upstreamTransferListener)
                }
            }
            val cache = getOrCreateCache(appContext)
            if (cache == null) {
                monitoredUpstreamFactory
            } else {
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(monitoredUpstreamFactory)
                    .setCacheKeyFactory(playbackCacheKeyFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .setEventListener(cacheEventListener)
            }
        }
        return DataSource.Factory { delegate.createDataSource() }
    }

    /**
     * Gives mirrors of one authorized DASH track the same cache spans without changing the URL
     * used for the actual request. Unknown URLs retain the normal cache-key behavior.
     */
    fun buildCdnOptimizedDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory,
        cacheKeysByUrl: Map<String, String>
    ): DataSource.Factory {
        val cachedFactory = buildCachedDataSourceFactory(context, upstreamFactory)
        if (cacheKeysByUrl.isEmpty()) return cachedFactory
        return ResolvingDataSource.Factory(cachedFactory) { dataSpec ->
            val cacheKey = cacheKeysByUrl[dataSpec.uri.toString()]
            if (cacheKey.isNullOrBlank()) dataSpec else dataSpec.buildUpon().setKey(cacheKey).build()
        }
    }

    /** 构造带底层 Call 取消句柄的 OkHttpDataSource.Factory，供各预取路径在取消时中断 `open()`。 */
    fun buildCancelableOkHttpDataSourceFactory(
        callFactory: Call.Factory,
        defaultRequestProperties: Map<String, String>,
    ): Pair<DataSource.Factory, () -> Unit> {
        val cancelable = CancelableCallFactory(callFactory)
        val factory = OkHttpDataSource.Factory(cancelable)
            .setDefaultRequestProperties(defaultRequestProperties)
        return factory to cancelable::cancelAll
    }

    /** Caller-owned operation; cancellation closes active media requests and stops cache writes. */
    suspend fun prefetchRange(
        context: Context,
        upstreamFactory: DataSource.Factory,
        url: Uri,
        cacheKey: String,
        position: Long,
        length: Long,
        onCancelUpstream: (() -> Unit)? = null,
    ) {
        if (length <= 0L) return
        val cache = getOrCreateCache(context) ?: return
        val activeUpstream = AtomicReference<CdnPlaybackDataSource?>()
        val cancelableFactory = DataSource.Factory {
            CdnPlaybackDataSource(
                upstream = upstreamFactory.createDataSource(),
                background = true,
                onCancelDelegate = onCancelUpstream,
            ).also { activeUpstream.set(it) }
        }
        val cacheDataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(cancelableFactory)
            .setCacheKeyFactory(playbackCacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSourceForDownloading()
        val writer = CacheWriter(
            cacheDataSource,
            DataSpec.Builder()
                .setUri(url)
                .setKey(cacheKey)
                .setPosition(position)
                .setLength(length)
                .build(),
            null,
            null
        )
        val cancelTransfer = {
            writer.cancel()
            // CacheWriter closes the data source; only cancel its transport here.
            val upstream = activeUpstream.get()
            if (upstream != null) upstream.cancelPendingRequests() else onCancelUpstream?.invoke()
            Unit
        }
        coroutineScope {
            val reader = async {
                // Interrupt a cache lock wait within the lifetime of this worker operation.
                try {
                    runInterruptible(Dispatchers.IO) { writer.cache() }
                } catch (error: InterruptedIOException) {
                    // Media3 translates an interrupted cache wait into an I/O exception.
                    currentCoroutineContext().ensureActive()
                    throw error
                }
            }
            try {
                // Cancel OkHttp independently, including while its response body is blocked.
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation {
                        cancelTransfer()
                    }
                    reader.invokeOnCompletion { cause ->
                        if (continuation.isActive) {
                            if (cause == null) {
                                continuation.resume(Unit)
                            } else {
                                continuation.resumeWithException(cause)
                            }
                        }
                    }
                }
            } finally {
                cancelTransfer()
            }
        }
    }

    fun estimateBytes(context: Context): Long {
        return cacheDir(context).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun clear(context: Context): Result<Unit> =
        runCatching {
            simpleCache?.release()
            simpleCache = null
            val directory = cacheDir(context)
            check(!directory.exists() || directory.deleteRecursively()) { "部分媒体分片缓存无法清理" }
            Logger.d(TAG, "播放器媒体缓存已清理")
        }.onFailure { error ->
            Logger.w(TAG, "播放器媒体缓存清理失败: ${error.message}")
        }

    fun snapshotStats(): PlaybackMediaCacheStats {
        return PlaybackMediaCacheStats(
            upstreamBytes = upstreamBytes.get(),
            cachedBytes = cachedBytes.get(),
            ignoredCount = ignoredCount.get().toInt()
        )
    }

    fun logSeek(
        targetPositionMs: Long,
        currentPositionMs: Long,
        bufferedPositionMs: Long,
        durationMs: Long
    ) {
        val stats = snapshotStats()
        Logger.d(
            TAG,
            "seek target=$targetPositionMs current=$currentPositionMs buffered=$bufferedPositionMs " +
                "duration=$durationMs upstreamBytes=${stats.upstreamBytes} " +
                "cachedBytes=${stats.cachedBytes} ignored=${stats.ignoredCount}"
        )
    }

    private fun getOrCreateCache(context: Context): SimpleCache? {
        simpleCache?.let { return it }
        return synchronized(this) {
            simpleCache ?: runCatching {
                cacheDir(context).mkdirs()
                @Suppress("DEPRECATION")
                SimpleCache(
                    cacheDir(context),
                    LeastRecentlyUsedCacheEvictor(resolvePlaybackMediaCacheMaxBytes())
                )
            }.onSuccess {
                simpleCache = it
            }.onFailure { error ->
                Logger.w(TAG, "播放器媒体缓存初始化失败，降级为直接播放: ${error.message}")
            }.getOrNull()
        }
    }

    private fun cacheDir(context: Context): File {
        return File(context.cacheDir, PLAYBACK_MEDIA_CACHE_DIR)
    }

    private val playbackCacheKeyFactory = CacheKeyFactory { dataSpec ->
        val uri = dataSpec.uri
        if (shouldUsePlaybackMediaCache(uri)) {
            buildPlaybackCacheKey(uri = uri, explicitKey = dataSpec.key)
        } else {
            dataSpec.key ?: uri.toString()
        }
    }

    private val cacheEventListener = object : CacheDataSource.EventListener {
        override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
            cachedBytes.addAndGet(cachedBytesRead)
            Logger.d(TAG, "cache-hit bytes=$cachedBytesRead cacheSize=$cacheSizeBytes")
        }

        override fun onCacheIgnored(reason: Int) {
            ignoredCount.incrementAndGet()
            Logger.d(TAG, "cache-ignored reason=$reason")
        }
    }

    private val upstreamTransferListener = object : TransferListener {
        override fun onTransferInitializing(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) = Unit

        override fun onTransferStart(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) = Unit

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int
        ) {
            if (isNetwork) {
                upstreamBytes.addAndGet(bytesTransferred.toLong())
            }
        }

        override fun onTransferEnd(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) = Unit
    }
}
