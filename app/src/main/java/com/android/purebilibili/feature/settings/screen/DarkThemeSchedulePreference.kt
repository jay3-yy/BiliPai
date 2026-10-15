package com.android.purebilibili.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.settings.DarkThemeSchedule
import com.android.purebilibili.feature.settings.formatThemeScheduleTime

/** Plain preference UI; persistence is owned by SettingsViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DarkThemeSchedulePreference(
    schedule: DarkThemeSchedule,
    onChange: (DarkThemeSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    // Search anchors use a Box: keep these three rows in one vertically measured child.
    Column(modifier = modifier) {
        AppSwitchPreference(
            title = "定时深色模式",
            subtitle = "开启后按设定时段切换，优先于上方模式；支持跨午夜",
            checked = schedule.enabled,
            onCheckedChange = { onChange(schedule.copy(enabled = it)) },
        )
        if (schedule.enabled) {
            AppPreferenceDivider()
            AppPreference(
                title = "开始时间",
                subtitle = formatThemeScheduleTime(schedule.startMinute),
                onClick = { editingStart = true },
            )
            AppPreferenceDivider()
            AppPreference(
                title = "结束时间",
                subtitle = formatThemeScheduleTime(schedule.endMinute),
                onClick = { editingStart = false },
            )
        }
    }
    editingStart?.let { start ->
        val minute = if (start) schedule.startMinute else schedule.endMinute
        val time = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)
        val useWheel = LocalAppUiStyle.current == AppUiStyle.MIUIX ||
            LocalAppThemeConfig.current.liquidGlassEnabled
        var keyboardInput by remember(start) { mutableStateOf(false) }
        var hourScrolling by remember(start) { mutableStateOf(false) }
        var minuteScrolling by remember(start) { mutableStateOf(false) }
        val selectedMinute = time.hour * 60 + time.minute
        val valid = selectedMinute != if (start) schedule.endMinute else schedule.startMinute
        AppAlertDialog(
            onDismissRequest = { editingStart = null },
            containerColor = if (useWheel) AppSurfaceTokens.surface() else null,
            title = { AppText(if (start) "开始深色模式" else "结束深色模式") },
            text = {
                Column {
                    if (useWheel) {
                        ThemeTimeWheelPicker(
                            hour = time.hour,
                            minute = time.minute,
                            onHourChange = { time.hour = it },
                            onMinuteChange = { time.minute = it },
                            onHourScrolling = { hourScrolling = it },
                            onMinuteScrolling = { minuteScrolling = it },
                        )
                    } else {
                        // Native MD3 keeps its dial and an explicit keyboard input option.
                        if (keyboardInput) TimeInput(state = time) else TimePicker(state = time)
                        AppTextButton(onClick = { keyboardInput = !keyboardInput }) {
                            AppText(if (keyboardInput) "切换到表盘" else "键盘输入")
                        }
                    }
                    if (!valid) AppText("开始和结束时间不能相同", color = AppContentColors.error())
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = valid && (!useWheel || (!hourScrolling && !minuteScrolling)),
                    onClick = {
                        onChange(if (start) schedule.copy(startMinute = selectedMinute)
                            else schedule.copy(endMinute = selectedMinute))
                        editingStart = null
                    },
                ) { AppText("确定") }
            },
            dismissButton = {
                AppTextButton(onClick = { editingStart = null }) { AppText("取消") }
            },
        )
    }
}
