package com.android.purebilibili.core.util

import coil3.Extras
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.allowRgb565
import coil3.memory.MemoryCache
import com.android.purebilibili.core.store.SettingsManager

private val OriginalBiliImage = Extras.Key<Boolean>(default = false)
private val BackgroundImagePreload = Extras.Key<Boolean>(default = false)
private const val QUALITY_CACHE_EXTRA = "bilipai_image_quality_v1"
private const val WIDTH_CACHE_EXTRA = "bilipai_image_width_bucket_v1"
private const val QUALITY_DISK_SUFFIX = "|bilipai-image-quality-v1:"

/** 大图预览和原图保存不经过列表图片的压缩策略。 */
internal fun ImageRequest.Builder.originalBiliImage() = apply {
    extras[OriginalBiliImage] = true
}

internal fun ImageRequest.Builder.backgroundImagePreload() = apply {
    extras[BackgroundImagePreload] = true
}

/** 显示、预加载、取色共用同一质量及缓存身份；重复应用不会重复追加。 */
internal fun ImageRequest.withBiliImageQuality(
    dataSaver: Boolean = SettingsManager.isDataSaverActive(context),
    listQuality: Int = com.android.purebilibili.core.store.ContentDisplayPreferences.imageSnapshot.listQuality,
    targetWidthPx: Int? = null,
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
    val resolved = BiliImageQualityPolicy.resolveUrl(url, selectedQuality, targetWidthPx)
    val quality = selectedQuality.toString()
    val widthBucket = targetWidthPx?.let(BiliImageQualityPolicy::resolveCdnWidthBucket)?.toString()
    val widthTag = widthBucket?.let { "_w$it" }.orEmpty()
    if (resolved == url && QUALITY_CACHE_EXTRA !in memoryCacheKeyExtras) return this
    if (resolved == url && memoryCacheKeyExtras[QUALITY_CACHE_EXTRA] == quality &&
        memoryCacheKeyExtras[WIDTH_CACHE_EXTRA] == widthBucket
    ) return this
    return newBuilder()
        .data(resolved)
        .memoryCacheKeyExtra(QUALITY_CACHE_EXTRA, quality)
        .apply {
            if (widthBucket != null) memoryCacheKeyExtra(WIDTH_CACHE_EXTRA, widthBucket)
            this@withBiliImageQuality.diskCacheKey?.let { key ->
                diskCacheKey(key.substringBefore(QUALITY_DISK_SUFFIX) + QUALITY_DISK_SUFFIX + quality + widthTag)
            }
            this@withBiliImageQuality.placeholderMemoryCacheKey?.let { key ->
                val extras = buildMap {
                    putAll(key.extras)
                    put(QUALITY_CACHE_EXTRA, quality)
                    if (widthBucket != null) put(WIDTH_CACHE_EXTRA, widthBucket)
                }
                placeholderMemoryCacheKey(MemoryCache.Key(key.key, extras))
            }
            // 解码缓存也区分新旧颜色精度。
            allowRgb565(false)
        }
        .build()
}

internal class BiliImageQualityInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // 组件已准备好的请求沿用同一快照，并按已解析视口宽度补充 CDN 宽度桶，避免下载超大图。
        val request = chain.request
        val targetWidthPx = (chain.size.width as? coil3.size.Dimension.Pixels)?.px
        val prepared = if (QUALITY_CACHE_EXTRA in request.memoryCacheKeyExtras) {
            if (targetWidthPx != null && request.extras[OriginalBiliImage] != true) {
                val quality = request.memoryCacheKeyExtras[QUALITY_CACHE_EXTRA]?.toIntOrNull()
                val url = (request.data as? String) ?: (request.data as? android.net.Uri)?.toString()
                val resized = if (quality != null && url != null) {
                    BiliImageQualityPolicy.resolveUrl(url, quality, targetWidthPx)
                } else null
                if (resized != null && resized != url) {
                    val widthBucket = BiliImageQualityPolicy.resolveCdnWidthBucket(targetWidthPx)?.toString()
                    val widthTag = widthBucket?.let { "_w$it" }.orEmpty()
                    request.newBuilder()
                        .data(resized)
                        .apply {
                            if (widthBucket != null) memoryCacheKeyExtra(WIDTH_CACHE_EXTRA, widthBucket)
                            request.diskCacheKey?.let { key ->
                                diskCacheKey(key.substringBefore(QUALITY_DISK_SUFFIX) + QUALITY_DISK_SUFFIX + quality + widthTag)
                            }
                        }
                        .build()
                } else request
            } else request
        } else {
            request.withBiliImageQuality(targetWidthPx = targetWidthPx)
        }
        return if (prepared.extras[BackgroundImagePreload] == true) {
            chain.withRequest(prepared).proceed()
        } else {
            AppImageLoadPriority.foreground { chain.withRequest(prepared).proceed() }
        }
    }
}
