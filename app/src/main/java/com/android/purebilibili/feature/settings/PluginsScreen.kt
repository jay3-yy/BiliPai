// 文件路径: feature/settings/PluginsScreen.kt
package com.android.purebilibili.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.PluginInfo
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.theme.iOSPink  // 插件图标色
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSGreen
import com.android.purebilibili.core.theme.iOSOrange
import com.android.purebilibili.core.theme.iOSPurple
import com.android.purebilibili.core.theme.iOSTeal
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import io.github.alexzhirkevich.cupertino.CupertinoSwitchDefaults
import kotlinx.coroutines.launch

/**
 *  插件中心页面
 * 
 * 显示所有可用插件，支持启用/禁用和配置。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    onBack: () -> Unit
) {
    // Top-level state for managing plugins and editing
    val plugins by PluginManager.pluginsFlow.collectAsState()
    val jsonPlugins by com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.collectAsState()
    val scope = rememberCoroutineScope()
    
    //  编辑插件状态
    var editingPlugin by remember { mutableStateOf<com.android.purebilibili.core.plugin.json.JsonRulePlugin?>(null) }
    
    //  如果正在编辑插件，显示编辑器全屏覆盖 (Mobile behavior)
    //  In Tablet, this will be handled differently.
    editingPlugin?.let { plugin ->
        JsonPluginEditorScreen(
            plugin = plugin,
            onBack = { editingPlugin = null },
            onSave = { updated ->
                com.android.purebilibili.core.plugin.json.JsonPluginManager.updatePlugin(updated)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件中心", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        PluginsContent(
            modifier = Modifier.padding(padding),
            plugins = plugins,
            jsonPlugins = jsonPlugins,
            onEditJsonPlugin = { editingPlugin = it }
        )
    }
}

@Composable
fun PluginsContent(
    modifier: Modifier = Modifier,
    plugins: List<com.android.purebilibili.core.plugin.PluginInfo>,
    jsonPlugins: List<com.android.purebilibili.core.plugin.json.LoadedJsonPlugin>,
    onEditJsonPlugin: (com.android.purebilibili.core.plugin.json.JsonRulePlugin) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Statistics
    val totalPlugins = plugins.size + jsonPlugins.size
    val enabledPlugins = plugins.count { it.enabled } + jsonPlugins.count { it.enabled }
    
    // Local UI states
    var expandedPluginId by remember { mutableStateOf<String?>(null) }
    
    //  导入插件对话框状态
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    
    //  测试对话框状态
    var testingPluginId by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Triple<Int, Int, List<com.android.purebilibili.data.model.response.VideoItem>>?>(null) }
    var testingSampleVideos by remember { mutableStateOf<List<com.android.purebilibili.data.model.response.VideoItem>>(emptyList()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
            
            // 标题说明
            item {
                Text(
                    text = "已安装插件".uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                )
            }
            
            // 插件列表
            item {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column {
                        plugins.forEachIndexed { index, pluginInfo ->
                            PluginItem(
                                pluginInfo = pluginInfo,
                                isExpanded = expandedPluginId == pluginInfo.plugin.id,
                                iconTint = getPluginColor(index),
                                onToggle = { enabled ->
                                    scope.launch {
                                        PluginManager.setEnabled(pluginInfo.plugin.id, enabled)
                                    }
                                },
                                onExpandToggle = {
                                    expandedPluginId = if (expandedPluginId == pluginInfo.plugin.id) {
                                        null
                                    } else {
                                        pluginInfo.plugin.id
                                    }
                                }
                            )
                            if (index < plugins.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .padding(start = 66.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
            
            // 统计信息
            item {
                val enabledCount = plugins.count { it.enabled }
                Text(
                    text = "${plugins.size} 个插件，$enabledCount 个已启用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, top = 16.dp)
                )
            }
            
            //  导入外部插件按钮
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "外部插件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                )
            }
            
            item {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showImportDialog = true },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(iOSBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CupertinoIcons.Default.IcloudAndArrowDown,
                                contentDescription = null,
                                tint = iOSBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "导入外部插件",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "通过 URL 安装 JSON 规则插件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = CupertinoIcons.Default.Plus,
                            contentDescription = null,
                            tint = iOSBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            //  已安装的 JSON 插件列表
            if (jsonPlugins.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    val filterStats by com.android.purebilibili.core.plugin.json.JsonPluginManager.filterStats.collectAsState()
                    
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column {
                            jsonPlugins.forEachIndexed { index, loadedPlugin ->
                                JsonPluginItem(
                                    loaded = loadedPlugin,
                                    filterCount = filterStats[loadedPlugin.plugin.id] ?: 0,
                                    onToggle = { enabled ->
                                        com.android.purebilibili.core.plugin.json.JsonPluginManager.setEnabled(
                                            loadedPlugin.plugin.id, enabled
                                        )
                                    },
                                    onEdit = {
                                        //  Callback to editing
                                        onEditJsonPlugin(loadedPlugin.plugin)
                                    },
                                    onDelete = {
                                        com.android.purebilibili.core.plugin.json.JsonPluginManager.removePlugin(
                                            loadedPlugin.plugin.id
                                        )
                                    },
                                    onResetStats = {
                                        com.android.purebilibili.core.plugin.json.JsonPluginManager.resetStats(loadedPlugin.plugin.id)
                                        android.widget.Toast.makeText(
                                            context,
                                            "统计已重置",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onTest = {
                                        //  获取首页样本视频进行测试
                                        scope.launch {
                                            try {
                                                // 从 API 获取样本视频
                                                val result = com.android.purebilibili.data.repository.VideoRepository.getHomeVideos(0)
                                                result.onSuccess { videos ->
                                                    val sampleVideos = videos.take(20)
                                                    testingSampleVideos = sampleVideos
                                                    val (original, filtered) = com.android.purebilibili.core.plugin.json.JsonPluginManager.testPluginRules(
                                                        loadedPlugin.plugin.id, sampleVideos
                                                    )
                                                    val blockedVideos = com.android.purebilibili.core.plugin.json.JsonPluginManager.getFilteredVideosByPlugin(
                                                        loadedPlugin.plugin.id, sampleVideos
                                                    )
                                                    testResult = Triple(original, filtered, blockedVideos)
                                                    testingPluginId = loadedPlugin.plugin.id
                                                }.onFailure {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "获取测试数据失败",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "测试失败: ${e.message}",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                )
                                if (index < jsonPlugins.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 52.dp)
                                            .height(0.5.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 底部说明
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "插件可以扩展应用功能，如自动跳过广告、过滤推荐内容等。\n启用插件后可点击展开查看详细设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    
    //  导入插件对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                importUrl = ""
                importError = null
            },
            icon = { Icon(CupertinoIcons.Default.IcloudAndArrowDown, contentDescription = null) },
            title = { Text("导入外部插件") },
            text = {
                Column {
                    Text(
                        text = "输入 JSON 规则插件的下载链接",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { 
                            importUrl = it
                            importError = null
                        },
                        label = { Text("插件 URL") },
                        placeholder = { Text("https://xxx.json") },
                        singleLine = true,
                        isError = importError != null,
                        supportingText = importError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (isImporting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在安装...")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importUrl.isBlank()) {
                            importError = "请输入 URL"
                            return@TextButton
                        }
                        
                        if (!importUrl.endsWith(".json")) {
                            importError = "链接必须以 .json 结尾"
                            return@TextButton
                        }
                        
                        isImporting = true
                        scope.launch {
                            val result = com.android.purebilibili.core.plugin.json.JsonPluginManager.importFromUrl(importUrl)
                            isImporting = false
                            
                            if (result.isSuccess) {
                                showImportDialog = false
                                importUrl = ""
                                importError = null
                                //  显示成功 Toast
                                android.widget.Toast.makeText(
                                    context,
                                    "插件 \"${result.getOrNull()?.name}\" 安装成功！",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                importError = result.exceptionOrNull()?.message ?: "安装失败"
                            }
                        }
                    },
                    enabled = !isImporting
                ) {
                    Text("安装")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showImportDialog = false
                        importUrl = ""
                        importError = null
                    },
                    enabled = !isImporting
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    //  测试结果对话框
    testingPluginId?.let { pluginId ->
        testResult?.let { (original, filtered, blockedVideos) ->
            val pluginName = jsonPlugins.find { it.plugin.id == pluginId }?.plugin?.name ?: "未知插件"
            TestResultDialog(
                pluginName = pluginName,
                originalCount = original,
                filteredCount = filtered,
                filteredVideos = blockedVideos,
                onDismiss = {
                    testingPluginId = null
                    testResult = null
                }
            )
        }
    }
}

@Composable
private fun PluginItem(
    pluginInfo: PluginInfo,
    isExpanded: Boolean,
    iconTint: Color,
    onToggle: (Boolean) -> Unit,
    onExpandToggle: () -> Unit
) {
    val plugin = pluginInfo.plugin
    
    Column {
        // 主行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = plugin.icon ?: CupertinoIcons.Default.Puzzlepiece,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            // 标题和描述
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    //  暂不可用标签
                    if (plugin.unavailable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "暂不可用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                //  显示作者
                if (plugin.author != "Unknown") {
                    Text(
                        text = "by ${plugin.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 开关
            val primaryColor = MaterialTheme.colorScheme.primary
            CupertinoSwitch(
                checked = pluginInfo.enabled,
                onCheckedChange = onToggle,
                colors = CupertinoSwitchDefaults.colors(
                    thumbColor = androidx.compose.ui.graphics.Color.White,
                    checkedTrackColor = primaryColor,
                    uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFFE9E9EA)
                )
            )
            
            // 展开箭头
            Icon(
                imageVector = if (isExpanded) CupertinoIcons.Default.ChevronUp else CupertinoIcons.Default.ChevronDown,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
        
        // 展开的配置区域
        AnimatedVisibility(
            visible = isExpanded && pluginInfo.enabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 66.dp, end = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ) {
                plugin.SettingsContent()
            }
        }
    }
}

/**
 * 获取插件对应的颜色
 */
