package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.AppTextButton as TextButton
import com.android.purebilibili.core.ui.components.AppIconButton as IconButton
import com.android.purebilibili.core.ui.components.AppSlider as Slider
import com.android.purebilibili.core.ui.components.AppOutlinedTextField as OutlinedTextField
import com.android.purebilibili.core.ui.components.AppText as Text
import com.android.purebilibili.core.ui.components.AppIcon as Icon
import com.android.purebilibili.core.ui.components.AppHorizontalDivider as HorizontalDivider
import com.android.purebilibili.core.ui.components.AppColorPicker
import com.android.purebilibili.core.ui.AppAlertDialog as AlertDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import com.android.purebilibili.feature.video.subtitle.importSubtitleFont
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

const val SUBTITLE_APPEARANCE_TITLE = "字幕样式"

enum class SubtitlePlacement(val storedValue: String, val label: String) {
    BOTTOM("bottom", "底部"),
    TOP("top", "顶部"),
    ;

    companion object {
        fun fromStored(value: String?): SubtitlePlacement =
            entries.firstOrNull { it.storedValue == value } ?: BOTTOM
    }
}

data class PlayerSubtitleAppearance(
    val inlineFontScale: Float = 1f,
    val fullscreenFontScale: Float = 1.5f,
    val fontWeight: Int = 6,
    val strokeWidth: Float = 2f,
    val horizontalPadding: Int = 24,
    val bottomPadding: Int = 24,
    val backgroundOpacity: Float = 0.67f,
    val textColor: Int = android.graphics.Color.WHITE,
    val strokeColor: Int = android.graphics.Color.BLACK,
    val fontFileName: String = "",
    val fontDisplayName: String = "",
    val placement: SubtitlePlacement = SubtitlePlacement.BOTTOM,
)

