package com.android.purebilibili.feature.video.playback.policy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

internal data class OnlineCountMediaKey(val bvid: String, val cid: Long)

internal data class OnlineCountRequest(
    val media: OnlineCountMediaKey,
    val requestToken: Long,
)

internal fun visibleOnlineCountRequest(
    request: OnlineCountRequest?,
    currentSession: OnlineCountRequest,
    visibleMedia: Collection<OnlineCountMediaKey>,
    isInBackground: Boolean,
): OnlineCountRequest? = request?.takeIf {
    it == currentSession && it.media in visibleMedia && shouldRefreshOnlineCount(
        showOnlineCountEnabled = true,
        isInBackground = isInBackground,
        currentBvid = it.media.bvid,
        currentCid = it.media.cid,
    )
}

/** 隐藏或换片时取消请求与间隔等待；恢复显示立即刷新，失败保留上一份已知人数。 */
internal suspend fun observeOnlineCountRequests(
    requests: Flow<OnlineCountRequest?>,
    load: suspend (OnlineCountRequest) -> String?,
    onCount: (OnlineCountRequest, String) -> Unit,
    onFailure: (Exception) -> Unit,
) {
    requests.distinctUntilChanged().collectLatest { request ->
        if (request == null) return@collectLatest
        while (currentCoroutineContext().isActive) {
            try {
                val count = load(request)
                currentCoroutineContext().ensureActive()
                if (count != null) onCount(request, count)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error)
            }
            delay(30_000L)
        }
    }
}
