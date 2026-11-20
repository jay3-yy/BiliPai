package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.player.PlayerBottomControlPlacement
import com.android.purebilibili.core.store.player.PlayerMenuConfiguration
import com.android.purebilibili.core.store.player.PlayerMenuStore
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppLazyColumn
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.LocalAppGlassMenuSource
import com.android.purebilibili.core.ui.components.LocalAppGlassPanelDismissAction
import java.io.IOException
import kotlinx.coroutines.launch

internal const val PLAYER_MENU_EDITOR_TITLE = "自定义菜单"

internal interface PlayerMenuEntry {
    val id: String
    val title: String
}

internal data class PlayerBottomControlOption(val title: String, val showInMenuByDefault: Boolean)

private const val ONLY_ON_BOTTOM_BAR = "仅底栏"
private const val ONLY_IN_MORE_MENU = "仅「更多」"

private fun bottomControlPlacementLabel(placement: PlayerBottomControlPlacement?, option: PlayerBottomControlOption): String =
    when (placement) {
        PlayerBottomControlPlacement.BOTTOM_BAR -> ONLY_ON_BOTTOM_BAR
        PlayerBottomControlPlacement.MORE_MENU -> ONLY_IN_MORE_MENU
        null -> if (option.showInMenuByDefault) "默认（更多可隐藏）" else ONLY_ON_BOTTOM_BAR
    }

internal fun <Entry : PlayerMenuEntry> PlayerMenuConfiguration.orderedEntries(
    defaultEntries: List<Entry>,
): List<Entry> {
    val byId = defaultEntries.associateBy { it.id }
    return (order.mapNotNull(byId::get) + defaultEntries).distinct()
}

internal fun <Entry : PlayerMenuEntry> PlayerMenuConfiguration?.visibleEntries(
    defaultEntries: List<Entry>,
    available: Set<Entry>,
): List<Entry> = if (this == null) {
    defaultEntries.filter { it in available }
} else {
    orderedEntries(defaultEntries).filter { entry ->
        entry in available && when (bottomControlPlacements[entry.id]) {
            PlayerBottomControlPlacement.BOTTOM_BAR -> false
            PlayerBottomControlPlacement.MORE_MENU -> true
            null -> entry.id !in hidden
        }
    }
}

@Composable
internal fun rememberPlayerMenuConfiguration(store: PlayerMenuStore): PlayerMenuConfiguration? {
    val context = LocalContext.current
    return remember(context, store) { store.configuration(context) }
        .collectAsStateWithLifecycle(initialValue = null).value
}

