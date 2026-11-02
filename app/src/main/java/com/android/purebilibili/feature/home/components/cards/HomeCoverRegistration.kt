package com.android.purebilibili.feature.home.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchEntry
import com.android.purebilibili.core.util.HomeCoverReturnPrefetchRegistry

/** Publish once per visible session and cover identity; leaving keeps the return snapshot. */
@Composable
internal fun RegisterVisibleHomeCover(
    allowed: Boolean,
    bvid: String,
    url: String,
    cacheKey: String,
    widthPx: Int,
    heightPx: Int,
) {
    if (!allowed || widthPx <= 0 || heightPx <= 0) return
    val publication = remember(bvid, url, cacheKey, widthPx, heightPx) {
        object {
            val entry = HomeCoverReturnPrefetchEntry(bvid.trim(), url, cacheKey, widthPx, heightPx)
            var published = false
        }
    }
    SideEffect {
        if (!publication.published) {
            HomeCoverReturnPrefetchRegistry.onCardVisible(publication.entry)
            publication.published = true
        }
    }
}
