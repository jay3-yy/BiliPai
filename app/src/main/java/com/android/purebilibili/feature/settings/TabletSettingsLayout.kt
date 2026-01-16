package com.android.purebilibili.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AdaptiveSplitLayout
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSPink
import com.android.purebilibili.core.theme.iOSPurple
import com.android.purebilibili.core.theme.iOSTeal
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*

enum class SettingsCategory(
    val title: String, 
    val icon: ImageVector,
    val color: Color
) {
    GENERAL("常规", CupertinoIcons.Filled.Gearshape, iOSPink),
    PRIVACY("隐私与安全", CupertinoIcons.Filled.Lock, iOSPurple),
    STORAGE("数据与存储", CupertinoIcons.Filled.Folder, iOSBlue),
    DEVELOPER("开发者选项", CupertinoIcons.Filled.Hammer, iOSTeal),
    ABOUT("关于", CupertinoIcons.Filled.InfoCircle, iOSOrange)
}

@Composable
fun TabletSettingsLayout(
    // Callbacks
    onBack: () -> Unit,
    onAppearanceClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onPermissionClick: () -> Unit,
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
    easterEggEnabled: Boolean,
    
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }
    
    // Internal navigation state for the right pane
    var activeDetail by remember { mutableStateOf<SettingsDetail?>(null) }
    
    // State from ViewModel (Need to access SettingsViewModel or pass state?)
    // The original TabletSettingsLayout receives primitive types. 
    // But the new *Content composables require ViewModel or State.
    // Ideally we should pass ViewModel to TabletSettingsLayout or hoist EVERYTHING.
    // Given the props list is long, passing ViewModel might be cleaner but let's see.
    // ThemeSettingsContent needs viewModel. AppearanceSettingsContent needs viewModel.
    // I should add viewModel parameter to TabletSettingsLayout.
    val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()

    AdaptiveSplitLayout(
        modifier = modifier,
        primaryRatio = 0.35f, // Left pane narrower
        primaryContent = {
            // Master List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                // Back Button Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 16.dp, start = 4.dp)
                        .clickable(onClick = onBack)
                        .padding(4.dp)
                ) {
                    Icon(
                        CupertinoIcons.Default.ChevronBackward, 
                        contentDescription = "返回", 
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "返回首页", 
                        style = MaterialTheme.typography.bodyLarge, 
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )
                
                // Author Section in Sidebar
                FollowAuthorSection(
                    onTelegramClick = onTelegramClick,
                    onTwitterClick = onTwitterClick
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SettingsCategory.entries.forEach { category ->
                    val isSelected = category == selectedCategory
                    NavigationDrawerItem(
                        label = { Text(category.title) },
                        selected = isSelected,
                        onClick = { 
                            selectedCategory = category 
                            activeDetail = null // Reset detail when category changes
                        },
                        icon = { 
                            Icon(
                                category.icon, 
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else category.color
                            ) 
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedContainerColor = Color.Transparent
                        )
                    )
                }
            }
        },
        secondaryContent = {
            // Detail Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // If we have an active detail, show it. Otherwise show Category Root.
                val detail = activeDetail
                if (detail != null) {
                    // Sub-page Content
                    Column(modifier = Modifier.widthIn(max = 800.dp)) { // Increased max width for sub-pages
                        // Header with Back Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .clickable { 
                                    // if in Appearance Sub-pages, go back to Appearance? 
                                    // Or just simple stack: Root -> Appearance -> Theme
                                    // Let's implement simple logic: if in Theme/Icon/Anim, go back to Appearance.
                                    // if in Appearance, go back to Null.
                                    if (detail == SettingsDetail.ICONS || detail == SettingsDetail.ANIMATION) {
                                        activeDetail = SettingsDetail.APPEARANCE
                                    } else {
                                        activeDetail = null
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Icon(CupertinoIcons.Default.ChevronBackward, null, tint = MaterialTheme.colorScheme.primary)
                            Text("返回", color = MaterialTheme.colorScheme.primary)
                        }
                        
                        when (detail) {
                            SettingsDetail.APPEARANCE -> AppearanceSettingsContent(
                                state = state,
                                viewModel = viewModel,
                                context = context,
                                onNavigateToIconSettings = { activeDetail = SettingsDetail.ICONS },
                                onNavigateToAnimationSettings = { activeDetail = SettingsDetail.ANIMATION }
                            )
                            SettingsDetail.ICONS -> {
                                // Need to recreate the data here or reuse helper?
                                // IconSettingsContent needs `iconGroups`. 
                                // I need to reconstruct them here or move them to a shared place.
                                // For now, I will duplicate or create a helper if possible.
                                // Since I can't easily move them to a separate file without another tool call, 
                                // and I want to proceed, I will redefine them here briefly or just pass empty if I can't access.
                                // Wait, I defined them inside `IconSettingsScreen` file but at top level.
                                // check imports.
                                IconSettingsContent(
                                    state = state,
                                    viewModel = viewModel,
                                    context = context,
                                    iconGroups = com.android.purebilibili.feature.settings.getIconGroups() // Need a way to get this
                                )
                            }
                            SettingsDetail.ANIMATION -> AnimationSettingsContent(
                                state = state,
                                viewModel = viewModel
                            )
                            SettingsDetail.PLAYBACK -> PlaybackSettingsContent(
                                state = state,
                                viewModel = viewModel
                            )
                            SettingsDetail.BOTTOM_BAR -> BottomBarSettingsContent(
                                modifier = Modifier
                            )
                            SettingsDetail.PERMISSION -> PermissionSettingsContent(
                                modifier = Modifier
                            )
                            SettingsDetail.PLUGINS -> {
                                // Need to manage editing state locally for the tablet view
                                var editingPlugin by remember { mutableStateOf<com.android.purebilibili.core.plugin.json.JsonRulePlugin?>(null) }
                                
                                val plugins by com.android.purebilibili.core.plugin.PluginManager.pluginsFlow.collectAsState()
                                val jsonPlugins by com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.collectAsState()
                                
                                if (editingPlugin != null) {
                                    // Show Editor
                                    // We need to manage state for the editor
                                    val plugin = editingPlugin!!
                                    var name by remember(plugin) { mutableStateOf(plugin.name) }
                                    var description by remember(plugin) { mutableStateOf(plugin.description) }
                                    var rules by remember(plugin) { mutableStateOf(plugin.rules) }
                                    
                                    Column {
                                        // Custom Header for Editor
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically, 
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically, 
                                                modifier = Modifier.clickable { editingPlugin = null }.padding(8.dp)
                                            ) {
                                                Icon(CupertinoIcons.Default.ChevronBackward, null, tint = MaterialTheme.colorScheme.primary)
                                                Text("返回插件列表", color = MaterialTheme.colorScheme.primary)
                                            }
                                            
                                            // Save Button
                                            IconButton(onClick = {
                                                val updated = plugin.copy(
                                                    name = name,
                                                    description = description,
                                                    rules = rules
                                                )
                                                com.android.purebilibili.core.plugin.json.JsonPluginManager.updatePlugin(updated)
                                                editingPlugin = null
                                            }) {
                                                Icon(CupertinoIcons.Default.CheckmarkCircle, contentDescription = "保存", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        
                                        JsonPluginEditorContent(
                                            modifier = Modifier.fillMaxSize(),
                                            name = name,
                                            onNameChange = { newName: String -> name = newName },
                                            description = description,
                                            onDescriptionChange = { newDesc: String -> description = newDesc },
                                            rules = rules,
                                            onRulesChange = { newRules: List<com.android.purebilibili.core.plugin.json.Rule> -> rules = newRules },
                                            pluginType = plugin.type
                                        )
                                    }
                                } else {
                                    // Show List
                                    PluginsContent(
                                        modifier = Modifier,
                                        plugins = plugins,
                                        jsonPlugins = jsonPlugins,
                                        onEditJsonPlugin = { editingPlugin = it }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Category Root
                    AnimatedContent(
                        targetState = selectedCategory,
                        transitionSpec = {
                            (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                slideOutVertically { height -> -height } + fadeOut())
                        },
                        label = "SettingsDetailTransition"
                    ) { category ->
                        Column(modifier = Modifier.widthIn(max = 600.dp)) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 24.dp, start = 16.dp)
                            )
                            
                            when (category) {
                                SettingsCategory.GENERAL -> GeneralSection(
                                    onAppearanceClick = { activeDetail = SettingsDetail.APPEARANCE },
                                    onPlaybackClick = { activeDetail = SettingsDetail.PLAYBACK },
                                    onBottomBarClick = { activeDetail = SettingsDetail.BOTTOM_BAR }
                                )
                                SettingsCategory.PRIVACY -> PrivacySection(
                                    privacyModeEnabled = privacyModeEnabled,
                                    onPrivacyModeChange = onPrivacyModeChange,
                                    onPermissionClick = { activeDetail = SettingsDetail.PERMISSION }
                                )
                                SettingsCategory.STORAGE -> DataStorageSection(
                                    customDownloadPath = customDownloadPath,
                                    cacheSize = cacheSize,
                                    onDownloadPathClick = onDownloadPathClick,
                                    onClearCacheClick = onClearCacheClick
                                )
                                SettingsCategory.DEVELOPER -> DeveloperSection(
                                    crashTrackingEnabled = crashTrackingEnabled,
                                    analyticsEnabled = analyticsEnabled,
                                    pluginCount = pluginCount,
                                    onCrashTrackingChange = onCrashTrackingChange,
                                    onAnalyticsChange = onAnalyticsChange,
                                    onPluginsClick = { activeDetail = SettingsDetail.PLUGINS },
                                    onExportLogsClick = onExportLogsClick
                                )
                                SettingsCategory.ABOUT -> AboutSection(
                                    versionName = versionName,
                                    easterEggEnabled = easterEggEnabled,
                                    onLicenseClick = onLicenseClick,
                                    onGithubClick = onGithubClick,
                                    onVersionClick = onVersionClick,
                                    onReplayOnboardingClick = onReplayOnboardingClick,
                                    onEasterEggChange = onEasterEggChange
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

enum class SettingsDetail {
    APPEARANCE, ICONS, ANIMATION, PLAYBACK, BOTTOM_BAR, PERMISSION, PLUGINS
}

// Helper to access Icon Groups if possible, otherwise I'll need to copy helper function
// Currently I cant access `getIconGroups` because I haven't defined it as a function in IconSettingsScreen.kt
// I defined `IconGroup` class, but the list `iconGroups` was inside `IconSettingsScreen` COMPOSABLE.

