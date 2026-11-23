package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.store.KeyboardShortcutStore
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.adaptive.CustomKeyBinding
import com.android.purebilibili.core.ui.adaptive.CustomKeyValidationResult
import com.android.purebilibili.core.ui.adaptive.CustomShortcutOverrides
import com.android.purebilibili.core.ui.adaptive.CustomShortcutSlot
import com.android.purebilibili.core.ui.adaptive.LocalKeyboardShortcutOverrides
import com.android.purebilibili.core.ui.adaptive.ShortcutScope
import com.android.purebilibili.core.ui.adaptive.exportCustomShortcutConfigText
import com.android.purebilibili.core.ui.adaptive.formatCustomKeyBinding
import com.android.purebilibili.core.ui.adaptive.importCustomShortcutConfigText
import com.android.purebilibili.core.ui.adaptive.normalizeShortcutModifiers
import com.android.purebilibili.core.ui.adaptive.validateCustomKeyBinding
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch

private data class ShortcutDisplayRow(
    val title: String,
    val keysLabel: String,
    val slot: CustomShortcutSlot? = null,
    val isCustomized: Boolean = false,
)

@Composable
private fun resolveSlotTitle(slot: CustomShortcutSlot): String = when (slot) {
    CustomShortcutSlot.AppOpenSearch -> stringResource(R.string.keyboard_shortcut_open_search)
    CustomShortcutSlot.AppOpenSettings -> stringResource(R.string.keyboard_shortcut_open_settings)
    CustomShortcutSlot.PlayerPlayPause -> stringResource(R.string.keyboard_shortcut_player_play_pause)
    CustomShortcutSlot.PlayerTheaterMode -> "影院模式切换"
    CustomShortcutSlot.PlayerFullscreen -> stringResource(R.string.keyboard_shortcut_player_fullscreen)
    CustomShortcutSlot.PlayerMute -> stringResource(R.string.keyboard_shortcut_player_mute)
    CustomShortcutSlot.PlayerDanmaku -> stringResource(R.string.keyboard_shortcut_player_danmaku)
    CustomShortcutSlot.PlayerLike -> "点赞"
    CustomShortcutSlot.PlayerCoin -> "投币"
    CustomShortcutSlot.PlayerFavorite -> "收藏"
    CustomShortcutSlot.PlayerTriple -> "一键三连"
    CustomShortcutSlot.PlayerScreenshot -> stringResource(R.string.keyboard_shortcut_player_screenshot)
    CustomShortcutSlot.PlayerLock -> stringResource(R.string.keyboard_shortcut_player_lock)
    CustomShortcutSlot.PlayerPreviousPart -> "上一个分 P"
    CustomShortcutSlot.PlayerNextPart -> "下一个分 P"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardShortcutsSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overrides = LocalKeyboardShortcutOverrides.current
    var editingSlot by remember { mutableStateOf<CustomShortcutSlot?>(null) }

    val screenTitle = stringResource(R.string.keyboard_shortcuts_title)
    val backLabel = stringResource(R.string.common_back)
    val contentBottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 32.dp
    )

    val appRows = listOf(
        ShortcutDisplayRow(
            title = resolveSlotTitle(CustomShortcutSlot.AppOpenSearch),
            keysLabel = formatCustomKeyBinding(overrides.bindingFor(CustomShortcutSlot.AppOpenSearch)),
            slot = CustomShortcutSlot.AppOpenSearch,
            isCustomized = overrides.isCustomized(CustomShortcutSlot.AppOpenSearch),
        ),
        ShortcutDisplayRow(
            title = resolveSlotTitle(CustomShortcutSlot.AppOpenSettings),
            keysLabel = formatCustomKeyBinding(overrides.bindingFor(CustomShortcutSlot.AppOpenSettings)),
            slot = CustomShortcutSlot.AppOpenSettings,
            isCustomized = overrides.isCustomized(CustomShortcutSlot.AppOpenSettings),
        ),
        ShortcutDisplayRow(
            title = stringResource(R.string.keyboard_shortcut_select_bottom_nav),
            keysLabel = "Ctrl+1 – Ctrl+9",
        ),
    )

    val playerCustomizableSlots = listOf(
        CustomShortcutSlot.PlayerPlayPause,
        CustomShortcutSlot.PlayerTheaterMode,
        CustomShortcutSlot.PlayerFullscreen,
        CustomShortcutSlot.PlayerMute,
        CustomShortcutSlot.PlayerDanmaku,
        CustomShortcutSlot.PlayerLike,
        CustomShortcutSlot.PlayerCoin,
        CustomShortcutSlot.PlayerFavorite,
        CustomShortcutSlot.PlayerTriple,
        CustomShortcutSlot.PlayerScreenshot,
        CustomShortcutSlot.PlayerLock,
        CustomShortcutSlot.PlayerPreviousPart,
        CustomShortcutSlot.PlayerNextPart,
    )
    val playerRows = playerCustomizableSlots.map { slot ->
        ShortcutDisplayRow(
            title = resolveSlotTitle(slot),
            keysLabel = formatCustomKeyBinding(overrides.bindingFor(slot)),
            slot = slot,
            isCustomized = overrides.isCustomized(slot),
        )
    } + listOf(
        ShortcutDisplayRow(
            title = stringResource(R.string.keyboard_shortcut_player_seek_short),
            keysLabel = stringResource(R.string.keyboard_shortcut_player_seek_short_keys),
        ),
        ShortcutDisplayRow(
            title = stringResource(R.string.keyboard_shortcut_player_seek_long),
            keysLabel = stringResource(R.string.keyboard_shortcut_player_seek_long_keys),
        ),
        ShortcutDisplayRow(
            title = stringResource(R.string.keyboard_shortcut_player_volume),
            keysLabel = stringResource(R.string.keyboard_shortcut_player_volume_keys),
        ),
        ShortcutDisplayRow(
            title = stringResource(R.string.keyboard_shortcut_player_percent),
            keysLabel = stringResource(R.string.keyboard_shortcut_player_percent_keys),
        ),
        ShortcutDisplayRow(
            title = stringResource(R.string.keyboard_shortcut_player_speed),
            keysLabel = stringResource(R.string.keyboard_shortcut_player_speed_keys),
        ),
    )

    EntranceGroup {
        SettingsPageScaffold(
            title = screenTitle,
            onBack = onBack,
            backContentDescription = backLabel,
            bottomContentPadding = contentBottomPadding,
            lazyListContent = {
                item {
                    Box(modifier = Modifier.entrance()) {
                        AppText(
                            text = "点击可自定义的条目后按下物理按键即可重新绑定",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                item {
                    Box(modifier = Modifier.entrance()) {
                        AppPreferenceSectionTitle(stringResource(R.string.keyboard_shortcuts_section_app))
                    }
                }
                item {
                    Box(modifier = Modifier.entrance()) {
                        ShortcutGroupCard(
                            rows = appRows,
                            onEditSlot = { editingSlot = it },
                        )
                    }
                }
                item {
                    Box(modifier = Modifier.entrance()) {
                        AppPreferenceSectionTitle(stringResource(R.string.keyboard_shortcuts_section_player))
                    }
                }
                item {
                    Box(modifier = Modifier.entrance()) {
                        Column {
                            AppText(
                                text = stringResource(R.string.keyboard_shortcuts_player_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ShortcutGroupCard(
                                rows = playerRows,
                                onEditSlot = { editingSlot = it },
                            )
                        }
                    }
                }
                item {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppOutlinedButton(
                            onClick = {
                                val exported = exportCustomShortcutConfigText(overrides)
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("BiliPai 快捷键配置", exported)
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    "已复制快捷键配置到剪贴板",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                        ) {
                            AppText("导出键位")
                        }
                        AppOutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                val raw = clipboard.primaryClip
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                val imported = importCustomShortcutConfigText(raw)
                                if (imported == null) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "剪贴板中未找到有效的快捷键配置",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    scope.launch { KeyboardShortcutStore.replaceAll(context, imported) }
                                    android.widget.Toast.makeText(
                                        context,
                                        "已导入快捷键配置",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        ) {
                            AppText("导入键位")
                        }
                        if (!overrides.isEmpty) {
                            AppOutlinedButton(
                                onClick = {
                                    scope.launch { KeyboardShortcutStore.resetAll(context) }
                                },
                            ) {
                                AppText("恢复默认")
                            }
                        }
                    }
                }
            },
        )
    }

    editingSlot?.let { slot ->
        ShortcutKeyCaptureDialog(
            slot = slot,
            slotTitle = resolveSlotTitle(slot),
            overrides = overrides,
            onDismiss = { editingSlot = null },
            onSave = { binding ->
                editingSlot = null
                scope.launch { KeyboardShortcutStore.saveBinding(context, slot, binding) }
            },
            onResetSlot = {
                editingSlot = null
                scope.launch { KeyboardShortcutStore.resetSlot(context, slot) }
            },
        )
    }
}

@Composable
private fun ShortcutGroupCard(
    rows: List<ShortcutDisplayRow>,
    onEditSlot: (CustomShortcutSlot) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AppShapes.container(ContainerLevel.Field))
            .background(AppSurfaceTokens.cardContainer())
    ) {
        rows.forEachIndexed { index, row ->
            ShortcutRow(
                title = row.title,
                keysLabel = row.keysLabel,
                editable = row.slot != null,
                isCustomized = row.isCustomized,
                onClick = row.slot?.let { slot -> { onEditSlot(slot) } },
            )
            if (index != rows.lastIndex) {
                AppHorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    title: String,
    keysLabel: String,
    editable: Boolean,
    isCustomized: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppContentColors.primaryText(),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
        AppText(
            text = if (isCustomized) "$keysLabel · 自定义" else keysLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCustomized) {
                AppContentColors.accent()
            } else if (editable) {
                AppContentColors.primaryText()
            } else {
                AppContentColors.secondaryText()
            },
        )
    }
}

@Composable
private fun ShortcutKeyCaptureDialog(
    slot: CustomShortcutSlot,
    slotTitle: String,
    overrides: CustomShortcutOverrides,
    onDismiss: () -> Unit,
    onSave: (CustomKeyBinding) -> Unit,
    onResetSlot: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var capturedBinding by remember(slot) { mutableStateOf<CustomKeyBinding?>(null) }
    var validationError by remember(slot) { mutableStateOf<String?>(null) }
    val currentBinding = overrides.bindingFor(slot)
    val conflictSlot = capturedBinding?.let { overrides.findConflict(slot, it) }
    val conflictTitle = conflictSlot?.let { resolveSlotTitle(it) }

    DisposableEffect(slot) {
        KeyboardShortcutStore.isCapturingShortcut = true
        onDispose { KeyboardShortcutStore.isCapturingShortcut = false }
    }
    LaunchedEffect(slot) {
        focusRequester.requestFocus()
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("自定义「$slotTitle」") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(
                    text = if (slot.scope == ShortcutScope.App) {
                        "请按下新的 Ctrl 组合键（如 Ctrl+G）"
                    } else {
                        "请按下新的单键或 Shift 组合键（如 P 或 Shift+P）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(AppShapes.container(ContainerLevel.Field))
                        .background(AppSurfaceTokens.surfaceContainer())
                        .border(
                            width = 1.5.dp,
                            color = if (validationError != null) AppContentColors.error() else AppContentColors.accent(),
                            shape = AppShapes.container(ContainerLevel.Field),
                        )
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val native = keyEvent.nativeKeyEvent
                            if (
                                native.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE ||
                                native.keyCode == android.view.KeyEvent.KEYCODE_BACK
                            ) {
                                return@onPreviewKeyEvent false
                            }
                            if (native.repeatCount != 0) return@onPreviewKeyEvent true
                            val candidate = CustomKeyBinding(
                                keyCode = native.keyCode,
                                modifiers = normalizeShortcutModifiers(native.metaState),
                            )
                            when (val validation = validateCustomKeyBinding(slot, candidate)) {
                                CustomKeyValidationResult.Valid -> {
                                    capturedBinding = candidate
                                    validationError = null
                                }
                                is CustomKeyValidationResult.Invalid -> {
                                    validationError = validation.reason
                                }
                            }
                            true
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = capturedBinding?.let(::formatCustomKeyBinding)
                            ?: "当前：${formatCustomKeyBinding(currentBinding)}（等待按键…）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                        textAlign = TextAlign.Center,
                    )
                }
                if (validationError != null) {
                    AppText(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.error(),
                    )
                } else if (conflictTitle != null) {
                    AppText(
                        text = "该按键已分配给「$conflictTitle」，保存后将自动互换键位",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.accent(),
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                enabled = capturedBinding != null && validationError == null,
                onClick = { capturedBinding?.let(onSave) },
            ) {
                AppText("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (overrides.isCustomized(slot)) {
                    AppTextButton(onClick = onResetSlot) {
                        AppText("恢复默认")
                    }
                }
                AppTextButton(onClick = onDismiss) {
                    AppText("取消")
                }
            }
        },
    )
}
