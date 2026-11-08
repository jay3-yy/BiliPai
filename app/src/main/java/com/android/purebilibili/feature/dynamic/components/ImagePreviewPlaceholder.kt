package com.android.purebilibili.feature.dynamic.components

import android.content.Context
import coil3.Image
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.withBiliImageQuality

internal data class ImagePreviewPlaceholder(val cacheKey: MemoryCache.Key?, val image: Image?)

/** 沿用缩略图的 URL、画质及缓存 extras；预览原图请求的质量不参与来源身份计算。 */
internal fun resolveImagePreviewPlaceholder(
    context: Context,
    imageLoader: ImageLoader,
    source: String,
    dataSaver: Boolean,
    listQuality: Int,
): ImagePreviewPlaceholder {
    val key = resolveImagePreviewPlaceholderCacheKey(source) ?: return ImagePreviewPlaceholder(null, null)
    val request = ImageRequest.Builder(context).data(key).memoryCacheKey(key).build()
        .withBiliImageQuality(dataSaver, listQuality)
    val cacheKey = MemoryCache.Key(key, request.memoryCacheKeyExtras)
    return ImagePreviewPlaceholder(cacheKey, imageLoader.memoryCache?.get(cacheKey)?.image)
}
