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
import androidx.compose.foundation.lazy.LazyListState
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
import com.android.purebilibili.core.util.CacheClearTarget
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.DEFAULT_ANALYTICS_ENABLED
import com.android.purebilibili.core.store.DEFAULT_CRASH_TRACKING_ENABLED
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CacheUtils
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.util.EasterEggs
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.LogCollector
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.TopReadabilityChrome
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.plugin.PluginManager

import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import kotlinx.coroutines.launch

import com.android.purebilibili.core.ui.components.IOSSectionTitle
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.feature.dynamic.defaultDynamicTabVisibleIds
import com.android.purebilibili.feature.dynamic.resolveDynamicVisibleTabIdsAfterToggle
import androidx.lifecycle.compose.collectAsStateWithLifecycle

const val GITHUB_URL = OFFICIAL_GITHUB_URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onAnimationClick: () -> Unit = {},
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val privacyModeEnabled by SettingsManager.getPrivacyModeEnabled(context).collectAsStateWithLifecycle(initialValue = false)
    val privacyContentAuthenticationEnabled by SettingsManager
        .getPrivacyContentAuthenticationEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val crashTrackingEnabled by SettingsManager.getCrashTrackingEnabled(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_CRASH_TRACKING_ENABLED)
    val analyticsEnabled by SettingsManager.getAnalyticsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_ANALYTICS_ENABLED)
    val easterEggEnabled by SettingsManager.getEasterEggEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val customDownloadPath by SettingsManager.getDownloadPath(context).collectAsStateWithLifecycle(initialValue = null)
    val downloadExportTreeUri by SettingsManager.getDownloadExportTreeUri(context).collectAsStateWithLifecycle(initialValue = null)
    val imageSaveTreeUri by SettingsManager.getImageSaveTreeUri(context).collectAsStateWithLifecycle(initialValue = null)
    val feedApiType by SettingsManager.getFeedApiType(context).collectAsStateWithLifecycle(initialValue = SettingsManager.FeedApiType.WEB
    )
    val autoCheckUpdateEnabled by SettingsManager.getAutoCheckAppUpdate(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val incrementalTimelineRefreshEnabled by SettingsManager.getIncrementalTimelineRefresh(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val homeRefreshCount by SettingsManager.getHomeRefreshCount(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.DEFAULT_HOME_REFRESH_COUNT)
    val dynamicVisibleTabIds by SettingsManager.getDynamicTabVisibleTabs(context)
        .collectAsStateWithLifecycle(initialValue = defaultDynamicTabVisibleIds)
    val dynamicImagePreviewTextVisible by SettingsManager.getDynamicImagePreviewTextVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val dynamicAllTabHorizontalUserListVisible by SettingsManager
        .getDynamicAllTabHorizontalUserListVisible(context)
        .collectAsStateWithLifecycle(initialValue = false)
    
    // Local UI State
    var showCacheDialog by remember { mutableStateOf(false) }
    var showCacheAnimation by remember { mutableStateOf(false) }
    var cacheProgress by remember { mutableStateOf<CacheClearProgress?>(null) }
    val cacheClearOptions = remember { resolveCacheClearOptions() }
    var selectedCacheClearTargets by remember {
        mutableStateOf(resolveDefaultCacheClearTargets())
    }
    var pendingCacheClearTargets by remember {
        mutableStateOf(resolveDefaultCacheClearTargets())
    }
    val selectedCacheSizeSummary = remember(state.cacheBreakdown, selectedCacheClearTargets) {
        resolveSelectedCacheSizeSummary(
            breakdown = state.cacheBreakdown,
            selectedTargets = selectedCacheClearTargets
        )
    }
    var versionClickCount by remember { mutableIntStateOf(0) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    var showImageSavePathDialog by remember { mutableStateOf(false) }
    // [新增] 打赏对话框
    var showDonateDialog by remember { mutableStateOf(false) }
    var showReleaseDisclaimerDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf("点击检查") }
    var updateCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var changelogCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var updateDownloadState by remember { mutableStateOf(AppUpdateDownloadState()) }
    var currentReleaseEvidence by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var installedApkSha256 by remember { mutableStateOf<String?>(null) }
    
    // [新增] 黑名单页面状态
    var showBlockedList by remember { mutableStateOf(false) }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    val installedBuildProvenance = remember { readInstalledAppBuildProvenance() }
    val buildVerificationState = remember(currentReleaseEvidence, installedApkSha256) {
        resolveAppBuildVerificationState(
            currentVersion = com.android.purebilibili.BuildConfig.VERSION_NAME,
            localBuildCommitSha = installedBuildProvenance.commitSha,
            localWorkflowRunId = installedBuildProvenance.workflowRunId,
            localWorkflowRunUrl = installedBuildProvenance.workflowRunUrl,
            localReleaseTag = installedBuildProvenance.releaseTag,
            localApkSha256 = installedApkSha256,
            remoteRelease = currentReleaseEvidence
        )
    }
    val buildVerificationLabel = remember(buildVerificationState.status) {
        resolveAppBuildVerificationLabel(buildVerificationState.status)
    }
    val buildSourceFallback = remember(buildVerificationState.releaseTag, buildVerificationState.workflowRunId) {
        if (
            !buildVerificationState.releaseTag.isNullOrBlank() ||
            !buildVerificationState.workflowRunId.isNullOrBlank()
        ) {
            "GitHub Release"
        } else {
            "本地构建"
        }
    }
    val buildSourceValue = remember(
        buildVerificationState.sourceCommitSha,
        installedBuildProvenance.commitSha,
        buildSourceFallback
    ) {
        resolveBuildSourceValue(
            buildVerificationState.sourceCommitSha ?: installedBuildProvenance.commitSha,
            fallback = buildSourceFallback
        )
    }
    val buildSourceSubtitle = remember(buildVerificationState.workflowRunId, buildVerificationState.releaseTag) {
        resolveBuildSourceSubtitle(
            workflowRunId = buildVerificationState.workflowRunId ?: installedBuildProvenance.workflowRunId,
            releaseTag = buildVerificationState.releaseTag ?: installedBuildProvenance.releaseTag
        )
    }
    val buildFingerprintValue = remember(installedApkSha256) {
        resolveBuildFingerprintValue(installedApkSha256)
    }
    val buildFingerprintCopyValue = remember(installedApkSha256) {
        installedApkSha256 ?: "未读取"
    }
    val buildFingerprintSubtitle = remember(
        buildVerificationState.localApkSha256,
        buildVerificationState.remoteApkSha256,
        buildVerificationState.releaseIsImmutable,
        buildVerificationState.hasAttestation
    ) {
        resolveBuildFingerprintSubtitle(
            localApkSha256 = buildVerificationState.localApkSha256,
            remoteApkSha256 = buildVerificationState.remoteApkSha256,
            releaseIsImmutable = buildVerificationState.releaseIsImmutable,
            hasAttestation = buildVerificationState.hasAttestation
        )
    }

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
    val imageSaveFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        }

        scope.launch {
            SettingsManager.setImageSaveTreeUri(context, uri.toString())
        }
        Toast.makeText(context, "已设置图片保存目录", Toast.LENGTH_SHORT).show()
    }

    // Callbacks
    val onClearCacheAction = {
        selectedCacheClearTargets = resolveDefaultCacheClearTargets()
        showCacheDialog = true
    }
    val onDownloadPathAction = { showPathDialog = true }
    val onImageSavePathAction = { showImageSavePathDialog = true }
    
    // Logic Callbacks
    val onPrivacyModeChange: (Boolean) -> Unit = { enabled ->
        scope.launch { SettingsManager.setPrivacyModeEnabled(context, enabled) }
    }
    val onPrivacyContentAuthenticationChange: (Boolean) -> Unit = { enabled ->
        scope.launch { SettingsManager.setPrivacyContentAuthenticationEnabled(context, enabled) }
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
    val onVerificationClick: () -> Unit = {
        uriHandler.openUri(
            currentReleaseEvidence?.verificationMetadata?.attestationUrl
                ?: currentReleaseEvidence?.releaseUrl
                ?: OFFICIAL_GITHUB_URL
        )
    }
    val onBuildSourceClick: () -> Unit = {
        uriHandler.openUri(
            buildVerificationState.workflowRunUrl
                ?: installedBuildProvenance.workflowRunUrl
                    .takeIf { it.isNotBlank() }
                ?: OFFICIAL_GITHUB_URL
        )
    }
    val onBuildFingerprintClick: () -> Unit = {
        uriHandler.openUri(
            currentReleaseEvidence?.verificationMetadata?.attestationUrl
                ?: currentReleaseEvidence?.releaseUrl
                ?: OFFICIAL_GITHUB_URL
        )
    }
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
            val clearResult = viewModel.clearCache(pendingCacheClearTargets)
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
        installedApkSha256 = calculateInstalledApkSha256(context)
        currentReleaseEvidence = AppUpdateChecker
            .check(com.android.purebilibili.BuildConfig.VERSION_NAME)
            .getOrNull()
    }

    //  Transparent Navigation Bar
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        @Suppress("DEPRECATION")
        val originalNavBarColor = window?.navigationBarColor ?: android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        if (window != null) window.navigationBarColor = android.graphics.Color.TRANSPARENT
        onDispose {
            @Suppress("DEPRECATION")
            if (window != null) window.navigationBarColor = originalNavBarColor
        }
    }

    // Dialogs
    if (showCacheDialog) {
        CacheClearConfirmDialog(
            selectedCacheSizeSummary = selectedCacheSizeSummary,
            options = cacheClearOptions,
            selectedTargets = selectedCacheClearTargets,
            onTargetToggle = { target, checked ->
                selectedCacheClearTargets = if (checked) {
                    selectedCacheClearTargets + target
                } else {
                    selectedCacheClearTargets - target
                }
            },
            onConfirm = {
                if (selectedCacheClearTargets.isEmpty()) {
                    Toast.makeText(context, "请至少选择一项要清理的缓存", Toast.LENGTH_SHORT).show()
                } else {
                    pendingCacheClearTargets = selectedCacheClearTargets
                    showCacheDialog = false
                    showCacheAnimation = true
                }
            },
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
    if (showImageSavePathDialog) {
        com.android.purebilibili.core.ui.IOSAlertDialog(
            onDismissRequest = { showImageSavePathDialog = false },
            title = { Text("图片保存位置", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text(
                        "默认保存到系统相册的 BiliPai 文件夹。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "可通过系统文件夹授权选择动态图片、头像和评论图片的保存目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.android.purebilibili.core.theme.iOSOrange
                    )
                    if (!imageSaveTreeUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "当前图片目录：已选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                    showImageSavePathDialog = false
                    imageSaveFolderPicker.launch(null)
                }) { Text("选择图片目录") }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                    scope.launch {
                        SettingsManager.setImageSaveTreeUri(context, null)
                    }
                    showImageSavePathDialog = false
                    Toast.makeText(context, "已恢复默认图片保存位置", Toast.LENGTH_SHORT).show()
                }) { Text("恢复默认") }
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
        val releaseCommit = remember(info.buildMetadata?.gitCommitSha) {
            resolveBuildSourceValue(info.buildMetadata?.gitCommitSha, fallback = "未知")
        }
        val releaseWorkflowSubtitle = remember(info.buildMetadata?.workflowRunId, info.buildMetadata?.releaseTag) {
            resolveBuildSourceSubtitle(
                workflowRunId = info.buildMetadata?.workflowRunId,
                releaseTag = info.buildMetadata?.releaseTag
            )
        }
        val releaseVerificationEvidence = remember(info.verificationMetadata?.attestationUrl) {
            if (info.verificationMetadata?.attestationUrl?.isNotBlank() == true) {
                "GitHub Attestation"
            } else {
                "未提供"
            }
        }
        val isDialogDarkTheme = AppSurfaceTokens.cardContainer().luminance() < 0.5f
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Release 锁定：${if (info.releaseIsImmutable) "Immutable" else "可变"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogTextColors.currentVersionColor
                    )
                    Text(
                        text = "源码提交：$releaseCommit",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogTextColors.currentVersionColor
                    )
                    Text(
                        text = "构建来源：$releaseWorkflowSubtitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogTextColors.currentVersionColor
                    )
                    Text(
                        text = "Provenance：$releaseVerificationEvidence",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogTextColors.currentVersionColor
                    )
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
        val releaseCommit = remember(info.buildMetadata?.gitCommitSha) {
            resolveBuildSourceValue(info.buildMetadata?.gitCommitSha, fallback = "未知")
        }
        val releaseWorkflowSubtitle = remember(info.buildMetadata?.workflowRunId, info.buildMetadata?.releaseTag) {
            resolveBuildSourceSubtitle(
                workflowRunId = info.buildMetadata?.workflowRunId,
                releaseTag = info.buildMetadata?.releaseTag
            )
        }
        val releaseVerificationEvidence = remember(info.verificationMetadata?.attestationUrl) {
            if (info.verificationMetadata?.attestationUrl?.isNotBlank() == true) {
                "GitHub Attestation"
            } else {
                "未提供"
            }
        }
        val isDialogDarkTheme = AppSurfaceTokens.cardContainer().luminance() < 0.5f
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Release 锁定：${if (info.releaseIsImmutable) "Immutable" else "可变"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogTextColors.currentVersionColor
                    )
                    Text(
                        text = "源码提交：$releaseCommit",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogTextColors.currentVersionColor
                    )
                    Text(
                        text = "构建来源：$releaseWorkflowSubtitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogTextColors.currentVersionColor
                    )
                    Text(
                        text = "Provenance：$releaseVerificationEvidence",
                        style = MaterialTheme.typography.bodySmall,
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
            maxResults = 20
        )
    }
    BackHandler(enabled = shouldConsumeSettingsBack(showBlockedList)) {
        showBlockedList = false
    }
    val onSettingsSearchResultClick: (SettingsSearchResult) -> Unit = handler@{ result ->
        settingsSearchQuery = ""
        resolveSettingsSceneDetailFocus(result.target)?.let { detailFocus ->
            SettingsSearchFocusController.submit(detailFocus.target, detailFocus.focusId)
            when (detailFocus.target) {
                SettingsSearchTarget.APPEARANCE -> onAppearanceClick()
                SettingsSearchTarget.ANIMATION -> onAnimationClick()
                SettingsSearchTarget.PLAYBACK -> onPlaybackClick()
                SettingsSearchTarget.BOTTOM_BAR -> onNavigateToBottomBarSettings()
                else -> Unit
            }
            return@handler
        }
        if (isSceneSettingsSearchTarget(result.target)) {
            SettingsSearchFocusController.submit(result.target, result.focusId ?: result.target.name)
            return@handler
        }
        SettingsSearchFocusController.submit(result.target, result.focusId)
        when (result.target) {
            SettingsSearchTarget.INTERFACE_THEME,
            SettingsSearchTarget.HOME_FEED,
            SettingsSearchTarget.NAVIGATION,
            SettingsSearchTarget.PLAYBACK_QUALITY,
            SettingsSearchTarget.FULLSCREEN_GESTURE,
            SettingsSearchTarget.INTERACTION_COMMENT,
            SettingsSearchTarget.DATA_BACKUP,
            SettingsSearchTarget.PRIVACY_PERMISSION,
            SettingsSearchTarget.DIAGNOSTICS,
            SettingsSearchTarget.ABOUT_SUPPORT -> Unit
            SettingsSearchTarget.APPEARANCE -> onAppearanceClick()
            SettingsSearchTarget.ANIMATION -> onAnimationClick()
            SettingsSearchTarget.PLAYBACK -> onPlaybackClick()
            SettingsSearchTarget.BOTTOM_BAR -> onNavigateToBottomBarSettings()
            SettingsSearchTarget.PERMISSION -> onPermissionClick()
            SettingsSearchTarget.BLOCKED_LIST -> onBlockedListClickAction()
            SettingsSearchTarget.SETTINGS_SHARE -> onSettingsShareClick()
            SettingsSearchTarget.WEBDAV_BACKUP -> onWebDavBackupClick()
            SettingsSearchTarget.DOWNLOAD_PATH -> onDownloadPathAction()
            SettingsSearchTarget.IMAGE_SAVE_PATH -> onImageSavePathAction()
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
    CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides state.isLiquidGlassEnabled) {
        if (showBlockedList) {
            BlockedListScreen(onBack = { showBlockedList = false })
        } else {
        // Layout Switching
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSourceCompat(state = activeHazeState)
        ) {
            if (shouldUseSettingsSplitLayout(widthDp = configuration.screenWidthDp)) {
                TabletSettingsLayout(
                    onBack = onBack,
                    onAppearanceClick = onAppearanceClick,
                    onAnimationClick = onAnimationClick,
                    onPlaybackClick = onPlaybackClick,
                    onPermissionClick = onPermissionClick,
                    onPluginsClick = onPluginsClick,
                    onExportLogsClick = onExportLogsAction,
                    onLicenseClick = onOpenSourceLicensesClick,
                    onDisclaimerClick = onDisclaimerClick,
                    onGithubClick = onGithubClick,
                    onVerificationClick = onVerificationClick,
                    onBuildSourceClick = onBuildSourceClick,
                    onBuildFingerprintClick = onBuildFingerprintClick,
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
                    onImageSavePathClick = onImageSavePathAction,
                    onClearCacheClick = onClearCacheAction,
                    onPrivacyModeChange = onPrivacyModeChange,
                    onPrivacyContentAuthenticationChange = onPrivacyContentAuthenticationChange,
                    onCrashTrackingChange = onCrashTrackingChange,
                    onAnalyticsChange = onAnalyticsChange,
                    onEasterEggChange = onEasterEggChange,
                    onAutoCheckUpdateChange = onAutoCheckUpdateChange,
                    privacyModeEnabled = privacyModeEnabled,
                    customDownloadPath = downloadExportTreeUri ?: customDownloadPath,
                    customImageSavePath = imageSaveTreeUri,
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
                    privacyContentAuthenticationEnabled = privacyContentAuthenticationEnabled,
                    verificationLabel = buildVerificationLabel,
                    verificationSubtitle = buildVerificationState.summary,
                    buildSourceValue = buildSourceValue,
                    buildSourceSubtitle = buildSourceSubtitle,
                    buildFingerprintValue = buildFingerprintValue,
                    buildFingerprintCopyValue = buildFingerprintCopyValue,
                    buildFingerprintSubtitle = buildFingerprintSubtitle,
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
                    dynamicImagePreviewTextVisible = dynamicImagePreviewTextVisible,
                    onDynamicImagePreviewTextVisibleChange = { visible ->
                        scope.launch {
                            SettingsManager.setDynamicImagePreviewTextVisible(context, visible)
                        }
                    },
                    dynamicAllTabHorizontalUserListVisible = dynamicAllTabHorizontalUserListVisible,
                    onDynamicAllTabHorizontalUserListVisibleChange = { visible ->
                        scope.launch {
                            SettingsManager.setDynamicAllTabHorizontalUserListVisible(context, visible)
                        }
                    },
                    dynamicVisibleTabIds = dynamicVisibleTabIds,
                    onDynamicTabVisibilityChange = { tabId ->
                        scope.launch {
                            SettingsManager.setDynamicTabVisibleTabs(
                                context,
                                resolveDynamicVisibleTabIdsAfterToggle(dynamicVisibleTabIds, tabId)
                            )
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
                    onAnimationClick = onAnimationClick,
                    onPlaybackClick = onPlaybackClick,
                    onPermissionClick = onPermissionClick,
                    onNavigateToBottomBarSettings = onNavigateToBottomBarSettings,
                    onPluginsClick = onPluginsClick,
                    onExportLogsClick = onExportLogsAction,
                    onLicenseClick = onOpenSourceLicensesClick,
                    onDisclaimerClick = onDisclaimerClick,
                    onGithubClick = onGithubClick,
                    onVerificationClick = onVerificationClick,
                    onBuildSourceClick = onBuildSourceClick,
                    onBuildFingerprintClick = onBuildFingerprintClick,
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
                    onImageSavePathClick = onImageSavePathAction,
                    onClearCacheClick = onClearCacheAction,
                    onPrivacyModeChange = onPrivacyModeChange,
                    onPrivacyContentAuthenticationChange = onPrivacyContentAuthenticationChange,
                    onCrashTrackingChange = onCrashTrackingChange,
                    onAnalyticsChange = onAnalyticsChange,
                    onEasterEggChange = onEasterEggChange,
                    onAutoCheckUpdateChange = onAutoCheckUpdateChange,
                    privacyModeEnabled = privacyModeEnabled,
                    customDownloadPath = downloadExportTreeUri ?: customDownloadPath,
                    customImageSavePath = imageSaveTreeUri,
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
                    privacyContentAuthenticationEnabled = privacyContentAuthenticationEnabled,
                    verificationLabel = buildVerificationLabel,
                    verificationSubtitle = buildVerificationState.summary,
                    buildSourceValue = buildSourceValue,
                    buildSourceSubtitle = buildSourceSubtitle,
                    buildFingerprintValue = buildFingerprintValue,
                    buildFingerprintCopyValue = buildFingerprintCopyValue,
                    buildFingerprintSubtitle = buildFingerprintSubtitle,
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
                    dynamicImagePreviewTextVisible = dynamicImagePreviewTextVisible,
                    onDynamicImagePreviewTextVisibleChange = { visible ->
                        scope.launch {
                            SettingsManager.setDynamicImagePreviewTextVisible(context, visible)
                        }
                    },
                    dynamicAllTabHorizontalUserListVisible = dynamicAllTabHorizontalUserListVisible,
                    onDynamicAllTabHorizontalUserListVisibleChange = { visible ->
                        scope.launch {
                            SettingsManager.setDynamicAllTabHorizontalUserListVisible(context, visible)
                        }
                    },
                    dynamicVisibleTabIds = dynamicVisibleTabIds,
                    onDynamicTabVisibilityChange = { tabId ->
                        scope.launch {
                            SettingsManager.setDynamicTabVisibleTabs(
                                context,
                                resolveDynamicVisibleTabIdsAfterToggle(dynamicVisibleTabIds, tabId)
                            )
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
    onAnimationClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onNavigateToBottomBarSettings: () -> Unit,
    onTipsClick: () -> Unit, // [Feature]
    onPluginsClick: () -> Unit,
    onExportLogsClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onDisclaimerClick: () -> Unit,
    onGithubClick: () -> Unit,
    onVerificationClick: () -> Unit,
    onBuildSourceClick: () -> Unit,
    onBuildFingerprintClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onViewReleaseNotesClick: () -> Unit,
    onVersionClick: () -> Unit,
    onReplayOnboardingClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onTwitterClick: () -> Unit,
    onSettingsShareClick: () -> Unit,
    onWebDavBackupClick: () -> Unit,
    onDownloadPathClick: () -> Unit,
    onImageSavePathClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onDonateClick: () -> Unit,
    onOpenLinksClick: () -> Unit, // [New]
    onBlockedListClick: () -> Unit, // [New]
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SettingsSearchResult>,
    onSearchResultClick: (SettingsSearchResult) -> Unit,
    
    // Logic Callbacks
    onPrivacyModeChange: (Boolean) -> Unit,
    onPrivacyContentAuthenticationChange: (Boolean) -> Unit,
    onCrashTrackingChange: (Boolean) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onEasterEggChange: (Boolean) -> Unit,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    
    // State
    privacyModeEnabled: Boolean,
    privacyContentAuthenticationEnabled: Boolean,
    customDownloadPath: String?,
    customImageSavePath: String?,
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
    verificationLabel: String,
    verificationSubtitle: String,
    buildSourceValue: String,
    buildSourceSubtitle: String,
    buildFingerprintValue: String,
    buildFingerprintCopyValue: String,
    buildFingerprintSubtitle: String,
    cardAnimationEnabled: Boolean,
    isBottomBarFloating: Boolean,
    bottomBarLabelMode: Int,
    feedApiType: SettingsManager.FeedApiType,
    onFeedApiTypeChange: (SettingsManager.FeedApiType) -> Unit,
    incrementalTimelineRefreshEnabled: Boolean,
    onIncrementalTimelineRefreshChange: (Boolean) -> Unit,
    dynamicImagePreviewTextVisible: Boolean,
    onDynamicImagePreviewTextVisibleChange: (Boolean) -> Unit,
    dynamicAllTabHorizontalUserListVisible: Boolean,
    onDynamicAllTabHorizontalUserListVisibleChange: (Boolean) -> Unit,
    dynamicVisibleTabIds: Set<String>,
    onDynamicTabVisibilityChange: (String) -> Unit,
    homeRefreshCount: Int,
    onHomeRefreshCountChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val windowSizeClass = LocalWindowSizeClass.current
    val sectionOrder = remember { resolveSettingsRootCategoryOrder() }
    var activeRootCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val activeRootCategory = remember(activeRootCategoryName) {
        activeRootCategoryName?.let { name ->
            SettingsRootCategory.entries.firstOrNull { it.name == name }
        }
    }
    val bodyDestination = remember(searchQuery, activeRootCategory) {
        resolveSettingsRootBodyDestination(
            searchQuery = searchQuery,
            activeCategory = activeRootCategory
        )
    }
    // 每个层级（首页 / 分类子页 / 搜索）各自保留独立滚动位置，
    // 避免在子页滚动后返回首页时把偏移带回首页。
    val listStateStore = remember { mutableMapOf<String, LazyListState>() }
    val listStateKey = when (val destination = bodyDestination) {
        SettingsRootBodyDestination.Home -> "home"
        SettingsRootBodyDestination.Search -> "search"
        is SettingsRootBodyDestination.Category -> "category:${destination.category.name}"
    }
    val listState = listStateStore.getOrPut(listStateKey) { LazyListState() }
    val entranceAnimationEnabled by SettingsManager.getUiEntranceAnimationEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val reduceMotion = rememberSystemReduceMotion()
    val focusRequest by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
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
    val rootCategoryActions = SettingsRootCategoryActions(
        onAppearanceClick = onAppearanceClick,
        onAnimationClick = onAnimationClick,
        onPlaybackClick = onPlaybackClick,
        onBottomBarClick = onNavigateToBottomBarSettings,
        onPermissionClick = onPermissionClick,
        onBlockedListClick = onBlockedListClick,
        onPluginsClick = onPluginsClick,
        onExportLogsClick = onExportLogsClick,
        onSettingsShareClick = onSettingsShareClick,
        onWebDavBackupClick = onWebDavBackupClick,
        onDownloadPathClick = onDownloadPathClick,
        onImageSavePathClick = onImageSavePathClick,
        onClearCacheClick = onClearCacheClick,
        onGithubClick = onGithubClick,
        onTelegramClick = onTelegramClick,
        onTwitterClick = onTwitterClick,
        onDonateClick = onDonateClick,
        onDisclaimerClick = onDisclaimerClick,
        onLicenseClick = onLicenseClick,
        onVerificationClick = onVerificationClick,
        onBuildSourceClick = onBuildSourceClick,
        onBuildFingerprintClick = onBuildFingerprintClick,
        onCheckUpdateClick = onCheckUpdateClick,
        onViewReleaseNotesClick = onViewReleaseNotesClick,
        onVersionClick = onVersionClick,
        onReplayOnboardingClick = onReplayOnboardingClick,
        onTipsClick = onTipsClick,
        onOpenLinksClick = onOpenLinksClick,
        onPrivacyModeChange = onPrivacyModeChange,
        onPrivacyContentAuthenticationChange = onPrivacyContentAuthenticationChange,
        onCrashTrackingChange = onCrashTrackingChange,
        onAnalyticsChange = onAnalyticsChange,
        onEasterEggChange = onEasterEggChange,
        onAutoCheckUpdateChange = onAutoCheckUpdateChange,
        onFeedApiTypeChange = onFeedApiTypeChange,
        onIncrementalTimelineRefreshChange = onIncrementalTimelineRefreshChange,
        onDynamicImagePreviewTextVisibleChange = onDynamicImagePreviewTextVisibleChange,
        onDynamicAllTabHorizontalUserListVisibleChange = onDynamicAllTabHorizontalUserListVisibleChange,
        onDynamicTabVisibilityChange = onDynamicTabVisibilityChange,
        onHomeRefreshCountChange = onHomeRefreshCountChange
    )
    val rootCategoryState = SettingsRootCategoryState(
        privacyModeEnabled = privacyModeEnabled,
        privacyContentAuthenticationEnabled = privacyContentAuthenticationEnabled,
        crashTrackingEnabled = crashTrackingEnabled,
        analyticsEnabled = analyticsEnabled,
        pluginCount = pluginCount,
        customDownloadPath = customDownloadPath,
        customImageSavePath = customImageSavePath,
        cacheSize = cacheSize,
        versionName = versionName,
        easterEggEnabled = easterEggEnabled,
        updateStatusText = updateStatusText,
        isCheckingUpdate = isCheckingUpdate,
        autoCheckUpdateEnabled = autoCheckUpdateEnabled,
        verificationLabel = verificationLabel,
        verificationSubtitle = verificationSubtitle,
        buildSourceValue = buildSourceValue,
        buildSourceSubtitle = buildSourceSubtitle,
        buildFingerprintValue = buildFingerprintValue,
        buildFingerprintCopyValue = buildFingerprintCopyValue,
        buildFingerprintSubtitle = buildFingerprintSubtitle,
        versionClickCount = versionClickCount,
        versionClickThreshold = versionClickThreshold,
        feedApiType = feedApiType,
        incrementalTimelineRefreshEnabled = incrementalTimelineRefreshEnabled,
        dynamicImagePreviewTextVisible = dynamicImagePreviewTextVisible,
        dynamicAllTabHorizontalUserListVisible = dynamicAllTabHorizontalUserListVisible,
        dynamicVisibleTabIds = dynamicVisibleTabIds,
        homeRefreshCount = homeRefreshCount
    )

    LaunchedEffect(focusRequest, searchQuery) {
        val request = focusRequest ?: return@LaunchedEffect
        if (!isSceneSettingsSearchTarget(request.target) || searchQuery.isNotBlank()) {
            return@LaunchedEffect
        }
        val category = resolveSettingsRootCategoryForSearchTarget(request.target) ?: return@LaunchedEffect
        activeRootCategoryName = category.name
        SettingsSearchFocusController.clear(request.token)
    }
    BackHandler(enabled = activeRootCategory != null && searchQuery.isBlank()) {
        activeRootCategoryName = null
    }

    AdaptiveScaffold(
        topBar = {
            Box {
                TopReadabilityChrome(
                    height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                    surfaceColor = AppSurfaceTokens.groupedListContainer(),
                    surfaceAlpha = 0.86f
                )
                AdaptiveTopAppBar(
                    title = activeRootCategory?.title ?: screenTitle,
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (activeRootCategory != null && searchQuery.isBlank()) {
                                    activeRootCategoryName = null
                                } else {
                                    onBack()
                                }
                            }
                        ) {
                            Icon(
                                rememberAppBackIcon(),
                                contentDescription = backLabel
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        containerColor = AppSurfaceTokens.groupedListContainer(),
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        LazyColumn(
            state = listState,
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

            item {
                AnimatedContent(
                    targetState = bodyDestination,
                    transitionSpec = {
                        resolveSettingsRootCategoryContentTransform(
                            animationEnabled = entranceAnimationEnabled,
                            reduceMotion = reduceMotion
                        )
                    },
                    label = "SettingsRootBody"
                ) { destination ->
                    val settled = !transition.isRunning
                    key(destination) {
                    EntranceGroup(startWhen = settled) {
                        when (destination) {
                            SettingsRootBodyDestination.Home -> {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .entrance()
                                    ) {
                                        SettingsRootCategoryListSection(
                                            categories = sectionOrder,
                                            onCategoryClick = { category ->
                                                activeRootCategoryName = category.name
                                            }
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 16.dp)
                                            .entrance()
                                    ) {
                                        SettingsAboutHomeSection(
                                            onGithubClick = onGithubClick,
                                            onTelegramClick = onTelegramClick,
                                            onCheckUpdateClick = onCheckUpdateClick,
                                            onDonateClick = onDonateClick
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 16.dp)
                                            .entrance()
                                    ) {
                                        SettingsBackupHomeSection(
                                            onSettingsShareClick = onSettingsShareClick,
                                            onWebDavBackupClick = onWebDavBackupClick,
                                            onClearCacheClick = onClearCacheClick,
                                            cacheSize = cacheSize
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            is SettingsRootBodyDestination.Category -> {
                                Box(modifier = Modifier.padding(top = 12.dp)) {
                                    SettingsRootCategoryContent(
                                        category = destination.category,
                                        actions = rootCategoryActions,
                                        state = rootCategoryState
                                    )
                                }
                            }
                            SettingsRootBodyDestination.Search -> {
                                Column {
                                    SettingsRootCategoryEntranceSection {
                                    SettingsSearchResultsSection(
                                        results = searchResults,
                                        onResultClick = { result ->
                                            val category = resolveSettingsRootCategoryForSearchTarget(result.target)
                                            if (isSceneSettingsSearchTarget(result.target) && category != null) {
                                                activeRootCategoryName = category.name
                                                onSearchQueryChange("")
                                            } else {
                                                onSearchResultClick(result)
                                            }
                                        }
                                    )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                    }
                }
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
                            // QR code preview: ~16dp → Dialog level (14dp scaled per preset).
                            .clip(AppShapes.container(ContainerLevel.Dialog))
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
