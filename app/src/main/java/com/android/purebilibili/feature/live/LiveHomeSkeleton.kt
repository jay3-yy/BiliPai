package com.android.purebilibili.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.ContentVideoGridItemSkeleton
import kotlin.math.ceil

internal data class LiveSkeletonLayout(val cardCount: Int)

/** 与真实列表共用实际可用宽度、列数与封面比例，铺满手机、大屏和窄分屏视口。 */
@Composable
internal fun rememberLiveSkeletonLayout(
    width: Dp,
    height: Dp,
    metrics: LiveBiliPaiHomeMetrics,
): LiveSkeletonLayout {
    val density = LocalDensity.current
    return remember(width, height, metrics, density) {
        with(density) {
            val cells = with(LiveRoomGridCells()) {
                calculateCrossAxisCellSizes(
                    availableSize = (width - (metrics.safeSpaceDp * 2).dp).roundToPx(),
                    spacing = metrics.cardSpaceDp.dp.roundToPx(),
                )
            }
            val cardHeight = cells.first().toDp().value / metrics.coverAspectRatio + 50f
            val rows = ceil(height.value / (cardHeight + metrics.cardSpaceDp)).toInt() + 1
            LiveSkeletonLayout(cardCount = rows * cells.size)
        }
    }
}

internal fun LazyGridScope.liveHomeHeaderSkeletonItems(
    showFollowSection: Boolean,
    metrics: LiveBiliPaiHomeMetrics,
    color: () -> Color,
) {
    if (showFollowSection) {
        item(key = "live_follow_header_skeleton", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ContentSkeletonBlock(color, Modifier.weight(1f).height(22.dp))
                Spacer(Modifier.width(24.dp))
                ContentSkeletonBlock(color, Modifier.width(64.dp).height(22.dp))
            }
        }
        item(key = "live_follow_avatars_skeleton", span = { GridItemSpan(maxLineSpan) }) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val count = ceil(maxWidth.value / metrics.followItemExtentDp).toInt()
                Row(Modifier.clipToBounds()) {
                    repeat(count) {
                        Column(
                            Modifier.width(metrics.followItemExtentDp.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ContentSkeletonBlock(color, Modifier.size((metrics.followAvatarSizeDp + 4).dp), CircleShape)
                            Spacer(Modifier.height(6.dp))
                            ContentSkeletonBlock(color, Modifier.width(44.dp).height(14.dp))
                        }
                    }
                }
            }
        }
    }
    item(key = "live_categories_skeleton", span = { GridItemSpan(maxLineSpan) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) {
                ContentSkeletonBlock(color, Modifier.weight(1f).height(40.dp))
            }
        }
    }
}

internal fun LazyGridScope.liveRoomSkeletonItems(
    count: Int,
    metrics: LiveBiliPaiHomeMetrics,
    color: () -> Color,
) {
    items(count, key = { "live_room_skeleton_$it" }) {
        ContentVideoGridItemSkeleton(
            coverAspectRatio = metrics.coverAspectRatio,
            blockColorProvider = color,
        )
    }
}
