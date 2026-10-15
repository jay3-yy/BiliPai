package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.WallpaperPaletteOverrides
import com.android.purebilibili.core.store.WallpaperPaletteRole
import com.android.purebilibili.core.theme.formatMd3CustomColorHex
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlinx.coroutines.launch

private val WallpaperPaletteRole.label: String
    get() = when (this) {
        WallpaperPaletteRole.PRIMARY -> "主色"
        WallpaperPaletteRole.SECONDARY -> "副色"
        WallpaperPaletteRole.TERTIARY -> "第三色"
        WallpaperPaletteRole.CONTAINER -> "容器色"
    }

private data class WallpaperPaletteEdit(
    val role: WallpaperPaletteRole,
    val darkTheme: Boolean,
    val initialHex: String,
)

@Composable
internal fun WallpaperPaletteEditor(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val darkTheme = scheme.background.luminance() < 0.5f
    val modeLabel = if (darkTheme) "深色" else "浅色"
    val overridesFlow = remember(context) { SettingsManager.getWallpaperPaletteOverrides(context) }
    val overrides by overridesFlow
        .collectAsStateWithLifecycle(initialValue = WallpaperPaletteOverrides())
    val custom = overrides.forMode(darkTheme)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<WallpaperPaletteEdit?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText("壁纸重新调色 · $modeLabel", style = MaterialTheme.typography.labelMedium,
            color = AppContentColors.secondaryText())
        AppText("点按色块修改；未修改的颜色继续跟随壁纸。浅色与深色分别保存。",
            style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WallpaperPaletteRole.entries.forEach { role ->
                val color = when (role) {
                    WallpaperPaletteRole.PRIMARY -> scheme.primary
                    WallpaperPaletteRole.SECONDARY -> scheme.secondary
                    WallpaperPaletteRole.TERTIARY -> scheme.tertiary
                    WallpaperPaletteRole.CONTAINER -> scheme.primaryContainer
                }
                val hex = custom[role] ?: formatMd3CustomColorHex(color)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(AppShapes.container(ContainerLevel.Field))
                        .clickable(role = Role.Button, onClickLabel = "编辑${role.label}") {
                            editing = WallpaperPaletteEdit(role, darkTheme, hex)
                        }
                        .heightIn(min = 96.dp)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(AppShapes.container(ContainerLevel.Field)).background(color))
                    AppText(role.label, style = MaterialTheme.typography.labelSmall)
                    AppText(if (role in custom) "自定义" else "跟随壁纸",
                        style = MaterialTheme.typography.labelSmall, color = AppContentColors.secondaryText())
                }
            }
        }
        if (custom.isNotEmpty()) {
            AppTextButton(onClick = {
                scope.launch { SettingsManager.resetWallpaperPaletteColors(context, darkTheme) }
            }) { AppText("恢复${modeLabel}模式壁纸配色") }
        }
    }

    editing?.let { target ->
        ThemeCustomColorPickerDialog(
            initialHex = target.initialHex,
            exactRoleTitle = "${if (target.darkTheme) "深色" else "浅色"} · ${target.role.label}",
            onDismiss = { editing = null },
            onConfirm = { hex ->
                scope.launch { SettingsManager.setWallpaperPaletteColor(context, target.darkTheme, target.role, hex) }
                editing = null
            },
            onReset = if (target.role in overrides.forMode(target.darkTheme)) {
                {
                    scope.launch { SettingsManager.setWallpaperPaletteColor(context, target.darkTheme, target.role, null) }
                    editing = null
                }
            } else null,
        )
    }
}
