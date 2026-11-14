package com.android.purebilibili.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup
import top.yukonga.miuix.kmp.basic.DropdownEntry
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.AppUiStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.key
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * An action exposed from a page-level overflow menu.
 *
 * [children] open a secondary level inside the same window popup (for example a sort order).
 */
@Immutable
data class AppWindowAction(
    val label: String,
    val onClick: (() -> Unit)? = null,
    val icon: ImageVector? = null,
    val iconTint: Color? = null,
    val summary: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val children: List<AppWindowAction> = emptyList(),
)

/** 玻璃菜单将分级选项放入同一个面板，避免切换子级时重新测量材质外壳。 */
internal fun flattenGlassWindowActionGroups(groups: List<List<AppWindowAction>>): List<List<AppWindowAction>> {
    fun appendAction(target: MutableList<AppWindowAction>, action: AppWindowAction) {
        if (action.children.isEmpty()) {
            target += action
        } else {
            target += AppWindowAction(label = action.label, icon = action.icon, iconTint = action.iconTint, enabled = false)
            action.children.forEach { child ->
                appendAction(target, if (action.enabled) child else child.copy(enabled = false))
            }
        }
    }
    return groups.mapNotNull { group ->
        buildList { group.forEach { appendAction(this, it) } }.takeIf { it.isNotEmpty() }
    }
}

/**
 * Miuix window-level action menu used by page-level overflow buttons.
 *
 * Groups preserve the visual separation between related actions while sharing the same popup
 * implementation as Miuix settings choices.
 */
