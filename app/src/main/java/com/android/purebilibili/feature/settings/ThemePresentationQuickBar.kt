package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.components.AppSegmentOption

internal enum class AppThemePresentationOption(
    val title: String,
    val subtitle: String,
) {
    MATERIAL3("Material 3", "谷歌原生"),
    MIUIX("Miuix", "澎湃原生"),
}

internal fun resolveActiveThemePresentation(
    uiStyle: AppUiStyle,
): AppThemePresentationOption = when (uiStyle) {
    AppUiStyle.MIUIX -> AppThemePresentationOption.MIUIX
    AppUiStyle.MATERIAL3 -> AppThemePresentationOption.MATERIAL3
}

internal fun resolveThemePresentationTarget(
    option: AppThemePresentationOption,
    currentLiquidGlassEnabled: Boolean,
): Pair<AppUiStyle, Boolean> = when (option) {
    AppThemePresentationOption.MATERIAL3 -> AppUiStyle.MATERIAL3 to currentLiquidGlassEnabled
    AppThemePresentationOption.MIUIX -> AppUiStyle.MIUIX to currentLiquidGlassEnabled
}

private const val THEME_PRESENTATION_TITLE = "界面风格"

/**
 * 外观页「界面风格」入口：
 * 与其他设置项共用单选菜单，选项前的小样区分 Material 3 与 Miuix；
 * 切换时上报入口行中心坐标以驱动窗口快照揭示转场。
 */
@Composable
internal fun ThemePresentationPreference(
    currentUiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean,
    onSelect: (targetStyle: AppUiStyle, targetGlass: Boolean, anchorInWindow: Offset) -> Unit,
) {
    val activeOption = resolveActiveThemePresentation(uiStyle = currentUiStyle)
    val options = remember {
        AppThemePresentationOption.entries.map { option ->
            AppSegmentOption(option, option.title, option.subtitle)
        }
    }
    var anchorInWindow by remember { mutableStateOf(Offset.Unspecified) }

    SettingsItemAnchor("appearance.ui_style") {
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                anchorInWindow = coordinates.boundsInWindow().center
            },
        ) {
            SettingsSingleChoicePreference(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.UI_STYLE),
                title = THEME_PRESENTATION_TITLE,
                subtitle = activeOption.subtitle,
                options = options,
                selectedValue = activeOption,
                optionLeading = { option ->
                    SettingsOptionPreviewThumb(
                        SettingsOptionPreview.ThemeStyle(
                            miuix = option == AppThemePresentationOption.MIUIX,
                        ),
                    )
                },
                onSelectionChange = { option ->
                    val (targetStyle, targetGlass) =
                        resolveThemePresentationTarget(option, liquidGlassEnabled)
                    onSelect(targetStyle, targetGlass, anchorInWindow)
                },
            )
        }
    }
}
