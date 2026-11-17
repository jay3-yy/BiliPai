// 文件路径: data/repository/SponsorBlockRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

/** The request belongs to its caller, including cancellation while the response is queued. */
internal suspend fun Call.awaitSponsorResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            continuation.resumeWithException(error)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, resource, _ -> resource.close() }
        }
    })
}

internal fun buildSponsorBlockHttpClient(baseClient: OkHttpClient): OkHttpClient {
    return baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        // Run after the shared interceptors so the Bilibili Origin is replaced.
        .addInterceptor { chain ->
            chain.proceed(buildSponsorBlockClientRequest(chain.request(), BuildConfig.VERSION_NAME))
        }
        .build()
}

internal fun buildSponsorBlockClientRequest(request: Request, versionName: String): Request =
    request.newBuilder()
        .header("Origin", "https://github.com/jay3-yy/BiliPai")
        .header("x-ext-version", versionName)
        .header("User-Agent", "BiliPai/$versionName")
        .build()

internal fun buildSponsorBlockSegmentsUrl(
    baseUrl: String,
    bvid: String,
    cid: Long = 0L,
    categories: List<String> = SponsorCategory.ALL_CATEGORIES
): String {
    val params = buildList {
        add("videoID=$bvid")
        if (cid > 0L) {
            add("cid=$cid")
        }
        categories.forEach { category ->
            add("category=$category")
        }
    }
    return "$baseUrl/skipSegments?${params.joinToString("&")}"
}

internal fun buildSponsorBlockVoteBody(
    userId: String,
    segmentId: String,
    voteType: Int? = null,
    category: String? = null,
): String {
    require((voteType == null) != (category == null))
    // API types: 0 = downvote, 1 = upvote, 20 = undo vote.
    require(voteType == null || voteType in setOf(0, 1, 20))
    return buildJsonObject {
        put("UUID", segmentId)
        put("userID", userId)
        voteType?.let { put("type", it) }
        category?.let { put("category", it) }
    }.toString()
}

/**
 * 空降助手 (BilibiliSponsorBlock) 数据仓库
 * API 文档: https://github.com/hanydd/BilibiliSponsorBlock/wiki/API
 */
object SponsorBlockRepository {
    const val DEFAULT_BASE_URL = "https://bsbsb.top/api"
    private const val TAG = "SponsorBlock"
    private data class CachedSegments(val segments: List<SponsorSegment>, val expiresAtMs: Long)
    private val segmentCache = LinkedHashMap<String, CachedSegments>()
    private var segmentCacheGeneration = 0L
    private const val SEGMENT_CACHE_LIMIT = 64
    private const val SEGMENT_CACHE_TTL_MS = 5 * 60 * 1000L
    private const val EMPTY_SEGMENT_CACHE_TTL_MS = 30 * 1000L
    
    private val client = buildSponsorBlockHttpClient(NetworkModule.okHttpClient)
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    @Serializable
    data class SegmentSubmission(
        val segment: List<Float>,
        val category: String,
        val actionType: String = "skip"
    )

    @Serializable
    private data class SegmentSubmissionRequest(
        val videoID: String,
        val cid: String,
        val userID: String,
        val videoDuration: Float,
        val userAgent: String,
        val segments: List<SegmentSubmission>
    )

    data class ServerStatus(
        val reachable: Boolean,
        val message: String
    )

    data class CommunityUserInfo(
        val userName: String = "",
        val viewCount: Long = 0L,
        val minutesSaved: Double = 0.0,
        val segmentCount: Int = 0
    )

    class SponsorBlockRequestException(
        val statusCode: Int,
        detail: String? = null,
    ) : IllegalStateException(
        when (statusCode) {
            400 -> "参数错误"
            403 -> "被服务器自动审核机制拒绝"
            404 -> "未找到数据"
            409 -> "重复提交"
            429 -> "提交太快，请稍后重试"
            500 -> "服务器无法处理请求"
            else -> detail?.takeIf { it.isNotBlank() } ?: "服务器错误（HTTP $statusCode）"
        }
    )
    
    /** Compatibility API for callers that do not distinguish failure from absent data. */
    suspend fun getSegments(
        bvid: String,
        cid: Long = 0L,
        categories: List<String> = SponsorCategory.ALL_CATEGORIES,
        baseUrl: String = DEFAULT_BASE_URL
    ): List<SponsorSegment> = try {
        loadSegments(bvid, cid, categories, baseUrl)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e(TAG, "获取空降片段失败: ${e.message}")
        emptyList()
    }

    /** 404 is a successful empty result; transport, HTTP and parsing failures propagate. */
    suspend fun loadSegments(
        bvid: String,
        cid: Long = 0L,
        categories: List<String> = SponsorCategory.ALL_CATEGORIES,
        baseUrl: String = DEFAULT_BASE_URL
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext emptyList()
        val url = buildSponsorBlockSegmentsUrl(
            baseUrl = baseUrl.trimEnd('/'),
            bvid = bvid,
            cid = cid,
            categories = categories.distinct().sorted()
        )
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val (cached, cacheGeneration) = synchronized(segmentCache) {
            segmentCache[url]?.takeIf { it.expiresAtMs > nowMs } to segmentCacheGeneration
        }
        if (cached != null) return@withContext cached.segments

        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val segments = client.newCall(request).awaitSponsorResponse().use { response ->
            when (response.code) {
                200 -> json.decodeFromString<List<SponsorSegment>>(response.body.string())
                404 -> emptyList()
                else -> throw SponsorBlockRequestException(response.code, response.body.string().take(160))
            }
        }
        currentCoroutineContext().ensureActive()
        val ttlMs = if (segments.isEmpty()) EMPTY_SEGMENT_CACHE_TTL_MS else SEGMENT_CACHE_TTL_MS
        synchronized(segmentCache) {
            // A submission can invalidate data while this request is in flight.
            if (cacheGeneration == segmentCacheGeneration) {
                segmentCache.remove(url)
                segmentCache[url] = CachedSegments(
                    segments = segments,
                    expiresAtMs = android.os.SystemClock.elapsedRealtime() + ttlMs
                )
                while (segmentCache.size > SEGMENT_CACHE_LIMIT) {
                    segmentCache.remove(segmentCache.keys.first())
                }
            }
        }
        android.util.Log.d(TAG, "获取到 ${segments.size} 个空降片段 for $bvid/$cid")
        segments
    }

