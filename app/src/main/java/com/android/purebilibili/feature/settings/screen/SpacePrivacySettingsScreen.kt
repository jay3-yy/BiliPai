package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold

@Composable
internal fun SpacePrivacySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    model: SpacePrivacyViewModel = viewModel(),
) {
    val identity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val rawState by model.state.collectAsStateWithLifecycle()
    val state = rawState.takeIf { it.identity == identity } ?: SpacePrivacyUiState(identity = identity, loading = true)
    LaunchedEffect(model) { model.load() }
    SpacePrivacySettingsContent(state, model::change, model::save, model::load, onBack, modifier)
}

@Composable
internal fun SpacePrivacySettingsContent(
    state: SpacePrivacyUiState,
    onChange: (SpacePrivacyOption, Boolean) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by rememberSaveable(state.identity.mid, state.identity.generation) { mutableStateOf(false) }
    val dirty = state.changes.isNotEmpty()
    val requestBack: () -> Unit = {
        if (!state.saving) {
            if (dirty) confirmDiscard = true else onBack()
        }
    }
    SettingsLocalBackHandler(enabled = dirty || state.saving, onBackCompleted = requestBack)
    SettingsPageScaffold(
        title = settingsDestinationCopy(SettingsSearchTarget.SPACE_PRIVACY).title,
        onBack = requestBack,
        backContentDescription = "返回",
        bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        modifier = modifier,
        actions = {
            AppTextButton(onClick = onSave, enabled = dirty && !state.saving && !state.loading) {
                AppText(if (state.saving) "保存中…" else "保存")
            }
        },
        lazyListContent = {
            item(key = "account") {
                AppText(
                    if ((state.identity.mid ?: 0L) > 0L) "当前账号 UID ${state.identity.mid}。修改将同步到 B 站，调整后请点击保存。"
                    else "请先登录后管理空间公开范围。",
                    modifier = Modifier.padding(16.dp),
                )
            }
            state.message?.let { message -> item(key = "message") { AppText(message, Modifier.padding(16.dp)) } }
            if (state.loading) {
                item(key = "loading") { AppText("正在读取空间公开范围…", Modifier.padding(16.dp)) }
            }
            if ((state.identity.mid ?: 0L) > 0L && !state.loading) {
                item(key = "reload") {
                    AppTextButton(onClick = onReload, enabled = !state.saving && !dirty) {
                        AppText(if (state.original == null) "重试" else "重新读取")
                    }
                }
            }
            if (state.original != null) {
                SpacePrivacyGroup.entries.forEach { group ->
                    item(key = group.name) {
                        AppPreferenceSectionTitle(group.title)
                        SettingsCardGroup {
                            SpacePrivacyOption.entries.filter { it.group == group }.forEachIndexed { index, option ->
                                if (index > 0) AppPreferenceDivider()
                                val checked = option.checked(state.draft[option.field])
                                AppSwitchPreference(
                                    icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.SPACE_PRIVACY),
                                    title = option.title,
                                    subtitle = if (checked == null) "当前账号暂不提供此选项" else null,
                                    checked = checked == true,
                                    onCheckedChange = { onChange(option, it) },
                                    enabled = checked != null && !state.loading && !state.saving,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
    if (confirmDiscard) {
        AppAlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { AppText("放弃未保存的修改？") },
            text = { AppText("这些修改尚未同步到 B 站。") },
            confirmButton = { AppTextButton(onClick = { confirmDiscard = false; onBack() }) { AppText("放弃修改") } },
            dismissButton = { AppTextButton(onClick = { confirmDiscard = false }) { AppText("继续编辑") } },
        )
    }
}
