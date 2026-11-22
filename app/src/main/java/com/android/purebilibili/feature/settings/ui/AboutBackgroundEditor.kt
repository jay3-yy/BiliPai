package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.AboutAppearanceStore
import com.android.purebilibili.core.store.AboutBackgroundMode
import com.android.purebilibili.core.store.AboutBackgroundSettings
import com.android.purebilibili.core.theme.formatMd3CustomColorHex
import com.android.purebilibili.core.theme.parseMd3CustomColorHex
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppModalPresentation
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppColorPicker
import com.android.purebilibili.core.ui.components.AppNativeSegmentedControl
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import kotlinx.coroutines.launch

/** 持久设置在宿主收集；预览和编辑器只接收值与保存回调。 */
@Composable
internal fun MiuixAboutAppearanceHost(
    content: @Composable (AboutBackgroundSettings, Boolean, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val settings by remember(context) { AboutAppearanceStore.observe(context) }
        .collectAsStateWithLifecycle(initialValue = AboutBackgroundSettings())
    val scope = rememberCoroutineScope()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    content(settings, showEditor) { showEditor = true }
    if (showEditor) {
        AboutBackgroundEditor(
            initialSettings = settings,
            onDismiss = { showEditor = false },
            onSave = { draft ->
                scope.launch {
                    AboutAppearanceStore.save(context, draft)
                    showEditor = false
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutBackgroundEditor(
    initialSettings: AboutBackgroundSettings,
    onDismiss: () -> Unit,
    onSave: (AboutBackgroundSettings) -> Unit,
) {
    var modeName by rememberSaveable { mutableStateOf(initialSettings.mode.name) }
    var topColor by rememberSaveable { mutableIntStateOf(initialSettings.topColor) }
    var bottomColor by rememberSaveable { mutableIntStateOf(initialSettings.bottomColor) }
    var glowColor by rememberSaveable { mutableIntStateOf(initialSettings.glowColor) }
    var selectedPoint by rememberSaveable { mutableIntStateOf(0) }
    val draft = AboutBackgroundSettings(AboutBackgroundMode.valueOf(modeName), topColor, bottomColor, glowColor)
    val selectedColor = listOf(topColor, bottomColor, glowColor)[selectedPoint]
    var hexInput by rememberSaveable { mutableStateOf(formatMd3CustomColorHex(Color(selectedColor))) }
    val validHex = isValidMd3CustomColorHex(hexInput)

    fun setColor(color: Color) {
        when (selectedPoint) {
            0 -> topColor = color.toArgb()
            1 -> bottomColor = color.toArgb()
            2 -> glowColor = color.toArgb()
        }
    }

    val maxEditorHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        presentationOverride = AppModalPresentation.BottomSheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxEditorHeight)
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText("关于页背景", style = MaterialTheme.typography.titleLarge)
            AppText(
                "预览后保存，取消不会更改当前背景。",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .then(miuixAboutBackground(draft, 120.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixAboutWordmark(compact = true)
                }
                SettingsSingleChoicePreference(
                    title = "背景模式",
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.APPEARANCE),
                    options = AboutBackgroundMode.entries.map { AppSegmentOption(it.name, it.label) },
                    selectedValue = modeName,
                    onSelectionChange = { modeName = it },
                )
                if (draft.mode == AboutBackgroundMode.CUSTOM) {
                    AppText("编辑色点", style = MaterialTheme.typography.titleSmall)
                    AppNativeSegmentedControl(
                        options = listOf(
                            AppSegmentOption(0, "顶部"),
                            AppSegmentOption(1, "底部"),
                            AppSegmentOption(2, "柔光"),
                        ),
                        selectedValue = selectedPoint,
                        modifier = Modifier.fillMaxWidth(),
                        onSelectionChange = {
                            selectedPoint = it
                            hexInput = formatMd3CustomColorHex(Color(listOf(topColor, bottomColor, glowColor)[it]))
                        },
                    )
                    AppColorPicker(
                        color = Color(selectedColor),
                        onColorChanged = {
                            setColor(it)
                            hexInput = formatMd3CustomColorHex(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = hexInput,
                        onValueChange = {
                            hexInput = it
                            if (isValidMd3CustomColorHex(it)) setColor(parseMd3CustomColorHex(it))
                        },
                        label = "颜色 HEX（#RRGGBB）",
                        isError = !validHex,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!validHex) {
                        AppText(
                            "请输入 6 位十六进制颜色，例如 #AABBCC。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    AppText(
                        "颜色会融入当前页面底色，浅色和深色模式分别适配。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
                AppTextButton(onClick = {
                    val defaults = AboutBackgroundSettings()
                    modeName = defaults.mode.name
                    topColor = defaults.topColor
                    bottomColor = defaults.bottomColor
                    glowColor = defaults.glowColor
                    hexInput = formatMd3CustomColorHex(Color(listOf(topColor, bottomColor, glowColor)[selectedPoint]))
                }) {
                    AppText("恢复默认", tapToCopyEnabled = false)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    AppText("取消", tapToCopyEnabled = false)
                }
                AppButton(
                    onClick = { onSave(draft) },
                    enabled = draft.mode != AboutBackgroundMode.CUSTOM || validHex,
                    modifier = Modifier.weight(1f),
                ) { AppText("保存", tapToCopyEnabled = false) }
            }
        }
    }
}
