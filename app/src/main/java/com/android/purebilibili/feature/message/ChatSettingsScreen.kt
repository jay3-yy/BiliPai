package com.android.purebilibili.feature.message

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.*
import coil3.compose.AsyncImage

@Composable
internal fun ChatSettingsScreen(
    talkerId: Long,
    userName: String,
    state: ChatUiState,
    model: ChatViewModel,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    com.android.purebilibili.core.ui.LocalNavigationBackHandler(enabled = true) { onBack() }
    LaunchedEffect(talkerId) { model.loadChatUser(); model.loadSessionControlInfo() }
    DisposableEffect(model) {
        onDispose { model.clearSessionControlError() }
    }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmIntercept by remember { mutableStateOf(false) }
    val info = state.sessionControlInfo
    val webThemeQuery = if (com.android.purebilibili.core.ui.AppSurfaceTokens.surface().luminance() < 0.5f) "native.theme=2&night=1" else "native.theme=1&night=0"
    val enabled = !state.isSessionControlUpdating && !state.isSessionControlLoading
    MessageAppScaffold(topBar = { AppTopBar(title = "聊天设置", navigationIcon = { AppIconButton(onClick = onBack) { AppIcon(rememberAppBackIcon(), "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            MessageSettingRow(state.userInfo?.name ?: userName, "UID: $talkerId", onClick = { onOpenLink("bilibili://space/$talkerId") }) {
                AsyncImage(state.userInfo?.face, "头像", Modifier.size(48.dp))
            }
            MessageSettingRow("消息免打扰", enabled = enabled && info.isDnd != null, onClick = model::toggleDnd) {
                AppSwitch(info.isDnd == true, { model.toggleDnd() }, enabled = enabled && info.isDnd != null)
            }
            if (info.showPushSetting) MessageSettingRow("接收推送", enabled = enabled && info.pushMuted != null, onClick = model::togglePushMuted) {
                AppSwitch(info.pushMuted == false, { model.togglePushMuted() }, enabled = enabled && info.pushMuted != null)
            }
            MessageSettingRow("置顶聊天", enabled = enabled && info.isPinned != null, onClick = model::togglePinned) {
                AppSwitch(info.isPinned == true, { model.togglePinned() }, enabled = enabled && info.isPinned != null)
            }
            MessageSettingRow("加入黑名单", enabled = enabled && info.followStatus != null,
                onClick = { if (info.followStatus == 128) model.toggleBlocked() else confirmBlock = true }) {
                AppSwitch(info.followStatus == 128, { if (info.followStatus == 128) model.toggleBlocked() else confirmBlock = true }, enabled = enabled && info.followStatus != null)
            }
            MessageSettingRow(if (info.isIntercept == true) "移出拦截" else "移入拦截", enabled = enabled && info.isIntercept != null,
                onClick = { if (info.isIntercept == true) model.toggleIntercept() else confirmIntercept = true })
            MessageSettingRow("举报", onClick = { onOpenLink("https://account.bilibili.com/h5/account-h5/gr/report?navhide=1&targetmid=$talkerId&$webThemeQuery") })
            if (info.isLimit == true) AppText("会话受限", Modifier.padding(16.dp))
            if (info.reportLimit == true) AppText("举报受限", Modifier.padding(16.dp))
            state.sessionControlError?.let { errorEvent ->
                LaunchedEffect(errorEvent.id, model) {
                    kotlinx.coroutines.delay(3_000L)
                    model.consumeSessionControlError(errorEvent.id)
                }
                AppText(errorEvent.message, Modifier.padding(16.dp))
            }
            AppTextButton(onClick = model::loadSessionControlInfo, enabled = !state.isSessionControlLoading) { AppText("刷新状态") }
        }
    }
    if (confirmBlock) MessageConfirmDialog("确认拉黑该用户", "加入黑名单后，将自动解除关注和合集订阅关系，并限制对方与你互动或查看你的空间。", onDismiss = { confirmBlock = false }) { confirmBlock = false; model.toggleBlocked() }
    if (confirmIntercept) MessageConfirmDialog("移入拦截", "后续会话会进入拦截分类，仍可在拦截列表中查看和恢复。", onDismiss = { confirmIntercept = false }) { confirmIntercept = false; model.toggleIntercept() }
}
