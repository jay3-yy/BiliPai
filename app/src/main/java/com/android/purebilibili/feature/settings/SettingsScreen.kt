package com.android.purebilibili.feature.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CacheUtils
import com.android.purebilibili.core.util.CrashReporter
import com.android.purebilibili.core.util.EasterEggs
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.LogCollector
import com.android.purebilibili.core.plugin.PluginManager

import dev.chrisbanes.haze.haze
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import kotlinx.coroutines.launch

import com.android.purebilibili.core.ui.components.IOSSectionTitle

const val GITHUB_URL = "https://github.com/jay3-yy/BiliPai/"

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
    onNavigateToBottomBarSettings: () -> Unit = {},
    onReplayOnboardingClick: () -> Unit = {},
    mainHazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val windowSizeClass = LocalWindowSizeClass.current
    
    // State Collection
    val state by viewModel.state.collectAsState()
    val privacyModeEnabled by SettingsManager.getPrivacyModeEnabled(context).collectAsState(initial = false)
    val crashTrackingEnabled by SettingsManager.getCrashTrackingEnabled(context).collectAsState(initial = true)
    val analyticsEnabled by SettingsManager.getAnalyticsEnabled(context).collectAsState(initial = true)
    val easterEggEnabled by SettingsManager.getEasterEggEnabled(context).collectAsState(initial = true)
    val customDownloadPath by SettingsManager.getDownloadPath(context).collectAsState(initial = null)
    
    // Local UI State
    var showCacheDialog by remember { mutableStateOf(false) }
    var showCacheAnimation by remember { mutableStateOf(false) }
    var cacheProgress by remember { mutableStateOf<CacheClearProgress?>(null) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    
    // Haze State for this screen
    val settingsHazeState = remember { dev.chrisbanes.haze.HazeState() }

    // Directory Picker Logic
    val defaultPath = remember { SettingsManager.getDefaultDownloadPath(context) }
    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { selectedUri ->
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
            scope.launch {
                SettingsManager.setDownloadPath(context, selectedUri.toString())
            }
            Toast.makeText(context, "下载路径已更新", Toast.LENGTH_SHORT).show()
        }
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
    
    val onVersionClickAction: () -> Unit = {
        versionClickCount++
        val message = EasterEggs.getVersionClickMessage(versionClickCount)
        if (EasterEggs.isVersionEasterEggTriggered(versionClickCount)) {
            showEasterEggDialog = true
        } else if (versionClickCount >= 3) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    val onExportLogsAction: () -> Unit = { LogCollector.exportAndShare(context) }
    val onTelegramClick: () -> Unit = { uriHandler.openUri("https://t.me/BiliPai") }
    val onTwitterClick: () -> Unit = { uriHandler.openUri("https://x.com/YangY_0x00") }
    val onGithubClick: () -> Unit = { uriHandler.openUri(GITHUB_URL) }

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
            viewModel.clearCache()
            cacheProgress = CacheClearProgress(
                current = totalSize,
                total = totalSize,
                isComplete = true,
                clearedSize = clearedSizeStr
            )
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
                    Text(" 默认位置随应用卸载而删除，选择自定义位置可保留下载文件", style = MaterialTheme.typography.bodySmall, color = com.android.purebilibili.core.theme.iOSOrange)
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = { showPathDialog = false; directoryPicker.launch(null) }) { Text("选择自定义目录") }
            },
            dismissButton = { 
                com.android.purebilibili.core.ui.IOSDialogAction(onClick = { 
                    scope.launch { SettingsManager.setDownloadPath(context, null) }
                    showPathDialog = false
                    Toast.makeText(context, "已重置为默认路径", Toast.LENGTH_SHORT).show()
                }) { Text("使用默认") } 
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

    // Layout Switching
    Box(
        modifier = Modifier
            .fillMaxSize()
            .haze(state = settingsHazeState)
    ) {
        if (windowSizeClass.shouldUseSplitLayout) {
            TabletSettingsLayout(
                onBack = onBack,
                onAppearanceClick = onAppearanceClick,
                onPlaybackClick = onPlaybackClick,
                onPermissionClick = onPermissionClick,
                onPluginsClick = onPluginsClick,
                onExportLogsClick = onExportLogsAction,
                onLicenseClick = onOpenSourceLicensesClick,
                onGithubClick = onGithubClick,
                onVersionClick = onVersionClickAction,
                onReplayOnboardingClick = onReplayOnboardingClick,
                onTelegramClick = onTelegramClick,
                onTwitterClick = onTwitterClick,
                onDownloadPathClick = onDownloadPathAction,
                onClearCacheClick = onClearCacheAction,
                onPrivacyModeChange = onPrivacyModeChange,
                onCrashTrackingChange = onCrashTrackingChange,
                onAnalyticsChange = onAnalyticsChange,
                onEasterEggChange = onEasterEggChange,
                privacyModeEnabled = privacyModeEnabled,
                customDownloadPath = customDownloadPath,
                cacheSize = state.cacheSize,
                crashTrackingEnabled = crashTrackingEnabled,
                analyticsEnabled = analyticsEnabled,
                pluginCount = PluginManager.getEnabledCount(),
                versionName = com.android.purebilibili.BuildConfig.VERSION_NAME,
                easterEggEnabled = easterEggEnabled
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
                onGithubClick = onGithubClick,
                onVersionClick = onVersionClickAction,
                onReplayOnboardingClick = onReplayOnboardingClick,
                onTelegramClick = onTelegramClick,
                onTwitterClick = onTwitterClick,
                onDownloadPathClick = onDownloadPathAction,
                onClearCacheClick = onClearCacheAction,
                onPrivacyModeChange = onPrivacyModeChange,
                onCrashTrackingChange = onCrashTrackingChange,
                onAnalyticsChange = onAnalyticsChange,
                onEasterEggChange = onEasterEggChange,
                privacyModeEnabled = privacyModeEnabled,
                customDownloadPath = customDownloadPath,
                cacheSize = state.cacheSize,
                crashTrackingEnabled = crashTrackingEnabled,
                analyticsEnabled = analyticsEnabled,
                pluginCount = PluginManager.getEnabledCount(),
                versionName = com.android.purebilibili.BuildConfig.VERSION_NAME,
                easterEggEnabled = easterEggEnabled
            )
        }
        
        // Onboarding Bottom Sheet (Shared)

    }
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
    onPluginsClick: () -> Unit,
    onExportLogsClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onGithubClick: () -> Unit,
    onVersionClick: () -> Unit,
    onReplayOnboardingClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onTwitterClick: () -> Unit,
    onDownloadPathClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    
    // Logic Callbacks
    onPrivacyModeChange: (Boolean) -> Unit,
    onCrashTrackingChange: (Boolean) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onEasterEggChange: (Boolean) -> Unit,
    
    // State
    privacyModeEnabled: Boolean,
    customDownloadPath: String?,
    cacheSize: String,
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
    pluginCount: Int,
    versionName: String,
    easterEggEnabled: Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Outlined.ChevronBackward, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            item { IOSSectionTitle("关注作者") }
            item { 
                FollowAuthorSection(onTelegramClick, onTwitterClick)  
            }
            
            item { IOSSectionTitle("常规") }
            item { 
                GeneralSection(
                    onAppearanceClick = onAppearanceClick,
                    onPlaybackClick = onPlaybackClick,
                    onBottomBarClick = onNavigateToBottomBarSettings
                )
            }
            
            item { IOSSectionTitle("隐私与安全") }
            item { 
                PrivacySection(privacyModeEnabled, onPrivacyModeChange, onPermissionClick)
            }
            
            item { IOSSectionTitle("数据与存储") }
            item { 
                DataStorageSection(customDownloadPath, cacheSize, onDownloadPathClick, onClearCacheClick)
            }
            
            item { IOSSectionTitle("开发者选项") }
            item {
                DeveloperSection(
                    crashTrackingEnabled, analyticsEnabled, pluginCount,
                    onCrashTrackingChange, onAnalyticsChange,
                    onPluginsClick, onExportLogsClick
                )
            }
            
            item { IOSSectionTitle("关于") }
            item {
                AboutSection(
                    versionName, easterEggEnabled,
                    onLicenseClick, onGithubClick,
                    onVersionClick, onReplayOnboardingClick,
                    onEasterEggChange
                )
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}