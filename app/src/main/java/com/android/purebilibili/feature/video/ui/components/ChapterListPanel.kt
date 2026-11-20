package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.android.purebilibili.core.ui.components.AppSwitch as Switch
import com.android.purebilibili.core.ui.components.AppText as Text
import com.android.purebilibili.core.ui.components.appScrollBoundaryHaptics
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.ViewPoint

@Composable
fun ChapterListPanel(
    viewPoints: List<ViewPoint>, currentPositionMs: Long,
    onSeek: (Long) -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    placement: PlayerListPopupPlacement = PlayerListPopupPlacement.START,
    anchorBounds: IntRect? = null,
    isFullscreen: Boolean = false, playerBounds: IntRect? = null,
    showViewPoints: Boolean = true, onShowViewPointsChange: ((Boolean) -> Unit)? = null,
    useGlassSurface: Boolean = false,
) {
    val selectionHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_SEEK)
    val currentIndex = viewPoints.indexOfLast { currentPositionMs >= it.fromMs && currentPositionMs < it.to.toLong() * 1000L }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    PlayerDetailPanel(
        "分段信息", isFullscreen, playerBounds, onDismiss,
        draggable = true, useGlassSurface = useGlassSurface,
    ) { requestDismiss ->
        Row(modifier.fillMaxWidth().height(45.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("分段信息", fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (onShowViewPointsChange != null) Switch(checked = showViewPoints, onCheckedChange = onShowViewPointsChange)
            PanelAction(Icons.Default.Close, "关闭", requestDismiss)
        }
        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth().appScrollBoundaryHaptics(state),
            overscrollEffect = null,
            contentPadding = PaddingValues(top = 7.dp, bottom = 100.dp),
        ) {
            itemsIndexed(viewPoints) { index, point ->
                val range = "${FormatUtils.formatDuration(point.from)} - ${FormatUtils.formatDuration(point.to)}"
                PlayerPanelMediaRow(
                    point.content, point.imgUrl, 0, index == currentIndex, range,
                    expandTitle = true,
                ) {
                    if (index != currentIndex) selectionHaptic(HapticType.LIGHT)
                    onSeek(point.fromMs)
                    requestDismiss()
                }
            }
        }
    }
}
