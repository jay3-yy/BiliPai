package com.android.purebilibili.feature.dynamic.visibility

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

/** 发布入口共用结果提示，账号变化时丢弃旧结果，后台不弹出。 */
@Composable
internal fun DynamicVisibilityNoticeHost() {
    val identity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val notices by DynamicVisibilityFeedback.notices.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(identity) { DynamicVisibilityFeedback.retainAccount(identity) }
    val notice = notices.firstOrNull { it.identity == identity }
    if (notice != null && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
        AppAlertDialog(
            onDismissRequest = { DynamicVisibilityFeedback.dismiss(notice) },
            title = { AppText("发布后可见性检查") },
            text = {
                Column {
                    AppText(notice.status.message)
                    AppText("动态 ${notice.dynamicId}")
                }
            },
            confirmButton = {
                AppTextButton(onClick = { DynamicVisibilityFeedback.dismiss(notice) }) { AppText("知道了") }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    clipboard.setText(AnnotatedString("https://www.bilibili.com/opus/${notice.dynamicId}"))
                    DynamicVisibilityFeedback.dismiss(notice)
                }) { AppText("复制动态链接") }
            },
        )
    }
}
