package com.android.purebilibili.core.player.ripper

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** 一个字节块的响应。[bytes] 长度可能小于请求长度（只有在总长度未知、到达文件末尾时才允许）。 */
internal class ChunkResponse(
    val bytes: ByteArray,
    val contentRange: ContentRange?,
    val statusCode: Int
)

/** 服务端对 Range 请求回了 200 完整文件，无法并发分块，调用方应退回单连接。 */
internal class RangeNotSupportedException(message: String) : IOException(message)

/** 请求的范围超出文件末尾（HTTP 416）。 */
internal class RangeOutOfBoundsException(message: String) : IOException(message)

/** 节点没有返回任何数据就断开；连续出现会把该节点在本次播放中停用。 */
internal class EmptyChunkException(message: String) : IOException(message)

/** 跨线程取消令牌，注册的回调在取消时立刻执行（例如 `Call.cancel()`）。 */
internal class CancelToken {
    private val cancelled = AtomicBoolean(false)
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    val isCancelled: Boolean get() = cancelled.get()

    fun register(callback: () -> Unit) {
        callbacks += callback
        if (cancelled.get()) callback()
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        callbacks.forEach { callback -> runCatching { callback() } }
    }
}

/** 字节块传输进度回调，用于侧边统计（线程数、速度、节点）。 */
internal interface RangeTransferListener {
    fun onChunkStarted(host: String) {}
    fun onChunkBytes(host: String, bytes: Int) {}
    fun onChunkFinished(host: String, success: Boolean) {}
}

/**
 * 阻塞式拉取一个字节块。实现必须在 [cancel] 触发后尽快返回（抛出 IOException 即可），
 * 且只能通过 [listener] 汇报本块自己的进度。[headers] 是本次请求附加的 HTTP 头。
 */
internal interface RangeChunkFetcher {
    @Throws(IOException::class)
    fun fetch(
        url: String,
        start: Long,
        length: Long,
        headers: Map<String, String>,
        cancel: CancelToken,
        listener: RangeTransferListener?
    ): ChunkResponse
}
