package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

internal fun canSubmitDanmakuReport(reason: Int?, explanation: String): Boolean =
    reason != null && DanmakuReportReasons.any { it.second == reason } &&
        (reason != 11 || explanation.isNotBlank())

@Composable
internal fun DanmakuReportForm(
    canBlockUser: Boolean,
    onSubmit: (reason: Int, explanation: String, blockUser: Boolean) -> Unit,
) {
    var reason by remember { mutableStateOf<Int?>(null) }
    var explanation by remember { mutableStateOf("") }
    var blockUser by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
        DanmakuReportReasons.forEach { (label, code) ->
            AppTextButton(onClick = { reason = code }, modifier = Modifier.fillMaxWidth()) {
                AppText((if (reason == code) "✓ " else "") + label)
            }
        }
        if (reason == 11) {
            AppOutlinedTextField(
                value = explanation,
                onValueChange = { explanation = it },
                placeholderText = "请说明举报原因",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (canBlockUser) {
            Row {
                AppCheckbox(checked = blockUser, onCheckedChange = { blockUser = it })
                AppText("同时屏蔽该发送者", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp))
            }
        }
        AppTextButton(
            enabled = canSubmitDanmakuReport(reason, explanation),
            onClick = { reason?.let { onSubmit(it, explanation.trim(), blockUser) } },
            modifier = Modifier.fillMaxWidth(),
        ) { AppText("提交举报") }
    }
}
