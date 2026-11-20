package com.android.purebilibili.feature.settings
import com.android.purebilibili.core.ui.components.appElasticPress

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** Miuix renderer: native responsive WindowDialog, cards, progress and squircle buttons. */
@Composable
internal fun MiuixAppUpdateDialog(
    state: AppUpdateDialogState,
    actions: AppUpdateDialogActions,
    contentScrollState: ScrollState,
) {
    WindowDialog(
        show = true,
        onDismissRequest = actions.onDismissRequest,
        maxWidth = 420.dp,
    ) {
        com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect()
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MiuixUpdateHeader(state = state, onDismissRequest = actions.onDismissRequest)
            MiuixUpdateContent(
                state = state,
                actions = actions,
                scrollState = contentScrollState,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state.showPrimaryAction || state.showSecondaryAction) {
                MiuixUpdateActions(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun MiuixUpdateHeader(
    state: AppUpdateDialogState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (state.showReleaseNotesOnly) "更新日志" else "发现新版本",
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "版本 ${state.update.latestVersion}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onDismissRequest,
            minWidth = 48.dp,
            minHeight = 48.dp,
            modifier = Modifier.appElasticPress(),
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.Close,
                contentDescription = "关闭",
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun MiuixUpdateContent(
    state: AppUpdateDialogState,
    actions: AppUpdateDialogActions,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val contentMaxHeightDp = remember(screenHeightDp) {
        resolveAppUpdateScrollableContentMaxHeight(
            screenHeightDp = screenHeightDp,
            heightFraction = 0.48f,
            minHeightDp = 170,
            maxHeightDp = 390,
        )
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = contentMaxHeightDp.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.downloadState.status != AppUpdateDownloadStatus.IDLE) {
                MiuixDownloadStatus(state = state.downloadState)
            }
            MiuixVersionCard(state = state)
            MiuixReleaseNotes(releaseNotes = state.update.releaseNotes)
        }
        AppUpdateDownloadChannels(state = state, actions = actions)
    }
}

@Composable
private fun MiuixVersionCard(
    state: AppUpdateDialogState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MiuixVersionValue(
                label = "当前版本",
                value = state.update.currentVersion,
                modifier = Modifier.weight(1f),
            )
            MiuixVersionValue(
                label = "更新至",
                value = state.update.latestVersion,
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
        }
        state.preferredAssetName?.takeUnless { state.showReleaseNotesOnly }?.let { assetName ->
            Text(
                text = "安装包  $assetName",
                modifier = Modifier.padding(top = 10.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun MiuixVersionValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.headline1,
            color = if (emphasized) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun MiuixDownloadStatus(
    state: AppUpdateDownloadState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer,
            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = appUpdateStatusText(state),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
        )
        if (state.isActiveDownload()) {
            LinearProgressIndicator(
                progress = state.progress.takeIf { state.totalBytes > 0L },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (state.status == AppUpdateDownloadStatus.COMPLETED) {
            Text(
                text = if (state.checksumProvided) {
                    "SHA-256 完整性校验已通过"
                } else {
                    "此 Release 未提供 SHA-256 校验信息"
                },
                modifier = Modifier.padding(top = 8.dp),
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
private fun MiuixReleaseNotes(
    releaseNotes: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "本次更新",
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        parseUpdateReleaseNotes(releaseNotes).forEach { block ->
            when (block) {
                is AppUpdateReleaseNotesBlock.Heading -> Text(
                    text = block.text,
                    style = if (block.level == 1) {
                        MiuixTheme.textStyles.headline1
                    } else {
                        MiuixTheme.textStyles.body1
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                is AppUpdateReleaseNotesBlock.Bullet -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "•", color = MiuixTheme.colorScheme.primary)
                    Text(
                        text = block.text,
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
                AppUpdateReleaseNotesBlock.Divider -> HorizontalDivider()
                is AppUpdateReleaseNotesBlock.Paragraph -> Text(
                    text = block.text,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun MiuixUpdateActions(
    state: AppUpdateDialogState,
    actions: AppUpdateDialogActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val isDownloading = state.downloadState.isActiveDownload()
        if (state.showSecondaryAction) {
            TextButton(
                text = if (isDownloading) "取消下载" else "稍后",
                onClick = if (isDownloading) actions.onCancelDownload else actions.onDismissRequest,
                modifier = Modifier.weight(1f).appElasticPress(),
                minHeight = 48.dp,
            )
        }
        if (state.showPrimaryAction) {
            TextButton(
                text = appUpdatePrimaryLabel(state.downloadState, state.hasAsset),
                onClick = actions.onPrimaryAction,
                modifier = Modifier.weight(1f).appElasticPress(enabled = !isDownloading),
                enabled = !isDownloading,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                minHeight = 48.dp,
            )
        }
    }
}
