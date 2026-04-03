package com.android.purebilibili.feature.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable // [New]
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.DEFAULT_ANALYTICS_ENABLED
import com.android.purebilibili.core.store.DEFAULT_CRASH_TRACKING_ENABLED
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CacheUtils
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.util.EasterEggs
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.LogCollector
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.plugin.PluginManager

import dev.chrisbanes.haze.hazeSource
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import kotlinx.coroutines.launch

import com.android.purebilibili.core.ui.components.IOSSectionTitle
import com.android.purebilibili.core.ui.animation.staggeredEntrance

const val GITHUB_URL = OFFICIAL_GITHUB_URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onPlaybackClick: () -> Unit = {},
    onPermissionClick: () -> Unit = {},
    onPluginsClick: () -> Unit = {},
    onSettingsShareClick: () -> Unit = {},
    onWebDavBackupClick: () -> Unit = {},
    onNavigateToBottomBarSettings: () -> Unit = {},
    onTipsClick: () -> Unit = {}, // [Feature] Tips
    onReplayOnboardingClick: () -> Unit = {},
    mainHazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val versionClickThreshold = EasterEggs.VERSION_EASTER_EGG_THRESHOLD
    
    // State Collection
    val state by viewModel.state.collectAsState()
    val privacyModeEnabled by SettingsManager.getPrivacyModeEnabled(context).collectAsState(initial = false)
    val crashTrackingEnabled by SettingsManager.getCrashTrackingEnabled(context)
        .collectAsState(initial = DEFAULT_CRASH_TRACKING_ENABLED)
    val analyticsEnabled by SettingsManager.getAnalyticsEnabled(context)
        .collectAsState(initial = DEFAULT_ANALYTICS_ENABLED)
    val easterEggEnabled by SettingsManager.getEasterEggEnabled(context).collectAsState(initial = true)
    val customDownloadPath by SettingsManager.getDownloadPath(context).collectAsState(initial = null)
    val downloadExportTreeUri by SettingsManager.getDownloadExportTreeUri(context).collectAsState(initial = null)
    val feedApiType by SettingsManager.getFeedApiType(context).collectAsState(
        initial = SettingsManager.FeedApiType.WEB
    )
    val autoCheckUpdateEnabled by SettingsManager.getAutoCheckAppUpdate(context)
        .collectAsState(initial = true)
    val incrementalTimelineRefreshEnabled by SettingsManager.getIncrementalTimelineRefresh(context)
        .collectAsState(initial = false)
    val homeRefreshCount by SettingsManager.getHomeRefreshCount(context)
        .collectAsState(initial = com.android.purebilibili.core.store.DEFAULT_HOME_REFRESH_COUNT)
    
    // Local UI State
    var showCacheDialog by remember { mutableStateOf(false) }
    var showCacheAnimation by remember { mutableStateOf(false) }
    var cacheProgress by remember { mutableStateOf<CacheClearProgress?>(null) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    // [新增] 打赏对话框
    var showDonateDialog by remember { mutableStateOf(false) }
    var showReleaseDisclaimerDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf("点击检查") }
    var updateCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var changelogCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var updateDownloadState by remember { mutableStateOf(AppUpdateDownloadState()) }
    
    // [新增] 黑名单页面状态
    var showBlockedList by remember { mutableStateOf(false) }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }

    // Haze State for this screen
    val activeHazeState = mainHazeState ?: rememberRecoverableHazeState()

    // Directory Picker - 使用文件系统 API
    val defaultPath = remember { SettingsManager.getDefaultDownloadPath(context) }
    val downloadFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        }

        scope.launch {
            SettingsManager.setDownloadExportTreeUri(context, uri.toString())
            SettingsManager.setDownloadPath(context, null)
        }
        Toast.makeText(context, "已设置导出目录", Toast.LENGTH_SHORT).show()
    }

    // Callbacks
    val onClearCacheAction = { showCacheDialog = true }
    val onDownloadPathAction = { showPathDialog = true }
    
    // Logic Callbacks
    val onPrivacyModeChange: (Boolean) -> Unit = { enabled ->
        scope.launch { SettingsManager.setPrivacyModeEnabled(context, enabled) }
    }
    val onCrashTrackingChange: (Boolean) -> Unit = { enabled ->
        scope.launch {
            SettingsManager.setCrashTrackingEnabled(context, enabled)
            CrashReporter.setEnabled(enabled)
        }
    }
    val onAnalyticsChange: (Boolean) -> Unit = { enabled ->
        scope.launch {
            SettingsManager.setAnalyticsEnabled(context, enabled)
            AnalyticsHelper.setEnabled(enabled)
        }
    }
    val onEasterEggChange: (Boolean) -> Unit = { enabled ->
        scope.launch { SettingsManager.setEasterEggEnabled(context, enabled) }
    }
    val onAutoCheckUpdateChange: (Boolean) -> Unit = { enabled ->
        scope.launch { SettingsManager.setAutoCheckAppUpdate(context, enabled) }
    }
    
    val onVersionClickAction: () -> Unit = {
        versionClickCount++
        val message = EasterEggs.getVersionClickMessage(
            clickCount = versionClickCount,
            threshold = versionClickThreshold
        )
        val remainingClicks = (versionClickThreshold - versionClickCount).coerceAtLeast(0)
        val hapticType = if (remainingClicks <= 1) {
            HapticFeedbackType.LongPress
        } else {
            HapticFeedbackType.TextHandleMove
        }
        hapticFeedback.performHapticFeedback(hapticType)

        if (EasterEggs.isVersionEasterEggTriggered(versionClickCount, versionClickThreshold)) {
            showEasterEggDialog = true
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } else if (versionClickCount >= 2 || remainingClicks <= 3) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    val onExportLogsAction: () -> Unit = { LogCollector.exportAndShare(context) }
    val onTelegramClick: () -> Unit = { uriHandler.openUri(OFFICIAL_TELEGRAM_URL) }
    val onTwitterClick: () -> Unit = { uriHandler.openUri("https://x.com/YangY_0x00") }
    val onGithubClick: () -> Unit = { uriHandler.openUri(OFFICIAL_GITHUB_URL) }
    val onDisclaimerClick: () -> Unit = { showReleaseDisclaimerDialog = true }
    val onBlockedListClickAction: () -> Unit = { showBlockedList = true }
    suspend fun runUpdateCheck(
        silent: Boolean,
        shouldOpenReleaseNotes: Boolean = false
    ) {
        isCheckingUpdate = true
        if (!silent) {
            updateStatusText = "检查中..."
        }
        val result = AppUpdateChecker.check(com.android.purebilibili.BuildConfig.VERSION_NAME)
        result.onSuccess { info ->
            updateStatusText = info.message
            when (resolveAppUpdateDialogMode(info.isUpdateAvailable, shouldOpenReleaseNotes)) {
                AppUpdateDialogMode.UPDATE_AVAILABLE -> {
                    updateCheckResult = info
                }
                AppUpdateDialogMode.CHANGELOG -> {
                    changelogCheckResult = info
                }
                AppUpdateDialogMode.NONE -> {
                    if (!silent) {
                        Toast.makeText(context, info.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.onFailure { error ->
            if (!silent || shouldOpenReleaseNotes) {
                updateStatusText = "检查失败"
                Toast.makeText(context, error.message ?: "更新检查失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
        isCheckingUpdate = false
    }
    val onCheckUpdateAction: () -> Unit = {
        if (isCheckingUpdate) {
            Toast.makeText(context, "正在检查更新，请稍候", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                runUpdateCheck(
                    silent = false,
                    shouldOpenReleaseNotes = false
                )
            }
        }
    }
    val onViewReleaseNotesAction: () -> Unit = {
        if (isCheckingUpdate) {
            Toast.makeText(context, "正在检查更新，请稍候", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                runUpdateCheck(
                    silent = true,
                    shouldOpenReleaseNotes = true
                )
            }
        }
    }

    // Effects
    LaunchedEffect(showCacheAnimation) {
        if (showCacheAnimation) {
            val breakdown = CacheUtils.getCacheBreakdown(context)
            val totalSize = breakdown.totalSize
            val clearedSizeStr = breakdown.format()
            for (i in 0..100 step 10) {
                cacheProgress = CacheClearProgress(
                    current = (totalSize * i / 100),
                    total = totalSize,
                    isComplete = false,
                    clearedSize = clearedSizeStr
                )
                kotlinx.coroutines.delay(150)
            }
            val clearResult = viewModel.clearCache()
            if (shouldMarkCacheClearAnimationComplete(clearResult.isSuccess)) {
                cacheProgress = CacheClearProgress(
                    current = totalSize,
                    total = totalSize,
                    isComplete = true,
                    clearedSize = clearedSizeStr
                )
            } else {
                Toast.makeText(
                    context,
                    resolveCacheClearFailureMessage(clearResult.exceptionOrNull()),
                    Toast.LENGTH_SHORT
                ).show()
                showCacheAnimation = false
                cacheProgress = null
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
        AnalyticsHelper.logScreenView("SettingsScreen")
    }

    //  Transparent Navigation Bar
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val originalNavBarColor = window?.navigationBarColor ?: android.graphics.Color.TRANSPARENT
        if (window != null) window.navigationBarColor = android.graphics.Color.TRANSPARENT
        onDispose { if (window != null) window.navigationBarColor = originalNavBarColor }
    }

    // Dialogs
    if (showCacheDialog) {
        CacheClearConfirmDialog(
            cacheSize = state.cacheSize,
            onConfirm = { showCacheDialog = false; showCacheAnimation = true },
            onDismiss = { showCacheDialog = false }
        )
    }
    
    if (showCacheAnimation && cacheProgress != null) {
        CacheClearAnimationDialog(progress = cacheProgress!!, onDismiss = { showCacheAnimation = false; cacheProgress = null })
    }
    
    if (showPathDialog) {
        com.android.purebilibili.core.ui.IOSAlertDialog(
            onDismissRequest = { showPathDialog = false },
            title = { Text("下载位置", color = MaterialTheme.colorScheme.onSurface) },
            text = { 
                Column {
                    Text("默认位置（应用私有目录）：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(defaultPath.substringAfterLast("Android/"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "可选：通过系统文件夹授权设置导出目录（无需“管理所有文件”权限）",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.android.purebilibili.core.theme.iOSOrange
                    )
                    if (!downloadExportTreeUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "当前导出目录：已设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = { 
                    showPathDialog = false
                    downloadFolderPicker.launch(null)
                }) { Text("选择导出目录") }
            },
            dismissButton = { 
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = { 
                    scope.launch {
                        SettingsManager.setDownloadPath(context, null)
                        SettingsManager.setDownloadExportTreeUri(context, null)
                    }
                    showPathDialog = false
                    Toast.makeText(context, "已恢复仅应用内存储", Toast.LENGTH_SHORT).show()
                }) { Text("仅使用默认") } 
            }
        )
    }
    
    if (showEasterEggDialog) {
        com.android.purebilibili.core.ui.IOSAlertDialog(
            onDismissRequest = { showEasterEggDialog = false; versionClickCount = 0 },
            title = { Text(" 你发现了彩蛋！", fontWeight = FontWeight.Bold) },
            text = { Text("感谢你使用 BiliPai！这是一个用爱发电的开源项目。") },
            confirmButton = { com.android.purebilibili.core.ui.IOSDialogAction(onClick = { showEasterEggDialog = false; versionClickCount = 0 }) { Text("我知道了！") } }
        )
    }

    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    if (showReleaseDisclaimerDialog) {
        ReleaseChannelDisclaimerDialog(
            onDismiss = { showReleaseDisclaimerDialog = false },
            onOpenGithub = onGithubClick,
            onOpenTelegram = onTelegramClick
        )
    }

    updateCheckResult?.let { info ->
        val resolvedReleaseNotes = remember(info.releaseNotes) {
            resolveUpdateReleaseNotesText(info.releaseNotes)
        }
        val preferredAsset = remember(info.assets) {
            selectPreferredAppUpdateAsset(info.assets)
        }
        val isDialogDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val dialogTextColors = remember(isDialogDarkTheme) {
            resolveAppUpdateDialogTextColors(
                isDarkTheme = isDialogDarkTheme
            )
        }
        val releaseNotesScrollState = rememberScrollState()
        com.android.purebilibili.core.ui.IOSAlertDialog(
            onDismissRequest = { updateCheckResult = null },
            title = {
                Text(
                    text = "发现新版本 v${info.latestVersion}",
                    color = dialogTextColors.titleColor
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "当前版本 v${info.currentVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = dialogTextColors.currentVersionColor
                    )
                    preferredAsset?.let { asset ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "安装包：${asset.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = dialogTextColors.currentVersionColor
                        )
                    }
                    if (updateDownloadState.status != AppUpdateDownloadStatus.IDLE) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (updateDownloadState.status) {
                                AppUpdateDownloadStatus.DOWNLOADING -> "下载中 ${(updateDownloadState.progress * 100).toInt()}%"
                                AppUpdateDownloadStatus.COMPLETED -> "下载完成，正在准备安装"
                                AppUpdateDownloadStatus.FAILED -> updateDownloadState.errorMessage ?: "下载失败"
                                AppUpdateDownloadStatus.IDLE -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = dialogTextColors.currentVersionColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = resolvedReleaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = dialogTextColors.releaseNotesColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(releaseNotesScrollState)
                    )
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                    val downloadedFile = updateDownloadState.filePath
                        ?.takeIf { updateDownloadState.status == AppUpdateDownloadStatus.COMPLETED }
                        ?.let { path -> java.io.File(path) }
                        ?.takeIf { it.exists() }

                    if (downloadedFile != null) {
                        installDownloadedAppUpdate(context, downloadedFile)
                        return@IOSDialogAction
                    }

                    val asset = preferredAsset
                    if (asset == null) {
                        updateCheckResult = null
                        uriHandler.openUri(info.releaseUrl)
                        return@IOSDialogAction
                    }

                    if (updateDownloadState.status == AppUpdateDownloadStatus.DOWNLOADING) {
                        return@IOSDialogAction
                    }

                    scope.launch {
                        downloadAppUpdateApk(
                            context = context,
                            asset = asset,
                            onStateChange = { state -> updateDownloadState = state }
                        ).onSuccess { file ->
                            updateDownloadState = completeAppUpdateDownload(
                                current = updateDownloadState,
                                filePath = file.absolutePath
                            )
                            val installAction = installDownloadedAppUpdate(context, file)
                            if (installAction == AppUpdateInstallAction.OPEN_UNKNOWN_SOURCES_SETTINGS) {
                                Toast.makeText(context, "请先允许安装未知来源应用", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure { error ->
                            updateDownloadState = failAppUpdateDownload(
                                current = updateDownloadState,
                                errorMessage = error.message ?: "更新下载失败"
                            )
                            Toast.makeText(context, error.message ?: "更新下载失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text(
                        when {
                            preferredAsset == null -> "前往下载"
                            updateDownloadState.status == AppUpdateDownloadStatus.DOWNLOADING ->
                                "下载中 ${(updateDownloadState.progress * 100).toInt()}%"
                            updateDownloadState.status == AppUpdateDownloadStatus.COMPLETED -> "安装更新"
                            else -> "立即更新"
                        }
                    )
                }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                    updateCheckResult = null
                    updateDownloadState = AppUpdateDownloadState()
                }) { Text("稍后") }
            }
        )
    }

    changelogCheckResult?.let { info ->
        val resolvedReleaseNotes = remember(info.releaseNotes) {
            resolveUpdateReleaseNotesText(info.releaseNotes)
        }
        val isDialogDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val dialogTextColors = remember(isDialogDarkTheme) {
            resolveAppUpdateDialogTextColors(
                isDarkTheme = isDialogDarkTheme
            )
        }
        val releaseNotesScrollState = rememberScrollState()
        com.android.purebilibili.core.ui.IOSAlertDialog(
            onDismissRequest = { changelogCheckResult = null },
            title = {
                Text(
                    text = "更新日志 v${info.latestVersion}",
                    color = dialogTextColors.titleColor
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "当前版本 v${info.currentVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = dialogTextColors.currentVersionColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = resolvedReleaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = dialogTextColors.releaseNotesColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(releaseNotesScrollState)
                    )
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                    changelogCheckResult = null
                    uriHandler.openUri(info.releaseUrl)
                }) { Text("查看发布页") }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                    changelogCheckResult = null
                }) { Text("关闭") }
            }
        )
    }

    val onOpenLinksAction: () -> Unit = {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }
    val settingsSearchResults = remember(settingsSearchQuery) {
        resolveSettingsSearchResults(
            query = settingsSearchQuery,
            maxResults = 12
        )
    }
    BackHandler(enabled = shouldConsumeSettingsBack(showBlockedList)) {
        showBlockedList = false
    }
    val onSettingsSearchResultClick: (SettingsSearchTarget) -> Unit = { target ->
        settingsSearchQuery = ""
        when (target) {
            SettingsSearchTarget.APPEARANCE -> onAppearanceClick()
            SettingsSearchTarget.PLAYBACK -> onPlaybackClick()
            SettingsSearchTarget.BOTTOM_BAR -> onNavigateToBottomBarSettings()
            SettingsSearchTarget.PERMISSION -> onPermissionClick()
            SettingsSearchTarget.BLOCKED_LIST -> onBlockedListClickAction()
            SettingsSearchTarget.SETTINGS_SHARE -> onSettingsShareClick()
            SettingsSearchTarget.WEBDAV_BACKUP -> onWebDavBackupClick()
            SettingsSearchTarget.DOWNLOAD_PATH -> onDownloadPathAction()
            SettingsSearchTarget.CLEAR_CACHE -> onClearCacheAction()
            SettingsSearchTarget.PLUGINS -> onPluginsClick()
            SettingsSearchTarget.EXPORT_LOGS -> onExportLogsAction()
            SettingsSearchTarget.OPEN_SOURCE_LICENSES -> onOpenSourceLicensesClick()
            SettingsSearchTarget.OPEN_SOURCE_HOME -> onGithubClick()
            SettingsSearchTarget.CHECK_UPDATE -> onCheckUpdateAction()
            SettingsSearchTarget.VIEW_RELEASE_NOTES -> onViewReleaseNotesAction()
            SettingsSearchTarget.REPLAY_ONBOARDING -> onReplayOnboardingClick()
            SettingsSearchTarget.TIPS -> onTipsClick()
            SettingsSearchTarget.OPEN_LINKS -> onOpenLinksAction()
            SettingsSearchTarget.DONATE -> showDonateDialog = true
            SettingsSearchTarget.TELEGRAM -> onTelegramClick()
            SettingsSearchTarget.TWITTER -> onTwitterClick()
            SettingsSearchTarget.DISCLAIMER -> onDisclaimerClick()
        }
    }

    // 页面跳转逻辑
    if (showBlockedList) {
        BlockedListScreen(onBack = { showBlockedList = false })
    } else {
        // Layout Switching
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = activeHazeState)
        ) {
            if (shouldUseSettingsSplitLayout(widthDp = configuration.screenWidthDp)) {
                TabletSettingsLayout(
                    onBack = onBack,
                    onAppearanceClick = onAppearanceClick,
                    onPlaybackClick = onPlaybackClick,
                    onPermissionClick = onPermissionClick,
                    onPluginsClick = onPluginsClick,
                    onExportLogsClick = onExportLogsAction,
                    onLicenseClick = onOpenSourceLicensesClick,
                    onDisclaimerClick = onDisclaimerClick,
                    onGithubClick = onGithubClick,
                    onCheckUpdateClick = onCheckUpdateAction,
                    onViewReleaseNotesClick = onViewReleaseNotesAction,
                    onVersionClick = onVersionClickAction,
                    onReplayOnboardingClick = onReplayOnboardingClick,
                    onTipsClick = onTipsClick, // [Feature]
                    onTelegramClick = onTelegramClick,
                    onTwitterClick = onTwitterClick,
                    onSettingsShareClick = onSettingsShareClick,
                    onWebDavBackupClick = onWebDavBackupClick,
                    onDownloadPathClick = onDownloadPathAction,
                    onClearCacheClick = onClearCacheAction,
                    onPrivacyModeChange = onPrivacyModeChange,
                    onCrashTrackingChange = onCrashTrackingChange,
                    onAnalyticsChange = onAnalyticsChange,
                    onEasterEggChange = onEasterEggChange,
                    onAutoCheckUpdateChange = onAutoCheckUpdateChange,
                    privacyModeEnabled = privacyModeEnabled,
                    customDownloadPath = downloadExportTreeUri ?: customDownloadPath,
                    cacheSize = state.cacheSize,
                    crashTrackingEnabled = crashTrackingEnabled,
                    analyticsEnabled = analyticsEnabled,
                    pluginCount = PluginManager.getEnabledCount(),
                    versionName = com.android.purebilibili.BuildConfig.VERSION_NAME,
                    versionClickCount = versionClickCount,
                    versionClickThreshold = versionClickThreshold,
                    easterEggEnabled = easterEggEnabled,
                    updateStatusText = updateStatusText,
                    isCheckingUpdate = isCheckingUpdate,
                    autoCheckUpdateEnabled = autoCheckUpdateEnabled,
                    onDonateClick = { showDonateDialog = true },
                    onOpenLinksClick = onOpenLinksAction,
                    onBlockedListClick = onBlockedListClickAction, // Pass to tablet layout
                    searchQuery = settingsSearchQuery,
                    onSearchQueryChange = { settingsSearchQuery = it },
                    searchResults = settingsSearchResults,
                    onSearchResultClick = onSettingsSearchResultClick,
                    feedApiType = feedApiType,
                    onFeedApiTypeChange = { type ->
                        scope.launch {
                            SettingsManager.setFeedApiType(context, type)
                            android.widget.Toast.makeText(
                                context,
                                "已切换为${type.label}，下拉刷新生效",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    incrementalTimelineRefreshEnabled = incrementalTimelineRefreshEnabled,
                    onIncrementalTimelineRefreshChange = { enabled ->
                        scope.launch {
                            SettingsManager.setIncrementalTimelineRefresh(context, enabled)
                        }
                    },
                    homeRefreshCount = homeRefreshCount,
                    onHomeRefreshCountChange = { count ->
                        scope.launch {
                            SettingsManager.setHomeRefreshCount(context, count)
                        }
                    }
                )
            } else {
                MobileSettingsLayout(
                    onBack = onBack,
                    onAppearanceClick = onAppearanceClick,
                    onPlaybackClick = onPlaybackClick,
                    onPermissionClick = onPermissionClick,
                    onNavigateToBottomBarSettings = onNavigateToBottomBarSettings,
                    onPluginsClick = onPluginsClick,
                    onExportLogsClick = onExportLogsAction,
                    onLicenseClick = onOpenSourceLicensesClick,
                    onDisclaimerClick = onDisclaimerClick,
                    onGithubClick = onGithubClick,
                    onCheckUpdateClick = onCheckUpdateAction,
                    onViewReleaseNotesClick = onViewReleaseNotesAction,
                    onVersionClick = onVersionClickAction,
                    onTipsClick = onTipsClick, // [Feature]
                    onReplayOnboardingClick = onReplayOnboardingClick,
                    onTelegramClick = onTelegramClick,
                    onTwitterClick = onTwitterClick,
                    onSettingsShareClick = onSettingsShareClick,
                    onWebDavBackupClick = onWebDavBackupClick,
                    onDownloadPathClick = onDownloadPathAction,
                    onClearCacheClick = onClearCacheAction,
                    onPrivacyModeChange = onPrivacyModeChange,
                    onCrashTrackingChange = onCrashTrackingChange,
                    onAnalyticsChange = onAnalyticsChange,
                    onEasterEggChange = onEasterEggChange,
                    onAutoCheckUpdateChange = onAutoCheckUpdateChange,
                    privacyModeEnabled = privacyModeEnabled,
                    customDownloadPath = downloadExportTreeUri ?: customDownloadPath,
                    cacheSize = state.cacheSize,
                    crashTrackingEnabled = crashTrackingEnabled,
                    analyticsEnabled = analyticsEnabled,
                    pluginCount = PluginManager.getEnabledCount(),
                    versionName = com.android.purebilibili.BuildConfig.VERSION_NAME,
                    versionClickCount = versionClickCount,
                    versionClickThreshold = versionClickThreshold,
                    easterEggEnabled = easterEggEnabled,
                    updateStatusText = updateStatusText,
                    isCheckingUpdate = isCheckingUpdate,
                    autoCheckUpdateEnabled = autoCheckUpdateEnabled,
                    cardAnimationEnabled = state.cardAnimationEnabled,
                    isBottomBarFloating = state.isBottomBarFloating,
                    bottomBarLabelMode = state.bottomBarLabelMode,
                    feedApiType = feedApiType,
                    onFeedApiTypeChange = { type ->
                        scope.launch {
                            SettingsManager.setFeedApiType(context, type)
                            android.widget.Toast.makeText(
                                context, 
                                "已切换为${type.label}，下拉刷新生效", 
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    incrementalTimelineRefreshEnabled = incrementalTimelineRefreshEnabled,
                    onIncrementalTimelineRefreshChange = { enabled ->
                        scope.launch {
                            SettingsManager.setIncrementalTimelineRefresh(context, enabled)
                        }
                    },
                    homeRefreshCount = homeRefreshCount,
                    onHomeRefreshCountChange = { count ->
                        scope.launch {
                            SettingsManager.setHomeRefreshCount(context, count)
                        }
                    },
                    onDonateClick = { showDonateDialog = true },
                    onOpenLinksClick = onOpenLinksAction,
                    onBlockedListClick = onBlockedListClickAction, // Pass to mobile layout
                    searchQuery = settingsSearchQuery,
                    onSearchQueryChange = { settingsSearchQuery = it },
                    searchResults = settingsSearchResults,
                    onSearchResultClick = onSettingsSearchResultClick
                )
            }
            
            // Onboarding Bottom Sheet (Shared)
    
        }
    }
}

internal enum class MobileSettingsRootSection {
    FOLLOW_AUTHOR,
    GENERAL,
    PRIVACY,
    STORAGE,
    DEVELOPER,
    FEED,
    ABOUT,
    SUPPORT
}

internal fun resolveMobileSettingsRootSectionOrder(): List<MobileSettingsRootSection> = listOf(
    MobileSettingsRootSection.FOLLOW_AUTHOR,
    MobileSettingsRootSection.GENERAL,
    MobileSettingsRootSection.PRIVACY,
    MobileSettingsRootSection.STORAGE,
    MobileSettingsRootSection.DEVELOPER,
    MobileSettingsRootSection.FEED,
    MobileSettingsRootSection.ABOUT,
    MobileSettingsRootSection.SUPPORT
)

internal fun resolveMobileSettingsRootSectionTitleRes(section: MobileSettingsRootSection): Int = when (section) {
    MobileSettingsRootSection.FOLLOW_AUTHOR -> R.string.settings_section_follow_author
    MobileSettingsRootSection.GENERAL -> R.string.settings_section_general
    MobileSettingsRootSection.PRIVACY -> R.string.settings_section_privacy
    MobileSettingsRootSection.STORAGE -> R.string.settings_section_storage
    MobileSettingsRootSection.DEVELOPER -> R.string.settings_section_developer
    MobileSettingsRootSection.FEED -> R.string.settings_section_feed
    MobileSettingsRootSection.ABOUT -> R.string.settings_section_about
    MobileSettingsRootSection.SUPPORT -> R.string.settings_section_support
}

internal fun shouldMarkCacheClearAnimationComplete(clearSucceeded: Boolean): Boolean = clearSucceeded

internal fun resolveCacheClearFailureMessage(error: Throwable?): String {
    return error?.message?.takeIf { it.isNotBlank() } ?: "清理缓存失败，请稍后重试"
}

@Composable
internal fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSettingsLayout(
    onBack: () -> Unit,
    // Callbacks
    onAppearanceClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onNavigateToBottomBarSettings: () -> Unit,
    onTipsClick: () -> Unit, // [Feature]
    onPluginsClick: () -> Unit,
    onExportLogsClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onDisclaimerClick: () -> Unit,
    onGithubClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onViewReleaseNotesClick: () -> Unit,
    onVersionClick: () -> Unit,
    onReplayOnboardingClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onTwitterClick: () -> Unit,
    onSettingsShareClick: () -> Unit,
    onWebDavBackupClick: () -> Unit,
    onDownloadPathClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onDonateClick: () -> Unit,
    onOpenLinksClick: () -> Unit, // [New]
    onBlockedListClick: () -> Unit, // [New]
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SettingsSearchResult>,
    onSearchResultClick: (SettingsSearchTarget) -> Unit,
    
    // Logic Callbacks
    onPrivacyModeChange: (Boolean) -> Unit,
    onCrashTrackingChange: (Boolean) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onEasterEggChange: (Boolean) -> Unit,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    
    // State
    privacyModeEnabled: Boolean,
    customDownloadPath: String?,
    cacheSize: String,
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
    pluginCount: Int,
    versionName: String,
    versionClickCount: Int,
    versionClickThreshold: Int,
    easterEggEnabled: Boolean,
    updateStatusText: String,
    isCheckingUpdate: Boolean,
    autoCheckUpdateEnabled: Boolean,
    cardAnimationEnabled: Boolean,
    isBottomBarFloating: Boolean,
    bottomBarLabelMode: Int,
    feedApiType: SettingsManager.FeedApiType,
    onFeedApiTypeChange: (SettingsManager.FeedApiType) -> Unit,
    incrementalTimelineRefreshEnabled: Boolean,
    onIncrementalTimelineRefreshChange: (Boolean) -> Unit,
    homeRefreshCount: Int,
    onHomeRefreshCountChange: (Int) -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val windowSizeClass = LocalWindowSizeClass.current
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val effectiveMotionTier = remember(deviceUiProfile.motionTier) {
        resolveSettingsEntranceMotionTier(deviceUiProfile.motionTier)
    }
    val sectionOrder = remember { resolveMobileSettingsRootSectionOrder() }
    val bottomBarVisible = LocalBottomBarVisible.current
    val bottomInset = resolveSettingsContentBottomPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        bottomBarVisible = bottomBarVisible,
        isBottomBarFloating = isBottomBarFloating,
        bottomBarLabelMode = bottomBarLabelMode,
        isTablet = windowSizeClass.isTablet
    )
    val screenTitle = stringResource(R.string.settings_title)
    val backLabel = stringResource(R.string.common_back)

    LaunchedEffect(Unit) { isVisible = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(screenTitle, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            rememberAppBackIcon(),
                            contentDescription = backLabel
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset)
        ) {
            item {
                SettingsSearchBarSection(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange
                )
            }

            if (searchQuery.isNotBlank()) {
                item {
                    SettingsSearchResultsSection(
                        results = searchResults,
                        onResultClick = onSearchResultClick
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            } else {
                sectionOrder.forEachIndexed { index, section ->
                    item {
                        Box(modifier = Modifier.staggeredEntrance(index * 2, isVisible, motionTier = effectiveMotionTier)) {
                            SettingsCategoryHeader(
                                title = stringResource(resolveMobileSettingsRootSectionTitleRes(section))
                            )
                        }
                    }
                    item {
                        Box(modifier = Modifier.staggeredEntrance(index * 2 + 1, isVisible, motionTier = effectiveMotionTier)) {
                            when (section) {
                                MobileSettingsRootSection.FOLLOW_AUTHOR -> {
                                    FollowAuthorSection(
                                        onTelegramClick = onTelegramClick,
                                        onTwitterClick = onTwitterClick,
                                        onDonateClick = onDonateClick
                                    )
                                }
                                MobileSettingsRootSection.GENERAL -> {
                                    GeneralSection(
                                        onAppearanceClick = onAppearanceClick,
                                        onPlaybackClick = onPlaybackClick,
                                        onBottomBarClick = onNavigateToBottomBarSettings
                                    )
                                }
                                MobileSettingsRootSection.PRIVACY -> {
                                    PrivacySection(
                                        privacyModeEnabled = privacyModeEnabled,
                                        onPrivacyModeChange = onPrivacyModeChange,
                                        onPermissionClick = onPermissionClick,
                                        onBlockedListClick = onBlockedListClick
                                    )
                                }
                                MobileSettingsRootSection.STORAGE -> {
                                    DataStorageSection(
                                        customDownloadPath = customDownloadPath,
                                        cacheSize = cacheSize,
                                        onSettingsShareClick = onSettingsShareClick,
                                        onWebDavBackupClick = onWebDavBackupClick,
                                        onDownloadPathClick = onDownloadPathClick,
                                        onClearCacheClick = onClearCacheClick
                                    )
                                }
                                MobileSettingsRootSection.DEVELOPER -> {
                                    DeveloperSection(
                                        crashTrackingEnabled = crashTrackingEnabled,
                                        analyticsEnabled = analyticsEnabled,
                                        pluginCount = pluginCount,
                                        onCrashTrackingChange = onCrashTrackingChange,
                                        onAnalyticsChange = onAnalyticsChange,
                                        onPluginsClick = onPluginsClick,
                                        onExportLogsClick = onExportLogsClick
                                    )
                                }
                                MobileSettingsRootSection.FEED -> {
                                    FeedApiSection(
                                        feedApiType = feedApiType,
                                        onFeedApiTypeChange = onFeedApiTypeChange,
                                        incrementalTimelineRefreshEnabled = incrementalTimelineRefreshEnabled,
                                        onIncrementalTimelineRefreshChange = onIncrementalTimelineRefreshChange,
                                        homeRefreshCount = homeRefreshCount,
                                        onHomeRefreshCountChange = onHomeRefreshCountChange
                                    )
                                }
                                MobileSettingsRootSection.ABOUT -> {
                                    AboutSection(
                                        versionName = versionName,
                                        easterEggEnabled = easterEggEnabled,
                                        onDisclaimerClick = onDisclaimerClick,
                                        onLicenseClick = onLicenseClick,
                                        onGithubClick = onGithubClick,
                                        onCheckUpdateClick = onCheckUpdateClick,
                                        onViewReleaseNotesClick = onViewReleaseNotesClick,
                                        autoCheckUpdateEnabled = autoCheckUpdateEnabled,
                                        onAutoCheckUpdateChange = onAutoCheckUpdateChange,
                                        onVersionClick = onVersionClick,
                                        onReplayOnboardingClick = onReplayOnboardingClick,
                                        onEasterEggChange = onEasterEggChange,
                                        updateStatusText = updateStatusText,
                                        isCheckingUpdate = isCheckingUpdate,
                                        versionClickCount = versionClickCount,
                                        versionClickThreshold = versionClickThreshold
                                    )
                                }
                                MobileSettingsRootSection.SUPPORT -> {
                                    SupportToolsSection(
                                        onTipsClick = onTipsClick,
                                        onOpenLinksClick = onOpenLinksClick
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// Imports... (Ensure clickable is imported)

@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false, // Full screen
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            // QR Code Container
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.TopStart) {
                    Image(
                        painter = painterResource(id = com.android.purebilibili.R.drawable.author_qr),
                        contentDescription = "打赏二维码",
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onDismiss() }, // [New] Click to dismiss
                        contentScale = ContentScale.Fit
                    )

                    // Close Button (Top Left of Image)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = CupertinoIcons.Default.Xmark, // Fixed: Filled.Xmark -> Default.Xmark or correct path
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "感谢您的支持！",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "点击二维码或关闭按钮退出",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
