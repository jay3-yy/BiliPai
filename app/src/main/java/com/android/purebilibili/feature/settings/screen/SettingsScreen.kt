package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.util.CacheClearTarget
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.DEFAULT_ANALYTICS_ENABLED
import com.android.purebilibili.core.store.DEFAULT_CRASH_TRACKING_ENABLED
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import com.android.purebilibili.core.ui.LocalBottomBarVisible
import com.android.purebilibili.core.ui.animation.LocalAppNavigationEntranceReady
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.AppNavigationSettings
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CacheUtils
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.util.EasterEggs
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.LogCollector
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.TopReadabilityChrome
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.plugin.PluginManager

import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import kotlinx.coroutines.launch

import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.feature.dynamic.defaultDynamicTabVisibleIds
import com.android.purebilibili.feature.dynamic.resolveDynamicVisibleTabIdsAfterToggle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.feature.settings.screen.CommentFraudHistoryScreen

const val GITHUB_URL = OFFICIAL_GITHUB_URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onSettingsHome: () -> Unit = onBack,
    onOpenSourceLicensesClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onAnimationClick: () -> Unit = {},
    onPlaybackClick: () -> Unit = {},
    onPermissionClick: () -> Unit = {},
    onSpacePrivacyClick: () -> Unit = {},
    onLoginActivityClick: () -> Unit = {},
    onMessageNotificationClick: () -> Unit = {},
    onPluginsClick: () -> Unit = {},
    onSettingsShareClick: () -> Unit = {},
    onWebDavBackupClick: () -> Unit = {},
    onNavigateToBottomBarSettings: () -> Unit = {},
    onTipsClick: () -> Unit = {},
    onKeyboardShortcutsClick: () -> Unit = {},
    onReplayOnboardingClick: () -> Unit = {},
    onCategoryClick: (SettingsRootCategory) -> Unit = {},
    onSearchOpen: () -> Unit = {},
    destination: SettingsNavDestination = SettingsNavDestination.Home,
    mainHazeState: dev.chrisbanes.haze.HazeState? = null,
    forceSinglePaneContent: Boolean = false,
    rootEntranceEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val hapticFeedback = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION)
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val navigationTransitionRunning =
        !LocalAppNavigationEntranceReady.current.value
    val rootEntranceStartWhen = shouldStartSettingsEntrance(
        entranceEnabled = rootEntranceEnabled,
        navigationTransitionRunning = navigationTransitionRunning,
    )
    val versionClickThreshold = EasterEggs.VERSION_EASTER_EGG_THRESHOLD

    // State Collection
    val state by viewModel.state.collectAsStateWithLifecycle()
    val privacyModeEnabled by remember(context) { SettingsManager.getPrivacyModeEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
    val searchSuggestionsEnabled by remember(context) { SettingsManager.getSearchSuggestionsEnabled(context) }.collectAsStateWithLifecycle(initialValue = true)
    val privacyContentAuthenticationEnabled by remember(context) { SettingsManager.getPrivacyContentAuthenticationEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val crashTrackingEnabled by remember(context) { SettingsManager.getCrashTrackingEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_CRASH_TRACKING_ENABLED)
    val analyticsEnabled by remember(context) { SettingsManager.getAnalyticsEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = DEFAULT_ANALYTICS_ENABLED)
    val enhancedDiagnosticLoggingEnabled by remember(context) { SettingsManager.getEnhancedDiagnosticLoggingEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val easterEggEnabled by remember(context) { SettingsManager.getEasterEggEnabled(context) }.collectAsStateWithLifecycle(initialValue = true)
    val customDownloadPath by remember(context) { SettingsManager.getDownloadPath(context) }.collectAsStateWithLifecycle(initialValue = null)
    val downloadExportTreeUri by remember(context) { SettingsManager.getDownloadExportTreeUri(context) }.collectAsStateWithLifecycle(initialValue = null)
    val imageSaveTreeUri by remember(context) { SettingsManager.getImageSaveTreeUri(context) }.collectAsStateWithLifecycle(initialValue = null)
    val autoCacheClearInterval by remember(context) { SettingsManager.getAutoCacheClearInterval(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.AutoCacheClearInterval.NEVER)
    val autoCacheClearThresholdGb by remember(context) { SettingsManager.getAutoCacheClearThresholdGb(context) }
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.DEFAULT_AUTO_CACHE_CLEAR_THRESHOLD_GB
        )
    val feedApiType by remember(context) { SettingsManager.getFeedApiType(context) }.collectAsStateWithLifecycle(initialValue = SettingsManager.FeedApiType.WEB
    )
    val autoCheckUpdateEnabled by remember(context) { SettingsManager.getAutoCheckAppUpdate(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val appUpdateChannel by remember(context) { SettingsManager.getAppUpdateChannel(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.AppUpdateChannel.STABLE)
    val incrementalTimelineRefreshEnabled by remember(context) { SettingsManager.getIncrementalTimelineRefresh(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val dynamicVisibleTabIds by remember(context) { SettingsManager.getDynamicTabVisibleTabs(context) }
        .collectAsStateWithLifecycle(initialValue = defaultDynamicTabVisibleIds)
    val dynamicTabOrder by remember(context) { SettingsManager.getDynamicTabOrder(context) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val dynamicImagePreviewTextVisible by remember(context) { SettingsManager.getDynamicImagePreviewTextVisible(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val dynamicCommentPreviewVisible by remember(context) { com.android.purebilibili.core.store.home.DynamicSettingsStore.observeCommentPreviewVisible(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val dynamicDetailImageLayout by remember(context) { SettingsManager.getDynamicDetailImageLayout(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.peekDynamicDetailImageLayout(context))
    val dynamicTopBarCollapseOnScroll by remember(context) { SettingsManager.getDynamicTopBarCollapseOnScroll(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val dynamicFeedLayoutMode by remember(context) { SettingsManager.getDynamicFeedLayoutMode(context) }
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode.WATERFALL)

    // Local UI State
    var showCacheDialog by remember { mutableStateOf(false) }
    var showCacheAnimation by remember { mutableStateOf(false) }
    var cacheProgress by remember { mutableStateOf<CacheClearProgress?>(null) }
    var cacheClearSucceeded by remember { mutableStateOf(false) }
    var cacheReleasedSize by remember { mutableStateOf("") }
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
    var showLogExportDialog by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    var showImageSavePathDialog by remember { mutableStateOf(false) }
    // [新增] 打赏对话框
    var showDonateDialog by remember { mutableStateOf(false) }
    var showReleaseDisclaimerDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf(context.getString(R.string.settings_update_tap_to_check)) }
    var updateCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var changelogCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    val currentReleaseEvidence = state.currentReleaseEvidence
    val installedApkSha256 = state.installedApkSha256

    // [新增] 黑名单页面状态
    var showBlockedList by remember { mutableStateOf(false) }
    // [新增] 发评反诈页面状态
    var showCommentFraudHistory by remember { mutableStateOf(false) }
    val installedBuildProvenance = remember { readInstalledAppBuildProvenance() }

    // Effects
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
            context.getString(R.string.settings_build_source_local)
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
        installedApkSha256 ?: context.getString(R.string.settings_build_fingerprint_unavailable)
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

    // 设置首页为共享导航材质提供采样；分类页仅在缓存清理弹层显示时采样。
    val localOverlayHazeState = rememberRecoverableHazeState()
    val isSettingsHome = destination == SettingsNavDestination.Home
    val activeHazeState = if (isSettingsHome) {
        mainHazeState ?: localOverlayHazeState
    } else {
        localOverlayHazeState
    }
    val capturePageForHaze = isSettingsHome

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
        Toast.makeText(context, context.getString(R.string.settings_download_folder_set), Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, context.getString(R.string.settings_image_folder_set), Toast.LENGTH_SHORT).show()
    }

    // Callbacks
    val onClearCacheAction = {
        selectedCacheClearTargets = resolveDefaultCacheClearTargets()
        cacheClearSucceeded = false
        cacheReleasedSize = ""
        viewModel.refreshCacheSize()
        showCacheDialog = true
    }
    val onDownloadPathAction = { showPathDialog = true }
    val onImageSavePathAction = { showImageSavePathDialog = true }

    // Logic Callbacks
    val onSearchSuggestionsChange: (Boolean) -> Unit = { enabled ->
        scope.launch { SettingsManager.setSearchSuggestionsEnabled(context, enabled) }
    }
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
            com.android.purebilibili.core.performance.Android17Diagnostics.updateEnabled(context, enabled)
        }
    }
    val onAnalyticsChange: (Boolean) -> Unit = { enabled ->
        scope.launch {
            SettingsManager.setAnalyticsEnabled(context, enabled)
            AnalyticsHelper.setEnabled(enabled)
        }
    }
    val onEnhancedDiagnosticLoggingChange: (Boolean) -> Unit = { enabled ->
        scope.launch {
            SettingsManager.setEnhancedDiagnosticLoggingEnabled(context, enabled)
            Logger.configureEnhancedDiagnosticLogging(context, enabled)
        }
    }
    val onEasterEggChange: (Boolean) -> Unit = { enabled ->
        scope.launch { SettingsManager.setEasterEggEnabled(context, enabled) }
    }
    val onAutoCheckUpdateChange: (Boolean) -> Unit = { enabled ->
        scope.launch { SettingsManager.setAutoCheckAppUpdate(context, enabled) }
    }
    val onAppUpdateChannelChange: (SettingsManager.AppUpdateChannel) -> Unit = { channel ->
        scope.launch { SettingsManager.setAppUpdateChannel(context, channel) }
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

    val onExportLogsAction: () -> Unit = { showLogExportDialog = true }
    val onTelegramClick: () -> Unit = { uriHandler.openUri(OFFICIAL_TELEGRAM_CHANNEL_URL) }
    val onTelegramGroupClick: () -> Unit = { uriHandler.openUri(OFFICIAL_TELEGRAM_GROUP_URL) }
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
    val onCommentFraudHistoryClickAction: () -> Unit = { showCommentFraudHistory = true }
    suspend fun runUpdateCheck(
        silent: Boolean,
        shouldOpenReleaseNotes: Boolean = false
    ) {
        isCheckingUpdate = true
        if (!silent) {
            updateStatusText = context.getString(R.string.settings_update_checking)
        }
        val result = AppUpdateChecker.check(
            currentVersion = com.android.purebilibili.BuildConfig.VERSION_NAME,
            currentVersionCode = com.android.purebilibili.BuildConfig.VERSION_CODE,
            includePrerelease = appUpdateChannel == SettingsManager.AppUpdateChannel.BETA
        )
        result.onSuccess { info ->
            viewModel.recordReleaseEvidence(info)
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
                updateStatusText = context.getString(R.string.settings_update_failed)
                Toast.makeText(context, error.message ?: context.getString(R.string.settings_update_error), Toast.LENGTH_SHORT).show()
            }
        }
        isCheckingUpdate = false
    }
    val onCheckUpdateAction: () -> Unit = {
        if (isCheckingUpdate) {
            Toast.makeText(context, context.getString(R.string.settings_update_in_progress), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, context.getString(R.string.settings_update_in_progress), Toast.LENGTH_SHORT).show()
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
        if (!showCacheAnimation) return@LaunchedEffect
        val startedAt = android.os.SystemClock.elapsedRealtime()
        cacheClearSucceeded = false
        cacheProgress = CacheClearProgress(0L, pendingCacheClearTargets.size.toLong())
        try {
            val before = CacheUtils.getCacheBreakdown(context)
            val clearResult = viewModel.clearCache(pendingCacheClearTargets) { step ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    cacheProgress = CacheClearProgress(
                        current = step.completedTargets.toLong(), total = step.totalTargets.toLong(),
                        message = resolveCacheClearStageMessage(step.target)
                    )
                }
            }
            if (shouldMarkCacheClearAnimationComplete(clearResult.isSuccess)) {
                val after = clearResult.getOrThrow()
                // 磁盘释放量不混入内存估算；任务进度与容量统计采用不同的单位。
                cacheReleasedSize = formatCacheClearBytes((before.reclaimableDiskSize - after.reclaimableDiskSize).coerceAtLeast(0L))
                cacheClearSucceeded = true
                cacheProgress = CacheClearProgress(
                    current = pendingCacheClearTargets.size.toLong(), total = pendingCacheClearTargets.size.toLong(),
                    isComplete = true, clearedSize = cacheReleasedSize
                )
                if (android.os.SystemClock.elapsedRealtime() - startedAt < 150L) {
                    showCacheAnimation = false
                    cacheProgress = null
                }
            } else {
                Toast.makeText(context, resolveCacheClearFailureMessage(clearResult.exceptionOrNull(),
                    context.getString(R.string.settings_cache_clear_failed)), Toast.LENGTH_SHORT).show()
                viewModel.refreshCacheSize()
                showCacheAnimation = false
                cacheProgress = null
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Toast.makeText(context, resolveCacheClearFailureMessage(error), Toast.LENGTH_SHORT).show()
            viewModel.refreshCacheSize()
            showCacheAnimation = false
            cacheProgress = null
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.ensureDiagnosticsLoaded()
    }
    LaunchedEffect(Unit) {
        AnalyticsHelper.logScreenView("SettingsScreen")
    }

    // Dialogs
    if (showLogExportDialog) {
        val rawTraceAvailable = LogCollector.hasRawCrashTrace(context)
        com.android.purebilibili.core.ui.AppAlertDialog(
            onDismissRequest = { showLogExportDialog = false },
            title = { AppText(stringResource(R.string.settings_log_export_title)) },
            text = {
                Column {
                    AppText(
                        stringResource(R.string.settings_log_export_description)
                    )
                    if (rawTraceAvailable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(stringResource(R.string.settings_log_export_raw_warning))
                        com.android.purebilibili.core.ui.AppDialogAction(onClick = {
                            showLogExportDialog = false
                            LogCollector.exportAndShare(context, includeRawCrashTrace = true)
                        }) { AppText(stringResource(R.string.settings_log_export_raw_action)) }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(stringResource(R.string.settings_log_export_raw_unavailable))
                    }
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.AppDialogAction(onClick = {
                    showLogExportDialog = false
                    LogCollector.exportAndShare(context)
                }) { AppText(stringResource(R.string.settings_log_export_standard_action)) }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.AppDialogAction(
                    onClick = { showLogExportDialog = false }
                ) { AppText(stringResource(R.string.common_cancel)) }
            },
        )
    }
    if (showCacheDialog) {
        CacheClearConfirmDialog(
            breakdown = state.cacheBreakdown,
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
                    Toast.makeText(context, context.getString(R.string.settings_cache_select_at_least_one), Toast.LENGTH_SHORT).show()
                } else {
                    pendingCacheClearTargets = selectedCacheClearTargets
                    cacheProgress = CacheClearProgress(0L, selectedCacheClearTargets.size.toLong())
                    showCacheAnimation = true
                }
            },
            onDismiss = { if (!showCacheAnimation) showCacheDialog = false },
            clearing = showCacheAnimation,
            progress = cacheProgress,
            clearSucceeded = cacheClearSucceeded,
            releasedSize = cacheReleasedSize,
            onProgressDismiss = { showCacheAnimation = false; cacheProgress = null }
        )
    }

    if (showPathDialog) {
        com.android.purebilibili.core.ui.AppAlertDialog(
            onDismissRequest = { showPathDialog = false },
            title = { AppText(stringResource(R.string.settings_download_location_title), color = AppContentColors.primaryText()) },
            text = {
                Column {
                    AppText(stringResource(R.string.settings_download_location_default), style = MaterialTheme.typography.labelMedium, color = AppContentColors.secondaryText())
                    AppText(defaultPath.substringAfterLast("Android/"), style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        stringResource(R.string.settings_download_location_export_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.warning()
                    )
                    if (!downloadExportTreeUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(
                            stringResource(R.string.settings_download_location_export_set),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText()
                        )
                    }
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.AppDialogAction(onClick = {
                    showPathDialog = false
                    downloadFolderPicker.launch(null)
                }) { AppText(stringResource(R.string.settings_download_location_choose_export)) }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.AppDialogAction(onClick = {
                    scope.launch {
                        SettingsManager.setDownloadPath(context, null)
                        SettingsManager.setDownloadExportTreeUri(context, null)
                    }
                    showPathDialog = false
                    Toast.makeText(context, context.getString(R.string.settings_download_location_reset_toast), Toast.LENGTH_SHORT).show()
                }) { AppText(stringResource(R.string.common_reset_default)) }
            }
        )
    }
    if (showImageSavePathDialog) {
        com.android.purebilibili.core.ui.AppAlertDialog(
            onDismissRequest = { showImageSavePathDialog = false },
            title = { AppText(stringResource(R.string.settings_image_location_title), color = AppContentColors.primaryText()) },
            text = {
                Column {
                    AppText(
                        stringResource(R.string.settings_image_location_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        stringResource(R.string.settings_image_location_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.warning()
                    )
                    if (!imageSaveTreeUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(
                            stringResource(R.string.settings_image_location_custom_set),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText()
                        )
                    }
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.AppDialogAction(onClick = {
                    showImageSavePathDialog = false
                    imageSaveFolderPicker.launch(null)
                }) { AppText(stringResource(R.string.settings_image_location_choose)) }
            },
            dismissButton = {
                com.android.purebilibili.core.ui.AppDialogAction(onClick = {
                    scope.launch {
                        SettingsManager.setImageSaveTreeUri(context, null)
                    }
                    showImageSavePathDialog = false
                    Toast.makeText(context, context.getString(R.string.settings_image_location_reset_toast), Toast.LENGTH_SHORT).show()
                }) { AppText(stringResource(R.string.common_reset_default)) }
            }
        )
    }

    if (showEasterEggDialog) {
        com.android.purebilibili.core.ui.AppAlertDialog(
            onDismissRequest = { showEasterEggDialog = false; versionClickCount = 0 },
            title = { AppText(stringResource(R.string.settings_easter_egg_title), fontWeight = FontWeight.Bold) },
            text = { AppText(stringResource(R.string.settings_easter_egg_message)) },
            confirmButton = { com.android.purebilibili.core.ui.AppDialogAction(onClick = { showEasterEggDialog = false; versionClickCount = 0 }) { AppText(stringResource(R.string.common_got_it)) } }
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
        AppUpdateDialogHost(
            update = info,
            onDismissRequest = { updateCheckResult = null },
        )
    }

    changelogCheckResult?.let { info ->
        AppUpdateDialogHost(
            update = info,
            showReleaseNotesOnly = true,
            onDismissRequest = { changelogCheckResult = null },
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
            Toast.makeText(context, context.getString(R.string.settings_open_system_settings_failed), Toast.LENGTH_SHORT).show()
        }
    }
    val settingsBackTarget = resolveSettingsBackTarget(
        showCacheAnimation = showCacheAnimation,
        showCacheDialog = showCacheDialog,
        showPathDialog = showPathDialog,
        showImageSavePathDialog = showImageSavePathDialog,
        showEasterEggDialog = showEasterEggDialog,
        showDonateDialog = showDonateDialog,
        showReleaseDisclaimerDialog = showReleaseDisclaimerDialog,
        showUpdateResult = updateCheckResult != null,
        showChangelogResult = changelogCheckResult != null,
        showBlockedList = showBlockedList,
    )
    SettingsLocalBackHandler(enabled = settingsBackTarget != SettingsBackTarget.NONE) {
        when (settingsBackTarget) {
            SettingsBackTarget.NONE -> Unit
            SettingsBackTarget.CACHE_ANIMATION -> {
                if (cacheProgress?.isComplete == true) {
                    showCacheAnimation = false
                    cacheProgress = null
                }
            }
            SettingsBackTarget.CACHE_DIALOG -> showCacheDialog = false
            SettingsBackTarget.PATH_DIALOG -> showPathDialog = false
            SettingsBackTarget.IMAGE_SAVE_PATH_DIALOG -> showImageSavePathDialog = false
            SettingsBackTarget.EASTER_EGG_DIALOG -> {
                showEasterEggDialog = false
                versionClickCount = 0
            }
            SettingsBackTarget.DONATE_DIALOG -> showDonateDialog = false
            SettingsBackTarget.RELEASE_DISCLAIMER_DIALOG -> showReleaseDisclaimerDialog = false
            SettingsBackTarget.UPDATE_RESULT -> updateCheckResult = null
            SettingsBackTarget.CHANGELOG_RESULT -> changelogCheckResult = null
            SettingsBackTarget.BLOCKED_LIST -> showBlockedList = false
        }
    }

    // 页面跳转逻辑
    CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
        if (showBlockedList) {
            BlockedListScreen(onBack = { showBlockedList = false })
        } else if (showCommentFraudHistory) {
            CommentFraudHistoryScreen(onBack = { showCommentFraudHistory = false })
        } else {
        // Layout Switching
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (capturePageForHaze) Modifier.hazeSourceCompat(state = activeHazeState)
                    else Modifier,
                )
        ) {
            if (shouldRenderSettingsSinglePaneContent(
                    widthDp = configuration.screenWidthDp,
                    forceSinglePaneContent = forceSinglePaneContent
                )
            ) {
                MobileSettingsNavLayout(
                    destination = destination,
                    rootEntranceEnabled = rootEntranceEnabled,
                    rootEntranceStartWhen = rootEntranceStartWhen,
                    onBack = onBack,
                    onSettingsHome = onSettingsHome,
                    onCategoryClick = onCategoryClick,
                    onSearchOpen = onSearchOpen,
                    onAppearanceClick = onAppearanceClick,
                    onHomeClick = onHomeClick,
                    onAnimationClick = onAnimationClick,
                    onPlaybackClick = onPlaybackClick,
                    onPermissionClick = onPermissionClick,
                    onSpacePrivacyClick = onSpacePrivacyClick,
                    onLoginActivityClick = onLoginActivityClick,
                    onMessageNotificationClick = onMessageNotificationClick,
                    onNavigateToBottomBarSettings = onNavigateToBottomBarSettings,
                    onTipsClick = onTipsClick,
                    onKeyboardShortcutsClick = onKeyboardShortcutsClick,
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
                    onTelegramClick = onTelegramClick,
                    onTelegramGroupClick = onTelegramGroupClick,
                    onTwitterClick = onTwitterClick,
                    onSettingsShareClick = onSettingsShareClick,
                    onWebDavBackupClick = onWebDavBackupClick,
                    onDownloadPathClick = onDownloadPathAction,
                    onImageSavePathClick = onImageSavePathAction,
                    onClearCacheClick = onClearCacheAction,
                    onAutoCacheClearIntervalChange = { interval ->
                        scope.launch { SettingsManager.setAutoCacheClearInterval(context, interval) }
                    },
                    onAutoCacheClearThresholdChange = { thresholdGb ->
                        scope.launch {
                            SettingsManager.setAutoCacheClearThresholdGb(context, thresholdGb)
                        }
                    },
                    onDonateClick = { showDonateDialog = true },
                    onOpenLinksClick = onOpenLinksAction,
                    onBlockedListClick = onBlockedListClickAction,
                    onCommentFraudHistoryClick = onCommentFraudHistoryClickAction,
                    onPrivacyModeChange = onPrivacyModeChange,
                    onSearchSuggestionsChange = onSearchSuggestionsChange,
                    onPrivacyContentAuthenticationChange = onPrivacyContentAuthenticationChange,
                    onCrashTrackingChange = onCrashTrackingChange,
                    onAnalyticsChange = onAnalyticsChange,
                    onEnhancedDiagnosticLoggingChange = onEnhancedDiagnosticLoggingChange,
                    onEasterEggChange = onEasterEggChange,
                    onAutoCheckUpdateChange = onAutoCheckUpdateChange,
                    onAppUpdateChannelChange = onAppUpdateChannelChange,
                    privacyModeEnabled = privacyModeEnabled,
                    searchSuggestionsEnabled = searchSuggestionsEnabled,
                    customDownloadPath = downloadExportTreeUri ?: customDownloadPath,
                    customImageSavePath = imageSaveTreeUri,
                    cacheSize = state.cacheSize,
                    autoCacheClearInterval = autoCacheClearInterval,
                    autoCacheClearThresholdGb = autoCacheClearThresholdGb,
                    crashTrackingEnabled = crashTrackingEnabled,
                    analyticsEnabled = analyticsEnabled,
                    enhancedDiagnosticLoggingEnabled = enhancedDiagnosticLoggingEnabled,
                    pluginCount = PluginManager.getEnabledCount(),
                    versionName = com.android.purebilibili.BuildConfig.VERSION_NAME,
                    appIcon = state.appIcon,
                    versionClickCount = versionClickCount,
                    versionClickThreshold = versionClickThreshold,
                    easterEggEnabled = easterEggEnabled,
                    updateStatusText = updateStatusText,
                    isCheckingUpdate = isCheckingUpdate,
                    autoCheckUpdateEnabled = autoCheckUpdateEnabled,
                    appUpdateChannel = appUpdateChannel,
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
                                context.getString(R.string.settings_feed_api_changed, localizedFeedApiTypeLabel(context, type)),
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
                    dynamicCommentPreviewVisible = dynamicCommentPreviewVisible,
                    onDynamicCommentPreviewVisibleChange = { visible ->
                        scope.launch {
                            com.android.purebilibili.core.store.home.DynamicSettingsStore
                                .setCommentPreviewVisible(context, visible)
                        }
                    },
                    onDynamicImagePreviewTextVisibleChange = { visible ->
                        scope.launch {
                            SettingsManager.setDynamicImagePreviewTextVisible(context, visible)
                        }
                    },
                    dynamicDetailImageLayout = dynamicDetailImageLayout,
                    onDynamicDetailImageLayoutChange = { layout ->
                        scope.launch {
                            SettingsManager.setDynamicDetailImageLayout(context, layout)
                        }
                    },
                    dynamicTopBarCollapseOnScroll = dynamicTopBarCollapseOnScroll,
                    onDynamicTopBarCollapseOnScrollChange = { enabled ->
                        scope.launch {
                            SettingsManager.setDynamicTopBarCollapseOnScroll(context, enabled)
                        }
                    },
                    dynamicFeedLayoutMode = dynamicFeedLayoutMode,
                    onDynamicFeedLayoutModeChange = { mode ->
                        scope.launch {
                            SettingsManager.setDynamicFeedLayoutMode(context, mode)
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
                    dynamicTabOrder = dynamicTabOrder,
                    onDynamicTabOrderChange = { order ->
                        scope.launch {
                            SettingsManager.setDynamicTabOrder(context, order)
                        }
                    },
                )
            }
        }
        }
    }


}

internal fun shouldMarkCacheClearAnimationComplete(clearSucceeded: Boolean): Boolean = clearSucceeded

internal fun resolveCacheClearFailureMessage(
    error: Throwable?,
    fallbackMessage: String = "清理缓存失败，请稍后重试",
): String {
    return error?.message?.takeIf { it.isNotBlank() } ?: fallbackMessage
}

private fun localizedFeedApiTypeLabel(
    context: android.content.Context,
    type: SettingsManager.FeedApiType,
): String = context.getString(
    when (type) {
        SettingsManager.FeedApiType.WEB -> R.string.settings_feed_api_web
        SettingsManager.FeedApiType.MOBILE -> R.string.settings_feed_api_mobile
        SettingsManager.FeedApiType.MERGED -> R.string.settings_feed_api_merged
    }
)

@Composable
internal fun SettingsCategoryHeader(title: String) {
    AppText(
        text = title,
        color = AppContentColors.secondaryText(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = AppSpacingTokens.Small)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSettingsNavLayout(
    destination: SettingsNavDestination,
    rootEntranceEnabled: Boolean,
    rootEntranceStartWhen: Boolean,
    onBack: () -> Unit,
    onSettingsHome: () -> Unit,
    onCategoryClick: (SettingsRootCategory) -> Unit,
    onSearchOpen: () -> Unit,
    onAppearanceClick: () -> Unit,
    onHomeClick: () -> Unit,
    onAnimationClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onSpacePrivacyClick: () -> Unit,
    onLoginActivityClick: () -> Unit,
    onMessageNotificationClick: () -> Unit,
    onNavigateToBottomBarSettings: () -> Unit,
    onTipsClick: () -> Unit,
    onKeyboardShortcutsClick: () -> Unit,
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
    onTelegramGroupClick: () -> Unit = {},
    onTwitterClick: () -> Unit,
    onSettingsShareClick: () -> Unit,
    onWebDavBackupClick: () -> Unit,
    onDownloadPathClick: () -> Unit,
    onImageSavePathClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onAutoCacheClearIntervalChange: (SettingsManager.AutoCacheClearInterval) -> Unit,
    onAutoCacheClearThresholdChange: (Int) -> Unit,
    onDonateClick: () -> Unit,
    onOpenLinksClick: () -> Unit,
    onBlockedListClick: () -> Unit,
    onCommentFraudHistoryClick: () -> Unit,
    onPrivacyModeChange: (Boolean) -> Unit,
    onSearchSuggestionsChange: (Boolean) -> Unit,
    onPrivacyContentAuthenticationChange: (Boolean) -> Unit,
    onCrashTrackingChange: (Boolean) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onEnhancedDiagnosticLoggingChange: (Boolean) -> Unit,
    onEasterEggChange: (Boolean) -> Unit,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    onAppUpdateChannelChange: (SettingsManager.AppUpdateChannel) -> Unit,
    privacyModeEnabled: Boolean,
    searchSuggestionsEnabled: Boolean,
    privacyContentAuthenticationEnabled: Boolean,
    customDownloadPath: String?,
    customImageSavePath: String?,
    cacheSize: String,
    autoCacheClearInterval: SettingsManager.AutoCacheClearInterval,
    autoCacheClearThresholdGb: Int,
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
    enhancedDiagnosticLoggingEnabled: Boolean,
    pluginCount: Int,
    versionName: String,
    appIcon: String,
    versionClickCount: Int,
    versionClickThreshold: Int,
    easterEggEnabled: Boolean,
    updateStatusText: String,
    isCheckingUpdate: Boolean,
    autoCheckUpdateEnabled: Boolean,
    appUpdateChannel: SettingsManager.AppUpdateChannel,
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
    dynamicCommentPreviewVisible: Boolean,
    onDynamicCommentPreviewVisibleChange: (Boolean) -> Unit,
    dynamicDetailImageLayout: SettingsManager.DynamicDetailImageLayout,
    onDynamicDetailImageLayoutChange: (SettingsManager.DynamicDetailImageLayout) -> Unit,
    dynamicTopBarCollapseOnScroll: Boolean,
    onDynamicTopBarCollapseOnScrollChange: (Boolean) -> Unit,
    dynamicFeedLayoutMode: com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode,
    onDynamicFeedLayoutModeChange: (com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode) -> Unit,
    dynamicVisibleTabIds: Set<String>,
    onDynamicTabVisibilityChange: (String) -> Unit,
    dynamicTabOrder: List<String>,
    onDynamicTabOrderChange: (List<String>) -> Unit,
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val sectionOrder = remember { resolveSettingsRootCategoryOrder() }
    val bottomBarVisible = LocalBottomBarVisible.current
    val bottomInset = resolveSettingsContentBottomPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        bottomBarVisible = bottomBarVisible,
        isBottomBarFloating = isBottomBarFloating,
        bottomBarLabelMode = bottomBarLabelMode,
        isTablet = windowSizeClass.isTablet,
    )
    val screenTitle = resolveSettingsNavDestinationTitle(destination)
    val backLabel = stringResource(R.string.common_back)
    val rootCategoryActions = SettingsRootCategoryActions(
        onAppearanceClick = onAppearanceClick,
        onHomeClick = onHomeClick,
        onAnimationClick = onAnimationClick,
        onPlaybackClick = onPlaybackClick,
        onBottomBarClick = onNavigateToBottomBarSettings,
        onPermissionClick = onPermissionClick,
        onSpacePrivacyClick = onSpacePrivacyClick,
        onLoginActivityClick = onLoginActivityClick,
        onMessageNotificationClick = onMessageNotificationClick,
        onBlockedListClick = onBlockedListClick,
        onCommentFraudHistoryClick = onCommentFraudHistoryClick,
        onPluginsClick = onPluginsClick,
        onExportLogsClick = onExportLogsClick,
        onSettingsShareClick = onSettingsShareClick,
        onWebDavBackupClick = onWebDavBackupClick,
        onDownloadPathClick = onDownloadPathClick,
        onImageSavePathClick = onImageSavePathClick,
        onClearCacheClick = onClearCacheClick,
        onAutoCacheClearIntervalChange = onAutoCacheClearIntervalChange,
        onAutoCacheClearThresholdChange = onAutoCacheClearThresholdChange,
        onGithubClick = onGithubClick,
        onTelegramClick = onTelegramClick,
        onTelegramGroupClick = onTelegramGroupClick,
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
        onKeyboardShortcutsClick = onKeyboardShortcutsClick,
        onOpenLinksClick = onOpenLinksClick,
        onPrivacyModeChange = onPrivacyModeChange,
        onSearchSuggestionsChange = onSearchSuggestionsChange,
        onPrivacyContentAuthenticationChange = onPrivacyContentAuthenticationChange,
        onCrashTrackingChange = onCrashTrackingChange,
        onAnalyticsChange = onAnalyticsChange,
        onEnhancedDiagnosticLoggingChange = onEnhancedDiagnosticLoggingChange,
        onEasterEggChange = onEasterEggChange,
        onAutoCheckUpdateChange = onAutoCheckUpdateChange,
        onAppUpdateChannelChange = onAppUpdateChannelChange,
        onFeedApiTypeChange = onFeedApiTypeChange,
        onIncrementalTimelineRefreshChange = onIncrementalTimelineRefreshChange,
        onDynamicImagePreviewTextVisibleChange = onDynamicImagePreviewTextVisibleChange,
        onDynamicCommentPreviewVisibleChange = onDynamicCommentPreviewVisibleChange,
        onDynamicDetailImageLayoutChange = onDynamicDetailImageLayoutChange,
        onDynamicTopBarCollapseOnScrollChange = onDynamicTopBarCollapseOnScrollChange,
        onDynamicFeedLayoutModeChange = onDynamicFeedLayoutModeChange,
        onDynamicTabVisibilityChange = onDynamicTabVisibilityChange,
        onDynamicTabOrderChange = onDynamicTabOrderChange,
    )
    val rootCategoryState = SettingsRootCategoryState(
        privacyModeEnabled = privacyModeEnabled,
        searchSuggestionsEnabled = searchSuggestionsEnabled,
        privacyContentAuthenticationEnabled = privacyContentAuthenticationEnabled,
        crashTrackingEnabled = crashTrackingEnabled,
        analyticsEnabled = analyticsEnabled,
        enhancedDiagnosticLoggingEnabled = enhancedDiagnosticLoggingEnabled,
        pluginCount = pluginCount,
        customDownloadPath = customDownloadPath,
        customImageSavePath = customImageSavePath,
        cacheSize = cacheSize,
        autoCacheClearInterval = autoCacheClearInterval,
        autoCacheClearThresholdGb = autoCacheClearThresholdGb,
        versionName = versionName,
        appIcon = appIcon,
        easterEggEnabled = easterEggEnabled,
        updateStatusText = updateStatusText,
        isCheckingUpdate = isCheckingUpdate,
        autoCheckUpdateEnabled = autoCheckUpdateEnabled,
        appUpdateChannel = appUpdateChannel,
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
        dynamicCommentPreviewVisible = dynamicCommentPreviewVisible,
        dynamicDetailImageLayout = dynamicDetailImageLayout,
        dynamicTopBarCollapseOnScroll = dynamicTopBarCollapseOnScroll,
        dynamicFeedLayoutMode = dynamicFeedLayoutMode,
        dynamicVisibleTabIds = dynamicVisibleTabIds,
        dynamicTabOrder = dynamicTabOrder,
    )

    @Composable
    fun SettingsRootContent() {
        when (destination) {
            SettingsNavDestination.Home -> {
                Column {
                    SettingsHomeSearchEntry(onClick = onSearchOpen)
                    Box(modifier = Modifier.padding(top = 8.dp).entrance()) {
                        SettingsRootCategoryListSection(
                            categories = sectionOrder,
                            onCategoryClick = onCategoryClick,
                            onDonateClick = onDonateClick,
                        )
                    }
                }
            }
            is SettingsNavDestination.Category -> {
                Column {
                    SettingsCategoryBreadcrumb(
                        title = screenTitle,
                        onSettingsHome = onSettingsHome,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    SettingsRootCategoryContent(
                        category = destination.category,
                        actions = rootCategoryActions,
                        state = rootCategoryState,
                    )
                }
            }
            SettingsNavDestination.Search -> Unit
        }
    }

    com.android.purebilibili.feature.settings.ui.SettingsPageScaffold(
        title = screenTitle,
        onBack = onBack,
        backContentDescription = backLabel,
        bottomContentPadding = bottomInset,
        topBarStyle = when {
            destination == SettingsNavDestination.Home &&
                com.android.purebilibili.core.ui.isMiuixNonGlassEnabled() -> {
                com.android.purebilibili.core.ui.AppTopBarStyle.LARGE
            }
            destination == SettingsNavDestination.Home -> {
                com.android.purebilibili.core.ui.AppTopBarStyle.SMALL
            }
            else -> {
                com.android.purebilibili.core.ui.AppTopBarStyle.CENTERED
            }
        },
    ) {
        if (rootEntranceEnabled) {
            EntranceGroup(startWhen = rootEntranceStartWhen) {
                SettingsRootContent()
            }
        } else {
            SettingsRootContent()
        }
    }
}
