package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.AppTextButton as TextButton
import com.android.purebilibili.core.ui.components.AppSwitch as Switch
import com.android.purebilibili.core.ui.components.AppFilterChip as FilterChip
import com.android.purebilibili.core.ui.components.AppText as Text
import com.android.purebilibili.core.ui.components.AppIcon as Icon
import com.android.purebilibili.core.ui.AppAlertDialog as AlertDialog

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

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
    var custom by remember { mutableStateOf(false) }
    val presets = remember(minutes) { resolvePlayerSleepTimerPresets(minutes) }
    Text("定时关闭", Modifier.fillMaxWidth().padding(16.dp), fontSize = 14.sp)
    presets.forEach { value ->
        ListItem(
            headlineContent = { Text(if (value == 0) "禁用" else "$value 分钟", fontSize = 14.sp) },
            trailingContent = { if ((minutes ?: 0) == value) Icon(Icons.Outlined.Done, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.fillMaxWidth().clickable {
                onMinutesChange(value.takeIf { it > 0 }); onPresetSelected()
            },
        )
    }
    PlayerSettingsRow("自定义") { custom = true }
    onWaitForCurrentVideoEndChange?.let { change ->
        ListItem(
            headlineContent = { Text("播放完当前视频", fontSize = 14.sp) },
            supportingContent = { Text("开启后无需倒计时；播完当前视频再执行下方操作", style = MaterialTheme.typography.bodySmall) },
            trailingContent = { Switch(waitForCurrentVideoEnd, change) },
        )
    }
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
        var hours by remember { mutableIntStateOf((minutes ?: 0) / 60) }
        var remainder by remember { mutableIntStateOf((minutes ?: 0) % 60) }
        AlertDialog(
            onDismissRequest = { custom = false },
            modifier = Modifier.width(320.dp),
            title = { Text("自定义") },
            text = {
                Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    AndroidView(
                        factory = { context -> NumberPicker(context).apply { minValue = 0; maxValue = 24; value = hours; setOnValueChangedListener { _, _, value -> hours = value } } },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Text("小时")
                    AndroidView(
                        factory = { context -> NumberPicker(context).apply { minValue = 0; maxValue = 59; value = remainder; setOnValueChangedListener { _, _, value -> remainder = value } } },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Text("分钟")
                }
            },
            dismissButton = { TextButton({ custom = false }) { Text("取消") } },
            confirmButton = { TextButton({ custom = false; onMinutesChange((hours * 60 + remainder).takeIf { it > 0 }) }) { Text("确认") } },
        )
    }
}

