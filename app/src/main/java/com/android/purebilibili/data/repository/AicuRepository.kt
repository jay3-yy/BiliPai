package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.buildAppDns
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AicuDataSource {
    suspend fun query(query: AicuQuery, onQueuePosition: (Int?) -> Unit): AicuPage
}

/** No Bilibili interceptors, persistent cache, authentication or WebView cookie sharing. */
internal class AicuCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != "api.aicu.cc") return
        cookies.filter { it.matches(url) }.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            this.cookies.add(cookie)
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return if (url.host == "api.aicu.cc") cookies.filter { it.matches(url) } else emptyList()
    }
}

internal fun buildAicuClient(): OkHttpClient = OkHttpClient.Builder()
    .dns(buildAppDns())
    .cookieJar(AicuCookieJar())
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.MINUTES)
    .build()

internal fun buildAicuQueryUrl(query: AicuQuery, ticket: String): HttpUrl {
    val (start, end) = query.filter.timestamps()
    return "https://api.aicu.cc/api/v4/search/${query.category.endpoint}".toHttpUrl().newBuilder()
        .addQueryParameter("uid", query.uid.toString())
        .addQueryParameter("pn", query.page.toString())
        .addQueryParameter("ps", AicuQuery.PAGE_SIZE.toString())
        .addQueryParameter("keyword", query.filter.keyword.trim())
        .addQueryParameter("need_count", "true")
        .addQueryParameter("ticket", ticket)
        .apply {
            if (start != null) addQueryParameter("stime", start.toString())
            if (end != null) addQueryParameter("etime", end.toString())
            if (query.category == AicuCategory.COMMENT) addQueryParameter("mode", query.filter.commentMode.toString())
        }.build()
}

class AicuRepository internal constructor(private val client: OkHttpClient = buildAicuClient()) : AicuDataSource {
    suspend fun getTrending(limit: Int = 10): List<AicuTrendingEntry> {
        val safeLimit = limit.coerceIn(1, 20)
        val url = "https://online.aicu.cc/api/community/trending".toHttpUrl()
            .newBuilder().addQueryParameter("limit", safeLimit.toString()).build()
        return request(Request.Builder().url(url).build()) { response ->
            val payload = Json { ignoreUnknownKeys = true }
                .decodeFromString<AicuTrendingResponse>(readJson(response))
            if (payload.code != 0) throw AicuRequestException(
                payload.message.ifBlank { "Aicu 热搜加载失败（${payload.code}）" }
            )
            payload.data?.hot_searches.orEmpty().filter {
                it.uid.matches(Regex("[1-9]\\d{0,19}")) &&
                    it.display_name.isNotBlank() && it.avatar.isNotBlank() &&
                    it.search_count >= 0 && it.hot_value >= 0 &&
                    it.trend in setOf("up", "down", "stable")
            }.take(safeLimit)
        }
    }

    override suspend fun query(query: AicuQuery, onQueuePosition: (Int?) -> Unit): AicuPage {
        query.filter.timestamps() // Validate before any request.
        var ticket: String? = null
        var consumed = false
        try {
            val queued = request(queueRequest("enqueue", post = true)) { decodeAicuEnvelope(readJson(it)) }
            ticket = queued.aicuText("ticket").takeIf { it.isNotBlank() }
                ?: throw AicuRequestException("未能取得排队凭据，请稍后重试。")
            if (queued.aicuText("status") != "ready") {
                onQueuePosition(((queued.aicuLong("position") ?: 1) - 1).coerceAtLeast(0).toInt())
                request(queueRequest("stream", ticket)) { response ->
                    if (!response.header("Content-Type").orEmpty().contains("text/event-stream")) {
                        throw AicuRequestException("排队服务返回了无效响应，可能需要安全验证。")
                    }
                    val decoder = AicuQueueEventDecoder()
                    val source = response.body.source()
                    var ready = false
                    while (!ready && !source.exhausted()) {
                        val line = source.readUtf8LineStrict(65536).removeSuffix("\r")
                        val event = decoder.accept(line) ?: continue
                        when (event.type) {
                            "ready" -> ready = true
                            "position" -> {
                                val data = runCatching { Json.parseToJsonElement(event.data) as? JsonObject }.getOrNull()
                                data?.let {
                                    onQueuePosition((it.aicuLong("ahead") ?: ((it.aicuLong("position") ?: 1) - 1))
                                        .coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                                }
                            }
                            "expired" -> throw AicuRequestException("排队已超时，请重新查询。")
                            "error" -> throw AicuRequestException("排队服务暂不可用，请重新查询。")
                        }
                    }
                    if (!ready) throw AicuRequestException("排队连接已中断，请重新查询。")
                }
            }
            onQueuePosition(null)
            val result = request(Request.Builder().url(buildAicuQueryUrl(query, ticket)).build()) {
                decodeAicuPage(decodeAicuEnvelope(readJson(it)), query)
            }
            consumed = true
            return result
        } finally {
            // Best effort, bounded cleanup. Cancellation must not keep the screen alive.
            if (!consumed) ticket?.let(::cancelTicket)
        }
    }

    private fun queueRequest(action: String, ticket: String? = null, post: Boolean = false): Request =
        Request.Builder().url("https://api.aicu.cc/api/v4/queue/$action".toHttpUrl().newBuilder()
            .apply { ticket?.let { addQueryParameter("ticket", it) } }.build())
            .apply {
                if (post) post(ByteArray(0).toRequestBody())
                if (action == "stream") header("Accept", "text/event-stream")
            }.build()

    private fun cancelTicket(ticket: String) {
        val call = client.newCall(queueRequest("cancel", ticket, post = true))
        call.timeout().timeout(5, TimeUnit.SECONDS)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun readJson(response: Response): String {
        val source = response.body.source()
        if (source.request(8L * 1024 * 1024 + 1)) throw AicuRequestException("查询响应过大，请缩小日期范围。")
        return source.readUtf8()
    }

    private suspend fun <T> request(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        val openResponse = AtomicReference<Response?>()
        continuation.invokeOnCancellation {
            call.cancel()
            openResponse.getAndSet(null)?.close()
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                openResponse.set(response)
                response.use {
                    try {
                        if (!continuation.isActive) return
                        if (!response.isSuccessful) throw aicuHttpFailure(response.code, response.header("Retry-After"))
                        val result = read(response)
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        openResponse.compareAndSet(response, null)
                    }
                }
            }
        })
    }
}
