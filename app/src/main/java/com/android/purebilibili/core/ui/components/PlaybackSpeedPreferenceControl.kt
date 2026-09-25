package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.parseNewPlaybackSpeedOption
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import kotlin.math.roundToInt

fun formatPlaybackSpeed(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    val value = hundredths / 100f
    return if (hundredths % 100 == 0) "${value.toInt()}x" else "${value}x"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackSpeedPreferenceControl(
    currentSpeed: Float,
    options: List<Float>,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = "默认播放速度",
    subtitle: String? = null,
    showCurrentValue: Boolean = true
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null || subtitle != null || showCurrentValue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (title != null) {
                        AppText(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (subtitle != null) {
                        AppText(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (showCurrentValue) {
                    AppSurface(
                        shape = AppShapes.container(ContainerLevel.Pill),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        AppText(
                            text = formatPlaybackSpeed(currentSpeed),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { speed ->
                val selected = currentSpeed == speed
                AppSurface(
                    onClick = { onSpeedChange(speed) },
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    AppText(
                        text = formatPlaybackSpeed(speed),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackSpeedOptionsPreferenceControl(
    options: List<Float>,
    defaultSpeed: Float,
    longPressSpeed: Float,
    onAddSpeed: (Float) -> Unit,
    onRemoveSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var newSpeedInput by rememberSaveable { mutableStateOf("") }
    val candidate = parseNewPlaybackSpeedOption(newSpeedInput, options)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppText(
            text = "播放器倍速列表",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        AppText(
            text = "播放器菜单、双指调速、默认与长按倍速共用此列表；删除已选倍速会自动切换到最接近的选项。1x 不可删除。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { speed ->
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val roleSuffix = when {
                            speed == defaultSpeed && speed == longPressSpeed -> " · 默认·长按"
                            speed == defaultSpeed -> " · 默认"
                            speed == longPressSpeed -> " · 长按"
                            else -> ""
                        }
                        AppText(
                            text = formatPlaybackSpeed(speed) + roleSuffix,
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppIconButton(
                            onClick = { onRemoveSpeed(speed) },
                            enabled = speed != 1f,
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "删除 ${formatPlaybackSpeed(speed)} 倍速"
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextField(
                value = newSpeedInput,
                onValueChange = { newSpeedInput = it },
                modifier = Modifier.weight(1f),
                label = "新增倍速（0.1–8x）",
                isError = newSpeedInput.isNotBlank() && candidate == null,
                supportingText = if (newSpeedInput.isNotBlank() && candidate == null) {
                    { AppText("请输入不重复的倍速，最多两位小数") }
                } else null
            )
            AppButton(
                onClick = {
                    candidate?.let { speed ->
                        onAddSpeed(speed)
                        newSpeedInput = ""
                    }
                },
                enabled = candidate != null,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                AppText("添加")
            }
        }
    }
}
