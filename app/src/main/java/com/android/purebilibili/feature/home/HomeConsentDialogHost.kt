package com.android.purebilibili.feature.home

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun HomeConsentDialogHost(crashTrackingConsentShown: Boolean) {
    val context = LocalContext.current
    //  [修复] 使用 rememberSaveable 记住本次会话中是否已处理过弹窗（防止导航后重新显示）
    var consentDialogHandled by rememberSaveable { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }

    // 使用须知确认后再展示崩溃统计同意（避免与门禁叠层）
    val welcomePrefs = remember { context.getSharedPreferences("app_welcome", Context.MODE_PRIVATE) }
    val userAgreementAcked = welcomePrefs.getBoolean(
        com.android.purebilibili.feature.onboarding.USER_AGREEMENT_ACK_KEY,
        false
    ) || welcomePrefs.getBoolean("first_launch_shown", false)

    // 检查是否需要显示弹窗（使用须知已确认 且 同意弹窗尚未显示过 且 本次会话未处理过）
    LaunchedEffect(crashTrackingConsentShown) {
        if (userAgreementAcked && !crashTrackingConsentShown && !consentDialogHandled) {
            showConsentDialog = true
        }
    }

    // 显示弹窗
    if (showConsentDialog) {
        com.android.purebilibili.feature.home.components.CrashTrackingConsentDialog(
            onDismiss = {
                showConsentDialog = false
                consentDialogHandled = true  // 标记为已处理
            }
        )
    }
}
