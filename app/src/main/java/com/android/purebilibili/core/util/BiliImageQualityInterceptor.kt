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
    val normalizedQuality = com.android.purebilibili.core.store.ContentDisplayPreferences.normalizeQuality(listQuality)
    val selectedQuality = if (dataSaver) BiliImageQualityPolicy.DATA_SAVER_QUALITY
        else normalizedQuality.takeIf { it > 0 } ?: BiliImageQualityPolicy.HIGH_QUALITY
    return applyBiliImageQualityTransform(
        selectedQuality = selectedQuality,
        targetWidthPx = targetWidthPx,
    )
}

private fun ImageRequest.applyBiliImageQualityTransform(
    selectedQuality: Int,
    targetWidthPx: Int?,
): ImageRequest {
    val url = when (val value = data) {
        is String -> value
        is android.net.Uri -> value.toString()
        else -> return this
    }
    val resolution = BiliImageQualityPolicy.resolveUrlWithMetadata(url, selectedQuality, targetWidthPx)
    val resolved = resolution.url
    val quality = selectedQuality.toString()
    // 仅当本次 URL 变换实际追加了宽度桶（或请求上一次已记录该宽度桶）时才写入宽度缓存维度；
    // 原 URL 自带 640w 等宽度时保持与显示路径一致，不额外附加 _w672。
    val widthBucket = resolution.appliedWidthBucket?.toString()
        ?: memoryCacheKeyExtras[WIDTH_CACHE_EXTRA]
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
            this@applyBiliImageQualityTransform.diskCacheKey?.let { key ->
                diskCacheKey(key.substringBefore(QUALITY_DISK_SUFFIX) + QUALITY_DISK_SUFFIX + quality + widthTag)
            }
            this@applyBiliImageQualityTransform.placeholderMemoryCacheKey?.let { key ->
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
        // 组件已准备好的请求沿用同一质量快照，并按实际生效的 URL 变换统一内存、磁盘与占位缓存键。
        val request = chain.request
        val targetWidthPx = (chain.size.width as? coil3.size.Dimension.Pixels)?.px
        val prepared = if (QUALITY_CACHE_EXTRA in request.memoryCacheKeyExtras) {
            if (targetWidthPx != null && request.extras[OriginalBiliImage] != true) {
                val quality = request.memoryCacheKeyExtras[QUALITY_CACHE_EXTRA]?.toIntOrNull()
                if (quality != null) {
                    request.applyBiliImageQualityTransform(
                        selectedQuality = quality,
                        targetWidthPx = targetWidthPx,
                    )
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
