package com.android.purebilibili.core.plugin.feed

import com.android.purebilibili.data.repository.OnDemandRequestCache
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import kotlin.coroutines.resume

internal data class PublicLinkPreview(
    val title: String,
    val description: String,
    val imageUrl: HttpUrl?,
)

internal val publicLinkPreviewCache = OnDemandRequestCache<HttpUrl, PublicLinkPreview?>(
    maxEntries = 128,
    maxAgeMs = 5 * 60_000L,
    canCache = { it != null },
)

private val publicPreviewRequests = Semaphore(4)

private val trailingEncodedNoBreakSpaces = Regex("(?i)(?:%C2%A0)+$")

internal fun publicLinkPreviewUrl(rawUrl: String): HttpUrl? {
    val url = rawUrl.trim().toHttpUrlOrNull() ?: return null
    if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    // 预览只访问公网域名，不发送链接中的查询参数。
    if ('.' !in url.host || url.host.endsWith(".local")) return null
    if (':' in url.host || url.host.all { it.isDigit() || it == '.' }) return null
    return url.newBuilder()
        .encodedPath(url.encodedPath.replace(trailingEncodedNoBreakSpaces, ""))
        .query(null)
        .fragment(null)
        .build()
}

internal val publicLinkPreviewHttpClient by lazy {
    publicContentHttpClient.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .dns { host ->
            Dns.SYSTEM.lookup(host).filter(::isPublicPreviewAddress).ifEmpty { throw UnknownHostException() }
        }
        .followRedirects(false)
        .apply { networkInterceptors().clear() }
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.tag(BilibiliPreviewLookup::class.java)?.url
                ?: publicLinkPreviewUrl(request.url.toString()) ?: throw IOException("预览地址无效")
            chain.proceed(request.newBuilder().url(url).build())
        }
        .addNetworkInterceptor { chain ->
            chain.proceed(sanitizePublicContentRequest(chain.request(), includeBiliImageReferer = false))
        }
        .build()
}

private fun isPublicPreviewAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress
    ) return false
    val bytes = address.address
    val first = bytes[0].toInt() and 0xff
    if (bytes.size == 16) return first in 0x20..0x3f
    val second = bytes[1].toInt() and 0xff
    return first in 1..223 && !(first == 100 && second in 64..127)
}

internal suspend fun fetchPublicLinkPreview(url: HttpUrl): PublicLinkPreview? =
    publicLinkPreviewCache.getOrLoad(url) {
        var page = url
        // Revalidate each hop and retain the original site boundary for automatic previews.
        repeat(3) { hop ->
            val response = readPublicPreviewResponse(Request.Builder().url(page).build()) {
                val type = it.body.contentType()?.subtype
                when {
                    it.isRedirect && hop < 2 -> {
                        it.header("Location")
                            ?.let { location -> resolvePublicPreviewRedirect(page, location) }
                            ?.let(PublicPagePreviewResponse::Redirect)
                    }
                    it.isSuccessful && (type == null || type == "html" || type == "xhtml+xml") ->
                        readPublicPageMetadata(it, page)?.let(PublicPagePreviewResponse::Preview)
                    else -> null
                }
            }
            when (response) {
                is PublicPagePreviewResponse.Redirect -> page = response.url
                is PublicPagePreviewResponse.Preview -> return@getOrLoad response.preview
                null -> return@getOrLoad null
            }
        }
        null
    }

private sealed interface PublicPagePreviewResponse {
    data class Redirect(val url: HttpUrl) : PublicPagePreviewResponse
    data class Preview(val preview: PublicLinkPreview) : PublicPagePreviewResponse
}

internal fun resolvePublicPreviewRedirect(source: HttpUrl, location: String): HttpUrl? {
    val destination = source.resolve(location)?.toString()?.let(::publicLinkPreviewUrl) ?: return null
    if (source.host == destination.host) return destination
    val site = source.topPrivateDomain() ?: return null
    return destination.takeIf { it.topPrivateDomain() == site }
}

internal suspend fun <T> readPublicPreviewResponse(
    request: Request,
    parse: (Response) -> T?,
): T? = publicPreviewRequests.withPermit {
    awaitPublicPreviewResponse(request, parse)
}

private suspend fun <T> awaitPublicPreviewResponse(
    request: Request,
    parse: (Response) -> T?,
): T? = suspendCancellableCoroutine { continuation ->
    val call = publicLinkPreviewHttpClient.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = continuation.resume(null)

        override fun onResponse(call: Call, response: Response) {
            val preview = try {
                response.use(parse)
            } catch (_: IOException) {
                null
            }
            continuation.resume(preview)
        }
    })
}

private const val PREVIEW_PREFIX_BYTES = 64L * 1024
private const val PREVIEW_MAX_BYTES = 256L * 1024

private fun readPublicPageMetadata(response: Response, url: HttpUrl): PublicLinkPreview? {
    val prefix = response.peekBody(PREVIEW_PREFIX_BYTES)
    val complete = prefix.contentLength() < PREVIEW_PREFIX_BYTES
    val earlyPreview = parsePublicLinkPreview(prefix.string(), url, isComplete = complete)
    return if (earlyPreview != null || complete) earlyPreview
    else parsePublicLinkPreview(response.peekBody(PREVIEW_MAX_BYTES).string(), url)
}

internal fun parsePublicLinkPreview(
    html: String,
    url: HttpUrl,
    isComplete: Boolean = true,
): PublicLinkPreview? {
    val document = if (isComplete) Jsoup.parse(html)
    else Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true))
    val head = document.head()
    if (!isComplete && !head.endsInsidePreviewPrefix(html.length)) return null

    val socialTitle = head.previewMeta("meta[property=og:title]")
        .ifBlank { head.previewMeta("meta[name=twitter:title]") }
    val socialDescription = head.previewMeta("meta[property=og:description]")
        .ifBlank { head.previewMeta("meta[name=twitter:description]") }
    val heading = if (socialTitle.isBlank()) {
        document.selectFirst("main h1, article h1, [role=main] h1")
    } else null
    val summary = heading?.nextElementSibling()?.takeIf { it.normalName() == "p" }
    if (!isComplete && socialTitle.isBlank()) {
        // EOF closes open nodes implicitly. Only use text whose end is inside the prefix.
        if (heading?.endsInsidePreviewPrefix(html.length) != true ||
            summary?.endsInsidePreviewPrefix(html.length) != true
        ) return null
    }

    val title = socialTitle.ifBlank { heading?.text().orEmpty() }.ifBlank { document.title() }
    if (title.isBlank()) return null
    val description = socialDescription
        .ifBlank { summary?.text().orEmpty() }
        .ifBlank { head.previewMeta("meta[name=description]") }
    val image = head.previewMeta("meta[property=og:image]")
        .ifBlank { head.previewMeta("meta[name=twitter:image]") }
        .takeIf(String::isNotBlank)?.let(url::resolve)
    return PublicLinkPreview(title.take(200), description.take(300), image?.toString()?.let(::publicLinkPreviewUrl))
}

private fun Element.previewMeta(selector: String): String =
    selectFirst(selector)?.attr("content").orEmpty().trim()

private fun Element.endsInsidePreviewPrefix(prefixLength: Int): Boolean =
    endSourceRange().let { it.isTracked() && (!it.isImplicit() || it.endPos() < prefixLength) }
