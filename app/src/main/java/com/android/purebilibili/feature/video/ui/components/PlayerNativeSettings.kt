package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.android.purebilibili.core.store.FullscreenActionCountSettings
import com.android.purebilibili.core.ui.components.AppSwitch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.LocalAppThemeConfig


/** 只在同窗玻璃设置容器实际接管后移除内容的原生实色底板。 */
internal val LocalPlayerGlassSettingsSurface = compositionLocalOf { false }

internal fun playerNativeMenuConfig(config: com.android.purebilibili.core.ui.AppThemeConfig) =
    config.copy(nativeMiuixPopupsEnabled = false, liquidGlassEnabled = config.popupGlassEnabled)
/** 播放器保留原生面板结构，内部菜单跟随菜单与弹窗场景；关闭玻璃不启用独立 Miuix 菜单。 */
@Composable
internal fun PlayerNativeMenuTheme(content: @Composable () -> Unit) {
    val config = LocalAppThemeConfig.current
    CompositionLocalProvider(
        LocalAppThemeConfig provides playerNativeMenuConfig(config),
        LocalAppUiStyle provides AppUiStyle.MATERIAL3,
        content = content,
    )
}

@Composable
internal fun PlayerNativeSettingsHost(
    onDismiss: () -> Unit,
    bottomPaddingDp: Int = 0,
    isFullscreen: Boolean = false,
    playerBounds: androidx.compose.ui.unit.IntRect? = null,
    content: @Composable () -> Unit,
) {
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val config = LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sidePanel = isFullscreen && windowSize.width > windowSize.height &&
        LocalPlayerPanelViewport.current != null
    val policy = resolveDanmakuSettingsPanelLayoutPolicy(
        isFullscreen = false,
        screenWidthDp = config.screenWidthDp,
        screenHeightDp = config.screenHeightDp,
    )
    val safeWindow = playerPanelSafeWindowBounds()
    val menuWidth = with(density) {
        if (sidePanel) {
            resolvePlayerPanelBounds(
                safeWindow, playerBounds, isFullscreen, landscape = true,
                maxSideWidth = 500.dp.roundToPx(),
            ).width.toDp()
        } else {
            // 原生外层有左右各 12dp 留白；两种材质共用可见面板宽度。
            (policy.maxWidthDp.dp - 24.dp).coerceAtMost(
                (safeWindow.width.toDp() - 32.dp).coerceAtLeast(1.dp),
            ).coerceAtLeast(1.dp)
        }
    }
    val glassMaxHeight = (config.screenHeightDp - 32).coerceAtLeast(1).dp
    // 玻璃只接管外壳与触发按钮开合，不再单独收窄主菜单及子页。
    if (com.android.purebilibili.core.ui.components.AppGlassMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            title = "更多操作",
            placement = if (com.android.purebilibili.core.ui.components.LocalAppGlassMenuSource.current?.isAvailable == true) {
                com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.ANCHOR_END
            } else if (isFullscreen) com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.END
            else com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.CENTER,
            minWidth = menuWidth,
            maxWidth = menuWidth,
            maxHeight = glassMaxHeight,
            interactiveContent = true,
            showScrollbar = false,
            useAvailableHeight = true,
        ) {
            PlayerNativeMenuTheme {
                CompositionLocalProvider(LocalPlayerGlassSettingsSurface provides true) {
                    Box(Modifier.width(menuWidth).heightIn(max = glassMaxHeight)) { content() }
                }
            }
        }) return
    // 播放器提供避让宿主时，横屏更多操作与选集共用侧面板；其他入口保留原有弹窗。
    if (sidePanel) {
        PlayerNativeMenuTheme {
            PlayerDetailPanel("更多操作", isFullscreen, playerBounds, onDismiss) { requestDismiss ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("更多操作", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    PanelAction(Icons.Outlined.Close, "关闭", requestDismiss)
                }
                content()
            }
        }
        return
    }
    PlayerNativeMenuTheme {
        DanmakuSettingsPanelHost(false, policy, onDismiss) {
            Surface(
                modifier = Modifier
                    .padding(12.dp)
                    .padding(bottom = bottomPaddingDp.dp)
                    .width(menuWidth)
                    .heightIn(max = policy.maxHeightDp.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) { content() }
        }
    }
}

