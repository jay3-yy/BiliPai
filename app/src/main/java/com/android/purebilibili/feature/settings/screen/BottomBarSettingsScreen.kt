// 文件路径: feature/settings/BottomBarSettingsScreen.kt
package com.android.purebilibili.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress // [New]
import androidx.compose.ui.input.pointer.pointerInput // [New]
import androidx.compose.ui.zIndex // [New]
import androidx.compose.ui.draw.scale // [New]
import androidx.compose.animation.core.animateFloatAsState // [New]
import androidx.compose.animation.core.snap // [New]
import androidx.compose.animation.core.spring // [New]
import androidx.compose.ui.platform.LocalDensity // [New]
import androidx.compose.ui.geometry.Offset // [New]
import androidx.compose.ui.input.pointer.PointerInputChange // [New]
import com.android.purebilibili.core.util.rememberHapticFeedback // [New]
import com.android.purebilibili.core.util.HapticType // [New]
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.store.HomeHeaderBlurMode
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.BottomBarColors  //  统一底栏颜色配置
import com.android.purebilibili.core.theme.BottomBarColorPalette  //  调色板
import com.android.purebilibili.core.theme.BottomBarColorNames  //  颜色名称
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.ui.AdaptiveTopAppBar
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.resolveAppBookmarkIcon
import com.android.purebilibili.core.ui.resolveAppDynamicIcon
import com.android.purebilibili.core.ui.resolveAppHomeIcon
import com.android.purebilibili.core.ui.resolveAppSettingsIcon
import com.android.purebilibili.core.ui.resolveAppTvIcon
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.util.LocalWindowSizeClass
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.staggeredEntrance

/**
 *  底栏项目配置
 */
data class BottomBarTabConfig(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val isDefault: Boolean = true  // 是否为默认项（默认项不可删除）
)

data class TopTabConfig(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val fixedVisible: Boolean = false
)

internal fun resolveBottomBarTabIcon(
    id: String,
    uiPreset: UiPreset = UiPreset.IOS
): ImageVector {
    return when (uiPreset) {
        UiPreset.MD3 -> when (id) {
            "HOME" -> resolveAppHomeIcon(uiPreset)
            "DYNAMIC" -> resolveAppDynamicIcon(uiPreset)
            "STORY" -> Icons.Outlined.PlayCircleOutline
            "HISTORY" -> Icons.Outlined.History
            "PROFILE" -> Icons.Outlined.Person
            "FAVORITE" -> Icons.Outlined.StarBorder
            "LIVE" -> resolveAppTvIcon(uiPreset)
            "WATCHLATER" -> resolveAppBookmarkIcon(uiPreset)
            "SETTINGS" -> resolveAppSettingsIcon(uiPreset)
            else -> resolveAppHomeIcon(uiPreset)
        }
        UiPreset.IOS -> when (id) {
            "HOME" -> CupertinoIcons.Default.House
            "DYNAMIC" -> CupertinoIcons.Default.RectangleStack
            "STORY" -> CupertinoIcons.Default.PlayCircle
            "HISTORY" -> CupertinoIcons.Default.Clock
            "PROFILE" -> CupertinoIcons.Default.PersonCircle
            "FAVORITE" -> CupertinoIcons.Default.Star
            "LIVE" -> CupertinoIcons.Default.Video
            "WATCHLATER" -> CupertinoIcons.Default.Bookmark
            "SETTINGS" -> CupertinoIcons.Default.Gearshape
            else -> CupertinoIcons.Default.House
        }
    }
}