@Composable
fun AppWindowActionMenu(
    groups: List<List<AppWindowAction>>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    // Explicit anchor sizing is opt-in; existing menus retain native button geometry.
    anchorModifier: Modifier = Modifier,
    compact: Boolean = false,
    onActionSource: ((AppGlassMenuSource) -> Unit)? = null,
    // 独立玻璃胶囊由父层捕获完整材质；内部按钮只保留打开手势。
    source: AppGlassMenuSource? = null,
    sourceCapturedByParent: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 原生窗口菜单统一使用菜单条目与级联布局；紧凑列表仅用于其他材质。
    val nativeMenu = LocalAppUiStyle.current == AppUiStyle.MIUIX &&
        LocalAppThemeConfig.current.nativeMiuixPopupsEnabled && !appGlassMenuAvailable()
    if (compact && !nativeMenu) {
        AppCompactWindowActionMenu(
            groups = groups,
            modifier = modifier,
            enabled = enabled,
            onExpandedChange = onExpandedChange,
            anchorModifier = anchorModifier,
            onActionSource = onActionSource,
            source = source,
            sourceCapturedByParent = sourceCapturedByParent,
            content = content,
        )
        return
    }

    val glassSource = source ?: rememberAppGlassMenuSource()
    val glassMenuAvailable = appGlassMenuAvailable()
    if (glassMenuAvailable || LocalAppUiStyle.current != AppUiStyle.MIUIX || !LocalAppThemeConfig.current.nativeMiuixPopupsEnabled) {
        var expanded by remember { mutableStateOf(false) }
        var parentActions by remember { mutableStateOf(emptyList<AppWindowAction>()) }
        val visibleGroups = if (glassMenuAvailable) flattenGlassWindowActionGroups(groups)
            else parentActions.lastOrNull()?.let { listOf(it.children) } ?: groups.filter { it.isNotEmpty() }
        Box(modifier = modifier) {
            AppIconButton(
                modifier = anchorModifier.appGlassMenuSource(glassSource, enabled = enabled, captureSurface = !sourceCapturedByParent, onOpen = {
                    parentActions = emptyList()
                    expanded = true
                    onExpandedChange?.invoke(true)
                }),
                onClick = {
                    parentActions = emptyList()
                    expanded = true
                    onExpandedChange?.invoke(true)
                },
                enabled = enabled,
            ) { content() }
            AppDropdownMenu(
                glassSource = glassSource,
                expanded = expanded,
                onDismissRequest = {
                    parentActions = emptyList()
                    expanded = false
                    onExpandedChange?.invoke(false)
                },
            ) {
                if (parentActions.isNotEmpty()) {
                    AppDropdownMenuItem(
                        text = { AppText("返回") },
                        dismissOnClick = false,
                        onClick = { parentActions = parentActions.dropLast(1) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                visibleGroups.forEachIndexed { groupIndex, actions ->
                    if (groupIndex > 0) {
                        AppHorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    actions.forEach { action ->
                        AppDropdownMenuItem(
                            text = { AppText(action.label) },
                            leadingIcon = action.icon?.let { icon ->
                                {
                                    if (action.iconTint == null) {
                                        AppIcon(icon, contentDescription = null)
                                    } else {
                                        AppIcon(icon, contentDescription = null, tint = action.iconTint)
                                    }
                                }
                            },
                            trailingIcon = when {
                                action.children.isNotEmpty() -> { { AppText("›") } }
                                action.selected -> { { AppText("✓") } }
                                else -> null
                            },
                            enabled = action.enabled,
                            selected = action.selected,
                            dismissOnClick = action.children.isEmpty(),
                            onClick = {
                                if (action.children.isNotEmpty()) {
                                    parentActions = parentActions + action
                                } else {
                                    expanded = false
                                    onExpandedChange?.invoke(false)
                                    if (glassSource.isAvailable) onActionSource?.invoke(glassSource)
                                    action.onClick?.invoke()
                                }
                            },
                        )
                    }
                }
            }
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    fun dismiss() {
        if (expanded) {
            expanded = false
            onExpandedChange?.invoke(false)
        }
    }
    val selectNativeAction = appValueChangeWithFeedback<AppWindowAction>(
        scene = com.android.purebilibili.core.ui.AppHapticScene.MENUS,
        onValueChange = { action ->
            if (expanded) {
                dismiss()
                action.onClick?.invoke()
            }
        },
    )
    Box(modifier = modifier) {
        AppIconButton(
            modifier = anchorModifier,
            enabled = enabled && groups.any { it.isNotEmpty() },
            onClick = { expanded = true; onExpandedChange?.invoke(true) },
        ) { content() }
        WindowCascadingListPopup(
            show = expanded,
            entries = groups.filter { it.isNotEmpty() }.map { actions ->
                DropdownEntry(items = actions.map { action ->
                    action.toDropdownItem(selectNativeAction)
                })
            },
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = ::dismiss,
        )
    }
}

@Composable
private fun AppCompactWindowActionMenu(
    groups: List<List<AppWindowAction>>,
    modifier: Modifier,
    enabled: Boolean,
    onExpandedChange: ((Boolean) -> Unit)?,
    anchorModifier: Modifier,
    onActionSource: ((AppGlassMenuSource) -> Unit)?,
    source: AppGlassMenuSource?,
    sourceCapturedByParent: Boolean,
    content: @Composable () -> Unit,
) {
    val glassSource = source ?: rememberAppGlassMenuSource()
    val glassMenuAvailable = appGlassMenuAvailable()
    var expanded by remember { mutableStateOf(false) }
    var parentActions by remember { mutableStateOf(emptyList<AppWindowAction>()) }
    val visibleGroups = if (glassMenuAvailable) flattenGlassWindowActionGroups(groups)
        else parentActions.lastOrNull()?.let { listOf(it.children) } ?: groups.filter { it.isNotEmpty() }
    fun dismiss() {
        if (expanded) {
            expanded = false
            parentActions = emptyList()
            onExpandedChange?.invoke(false)
        }
    }
    val menuContent: @Composable (Boolean) -> Unit = { scrollable ->
        key(parentActions) {
            AppCompactMenuColumn(scrollable = scrollable) {
                if (parentActions.isNotEmpty()) {
                    AppCompactMenuItem(
                        text = "返回",
                        dismissOnClick = false,
                        onClick = { parentActions = parentActions.dropLast(1) },
                        leadingIcon = {
                            AppIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        },
                    )
                }
                visibleGroups.forEachIndexed { groupIndex, actions ->
                    if (groupIndex > 0) {
                        AppHorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    actions.forEach { action ->
                        AppCompactMenuItem(
                            text = action.label,
                            summary = action.summary,
                            selected = action.selected,
                            dismissOnClick = action.children.isEmpty(),
                            enabled = action.enabled,
                            leadingIcon = action.icon?.let { icon ->
                                {
                                    if (action.iconTint == null || !action.enabled) {
                                        AppIcon(icon, contentDescription = null)
                                    } else {
                                        AppIcon(icon, contentDescription = null, tint = action.iconTint)
                                    }
                                }
                            },
                            trailingIcon = if (action.children.isNotEmpty()) {
                                {
                                    AppIcon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                    )
                                }
                            } else null,
                            onClick = {
                                if (expanded && action.enabled) {
                                    if (action.children.isNotEmpty()) {
                                        parentActions = parentActions + action
                                    } else {
                                        dismiss()
                                        if (glassSource.isAvailable) onActionSource?.invoke(glassSource)
                                        action.onClick?.invoke()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    Box(modifier = modifier) {
        AppIconButton(
            modifier = anchorModifier.appGlassMenuSource(glassSource, enabled = enabled, captureSurface = !sourceCapturedByParent, onOpen = {
                    parentActions = emptyList()
                    expanded = true
                    onExpandedChange?.invoke(true)
                }),
            enabled = enabled && groups.any { it.isNotEmpty() },
            onClick = {
                parentActions = emptyList()
                expanded = true
                onExpandedChange?.invoke(true)
            },
        ) { content() }
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX &&
            LocalAppThemeConfig.current.nativeMiuixPopupsEnabled && !appGlassMenuAvailable()
        ) {
            WindowListPopup(
                show = expanded,
                alignment = PopupPositionProvider.Align.End,
                minWidth = 120.dp,
                onDismissRequest = ::dismiss,
            ) { menuContent(true) }
        } else {
            AppDropdownMenu(
                glassSource = glassSource,
                expanded = expanded,
                onDismissRequest = ::dismiss,
            ) { menuContent(false) }
        }
    }
}

private fun AppWindowAction.toDropdownItem(onSelected: (AppWindowAction) -> Unit): DropdownItem = DropdownItem(
    text = label,
    enabled = enabled,
    selected = selected,
    onClick = { onSelected(this) },
    icon = icon?.let { imageVector ->
        { modifier ->
            if (iconTint == null) {
                AppIcon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = modifier,
                )
            } else {
                AppIcon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = modifier,
                )
            }
        }
    },
    summary = summary,
    children = children.takeIf { it.isNotEmpty() }?.map { it.toDropdownItem(onSelected) },
)
