package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.AboutCardShapeSettings
import com.android.purebilibili.core.store.AboutCardShapeStore
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppModalPresentation
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import kotlinx.coroutines.launch

internal val LocalAboutCardShape = staticCompositionLocalOf { AboutCardShapeSettings() }

internal fun AboutCardShapeSettings.toShape() = RoundedCornerShape(
    topStart = topStart.dp,
    topEnd = topEnd.dp,
    bottomStart = bottomStart.dp,
    bottomEnd = bottomEnd.dp,
)

/** MD3 原生与玻璃共用关于页卡片形状，不影响 Miuix 渐变及其他页面。 */
@Composable
internal fun MaterialAboutAppearanceHost(content: @Composable (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val settings by remember(context) { AboutCardShapeStore.observe(context) }
        .collectAsStateWithLifecycle(initialValue = AboutCardShapeSettings())
    val scope = rememberCoroutineScope()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalAboutCardShape provides settings) {
        content { showEditor = true }
    }
    if (showEditor) {
        AboutCardShapeEditor(
            initialSettings = settings,
            onDismiss = { showEditor = false },
            onSave = { draft ->
                scope.launch {
                    AboutCardShapeStore.save(context, draft)
                    showEditor = false
                }
            },
        )
    }
}

private val aboutShapePresets = linkedMapOf(
    "默认不对称" to AboutCardShapeSettings(),
    "圆角" to AboutCardShapeSettings(28, 28, 28, 28),
    "叶片" to AboutCardShapeSettings(72, 8, 8, 72),
    "直角" to AboutCardShapeSettings(0, 0, 0, 0),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutCardShapeEditor(
    initialSettings: AboutCardShapeSettings,
    onDismiss: () -> Unit,
    onSave: (AboutCardShapeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputs by rememberSaveable {
        mutableStateOf(listOf(initialSettings.topStart, initialSettings.topEnd,
            initialSettings.bottomStart, initialSettings.bottomEnd).map { it.toString() })
    }
    val values = inputs.map { it.toIntOrNull()?.takeIf { value -> value in 0..96 } }
    val valid = values.all { it != null }
    val draft = if (valid) AboutCardShapeSettings(values[0]!!, values[1]!!, values[2]!!, values[3]!!) else null
    val selectedPreset = aboutShapePresets.entries.firstOrNull { it.value == draft }?.key ?: "自定义"
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
            AppText("关于页卡片形状", style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (draft != null) {
                    Surface(
                        shape = draft.toShape(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppText("BiliPai", style = MaterialTheme.typography.displaySmall)
                        }
                    }
                }
                SettingsSingleChoicePreference(
                    title = "形状预设",
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.ABOUT_CARD_SHAPE),
                    options = (aboutShapePresets.keys + "自定义").map { AppSegmentOption(it, it) },
                    selectedValue = selectedPreset,
                    onSelectionChange = { name ->
                        aboutShapePresets[name]?.let {
                            inputs = listOf(it.topStart, it.topEnd, it.bottomStart, it.bottomEnd).map(Int::toString)
                        }
                    },
                )
                AppText("四角圆角大小（0–96 dp），保存后生效；取消保留原形状。",
                    style = MaterialTheme.typography.bodySmall)
                listOf("起始侧顶部", "结束侧顶部", "起始侧底部", "结束侧底部").forEachIndexed { index, label ->
                    AppTextField(
                        value = inputs[index],
                        onValueChange = { value -> inputs = inputs.toMutableList().also { it[index] = value } },
                        label = "$label（dp）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = values[index] == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!valid) {
                    AppText("每个角请输入 0–96 的整数。", color = MaterialTheme.colorScheme.error)
                }
                AppTextButton(onClick = {
                    val defaults = AboutCardShapeSettings()
                    inputs = listOf(defaults.topStart, defaults.topEnd, defaults.bottomStart, defaults.bottomEnd).map(Int::toString)
                }) { AppText("恢复默认", tapToCopyEnabled = false) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppTextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    AppText("取消", tapToCopyEnabled = false)
                }
                AppButton(onClick = { draft?.let(onSave) }, enabled = valid, modifier = Modifier.weight(1f)) {
                    AppText("保存", tapToCopyEnabled = false)
                }
            }
        }
    }
}