internal fun resolveTopTabIcon(
    id: String,
    uiPreset: UiPreset = UiPreset.IOS
): ImageVector {
    return when (uiPreset) {
        UiPreset.MD3 -> when (id) {
            "RECOMMEND" -> Icons.Outlined.Home
            "FOLLOW" -> Icons.Outlined.Person
            "POPULAR" -> Icons.AutoMirrored.Outlined.TrendingUp
            "LIVE" -> Icons.Outlined.LiveTv
            "ANIME" -> Icons.Outlined.Tv
            "GAME" -> Icons.Outlined.PlayCircleOutline
            "KNOWLEDGE" -> Icons.Outlined.Lightbulb
            "TECH" -> Icons.Outlined.SmartToy
            else -> Icons.Outlined.Home
        }
        UiPreset.IOS -> when (id) {
            "RECOMMEND" -> CupertinoIcons.Default.House
            "FOLLOW" -> CupertinoIcons.Default.PersonCropCircleBadgePlus
            "POPULAR" -> CupertinoIcons.Default.ChartBar
            "LIVE" -> CupertinoIcons.Default.Video
            "ANIME" -> CupertinoIcons.Default.Tv
            "GAME" -> CupertinoIcons.Default.PlayCircle
            "KNOWLEDGE" -> CupertinoIcons.Default.Lightbulb
            "TECH" -> CupertinoIcons.Default.Cpu
            else -> CupertinoIcons.Default.House
        }
    }
}

/**
 * 所有可用的底栏项目
 */
internal fun resolveAllBottomBarTabs(uiPreset: UiPreset = UiPreset.IOS): List<BottomBarTabConfig> = listOf(
    BottomBarTabConfig("HOME", "首页", resolveBottomBarTabIcon("HOME", uiPreset), isDefault = true),
    BottomBarTabConfig("DYNAMIC", "动态", resolveBottomBarTabIcon("DYNAMIC", uiPreset), isDefault = true),
    BottomBarTabConfig("STORY", "短视频", resolveBottomBarTabIcon("STORY", uiPreset), isDefault = false),
    BottomBarTabConfig("HISTORY", "历史", resolveBottomBarTabIcon("HISTORY", uiPreset), isDefault = true),
    BottomBarTabConfig("PROFILE", "我的", resolveBottomBarTabIcon("PROFILE", uiPreset), isDefault = true),
    BottomBarTabConfig("FAVORITE", "收藏", resolveBottomBarTabIcon("FAVORITE", uiPreset), isDefault = false),
    BottomBarTabConfig("LIVE", "直播", resolveBottomBarTabIcon("LIVE", uiPreset), isDefault = false),
    BottomBarTabConfig("WATCHLATER", "稍后看", resolveBottomBarTabIcon("WATCHLATER", uiPreset), isDefault = false),
    BottomBarTabConfig("SETTINGS", "设置", resolveBottomBarTabIcon("SETTINGS", uiPreset), isDefault = false)
)

private val defaultTopTabIds = listOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME")

internal fun resolveAllTopTabs(uiPreset: UiPreset = UiPreset.IOS): List<TopTabConfig> = listOf(
    TopTabConfig("RECOMMEND", "推荐", resolveTopTabIcon("RECOMMEND", uiPreset), fixedVisible = true),
    TopTabConfig("FOLLOW", "关注", resolveTopTabIcon("FOLLOW", uiPreset)),
    TopTabConfig("POPULAR", "热门", resolveTopTabIcon("POPULAR", uiPreset)),
    TopTabConfig("LIVE", "直播", resolveTopTabIcon("LIVE", uiPreset)),
    TopTabConfig("ANIME", "追番", resolveTopTabIcon("ANIME", uiPreset)),
    TopTabConfig("GAME", "游戏", resolveTopTabIcon("GAME", uiPreset)),
    TopTabConfig("KNOWLEDGE", "知识", resolveTopTabIcon("KNOWLEDGE", uiPreset)),
    TopTabConfig("TECH", "科技", resolveTopTabIcon("TECH", uiPreset))
)

