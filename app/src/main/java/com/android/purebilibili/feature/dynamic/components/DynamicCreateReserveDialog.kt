package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.data.model.response.DynamicCreatedReserve
import com.android.purebilibili.data.repository.DynamicCreateRepository
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicCreateReserveDialog(
    reserveId: Long? = null,
    onDismiss: () -> Unit,
    onCreated: (DynamicCreatedReserve) -> Unit
) {
    val scope = rememberCoroutineScope()
    val initial = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0) }
    }
    var title by remember { mutableStateOf("") }
    var subType by remember { mutableIntStateOf(0) }
    var startAtMillis by remember { mutableLongStateOf(initial.timeInMillis) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(reserveId != null) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(reserveId, loadAttempt) {
        if (reserveId != null) {
            loading = true
            loadFailed = false
            errorMessage = null
            DynamicCreateRepository.getReserveInfo(reserveId).fold(
                onSuccess = { info ->
                    title = info.title
                    subType = info.sub_type
                    if (info.live_plan_start_time > 0) startAtMillis = info.live_plan_start_time * 1000
                },
                onFailure = { loadFailed = true; errorMessage = it.message ?: "加载预约失败" },
            )
            loading = false
        }
    }
    val startLabel = remember(startAtMillis) {
        val calendar = Calendar.getInstance().apply { timeInMillis = startAtMillis }
        "%d-%02d-%02d %02d:%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    AppAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { AppText(if (reserveId == null) "添加直播预约" else "编辑直播预约") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
            ) {
                if (loading) AppText("加载中…")
                if (!loading && !loadFailed) {
                AppTextField(value = title, onValueChange = { if (!submitting && it.length <= 14) title = it }, placeholder = "预约标题（最多 14 字）", singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
                AppFilterChip(
                    selected = subType == 0,
                    enabled = !submitting,
                    onClick = { subType = 0 },
                    label = { AppText("公开直播") }
                )
                AppFilterChip(
                    selected = subType == 1,
                    enabled = !submitting,
                    onClick = { subType = 1 },
                    label = { AppText("大航海直播") }
                )
                }
                AppTextButton(
                    enabled = !submitting,
                    onClick = { showDateTimePicker = true },
                ) {
                    AppText("开播时间 $startLabel")
                }
                }
                errorMessage?.let { AppText(it, color = AppContentColors.error()) }
                if (loadFailed) {
                    AppTextButton(onClick = { loadAttempt++ }) { AppText("重新加载") }
                }
            }
        },
        confirmButton = {
            AppDialogAction(
                onClick = {
                    if (submitting || loading || loadFailed) return@AppDialogAction
                    val now = System.currentTimeMillis()
                    if (title.isBlank()) {
                        errorMessage = "请填写预约标题"
                        return@AppDialogAction
                    }
                    if (startAtMillis <= now || startAtMillis > now + 90L * 24 * 60 * 60 * 1000) {
                        errorMessage = "请选择未来 90 天内的开播时间"
                        return@AppDialogAction
                    }
                    submitting = true
                    errorMessage = null
                    scope.launch {
                        DynamicCreateRepository.createReserve(
                            title = title,
                            livePlanStartTimeSeconds = startAtMillis / 1000L,
                            subType = subType,
                            reserveId = reserveId,
                        ).fold(
                            onSuccess = { created ->
                                submitting = false
                                onCreated(created)
                            },
                            onFailure = { error ->
                                submitting = false
                                errorMessage = error.message ?: "创建失败"
                            }
                        )
                    }
                }
            ) {
                AppText(when { loading -> "加载中…"; submitting -> "保存中…"; reserveId != null -> "保存"; else -> "创建" })
            }
        },
        dismissButton = {
            AppDialogAction(onClick = { if (!submitting) onDismiss() }) { AppText("取消") }
        }
    )
    if (showDateTimePicker) {
        DynamicDateTimeDialog("开播时间", startAtMillis, onDismiss = { showDateTimePicker = false }, onConfirm = {
            startAtMillis = it
            showDateTimePicker = false
        })
    }
}