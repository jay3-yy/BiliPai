package com.android.purebilibili.feature.plugin

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class CdnRangeSample(val bytes: ByteArray, val firstByteMs: Long, val elapsedMs: Long)

/** Strict, bounded range read with immediate socket cancellation when its caller is canceled. */
internal suspend fun readExactCdnRange(
    client: OkHttpClient,
    url: String,
    range: CdnByteRange,
    onProgress: ((bytesRead: Int, elapsedMs: Long) -> Unit)? = null,
): CdnRangeSample {
    require(range.start >= 0 && range.endInclusive >= range.start && range.length in 1..CDN_WINDOW_BYTES)
    return suspendCancellableCoroutine { continuation ->
        val startedAt = System.nanoTime()
        val request = Request.Builder().url(url)
            .header("Range", "bytes=${range.start}-${range.endInclusive}")
            .header("Referer", "https://www.bilibili.com")
            .header("Accept-Encoding", "identity")
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val firstByteMs = ((System.nanoTime() - startedAt) / 1_000_000).coerceAtLeast(1)
                    val bytes = response.use {
                        if (!isExactCdnRange(it.code, it.header("Content-Range"), range)) throw IOException("CDN invalid range: HTTP ${it.code}")
                        val input = it.body?.byteStream() ?: throw IOException("CDN empty body")
                        input.use {
                            val data = ByteArray(range.length.toInt())
                            var offset = 0
                            var lastProgressAt = 0L
                            while (offset < data.size) {
                                if (call.isCanceled()) throw IOException("CDN request canceled")
                                val read = input.read(data, offset, data.size - offset)
                                if (read < 0) throw IOException("CDN truncated range")
                                offset += read
                                if (onProgress != null) {
                                    val elapsed = ((System.nanoTime() - startedAt) / 1_000_000).coerceAtLeast(1)
                                    if (elapsed - lastProgressAt >= 100 || offset == data.size) {
                                        onProgress(offset, elapsed)
                                        lastProgressAt = elapsed
                                    }
                                }
                            }
                            if (input.read() != -1) throw IOException("CDN oversized range")
                            data
                        }
                    }
                    val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000).coerceAtLeast(1)
                    if (continuation.isActive) continuation.resume(CdnRangeSample(bytes, firstByteMs, elapsedMs))
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