/**
 *  导航设置页面
 * 支持底栏、顶部标签和平板侧边栏配置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomBarSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsLiquidGlassEnabled by SettingsManager.getLiquidGlassEnabled(context).collectAsState(initial = true)
    val screenTitle = stringResource(R.string.bottom_bar_management_title)
    val backLabel = stringResource(R.string.common_back)
    AdaptiveScaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = screenTitle,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        CompositionLocalProvider(LocalSettingsLiquidGlassEnabled provides settingsLiquidGlassEnabled) {
            BottomBarSettingsContent(
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun BottomBarSettingsContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiPreset = LocalUiPreset.current
    val windowSizeClass = LocalWindowSizeClass.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequest by SettingsSearchFocusController.request.collectAsState()
    var isVisible by remember { mutableStateOf(false) }
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val effectiveMotionTier = remember(deviceUiProfile.motionTier) {
        resolveSettingsEntranceMotionTier(deviceUiProfile.motionTier)
    }
    LaunchedEffect(focusRequest?.token) {
        val request = focusRequest ?: return@LaunchedEffect
        if (request.target != SettingsSearchTarget.BOTTOM_BAR) return@LaunchedEffect
        val index = resolveBottomBarSettingsScrollIndex(request.focusId) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
        SettingsSearchFocusController.clear(request.token)
    }
    val allBottomBarTabs = remember(uiPreset) { resolveAllBottomBarTabs(uiPreset) }
    val allTopTabs = remember(uiPreset) { resolveAllTopTabs(uiPreset) }

    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // 读取当前配置
    val order by SettingsManager.getBottomBarOrder(context).collectAsState(initial = listOf("HOME", "DYNAMIC", "HISTORY", "PROFILE"))
    val visibleTabs by SettingsManager.getBottomBarVisibleTabs(context).collectAsState(initial = setOf("HOME", "DYNAMIC", "HISTORY", "PROFILE"))
    val topTabOrder by SettingsManager.getTopTabOrder(context).collectAsState(initial = defaultTopTabIds)
    val topTabVisible by SettingsManager.getTopTabVisibleTabs(context).collectAsState(initial = defaultTopTabIds.toSet())
    val defaultHomeTopTabId by SettingsManager.getDefaultHomeTopTabId(context).collectAsState(initial = "RECOMMEND")
    val headerCollapseEnabled by SettingsManager.getHeaderCollapseEnabled(context).collectAsState(initial = true)
    val topTabLabelMode by SettingsManager.getTopTabLabelMode(context)
        .collectAsState(initial = SettingsManager.TopTabLabelMode.TEXT_ONLY)
    val headerBlurMode by SettingsManager.getHomeHeaderBlurMode(context)
        .collectAsState(initial = HomeHeaderBlurMode.FOLLOW_PRESET)
    val tabletUseSidebar by SettingsManager.getTabletUseSidebar(context).collectAsState(initial = false)
    
    // 可编辑的本地状态
    var localOrder by remember(order) { mutableStateOf(order) }
    var localVisibleTabs by remember(visibleTabs) { mutableStateOf(visibleTabs) }
    var localTopTabOrder by remember(topTabOrder) {
        mutableStateOf(
            (topTabOrder + allTopTabs.map { it.id })
                .distinct()
                .filter { id -> allTopTabs.any { it.id == id } }
        )
    }
    var localTopTabVisible by remember(topTabVisible) {
        mutableStateOf(
            (topTabVisible.filter { id -> allTopTabs.any { it.id == id } }.toSet() + "RECOMMEND")
        )
    }
    var localDefaultHomeTopTabId by remember(defaultHomeTopTabId) {
        mutableStateOf(defaultHomeTopTabId)
    }

    LaunchedEffect(localTopTabVisible, localDefaultHomeTopTabId) {
        if (localDefaultHomeTopTabId !in localTopTabVisible) {
            localDefaultHomeTopTabId = "RECOMMEND"
            SettingsManager.setDefaultHomeTopTabId(context, "RECOMMEND")
        }
    }
    
    // [新增] 监听顺序变化并保存
    fun onOrderChanged(fromIndex: Int, toIndex: Int) {
        val currentVisibleTabsList = localOrder.filter { it in localVisibleTabs }
        if (fromIndex in currentVisibleTabsList.indices && toIndex in currentVisibleTabsList.indices) {
            val fromId = currentVisibleTabsList[fromIndex]
            val toId = currentVisibleTabsList[toIndex]
            
            val globalFrom = localOrder.indexOf(fromId)
            val globalTo = localOrder.indexOf(toId)
            
            if (globalFrom != -1 && globalTo != -1) {
                // 交换位置
                val newOrder = localOrder.toMutableList()
                val item = newOrder.removeAt(globalFrom)
                newOrder.add(globalTo, item)
                localOrder = newOrder
            }
        }
    }
    
    //  [新增] 读取项目颜色配置
    val itemColors by SettingsManager.getBottomBarItemColors(context).collectAsState(initial = emptyMap())
    
    // 保存配置
    fun saveConfig() {
        scope.launch {
            SettingsManager.setBottomBarOrder(context, localOrder)
            SettingsManager.setBottomBarVisibleTabs(context, localVisibleTabs)
        }
    }

    fun saveTopTabConfig(
        order: List<String> = localTopTabOrder,
        visible: Set<String> = localTopTabVisible,
        defaultTabId: String = localDefaultHomeTopTabId
    ) {
        val effectiveVisible = visible + "RECOMMEND"
        val effectiveDefaultTabId = defaultTabId.takeIf { it in effectiveVisible } ?: "RECOMMEND"
        scope.launch {
            SettingsManager.setTopTabOrder(context, order)
            SettingsManager.setTopTabVisibleTabs(context, effectiveVisible)
            SettingsManager.setDefaultHomeTopTabId(context, effectiveDefaultTabId)
        }
    }

    fun moveTopTab(tabId: String, direction: Int) {
        val visibleOrder = localTopTabOrder.filter { it in localTopTabVisible }
        val from = visibleOrder.indexOf(tabId)
        if (from < 0) return
        val to = (from + direction).coerceIn(0, visibleOrder.lastIndex)
        if (to == from) return

        val toId = visibleOrder[to]
        val globalFrom = localTopTabOrder.indexOf(tabId)
        val globalTo = localTopTabOrder.indexOf(toId)
        if (globalFrom < 0 || globalTo < 0) return

        val mutable = localTopTabOrder.toMutableList()
        val item = mutable.removeAt(globalFrom)
        mutable.add(globalTo, item)
        // 推荐固定在首位
        val withoutRecommend = mutable.filterNot { it == "RECOMMEND" }
        localTopTabOrder = listOf("RECOMMEND") + withoutRecommend
        saveTopTabConfig()
    }
    
    //  [新增] 保存颜色配置
    fun saveItemColor(itemId: String, colorIndex: Int) {
        scope.launch {
            SettingsManager.setBottomBarItemColor(context, itemId, colorIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // 说明文字
            item {
                Box(modifier = Modifier.staggeredEntrance(0, isVisible, motionTier = effectiveMotionTier)) {
                    Text(
                        text = "集中管理底部导航、首页顶部标签和平板侧边栏。底栏项目最少 2 个，最多 5 个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 底部导航
            item {
                Box(modifier = Modifier.staggeredEntrance(1, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("底部导航")
                }
            }

            item {
                Box(modifier = Modifier.staggeredEntrance(2, isVisible, motionTier = effectiveMotionTier)) {
                    IOSGroup {
                        val scope = rememberCoroutineScope()
                        val visibilityMode by SettingsManager.getBottomBarVisibilityMode(context).collectAsState(initial = SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE)
                        val labelMode by SettingsManager.getBottomBarLabelMode(context).collectAsState(initial = 0)
                        
                        //  底栏显示模式选择（抽屉式）
                        var visibilityModeExpanded by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { visibilityModeExpanded = !visibilityModeExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    CupertinoIcons.Default.Eye,
                                    contentDescription = null,
                                    tint = com.android.purebilibili.core.theme.iOSOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "显示模式",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = visibilityMode.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (visibilityModeExpanded) CupertinoIcons.Default.ChevronUp else CupertinoIcons.Default.ChevronDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // 展开后的选项
                            androidx.compose.animation.AnimatedVisibility(
                                visible = visibilityModeExpanded,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    com.android.purebilibili.core.store.SettingsManager.BottomBarVisibilityMode.entries.forEach { mode ->
                                        val isSelected = mode == visibilityMode
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                )
                                                .clickable {
                                                    scope.launch {
                                                        SettingsManager.setBottomBarVisibilityMode(context, mode)
                                                    }
                                                    visibilityModeExpanded = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    mode.label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                            else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    mode.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    CupertinoIcons.Default.Checkmark,
                                                    contentDescription = "已选择",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        IOSDivider()
                        
                        //  底栏标签样式（选择器）
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    CupertinoIcons.Default.Tag,
                                    contentDescription = null,
                                    tint = com.android.purebilibili.core.theme.iOSPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "标签样式",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when(labelMode) {
                                            0 -> "图标 + 文字"
                                            2 -> "仅文字"
                                            else -> "仅图标"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 三种模式选择按钮
                                listOf(
                                    Triple(0, "图标+文字", CupertinoIcons.Default.House),
                                    Triple(1, "仅图标", CupertinoIcons.Default.HandThumbsup),
                                    Triple(2, "仅文字", CupertinoIcons.Default.Character)
                                ).forEach { (mode, label, icon) ->
                                    val isSelected = labelMode == mode
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { 
                                                scope.launch { SettingsManager.setBottomBarLabelMode(context, mode) }
                                            }
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                    }
                }
            }

            // 顶部标签
            item {
                Box(modifier = Modifier.staggeredEntrance(3, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("顶部标签")
                }
            }

            item {
                Box(modifier = Modifier.staggeredEntrance(4, isVisible, motionTier = effectiveMotionTier)) {
                    IOSGroup {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    CupertinoIcons.Default.ListBullet,
                                    contentDescription = null,
                                    tint = com.android.purebilibili.core.theme.iOSBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "顶部标签样式",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (topTabLabelMode) {
                                            SettingsManager.TopTabLabelMode.ICON_AND_TEXT -> "图标 + 文字"
                                            SettingsManager.TopTabLabelMode.ICON_ONLY -> "仅图标"
                                            else -> "仅文字"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(
                                    Triple(SettingsManager.TopTabLabelMode.ICON_AND_TEXT, "图标+文字", CupertinoIcons.Default.ListBullet),
                                    Triple(SettingsManager.TopTabLabelMode.ICON_ONLY, "仅图标", CupertinoIcons.Default.Tag),
                                    Triple(SettingsManager.TopTabLabelMode.TEXT_ONLY, "仅文字", CupertinoIcons.Default.Character)
                                ).forEach { (mode, label, icon) ->
                                    val isSelected = topTabLabelMode == mode
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                scope.launch { SettingsManager.setTopTabLabelMode(context, mode) }
                                            }
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    CupertinoIcons.Default.Drop,
                                    contentDescription = null,
                                    tint = com.android.purebilibili.core.theme.iOSTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "顶部模糊",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (headerBlurMode) {
                                            HomeHeaderBlurMode.FOLLOW_PRESET -> "跟随预设"
                                            HomeHeaderBlurMode.ALWAYS_ON -> "始终开启"
                                            HomeHeaderBlurMode.ALWAYS_OFF -> "始终关闭"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(
                                    HomeHeaderBlurMode.FOLLOW_PRESET to "跟随预设",
                                    HomeHeaderBlurMode.ALWAYS_ON to "始终开",
                                    HomeHeaderBlurMode.ALWAYS_OFF to "始终关"
                                ).forEach { (mode, label) ->
                                    val isSelected = headerBlurMode == mode
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                scope.launch {
                                                    SettingsManager.setHomeHeaderBlurMode(context, mode)
                                                }
                                            }
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when (mode) {
                                                HomeHeaderBlurMode.FOLLOW_PRESET -> "iOS 开 / MD3 开"
                                                HomeHeaderBlurMode.ALWAYS_ON -> "两套都开"
                                                HomeHeaderBlurMode.ALWAYS_OFF -> "两套都关"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            Text(
                                text = "推荐固定显示。可调整其余标签的显示/隐藏、顺序，以及下滑时的自动收起行为。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = CupertinoIcons.Default.ChevronUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "顶部栏自动收缩",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "列表离开顶部时自动隐藏推荐、直播那一排标签，回到顶部时恢复",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AppAdaptiveSwitch(
                                    checked = headerCollapseEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            SettingsManager.setHeaderCollapseEnabled(context, checked)
                                        }
                                    }
                                )
                            }

                            val visibleTopOrder = localTopTabOrder.filter { it in localTopTabVisible }

                            HorizontalDivider()

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    CupertinoIcons.Default.House,
                                    contentDescription = null,
                                    tint = com.android.purebilibili.core.theme.iOSBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "默认打开标签",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = allTopTabs.firstOrNull { it.id == localDefaultHomeTopTabId }?.label
                                            ?: "推荐",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            visibleTopOrder.forEach { id ->
                                val tab = allTopTabs.firstOrNull { it.id == id } ?: return@forEach
                                val isSelected = localDefaultHomeTopTabId == tab.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            localDefaultHomeTopTabId = tab.id
                                            saveTopTabConfig(defaultTabId = tab.id)
                                        }
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                }
                            }

                            HorizontalDivider()

                            Text(
                                text = "已显示（上下按钮可排序）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            visibleTopOrder.forEachIndexed { index, id ->
                                val tab = allTopTabs.firstOrNull { it.id == id } ?: return@forEachIndexed
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (tab.fixedVisible) {
                                        Text(
                                            text = "固定",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { moveTopTab(tab.id, -1) },
                                        enabled = !tab.fixedVisible && index > 1
                                    ) {
                                        Icon(
                                            CupertinoIcons.Default.ChevronUp,
                                            contentDescription = "上移",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { moveTopTab(tab.id, 1) },
                                        enabled = !tab.fixedVisible && index < visibleTopOrder.lastIndex
                                    ) {
                                        Icon(
                                            CupertinoIcons.Default.ChevronDown,
                                            contentDescription = "下移",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "可用标签",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            allTopTabs.forEach { tab ->
                                val isVisibleTab = tab.id in localTopTabVisible
                                val canToggle = if (tab.fixedVisible) {
                                    false
                                } else if (isVisibleTab) {
                                    localTopTabVisible.size > 2
                                } else {
                                    true
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppAdaptiveSwitch(
                                        checked = isVisibleTab,
                                        onCheckedChange = { checked ->
                                            if (!canToggle) return@AppAdaptiveSwitch
                                            val newVisible = if (checked) {
                                                localTopTabVisible + tab.id
                                            } else {
                                                localTopTabVisible - tab.id
                                            }
                                            localTopTabVisible = newVisible
                                            if (checked && tab.id !in localTopTabOrder) {
                                                localTopTabOrder = localTopTabOrder + tab.id
                                            }
                                            // 推荐固定在首位
                                            val withoutRecommend = localTopTabOrder.filterNot { it == "RECOMMEND" }
                                            localTopTabOrder = listOf("RECOMMEND") + withoutRecommend
                                            val newDefaultTabId = if (localDefaultHomeTopTabId in newVisible) {
                                                localDefaultHomeTopTabId
                                            } else {
                                                "RECOMMEND"
                                            }
                                            localDefaultHomeTopTabId = newDefaultTabId
                                            saveTopTabConfig(
                                                order = localTopTabOrder,
                                                visible = newVisible,
                                                defaultTabId = newDefaultTabId
                                            )
                                        },
                                        enabled = canToggle
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 平板导航
            item {
                Box(modifier = Modifier.staggeredEntrance(5, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("平板导航")
                }
            }

            item {
                Box(modifier = Modifier.staggeredEntrance(6, isVisible, motionTier = effectiveMotionTier)) {
                    IOSGroup {
                        IOSSwitchItem(
                            icon = CupertinoIcons.Outlined.SidebarLeft,
                            title = "侧边导航栏",
                            subtitle = "在平板横屏或大屏布局中使用侧边栏代替底部导航",
                            checked = tabletUseSidebar,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    SettingsManager.setTabletUseSidebar(context, checked)
                                }
                            },
                            iconTint = com.android.purebilibili.core.theme.iOSBlue
                        )
                    }
                }
            }

            // 当前底栏预览
            item {
                Box(modifier = Modifier.staggeredEntrance(7, isVisible, motionTier = effectiveMotionTier)) {
                    IOSSectionTitle("当前底栏")
                }
            }
            
            item {
                Box(modifier = Modifier.staggeredEntrance(8, isVisible, motionTier = effectiveMotionTier)) {
                    BottomBarPreview(
                        tabs = localOrder.filter { it in localVisibleTabs }
                            .mapNotNull { id -> allBottomBarTabs.find { it.id == id } },
                        onMove = { from, to -> onOrderChanged(from, to) },
                        onDragEnd = { saveConfig() }
                    )
                }
            }
            
            // 可用项目列表
            item {
                Box(modifier = Modifier.staggeredEntrance(9, isVisible, motionTier = effectiveMotionTier)) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        IOSSectionTitle("可用项目")
                    }
                }
            }
            
            item {
                Box(modifier = Modifier.staggeredEntrance(10, isVisible, motionTier = effectiveMotionTier)) {
                    IOSGroup {
                        allBottomBarTabs.forEachIndexed { index, tab ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                            }
                            BottomBarTabItem(
                                tab = tab,
                                isVisible = tab.id in localVisibleTabs,
                                colorIndex = itemColors[tab.id] ?: BottomBarColors.getDefaultColorIndex(tab.id),
                                canToggle = if (tab.id in localVisibleTabs) {
                                    // 已显示的项目：至少保留 2 个可见
                                    localVisibleTabs.size > 2
                                } else {
                                    // 未显示的项目：最多显示 5 个
                                    localVisibleTabs.size < 5
                                },
                                onToggle = { visible ->
                                    localVisibleTabs = if (visible) {
                                        localVisibleTabs + tab.id
                                    } else {
                                        localVisibleTabs - tab.id
                                    }
                                    // 如果是新增项目，加到顺序末尾
                                    if (visible && tab.id !in localOrder) {
                                        localOrder = localOrder + tab.id
                                    }
                                    saveConfig()
                                },
                                onColorChange = { newColorIndex ->
                                    saveItemColor(tab.id, newColorIndex)
                                }
                            )
                        }
                    }
                }
            }
            
            // 顺序调整说明
            item {
                Box(modifier = Modifier.staggeredEntrance(11, isVisible, motionTier = effectiveMotionTier)) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = " 长按图标并拖拽可调整显示顺序",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // 重置按钮
            item {
                Box(modifier = Modifier.staggeredEntrance(12, isVisible, motionTier = effectiveMotionTier)) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        io.github.alexzhirkevich.cupertino.CupertinoButton(
                            onClick = {
                                localOrder = listOf("HOME", "DYNAMIC", "HISTORY", "PROFILE")
                                localVisibleTabs = setOf("HOME", "DYNAMIC", "HISTORY", "PROFILE")
                                localTopTabOrder = defaultTopTabIds
                                localTopTabVisible = defaultTopTabIds.toSet()
                                localDefaultHomeTopTabId = "RECOMMEND"
                                saveConfig()
                                saveTopTabConfig(defaultTabId = "RECOMMEND")
                                scope.launch {
                                    SettingsManager.setHomeHeaderBlurMode(context, HomeHeaderBlurMode.FOLLOW_PRESET)
                                    SettingsManager.setTabletUseSidebar(context, false)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults.borderedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(CupertinoIcons.Default.ArrowCounterclockwise, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("重置为默认")
                        }
                    }
                }
            }
        }
    }


/**
 * 底栏预览组件（支持长按拖拽排序）
 */
