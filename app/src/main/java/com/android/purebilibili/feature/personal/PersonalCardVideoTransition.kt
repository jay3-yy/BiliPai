// 文件路径: feature/personal/PersonalCardVideoTransition.kt
package com.android.purebilibili.feature.personal

import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.LocalVideoTransitionAdaptiveInfo
import com.android.purebilibili.core.ui.transition.NativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.shouldUseVideoCardShellSharedBounds
import com.android.purebilibili.core.ui.transition.withMeasuredCoverDecodeSize
import com.android.purebilibili.core.util.CardPositionManager

/** 卡片/封面 bounds 持有器，由 onGloballyPositioned 写入。 */
internal class PersonalCardBoundsHolder {
    var cardBounds: Rect? = null
    var coverBounds: Rect? = null
}

/**
 * 个人列表视频卡（历史/收藏/稍后再看等）共享的转场录制样板：
 * 收集 shared-transition 作用域、解析动效规格、持有卡片/封面 bounds 与
 * 原生像素快照控制器，并在点击时统一上报 [CardPositionManager]。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal class PersonalCardVideoTransition(
    val sourceRoute: String?,
    val useSharedBounds: Boolean,
    val shellModifier: Modifier,
    val nativeCardSnapshot: NativeVideoCardSnapshotController,
    val bounds: PersonalCardBoundsHolder,
    private val screenWidthPx: Float,
    private val screenHeightPx: Float,
) {
    val cardBounds: Rect?
        get() = bounds.cardBounds
    val coverBounds: Rect?
        get() = bounds.coverBounds

    /** 点击时录制卡片几何与 chrome 快照；未完成布局时静默跳过。 */
    fun recordPosition(
        bvid: String,
        stacked: Boolean,
        sourceCornerDp: Int,
        chrome: VideoCardSourceChromeSnapshot,
    ) {
        if (CardPositionManager.isVideoNavigationDebounceActive()) {
            // 防抖窗口内的点击注定被导航层拒绝；不得覆写在飞转场的全局记录与冻结像素。
            return
        }
        val cardBounds = bounds.cardBounds ?: return
        val sourceCoverBounds = bounds.coverBounds
        CardPositionManager.recordVideoCardPosition(
            bvid = bvid,
            sourceRoute = sourceRoute,
            bounds = cardBounds,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            sourceCornerDp = sourceCornerDp,
            coverBounds = sourceCoverBounds,
            sourceLayout = if (stacked) {
                VideoCardSourceLayout.STACKED
            } else {
                VideoCardSourceLayout.SIDE_BY_SIDE
            },
            sourceChromeSnapshot = chrome.withMeasuredCoverDecodeSize(sourceCoverBounds),
        )
        nativeCardSnapshot.capture()
    }
}

/**
 * 收集个人列表卡转场所需的公共状态。原样板分布在
 * HistoryPersonalCard / FavoritePersonalCard / WatchLaterVideoCard 中逐行重复。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun rememberPersonalCardVideoTransition(
    bvid: String,
    transitionEnabled: Boolean,
    clipShape: Shape,
): PersonalCardVideoTransition {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val speedSettings = LocalVideoSharedTransitionSpeedSettings.current
    val transitionAdaptiveInfo = LocalVideoTransitionAdaptiveInfo.current
    val sharedElementReady = transitionEnabled &&
        bvid.isNotBlank() &&
        sourceRoute != null &&
        sharedTransitionScope != null &&
        animatedVisibilityScope != null
    val motionSpec = remember(sourceRoute, transitionEnabled, speedSettings, transitionAdaptiveInfo) {
        resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = sourceRoute,
            transitionEnabled = transitionEnabled,
            speedSettings = speedSettings,
            adaptiveInfo = transitionAdaptiveInfo,
        )
    }
    val useSharedBounds = shouldUseVideoCardShellSharedBounds(
        sourceRoute = sourceRoute,
        transitionEnabled = sharedElementReady,
    )
    val boundsHolder = remember(bvid) { PersonalCardBoundsHolder() }
    val nativeCardSnapshot = rememberNativeVideoCardSnapshotController(bvid)
    val shellModifier = Modifier.videoCardSourceAnchor(bvid, clipShape)
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    // 每次组合重建轻量包装：shellModifier 随 motionSpec 重建，避免持有过期 modifier。
    return PersonalCardVideoTransition(
        sourceRoute = sourceRoute,
        useSharedBounds = useSharedBounds,
        shellModifier = shellModifier,
        nativeCardSnapshot = nativeCardSnapshot,
        bounds = boundsHolder,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
    )
}
