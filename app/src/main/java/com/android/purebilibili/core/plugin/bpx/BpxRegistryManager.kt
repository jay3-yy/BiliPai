package com.android.purebilibili.core.plugin.bpx

import android.content.Context
import com.android.purebilibili.core.network.GithubMirrorInterceptor
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.buildAppDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val PREFS_BPX_REGISTRIES = "bpx_registries"
private const val KEY_REGISTRY_URLS = "subscribed_urls"
const val OFFICIAL_STARTER_REGISTRY_URL =
    "https://raw.githubusercontent.com/jay3-yy/BiliPai/main/plugins/bpx-cookbook/registry.json"

data class LoadedBpxRegistry(
    val sourceUrl: String,
    val manifest: BpxRegistryManifest?,
    val error: String? = null,
)

object BpxRegistryManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .dns(buildAppDns())
            .addInterceptor(GithubMirrorInterceptor())
            .protocols(NetworkModule.resolveSharedNetworkProtocols())
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun getSubscribedUrls(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_BPX_REGISTRIES, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_REGISTRY_URLS, null)
        if (stored == null) return listOf(OFFICIAL_STARTER_REGISTRY_URL)
        return (listOf(OFFICIAL_STARTER_REGISTRY_URL) + stored).distinct()
    }

    fun addSubscribedUrl(context: Context, rawUrl: String): Result<List<String>> = runCatching {
        val url = rawUrl.trim()
        require(url.startsWith("https://") || url.startsWith("http://")) { "请输入有效的 http/https 订阅源链接" }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_BPX_REGISTRIES, Context.MODE_PRIVATE)
        val next = (getSubscribedUrls(context) + url).toSet()
        prefs.edit().putStringSet(KEY_REGISTRY_URLS, next).apply()
        next.toList()
    }

    fun removeSubscribedUrl(context: Context, url: String): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_BPX_REGISTRIES, Context.MODE_PRIVATE)
        val next = (getSubscribedUrls(context) - url).filter { it != OFFICIAL_STARTER_REGISTRY_URL }.toSet()
        prefs.edit().putStringSet(KEY_REGISTRY_URLS, next).apply()
        return getSubscribedUrls(context)
    }

    suspend fun fetchAllSubscribedRegistries(context: Context): List<LoadedBpxRegistry> =
        withContext(Dispatchers.IO) {
            getSubscribedUrls(context).map { url ->
                runCatching {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "BiliPai-BPX-Registry/1.0")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        val manifest = json.decodeFromString(BpxRegistryManifest.serializer(), resp.body.string())
                        LoadedBpxRegistry(sourceUrl = url, manifest = manifest)
                    }
                }.getOrElse { err ->
                    LoadedBpxRegistry(sourceUrl = url, manifest = null, error = err.message ?: "加载失败")
                }
            }
        }

    suspend fun downloadExtensionPreview(entry: BpxRegistryExtensionEntry): Result<BpxPreviewPackage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(entry.downloadUrl.trim())
                    .header("User-Agent", "BiliPai-BPX-Registry/1.0")
                    .get()
                    .build()
                val rawSource = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("下载失败: HTTP ${resp.code}")
                    resp.body.string()
                }
                val parsed = BpxOfflineCodec.parseBpxSource(rawSource).getOrThrow()
                if (entry.sha256.isNotBlank() && !entry.sha256.equals(parsed.sha256, ignoreCase = true)) {
                    throw SecurityException("扩展包 SHA-256 校验不一致，已拦截安装")
                }
                parsed
            }
        }

    fun hasNewerVersion(remoteVersion: String, installedVersion: String?): Boolean {
        if (installedVersion.isNullOrBlank()) return false
        val r = parseSemVerTriplet(remoteVersion) ?: return remoteVersion != installedVersion
        val i = parseSemVerTriplet(installedVersion) ?: return remoteVersion != installedVersion
        if (r.first != i.first) return r.first > i.first
        if (r.second != i.second) return r.second > i.second
        return r.third > i.third
    }

    private fun parseSemVerTriplet(v: String): Triple<Int, Int, Int>? {
        val core = v.trim().removePrefix("v").substringBefore('-')
        val parts = core.split('.')
        if (parts.size < 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        return Triple(major, minor, patch)
    }
}
