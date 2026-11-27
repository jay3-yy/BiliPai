package com.android.purebilibili.feature.live

import com.android.purebilibili.core.util.toUserFriendlyMessage

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.model.response.LiveFeedbackReason
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiveFeedbackDialog(
    card: LiveRoomCardUiModel,
    onSubmit: suspend (LiveFeedbackReason) -> Result<Unit>,
    onDismiss: () -> Unit,
    source: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var submitting by remember(card.roomId) { mutableStateOf(false) }
    fun submit(reason: LiveFeedbackReason) {
        if (submitting) return
        submitting = true
        scope.launch {
            try {
                onSubmit(reason).onSuccess {
                    Toast.makeText(context, "提交成功", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }.onFailure {
                    Toast.makeText(context, it.toUserFriendlyMessage("提交失败，请重试"), Toast.LENGTH_SHORT).show()
                }
            } finally { submitting = false }
        }
    }
    if (com.android.purebilibili.core.ui.components.AppGlassMenu(
            expanded = true,
            onDismissRequest = { if (!submitting) onDismiss() },
            title = "推荐反馈",
            source = source,
            maxHeight = 400.dp,
        ) {
            Column {
                card.feedback.forEach { group ->
                    AppText(group.title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    if (group.subtitle.isNotBlank()) {
                        AppText(group.subtitle, color = AppContentColors.secondaryText(),
                            modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    group.reasons.filter { it.name.isNotBlank() && it.idType.isNotBlank() }.forEach { reason ->
                        com.android.purebilibili.core.ui.components.AppDropdownMenuItem(
                            text = { AppText(reason.name) },
                            enabled = !submitting,
                            dismissOnClick = false,
                            onClick = { submit(reason) },
                        )
                    }
                }
                if (submitting) AppText("正在提交…", modifier = Modifier.padding(12.dp))
            }
        }) return
    AppAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { AppText("推荐反馈") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                card.feedback.forEach { group ->
                    AppText(group.title)
                    if (group.subtitle.isNotBlank()) AppText(group.subtitle, color = AppContentColors.secondaryText())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.reasons.filter { it.name.isNotBlank() && it.idType.isNotBlank() }.forEach { reason ->
                            AppTextButton(enabled = !submitting, onClick = { submit(reason) }) {
                                AppText(reason.name)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (submitting) AppText("正在提交…", color = AppContentColors.secondaryText())
            }
        },
        confirmButton = { AppTextButton(enabled = !submitting, onClick = onDismiss) { AppText("取消") } },
    )
}
