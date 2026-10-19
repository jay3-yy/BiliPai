package com.android.purebilibili.core.plugin.feed

import com.android.purebilibili.core.network.buildAppDns
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** RSS, extracted articles and their images never inherit an authenticated client. */
internal val publicContentHttpClient: OkHttpClient by lazy { createPublicContentHttpClient() }

internal fun createPublicContentHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .dns(buildAppDns())
    .cookieJar(CookieJar.NO_COOKIES)
    .authenticator(Authenticator.NONE)
    .proxyAuthenticator(Authenticator.NONE)
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .callTimeout(8, TimeUnit.SECONDS)
    // A network interceptor runs again at every redirect destination, after the
    // HTTP bridge. Cookies, bearer headers and token query parameters stay absent.
    .addNetworkInterceptor { chain -> chain.proceed(sanitizePublicContentRequest(chain.request())) }
    .build()

internal fun sanitizePublicContentUrl(url: HttpUrl): HttpUrl {
    val builder = url.newBuilder().username("").password("")
    url.queryParameterNames.filter(::isPublicContentCredential).forEach(builder::removeAllQueryParameters)
    return builder.build()
}

internal fun sanitizePublicContentRequest(request: Request): Request {
    val builder = request.newBuilder().url(sanitizePublicContentUrl(request.url))
    request.headers.names().filter { it.lowercase() !in publicContentHeaders }.forEach(builder::removeHeader)
    // Bilibili CDN thumbnails need only a fixed public Referer, never account data.
    if (request.url.host == "hdslb.com" || request.url.host.endsWith(".hdslb.com")) {
        builder.header("Referer", "https://www.bilibili.com/")
    }
    return builder.build()
}

/**
 * Image-only variant of the anonymous public content chain. Hotlink protected CDNs
 * (for example cdnfile.sspai.com) answer 403 when a request carries no Referer, while a
 * browser always sends the article page as Referer. Restoring a same-site Referer keeps
 * RSS body images loading the way a normal page view does.
 */
internal val publicContentImageHttpClient: OkHttpClient by lazy {
    publicContentHttpClient.newBuilder()
        .addNetworkInterceptor { chain -> chain.proceed(withPublicImageReferer(chain.request())) }
        .build()
}

internal fun withPublicImageReferer(request: Request): Request {
    if (request.header("Referer") != null) return request
    return request.newBuilder().header("Referer", publicImageReferer(request.url)).build()
}

internal fun publicImageReferer(url: HttpUrl): String {
    val siteRoot = publicSiteRoot(url.host)
    // Gcores rejects the bare-domain Referer (403), but accepts its canonical www site.
    // Keep this public fixed origin within the anonymous image chain.
    val refererHost = if (siteRoot == "gcores.com") "www.gcores.com" else siteRoot
    return "${url.scheme}://$refererHost/"
}

/** Article pages are served from the site root, so that is the Referer a browser would send. */
internal fun publicSiteRoot(host: String): String {
    if (host.isEmpty() || host.contains(':') || ipv4Literal.matches(host)) return host
    val labels = host.split('.')
    if (labels.size <= 2) return host
    val rootStart = if (labels[labels.size - 2] in multiPartPublicSuffixes) labels.size - 3 else labels.size - 2
    return labels.subList(rootStart, labels.size).joinToString(".")
}

private val ipv4Literal = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
private val multiPartPublicSuffixes = setOf("com", "co", "net", "org", "gov", "edu", "ac")

/** Called from the gallery's existing IO context for saving or sharing public images. */
internal fun downloadPublicContentToFile(url: String, file: java.io.File) {
    try {
        publicContentImageHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("公开图片请求失败")
            response.body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        }
    } catch (error: Exception) {
        file.delete()
        throw error
    }
}

private val publicContentHeaders = setOf(
    "host", "connection", "accept", "accept-encoding", "accept-language", "user-agent",
    "if-none-match", "if-modified-since", "range", "cache-control", "pragma",
)

private fun isPublicContentCredential(name: String): Boolean =
    name.lowercase().replace("-", "").replace("_", "") in publicContentCredentials

private val publicContentCredentials = setOf(
    "accesstoken", "acesstoken", "accesskey", "refreshtoken", "sessdata", "bilijct", "csrf", "csrftoken",
    "authorization", "cookie",
)
