package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.normalizeLongPressSpeed
import com.android.purebilibili.core.store.parseNewPlaybackSpeedOption
import com.android.purebilibili.core.store.parseLongPressSpeedInput
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.ContainerLevel
import kotlin.math.roundToInt

fun formatPlaybackSpeed(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    val value = hundredths / 100f
    return if (hundredths % 100 == 0) "${value.toInt()}x" else "${value}x"
}

private val LONG_PRESS_SPEED_PRESETS = listOf(1.5f, 2f, 3f, 4f, 6f, 8f)

// Shared interaction change for every material and player/settings caller: edit the value
// directly; presets live in the editor instead of duplicating a slider on the page.
@Composable
fun LongPressSpeedPreferenceControl(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = "长按临时加速",
    subtitle: String? = null,
    titleTrailingContent: (@Composable () -> Unit)? = null,
) {
    val displayedSpeed = normalizeLongPressSpeed(currentSpeed)
    var showSpeedEditor by rememberSaveable { mutableStateOf(false) }
    var speedInput by rememberSaveable { mutableStateOf("") }
    val useGlassAnchorMenu = appGlassMenuAvailable()
    var glassMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val openCustomEditor = {
        speedInput = formatPlaybackSpeed(displayedSpeed).removeSuffix("x")
        showSpeedEditor = true
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (useGlassAnchorMenu) {
                        Modifier.clickable { glassMenuExpanded = true }
                    } else Modifier,
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    if (titleTrailingContent == null) {
                        AppText(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(text = title, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            titleTrailingContent()
                        }
                    }
                }
                if (subtitle != null) {
                    AppText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (useGlassAnchorMenu) {
                val valueTextStyle = if (
                    com.android.purebilibili.core.theme.LocalAppUiStyle.current == com.android.purebilibili.core.theme.AppUiStyle.MIUIX ||
                    com.android.purebilibili.core.ui.rememberResolvedAppListItemStyle() == com.android.purebilibili.core.ui.AppListItemStyle.NATIVE
                ) {
                    MaterialTheme.typography.bodySmall
                } else MaterialTheme.typography.bodyMedium
                val valueTint = com.android.purebilibili.core.ui.AppContentColors.secondaryText()
                AppGlassMenuAnchor(
                    onOpen = { glassMenuExpanded = true },
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .semantics {
                            contentDescription = "选择长按倍速，当前 ${formatPlaybackSpeed(displayedSpeed)}"
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            text = formatPlaybackSpeed(displayedSpeed),
                            style = valueTextStyle,
                            color = valueTint,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = null,
                            tint = valueTint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    AppGlassMenu(
                        expanded = glassMenuExpanded,
                        onDismissRequest = { glassMenuExpanded = false },
                        title = title ?: "长按临时加速",
                        useAvailableHeight = true,
                    ) {
                        Column {
                            val isCustom = displayedSpeed !in LONG_PRESS_SPEED_PRESETS
                            LONG_PRESS_SPEED_PRESETS.forEach { preset ->
                                val selected = displayedSpeed == preset
                                AppDropdownMenuItem(
                                    text = { AppText(formatPlaybackSpeed(preset)) },
                                    trailingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    } else null,
                                    selected = selected,
                                    onClick = {
                                        onSpeedChange(preset)
                                        glassMenuExpanded = false
                                    },
                                )
                            }
                            AppDropdownMenuItem(
                                text = {
                                    AppText(
                                        if (isCustom) "自定义（${formatPlaybackSpeed(displayedSpeed)}）" else "自定义…",
                                    )
                                },
                                trailingIcon = if (isCustom) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else null,
                                selected = isCustom,
                                onClick = {
                                    glassMenuExpanded = false
                                    openCustomEditor()
                                },
                            )
                        }
                    }
                }
            } else {
                AppTextButton(
                    onClick = openCustomEditor,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "输入长按倍速，当前 ${formatPlaybackSpeed(displayedSpeed)}"
                        },
                ) {
                    AppText(
                        text = formatPlaybackSpeed(displayedSpeed),
                        color = com.android.purebilibili.core.ui.AppContentColors.link(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
    if (showSpeedEditor) {
        val candidate = parseLongPressSpeedInput(speedInput)
        AppAlertDialog(
            onDismissRequest = { showSpeedEditor = false },
            title = { AppText("输入长按倍速") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(
                        value = speedInput,
                        onValueChange = { speedInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "倍速",
                        placeholder = formatPlaybackSpeed(displayedSpeed),
                        isError = speedInput.isNotBlank() && candidate == null,
                        supportingText = { AppText("范围 1.0x–8.0x，最多两位小数") }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LONG_PRESS_SPEED_PRESETS.forEach { preset ->
                            val selected = displayedSpeed == preset
                            AppSurface(
                                onClick = {
                                    onSpeedChange(preset)
                                    showSpeedEditor = false
                                },
                                shape = AppShapes.container(ContainerLevel.Card),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Box(
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppText(
                                        text = formatPlaybackSpeed(preset),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        candidate?.let { speed ->
                            onSpeedChange(speed)
                            showSpeedEditor = false
                        }
                    },
                    enabled = candidate != null
                ) {
                    AppText("确定")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showSpeedEditor = false }) {
                    AppText("取消")
                }
            }
        )
    }
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
            text = "播放器菜单、双指调速与默认速度共用此列表；长按倍速独立设置。删除已选倍速会自动切换到最接近的选项。1x 不可删除。",
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
                        val roleSuffix = if (speed == defaultSpeed) " · 默认" else ""
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
