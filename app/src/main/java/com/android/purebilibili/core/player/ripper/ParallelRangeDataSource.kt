package com.android.purebilibili.core.player.ripper

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.android.purebilibili.core.util.Logger
import java.io.IOException
import java.net.URI
import java.util.concurrent.ExecutorService

private const val TAG = "ParallelRangeDataSource"

/** 一次 open 决定是否走并发引擎所需的全部参数。 */
@UnstableApi
internal class ParallelRangeSpec(
    val config: ParallelRangeConfig,
    /** 可替换原地址主机名的候选 CDN 主机；为空则只用原地址。 */
    val alternateHosts: List<String> = emptyList(),
    /** 小于该长度的已知范围（init 段、sidx 探测等）直接走上游单连接。 */
    val minParallelBytes: Long = 2L * RIPPER_MIN_CHUNK_BYTES
)

/**
 * 把 media3 的顺序读取接口接到 [ParallelRangeEngine] 上。
 *
 * 播放器每次 `open` 一段范围，这里把它拆成多块并发下载，再按顺序从 `read` 吐出。
 * 非 HTTP、非 GET、范围很小或服务端不支持 Range 的请求都原样交给 [upstream]，
 * 所以放在 CacheDataSource 与 OkHttpDataSource 之间不会改变其它行为。
 */
@UnstableApi
internal class ParallelRangeDataSource(
    private val upstream: DataSource,
    private val spec: ParallelRangeSpec,
    private val fetcher: RangeChunkFetcher,
    private val executor: ExecutorService,
    private val listener: RangeTransferListener?
) : DataSource {

    private val listeners = ArrayList<TransferListener>()
    private var currentSpec: DataSpec? = null
    private var engine: ParallelRangeEngine? = null
    private var passthrough = false
    private var opened = false

    /** 直通模式下事件由 [upstream] 自己发出，并发模式下由本类发出，两者不会重复。 */
    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        check(!opened) { "DataSource already open" }
        currentSpec = dataSpec
        if (!shouldParallelize(dataSpec)) {
            passthrough = true
            opened = true
            return upstream.open(dataSpec)
        }
        listeners.forEach { it.onTransferInitializing(this, dataSpec, /* isNetwork = */ true) }
        val length = dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() }
        val candidate = ParallelRangeEngine(
            urls = candidateUrls(dataSpec.uri),
            position = dataSpec.position,
            length = length,
            config = spec.config,
            fetcher = fetcher,
            executor = executor,
            listener = listener,
            headers = dataSpec.httpRequestHeaders
        )
        val resolved = try {
            candidate.open()
        } catch (error: RangeNotSupportedException) {
            candidate.close()
            Logger.w(TAG, "Range unsupported, falling back to single connection: ${error.message}")
            passthrough = true
            opened = true
            return upstream.open(dataSpec)
        } catch (error: IOException) {
            candidate.close()
            throw error
        }
        engine = candidate
        opened = true
        listeners.forEach { it.onTransferStart(this, dataSpec, /* isNetwork = */ true) }
        return resolved ?: C.LENGTH_UNSET.toLong()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        val active = engine ?: throw IOException("DataSource not open")
        val read = active.read(buffer, offset, length)
        if (read < 0) return C.RESULT_END_OF_INPUT
        val dataSpec = currentSpec
        if (dataSpec != null) listeners.forEach { it.onBytesTransferred(this, dataSpec, /* isNetwork = */ true, read) }
        return read
    }

    override fun getUri(): Uri? = if (passthrough) upstream.uri else currentSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (passthrough) upstream.responseHeaders else emptyMap()

    @Throws(IOException::class)
    override fun close() {
        val wasPassthrough = passthrough
        val wasEngine = engine != null
        val dataSpec = currentSpec
        engine?.close()
        engine = null
        passthrough = false
        opened = false
        currentSpec = null
        if (wasPassthrough) {
            upstream.close()
        } else if (wasEngine && dataSpec != null) {
            listeners.forEach { it.onTransferEnd(this, dataSpec, /* isNetwork = */ true) }
        }
    }

    private fun shouldParallelize(dataSpec: DataSpec): Boolean {
        if (spec.config.concurrency <= 1) return false
        val scheme = dataSpec.uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET || dataSpec.httpBody != null) return false
        val length = dataSpec.length
        if (length != C.LENGTH_UNSET.toLong() && length < spec.minParallelBytes) return false
        return true
    }

    private fun candidateUrls(uri: Uri): List<String> {
        val original = uri.toString()
        if (spec.alternateHosts.isEmpty()) return listOf(original)
        val originalHost = uri.host?.lowercase().orEmpty()
        val alternates = spec.alternateHosts
            .map { it.lowercase() }
            .filter { it.isNotBlank() && it != originalHost }
            .mapNotNull { host -> rewriteHost(original, host) }
        return (listOf(original) + alternates).distinct()
    }

    /** 并发引擎的工厂；关闭功能时直接返回上游工厂创建的实例，零开销。 */
    @UnstableApi
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val specProvider: () -> ParallelRangeSpec?,
        private val fetcherProvider: () -> RangeChunkFetcher,
        private val executor: ExecutorService,
        private val listener: RangeTransferListener? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val upstream = upstreamFactory.createDataSource()
            val spec = specProvider() ?: return upstream
            return ParallelRangeDataSource(upstream, spec, fetcherProvider(), executor, listener)
        }
    }

    companion object {
        /** 只替换 bilivideo 域名下的主机名，其它域名（如 Akamai）保持原样。 */
        internal fun rewriteHost(url: String, newHost: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (host != "bilivideo.com" && !host.endsWith(".bilivideo.com")) return null
            val scheme = uri.scheme ?: return null
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            return "$scheme://$newHost$port${uri.rawPath.orEmpty()}$query"
        }
    }
}
