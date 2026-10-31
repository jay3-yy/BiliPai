package com.android.purebilibili.feature.space

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.*

@Composable
internal fun FavoriteResourceOrderDialog(
    folderId: Long,
    ownerMid: Long,
    onDismiss: () -> Unit,
    onSaved: (AccountSessionIdentity) -> Unit,
    modifier: Modifier = Modifier,
    model: FavoriteResourceOrderViewModel = viewModel(key = "favorite_resource_order_$folderId"),
) {
    val identity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(model, identity, folderId, ownerMid) {
        if (identity.mid != ownerMid || ownerMid <= 0L || state.identity != identity) latestDismiss()
        else model.load(folderId, ownerMid)
    }
    LaunchedEffect(state.saved, state.identity) {
        if (state.saved && state.identity == identity) {
            model.consumeSaved()
            latestSaved(identity)
            latestDismiss()
        }
    }
    if (identity.mid == ownerMid && state.identity == identity) {
        FavoriteResourceOrderContent(state, model::move, model::save, { model.load(folderId, ownerMid) }, model::loadNext, onDismiss, modifier)
    }
}

@Composable
internal fun FavoriteResourceOrderContent(
    state: FavoriteResourceOrderState,
    onMove: (String, String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var draggedId by remember(state.identity) { mutableStateOf<String?>(null) }
    val dragCenter = remember { mutableFloatStateOf(0f) }
    val latestItems by rememberUpdatedState(state.draft)
    val latestMove by rememberUpdatedState(onMove)
    var pendingDiscard by remember(state.identity) { mutableStateOf<String?>(null) }
    val busy = state.loading || state.saving
    val requestDismiss: () -> Unit = {
        if (!state.saving) {
            if (state.dirty) pendingDiscard = "close" else onDismiss()
        }
    }
    val listHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        .minus(360.dp).coerceIn(64.dp, 420.dp)
    AppAlertDialog(
        modifier = modifier,
        onDismissRequest = requestDismiss,
        title = { AppText("收藏内容排序") },
        text = {
            Column {
                AppText("长按拖动手柄或点按上下箭头调整顺序，保存后同步到收藏夹。可继续加载更多内容。")
                if (state.loading) AppText("正在读取收藏内容…", Modifier.padding(vertical = 12.dp))
                state.error?.let { AppText(it, Modifier.padding(vertical = 8.dp)) }
                if (state.original != null && state.draft.isEmpty()) AppText("收藏夹暂无内容", Modifier.padding(vertical = 12.dp))
                if (state.draft.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = !busy && draggedId == null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = listHeight),
                    ) {
                        itemsIndexed(state.draft, key = { _, tag -> tag.orderKey }) { index, tag ->
                            val dragging = draggedId == tag.orderKey
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer {
                                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tag.orderKey }
                                        translationY = if (draggedId == tag.orderKey && info != null) dragCenter.floatValue - info.offset - info.size / 2f else 0f
                                        shadowElevation = if (draggedId == tag.orderKey) 4.dp.toPx() else 0f
                                    },
                            ) {
                                AppText(tag.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                        AppIconButton(
                                            onClick = { onMove(tag.orderKey, state.draft[index - 1].orderKey) },
                                            enabled = !busy && draggedId == null && index > 0,
                                        ) { AppIcon(Icons.Outlined.KeyboardArrowUp, "上移${tag.title}") }
                                        AppIconButton(
                                            onClick = { onMove(tag.orderKey, state.draft[index + 1].orderKey) },
                                            enabled = !busy && draggedId == null && index < state.draft.lastIndex,
                                        ) { AppIcon(Icons.Outlined.KeyboardArrowDown, "下移${tag.title}") }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .semantics {
                                                    contentDescription = "长按拖动${tag.title}"
                                                    customActions = if (busy) emptyList() else buildList {
                                                        if (index > 0) add(CustomAccessibilityAction("上移") { onMove(tag.orderKey, state.draft[index - 1].orderKey); true })
                                                        if (index < state.draft.lastIndex) add(CustomAccessibilityAction("下移") { onMove(tag.orderKey, state.draft[index + 1].orderKey); true })
                                                    }
                                                }
                                                .focusable(!busy)
                                                .pointerInput(tag.orderKey, busy) {
                                                    if (!busy) detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tag.orderKey }?.let { info ->
                                                                draggedId = tag.orderKey
                                                                dragCenter.floatValue = info.offset + info.size / 2f
                                                            }
                                                        },
                                                        onDragEnd = { draggedId = null },
                                                        onDragCancel = { draggedId = null },
                                                        onDrag = { change, amount ->
                                                            if (draggedId == tag.orderKey) {
                                                                change.consume()
                                                                val layout = listState.layoutInfo
                                                                val top = layout.viewportStartOffset.toFloat()
                                                                val bottom = layout.viewportEndOffset.toFloat()
                                                                dragCenter.floatValue = (dragCenter.floatValue + amount.y).coerceIn(top, bottom)
                                                                val source = layout.visibleItemsInfo.firstOrNull { it.key == tag.orderKey }
                                                                val target = layout.visibleItemsInfo.firstOrNull { it.key != tag.orderKey && dragCenter.floatValue >= it.offset && dragCenter.floatValue <= it.offset + it.size }
                                                                // 等上一轮移动完成布局后再交换，避免使用旧位置来回换位。
                                                                if (source?.index == latestItems.indexOfFirst { it.orderKey == tag.orderKey } && target != null) {
                                                                    (target.key as? String)?.let { latestMove(tag.orderKey, it) }
                                                                }
                                                                // 只在手指移动时滚动，不启动常驻逐帧任务。
                                                                val edge = 48.dp.toPx()
                                                                val scroll = when {
                                                                    dragCenter.floatValue < top + edge -> -12.dp.toPx()
                                                                    dragCenter.floatValue > bottom - edge -> 12.dp.toPx()
                                                                    else -> 0f
                                                                }
                                                                if (scroll != 0f) listState.dispatchRawDelta(scroll)
                                                            }
                                                        },
                                                    )
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) { AppIcon(Icons.Outlined.DragHandle, null) }
                                }
                            }
                        }
                    }
                }
                if (state.original != null) {
                    AppText("已加载 ${state.draft.size} 项" + (state.total?.let { "，共 $it 项" } ?: ""))
                }
                if (state.hasMore || (state.error != null && state.page > 0)) {
                    AppTextButton(onClick = onLoadMore, enabled = !busy && draggedId == null) { AppText("继续加载") }
                }
                AppTextButton(
                    enabled = !busy && draggedId == null,
                    onClick = { if (state.dirty) pendingDiscard = "reload" else onReload() },
                ) { AppText(if (state.original == null) "重试" else "重新读取") }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onSave, enabled = state.dirty && !busy && draggedId == null) {
                AppText(if (state.saving) "保存中…" else "保存")
            }
        },
        dismissButton = { AppTextButton(onClick = requestDismiss, enabled = !state.saving) { AppText("取消") } },
    )
    if (pendingDiscard != null) {
        AppAlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { AppText("放弃未保存的排序？") },
            text = { AppText("当前排序尚未同步到 B 站。") },
            confirmButton = { AppTextButton(onClick = {
                val reload = pendingDiscard == "reload"
                pendingDiscard = null
                if (reload) onReload() else onDismiss()
            }) { AppText("放弃修改") } },
            dismissButton = { AppTextButton(onClick = { pendingDiscard = null }) { AppText("继续调整") } },
        )
    }
}