@Composable
private fun BottomBarPreview(
    tabs: List<BottomBarTabConfig>,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
) {
    // 触感反馈
    val haptic = rememberHapticFeedback()
    
    // 拖拽状态
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemCenter by remember { mutableFloatStateOf(0f) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            val totalWidth = maxWidth
            val itemWidth = totalWidth / tabs.size.coerceAtLeast(1)
            val density = LocalDensity.current
            
            // 全局手势检测区域
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(20f) // 确保在最上层接收触摸事件
                    .pointerInput(tabs.size, itemWidth) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val index = (offset.x / itemWidth.toPx()).toInt().coerceIn(0, tabs.lastIndex)
                                draggingItemIndex = index
                                draggingItemCenter = offset.x.coerceIn(0f, totalWidth.toPx())
                                haptic.invoke(HapticType.MEDIUM)
                            },
                            onDragEnd = {
                                draggingItemIndex = null
                                onDragEnd()
                            },
                            onDragCancel = {
                                draggingItemIndex = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggingItemCenter = (draggingItemCenter + dragAmount.x).coerceIn(0f, totalWidth.toPx())
                                
                                // 计算新索引
                                val newIndex = (draggingItemCenter / itemWidth.toPx()).toInt()
                                    .coerceIn(0, tabs.lastIndex)
                                
                                if (newIndex != draggingItemIndex) {
                                    if (draggingItemIndex != null) {
                                        onMove(draggingItemIndex!!, newIndex)
                                        draggingItemIndex = newIndex
                                        haptic.invoke(HapticType.LIGHT)
                                    }
                                }
                            }
                        )
                    }
            )

            tabs.forEachIndexed { index, tab ->
                key(tab.id) {
                    val isDragging = index == draggingItemIndex
                    val zIndex = if (isDragging) 10f else 0f
                    val scale by androidx.compose.animation.core.animateFloatAsState(if (isDragging) 1.2f else 1f, label = "scale")
                    
                    // 计算目标 X 位置
                    val targetX = if (isDragging) {
                         // 拖拽时：跟随手指中心
                         with(density) { draggingItemCenter.toDp() - (itemWidth / 2) }
                    } else {
                        // 静止时：网格位置
                        itemWidth * index
                    }
                    
                    val animatedX by androidx.compose.animation.core.animateDpAsState(
                        targetValue = targetX,
                        animationSpec = if (isDragging) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(),
                        label = "offset"
                    )
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(itemWidth)
                            .offset(x = animatedX)
                            .zIndex(zIndex)
                            .scale(scale)
                            // 移除单独的 pointerInput
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (index == 0 && !isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (index == 0 && !isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底栏项目单项
 */
@Composable
private fun BottomBarTabItem(
    tab: BottomBarTabConfig,
    isVisible: Boolean,
    colorIndex: Int,
    canToggle: Boolean,
    onToggle: (Boolean) -> Unit,
    onColorChange: (Int) -> Unit
) {
    //  获取项目当前颜色
    val itemColor = BottomBarColors.getColorByIndex(colorIndex)
    
    //  颜色选择弹窗状态
    var showColorPicker by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标 -  点击可更换颜色
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(itemColor.copy(alpha = 0.12f))
                .clickable { showColorPicker = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = itemColor,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        // 名称
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "点击图标更换颜色",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 开关
        AppAdaptiveSwitch(
            checked = isVisible,
            onCheckedChange = { newValue -> if (canToggle) onToggle(newValue) },
            enabled = canToggle
        )
    }
    
    //  颜色选择弹窗
    if (showColorPicker) {
        com.android.purebilibili.core.ui.IOSAlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("选择${tab.label}颜色") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BottomBarColorPalette.forEachIndexed { index, color ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onColorChange(index)
                                    showColorPicker = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = BottomBarColorNames[index],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (index == colorIndex) {
                                Icon(
                                    CupertinoIcons.Default.Checkmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                com.android.purebilibili.core.ui.IOSDialogAction(
                    onClick = { showColorPicker = false }
                ) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}
