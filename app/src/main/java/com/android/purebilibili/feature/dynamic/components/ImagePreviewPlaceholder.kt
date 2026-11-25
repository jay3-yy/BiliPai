package com.android.purebilibili.feature.dynamic.components

import android.content.Context
import coil3.Image
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.BiliImageQualityPolicy
import com.android.purebilibili.core.util.withBiliImageQuality

internal data class ImagePreviewPlaceholder(val cacheKey: MemoryCache.Key?, val image: Image?)

/**
 * 沿用缩略图的 URL、画质及缓存 extras；预览原图请求的质量不参与来源身份计算。
 * 列表图经拦截器按视图宽度追加宽度桶 extra 后，优先按来源宽度匹配，其次按高到低桶位回退。
 */
internal fun resolveImagePreviewPlaceholder(
    context: Context,
    imageLoader: ImageLoader,
    source: String,
    dataSaver: Boolean,
    listQuality: Int,
    sourceWidthPx: Int? = null,
): ImagePreviewPlaceholder {
    val key = resolveImagePreviewPlaceholderCacheKey(source) ?: return ImagePreviewPlaceholder(null, null)
    val baseRequest = ImageRequest.Builder(context).data(key).memoryCacheKey(key).build()
    val memoryCache = imageLoader.memoryCache
    val unsizedKey = MemoryCache.Key(
        key,
        baseRequest.withBiliImageQuality(dataSaver, listQuality).memoryCacheKeyExtras,
    )
    if (memoryCache == null) return ImagePreviewPlaceholder(unsizedKey, null)

    val candidateKeys = LinkedHashSet<MemoryCache.Key>()
    val preferredBucket = sourceWidthPx?.takeIf { it > 0 }?.let(BiliImageQualityPolicy::resolveCdnWidthBucket)
    if (preferredBucket != null) {
        candidateKeys += MemoryCache.Key(
            key,
            baseRequest.withBiliImageQuality(dataSaver, listQuality, targetWidthPx = preferredBucket).memoryCacheKeyExtras,
        )
    }
    candidateKeys += unsizedKey
    for (bucket in BiliImageQualityPolicy.supportedCdnWidthBuckets().asReversed()) {
        candidateKeys += MemoryCache.Key(
            key,
            baseRequest.withBiliImageQuality(dataSaver, listQuality, targetWidthPx = bucket).memoryCacheKeyExtras,
        )
    }

    for (candidateKey in candidateKeys) {
        memoryCache[candidateKey]?.image?.let { image ->
            return ImagePreviewPlaceholder(candidateKey, image)
        }
    }
    return ImagePreviewPlaceholder(preferredBucket?.let { candidateKeys.firstOrNull() } ?: unsizedKey, null)
}
