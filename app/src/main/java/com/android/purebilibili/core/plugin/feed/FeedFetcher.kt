package com.android.purebilibili.core.plugin.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

private const val FEED_BODY_LIMIT_BYTES = 2 * 1024 * 1024
private const val FEED_REQUEST_TIMEOUT_MS = 8_000L

internal class NativeArticleRedirect(val targetUrl: String) : java.io.IOException("此链接应由 B 站原生页面打开")
internal class ArticleHttpFailure(val statusCode: Int) : java.io.IOException("原文请求失败 $statusCode")
internal class ArticleContentUnavailable(val userMessage: String) : java.io.IOException(userMessage)

suspend fun fetchArticleHtml(url: String): Result<String> = fetchReadableArticle(url).map { it.html }

suspend fun fetchReadableArticle(url: String): Result<ReadableArticle> = withContext(Dispatchers.IO) {
    val call = if (isHttpFeedUrl(url)) {
        // 使用 HTTP 客户端的默认标识，避免站点拒绝自定义的阅读器标识。
        publicContentHttpClient.newCall(Request.Builder().url(url.trim()).build())
    } else null
    if (call == null) return@withContext Result.failure(IllegalArgumentException("原文地址无效"))
    runCatching {
        // Keep cancellation attached until the body has been read and parsed, so
        // leaving the article interrupts both waiting for headers and reading data.
        suspendCancellableCoroutine<ReadableArticle> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, error: java.io.IOException) {
                    continuation.resumeWith(Result.failure(error))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val result = runCatching {
                        response.use {
                            val finalUrl = sanitizePublicContentUrl(response.request.url)
                            if (com.android.purebilibili.core.util.isBilibiliWebHost(finalUrl.host)) {
                                throw NativeArticleRedirect(finalUrl.toString())
                            }
                            if (!response.isSuccessful) throw ArticleHttpFailure(response.code)
                            val mediaType = response.body.contentType()
                            if (mediaType != null && mediaType.subtype != "html" && mediaType.subtype != "xhtml+xml") {
                                throw ArticleContentUnavailable("此链接不是可阅读的网页")
                            }
                            val stream = response.body.byteStream()
                            val buffer = ByteArray(8 * 1024)
                            val output = ByteArrayOutputStream()
                            while (continuation.isActive) {
                                val read = stream.read(buffer)
                                if (read < 0) break
                                if (output.size() + read > FEED_BODY_LIMIT_BYTES) {
                                    throw ArticleContentUnavailable("原文内容超过 2MB")
                                }
                                output.write(buffer, 0, read)
                            }
                            if (!continuation.isActive) throw kotlinx.coroutines.CancellationException()
                            val charset = response.body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                            val page = output.toString(charset.name())
                            extractReadableArticle(page, finalUrl.toString())
                                ?: throw ArticleContentUnavailable("暂时无法提取原文正文")
                        }
                    }
                    continuation.resumeWith(result)
                }
            })
        }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
}

sealed interface FeedFetchOutcome {
    data class Modified(
        val xml: String,
        val etag: String? = null,
        val lastModified: String? = null,
    ) : FeedFetchOutcome

    data object NotModified : FeedFetchOutcome
}

suspend fun fetchFeedXml(url: String): Result<String> =
    fetchFeedXmlConditional(url).map { outcome ->
        when (outcome) {
            is FeedFetchOutcome.Modified -> outcome.xml
            FeedFetchOutcome.NotModified -> error("订阅内容未变化")
        }
    }

