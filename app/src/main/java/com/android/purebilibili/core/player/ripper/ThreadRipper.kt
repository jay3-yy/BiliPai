package com.android.purebilibili.core.player.ripper

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.PlayerSettingsCache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 并发线程数的可选档位，与浏览器插件保持一致：8 起步，不够再加。 */
val THREAD_RIPPER_CONCURRENCY_OPTIONS: List<Int> = listOf(1, 2, 4, 8, 16, 32)
const val DEFAULT_THREAD_RIPPER_ENABLED = false
const val DEFAULT_THREAD_RIPPER_CONCURRENCY = 8
const val DEFAULT_THREAD_RIPPER_MULTI_HOST = false

/** 多线路分摊时可用的大陆 CDN 主机，块会在原地址与这些主机之间轮转。 */
internal val THREAD_RIPPER_MAINLAND_HOSTS: List<String> = listOf(
    "upos-sz-mirrorali.bilivideo.com",
    "upos-sz-mirrorhw.bilivideo.com",
    "upos-sz-mirrorcos.bilivideo.com",
    "upos-sz-mirror08c.bilivideo.com",
    "upos-sz-mirrorbd.bilivideo.com",
    "upos-sz-mirrorbos.bilivideo.com"
)

internal fun resolveThreadRipperSpec(
    enabled: Boolean,
    concurrency: Int,
    multiHost: Boolean
): ParallelRangeSpec? {
    if (!enabled) return null
    val threads = clampRipperConcurrency(concurrency)
    if (threads <= 1) return null
    return ParallelRangeSpec(
        config = ParallelRangeConfig(concurrency = threads),
        alternateHosts = if (multiHost) THREAD_RIPPER_MAINLAND_HOSTS else emptyList()
    )
}

/**
 * “线程撕裂者”多线程下载的装配点：把设置、专用 HTTP/1.1 客户端和共享线程池组装成
 * 可插在播放链路上游的 [DataSource.Factory]。
 */
@UnstableApi
internal object ThreadRipper {

    /** 所有播放器实例共用；视频与音频两条轨道同时下载时线程数最多为并发档位的两倍。 */
    private val executor: ExecutorService by lazy {
        val counter = AtomicInteger(0)
        val factory = ThreadFactory { runnable ->
            Thread(runnable, "thread-ripper-${counter.incrementAndGet()}").apply { isDaemon = true }
        }
        // 核心线程数即上限，空闲 30 秒回收；超出上限的任务排队而不是被拒绝。
        val poolSize = RIPPER_MAX_CONCURRENCY * 2
        ThreadPoolExecutor(poolSize, poolSize, 30L, TimeUnit.SECONDS, LinkedBlockingQueue(), factory)
            .apply { allowCoreThreadTimeOut(true) }
    }

    /**
     * 强制 HTTP/1.1：每个块独占一条 TCP 连接，才能在高延迟线路上叠加吞吐。
     * 不复用 OkHttp 磁盘缓存，媒体缓存由上层 CacheDataSource 负责。
     */
    private val client: OkHttpClient by lazy {
        NetworkModule.playbackOkHttpClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(RIPPER_MAX_CONCURRENCY * 2, 1L, TimeUnit.MINUTES))
            .cache(null)
            .readTimeout(15L, TimeUnit.SECONDS)
            .build()
    }

    fun currentSpec(context: Context): ParallelRangeSpec? = resolveThreadRipperSpec(
        enabled = PlayerSettingsCache.isThreadRipperEnabled(context),
        concurrency = PlayerSettingsCache.getThreadRipperConcurrency(context),
        multiHost = PlayerSettingsCache.isThreadRipperMultiHostEnabled(context)
    )

    /**
     * 用并发引擎包装 [upstreamFactory]。设置关闭时工厂直接返回上游实例，没有任何额外开销。
     *
     * @param headers 与上游 OkHttpDataSource 相同的默认请求头（Referer、User-Agent）。
     */
    fun wrap(
        context: Context,
        upstreamFactory: DataSource.Factory,
        headers: Map<String, String>
    ): DataSource.Factory {
        val appContext = context.applicationContext
        return ParallelRangeDataSource.Factory(
            upstreamFactory = upstreamFactory,
            specProvider = { currentSpec(appContext) },
            fetcherProvider = { OkHttpRangeChunkFetcher(client, headers) },
            executor = executor,
            listener = ThreadRipperStats
        )
    }
}
