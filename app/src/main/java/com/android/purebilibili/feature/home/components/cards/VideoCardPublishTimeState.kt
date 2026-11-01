package com.android.purebilibili.feature.home.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.components.LocalPageImageLoadingAllowed
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.SharedContentRepository
import com.android.purebilibili.feature.video.ui.section.resolveCompactPublishTimeRowText
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val publishTimeRequests = Semaphore(2)

/** 缺失时间只为当前可见视频按需补取，复用账号隔离的详情缓存。 */
@Composable
internal fun rememberVideoCardPublishTimeText(
    video: VideoItem,
    enabled: Boolean,
    isVisibleInWindow: Boolean,
): String {
    if (!enabled) return ""
    if (video.pubdate > 0L) return resolveCompactPublishTimeRowText(video.pubdate)
    return rememberMissingVideoCardPublishTimeText(video, isVisibleInWindow)
}

@Composable
private fun rememberMissingVideoCardPublishTimeText(
    video: VideoItem,
    isVisibleInWindow: Boolean,
): String {
    val accountIdentity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    var fetchedPubdate by remember(video.bvid, video.aid, accountIdentity) { mutableLongStateOf(0L) }
    var attempted by remember(video.bvid, video.aid, accountIdentity) { mutableStateOf(false) }
    val cachedPubdate = SharedContentRepository.cachedDetailInfo(video.bvid, video.aid)?.pubdate ?: 0L
    val pubdate = cachedPubdate.takeIf { it > 0L } ?: fetchedPubdate
    val isVideo = video.contentType.isBlank() || video.contentType == "av"
    val pageAllowsWork = LocalPageImageLoadingAllowed.current
    val visualWorkActive = rememberVisualWorkActive()
    val workAllowed = isVideo && isVisibleInWindow &&
        pageAllowsWork && visualWorkActive && LocalHomeCardAuxiliaryWorkAllowed.current

    LaunchedEffect(video.bvid, video.aid, accountIdentity, pubdate, workAllowed) {
        if (!workAllowed || pubdate > 0L || attempted) return@LaunchedEffect
        // 快速滑过的卡片不发起额外请求；离开视口后取消排队与等待。
        delay(150)
        val result = publishTimeRequests.withPermit {
            SharedContentRepository.detail(video.bvid, video.aid)
        }
        fetchedPubdate = result.getOrNull()?.pubdate?.takeIf { it > 0L } ?: 0L
        attempted = true
    }
    return resolveCompactPublishTimeRowText(pubdate).ifBlank { "发布时间未知" }
}
