package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.ThemeColors
import com.android.purebilibili.core.theme.ThemeColorNames
import com.android.purebilibili.core.theme.formatMd3CustomColorHex
import com.android.purebilibili.core.theme.parseMd3CustomColorHex
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppModalPresentation
import com.android.purebilibili.core.ui.LocalAppLineIconColor
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlinx.coroutines.launch

private const val LINE_ICON_COLOR_TITLE = "线性图标颜色"

@Composable
internal fun LineIconColorPreference() {
    val context = LocalContext.current
    val themeColor = LocalAppLineIconColor.current
    val storedColor by remember(context) { SettingsManager.getAppLineIconColor(context) }
        .collectAsStateWithLifecycle(initialValue = themeColor.takeUnless { it == Color.Unspecified }?.toArgb())
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AppPreference(
        icon = rememberSettingsSemanticIcon(SettingsIconRole.LINE_ICON_COLOR),
        title = LINE_ICON_COLOR_TITLE,
        value = storedColor?.let { value ->
            ThemeColors.indexOfFirst { it.toArgb() == value }.takeIf { it >= 0 }
                ?.let { ThemeColorNames[it] } ?: formatMd3CustomColorHex(Color(value))
        } ?: "跟随主题",
        subtitle = "设置单色线性图标的颜色",
        onClick = { showEditor = true },
    )
    if (showEditor) {
        LineIconColorEditor(
            initialColor = storedColor,
            onDismiss = { showEditor = false },
            onSave = { color ->
                scope.launch {
                    SettingsManager.setAppLineIconColor(context, color)
                    showEditor = false
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineIconColorEditor(
    initialColor: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selection by rememberSaveable {
        mutableStateOf(if (initialColor == null) "跟随主题" else "自定义")
    }
    val initialHsv = remember(initialColor) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(initialColor ?: ThemeColors.first().toArgb(), it)
        }
    }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    var hex by rememberSaveable {
        mutableStateOf(formatMd3CustomColorHex(Color(initialColor ?: ThemeColors.first().toArgb())))
    }
    val color = Color.hsv(hue, saturation, brightness)
    val valid = selection != "自定义" || isValidMd3CustomColorHex(hex)
    val previewTint = if (selection == "跟随主题") MaterialTheme.colorScheme.onSurfaceVariant else color
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        presentationOverride = AppModalPresentation.BottomSheet,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(LINE_ICON_COLOR_TITLE, style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    listOf(SettingsIconRole.ICON_STYLE, SettingsIconRole.PLAYBACK, SettingsIconRole.APPEARANCE).forEach { role ->
                        AppIcon(
                            imageVector = rememberSettingsSemanticIcon(role),
                            contentDescription = null,
                            tint = previewTint,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                SettingsSingleChoicePreference(
                    title = "颜色选择",
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.LINE_ICON_COLOR),
                    options = listOf("跟随主题", "自定义").map { AppSegmentOption(it, it) },
                    selectedValue = selection,
                    onSelectionChange = { value ->
                        selection = value
                        hex = formatMd3CustomColorHex(color)
                    },
                )
                if (selection == "自定义") {
                    ThemeColorSelectionControls(
                        hue = hue,
                        saturation = saturation,
                        brightness = brightness,
                        pendingHex = hex,
                        onHsvChange = { h, s, v ->
                            hue = h
                            saturation = s
                            brightness = v
                            hex = formatMd3CustomColorHex(Color.hsv(h, s, v))
                        },
                        onHexChange = { input ->
                            hex = input
                            if (isValidMd3CustomColorHex(input)) {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parseMd3CustomColorHex(input).toArgb(), hsv)
                                // 灰色不覆盖色相，黑色不覆盖饱和度，保留后续拖动选色的位置。
                                if (hsv[1] > 0f) hue = hsv[0]
                                if (hsv[2] > 0f) saturation = hsv[1]
                                brightness = hsv[2]
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AppText("预览后保存；仅应用于单色线性功能图标，其他图标样式保留原有配色。",
                    style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppTextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    AppText("取消", tapToCopyEnabled = false)
                }
                AppButton(
                    onClick = { onSave(if (selection == "跟随主题") null else color.toArgb()) },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                ) { AppText("保存", tapToCopyEnabled = false) }
            }
        }
    }
}
