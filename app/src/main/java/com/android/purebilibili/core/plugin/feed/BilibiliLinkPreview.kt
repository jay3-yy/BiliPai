package com.android.purebilibili.core.plugin.feed

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.isBilibiliHost
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup

internal class BilibiliPreviewLookup private constructor(val url: HttpUrl) {
    companion object {
        fun forPage(page: HttpUrl): BilibiliPreviewLookup? {
            if (!isBilibiliHost(page.host)) return null
            val target = BilibiliNavigationTargetParser.parse(page.toString())
            val url = when (target) {
                is BilibiliNavigationTarget.Video -> {
                    if (page.pathSegments.first() != "video") return null
                    val id = target.videoId
                    if (!id.equals(page.pathSegments.getOrNull(1), ignoreCase = true)) return null
                    val aid = id.takeIf { it.startsWith("av", ignoreCase = true) }?.drop(2)?.toLongOrNull()
                    when {
                        aid != null && aid > 0 -> videoApi.lookup("aid", aid)
                        videoBvid.matches(id) -> videoApi.lookup("bvid", "BV" + id.drop(2))
                        else -> return null
                    }
                }
                is BilibiliNavigationTarget.Live -> roomApi.lookup("room_id", target.roomId)
                is BilibiliNavigationTarget.BangumiSeason -> seasonApi.lookup("season_id", target.seasonId)
                is BilibiliNavigationTarget.BangumiEpisode -> seasonApi.lookup("ep_id", target.epId)
                else -> return null
            }
            return url?.let(::BilibiliPreviewLookup)
        }

        private val videoApi = "https://api.bilibili.com/x/web-interface/view".toHttpUrl()
        private val roomApi = "https://api.live.bilibili.com/room/v1/Room/get_info".toHttpUrl()
        private val seasonApi = "https://api.bilibili.com/pgc/view/web/season".toHttpUrl()
        private val videoBvid = Regex("BV[A-Za-z0-9]{10}", RegexOption.IGNORE_CASE)

        private fun HttpUrl.lookup(name: String, id: Long): HttpUrl? =
            if (id > 0) lookup(name, id.toString()) else null

        private fun HttpUrl.lookup(name: String, id: String): HttpUrl =
            newBuilder().addQueryParameter(name, id).build()
    }
}

internal fun isPublicBilibiliToyPage(url: HttpUrl): Boolean =
    url.host in setOf("www.bilibili.com", "m.bilibili.com", "bilibili.com") &&
        url.pathSegments.size == 3 && url.pathSegments.first() == "toy" &&
        bilibiliToySlug.matches(url.pathSegments[1]) && url.pathSegments.last() == "index.html"

private val bilibiliToySlug = Regex("[A-Za-z0-9_-]+")

private val bilibiliShortCode = Regex("[A-Za-z0-9]+")

internal fun isPublicBilibiliShortLink(url: HttpUrl): Boolean =
    url.host == "b23.tv" && url.pathSegments.singleOrNull()?.let(bilibiliShortCode::matches) == true

internal fun resolveBilibiliShortPreviewPage(url: HttpUrl, location: String): HttpUrl? {
    val redirect = url.resolve(location) ?: return null
    val page = publicLinkPreviewUrl(redirect.toString()) ?: return null
    return page.takeIf { isBilibiliHost(it.host) }
}

internal suspend fun fetchBilibiliShortLinkPreview(url: HttpUrl): PublicLinkPreview? =
    publicLinkPreviewCache.getOrLoad(url) {
        // 只读取短链的首跳地址，后续请求按公开内容类型生成。
        val request = Request.Builder().url(url).build()
        val page = readPublicPreviewResponse(request) { response ->
            if (response.isRedirect) {
                response.header("Location")?.let { resolveBilibiliShortPreviewPage(url, it) }
            } else null
        } ?: return@getOrLoad null
        val lookup = BilibiliPreviewLookup.forPage(page)
        when {
            lookup != null -> fetchBilibiliLinkPreview(lookup)
            isPublicBilibiliToyPage(page) -> fetchPublicLinkPreview(page)
            else -> null
        }
    }

internal suspend fun fetchBilibiliLinkPreview(lookup: BilibiliPreviewLookup): PublicLinkPreview? =
    publicLinkPreviewCache.getOrLoad(lookup.url) {
        // 公开 API 只接收由站内路由解析出的内容 ID。
        val request = Request.Builder().url(lookup.url)
            .tag(BilibiliPreviewLookup::class.java, lookup)
            .header("Accept", "application/json")
            .build()
        readPublicPreviewResponse(request) {
            if (it.isSuccessful) parseBilibiliLinkPreview(it.peekBody(256L * 1024).string()) else null
        }
    }

internal fun parseBilibiliLinkPreview(json: String): PublicLinkPreview? {
    val response = try {
        Json.parseToJsonElement(json) as? JsonObject ?: return null
    } catch (_: SerializationException) {
        return null
    }
    if ((response["code"] as? JsonPrimitive)?.intOrNull != 0) return null
    val data = response["data"] as? JsonObject ?: response["result"] as? JsonObject ?: return null
    val title = data.previewText("title").takeIf(String::isNotBlank) ?: return null
    val description = data.previewText("desc").ifBlank { data.previewText("description") }
        .ifBlank { data.previewText("evaluate") }
    val cover = data.previewText("pic").ifBlank { data.previewText("user_cover") }
        .ifBlank { data.previewText("cover") }
    val coverUrl = when {
        cover.startsWith("//") -> "https:$cover"
        cover.startsWith("http://") -> "https://" + cover.removePrefix("http://")
        else -> cover
    }
    return PublicLinkPreview(
        title = title.take(200),
        description = Jsoup.parseBodyFragment(description).text().take(300),
        imageUrl = publicLinkPreviewUrl(coverUrl),
    )
}

private fun JsonObject.previewText(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
