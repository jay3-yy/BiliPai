package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.AppTextButton as TextButton
import com.android.purebilibili.core.ui.components.AppSwitch as Switch
import com.android.purebilibili.core.ui.components.AppFilterChip as FilterChip
import com.android.purebilibili.core.ui.components.AppText as Text
import com.android.purebilibili.core.ui.components.AppIcon as Icon
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppNumberPicker
import com.android.purebilibili.core.ui.AppAlertDialog as AlertDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.audio.player.isCurrentlyLateNightForSleepSuggestion
import kotlinx.coroutines.launch

@Composable
internal fun PlayerSleepTimerSettings(
    minutes: Int?,
    onMinutesChange: (Int?) -> Unit,
    waitForCurrentVideoEnd: Boolean,
    onWaitForCurrentVideoEndChange: ((Boolean) -> Unit)?,
    exitApp: Boolean,
    onExitAppChange: ((Boolean) -> Unit)?,
    onPresetSelected: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fadeOutEnabled by remember(context) { SettingsManager.getSleepTimerFadeOutEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getSleepTimerFadeOutEnabledSync(context))
    val fadeDurationSec by remember(context) { SettingsManager.getSleepTimerFadeDurationSec(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getSleepTimerFadeDurationSecSync(context))
    val isLateNight = remember { isCurrentlyLateNightForSleepSuggestion() }
    var custom by remember { mutableStateOf(false) }
    val presets = remember(minutes) { resolvePlayerSleepTimerPresets(minutes) }
    Text("定时关闭", Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.titleSmall)
    if (isLateNight && onWaitForCurrentVideoEndChange != null && !waitForCurrentVideoEnd) {
        LateNightSleepSuggestionCard(
            onEnableAfterCurrent = {
                onWaitForCurrentVideoEndChange(true)
                onPresetSelected()
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    presets.forEach { value ->
        AppListItem(
            headlineContent = { Text(if (value == 0) "禁用" else "$value 分钟", style = MaterialTheme.typography.bodyMedium) },
            trailingContent = { if ((minutes ?: 0) == value) Icon(Icons.Outlined.Done, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.fillMaxWidth().clickable {
                onMinutesChange(value.takeIf { it > 0 }); onPresetSelected()
            },
        )
    }
    PlayerSettingsRow("自定义") { custom = true }
    onWaitForCurrentVideoEndChange?.let { change ->
        AppListItem(
            headlineContent = { Text("播放完当前视频", style = MaterialTheme.typography.bodyMedium) },
            supportingContent = { Text("开启后无需倒计时；播完当前视频再执行下方操作", style = MaterialTheme.typography.bodySmall) },
            trailingContent = { Switch(waitForCurrentVideoEnd, change) },
        )
    }
    SleepTimerFadePreferenceRow(
        fadeEnabled = fadeOutEnabled,
        onFadeEnabledChange = { enabled ->
            scope.launch { SettingsManager.setSleepTimerFadeOutEnabled(context, enabled) }
        },
        fadeDurationSec = fadeDurationSec,
        onFadeDurationSecChange = { seconds ->
            scope.launch { SettingsManager.setSleepTimerFadeDurationSec(context, seconds) }
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    onExitAppChange?.let { change ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(!exitApp, { change(false) }, label = { Text("暂停视频") })
            FilterChip(exitApp, { change(true) }, label = { Text("退出APP") })
        }
    }
    if (custom) {
        var hours by remember { mutableIntStateOf(((minutes ?: 0) / 60).coerceIn(0, 24)) }
        var remainder by remember { mutableIntStateOf(((minutes ?: 0) % 60).coerceIn(0, 59)) }
        AlertDialog(
            onDismissRequest = { custom = false },
            modifier = Modifier.width(320.dp),
            title = { Text("自定义定时") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppNumberPicker(
                        value = hours,
                        onValueChange = { hours = it },
                        modifier = Modifier.weight(1f),
                        range = 0..24,
                        label = "小时",
                    )
                    AppNumberPicker(
                        value = remainder,
                        onValueChange = { remainder = it },
                        modifier = Modifier.weight(1f),
                        range = 0..59,
                        label = "分钟",
                    )
                }
            },
            dismissButton = { TextButton({ custom = false }) { Text("取消") } },
            confirmButton = {
                TextButton({
                    custom = false
                    onMinutesChange((hours * 60 + remainder).takeIf { it > 0 })
                }) { Text("确认") }
            },
        )
    }
}

