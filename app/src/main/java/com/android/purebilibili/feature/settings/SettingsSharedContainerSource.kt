package com.android.purebilibili.feature.settings

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.transition.NavigationReturnLanding
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.SettingsSharedContainerBounds

internal class SettingsSharedContainerOrigin(
    val owner: BiliPaiNavKey,
    color: Color,
    val sourceIdentity: Any? = null,
) {
    var coordinates: LayoutCoordinates? = null
    var available by mutableStateOf(false)
    var color by mutableStateOf(color)
    var topRadius by mutableStateOf(0.dp)
    var bottomRadius by mutableStateOf(0.dp)
    var sharedBounds by mutableStateOf<SettingsSharedContainerBounds?>(null)
    var returnLandingHandledByOwner = false
    val returnLanding = NavigationReturnLanding()
    var radiusUpdater: ((LayoutCoordinates) -> Unit)? = null
}

internal class SettingsSharedContainerSources(
    private val allowPartial: Boolean = false,
) {
    val sources = mutableStateMapOf<BiliPaiNavKey, SettingsSharedContainerOrigin>()
    var hostOrigin = Offset.Zero
    var hostSize = IntSize.Zero

    fun isAvailable(coordinates: LayoutCoordinates): Boolean {
        if (!coordinates.isAttached || coordinates.size.width <= 1 || coordinates.size.height <= 1 ||
            hostSize.width <= 1 || hostSize.height <= 1
        ) return false
        val visibleBounds = coordinates.boundsInRoot()
        if (allowPartial) {
            // Compose sharedBounds 直接驱动节点本身，不依赖窗口截图：
            // 卡片被底栏/顶栏部分遮挡或处于按压缩放态时，只要在宿主视口内仍可见即可展开。
            val visible = visibleBounds.translate(-hostOrigin)
            return minOf(visible.right, hostSize.width.toFloat()) - maxOf(visible.left, 0f) > 1f &&
                minOf(visible.bottom, hostSize.height.toFloat()) - maxOf(visible.top, 0f) > 1f
        }
        return resolveSettingsContainerCaptureBounds(
            boundsInRoot = visibleBounds,
            rowSize = coordinates.size,
            hostOrigin = hostOrigin,
            hostSize = hostSize,
            allowPartial = false,
            visibleBoundsInRoot = visibleBounds,
        ) != null
    }

    fun capture(target: BiliPaiNavKey, origin: SettingsSharedContainerOrigin) {
        origin.returnLanding.clear()
        val coordinates = origin.coordinates
        if (coordinates != null && coordinates.isAttached) {
            origin.radiusUpdater?.invoke(coordinates)
        }
        origin.available = coordinates != null && isAvailable(coordinates)
        if (origin.available) {
            sources[target] = origin
        } else {
            sources.remove(target)
        }
    }
}

internal val LocalSettingsContainerSources = staticCompositionLocalOf<SettingsSharedContainerSources?> { null }
internal val LocalSettingsContainerOwner = staticCompositionLocalOf<BiliPaiNavKey?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSettingsContainerSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
