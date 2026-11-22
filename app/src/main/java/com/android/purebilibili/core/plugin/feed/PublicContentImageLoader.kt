package com.android.purebilibili.core.plugin.feed

import android.content.Context
import coil3.ImageLoader
import com.android.purebilibili.core.ui.components.biliImagePlaceholders
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.maxBitmapSize

/**
 * Separate loader/cache prevents RSS previews from falling back to the account image client.
 * Requests use the image-only public content client, which restores the same-site Referer
 * that hotlink protected image CDNs require.
 */
internal object PublicContentImageLoader {
    private const val FEED_MEMORY_CACHE_MAX_BYTES = 16L * 1024 * 1024
    private const val LINK_PREVIEW_MEMORY_CACHE_MAX_BYTES = 8L * 1024 * 1024

    @Volatile private var instance: ImageLoader? = null
    @Volatile private var previewInstance: ImageLoader? = null

    fun forLinkPreview(context: Context): ImageLoader = previewInstance ?: synchronized(this) {
        previewInstance ?: ImageLoader.Builder(context.applicationContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { publicLinkPreviewHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(LINK_PREVIEW_MEMORY_CACHE_MAX_BYTES)
                    .build()
            }
            .diskCache(null)
            .maxBitmapSize(coil3.size.Size(1280, 1280))
            .build().also { previewInstance = it }
    }

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .biliImagePlaceholders(context.applicationContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { publicContentImageHttpClient }))
                if (android.os.Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(FEED_MEMORY_CACHE_MAX_BYTES)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("public_content_images"))
                    .maxSizeBytes(30L * 1024 * 1024)
                    .build()
            }
            .maxBitmapSize(coil3.size.Size(4608, 4608))
            .build().also { instance = it }
    }

    fun trimMemory(clear: Boolean) {
        listOfNotNull(instance, previewInstance).forEach { loader ->
            loader.memoryCache?.apply {
                if (clear) clear() else trimToSize(size / 2)
            }
        }
    }

    fun clearMemoryCache() {
        trimMemory(clear = true)
    }
}
