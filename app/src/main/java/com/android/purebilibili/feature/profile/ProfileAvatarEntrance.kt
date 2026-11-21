package com.android.purebilibili.feature.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.motion.scrollCompactExpansionSpring
import kotlinx.coroutines.flow.first

internal class ProfileAvatarEntrance(val sourceSize: Dp) {
    var played = false
}

@Composable
internal fun rememberProfileAvatarEntranceScale(
    entrance: ProfileAvatarEntrance?,
    pageActive: Boolean,
    photoState: ProfileAvatarExpansionState?,
    photoUrl: String,
    photoRangeProvider: () -> Float,
    listState: LazyListState,
): () -> Float {
    if (entrance == null) return { 1f }
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val canStart = pageActive && windowFocused && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val initialScale = entrance.sourceSize.value / PROFILE_EXPANDED_AVATAR_SIZE_DP
    val scale = remember(entrance) { Animatable(if (entrance.played) 1f else initialScale) }
    val density = LocalDensity.current.density
    val latestRange = rememberUpdatedState(photoRangeProvider)
    LaunchedEffect(entrance, photoState, photoUrl, listState, canStart, motionEnabled, density) {
        if (entrance.played) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        if (!canStart) return@LaunchedEffect
        if (photoState != null) {
            if (photoUrl.isBlank()) return@LaunchedEffect
            listState.scrollToItem(0)
            // 骨架切换和首轮测量可能先后到达；等当前头像和可展开几何同时就绪。
            val range = snapshotFlow {
                if (photoState.photoLoadStatus(photoUrl) == ProfileAvatarPhotoLoadStatus.Ready) latestRange.value() else 0f
            }.first { it > 0f }
            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                photoState.settle(
                    expanded = true,
                    range = range,
                    velocity = 0f,
                    density = density,
                    motion = motionEnabled,
                    entranceSourceSize = entrance.sourceSize,
                )
                if (range != latestRange.value()) photoState.updateExpandedRange(latestRange.value())
            }
            entrance.played = true
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        entrance.played = true
        if (motionEnabled) scale.animateTo(1f, scrollCompactExpansionSpring()) else scale.snapTo(1f)
    }
    return remember(scale, motionEnabled, photoState, photoUrl) {
        {
            val showFullCircle = photoState != null && (
                photoUrl.isBlank() ||
                    photoState.photoLoadStatus(photoUrl) == ProfileAvatarPhotoLoadStatus.Failed ||
                    (photoState.viewportWidthPx > 0 && latestRange.value() <= 0f)
                )
            if (motionEnabled && !showFullCircle) scale.value else 1f
        }
    }
}
