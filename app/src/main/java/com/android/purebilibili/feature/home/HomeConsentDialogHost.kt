package com.android.purebilibili.feature.home

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.feature.agreement.UserAgreementStore

@Composable
internal fun HomeConsentDialogHost(crashTrackingConsentShown: Boolean) {
    val context = LocalContext.current
    //  [修复] 使用 rememberSaveable 记住本次会话中是否已处理过弹窗（防止导航后重新显示）
    var consentDialogHandled by rememberSaveable { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }

    // 在用户协议与隐私政策确认后，单独询问崩溃追踪设置。
    LaunchedEffect(crashTrackingConsentShown) {
        if (UserAgreementStore.isAccepted(context) && !crashTrackingConsentShown && !consentDialogHandled) {
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