@Composable
internal fun <Entry : PlayerMenuEntry> PlayerMenuEditor(
    configuration: PlayerMenuConfiguration,
    defaultEntries: List<Entry>,
    availableEntries: Set<Entry>,
    onBack: () -> Unit,
    onSave: suspend (PlayerMenuConfiguration) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    topShortcutEntries: List<Entry> = emptyList(),
    bottomControlOptions: Map<Entry, PlayerBottomControlOption> = emptyMap(),
    playbackSettingsEntries: Set<Entry> = emptySet(),
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var order by remember { mutableStateOf(configuration.orderedEntries(defaultEntries)) }
    var hidden by remember { mutableStateOf(configuration.hidden) }
    var topShortcut by remember {
        mutableStateOf(topShortcutEntries.firstOrNull { it.id == configuration.topShortcutId })
    }
    var bottomPlacements by remember { mutableStateOf(configuration.bottomControlPlacements) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var draggedEntry by remember { mutableStateOf<Entry?>(null) }
    val dragCenter = remember { mutableFloatStateOf(0f) }
    val canEdit = !saving && draggedEntry == null

    fun move(from: Int, to: Int) {
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    Column(modifier) {
        AppLazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            state = listState,
            userScrollEnabled = draggedEntry == null,
        ) {
            item {
                PlayerSettingsRow("返回", onClick = onBack.takeIf { canEdit })
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    AppText(PLAYER_MENU_EDITOR_TITLE)
                    AppText("关闭开关可隐藏条目，长按右侧手柄可排序。部分条目仅在支持时显示。")
                    if (playbackSettingsEntries.isNotEmpty()) {
                        AppText("「${PLAYER_PLAYBACK_SETTINGS_TITLE}」入口随组内第一个可见条目排序。")
                    }
                }
                if (topShortcutEntries.isNotEmpty()) {
                    val noShortcut = "仅在「更多」中显示"
                    PlayerSettingsPopupRow(
                        title = PLAYER_TOP_SHORTCUT_TITLE,
                        current = topShortcut?.title ?: noShortcut,
                        options = listOf(noShortcut) + topShortcutEntries.map { it.title },
                        enabled = canEdit,
                    ) { index ->
                        topShortcut = topShortcutEntries.getOrNull(index - 1)?.also { entry ->
                            hidden = hidden - entry.id
                        }
                    }
                    AppText("顶栏空间不足时，所选操作仍会显示在「更多」中。", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                bottomControlOptions.forEach { (entry, option) ->
                    val placements = if (option.showInMenuByDefault) {
                        listOf(null, PlayerBottomControlPlacement.BOTTOM_BAR, PlayerBottomControlPlacement.MORE_MENU)
                    } else {
                        listOf(null, PlayerBottomControlPlacement.MORE_MENU)
                    }
                    PlayerSettingsPopupRow(
                        title = option.title,
                        current = bottomControlPlacementLabel(bottomPlacements[entry.id], option),
                        options = placements.map { bottomControlPlacementLabel(it, option) },
                        enabled = canEdit,
                    ) { index ->
                        val placement = placements[index]
                        bottomPlacements = if (placement == null) bottomPlacements - entry.id
                        else bottomPlacements + (entry.id to placement)
                    }
                }
            }
            itemsIndexed(order, key = { _, entry -> entry.id }) { index, entry ->
                val dragging = draggedEntry == entry
                val title = entry.title
                val placement = bottomPlacements[entry.id]
                    ?: PlayerBottomControlPlacement.BOTTOM_BAR.takeIf { bottomControlOptions[entry]?.showInMenuByDefault == false }
                val placementLabel = when (placement) {
                    PlayerBottomControlPlacement.BOTTOM_BAR -> ONLY_ON_BOTTOM_BAR
                    PlayerBottomControlPlacement.MORE_MENU -> ONLY_IN_MORE_MENU
                    null -> null
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            val info = if (draggedEntry == entry) {
                                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.id }
                            } else null
                            translationY = if (info == null) 0f else dragCenter.floatValue - info.offset - info.size / 2f
                        }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                        AppText(title)
                        if (entry == topShortcut) {
                            AppText(PLAYER_TOP_SHORTCUT_TITLE, color = AppContentColors.secondaryText())
                        }
                        placementLabel?.let { AppText(it, color = AppContentColors.secondaryText()) }
                        if (entry in playbackSettingsEntries) {
                            AppText(PLAYER_PLAYBACK_SETTINGS_TITLE, color = AppContentColors.secondaryText())
                        }
                        if (entry !in availableEntries) {
                            AppText("当前不可用", color = AppContentColors.secondaryText())
                        }
                    }
                    AppSwitch(
                        checked = when (placement) {
                            PlayerBottomControlPlacement.BOTTOM_BAR -> false
                            PlayerBottomControlPlacement.MORE_MENU -> true
                            null -> entry.id !in hidden
                        },
                        onCheckedChange = { checked ->
                            hidden = if (checked) hidden - entry.id else hidden + entry.id
                        },
                        enabled = canEdit && entry != topShortcut && placement == null,
                        modifier = Modifier.semantics { contentDescription = "显示$title" },
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = "长按拖动$title"
                                customActions = if (!canEdit) emptyList() else buildList {
                                    if (index > 0) add(CustomAccessibilityAction("上移") { move(index, index - 1); true })
                                    if (index < order.lastIndex) add(CustomAccessibilityAction("下移") { move(index, index + 1); true })
                                }
                            }
                            .focusable(canEdit)
                            .pointerInput(entry, saving) {
                                if (saving) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.id }?.let { info ->
                                            draggedEntry = entry
                                            dragCenter.floatValue = info.offset + info.size / 2f
                                        }
                                    },
                                    onDragEnd = { draggedEntry = null },
                                    onDragCancel = { draggedEntry = null },
                                    onDrag = { change, amount ->
                                        if (draggedEntry != entry) return@detectDragGesturesAfterLongPress
                                        change.consume()
                                        val layout = listState.layoutInfo
                                        val top = layout.viewportStartOffset.toFloat()
                                        val bottom = layout.viewportEndOffset.toFloat()
                                        val center = (dragCenter.floatValue + amount.y).coerceIn(top, bottom)
                                        dragCenter.floatValue = center
                                        val from = order.indexOf(entry)
                                        // 编辑说明占用列表的第一个位置。
                                        val source = layout.visibleItemsInfo.firstOrNull { it.index == from + 1 }
                                        val target = layout.visibleItemsInfo.firstOrNull { item ->
                                            if (item.index == 0 || center < item.offset || center >= item.offset + item.size) {
                                                return@firstOrNull false
                                            }
                                            val midpoint = item.offset + item.size / 2f
                                            // 不等高的行交换后，手柄仍可能暂时压在上一行上。
                                            (item.index > from + 1 && amount.y > 0 && center >= midpoint) ||
                                                (item.index < from + 1 && amount.y < 0 && center <= midpoint)
                                        }
                                        if (source?.key == entry.id && target != null) move(from, target.index - 1)
                                        val edge = 48.dp.toPx()
                                        val scroll = when {
                                            amount.y < 0f && center < top + edge -> -12.dp.toPx()
                                            amount.y > 0f && center > bottom - edge -> 12.dp.toPx()
                                            else -> 0f
                                        }
                                        if (scroll != 0f) listState.dispatchRawDelta(scroll)
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(Icons.Outlined.DragHandle, null, tint = AppContentColors.secondaryText())
                    }
                }
            }
        }
        if (saveFailed) AppText("菜单配置保存失败，请重试。", Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppTextButton(
                onClick = {
                    order = defaultEntries
                    hidden = emptySet()
                    topShortcut = null
                    bottomPlacements = emptyMap()
                },
                enabled = canEdit,
            ) { AppText("恢复默认") }
            AppTextButton(
                onClick = {
                    val draft = PlayerMenuConfiguration(
                        order = order.map { it.id },
                        hidden = hidden,
                        topShortcutId = topShortcut?.id,
                        bottomControlPlacements = bottomPlacements,
                    )
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            onSave(draft)
                            onSaved()
                        } catch (_: IOException) {
                            saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = canEdit,
            ) { AppText(if (saving) "保存中…" else "保存") }
        }
    }
}

