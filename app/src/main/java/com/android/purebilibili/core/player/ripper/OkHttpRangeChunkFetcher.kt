package com.android.purebilibili.core.player.ripper

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI

/**
 * 用 OkHttp 拉取单个字节块。每个块一次 `Range` 请求，严格校验状态码与返回长度。
 *
 * 注意：调用方应传入强制 HTTP/1.1 的客户端。HTTP/2 会把同一主机的多个请求复用到一条 TCP
 * 连接上，并发块只是共享同一个拥塞窗口，达不到多连接聚合带宽的目的。
 */
internal class OkHttpRangeChunkFetcher(
    private val client: OkHttpClient,
    private val defaultHeaders: Map<String, String>
) : RangeChunkFetcher {

    override fun fetch(
        url: String,
        start: Long,
        length: Long,
        headers: Map<String, String>,
        cancel: CancelToken,
        listener: RangeTransferListener?
    ): ChunkResponse {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        val request = Request.Builder()
            .url(url)
            .apply {
                defaultHeaders.forEach { (name, value) -> header(name, value) }
                headers.forEach { (name, value) -> header(name, value) }
            }
            .header("Range", "bytes=$start-${start + length - 1}")
            .header("Accept-Encoding", "identity")
            .build()
        val call = client.newCall(request)
        cancel.register { call.cancel() }
        listener?.onChunkStarted(host)
        var success = false
        try {
            call.execute().use { response ->
                when (response.code) {
                    206 -> Unit
                    200 -> throw RangeNotSupportedException("$host ignored Range request (HTTP 200)")
                    416 -> throw RangeOutOfBoundsException("$host: range $start-${start + length - 1} out of bounds")
                    else -> throw IOException("$host responded HTTP ${response.code} for range $start-${start + length - 1}")
                }
                val contentRange = parseContentRange(response.header("Content-Range"))
                val body = response.body ?: throw EmptyChunkException("$host returned no body")
                val buffer = ByteArray(length.toInt())
                var filled = 0
                body.byteStream().use { stream ->
                    while (filled < buffer.size) {
                        val read = stream.read(buffer, filled, buffer.size - filled)
                        if (read < 0) break
                        filled += read
                        listener?.onChunkBytes(host, read)
                    }
                }
                if (filled == 0) throw EmptyChunkException("$host closed the connection with 0 bytes")
                success = true
                return ChunkResponse(
                    bytes = if (filled == buffer.size) buffer else buffer.copyOf(filled),
                    contentRange = contentRange,
                    statusCode = response.code
                )
            }
        } finally {
            listener?.onChunkFinished(host, success)
        }
    }
}
