package com.android.purebilibili.feature.following

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
import com.android.purebilibili.data.model.response.RelationTagItem

@Composable
internal fun FollowGroupOrderDialog(
    ownerMid: Long,
    onDismiss: () -> Unit,
    onSaved: (AccountSessionIdentity, List<RelationTagItem>) -> Unit,
    modifier: Modifier = Modifier,
    model: FollowGroupOrderViewModel = viewModel(key = "follow_group_order_$ownerMid"),
) {
    val identity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(model, identity) {
        if (identity.mid != ownerMid || ownerMid <= 0L || state.identity != identity) latestDismiss()
        else model.load()
    }
    LaunchedEffect(state.saved, state.identity) {
        if (state.saved && state.identity == identity) {
            model.consumeSaved()
            latestSaved(identity, state.draft)
            latestDismiss()
        }
    }
    if (identity.mid == ownerMid && state.identity == identity) {
        FollowGroupOrderContent(state, model::move, model::save, model::load, onDismiss, modifier)
    }
}

@Composable
internal fun FollowGroupOrderContent(
    state: FollowGroupOrderState,
    onMove: (Long, Long) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var draggedId by remember(state.identity) { mutableStateOf<Long?>(null) }
    val dragCenter = remember { mutableFloatStateOf(0f) }
    val latestGroups by rememberUpdatedState(state.draft)
    val latestMove by rememberUpdatedState(onMove)
    var pendingDiscard by remember(state.identity) { mutableStateOf<String?>(null) }
    val busy = state.loading || state.saving
    val requestDismiss: () -> Unit = {
        if (!state.saving) {
            if (state.dirty) pendingDiscard = "close" else onDismiss()
        }
    }
    val listHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        .minus(260.dp).coerceIn(64.dp, 420.dp)
    AppAlertDialog(
        modifier = modifier,
        onDismissRequest = requestDismiss,
        title = { AppText("关注分组排序") },
        text = {
            Column {
                AppText("长按拖动手柄调整自定义分组，也可点按上下箭头。全部、默认分组与系统分组保持固定。")
                if (state.loading) AppText("正在读取分组…", Modifier.padding(vertical = 12.dp))
                state.error?.let { AppText(it, Modifier.padding(vertical = 8.dp)) }
                if (state.original != null && state.draft.isEmpty()) AppText("暂无可排序的自定义分组", Modifier.padding(vertical = 12.dp))
                if (state.draft.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = !busy && draggedId == null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = listHeight).appScrollBoundaryHaptics(listState),
                    ) {
                        itemsIndexed(state.draft, key = { _, tag -> tag.tagid }) { index, tag ->
                            val dragging = draggedId == tag.tagid
                            AppListItem(
                                modifier = Modifier
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer {
                                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tag.tagid }
                                        translationY = if (draggedId == tag.tagid && info != null) dragCenter.floatValue - info.offset - info.size / 2f else 0f
                                        shadowElevation = if (draggedId == tag.tagid) 4.dp.toPx() else 0f
                                    },
                                headlineContent = { AppText(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { AppText("${tag.count} 人") },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AppIconButton(
                                            onClick = { onMove(tag.tagid, state.draft[index - 1].tagid) },
                                            enabled = !busy && draggedId == null && index > 0,
                                        ) { AppIcon(Icons.Outlined.KeyboardArrowUp, "上移${tag.name}") }
                                        AppIconButton(
                                            onClick = { onMove(tag.tagid, state.draft[index + 1].tagid) },
                                            enabled = !busy && draggedId == null && index < state.draft.lastIndex,
                                        ) { AppIcon(Icons.Outlined.KeyboardArrowDown, "下移${tag.name}") }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .semantics {
                                                    contentDescription = "长按拖动${tag.name}"
                                                    customActions = if (busy) emptyList() else buildList {
                                                        if (index > 0) add(CustomAccessibilityAction("上移") { onMove(tag.tagid, state.draft[index - 1].tagid); true })
                                                        if (index < state.draft.lastIndex) add(CustomAccessibilityAction("下移") { onMove(tag.tagid, state.draft[index + 1].tagid); true })
                                                    }
                                                }
                                                .focusable(!busy)
                                                .pointerInput(tag.tagid, busy) {
                                                    if (!busy) detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tag.tagid }?.let { info ->
                                                                draggedId = tag.tagid
                                                                dragCenter.floatValue = info.offset + info.size / 2f
                                                            }
                                                        },
                                                        onDragEnd = { draggedId = null },
                                                        onDragCancel = { draggedId = null },
                                                        onDrag = { change, amount ->
                                                            if (draggedId == tag.tagid) {
                                                                change.consume()
                                                                val layout = listState.layoutInfo
                                                                val top = layout.viewportStartOffset.toFloat()
                                                                val bottom = layout.viewportEndOffset.toFloat()
                                                                dragCenter.floatValue = (dragCenter.floatValue + amount.y).coerceIn(top, bottom)
                                                                val source = layout.visibleItemsInfo.firstOrNull { it.key == tag.tagid }
                                                                val target = layout.visibleItemsInfo.firstOrNull { it.key != tag.tagid && dragCenter.floatValue >= it.offset && dragCenter.floatValue <= it.offset + it.size }
                                                                // 等上一轮移动完成布局后再交换，避免使用旧位置来回换位。
                                                                if (source?.index == latestGroups.indexOfFirst { it.tagid == tag.tagid } && target != null) {
                                                                    (target.key as? Long)?.let { latestMove(tag.tagid, it) }
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
                                },
                            )
                        }
                    }
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