@Composable
internal fun <Entry : PlayerMenuEntry> PlayerMenuEditorPanel(
    store: PlayerMenuStore,
    configuration: PlayerMenuConfiguration,
    defaultEntries: List<Entry>,
    availableEntries: Set<Entry>,
    onReturnToMenu: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 编辑器居中使用可用窗口宽度，原菜单仍从原按钮展开。
    CompositionLocalProvider(LocalAppGlassMenuSource provides null) {
        PlayerNativeSettingsHost(
            onDismiss = onDismiss,
            title = PLAYER_MENU_EDITOR_TITLE,
        ) {
            val dispatchAfterDismiss = LocalAppGlassPanelDismissAction.current
            val returnToMenu = {
                if (dispatchAfterDismiss != null) dispatchAfterDismiss(onReturnToMenu)
                else onReturnToMenu()
            }
            PlayerMenuEditor(
                configuration = configuration,
                defaultEntries = defaultEntries,
                availableEntries = availableEntries,
                onBack = returnToMenu,
                onSave = { store.save(context, it) },
                onSaved = returnToMenu,
                modifier = Modifier.fillMaxWidth().then(
                    if (LocalPlayerGlassSettingsSurface.current) Modifier else Modifier.navigationBarsPadding()
                ),
            )
        }
    }
}
