package com.android.purebilibili.feature.login

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

@Composable
internal fun OfficialQrAuthorizationContent(
    modifier: Modifier = Modifier,
    viewModel: OfficialQrAuthorizationViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.reset()
        onDispose { viewModel.reset() }
    }
    OfficialQrAuthorizationPanel(
        state = state, onConfirm = viewModel::confirm, onReset = viewModel::reset,
        modifier = modifier,
        scanner = {
            BiliPaiTransferScanner(
                onCode = viewModel::scan, onError = viewModel::scannerFailed, singleShot = true, acceptAnyQr = true,
                modifier = Modifier.fillMaxWidth(),
                previewModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Black),
            )
        },
    )
}

@Composable
internal fun OfficialQrAuthorizationPanel(
    state: QrAuthorizationState,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    scanner: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            QrAuthorizationState.Idle -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        "扫描网页或 TV 登录二维码",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        "使用当前账号，授权其他设备登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
                scanner()
                AppText(
                    "将二维码对准取景框，保持完整、清晰",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    textAlign = TextAlign.Center,
                )
            }
            QrAuthorizationState.LoginRequired -> AppText("请先在 BiliPai 登录账号，再使用扫码授权。")
            QrAuthorizationState.Checking, QrAuthorizationState.Authorizing -> {
                AdaptiveLoadingIndicator()
                AppText(if (state == QrAuthorizationState.Checking) "正在校验登录请求…" else "正在授权…")
            }
            is QrAuthorizationState.Ready -> {
                AppText("确认${state.type.label}端登录", style = MaterialTheme.typography.titleMedium)
                AppText("使用账号：${state.accountName.ifBlank { "UID ${state.mid}" }}（UID ${state.mid}）",
                    textAlign = TextAlign.Center)
                state.location?.let { AppText("登录地点：$it", textAlign = TextAlign.Center) }
                if (state.locationDiffers) {
                    AppText("该登录请求来自不同地点，请核对是否为你正在使用的设备。",
                        color = AppContentColors.error(), textAlign = TextAlign.Center)
                }
                AppButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth().testTag("qr_authorization_confirm")) {
                    AppText("确认登录对方设备")
                }
                var showAgreement by remember { mutableStateOf(false) }
                com.android.purebilibili.core.ui.components.AppTextButton(
                    onClick = { showAgreement = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(
                        "确认即代表你已阅读并同意用户协议与隐私政策，点击查看全文。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        textAlign = TextAlign.Center,
                    )
                }
                if (showAgreement) {
                    com.android.purebilibili.feature.agreement.UserAgreementReviewDialog(
                        onDismiss = { showAgreement = false }
                    )
                }
                AppOutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { AppText("取消并重新扫描") }
            }
            is QrAuthorizationState.Authorized -> {
                AppText("已授权${state.type.label}端登录", style = MaterialTheme.typography.titleMedium)
                AppText("请查看对方设备的登录结果。", textAlign = TextAlign.Center)
                AppButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { AppText("继续扫描") }
            }
            is QrAuthorizationState.Failed -> {
                AppText(state.message, color = AppContentColors.error(), textAlign = TextAlign.Center)
                AppOutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { AppText("重新扫描") }
            }
        }
    }
}

@Composable
internal fun OfficialQrAuthorizationDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("扫码授权登录", style = MaterialTheme.typography.titleLarge) },
        text = {
            OfficialQrAuthorizationContent(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
        },
        confirmButton = { AppTextButton(onClick = onDismiss) { AppText("关闭") } },
    )
}
