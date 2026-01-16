// 文件路径: feature/settings/SettingsViewModel.kt
package com.android.purebilibili.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.util.CacheUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hwDecode: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    val bgPlay: Boolean = false,
    val gestureSensitivity: Float = 1.0f,
    val themeColorIndex: Int = 0,
    val appIcon: String = "Yuki",
    val isBottomBarFloating: Boolean = true,
    val bottomBarLabelMode: Int = 1,  // 0=图标+文字, 1=仅图标, 2=仅文字
    val headerBlurEnabled: Boolean = true,
    val bottomBarBlurEnabled: Boolean = true,
    val blurIntensity: BlurIntensity = BlurIntensity.THIN,  //  模糊强度
    val displayMode: Int = 0,
    val cardAnimationEnabled: Boolean = false,     //  卡片进场动画（默认关闭）
    val cardTransitionEnabled: Boolean = false,    //  卡片过渡动画（默认关闭）
    val cacheSize: String = "计算中...",
    val cacheBreakdown: CacheUtils.CacheBreakdown? = null,  //  详细缓存统计
    //  实验性功能
    val auto1080p: Boolean = true,
    val autoSkipOpEd: Boolean = false,
    val prefetchVideo: Boolean = false,
    val doubleTapLike: Boolean = true,
    //  UI 自定义
    val cornerRadiusScale: Float = 1.0f,
    val fontScale: Float = 1.0f,
    val uiScale: Float = 1.0f,
    //  空降助手
    val sponsorBlockEnabled: Boolean = false,
    val sponsorBlockAutoSkip: Boolean = true
)

// 内部数据类，用于分批合并流
private data class CoreSettings(
    val hwDecode: Boolean,
    val themeMode: AppThemeMode,
    val dynamicColor: Boolean,
    val bgPlay: Boolean
)

data class ExtraSettings(
    val gestureSensitivity: Float,
    val themeColorIndex: Int,
    val appIcon: String,
    val isBottomBarFloating: Boolean,
    val bottomBarLabelMode: Int,
    val headerBlurEnabled: Boolean,
    val bottomBarBlurEnabled: Boolean,
    val blurIntensity: BlurIntensity,  //  添加模糊强度
    val displayMode: Int,
    val cardAnimationEnabled: Boolean,
    val cardTransitionEnabled: Boolean,
    val cornerRadiusScale: Float,
    val fontScale: Float,
    val uiScale: Float
)

//  实验性功能设置
data class ExperimentalSettings(
    val auto1080p: Boolean,
    val autoSkipOpEd: Boolean,
    val prefetchVideo: Boolean,
    val doubleTapLike: Boolean,
    //  空降助手
    val sponsorBlockEnabled: Boolean,
    val sponsorBlockAutoSkip: Boolean
)

