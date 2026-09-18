package com.android.purebilibili.core.player.ripper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 当前多线程下载的快照，供播放器统计面板和设置页显示。 */
data class ThreadRipperSnapshot(
    /** 正在传输的连接数。 */
    val activeConnections: Int = 0,
    /** 最近一秒左右的总下载速度（字节/秒）。 */
    val bytesPerSecond: Long = 0L,
    /** 本次进程累计下载字节数。 */
    val totalBytes: Long = 0L,
    /** 每个 CDN 主机当前在途的连接数。 */
    val connectionsByHost: Map<String, Int> = emptyMap(),
    /** 累计失败的块数。 */
    val failedChunks: Int = 0
)

/** 设置页副标题：空闲时给出使用说明，下载中显示线程数、速度和节点。 */
fun formatThreadRipperStatus(snapshot: ThreadRipperSnapshot): String {
    if (snapshot.activeConnections == 0 && snapshot.bytesPerSecond == 0L) {
        return "把音视频拆成多个字节块并发下载，适合海外看冷门视频、4K 时单连接跑不满的情况"
    }
    val hosts = snapshot.connectionsByHost.entries
        .sortedByDescending { it.value }
        .joinToString(" · ") { (host, count) -> "${host.substringBefore('.')}×$count" }
    return buildString {
        append("当前 ${snapshot.activeConnections} 连接 · ${formatBytesPerSecond(snapshot.bytesPerSecond)}")
        if (hosts.isNotEmpty()) append(" · ").append(hosts)
        if (snapshot.failedChunks > 0) append(" · 失败 ${snapshot.failedChunks} 块")
    }
}

internal fun formatBytesPerSecond(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L -> String.format(java.util.Locale.ROOT, "%.1f MB/s", bytesPerSecond / 1048576.0)
    bytesPerSecond >= 1024L -> "${bytesPerSecond / 1024} KB/s"
    else -> "$bytesPerSecond B/s"
}

/** 全局统计：所有 [ParallelRangeEngine] 实例共用，轻量原子计数，按约 500ms 节流刷新快照。 */
internal object ThreadRipperStats : RangeTransferListener {
    private const val SAMPLE_WINDOW_MS = 1000L
    private const val PUBLISH_INTERVAL_MS = 500L

    private val active = AtomicInteger(0)
    private val total = AtomicLong(0L)
    private val failed = AtomicInteger(0)
    private val perHost = ConcurrentHashMap<String, AtomicInteger>()

    private val windowLock = Any()
    private var windowStartMs = 0L
    private var windowBytes = 0L
    private var lastSpeed = 0L
    private var lastPublishMs = 0L

    private val _snapshot = MutableStateFlow(ThreadRipperSnapshot())
    val snapshot: StateFlow<ThreadRipperSnapshot> = _snapshot.asStateFlow()

    override fun onChunkStarted(host: String) {
        active.incrementAndGet()
        perHost.getOrPut(host) { AtomicInteger(0) }.incrementAndGet()
        publish(force = true)
    }

    override fun onChunkBytes(host: String, bytes: Int) {
        total.addAndGet(bytes.toLong())
        synchronized(windowLock) {
            val now = System.currentTimeMillis()
            if (windowStartMs == 0L) windowStartMs = now
            windowBytes += bytes
            val elapsed = now - windowStartMs
            if (elapsed >= SAMPLE_WINDOW_MS) {
                lastSpeed = windowBytes * 1000L / elapsed
                windowStartMs = now
                windowBytes = 0L
            }
        }
        publish(force = false)
    }

    override fun onChunkFinished(host: String, success: Boolean) {
        active.updateAndGet { (it - 1).coerceAtLeast(0) }
        perHost[host]?.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (!success) failed.incrementAndGet()
        publish(force = true)
    }

    private fun publish(force: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(windowLock) {
            if (!force && now - lastPublishMs < PUBLISH_INTERVAL_MS) return
            lastPublishMs = now
            // 超过一个窗口没有新数据就把速度归零，避免暂停后一直显示旧值。
            if (now - windowStartMs > SAMPLE_WINDOW_MS * 2) lastSpeed = 0L
        }
        val activeNow = active.get()
        _snapshot.value = ThreadRipperSnapshot(
            activeConnections = activeNow,
            // 没有在途连接时速度直接归零，最后一块结束后不再显示残留数值。
            bytesPerSecond = if (activeNow == 0) 0L else lastSpeed,
            totalBytes = total.get(),
            connectionsByHost = perHost.filterValues { it.get() > 0 }.mapValues { it.value.get() },
            failedChunks = failed.get()
        )
    }
}
