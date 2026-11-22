package com.android.purebilibili.feature.message

import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppCompactInputField
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

internal data class MessageAction(val title: String, val run: () -> Unit)

@Composable
internal fun MessageActionDialog(actions: List<MessageAction>, onDismiss: () -> Unit) {
    AppAlertDialog(onDismissRequest = onDismiss, text = {
        Column { actions.forEach { action ->
            AppTextButton(onClick = { onDismiss(); action.run() }, modifier = Modifier.fillMaxWidth()) { AppText(action.title) }
        } }
    }, confirmButton = {})
}

/** 鼠标右键与触屏长按使用同一操作集合。 */
internal fun Modifier.messageSecondaryClick(onClick: () -> Unit): Modifier = pointerInput(onClick) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                onClick()
                event.changes.forEach { it.consume() }
            }
        }
    }
}

internal val privateMessageReportReasons = linkedMapOf(1 to "色情低俗", 2 to "政治敏感", 3 to "违法有害", 4 to "广告骚扰", 5 to "人身攻击", 6 to "诈骗", 0 to "其他问题")

@Composable
internal fun MessageReportDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit,
) {
    var selected by remember { mutableIntStateOf(4) }
    var description by remember { mutableStateOf("") }
    AppAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { AppText("举报私信") }, text = {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            privateMessageReportReasons.forEach { (id, label) ->
                AppTextButton(onClick = { selected = id }, enabled = !busy) { AppText((if (selected == id) "✓  " else "") + label) }
            }
            AppCompactInputField(value = description, onValueChange = { description = it }, placeholder = if (selected == 0) "请填写举报原因" else "补充说明（选填）", readOnly = busy)
            error?.let { AppText(it) }
        }
    }, confirmButton = {
        AppTextButton(onClick = { onSubmit(selected, description.trim().ifBlank { privateMessageReportReasons.getValue(selected) }) }, enabled = !busy && (selected != 0 || description.isNotBlank())) { AppText(if (busy) "提交中…" else "提交") }
    }, dismissButton = { AppTextButton(onClick = onDismiss, enabled = !busy) { AppText("取消") } })
}
