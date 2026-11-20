package com.android.purebilibili.feature.settings

import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import kotlinx.coroutines.launch
import java.io.File

internal const val GITHUB_RELEASE_DOWNLOAD_URL = "https://github.com/jay3-yy/BiliPai/releases/latest"
internal const val GITHUB_TEST_DOWNLOAD_URL = "https://github.com/jay3-yy/BiliPai/releases"

/** Owns update state and delegates rendering to the active theme's native dialog. */
@Composable
internal fun AppUpdateDialogHost(
    update: AppUpdateCheckResult,
    showReleaseNotesOnly: Boolean = false,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val preferredAsset = remember(update.assets) { selectPreferredAppUpdateAsset(update.assets) }
    val expectedSha256 = remember(preferredAsset, update.buildMetadata) {
        preferredAsset?.let { resolveAppUpdateExpectedSha256(it, update.buildMetadata) }
    }
    val downloadState by remember(context, update.latestVersion, expectedSha256) {
        AppUpdateDownloadCoordinator.observeState(
            context = context,
            releaseVersion = update.latestVersion,
            checksumProvided = expectedSha256 != null,
        )
    }.collectAsStateWithLifecycle(initialValue = AppUpdateDownloadState())
    val mirrorPreference = rememberAppUpdateMirrorPreference()
    var mirrorSource by remember(mirrorPreference.source) { mutableStateOf(mirrorPreference.source) }
    var editCustomMirror by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val contentScrollState = rememberScrollState()

    fun installIfReady(): Boolean {
        val completedFile = downloadState.filePath
            ?.takeIf { downloadState.status == AppUpdateDownloadStatus.COMPLETED }
            ?.let(::File)
            ?.takeIf(File::exists)
            ?: return false
        val action = installDownloadedAppUpdate(context, completedFile)
        if (action == AppUpdateInstallAction.OPEN_UNKNOWN_SOURCES_SETTINGS) {
            Toast.makeText(context, "请先允许安装未知来源应用", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    fun startMirrorDownload(customPrefix: String) {
        val asset = preferredAsset ?: return
        val mirrorUrl = resolveAppUpdateMirrorUrl(mirrorSource, customPrefix, asset.downloadUrl)
        if (mirrorUrl == null || !isAppUpdateSha256(expectedSha256)) {
            Toast.makeText(context, "此版本无法校验镜像下载，请使用 GitHub 下载", Toast.LENGTH_SHORT).show()
            return
        }
        AppUpdateDownloadCoordinator.enqueue(
            context = context,
            asset = asset,
            releaseVersion = update.latestVersion,
            expectedSha256 = expectedSha256,
            mirrorUrl = mirrorUrl,
        )
        scope.launch { contentScrollState.animateScrollTo(0) }
    }

    val onPrimaryAction: () -> Unit = {
        if (!installIfReady()) {
            if (preferredAsset != null) {
                AppUpdateDownloadCoordinator.enqueue(
                    context = context,
                    asset = preferredAsset,
                    releaseVersion = update.latestVersion,
                    expectedSha256 = expectedSha256,
                )
            } else {
                uriHandler.openUri(update.releaseUrl)
            }
        }
    }
    val dialogState = AppUpdateDialogState(
        update = update,
        downloadState = downloadState,
        preferredAssetName = preferredAsset?.name,
        hasAsset = preferredAsset != null,
        showReleaseNotesOnly = showReleaseNotesOnly,
        mirrorSource = mirrorSource,
    )
    val actions = AppUpdateDialogActions(
        onPrimaryAction = onPrimaryAction,
        onCancelDownload = { AppUpdateDownloadCoordinator.cancel(context) },
        onOpenRelease = { uriHandler.openUri(GITHUB_RELEASE_DOWNLOAD_URL) },
        onOpenTestRelease = { uriHandler.openUri(GITHUB_TEST_DOWNLOAD_URL) },
        onMirrorSelect = { mirrorSource = it },
        onMirrorAction = {
            if (mirrorSource == AppUpdateMirror.CUSTOM && mirrorPreference.customPrefix.isEmpty()) {
                editCustomMirror = true
            } else {
                startMirrorDownload(mirrorPreference.customPrefix)
            }
        },
        onDismissRequest = onDismissRequest,
    )

    when (LocalAppUiStyle.current) {
        AppUiStyle.MATERIAL3 -> Material3AppUpdateDialog(dialogState, actions, contentScrollState)
        AppUiStyle.MIUIX -> MiuixAppUpdateDialog(dialogState, actions, contentScrollState)
    }
    if (editCustomMirror) {
        AppUpdateCustomMirrorDialog(
            initialPrefix = mirrorPreference.customPrefix,
            onConfirm = { prefix ->
                editCustomMirror = false
                scope.launch { SettingsManager.setAppUpdateCustomMirror(context, prefix, useAsDefault = false) }
                startMirrorDownload(prefix)
            },
            onDismiss = { editCustomMirror = false },
        )
    }
}

internal data class AppUpdateDialogState(
    val update: AppUpdateCheckResult,
    val downloadState: AppUpdateDownloadState,
    val preferredAssetName: String?,
    val hasAsset: Boolean,
    val showReleaseNotesOnly: Boolean,
    val mirrorSource: AppUpdateMirror,
) {
    val showPrimaryAction: Boolean get() = !showReleaseNotesOnly || hasAsset
    val showSecondaryAction: Boolean get() = !showReleaseNotesOnly || downloadState.isActiveDownload()
}

internal data class AppUpdateDialogActions(
    val onPrimaryAction: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onOpenRelease: () -> Unit,
    val onOpenTestRelease: () -> Unit,
    val onMirrorSelect: (AppUpdateMirror) -> Unit,
    val onMirrorAction: () -> Unit,
    val onDismissRequest: () -> Unit,
)

internal fun appUpdatePrimaryLabel(state: AppUpdateDownloadState, hasAsset: Boolean): String = when {
    state.status == AppUpdateDownloadStatus.COMPLETED -> "安装更新"
    state.status == AppUpdateDownloadStatus.QUEUED -> "等待下载"
    state.status == AppUpdateDownloadStatus.DOWNLOADING -> "下载中"
    !hasAsset -> "前往下载"
    else -> "下载更新"
}

internal fun appUpdateStatusText(state: AppUpdateDownloadState): String = when (state.status) {
    AppUpdateDownloadStatus.QUEUED -> "等待网络后开始下载"
    AppUpdateDownloadStatus.DOWNLOADING -> {
        val label = if (state.usedGitHubFallback) "已切换到 GitHub 下载" else "正在下载"
        "$label · ${(state.progress * 100).toInt()}%"
    }
    AppUpdateDownloadStatus.COMPLETED ->
        if (state.usedGitHubFallback) "已从 GitHub 下载，可以安装" else "下载完成，可以安装"
    AppUpdateDownloadStatus.FAILED -> state.errorMessage ?: "下载失败，请稍后重试"
    AppUpdateDownloadStatus.IDLE -> ""
}

internal fun AppUpdateDownloadState.isActiveDownload(): Boolean {
    return status == AppUpdateDownloadStatus.DOWNLOADING || status == AppUpdateDownloadStatus.QUEUED
}