private data class BaseSettings(
    val hwDecode: Boolean,
    val themeMode: AppThemeMode,
    val dynamicColor: Boolean,
    val bgPlay: Boolean,
    val gestureSensitivity: Float,
    val themeColorIndex: Int,
    val appIcon: String,
    val isBottomBarFloating: Boolean,
    val bottomBarLabelMode: Int,
    val headerBlurEnabled: Boolean,
    val bottomBarBlurEnabled: Boolean,
    val blurIntensity: BlurIntensity,  //  模糊强度
    val displayMode: Int, //  新增
    val cardAnimationEnabled: Boolean, //  卡片进场动画
    val cardTransitionEnabled: Boolean, //  卡片过渡动画
    val cornerRadiusScale: Float,
    val fontScale: Float,
    val uiScale: Float
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    // 本地状态流：缓存大小
    private val _cacheSize = MutableStateFlow("计算中...")
    private val _cacheBreakdown = MutableStateFlow<CacheUtils.CacheBreakdown?>(null)

    //  [核心修复] 分步合并，解决 combine 参数限制报错
    // 第 1 步：合并前 4 个设置
    private val coreSettingsFlow = combine(
        SettingsManager.getHwDecode(context),
        SettingsManager.getThemeMode(context),
        SettingsManager.getDynamicColor(context),
        SettingsManager.getBgPlay(context)
    ) { hwDecode, themeMode, dynamicColor, bgPlay ->
        CoreSettings(hwDecode, themeMode, dynamicColor, bgPlay)
    }
    
    // 第 2 步：合并界面设置 (分两组，每组最多5个)
    private val uiSettingsFlow1 = combine(
        SettingsManager.getGestureSensitivity(context),
        SettingsManager.getThemeColorIndex(context),
        SettingsManager.getAppIcon(context)
    ) { gestureSensitivity, themeColorIndex, appIcon ->
        Triple(gestureSensitivity, themeColorIndex, appIcon)
    }
    
    private val uiSettingsFlow2 = combine(
        SettingsManager.getBottomBarFloating(context),
        SettingsManager.getBottomBarLabelMode(context),
        SettingsManager.getDisplayMode(context),
        SettingsManager.getCardAnimationEnabled(context),
        SettingsManager.getCardTransitionEnabled(context)
    ) { isBottomBarFloating, labelMode, displayMode, cardAnimation, cardTransition ->
        listOf(isBottomBarFloating, labelMode, displayMode, cardAnimation, cardTransition)
    }

    private val uiSettingsFlow3 = combine(
        SettingsManager.getCornerRadiusScale(context),
        SettingsManager.getFontScale(context),
        SettingsManager.getUIScale(context)
    ) { cornerRadius, font, ui ->
        Triple(cornerRadius, font, ui)
    }
    
    // 合并所有 UI 设置
    private val uiSettingsFlow = combine(uiSettingsFlow1, uiSettingsFlow2, uiSettingsFlow3) { ui1, ui2, ui3 ->
        // ui1: Triple(gesture, color, icon)
        // ui2: List(floating, label, display, cardAnim, cardTrans)
        // ui3: Triple(corner, font, ui)
        ExtraSettings(
            gestureSensitivity = ui1.first,
            themeColorIndex = ui1.second,
            appIcon = ui1.third,
            isBottomBarFloating = ui2[0] as Boolean,
            bottomBarLabelMode = ui2[1] as Int,
            displayMode = ui2[2] as Int,
            cardAnimationEnabled = ui2[3] as Boolean,
            cardTransitionEnabled = ui2[4] as Boolean,
            cornerRadiusScale = ui3.first,
            fontScale = ui3.second,
            uiScale = ui3.third,
            headerBlurEnabled = false, // 暂存，将在下一步合并
            bottomBarBlurEnabled = false, // 暂存
            blurIntensity = BlurIntensity.THIN // 暂存
        )
    }
    
    // 第 3 步：合并模糊设置 (3个)
    private val blurSettingsFlow = combine(
        SettingsManager.getHeaderBlurEnabled(context),
        SettingsManager.getBottomBarBlurEnabled(context),
        SettingsManager.getBlurIntensity(context)  //  添加模糊强度
    ) { headerBlur, bottomBarBlur, blurIntensity ->
        Triple(headerBlur, bottomBarBlur, blurIntensity)
    }
    
    // 第 4 步：合并 UI 和 模糊设置
    private val extraSettingsFlow = combine(uiSettingsFlow, blurSettingsFlow) { uiSettings, blur ->
        uiSettings.copy(
            headerBlurEnabled = blur.first,
            bottomBarBlurEnabled = blur.second,
            blurIntensity = blur.third
        )
    }
    
    //  第 4.5 步：合并实验性功能设置
    private val experimentalSettingsFlow = combine(
        SettingsManager.getAuto1080p(context),
        SettingsManager.getAutoSkipOpEd(context),
        SettingsManager.getPrefetchVideo(context),
        SettingsManager.getDoubleTapLike(context),
        SettingsManager.getSponsorBlockEnabled(context),
        SettingsManager.getSponsorBlockAutoSkip(context)
    ) { values ->
        ExperimentalSettings(
            auto1080p = values[0] as Boolean,
            autoSkipOpEd = values[1] as Boolean,
            prefetchVideo = values[2] as Boolean,
            doubleTapLike = values[3] as Boolean,
            sponsorBlockEnabled = values[4] as Boolean,
            sponsorBlockAutoSkip = values[5] as Boolean
        )
    }
    
    // 第 5 步：合并两组设置
    private val baseSettingsFlow = combine(coreSettingsFlow, extraSettingsFlow) { core, extra ->
        BaseSettings(
            hwDecode = core.hwDecode,
            themeMode = core.themeMode,
            dynamicColor = core.dynamicColor,
            bgPlay = core.bgPlay,
            gestureSensitivity = extra.gestureSensitivity,
            themeColorIndex = extra.themeColorIndex,
            appIcon = extra.appIcon,
            isBottomBarFloating = extra.isBottomBarFloating,
            bottomBarLabelMode = extra.bottomBarLabelMode,
            headerBlurEnabled = extra.headerBlurEnabled,
            bottomBarBlurEnabled = extra.bottomBarBlurEnabled,
            blurIntensity = extra.blurIntensity,  //  模糊强度
            displayMode = extra.displayMode,
            cardAnimationEnabled = extra.cardAnimationEnabled,
            cardTransitionEnabled = extra.cardTransitionEnabled,
            cornerRadiusScale = extra.cornerRadiusScale,
            fontScale = extra.fontScale,
            uiScale = extra.uiScale
        )
    }

    // 第 6 步：与缓存大小和实验性功能合并
    private val cacheFlow = combine(_cacheSize, _cacheBreakdown) { size, breakdown ->
        Pair(size, breakdown)
    }
    
    val state: StateFlow<SettingsUiState> = combine(
        baseSettingsFlow,
        cacheFlow,
        experimentalSettingsFlow
    ) { settings, cache, experimental ->
        SettingsUiState(
            hwDecode = settings.hwDecode,
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            bgPlay = settings.bgPlay,
            gestureSensitivity = settings.gestureSensitivity,
            themeColorIndex = settings.themeColorIndex,
            appIcon = settings.appIcon,
            isBottomBarFloating = settings.isBottomBarFloating,
            bottomBarLabelMode = settings.bottomBarLabelMode,
            headerBlurEnabled = settings.headerBlurEnabled,
            bottomBarBlurEnabled = settings.bottomBarBlurEnabled,
            blurIntensity = settings.blurIntensity,  //  模糊强度
            displayMode = settings.displayMode,
            cardAnimationEnabled = settings.cardAnimationEnabled,
            cardTransitionEnabled = settings.cardTransitionEnabled,
            cornerRadiusScale = settings.cornerRadiusScale,
            fontScale = settings.fontScale,
            uiScale = settings.uiScale,
            cacheSize = cache.first,
            cacheBreakdown = cache.second,  //  详细缓存统计
            //  实验性功能
            auto1080p = experimental.auto1080p,
            autoSkipOpEd = experimental.autoSkipOpEd,
            prefetchVideo = experimental.prefetchVideo,
            doubleTapLike = experimental.doubleTapLike,
            //  空降助手
            sponsorBlockEnabled = experimental.sponsorBlockEnabled,
            sponsorBlockAutoSkip = experimental.sponsorBlockAutoSkip
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        refreshCacheSize()
    }

    // --- 功能方法 ---

    //  优化：同时获取缓存大小和详细统计
    fun refreshCacheSize() {
        viewModelScope.launch { 
            val breakdown = CacheUtils.getCacheBreakdown(context)
            _cacheSize.value = breakdown.format()
            _cacheBreakdown.value = breakdown
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            CacheUtils.clearAllCache(context)
            // 清理后立即刷新
            val breakdown = CacheUtils.getCacheBreakdown(context)
            _cacheSize.value = breakdown.format()
            _cacheBreakdown.value = breakdown
        }
    }

    fun toggleHwDecode(value: Boolean) { viewModelScope.launch { SettingsManager.setHwDecode(context, value) } }
    fun setThemeMode(mode: AppThemeMode) { 
        viewModelScope.launch { 
            SettingsManager.setThemeMode(context, mode)
        } 
    }
    fun toggleDynamicColor(value: Boolean) { viewModelScope.launch { SettingsManager.setDynamicColor(context, value) } }
    fun toggleBgPlay(value: Boolean) { viewModelScope.launch { SettingsManager.setBgPlay(context, value) } }
    //  [新增] 手势灵敏度和主题色
    fun setGestureSensitivity(value: Float) { viewModelScope.launch { SettingsManager.setGestureSensitivity(context, value) } }
    fun setThemeColorIndex(index: Int) { 
        viewModelScope.launch { 
            SettingsManager.setThemeColorIndex(context, index)
            //  选择自定义主题色时，自动关闭动态取色
            if (index != 0) {
                SettingsManager.setDynamicColor(context, false)
            }
        }
    }

    //  [新增] 切换应用图标
    fun setAppIcon(iconKey: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. 保存偏好
            SettingsManager.setAppIcon(context, iconKey)
            
            // 2. 应用 Alias
            val pm = context.packageManager
            val packageName = context.packageName
            
            // alias 映射 - 必须与 AndroidManifest.xml 中声明的完全一致
            // [修复] 键必须与 IconSettingsScreen.kt 中的 IconOption.key 一致
            val allAliases = listOf(
                // 默认系列
                "default" to "${packageName}.MainActivityAliasYuki",  // 默认使用 Yuki 图标
                "icon_blue" to "${packageName}.MainActivityAliasBlue",
                "icon_neon" to "${packageName}.MainActivityAliasNeon",
                "icon_retro" to "${packageName}.MainActivityAliasRetro",
                "icon_3d" to "${packageName}.MainActivityAlias3D",
                // 特色系列
                "icon_anime" to "${packageName}.MainActivityAliasAnime",
                "icon_flat" to "${packageName}.MainActivityAliasFlat",
                "icon_telegram_blue" to "${packageName}.MainActivityAliasTelegramBlue",
                "icon_telegram_green" to "${packageName}.MainActivityAliasGreen",
                "icon_telegram_pink" to "${packageName}.MainActivityAliasPink",
                "icon_telegram_purple" to "${packageName}.MainActivityAliasPurple",
                "icon_telegram_dark" to "${packageName}.MainActivityAliasDark",
                // 兼容旧键名 (向后兼容)
                "Yuki" to "${packageName}.MainActivityAliasYuki",
                "Anime" to "${packageName}.MainActivityAliasAnime",
                "Tv" to "${packageName}.MainActivityAliasTv",
                "Headphone" to "${packageName}.MainActivityAliasHeadphone",
                "3D" to "${packageName}.MainActivityAlias3D",
                "Blue" to "${packageName}.MainActivityAliasBlue",
                "Retro" to "${packageName}.MainActivityAliasRetro",
                "Flat" to "${packageName}.MainActivityAliasFlat",
                "Neon" to "${packageName}.MainActivityAliasNeon",
                "Telegram Blue" to "${packageName}.MainActivityAliasTelegramBlue"
            )
            
            // 找到需要启用的 alias
            val targetAlias = allAliases.find { it.first == iconKey }?.second
                ?: "${packageName}.MainActivityAlias3D" // 默认 3D 图标
            
            // [修复] 获取所有唯一的 alias 名称（去重，因为向后兼容映射可能有重复）
            val allUniqueAliases = allAliases.map { it.second }.distinct()
            
            android.util.Log.d("SettingsViewModel", "Switching icon to: $iconKey -> $targetAlias")
            
            try {
                // 第一步：先启用目标 alias（确保始终有一个活动入口点）
                // ⚠️ [修复] 在尝试杀死进程的操作前，再次延迟，确保 DataStore/SharedPrefs 完全写入磁盘
                kotlinx.coroutines.delay(100)
                
                pm.setComponentEnabledSetting(
                    android.content.ComponentName(packageName, targetAlias),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                android.util.Log.d("SettingsViewModel", "Enabled alias: $targetAlias")
                
                // 第二步：短暂延迟，让启动器处理启用操作
                kotlinx.coroutines.delay(300)
                
                // 第三步：禁用所有其他 alias
                allUniqueAliases.filter { it != targetAlias }.forEach { aliasFullName ->
                    try {
                        pm.setComponentEnabledSetting(
                            android.content.ComponentName(packageName, aliasFullName),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Failed to disable alias: $aliasFullName", e)
                    }
                }
                android.util.Log.d("SettingsViewModel", "Icon switch completed: $iconKey")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to switch app icon to $iconKey", e)
            }
        }
    }

    //  [新增] 切换底栏样式
    fun toggleBottomBarFloating(value: Boolean) { viewModelScope.launch { SettingsManager.setBottomBarFloating(context, value) } }
    
    //  [新增] 底栏显示模式 (0=图标+文字, 1=仅图标, 2=仅文字)
    fun setBottomBarLabelMode(mode: Int) { viewModelScope.launch { SettingsManager.setBottomBarLabelMode(context, mode) } }
    
    //  [新增] 模糊效果开关
    fun toggleHeaderBlur(value: Boolean) { viewModelScope.launch { SettingsManager.setHeaderBlurEnabled(context, value) } }
    fun toggleBottomBarBlur(value: Boolean) { viewModelScope.launch { SettingsManager.setBottomBarBlurEnabled(context, value) } }
    fun setBlurIntensity(intensity: BlurIntensity) { viewModelScope.launch { SettingsManager.setBlurIntensity(context, intensity) } }  //  模糊强度设置
    
    //  [新增] 卡片进场动画开关
    fun toggleCardAnimation(value: Boolean) { viewModelScope.launch { SettingsManager.setCardAnimationEnabled(context, value) } }
    
    //  [新增] 卡片过渡动画开关
    fun toggleCardTransition(value: Boolean) { viewModelScope.launch { SettingsManager.setCardTransitionEnabled(context, value) } }
    
    //  [新增] 首页展示模式
    fun setDisplayMode(mode: Int) { 
        viewModelScope.launch { 
            // 兼容旧的 shared preferences
            context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putInt("display_mode", mode).apply()
            // 触发 flow 更新 (如果需要，或者仅仅依赖 prefs 监听? 这里简化处理，假设 ViewModel 只负责写，读在 flow 中)
            // 实际上这里的 flow 是基于 SettingsManager (DataStore) 的。
            // 如果 display_mode 还是 SharedPreferences，我们需要一个 flow 来通过 DataStore 或者手动构建。
            //为了简单统一，建议迁移到 SettingsManager。但为了不破坏 HomeScreen 读取，我们先保持 Prefs，
            // 并在 SettingsManager 中增加对 display_mode 的支持 (或者直接在这里用 MutableStateFlow 桥接?)
            // 鉴于 HomeScreen 可能直接读 Prefs，我们这里只需写 Prefs。
            // 但为了 UI 响应，我们需要通知 UIState。
            // 由于 SettingsManager 目前不管理 display_mode，我们需要添加它。
            // 既然要 refactor，就彻底点。
            SettingsManager.setDisplayMode(context, mode)
        } 
    }
    
    //  [新增] 实验性功能
    fun toggleAuto1080p(value: Boolean) { viewModelScope.launch { SettingsManager.setAuto1080p(context, value) } }
    fun toggleAutoSkipOpEd(value: Boolean) { viewModelScope.launch { SettingsManager.setAutoSkipOpEd(context, value) } }
    fun togglePrefetchVideo(value: Boolean) { viewModelScope.launch { SettingsManager.setPrefetchVideo(context, value) } }
    fun toggleDoubleTapLike(value: Boolean) { viewModelScope.launch { SettingsManager.setDoubleTapLike(context, value) } }
    
    //  [新增] 空降助手
    fun toggleSponsorBlock(value: Boolean) { viewModelScope.launch { SettingsManager.setSponsorBlockEnabled(context, value) } }
    fun toggleSponsorBlockAutoSkip(value: Boolean) { viewModelScope.launch { SettingsManager.setSponsorBlockAutoSkip(context, value) } }
    
    //  [新增] UI 自定义设置
    fun setCornerRadiusScale(value: Float) { viewModelScope.launch { SettingsManager.setCornerRadiusScale(context, value) } }
    fun setFontScale(value: Float) { viewModelScope.launch { SettingsManager.setFontScale(context, value) } }
    fun setUIScale(value: Float) { viewModelScope.launch { SettingsManager.setUIScale(context, value) } }
}

// Move DisplayMode enum here to be accessible
enum class DisplayMode(val title: String, val description: String, val value: Int) {
    DoubleGrid(title = "双列网格", description = "经典双列瀑布流布局", value = 0),
    SingleColumn(title = "单列视频", description = "类似信息流的单列布局", value = 1),
    DoubleColumn(title = "双列视频", description = "双列大图视频卡片模式", value = 2)
}