private fun getPluginColor(index: Int): Color {
    val colors = listOf(iOSTeal, iOSOrange, iOSBlue, iOSGreen, iOSPurple, iOSPink)
    return colors[index % colors.size]
}

/**
 * JSON 规则插件列表项
 */
@Composable
private fun JsonPluginItem(
    loaded: com.android.purebilibili.core.plugin.json.LoadedJsonPlugin,
    filterCount: Int,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResetStats: () -> Unit = {},
    onTest: () -> Unit = {}
) {
    val plugin = loaded.plugin
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iOSPurple.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.Terminal,
                    contentDescription = null,
                    tint = iOSPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = plugin.description.ifEmpty { plugin.type },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "by ${plugin.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = iOSPurple
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    //  统计始终显示
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (filterCount > 0) 
                            iOSGreen.copy(alpha = 0.15f)
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "已过滤 $filterCount 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (filterCount > 0) iOSGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // 开关
            val primaryColor = MaterialTheme.colorScheme.primary
            CupertinoSwitch(
                checked = loaded.enabled,
                onCheckedChange = onToggle,
                colors = CupertinoSwitchDefaults.colors(
                    thumbColor = androidx.compose.ui.graphics.Color.White,
                    checkedTrackColor = primaryColor,
                    uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFFE9E9EA)
                )
            )
            
            // 展开箭头
            Icon(
                imageVector = if (isExpanded) CupertinoIcons.Default.ChevronUp else CupertinoIcons.Default.ChevronDown,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
        
        //  展开的操作区域
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 66.dp, end = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 测试规则按钮
                    TextButton(
                        onClick = onTest,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = iOSBlue
                        )
                    ) {
                        Icon(CupertinoIcons.Default.Lightbulb, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("测试规则", style = MaterialTheme.typography.labelMedium)
                    }
                    
                    // 重置统计按钮
                    TextButton(
                        onClick = onResetStats,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = iOSOrange
                        )
                    ) {
                        Icon(CupertinoIcons.Default.ArrowCounterclockwise, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重置统计", style = MaterialTheme.typography.labelMedium)
                    }
                    
                    // 编辑按钮
                    TextButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = iOSPurple
                        )
                    ) {
                        Icon(CupertinoIcons.Default.Terminal, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("编辑", style = MaterialTheme.typography.labelMedium)
                    }
                    
                    // 删除按钮
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(CupertinoIcons.Default.Trash, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除插件") },
            text = { Text("确定要删除插件 \"${plugin.name}\" 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 *  测试结果对话框
 */
@Composable
private fun TestResultDialog(
    pluginName: String,
    originalCount: Int,
    filteredCount: Int,
    filteredVideos: List<com.android.purebilibili.data.model.response.VideoItem>,
    onDismiss: () -> Unit
) {
    val blockedCount = originalCount - filteredCount
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { 
            Icon(
                CupertinoIcons.Default.Lightbulb, 
                contentDescription = null,
                tint = iOSBlue
            ) 
        },
        title = { Text("规则测试结果") },
        text = {
            Column {
                Text(
                    text = "插件：$pluginName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // 统计卡片
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$originalCount",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "测试视频",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$blockedCount",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (blockedCount > 0) iOSGreen else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "被过滤",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$filteredCount",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "保留",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 被过滤的视频列表
                if (filteredVideos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "被过滤的视频示例：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column {
                        filteredVideos.take(3).forEach { video ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    CupertinoIcons.Default.Trash,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "时长: ${formatDuration(video.duration)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (filteredVideos.size > 3) {
                            Text(
                                text = "... 还有 ${filteredVideos.size - 3} 个视频",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else if (blockedCount == 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = " 当前测试样本中没有符合过滤条件的视频",
                        style = MaterialTheme.typography.bodySmall,
                        color = iOSGreen
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

/**
 * 格式化时长（秒 -> 分:秒）
 */
private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "${mins}分${secs}秒"
}
