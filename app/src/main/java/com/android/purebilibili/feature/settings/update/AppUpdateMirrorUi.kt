package com.android.purebilibili.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

@Composable
internal fun rememberAppUpdateMirrorPreference(): AppUpdateMirrorPreference {
    val context = LocalContext.current
    val preference by remember(context) { SettingsManager.getAppUpdateMirrorPreference(context) }
        .collectAsStateWithLifecycle(initialValue = AppUpdateMirrorPreference())
    return preference
}

@Composable
internal fun AppUpdateCustomMirrorDialog(
    initialPrefix: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var prefix by rememberSaveable { mutableStateOf(initialPrefix) }
    val normalizedPrefix = remember(prefix) { normalizeAppUpdateMirrorPrefix(prefix) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(AppUpdateMirror.CUSTOM.label) },
        text = {
            AppOutlinedTextField(
                value = prefix,
                onValueChange = { prefix = it },
                modifier = Modifier.fillMaxWidth(),
                labelText = "HTTPS 镜像前缀",
                supportingText = {
                    AppText(
                        "GHFast 前缀示例 ${AppUpdateMirror.GHFAST.prefix.orEmpty()}\n" +
                            "请求时会在此前缀后拼接原始 GitHub 资源链接",
                    )
                },
                isError = prefix.isNotBlank() && normalizedPrefix == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
            )
        },
        confirmButton = {
            AppTextButton(
                enabled = normalizedPrefix != null,
                onClick = { normalizedPrefix?.let(onConfirm) },
            ) { AppText("保存") }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("取消") } },
    )
}

private enum class AppUpdateReleaseLink(val versionLabel: String) {
    STABLE("正式版"),
    TEST("测试版"),
}

@Composable
internal fun AppUpdateDownloadChannels(state: AppUpdateDialogState, actions: AppUpdateDialogActions) {
    var releaseLink by rememberSaveable { mutableStateOf(AppUpdateReleaseLink.STABLE) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText("其他下载渠道", color = AppContentColors.secondaryText())
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 320.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppUpdateDownloadChannelButton(
                modifier = Modifier.weight(1f),
                choices = AppUpdateReleaseLink.entries,
                selected = releaseLink,
                label = { "${it.versionLabel}下载" },
                actionTitle = releaseLink.versionLabel,
                actionSubtitle = "下载",
                menuLabel = "选择发布版本",
                onSelect = { releaseLink = it },
                onClick = when (releaseLink) {
                    AppUpdateReleaseLink.STABLE -> actions.onOpenRelease
                    AppUpdateReleaseLink.TEST -> actions.onOpenTestRelease
                },
            )
            if (state.mirrorEnabled) {
                AppUpdateDownloadChannelButton(
                    modifier = Modifier.weight(1f),
                    choices = AppUpdateMirror.entries,
                    selected = state.mirrorSource,
                    label = AppUpdateMirror::label,
                    actionTitle = "镜像下载",
                    actionSubtitle = state.mirrorSource.label,
                    menuLabel = "选择下载镜像",
                    enabled = state.hasAsset && !state.downloadState.isActiveDownload(),
                    onSelect = actions.onMirrorSelect,
                    onClick = actions.onMirrorAction,
                )
            }
        }
    }
}

@Composable
private fun <T> AppUpdateDownloadChannelButton(
    modifier: Modifier,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    actionTitle: String,
    actionSubtitle: String,
    menuLabel: String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
    onClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val titleColor = if (enabled) AppContentColors.primaryText() else AppContentColors.disabled()
    val subtitleColor = if (enabled) AppContentColors.secondaryText() else AppContentColors.disabled()
    Box(modifier = modifier) {
        AppSurface(
            shape = AppShapes.container(ContainerLevel.Pill),
            color = Color.Transparent,
            border = BorderStroke(
                AppSurfaceTokens.OutlineWidth,
                AppSurfaceTokens.outline().copy(alpha = 0.45f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(
                            text = actionTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = titleColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            tapToCopyEnabled = false,
                        )
                        AppText(
                            text = actionSubtitle,
                            fontSize = 11.sp,
                            color = subtitleColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            tapToCopyEnabled = false,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.5f)
                        .width(AppSurfaceTokens.OutlineWidth)
                        .background(AppSurfaceTokens.outline().copy(alpha = 0.30f)),
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .clickable(enabled = enabled, role = Role.Button, onClick = { expanded = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = menuLabel,
                        tint = subtitleColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        AppDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            glassSource = null,
        ) {
            choices.forEach { choice ->
                AppDropdownMenuItem(
                    text = {
                        AppText(label(choice), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    },
                    selected = choice == selected,
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}
