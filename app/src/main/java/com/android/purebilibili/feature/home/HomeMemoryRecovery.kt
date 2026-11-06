package com.android.purebilibili.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.lifecycle.FairMemoryNavigationRegistry
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable

@Serializable
internal data class HomeFeedScrollAnchor(
    val key: String,
    val scrollOffsetDp: Float,
    val regularLayout: Boolean,
    val viewportWidthPx: Int,
    val columns: Int,
    val density: Float,
    val fontScale: Float,
)

@Serializable
internal data class HomeFeedMemorySnapshot(
    val id: String,
    val category: String,
    val popularSubCategory: String,
    val videos: List<VideoItem>,
    val pageIndex: Int,
    val hasMore: Boolean,
    val recommendRequestIndex: Int,
    val followOffset: String? = null,
    val followBaseline: String? = null,
    val anchor: HomeFeedScrollAnchor? = null,
    val headerOffsetDp: Float = 0f,
    val tabsCollapsed: Boolean = false,
)

internal fun supportsHomeFeedMemoryRecovery(category: HomeCategory): Boolean =
    category != HomeCategory.LIVE && category != HomeCategory.ANIME

/** 大列表只进入本地持久化与 ViewModel，不进入 Activity 保存的 Bundle。 */
internal object HomeMemoryRecoveryRuntime {
    private val _pending = MutableStateFlow<Pair<Long, HomeFeedMemorySnapshot>?>(null)
    val pending = _pending.asStateFlow()

    fun install(accountMid: Long, snapshot: HomeFeedMemorySnapshot?) {
        _pending.value = snapshot?.let { accountMid to it }
    }

    fun take(accountMid: Long): HomeFeedMemorySnapshot? {
        val saved = _pending.value
        _pending.value = null
        return saved?.takeIf { it.first == accountMid }?.second
    }
}

@Composable
internal fun rememberHomeMemoryRecoveryReady(viewModel: HomeViewModel): Boolean {
    val ownsHome = LocalActivity.current is MainActivity
    var ready by remember(viewModel) { mutableStateOf(false) }
    LaunchedEffect(viewModel, ownsHome) {
        // 视频宿主不能取走主首页的数据；它晚于主页读到快照时也要通知已挂载的首页。
        HomeMemoryRecoveryRuntime.pending.collect { transfer ->
            if (ownsHome && transfer != null) {
                val snapshot = HomeMemoryRecoveryRuntime.take(TokenManager.midCache ?: 0L)
                if (snapshot != null) {
                    ready = false
                    viewModel.restoreMemorySnapshot(snapshot)
                }
            }
            ready = true
        }
    }
    return ready
}

@Composable
internal fun RegisterHomeMemoryCapture(capture: () -> HomeFeedMemorySnapshot?) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val latestCapture by rememberUpdatedState(capture)
    DisposableEffect(activity) {
        val unregister = FairMemoryNavigationRegistry.registerHome(activity) { latestCapture() }
        onDispose { unregister() }
    }
}

internal fun resolveHomeMemoryAnchorIndex(
    anchor: HomeFeedScrollAnchor,
    videoKeys: List<String>,
    headerItemCount: Int,
    dividerIndex: Int?,
): Int? = resolveHomeOldContentGridItemIndex(
    anchorVideoIndex = videoKeys.indexOf(anchor.key),
    itemCount = videoKeys.size,
    dividerIndex = dividerIndex,
    headerItemCount = headerItemCount,
)

/** 版式变化后只恢复内容身份，不把旧卡片的像素偏移套到新尺寸上。 */
internal fun resolveHomeMemoryScrollOffsetPx(
    anchor: HomeFeedScrollAnchor,
    regularLayout: Boolean,
    viewportWidthPx: Int,
    columns: Int,
    density: Density,
): Int = if (anchor.regularLayout == regularLayout && anchor.viewportWidthPx == viewportWidthPx &&
    anchor.columns == columns && anchor.density == density.density && anchor.fontScale == density.fontScale
) {
    with(density) { androidx.compose.ui.unit.Dp(anchor.scrollOffsetDp).roundToPx() }
} else 0