suspend fun fetchFeedXmlConditional(
    url: String,
    etag: String? = null,
    lastModified: String? = null,
): Result<FeedFetchOutcome> = withContext(Dispatchers.IO) {
    if (!isHttpFeedUrl(url)) {
        return@withContext Result.failure(IllegalArgumentException("只接受 http 或 https 订阅地址"))
    }
    val request = Request.Builder()
        .url(url.trim())
        .header("User-Agent", "BiliPai Feed")
        .apply {
            etag?.takeIf { it.isNotBlank() }?.let { header("If-None-Match", it) }
            lastModified?.takeIf { it.isNotBlank() }?.let { header("If-Modified-Since", it) }
        }
        .build()
    val call = publicContentHttpClient.newCall(request)
    coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
    try {
        withTimeout(FEED_REQUEST_TIMEOUT_MS) {
            runCatching {
                call.execute().use { response ->
                    if (response.code == 304) return@use FeedFetchOutcome.NotModified
                    if (!response.isSuccessful) error("订阅请求失败 ${response.code}")
                    val stream = response.body.byteStream()
                    val buffer = ByteArray(8 * 1024)
                    val output = ByteArrayOutputStream()
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        if (output.size() + read > FEED_BODY_LIMIT_BYTES) error("订阅内容超过 2MB")
                        output.write(buffer, 0, read)
                    }
                    val bytes = output.toByteArray()
                    val declaration = bytes.copyOfRange(0, minOf(bytes.size, 256)).toString(Charsets.ISO_8859_1)
                    val declaredCharset = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(declaration)?.groupValues?.getOrNull(1)
                        ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                    val charset = response.body.contentType()?.charset() ?: declaredCharset ?: Charsets.UTF_8
                    FeedFetchOutcome.Modified(
                        xml = String(bytes, charset).removePrefix("\uFEFF"),
                        etag = response.header("ETag"),
                        lastModified = response.header("Last-Modified"),
                    )
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        call.cancel()
        Result.failure(IllegalStateException("连接超时"))
    }
}

internal fun friendlyFeedError(sourceTitle: String, error: Throwable): String {
    val raw = error.message.orEmpty()
    val reason = when {
        raw.contains("超时") || raw.contains("timeout", ignoreCase = true) -> "连接超时"
        raw.contains("订阅请求失败") -> "请求失败 ${raw.substringAfter("订阅请求失败").trim()}"
        raw.contains("failed to connect", ignoreCase = true) ||
            raw.contains("unable to resolve", ignoreCase = true) -> "连不上"
        raw.isBlank() -> "加载失败"
        else -> "暂时打不开"
    }
    return "$sourceTitle：$reason"
}

suspend fun loadFeedSources(
    sources: List<FeedSource>,
    validators: Map<String, FeedConditionalValidators> = emptyMap(),
    onUpdate: (FeedLoadSnapshot) -> Unit = {},
): FeedLoadSnapshot = supervisorScope {
    val items = mutableListOf<ParsedFeedItem>()
    val errors = mutableListOf<String>()
    val updatedValidators = mutableMapOf<String, FeedConditionalValidators>()
    var completedSources = 0
    fun publish(): FeedLoadSnapshot {
        val sorted = items.sortedWith(
            compareBy<ParsedFeedItem> { it.publishedEpochSec == null }
                .thenByDescending { it.publishedEpochSec ?: 0L }
        )
        return FeedLoadSnapshot(
            items = sorted.toList(),
            errors = errors.toList(),
            validators = updatedValidators.toMap(),
            completedSources = completedSources,
            totalSources = sources.size,
        )
    }
    val gate = Semaphore(4)
    sources.map { source ->
        async(Dispatchers.IO) {
            val saved = validators[source.url]
            val outcome = gate.withPermit {
                fetchFeedXmlConditional(source.url, saved?.etag, saved?.lastModified).mapCatching { result ->
                    when (result) {
                        FeedFetchOutcome.NotModified -> null
                        is FeedFetchOutcome.Modified -> parseFeedDocument(result.xml, source.id, source.title, source.url) to
                            FeedConditionalValidators(result.etag, result.lastModified)
                    }
                }
            }
            val snapshot = synchronized(items) {
                completedSources += 1
                outcome
                    .onSuccess { parsed ->
                        if (parsed == null) {
                            // 304：内容未变，沿用旧校验器即可，条目继续走本地缓存
                            updatedValidators[source.url] = saved ?: FeedConditionalValidators()
                        } else {
                            items += parsed.first.items
                            updatedValidators[source.url] = parsed.second
                        }
                    }
                    .onFailure { error -> errors += friendlyFeedError(source.title, error) }
                publish()
            }
            withContext(Dispatchers.Main.immediate) { onUpdate(snapshot) }
        }
    }.awaitAll()
    synchronized(items) { publish() }
}
