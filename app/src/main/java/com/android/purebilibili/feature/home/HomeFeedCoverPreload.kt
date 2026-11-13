package com.android.purebilibili.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.imageLoader
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.homeCoverDecodePolicy
import com.android.purebilibili.core.util.AppImageLoadPriority
import com.android.purebilibili.core.util.backgroundImagePreload
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.flow.collectLatest

/** Headers have no video range; aligned rows and waterfall cards share this mapping. */
internal fun resolveHomeVisibleVideoRange(
    visibleKeys: Set<Any>,
    videoRangesByLazyKey: Map<String, IntRange>,
): IntRange? {
    var first = Int.MAX_VALUE
    var last = -1
    for (key in visibleKeys) {
        if (key !is String) continue
        val range = videoRangesByLazyKey[key] ?: continue
        first = minOf(first, range.first)
        last = maxOf(last, range.last)
    }
    return if (last < 0) null else first..last
}

/** Preload from the displayed list, after carousel exclusion and row grouping. */
@Composable
internal fun HomeFeedCoverPreload(
    videos: List<VideoItem>,
    visibleVideoRange: State<IntRange?>,
    isScrolling: State<Boolean>,
    requestSpec: HomeCoverRequestSpec,
    allowed: Boolean,
    aheadCount: Int,
    imageLoader: ImageLoader? = null,
) {
    val context = LocalContext.current
    val loader = imageLoader ?: context.imageLoader
    LaunchedEffect(context, loader, videos, visibleVideoRange, isScrolling, requestSpec, allowed, aheadCount) {
        if (!allowed || aheadCount <= 0) return@LaunchedEffect
        snapshotFlow { if (isScrolling.value) null else visibleVideoRange.value?.last }
            .collectLatest { lastVisibleIndex ->
                // A header-only viewport cancels work instead of pretending video zero is visible.
                if (lastVisibleIndex == null) return@collectLatest
                val range = resolveHomeCoverPreloadRange(
                    isDataSaverActive = false,
                    isScrollInProgress = false,
                    lastVisibleIndex = lastVisibleIndex,
                    totalItemCount = videos.size,
                    preloadAheadCount = aheadCount,
                ) ?: return@collectLatest
                val sources = range.map { resolveHomeCoverImageSource(videos[it], false, requestSpec) }
                    .filter { it.url.isNotBlank() }
                    .distinctBy { it.cacheKey }
                for (source in sources) {
                    AppImageLoadPriority.prefetch(HOME_COVER_PRELOAD_SETTLE_MS) {
                        loader.execute(
                            ImageRequest.Builder(context)
                                .data(source.url)
                                .size(requestSpec.widthPx, requestSpec.heightPx)
                                .homeCoverDecodePolicy()
                                .memoryCacheKey(source.cacheKey)
                                .diskCacheKey(source.cacheKey)
                                .backgroundImagePreload()
                                .build(),
                        )
                    }
                }
            }
    }
}
