package com.android.purebilibili.core.util

import coil3.Extras
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.allowRgb565
import coil3.memory.MemoryCache
import com.android.purebilibili.core.store.SettingsManager

private val OriginalBiliImage = Extras.Key<Boolean>(default = false)
private const val QUALITY_CACHE_EXTRA = "bilipai_image_quality_v1"
private const val QUALITY_DISK_SUFFIX = "|bilipai-image-quality-v1:"

/** 大图预览和原图保存不经过列表图片的压缩策略。 */
internal fun ImageRequest.Builder.originalBiliImage() = apply {
    extras[OriginalBiliImage] = true
}

/** 显示、预加载、取色共用同一质量及缓存身份；重复应用不会重复追加。 */
internal fun ImageRequest.withBiliImageQuality(
    dataSaver: Boolean = SettingsManager.isDataSaverActive(context),
    listQuality: Int = com.android.purebilibili.core.store.ContentDisplayPreferences.imageSnapshot.listQuality,
): ImageRequest {
    if (extras[OriginalBiliImage] == true) return this
    val url = when (val value = data) {
        is String -> value
        is android.net.Uri -> value.toString()
        else -> return this
    }
    val normalizedQuality = com.android.purebilibili.core.store.ContentDisplayPreferences.normalizeQuality(listQuality)
    val selectedQuality = if (dataSaver) BiliImageQualityPolicy.DATA_SAVER_QUALITY
        else normalizedQuality.takeIf { it > 0 } ?: BiliImageQualityPolicy.HIGH_QUALITY
    val resolved = BiliImageQualityPolicy.resolveUrl(url, selectedQuality)
    if (resolved == url && QUALITY_CACHE_EXTRA !in memoryCacheKeyExtras) return this
    val quality = selectedQuality.toString()
    if (resolved == url && memoryCacheKeyExtras[QUALITY_CACHE_EXTRA] == quality) return this
    return newBuilder()
        .data(resolved)
        .memoryCacheKeyExtra(QUALITY_CACHE_EXTRA, quality)
        .apply {
            this@withBiliImageQuality.diskCacheKey?.let { key ->
                diskCacheKey(key.substringBefore(QUALITY_DISK_SUFFIX) + QUALITY_DISK_SUFFIX + quality)
            }
            this@withBiliImageQuality.placeholderMemoryCacheKey?.let { key ->
                placeholderMemoryCacheKey(MemoryCache.Key(key.key, key.extras + (QUALITY_CACHE_EXTRA to quality)))
            }
            // 解码缓存也区分新旧颜色精度。
            allowRgb565(false)
        }
        .build()
}

internal class BiliImageQualityInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // 组件已准备好的请求沿用同一快照，避免网络切换时缓存回放与实际加载身份不一致。
        val request = chain.request
        val prepared = if (QUALITY_CACHE_EXTRA in request.memoryCacheKeyExtras) {
            request
        } else {
            request.withBiliImageQuality()
        }
        return chain.withRequest(prepared).proceed()
    }
}