    suspend fun checkServerStatus(baseUrl: String): ServerStatus = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.newCall(
                Request.Builder().url("${baseUrl.trimEnd('/')}/status/uptime").get().build()
            ).execute()
            response.use {
                if (it.isSuccessful) ServerStatus(true, "服务正常")
                else ServerStatus(false, "HTTP ${it.code}")
            }
        }.getOrElse { error -> ServerStatus(false, error.message ?: "无法连接服务器") }
    }

    suspend fun getCommunityUserInfo(
        baseUrl: String,
        userId: String
    ): Result<CommunityUserInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = okhttp3.HttpUrl.Builder()
                .scheme(baseUrl.substringBefore("://"))
                .host(java.net.URI(baseUrl).host)
                .apply {
                    val port = java.net.URI(baseUrl).port
                    if (port != -1) port(port)
                    java.net.URI(baseUrl).path.trim('/').split('/').filter(String::isNotBlank).forEach(::addPathSegment)
                    addPathSegment("userInfo")
                    addQueryParameter("userID", userId)
                    addQueryParameter("values", "[\"userName\",\"viewCount\",\"minutesSaved\",\"segmentCount\"]")
                }
                .build()
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            response.use {
                val body = it.body.string()
                if (!it.isSuccessful) throw SponsorBlockRequestException(it.code, body.take(160))
                val payload = json.parseToJsonElement(body).jsonObject
                CommunityUserInfo(
                    userName = payload["userName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    viewCount = payload["viewCount"]?.jsonPrimitive?.longOrNull ?: 0L,
                    minutesSaved = payload["minutesSaved"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    segmentCount = payload["segmentCount"]?.jsonPrimitive?.intOrNull ?: 0
                )
            }
        }
    }

    suspend fun uploadViewedSegment(baseUrl: String, segmentId: String): Result<Unit> = postJson(
        url = "${baseUrl.trimEnd('/')}/viewedVideoSponsorTime",
        body = "{\"UUID\":${json.encodeToString(segmentId)}}"
    ).map { }

    suspend fun submitSegments(
        baseUrl: String,
        bvid: String,
        cid: Long,
        userId: String,
        videoDurationSeconds: Float,
        segments: List<SegmentSubmission>
    ): Result<List<SponsorSegment>> = postJson(
        url = "${baseUrl.trimEnd('/')}/skipSegments",
        body = json.encodeToString(SegmentSubmissionRequest(
            videoID = bvid,
            cid = cid.toString(),
            userID = userId,
            videoDuration = videoDurationSeconds,
            userAgent = "BiliPai",
            segments = segments,
        )),
    ).mapCatching { body ->
        val submittedSegments = json.decodeFromString<List<SponsorSegment>>(body)
        synchronized(segmentCache) {
            segmentCacheGeneration += 1
            segmentCache.clear()
        }
        submittedSegments
    }

    suspend fun voteOnSegment(
        baseUrl: String,
        userId: String,
        segmentId: String,
        voteType: Int? = null,
        category: String? = null,
    ): Result<Unit> {
        val body = buildSponsorBlockVoteBody(userId, segmentId, voteType, category)
        return postJson("${baseUrl.trimEnd('/')}/voteOnSponsorTime", body).map { }
    }

    private suspend fun postJson(url: String, body: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (!response.isSuccessful) {
                    throw SponsorBlockRequestException(response.code, responseBody.take(160))
                }
                responseBody
            }
        }
    }
    
    /**
     * 检查当前播放位置是否在某个空降片段内
     * @param segments 片段列表
     * @param currentPositionMs 当前播放位置（毫秒）
     * @return 匹配的片段，没有则返回 null
     */
    fun findSegmentAtPosition(
        segments: List<SponsorSegment>,
        currentPositionMs: Long
    ): SponsorSegment? {
        val currentSeconds = currentPositionMs / 1000f
        return segments.find { segment ->
            currentSeconds >= segment.startTime && currentSeconds < segment.endTime - 0.5f
        }
    }
    
    /**
     * 获取下一个即将到来的空降片段
     * @param segments 片段列表
     * @param currentPositionMs 当前播放位置（毫秒）
     * @param lookAheadMs 提前多少毫秒提示
     * @return 即将到来的片段，没有则返回 null
     */
    fun findUpcomingSegment(
        segments: List<SponsorSegment>,
        currentPositionMs: Long,
        lookAheadMs: Long = 2000
    ): SponsorSegment? {
        val currentSeconds = currentPositionMs / 1000f
        val lookAheadSeconds = lookAheadMs / 1000f
        
        return segments.find { segment ->
            val timeToStart = segment.startTime - currentSeconds
            timeToStart > 0 && timeToStart <= lookAheadSeconds
        }
    }
}