/** Changes are controlled by the caller and must update both the renderer and its persisted store. */
@Composable
fun PlayerSubtitleSettingsPanel(
    appearance: PlayerSubtitleAppearance,
    onAppearanceChange: (PlayerSubtitleAppearance) -> Unit,
    onDismiss: () -> Unit,
    isFullscreen: Boolean = false,
    onTimingSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestAppearance by rememberUpdatedState(appearance)
    val latestOnChange by rememberUpdatedState(onAppearanceChange)
    var fontImporting by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf<String?>(null) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            fontImporting = true
            try {
                val imported = importSubtitleFont(context, uri)
                latestOnChange(latestAppearance.copy(fontFileName = imported.first, fontDisplayName = imported.second))
                fontError = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fontError = error.message ?: "无法导入字体"
            } finally {
                fontImporting = false
            }
        }
    }
    PlayerNativeSettingsHost(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(45.dp), contentAlignment = Alignment.Center) {
                Text(SUBTITLE_APPEARANCE_TITLE, style = MaterialTheme.typography.titleSmall)
            }
            com.android.purebilibili.feature.video.subtitle.PiliStyledSubtitleText(
                text = "字幕样式预览 Aa 123", baseFontSizeSp = 18,
                appearance = appearance, isFullscreen = isFullscreen,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            PlayerSettingsRow(
                if (fontImporting) "正在导入字体…" else "字幕字体：${appearance.fontDisplayName.ifBlank { "默认字体" }}",
                subtitle = "支持 TTF、OTF、TTC，最大 32 MB，仅用于字幕",
                onClick = { if (!fontImporting) fontPicker.launch(arrayOf("*/*")) },
            )
            if (appearance.fontFileName.isNotBlank()) {
                TextButton(onClick = { onAppearanceChange(appearance.copy(fontFileName = "", fontDisplayName = "")) }) {
                    Text("恢复默认字体")
                }
            }
            fontError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            SubtitleColorPreference("文字颜色", appearance.textColor) { onAppearanceChange(appearance.copy(textColor = it)) }
            SubtitleColorPreference("描边颜色", appearance.strokeColor) { onAppearanceChange(appearance.copy(strokeColor = it)) }
            SubtitleAppearanceSlider("字体大小", appearance.inlineFontScale, 0.5f..2.5f, 1f, { "${(it * 100).roundToInt()}%" }) {
                onAppearanceChange(appearance.copy(inlineFontScale = it))
            }
            SubtitleAppearanceSlider("全屏字体大小", appearance.fullscreenFontScale, 0.5f..2.5f, 1.5f, { "${(it * 100).roundToInt()}%" }) {
                onAppearanceChange(appearance.copy(fullscreenFontScale = it))
            }
            SubtitleAppearanceSlider("字体粗细", appearance.fontWeight.toFloat(), 1f..9f, 6f, { "${it.roundToInt()}" }, steps = 7) {
                onAppearanceChange(appearance.copy(fontWeight = it.roundToInt()))
            }
            SubtitleAppearanceSlider(
                "描边粗细",
                appearance.strokeWidth,
                0f..5f,
                2f,
                { String.format(Locale.US, "%.1f", it) },
                steps = 9,
            ) {
                onAppearanceChange(appearance.copy(strokeWidth = it))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("字幕位置", Modifier.weight(1f), fontSize = 14.sp)
                SubtitlePlacement.entries.forEach { option ->
                    com.android.purebilibili.core.ui.components.AppFilterChip(
                        selected = appearance.placement == option,
                        onClick = { onAppearanceChange(appearance.copy(placement = option)) },
                        label = { Text(option.label) },
                    )
                }
            }
            SubtitleAppearanceSlider("左右边距", appearance.horizontalPadding.toFloat(), 0f..100f, 24f, { "${it.roundToInt()}" }, steps = 99) {
                onAppearanceChange(appearance.copy(horizontalPadding = it.roundToInt()))
            }
            SubtitleAppearanceSlider(
                if (appearance.placement == SubtitlePlacement.TOP) "顶部边距" else "底部边距",
                appearance.bottomPadding.toFloat(),
                0f..200f,
                24f,
                { "${it.roundToInt()}" },
                steps = 199,
            ) {
                onAppearanceChange(appearance.copy(bottomPadding = it.roundToInt()))
            }
            SubtitleAppearanceSlider("背景不透明度", appearance.backgroundOpacity, 0f..1f, 0.67f, { "${(it * 100).roundToInt()}%" }, steps = 99) {
                onAppearanceChange(appearance.copy(backgroundOpacity = it))
            }
            onTimingSettings?.let { action ->
                HorizontalDivider()
                PlayerSettingsRow("字幕时间偏移", onClick = action)
            }
        }
    }
}

@Composable
private fun SubtitleAppearanceSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    format: (Float) -> String,
    steps: Int = 199,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$label  ${format(value)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onChange(default) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Refresh, "恢复默认值 ${format(default)}", Modifier.size(18.dp))
            }
        }
        Slider(value.coerceIn(range), onChange, valueRange = range, steps = steps, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun SubtitleColorPreference(label: String, value: Int, onChange: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var hex by rememberSaveable(value) { mutableStateOf("#%06X".format(value and 0xFFFFFF)) }
    val parsed = hex.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }
        ?.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())
    val activeColor = parsed?.let { Color(it) } ?: Color(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { editing = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            "#%06X".format(value and 0xFFFFFF),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(value))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text(label) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC121212))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("字幕样式预览 Aa 123", color = activeColor, style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        "白" to 0xFFFFFFFF.toInt(),
                        "黑" to 0xFF000000.toInt(),
                        "黄" to 0xFFFFEB3B.toInt(),
                        "青" to 0xFF80DEEA.toInt(),
                        "粉" to 0xFFFB7299.toInt(),
                    ).forEach { (name, preset) ->
                        val isSelected = (parsed ?: value) == preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { hex = "#%06X".format(preset and 0xFFFFFF) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape,
                                    ),
                            )
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                AppColorPicker(
                    color = activeColor,
                    onColorChanged = { picked ->
                        hex = "#%06X".format(picked.toArgb() and 0xFFFFFF)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                com.android.purebilibili.core.ui.ProvideAppHapticScene(com.android.purebilibili.core.ui.AppHapticScene.TEXT) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it },
                        label = { Text("十六进制颜色 #RRGGBB") },
                        singleLine = true,
                        isError = parsed == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let(onChange); editing = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
    )
}