@Composable
internal fun PlayerSettingsRow(title: String, subtitle: String? = null, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.appActionClickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title == "弹幕设置") {
            PiliPlusDanmakuSettingsIcon(size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
        Icon(
            imageVector = when (title) {
                "稍后再看（添加 / 移出）" -> Icons.Outlined.WatchLater
                "查看笔记" -> Icons.Outlined.EditNote
                "离线缓存" -> Icons.Outlined.FolderZip
                "保存字幕" -> Icons.Outlined.Download
                "保存封面" -> Icons.Outlined.Image
                "定时关闭" -> Icons.Outlined.HourglassTop
                "播放地址" -> Icons.Outlined.Link
                "重载视频" -> Icons.Outlined.Refresh
                "画质增强" -> Icons.Outlined.StayCurrentLandscape
                "播放器音量" -> Icons.Outlined.VolumeUp
                "画面变换" -> Icons.Outlined.Flip
                "恢复默认" -> Icons.Outlined.Restore
                "CDN 设置" -> Icons.Outlined.Cloud
                "选择画质" -> Icons.Outlined.PlayCircle
                "选择音质" -> Icons.Outlined.Album
                "首选编码格式" -> Icons.Outlined.AvTimer
                "播完后行为" -> Icons.Outlined.Repeat
                "弹幕列表" -> Icons.Outlined.Comment
                "字幕设置" -> Icons.Outlined.Subtitles
                "加载字幕" -> Icons.Outlined.FileOpen
                "播放信息" -> Icons.Outlined.Info
                "举报" -> Icons.Outlined.ErrorOutline
                FullscreenActionCountSettings.TITLE -> ImageVector.vectorResource(com.android.purebilibili.R.drawable.ic_settings_action_counts_24)
                "返回" -> Icons.Outlined.ArrowBack
                else -> Icons.Outlined.Settings
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) Text(
                subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PlayerSettingsPopupRow(
    title: String,
    current: String,
    options: List<String>,
    enabledOptions: List<Boolean> = List(options.size) { true },
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 玻璃设置面板内展开选项，不再为内部行创建第二层玻璃弹窗。
    if (LocalPlayerGlassSettingsSurface.current) {
        Column(Modifier.fillMaxWidth()) {
            PlayerSettingsRow(title, current) { expanded = !expanded }
            if (expanded) {
                options.forEachIndexed { index, label ->
                    PlayerSettingsSelectionRow(
                        title = label,
                        selected = label == current,
                        enabled = enabledOptions.getOrElse(index) { true },
                        allowSelectedClick = true,
                        onClick = { expanded = false; if (label != current) onSelected(index) },
                    )
                }
            }
        }
        return
    }
    com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { expanded = true }) {
        PlayerSettingsRow(title, current) { expanded = true }
        com.android.purebilibili.core.ui.components.AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, label ->
                com.android.purebilibili.core.ui.components.AppDropdownMenuItem(
                    text = { Text(label, fontSize = 14.sp) },
                    enabled = enabledOptions.getOrElse(index) { true },
                    onClick = {
                        expanded = false
                        if (label != current) onSelected(index)
                    },
                    trailingIcon = if (label == current) ({ Icon(Icons.Outlined.Done, null) }) else null,
                )
            }
        }
    }
}

@Composable
internal fun PlayerSettingsSelectionRow(
    title: String,
    selected: Boolean,
    enabled: Boolean = true,
    subtitle: String? = null,
    allowSelectedClick: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)) },
        supportingContent = if (subtitle != null) ({ Text(subtitle, fontSize = 12.sp) }) else null,
        trailingContent = { if (selected) Icon(Icons.Outlined.Done, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().appActionClickable(enabled = enabled) { if (!selected || allowSelectedClick) onClick() },
        // 玻璃容器接管时透出父容器；其他场景保留原生列表底色。
        colors = ListItemDefaults.colors(
            containerColor = if (LocalPlayerGlassSettingsSurface.current) Color.Transparent
            else MaterialTheme.colorScheme.surface,
        ),
    )
}

/** 播放器子菜单沿用普通菜单行的字号与留白，不使用全局设置的彩色图标底座。 */
@Composable
internal fun PlayerSettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
