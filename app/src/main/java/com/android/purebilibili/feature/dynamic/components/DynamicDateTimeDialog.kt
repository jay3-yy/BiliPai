package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.feature.settings.screen.ThemeTimeWheelPicker
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DynamicDateTimeDialog(
    title: String,
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = remember(initialMillis) { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    var year by remember { mutableStateOf(initial.get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((initial.get(Calendar.MONTH) + 1).toString()) }
    var day by remember { mutableStateOf(initial.get(Calendar.DAY_OF_MONTH).toString()) }
    val time = rememberTimePickerState(initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), is24Hour = true)
    val useWheel = LocalAppUiStyle.current == AppUiStyle.MIUIX || LocalAppThemeConfig.current.liquidGlassEnabled
    var hourScrolling by remember { mutableStateOf(false) }
    var minuteScrolling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        containerColor = if (useWheel) AppSurfaceTokens.surface() else null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            ) {
                AppText("日期")
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.3f
                    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
                            AppTextField(value = year, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { year = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = "年", keyboardOptions = numberKeyboard)
                            AppTextField(value = month, onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { month = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = "月", keyboardOptions = numberKeyboard)
                            AppTextField(value = day, onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { day = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = "日", keyboardOptions = numberKeyboard)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
                            AppTextField(value = year, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { year = it; error = null } }, modifier = Modifier.weight(1.2f), label = "年", keyboardOptions = numberKeyboard)
                            AppTextField(value = month, onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { month = it; error = null } }, modifier = Modifier.weight(1f), label = "月", keyboardOptions = numberKeyboard)
                            AppTextField(value = day, onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { day = it; error = null } }, modifier = Modifier.weight(1f), label = "日", keyboardOptions = numberKeyboard)
                        }
                    }
                }
                AppText("时间")
                if (useWheel) {
                    ThemeTimeWheelPicker(
                        hour = time.hour, minute = time.minute,
                        onHourChange = { time.hour = it }, onMinuteChange = { time.minute = it },
                        onHourScrolling = { hourScrolling = it }, onMinuteScrolling = { minuteScrolling = it },
                    )
                } else {
                    TimeInput(state = time)
                }
                error?.let { AppText(it, color = AppContentColors.error()) }
            }
        },
        confirmButton = {
            AppTextButton(enabled = !hourScrolling && !minuteScrolling, onClick = {
                val y = year.toIntOrNull()
                val m = month.toIntOrNull()
                val d = day.toIntOrNull()
                val selected = if (y != null && m != null && d != null && y in 1900..9999) runCatching {
                    Calendar.getInstance().apply {
                        isLenient = false
                        clear()
                        set(y, m - 1, d, time.hour, time.minute, 0)
                    }.timeInMillis
                }.getOrNull() else null
                if (selected == null) error = "请输入有效日期" else onConfirm(selected)
            }) { AppText("确定") }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("取消") } },
    )
}
