package com.android.purebilibili.feature.home

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.android.purebilibili.feature.home.policy.HomeFeedScrollAnchor
import com.android.purebilibili.feature.home.policy.HomeFeedScrollAnchorSaver
import kotlinx.coroutines.delay

/** 首页只保留自己的视口和交互状态，详情返回会话仍由导航层通知。 */
internal class HomeReturnState(private val anchorState: MutableState<HomeFeedScrollAnchor?>) {
    var pendingAnchor by anchorState
        private set
    var videoNavigationInProgress by mutableStateOf(false)
        private set
    private var navigationGeneration = 0L
    private var leftHomeDuringVideoNavigation = false
    val contentInteractionRestored: Boolean get() = !videoNavigationInProgress

    fun freezeAnchorIfMissing(anchor: HomeFeedScrollAnchor) {
        if (pendingAnchor == null) pendingAnchor = anchor
    }

    fun beginVideoNavigation(anchor: HomeFeedScrollAnchor? = null) {
        if (anchor != null) pendingAnchor = anchor
        navigationGeneration++
        leftHomeDuringVideoNavigation = false
        videoNavigationInProgress = true
    }

    /** 视频入口可能转向其他播放页面；恢复点击不能只依赖详情页返回通知。 */
    fun onHomeVisibilityChanged(visible: Boolean) {
        if (!visible && videoNavigationInProgress) {
            leftHomeDuringVideoNavigation = true
        } else if (visible && leftHomeDuringVideoNavigation) {
            videoNavigationInProgress = false
            leftHomeDuringVideoNavigation = false
        }
    }

    /** 保护窗不阻塞交互；期间再次打开视频会使旧结束通知失效。 */
    suspend fun finishVideoReturn(protectionDurationMs: Long, onConsumed: () -> Unit): Boolean {
        videoNavigationInProgress = false
        val generation = navigationGeneration
        delay(protectionDurationMs)
        if (generation != navigationGeneration || videoNavigationInProgress) return false
        onConsumed()
        return true
    }

    fun consumeAnchor(anchor: HomeFeedScrollAnchor) {
        if (pendingAnchor === anchor) pendingAnchor = null
    }
}

@Composable
internal fun rememberHomeReturnState(): HomeReturnState {
    val anchor = rememberSaveable(stateSaver = HomeFeedScrollAnchorSaver) {
        mutableStateOf<HomeFeedScrollAnchor?>(null)
    }
    return remember(anchor) { HomeReturnState(anchor) }
}
