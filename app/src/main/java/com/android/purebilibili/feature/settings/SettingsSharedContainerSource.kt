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

internal class SettingsSharedContainerOrigin(val owner: BiliPaiNavKey, color: Color) {
    var coordinates: LayoutCoordinates? = null
    var available by mutableStateOf(false)
    var color by mutableStateOf(color)
    var topRadius by mutableStateOf(0.dp)
    var bottomRadius by mutableStateOf(0.dp)
    var sharedBounds by mutableStateOf<SettingsSharedContainerBounds?>(null)
    val returnLanding = NavigationReturnLanding()
}

internal class SettingsSharedContainerSources {
    val sources = mutableStateMapOf<BiliPaiNavKey, SettingsSharedContainerOrigin>()
    var hostOrigin = Offset.Zero
    var hostSize = IntSize.Zero

    fun isAvailable(coordinates: LayoutCoordinates): Boolean =
        coordinates.isAttached && resolveSettingsContainerCaptureBounds(
            boundsInRoot = coordinates.boundsInRoot(),
            rowSize = coordinates.size,
            hostOrigin = hostOrigin,
            hostSize = hostSize,
        ) != null

    fun capture(target: BiliPaiNavKey, origin: SettingsSharedContainerOrigin) {
        origin.returnLanding.clear()
        val coordinates = origin.coordinates
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
