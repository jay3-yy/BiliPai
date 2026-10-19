package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.buildAppDns
import com.android.purebilibili.data.model.response.SplashItem
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

internal val wallpaperArchiveSources = listOf(
    "bili_app_splash" to "https://github.com/zjkwdy/bili_app_splash",
    "bilibili_poster" to "https://github.com/AIboy996/bilibili_poster",
)

internal interface WallpaperArchiveApi {
    @GET
    suspend fun getIndex(@Url url: String): JsonObject
}

/** Public archive requests use a separate client without Bilibili account headers or cookies. */
internal object WallpaperArchiveRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(
            OkHttpClient.Builder()
                .dns(buildAppDns())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .callTimeout(35, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(WallpaperArchiveApi::class.java)

    suspend fun loadSplashArchive(): List<SplashItem> = parseWallpaperArchiveTree(
        api.getIndex("https://api.github.com/repos/zjkwdy/bili_app_splash/git/trees/main?recursive=1"),
        repository = "zjkwdy/bili_app_splash",
        directories = setOf("app_splash", "bizhiniang"),
        sourceLabel = "历史归档",
    )

    suspend fun loadPosterArchive(): List<SplashItem> = parseWallpaperArchiveTree(
        api.getIndex("https://api.github.com/repos/AIboy996/bilibili_poster/git/trees/main?recursive=1"),
        repository = "AIboy996/bilibili_poster",
        directories = setOf("imgs"),
        sourceLabel = "历史插画",
    )

    suspend fun loadPosterIndex(): List<SplashItem> = parseWallpaperPosterIndex(
        api.getIndex("https://raw.githubusercontent.com/AIboy996/bilibili_poster/main/database.json")
    )
}

internal fun parseWallpaperArchiveTree(
    root: JsonObject,
    repository: String,
    directories: Set<String>,
    sourceLabel: String,
): List<SplashItem> {
    check((root["truncated"] as? JsonPrimitive)?.booleanOrNull != true) { "$sourceLabel 返回的目录不完整" }
    val tree = root["tree"] as? JsonArray ?: error("$sourceLabel 目录格式错误")
    return tree.mapNotNull { element ->
        val file = element as? JsonObject ?: return@mapNotNull null
        val path = file.stringValue("path")
        if (file.stringValue("type") != "blob" || path.substringBefore('/') !in directories) return@mapNotNull null
        if (path.substringAfterLast('.').lowercase() !in setOf("png", "jpg", "jpeg", "webp", "gif")) return@mapNotNull null
        val url = "https://raw.githubusercontent.com/$repository/main/" + path.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        val title = if (path.startsWith("bizhiniang/")) {
            "壁纸娘 · ${path.substringAfter('/').substringBefore('/')}"
        } else "$sourceLabel · $name"
        SplashItem(
            id = -kotlin.math.abs(url.hashCode().toLong()) - 1L,
            thumb = url,
            image = url,
            title = title,
            archiveContentHash = file.stringValue("sha"),
        )
    }.distinctBy { it.archiveContentHash.ifBlank { it.image } }
}

internal fun parseWallpaperPosterIndex(root: JsonObject): List<SplashItem> = root.mapNotNull { (key, element) ->
    val entry = element as? JsonObject ?: return@mapNotNull null
    val url = entry.stringValue("thumb").replaceFirst("http://", "https://")
    if (!url.startsWith("https://")) return@mapNotNull null
    SplashItem(
        id = key.toLongOrNull() ?: -kotlin.math.abs(url.hashCode().toLong()) - 1L,
        thumb = url,
        image = url,
        title = "历史插画 · ${entry.stringValue("thumb_name").ifBlank { key }}",
    )
}

private fun JsonObject.stringValue(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()
