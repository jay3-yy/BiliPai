// 文件路径: feature/settings/SettingsViewModel.kt
package com.android.purebilibili.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.DEFAULT_APP_ICON_KEY
import com.android.purebilibili.core.store.AppIconAppearance
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.BottomBarSearchAutoExpandMode
import com.android.purebilibili.core.store.BottomBarSearchLayoutMode
import com.android.purebilibili.core.store.HomeFeedCardWidthPreset
import com.android.purebilibili.core.store.LiquidGlassMode
import com.android.purebilibili.core.store.LiquidGlassAdvancedSettings
import com.android.purebilibili.core.store.LiquidGlassReadabilityMode
import com.android.purebilibili.core.store.home.LiquidGlassSettingsStore
import com.android.purebilibili.core.store.allManagedAppIconLauncherAliases
import com.android.purebilibili.core.store.resolveDefaultLiquidGlassStrength
import com.android.purebilibili.core.store.resolveLegacyLiquidGlassProgress
import com.android.purebilibili.core.store.resolveLegacyLiquidGlassMode
import com.android.purebilibili.core.store.normalizeAppIconKey
import com.android.purebilibili.core.store.resolveAppIconLauncherAlias
import com.android.purebilibili.core.store.supportsAppIconAppearance
import com.android.purebilibili.core.theme.AppFontWeightPreset
import com.android.purebilibili.core.theme.AppFontSizePreset
import com.android.purebilibili.core.theme.AppUiScalePreset
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionSpeed
import com.android.purebilibili.core.util.CacheClearTarget
import com.android.purebilibili.core.util.CacheUtils
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class SettingsDiagnosticsLoadState {
    NOT_LOADED,
    LOADING,
    LOADED,
}

internal fun shouldStartSettingsDiagnostics(
    loadState: SettingsDiagnosticsLoadState,
    jobActive: Boolean,
): Boolean = loadState != SettingsDiagnosticsLoadState.LOADED && !jobActive

data class SettingsUiState(
    val themeSelection: AppUiStyle = AppUiStyle.MATERIAL3,
    val hwDecode: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val darkThemeSchedule: DarkThemeSchedule = DarkThemeSchedule(),
    val darkThemeStyle: DarkThemeStyle = DarkThemeStyle.DEFAULT,
    val appLanguage: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    val md3ColorSource: Md3ColorSource = Md3ColorSource.FOLLOW_WALLPAPER,
    val md3CustomColorHex: String = "#007AFF",
    val colorStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    val appFontSizePreset: AppFontSizePreset = AppFontSizePreset.DEFAULT,
    val appFontWeightPreset: AppFontWeightPreset = AppFontWeightPreset.FOLLOW_THEME,
    val appFontFileName: String = "",
    val appFontDisplayName: String = "",
    val appUiScalePreset: AppUiScalePreset = AppUiScalePreset.STANDARD,
    val appDpiOverridePercent: Int = 0,
    val bgPlay: Boolean = false,
    val gestureSensitivity: Float = 1.0f,
    val themeColorIndex: Int = 0,
    val appIcon: String = DEFAULT_APP_ICON_KEY,
    val appIconStyle: AppIconStyle = AppIconStyle.AUTO,
    val appListItemStyle: AppListItemStyle = AppListItemStyle.AUTO,
    val isBottomBarFloating: Boolean = true,
    val bottomBarLabelMode: Int = 1,  // 0=图标+文字, 1=仅图标, 2=仅文字
    val headerBlurEnabled: Boolean = true,
    val progressiveTopBlurEnabled: Boolean = true,
    val progressiveTopFadeEnabled: Boolean = true,
    val bottomBarBlurEnabled: Boolean = true,
    val blurIntensity: BlurIntensity = BlurIntensity.THIN,  //  模糊强度
    val displayMode: Int = 0,
    val cardAnimationEnabled: Boolean = false,     //  卡片进场动画（默认关闭）
    val cardTransitionEnabled: Boolean = false,    //  卡片过渡动画（默认关闭）
    val videoSharedTransitionSpeed: VideoSharedTransitionSpeed = VideoSharedTransitionSpeed.STANDARD,
    val videoSharedTransitionCustomDurationMillis: Int =
        VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS,
    val smartVisualGuardEnabled: Boolean = false, // [Retired] 智能流畅优先已下线
    val cacheSize: String = "计算中…",
    val cacheBreakdown: CacheUtils.CacheBreakdown? = null,  //  详细缓存统计
    val installedApkSha256: String? = null,
    val currentReleaseEvidence: AppUpdateCheckResult? = null,
    val diagnosticsLoadState: SettingsDiagnosticsLoadState =
        SettingsDiagnosticsLoadState.NOT_LOADED,
    //  实验性功能
    val auto1080p: Boolean = true,
    val autoSkipOpEd: Boolean = false,
    val prefetchVideo: Boolean = false,
    val doubleTapLike: Boolean = true,

    //  空降助手
    val sponsorBlockEnabled: Boolean = false,
    val sponsorBlockAutoSkip: Boolean = true,
    // [新增] 触感反馈
    val hapticFeedbackEnabled: Boolean = true,
    val topBarLiquidGlassEnabled: Boolean = false,
    val homeSearchLiquidGlassEnabled: Boolean = false,
    val bottomBarLiquidGlassEnabled: Boolean = true,
    val bottomBarSearchEnabled: Boolean = false,
    val bottomBarSearchAutoExpandMode: BottomBarSearchAutoExpandMode =
        BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
    val bottomBarSearchLayoutMode: BottomBarSearchLayoutMode =
        BottomBarSearchLayoutMode.FULL_DOCK,
    val androidNativeLiquidGlassEnabled: Boolean = true,
    val liquidGlassStyle: com.android.purebilibili.core.store.LiquidGlassStyle = com.android.purebilibili.core.store.LiquidGlassStyle.CLASSIC, // [New]
    val liquidGlassMode: LiquidGlassMode = LiquidGlassMode.BALANCED,
    val liquidGlassStrength: Float = 0.52f,
    val liquidGlassProgress: Float = 0.5f,
    // [New] 平板导航模式
    val tabletUseSidebar: Boolean = false,
    val isHeaderCollapseEnabled: Boolean = true,
    val gridColumnCount: Int = 0, // [New]
    val homeFeedCardWidthPreset: HomeFeedCardWidthPreset = HomeFeedCardWidthPreset.AUTO
) {
    val isLiquidGlassEnabled: Boolean
        get() = androidNativeLiquidGlassEnabled
}

// 内部数据类，用于分批合并流
private data class CoreSettings(
    val themeSelection: AppUiStyle,
    val hwDecode: Boolean,
    val themeMode: AppThemeMode,
    val darkThemeSchedule: DarkThemeSchedule,
    val darkThemeStyle: DarkThemeStyle,
    val appLanguage: AppLanguage,
    val dynamicColor: Boolean,
    val md3ColorSource: Md3ColorSource,
    val md3CustomColorHex: String,
    val colorStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    val bgPlay: Boolean
)

data class ExtraSettings(
    val gestureSensitivity: Float,
    val themeColorIndex: Int,
    val appIcon: String,
    val appIconStyle: AppIconStyle,
    val appListItemStyle: AppListItemStyle,
    val appFontSizePreset: AppFontSizePreset,
    val appFontWeightPreset: AppFontWeightPreset,
    val appFontFileName: String,
    val appFontDisplayName: String,
    val appUiScalePreset: AppUiScalePreset,
    val appDpiOverridePercent: Int,
    val isBottomBarFloating: Boolean,
    val bottomBarLabelMode: Int,
    val headerBlurEnabled: Boolean,
    val progressiveTopBlurEnabled: Boolean,
    val progressiveTopFadeEnabled: Boolean,
    val bottomBarBlurEnabled: Boolean,
    val blurIntensity: BlurIntensity,  //  添加模糊强度
    val displayMode: Int,
    val cardAnimationEnabled: Boolean,
    val cardTransitionEnabled: Boolean,
    val videoSharedTransitionSpeed: VideoSharedTransitionSpeed,
    val videoSharedTransitionCustomDurationMillis: Int,
    val smartVisualGuardEnabled: Boolean,
    val hapticFeedbackEnabled: Boolean, // [Restored]
    val topBarLiquidGlassEnabled: Boolean = false,
    val homeSearchLiquidGlassEnabled: Boolean = false,
    val bottomBarLiquidGlassEnabled: Boolean = true,
    val bottomBarSearchEnabled: Boolean = false,
    val bottomBarSearchAutoExpandMode: BottomBarSearchAutoExpandMode =
        BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
    val bottomBarSearchLayoutMode: BottomBarSearchLayoutMode =
        BottomBarSearchLayoutMode.FULL_DOCK,
    val androidNativeLiquidGlassEnabled: Boolean = true,
    val liquidGlassStyle: com.android.purebilibili.core.store.LiquidGlassStyle, // [New]
    val liquidGlassMode: LiquidGlassMode, // [New]
    val liquidGlassStrength: Float, // [New]
    val liquidGlassProgress: Float, // [New]
    val tabletUseSidebar: Boolean, // [New]
    val isHeaderCollapseEnabled: Boolean,
    val gridColumnCount: Int, // [New]
    val homeFeedCardWidthPreset: HomeFeedCardWidthPreset
)

private data class BlurSettings(
    val headerBlur: Boolean,
    val progressiveTopBlur: Boolean,
    val progressiveTopFade: Boolean,
    val bottomBarBlur: Boolean,
    val blurIntensity: BlurIntensity
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
    val themeSelection: AppUiStyle,
    val hwDecode: Boolean,
    val themeMode: AppThemeMode,
    val darkThemeSchedule: DarkThemeSchedule,
    val darkThemeStyle: DarkThemeStyle,
    val appLanguage: AppLanguage,
    val dynamicColor: Boolean,
    val md3ColorSource: Md3ColorSource,
    val md3CustomColorHex: String,
    val colorStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    val appFontSizePreset: AppFontSizePreset,
    val appFontWeightPreset: AppFontWeightPreset,
    val appFontFileName: String,
    val appFontDisplayName: String,
    val appUiScalePreset: AppUiScalePreset,
    val appDpiOverridePercent: Int,
    val bgPlay: Boolean,
    val gestureSensitivity: Float,
    val themeColorIndex: Int,
    val appIcon: String,
    val appIconStyle: AppIconStyle,
    val appListItemStyle: AppListItemStyle,
    val isBottomBarFloating: Boolean,
    val bottomBarLabelMode: Int,
    val headerBlurEnabled: Boolean,
    val progressiveTopBlurEnabled: Boolean,
    val progressiveTopFadeEnabled: Boolean,
    val bottomBarBlurEnabled: Boolean,
    val blurIntensity: BlurIntensity,  //  模糊强度
    val displayMode: Int, //  新增
    val cardAnimationEnabled: Boolean, //  卡片进场动画
    val cardTransitionEnabled: Boolean, //  卡片过渡动画
    val videoSharedTransitionSpeed: VideoSharedTransitionSpeed,
    val videoSharedTransitionCustomDurationMillis: Int,
    val smartVisualGuardEnabled: Boolean, // [New]
    val hapticFeedbackEnabled: Boolean, // [新增]
    val topBarLiquidGlassEnabled: Boolean,
    val homeSearchLiquidGlassEnabled: Boolean,
    val bottomBarLiquidGlassEnabled: Boolean,
    val bottomBarSearchEnabled: Boolean,
    val bottomBarSearchAutoExpandMode: BottomBarSearchAutoExpandMode,
    val bottomBarSearchLayoutMode: BottomBarSearchLayoutMode,
    val androidNativeLiquidGlassEnabled: Boolean,
    val liquidGlassStyle: com.android.purebilibili.core.store.LiquidGlassStyle, // [New]
    val liquidGlassMode: LiquidGlassMode, // [New]
    val liquidGlassStrength: Float, // [New]
    val liquidGlassProgress: Float, // [New]
    val tabletUseSidebar: Boolean, // [New]
    val isHeaderCollapseEnabled: Boolean,
    val gridColumnCount: Int, // [New]
    val homeFeedCardWidthPreset: HomeFeedCardWidthPreset
)

private fun <T> Flow<T>.asAnyFlow(): Flow<Any?> = map { it }


class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val searchHistory = com.android.purebilibili.core.store.SettingsSearchHistoryStore.observe(application)

    fun recordSearchQuery(query: String) {
        viewModelScope.launch {
            if (!SettingsManager.getPrivacyModeEnabled(getApplication()).first()) {
                com.android.purebilibili.core.store.SettingsSearchHistoryStore.record(getApplication(), query)
            }
        }
    }

    fun deleteSearchHistory(query: String) {
        viewModelScope.launch {
            com.android.purebilibili.core.store.SettingsSearchHistoryStore.delete(getApplication(), query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            com.android.purebilibili.core.store.SettingsSearchHistoryStore.clear(getApplication())
        }
    }

    private val context = application.applicationContext

    private data class DiagnosticsState(
        val installedApkSha256: String? = null,
        val currentReleaseEvidence: AppUpdateCheckResult? = null,
        val loadState: SettingsDiagnosticsLoadState = SettingsDiagnosticsLoadState.NOT_LOADED,
    )

    private data class UiSettingsGroup1(
        val gestureSensitivity: Float,
        val themeColorIndex: Int,
        val appIcon: String,
        val appIconStyle: AppIconStyle,
        val appListItemStyle: AppListItemStyle,
        val appFontSizePreset: AppFontSizePreset,
        val appFontWeightPreset: AppFontWeightPreset,
        val appFontFileName: String,
        val appFontDisplayName: String,
        val appUiScalePreset: AppUiScalePreset,
        val appDpiOverridePercent: Int
    )

    // 本地状态流：缓存大小
    private val _cacheSize = MutableStateFlow("计算中…")
    private val _cacheBreakdown = MutableStateFlow<CacheUtils.CacheBreakdown?>(null)
    private val _diagnosticsState = MutableStateFlow(DiagnosticsState())
    private var diagnosticsLoadJob: Job? = null

    //  [核心修复] 分步合并，解决 combine 参数限制报错
    // 第 1 步：合并前 4 个设置
    private val coreSettingsFlow = combine(
        SettingsManager.getUiStyle(context).asAnyFlow(),
        SettingsManager.getHwDecode(context).asAnyFlow(),
        SettingsManager.getThemeMode(context).asAnyFlow(),
        SettingsManager.getDarkThemeStyle(context).asAnyFlow(),
        SettingsManager.getAppLanguage(context).asAnyFlow(),
        SettingsManager.getDynamicColor(context).asAnyFlow(),
        SettingsManager.getMd3ColorSource(context).asAnyFlow(),
        SettingsManager.getMd3CustomColorHex(context).asAnyFlow(),
        SettingsManager.getThemeColorStyle(context).asAnyFlow(),
        SettingsManager.getThemeColorSpec(context).asAnyFlow(),
        SettingsManager.getMiniPlayerMode(context)
            .map { it != SettingsManager.MiniPlayerMode.OFF }
            .asAnyFlow(),
        SettingsManager.getDarkThemeSchedule(context).asAnyFlow()
    ) { values ->
        CoreSettings(
            themeSelection = values[0] as AppUiStyle,
            hwDecode = values[1] as Boolean,
            themeMode = values[2] as AppThemeMode,
            darkThemeSchedule = values[11] as DarkThemeSchedule,
            darkThemeStyle = values[3] as DarkThemeStyle,
            appLanguage = values[4] as AppLanguage,
            dynamicColor = values[5] as Boolean,
            md3ColorSource = values[6] as Md3ColorSource,
            md3CustomColorHex = values[7] as String,
            colorStyle = values[8] as PaletteStyle,
            colorSpec = values[9] as ColorSpec.SpecVersion,
            bgPlay = values[10] as Boolean
        )
    }
    
    // 第 2 步：合并界面设置 (分两组，每组最多5个)
    private val uiSettingsFlow1 = combine(
        SettingsManager.getGestureSensitivity(context).asAnyFlow(),
        SettingsManager.getThemeColorIndex(context).asAnyFlow(),
        SettingsManager.getAppIcon(context).asAnyFlow(),
        SettingsManager.getAppIconStyle(context).asAnyFlow(),
        SettingsManager.getAppListItemStyle(context).asAnyFlow(),
        SettingsManager.getAppFontSizePreset(context).asAnyFlow(),
        SettingsManager.getAppFontWeightPreset(context).asAnyFlow(),
        SettingsManager.getAppFontFileName(context).asAnyFlow(),
        SettingsManager.getAppFontDisplayName(context).asAnyFlow(),
        SettingsManager.getAppUiScalePreset(context).asAnyFlow(),
        SettingsManager.getAppDpiOverridePercent(context).asAnyFlow()
    ) { values ->
        UiSettingsGroup1(
            gestureSensitivity = values[0] as Float,
            themeColorIndex = values[1] as Int,
            appIcon = values[2] as String,
            appIconStyle = values[3] as AppIconStyle,
            appListItemStyle = values[4] as AppListItemStyle,
            appFontSizePreset = values[5] as AppFontSizePreset,
            appFontWeightPreset = values[6] as AppFontWeightPreset,
            appFontFileName = values[7] as String,
            appFontDisplayName = values[8] as String,
            appUiScalePreset = values[9] as AppUiScalePreset,
            appDpiOverridePercent = values[10] as Int
        )
    }
    
    private val uiSettingsFlow2 = combine(
        SettingsManager.getBottomBarFloating(context).asAnyFlow(),
        SettingsManager.getBottomBarLabelMode(context).asAnyFlow(),
        SettingsManager.getDisplayMode(context).asAnyFlow(),
        SettingsManager.getCardAnimationEnabled(context).asAnyFlow(), // [Restored]
        SettingsManager.getCardTransitionEnabled(context).asAnyFlow(),
        SettingsManager.getVideoSharedTransitionSpeed(context).asAnyFlow(),
        SettingsManager.getVideoSharedTransitionCustomDurationMillis(context).asAnyFlow(),
        SettingsManager.getSmartVisualGuardEnabled(context).asAnyFlow(), // [New]
        SettingsManager.getHapticFeedbackEnabled(context).asAnyFlow(), // [新增]
        SettingsManager.getTopBarLiquidGlassEnabled(context).asAnyFlow(),
        SettingsManager.getHomeSearchLiquidGlassEnabled(context).asAnyFlow(),
        SettingsManager.getBottomBarLiquidGlassEnabled(context).asAnyFlow(),
        SettingsManager.getBottomBarSearchEnabled(context).asAnyFlow(),
        SettingsManager.getBottomBarSearchAutoExpandMode(context).asAnyFlow(),
        SettingsManager.getBottomBarSearchLayoutMode(context).asAnyFlow(),
        SettingsManager.getAndroidNativeLiquidGlassEnabled(context).asAnyFlow(),
        SettingsManager.getLiquidGlassStyle(context).asAnyFlow(), // [New]
        SettingsManager.getLiquidGlassMode(context).asAnyFlow(), // [New]
        SettingsManager.getLiquidGlassStrength(context).asAnyFlow(), // [New]
        SettingsManager.getLiquidGlassProgress(context).asAnyFlow(), // [New]
        SettingsManager.getTabletUseSidebar(context).asAnyFlow(), // [New]
        SettingsManager.getHeaderCollapseEnabled(context).asAnyFlow(),
        SettingsManager.getGridColumnCount(context).asAnyFlow(), // [New]
        SettingsManager.getHomeFeedCardWidthPreset(context).asAnyFlow()
    ) { values ->
        val isBottomBarFloating = values[0] as Boolean
        val labelMode = values[1] as Int
        val displayMode = values[2] as Int
        val cardAnimation = values[3] as Boolean
        val cardTransition = values[4] as Boolean
        val videoSharedTransitionSpeed = values[5] as VideoSharedTransitionSpeed
        val videoSharedTransitionCustomDurationMillis = values[6] as Int
        val smartVisualGuard = values[7] as Boolean
        val hapticFeedback = values[8] as Boolean
        val topBarLiquidGlass = values[9] as Boolean
        val homeSearchLiquidGlass = values[10] as Boolean
        val bottomBarLiquidGlass = values[11] as Boolean
        val bottomBarSearch = values[12] as Boolean
        val bottomBarSearchAutoExpandMode = values[13] as BottomBarSearchAutoExpandMode
        val bottomBarSearchLayoutMode = values[14] as BottomBarSearchLayoutMode
        val androidNativeLiquidGlass = values[15] as Boolean
        val liquidGlassStyle = values[16] as com.android.purebilibili.core.store.LiquidGlassStyle
        val liquidGlassMode = values[17] as LiquidGlassMode
        val liquidGlassStrength = values[18] as Float
        val liquidGlassProgress = values[19] as Float
        val tabletUseSidebar = values[20] as Boolean
        val headerCollapse = values[21] as Boolean
        val gridColumnCount = values[22] as Int
        val homeFeedCardWidthPreset = values[23] as HomeFeedCardWidthPreset
        
        data class Ui2(
            val f: Boolean,
            val l: Int,
            val d: Int,
            val ca: Boolean,
            val ct: Boolean,
            val vsts: VideoSharedTransitionSpeed,
            val vstcdm: Int,
            val svg: Boolean,
            val h: Boolean,
            val tlg: Boolean,
            val hslg: Boolean,
            val blg: Boolean,
            val bbs: Boolean,
            val bbsam: BottomBarSearchAutoExpandMode,
            val bbslm: BottomBarSearchLayoutMode,
            val anlg: Boolean,
            val lgs: com.android.purebilibili.core.store.LiquidGlassStyle,
            val lgm: LiquidGlassMode,
            val lgt: Float,
            val lgp: Float,
            val tus: Boolean,
            val hc: Boolean,
            val gcc: Int,
            val hfcwp: HomeFeedCardWidthPreset
        )
        Ui2(
            isBottomBarFloating,
            labelMode,
            displayMode,
            cardAnimation,
            cardTransition,
            videoSharedTransitionSpeed,
            videoSharedTransitionCustomDurationMillis,
            smartVisualGuard,
            hapticFeedback,
            topBarLiquidGlass,
            homeSearchLiquidGlass,
            bottomBarLiquidGlass,
            bottomBarSearch,
            bottomBarSearchAutoExpandMode,
            bottomBarSearchLayoutMode,
            androidNativeLiquidGlass,
            liquidGlassStyle,
            liquidGlassMode,
            liquidGlassStrength,
            liquidGlassProgress,
            tabletUseSidebar,
            headerCollapse,
            gridColumnCount,
            homeFeedCardWidthPreset
        )
    }

    // 合并所有 UI 设置
    private val uiSettingsFlow = combine(uiSettingsFlow1, uiSettingsFlow2) { ui1, ui2 ->
        // ui2: Ui2 class
        ExtraSettings(
            gestureSensitivity = ui1.gestureSensitivity,
            themeColorIndex = ui1.themeColorIndex,
            appIcon = ui1.appIcon,
            appIconStyle = ui1.appIconStyle,
            appListItemStyle = ui1.appListItemStyle,
            appFontSizePreset = ui1.appFontSizePreset,
            appFontWeightPreset = ui1.appFontWeightPreset,
            appFontFileName = ui1.appFontFileName,
            appFontDisplayName = ui1.appFontDisplayName,
            appUiScalePreset = ui1.appUiScalePreset,
            appDpiOverridePercent = ui1.appDpiOverridePercent,
            isBottomBarFloating = ui2.f,
            bottomBarLabelMode = ui2.l,
            displayMode = ui2.d,
            cardAnimationEnabled = ui2.ca,
            cardTransitionEnabled = ui2.ct,
            videoSharedTransitionSpeed = ui2.vsts,
            videoSharedTransitionCustomDurationMillis = ui2.vstcdm,
            smartVisualGuardEnabled = ui2.svg,
            hapticFeedbackEnabled = ui2.h, // [新增]
            topBarLiquidGlassEnabled = ui2.tlg,
            homeSearchLiquidGlassEnabled = ui2.hslg,
            bottomBarLiquidGlassEnabled = ui2.blg,
            bottomBarSearchEnabled = ui2.bbs,
            bottomBarSearchAutoExpandMode = ui2.bbsam,
            bottomBarSearchLayoutMode = ui2.bbslm,
            androidNativeLiquidGlassEnabled = ui2.anlg,
            liquidGlassStyle = ui2.lgs, // [New]
            liquidGlassMode = ui2.lgm, // [New]
            liquidGlassStrength = ui2.lgt, // [New]
            liquidGlassProgress = ui2.lgp, // [New]
            tabletUseSidebar = ui2.tus, // [New]
            isHeaderCollapseEnabled = ui2.hc,
            gridColumnCount = ui2.gcc, // [New]
            homeFeedCardWidthPreset = ui2.hfcwp,
            headerBlurEnabled = false, // 暂存，将在下一步合并
            progressiveTopBlurEnabled = true, // 暂存
            progressiveTopFadeEnabled = true, // 暂存
            bottomBarBlurEnabled = false, // 暂存
            blurIntensity = BlurIntensity.THIN // 暂存
        )
    }
    
    // 第 3 步：合并模糊设置 (5个)
    private val blurSettingsFlow = combine(
        SettingsManager.getHeaderBlurEnabled(context),
        SettingsManager.getProgressiveTopBlurEnabled(context),
        SettingsManager.getProgressiveTopFadeEnabled(context),
        SettingsManager.getBottomBarBlurEnabled(context),
        SettingsManager.getBlurIntensity(context)  //  添加模糊强度
    ) { headerBlur, progressiveTopBlur, progressiveTopFade, bottomBarBlur, blurIntensity ->
        BlurSettings(headerBlur, progressiveTopBlur, progressiveTopFade, bottomBarBlur, blurIntensity)
    }
    
    // 第 4 步：合并 UI 和 模糊设置
    private val extraSettingsFlow = combine(uiSettingsFlow, blurSettingsFlow) { uiSettings, blur ->
        uiSettings.copy(
            headerBlurEnabled = blur.headerBlur,
            progressiveTopBlurEnabled = blur.progressiveTopBlur,
            progressiveTopFadeEnabled = blur.progressiveTopFade,
            bottomBarBlurEnabled = blur.bottomBarBlur,
            blurIntensity = blur.blurIntensity
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
            auto1080p = values[0],
            autoSkipOpEd = values[1],
            prefetchVideo = values[2],
            doubleTapLike = values[3],
            sponsorBlockEnabled = values[4],
            sponsorBlockAutoSkip = values[5]
        )
    }
    
    // 第 5 步：合并两组设置
    private val baseSettingsFlow = combine(coreSettingsFlow, extraSettingsFlow) { core, extra ->
        BaseSettings(
            themeSelection = core.themeSelection,
            hwDecode = core.hwDecode,
            themeMode = core.themeMode,
            darkThemeSchedule = core.darkThemeSchedule,
            darkThemeStyle = core.darkThemeStyle,
            appLanguage = core.appLanguage,
            dynamicColor = core.dynamicColor,
            md3ColorSource = core.md3ColorSource,
            md3CustomColorHex = core.md3CustomColorHex,
            colorStyle = core.colorStyle,
            colorSpec = core.colorSpec,
            appFontSizePreset = extra.appFontSizePreset,
            appFontWeightPreset = extra.appFontWeightPreset,
            appFontFileName = extra.appFontFileName,
            appFontDisplayName = extra.appFontDisplayName,
            appUiScalePreset = extra.appUiScalePreset,
            appDpiOverridePercent = extra.appDpiOverridePercent,
            bgPlay = core.bgPlay,
            gestureSensitivity = extra.gestureSensitivity,
            themeColorIndex = extra.themeColorIndex,
            appIcon = extra.appIcon,
            appIconStyle = extra.appIconStyle,
            appListItemStyle = extra.appListItemStyle,
            isBottomBarFloating = extra.isBottomBarFloating,
            bottomBarLabelMode = extra.bottomBarLabelMode,
            headerBlurEnabled = extra.headerBlurEnabled,
            progressiveTopBlurEnabled = extra.progressiveTopBlurEnabled,
            progressiveTopFadeEnabled = extra.progressiveTopFadeEnabled,
            bottomBarBlurEnabled = extra.bottomBarBlurEnabled,
            blurIntensity = extra.blurIntensity,  //  模糊强度
            displayMode = extra.displayMode,
            cardAnimationEnabled = extra.cardAnimationEnabled,
            cardTransitionEnabled = extra.cardTransitionEnabled,
            videoSharedTransitionSpeed = extra.videoSharedTransitionSpeed,
            videoSharedTransitionCustomDurationMillis =
                extra.videoSharedTransitionCustomDurationMillis,
            smartVisualGuardEnabled = extra.smartVisualGuardEnabled,
            hapticFeedbackEnabled = extra.hapticFeedbackEnabled, // [新增]
            topBarLiquidGlassEnabled = extra.topBarLiquidGlassEnabled,
            homeSearchLiquidGlassEnabled = extra.homeSearchLiquidGlassEnabled,
            bottomBarLiquidGlassEnabled = extra.bottomBarLiquidGlassEnabled,
            bottomBarSearchEnabled = extra.bottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = extra.bottomBarSearchAutoExpandMode,
            bottomBarSearchLayoutMode = extra.bottomBarSearchLayoutMode,
            androidNativeLiquidGlassEnabled = extra.androidNativeLiquidGlassEnabled,
            liquidGlassStyle = extra.liquidGlassStyle, // [New]
            liquidGlassMode = extra.liquidGlassMode, // [New]
            liquidGlassStrength = extra.liquidGlassStrength, // [New]
            liquidGlassProgress = extra.liquidGlassProgress, // [New]
            tabletUseSidebar = extra.tabletUseSidebar, // [New]
            isHeaderCollapseEnabled = extra.isHeaderCollapseEnabled,
            gridColumnCount = extra.gridColumnCount, // [New]
            homeFeedCardWidthPreset = extra.homeFeedCardWidthPreset
        )

    }

    // 第 6 步：与缓存大小和实验性功能合并
    private val cacheFlow = combine(_cacheSize, _cacheBreakdown) { size, breakdown ->
        Pair(size, breakdown)
    }
    
    val state: StateFlow<SettingsUiState> = combine(
        baseSettingsFlow,
        cacheFlow,
        experimentalSettingsFlow,
        _diagnosticsState,
    ) { settings, cache, experimental, diagnostics ->
        SettingsUiState(
            themeSelection = settings.themeSelection,
            hwDecode = settings.hwDecode,
            themeMode = settings.themeMode,
            darkThemeSchedule = settings.darkThemeSchedule,
            darkThemeStyle = settings.darkThemeStyle,
            appLanguage = settings.appLanguage,
            dynamicColor = settings.dynamicColor,
            md3ColorSource = settings.md3ColorSource,
            md3CustomColorHex = settings.md3CustomColorHex,
            colorStyle = settings.colorStyle,
            colorSpec = settings.colorSpec,
            appFontSizePreset = settings.appFontSizePreset,
            appFontWeightPreset = settings.appFontWeightPreset,
            appFontFileName = settings.appFontFileName,
            appFontDisplayName = settings.appFontDisplayName,
            appUiScalePreset = settings.appUiScalePreset,
            appDpiOverridePercent = settings.appDpiOverridePercent,
            bgPlay = settings.bgPlay,
            gestureSensitivity = settings.gestureSensitivity,
            themeColorIndex = settings.themeColorIndex,
            appIcon = settings.appIcon,
            appIconStyle = settings.appIconStyle,
            appListItemStyle = settings.appListItemStyle,
            isBottomBarFloating = settings.isBottomBarFloating,
            bottomBarLabelMode = settings.bottomBarLabelMode,
            headerBlurEnabled = settings.headerBlurEnabled,
            progressiveTopBlurEnabled = settings.progressiveTopBlurEnabled,
            progressiveTopFadeEnabled = settings.progressiveTopFadeEnabled,
            bottomBarBlurEnabled = settings.bottomBarBlurEnabled,
            blurIntensity = settings.blurIntensity,  //  模糊强度
            displayMode = settings.displayMode,
            cardAnimationEnabled = settings.cardAnimationEnabled,
            cardTransitionEnabled = settings.cardTransitionEnabled,
            videoSharedTransitionSpeed = settings.videoSharedTransitionSpeed,
            videoSharedTransitionCustomDurationMillis =
                settings.videoSharedTransitionCustomDurationMillis,
            smartVisualGuardEnabled = settings.smartVisualGuardEnabled,
            hapticFeedbackEnabled = settings.hapticFeedbackEnabled, // [新增]
            topBarLiquidGlassEnabled = settings.topBarLiquidGlassEnabled,
            homeSearchLiquidGlassEnabled = settings.homeSearchLiquidGlassEnabled,
            bottomBarLiquidGlassEnabled = settings.bottomBarLiquidGlassEnabled,
            bottomBarSearchEnabled = settings.bottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = settings.bottomBarSearchAutoExpandMode,
            bottomBarSearchLayoutMode = settings.bottomBarSearchLayoutMode,
            androidNativeLiquidGlassEnabled = settings.androidNativeLiquidGlassEnabled,
            liquidGlassStyle = settings.liquidGlassStyle, // [New]
            liquidGlassMode = settings.liquidGlassMode, // [New]
            liquidGlassStrength = settings.liquidGlassStrength, // [New]
            liquidGlassProgress = settings.liquidGlassProgress, // [New]
            tabletUseSidebar = settings.tabletUseSidebar, // [New]
            isHeaderCollapseEnabled = settings.isHeaderCollapseEnabled,
            gridColumnCount = settings.gridColumnCount, // [New]
            homeFeedCardWidthPreset = settings.homeFeedCardWidthPreset,

            cacheSize = cache.first,
            cacheBreakdown = cache.second,  //  详细缓存统计
            installedApkSha256 = diagnostics.installedApkSha256,
            currentReleaseEvidence = diagnostics.currentReleaseEvidence,
            diagnosticsLoadState = diagnostics.loadState,
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

    fun ensureDiagnosticsLoaded() {
        if (!shouldStartSettingsDiagnostics(
                loadState = _diagnosticsState.value.loadState,
                jobActive = diagnosticsLoadJob?.isActive == true,
            )
        ) {
            return
        }
        diagnosticsLoadJob = viewModelScope.launch {
            _diagnosticsState.update {
                it.copy(loadState = SettingsDiagnosticsLoadState.LOADING)
            }
            try {
                val (installedApkSha256, releaseEvidence) = coroutineScope {
                    val digest = async { calculateInstalledApkSha256(context) }
                    val release = async {
                        AppUpdateChecker
                            .check(
                                currentVersion = com.android.purebilibili.BuildConfig.VERSION_NAME,
                                currentVersionCode = com.android.purebilibili.BuildConfig.VERSION_CODE
                            )
                            .getOrNull()
                    }
                    digest.await() to release.await()
                }
                _diagnosticsState.value = DiagnosticsState(
                    installedApkSha256 = installedApkSha256,
                    currentReleaseEvidence = releaseEvidence,
                    loadState = SettingsDiagnosticsLoadState.LOADED,
                )
            } catch (error: CancellationException) {
                _diagnosticsState.update {
                    it.copy(loadState = SettingsDiagnosticsLoadState.NOT_LOADED)
                }
                throw error
            }
        }
    }

    fun recordReleaseEvidence(evidence: AppUpdateCheckResult) {
        _diagnosticsState.update { it.copy(currentReleaseEvidence = evidence) }
    }

    suspend fun clearCache(
        targets: Set<CacheClearTarget> = CacheClearTarget.entries.toSet()
    ): Result<CacheUtils.CacheBreakdown> {
        return CacheUtils.clearCache(context, targets).mapCatching {
            // 清理后立即刷新
            val breakdown = CacheUtils.getCacheBreakdown(context)
            _cacheSize.value = breakdown.format()
            _cacheBreakdown.value = breakdown
            breakdown
        }
    }

    fun toggleHwDecode(value: Boolean) { viewModelScope.launch { SettingsManager.setHwDecode(context, value) } }
    fun setThemeSelection(selection: AppUiStyle) {
        viewModelScope.launch {
            SettingsManager.setUiStyle(context, selection)
        }
    }
    fun setDarkThemeSchedule(schedule: DarkThemeSchedule) {
        viewModelScope.launch { SettingsManager.setDarkThemeSchedule(context, schedule) }
    }

    fun setThemeMode(mode: AppThemeMode) { 
        viewModelScope.launch { 
            SettingsManager.setThemeMode(context, mode)
        } 
    }
    fun setDarkThemeStyle(style: DarkThemeStyle) {
        viewModelScope.launch {
            SettingsManager.setDarkThemeStyle(context, style)
        }
    }
    fun setAppLanguage(appLanguage: AppLanguage) {
        viewModelScope.launch {
            SettingsManager.setAppLanguage(context, appLanguage)
        }
    }
    fun setAppIconStyle(iconStyle: AppIconStyle) {
        viewModelScope.launch {
            SettingsManager.setAppIconStyle(context, iconStyle)
        }
    }
    fun setAppListItemStyle(style: AppListItemStyle) {
        viewModelScope.launch {
            SettingsManager.setAppListItemStyle(context, style)
        }
    }
    fun toggleDynamicColor(value: Boolean) { viewModelScope.launch { SettingsManager.setDynamicColor(context, value) } }
    fun setMd3ColorSource(source: Md3ColorSource) {
        viewModelScope.launch { SettingsManager.setMd3ColorSource(context, source) }
    }
    fun setMd3CustomColorHex(hex: String) {
        viewModelScope.launch { SettingsManager.setMd3CustomColorHex(context, hex) }
    }
    fun applyMd3CustomColor(hex: String) {
        viewModelScope.launch { SettingsManager.applyMd3CustomColor(context, hex) }
    }
    fun setThemeColorStyle(style: PaletteStyle) {
        viewModelScope.launch { SettingsManager.setThemeColorStyle(context, style) }
    }
    fun setThemeColorSpec(spec: ColorSpec.SpecVersion) {
        viewModelScope.launch { SettingsManager.setThemeColorSpec(context, spec) }
    }
    fun setAppFontSizePreset(preset: AppFontSizePreset) {
        viewModelScope.launch { SettingsManager.setAppFontSizePreset(context, preset) }
    }
    fun setAppFontWeightPreset(preset: AppFontWeightPreset) {
        viewModelScope.launch { SettingsManager.setAppFontWeightPreset(context, preset) }
    }
    fun setAppFontFile(fileName: String, displayName: String) {
        viewModelScope.launch { SettingsManager.setAppFontFile(context, fileName, displayName) }
    }
    fun clearAppFontFile() {
        viewModelScope.launch { SettingsManager.clearAppFontFile(context) }
    }
    fun setAppUiScalePreset(preset: AppUiScalePreset) {
        viewModelScope.launch { SettingsManager.setAppUiScalePreset(context, preset) }
    }
    fun setAppDpiOverridePercent(percent: Int) {
        viewModelScope.launch { SettingsManager.setAppDpiOverridePercent(context, percent) }
    }
    fun toggleBgPlay(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setMiniPlayerMode(
                context,
                if (value) SettingsManager.MiniPlayerMode.SYSTEM_PIP else SettingsManager.MiniPlayerMode.OFF
            )
        }
    }
    //  [新增] 手势灵敏度和主题色
    fun setGestureSensitivity(value: Float) { viewModelScope.launch { SettingsManager.setGestureSensitivity(context, value) } }
    fun setThemeColorIndex(index: Int) { 
        viewModelScope.launch { 
            SettingsManager.setThemeColorIndex(context, index)
        }
    }

    //  [新增] 切换应用图标
    fun setAppIcon(iconKey: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val normalizedIconKey = normalizeAppIconKey(iconKey)
            // 1. 保存偏好
            SettingsManager.setAppIcon(context, normalizedIconKey)
            applyLauncherAliasForCurrentSplashIconSetting(normalizedIconKey)
        }
    }

    fun setAppIconAppearance(appearance: AppIconAppearance) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SettingsManager.setAppIconAppearance(context, appearance)
            val currentIcon = SettingsManager.getAppIconSync(context)
            if (!supportsAppIconAppearance(currentIcon)) return@launch
            applyLauncherAliasForCurrentSplashIconSetting(
                iconKey = currentIcon,
                appearance = appearance
            )
        }
    }

    private suspend fun applyLauncherAliasForCurrentSplashIconSetting(
        iconKey: String,
        appearance: AppIconAppearance = SettingsManager.getAppIconAppearanceSync(context)
    ) {
        val normalizedIconKey = normalizeAppIconKey(iconKey)
        // 2. 应用 Alias
        val pm = context.packageManager
        val packageName = context.packageName
        val splashIconVisible = SettingsManager.isSplashIconAnimationEnabledSync(context)

        val targetAlias = resolveAppIconLauncherAlias(
            packageName = packageName,
            rawKey = normalizedIconKey,
            splashIconVisible = splashIconVisible,
            appearance = appearance
        )
        val allUniqueAliases = allManagedAppIconLauncherAliases(packageName)

        android.util.Log.d("SettingsViewModel", "Switching icon to: $normalizedIconKey, splashIconVisible=$splashIconVisible -> $targetAlias")

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

            // 第二步：立即禁用其他 alias，避免部分桌面出现“双图标”残留
            allUniqueAliases
                .filter { it != targetAlias }
                .forEach { aliasFullName ->
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
            android.util.Log.d("SettingsViewModel", "Icon switch completed: $normalizedIconKey")
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Failed to switch app icon to $normalizedIconKey", e)
        }
    }

    //  [新增] 切换底栏样式
    fun toggleBottomBarFloating(value: Boolean) { viewModelScope.launch { SettingsManager.setBottomBarFloating(context, value) } }
    
    //  [新增] 底栏显示模式 (0=图标+文字, 1=仅图标, 2=仅文字)
    fun setBottomBarLabelMode(mode: Int) { viewModelScope.launch { SettingsManager.setBottomBarLabelMode(context, mode) } }
    


    fun toggleHeaderBlur(value: Boolean) {
        viewModelScope.launch {
            val resolved = resolveTopBarBlurToggleState(
                enableHeaderBlur = value,
                currentProgressiveTopBlurEnabled = state.value.progressiveTopBlurEnabled
            )
            SettingsManager.setTopBarVisualEffects(
                context = context,
                headerBlurEnabled = resolved.headerBlurEnabled,
                progressiveTopBlurEnabled = resolved.progressiveTopBlurEnabled
            )
        }
    }
    fun toggleProgressiveTopBlur(value: Boolean) {
        viewModelScope.launch {
            val resolved = resolveProgressiveTopBlurToggleState(
                enableProgressiveTopBlur = value,
                currentHeaderBlurEnabled = state.value.headerBlurEnabled
            )
            SettingsManager.setTopBarVisualEffects(
                context = context,
                headerBlurEnabled = resolved.headerBlurEnabled,
                progressiveTopBlurEnabled = resolved.progressiveTopBlurEnabled
            )
        }
    }
    fun toggleProgressiveTopFade(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setProgressiveTopFadeEnabled(context, value)
        }
    }
    fun toggleHeaderCollapse(value: Boolean) { viewModelScope.launch { SettingsManager.setHeaderCollapseEnabled(context, value) } }
    fun toggleBottomBarBlur(value: Boolean) {
        viewModelScope.launch {
            val resolved = resolveBottomBarBlurToggleState(
                enableBottomBarBlur = value,
                currentLiquidGlassEnabled = state.value.bottomBarLiquidGlassEnabled
            )
            SettingsManager.setBottomBarVisualEffects(
                context = context,
                blurEnabled = resolved.bottomBarBlurEnabled,
                liquidGlassEnabled = resolved.liquidGlassEnabled
            )
        }
    }
    fun setBlurIntensity(intensity: BlurIntensity) { viewModelScope.launch { SettingsManager.setBlurIntensity(context, intensity) } }  //  模糊强度设置
    
    //  [新增] 卡片进场动画开关
    fun toggleCardAnimation(value: Boolean) { viewModelScope.launch { SettingsManager.setCardAnimationEnabled(context, value) } }
    
    //  [新增] 卡片过渡动画开关
    fun toggleCardTransition(value: Boolean) { viewModelScope.launch { SettingsManager.setCardTransitionEnabled(context, value) } }

    fun toggleLiveSurfaceCardTransition(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setLiveSurfaceCardTransitionEnabled(context, value)
        }
    }

    fun toggleVideoTransitionRealtimeBlur(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setVideoTransitionRealtimeBlurEnabled(context, value)
        }
    }

    fun setVideoSharedTransitionSpeed(speed: VideoSharedTransitionSpeed) {
        viewModelScope.launch {
            SettingsManager.setVideoSharedTransitionSpeed(context, speed)
        }
    }

    fun setVideoSharedTransitionCustomDurationMillis(durationMillis: Int) {
        viewModelScope.launch {
            SettingsManager.setVideoSharedTransitionCustomDurationMillis(context, durationMillis)
        }
    }

    fun toggleSmartVisualGuard(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setSmartVisualGuardEnabled(context, value)
        }
    }
    
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
    
    // [New] Splash Screen
    fun toggleSplashEnabled(value: Boolean) { viewModelScope.launch { SettingsManager.setSplashEnabled(context, value) } }
    fun toggleSplashRandomEnabled(value: Boolean) { viewModelScope.launch { SettingsManager.setSplashRandomEnabled(context, value) } }
    fun setStartupAnimationStyle(value: com.android.purebilibili.core.store.StartupAnimationStyle) {
        viewModelScope.launch {
            SettingsManager.setStartupAnimationStyle(context, value)
        }
    }

    fun toggleSplashIconAnimationEnabled(value: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SettingsManager.setSplashIconAnimationEnabled(context, value)
            val currentIcon = SettingsManager.getAppIconSync(context)
            applyLauncherAliasForCurrentSplashIconSetting(currentIcon)
        }
    }

    // [New] 触感反馈
    fun toggleHapticFeedback(value: Boolean) { viewModelScope.launch { SettingsManager.setHapticFeedbackEnabled(context, value) } }
    
    fun toggleBottomBarSearch(enabled: Boolean) {
        viewModelScope.launch {
            SettingsManager.setBottomBarSearchEnabled(context, enabled)
        }
    }

    fun setBottomBarSearchAutoExpandMode(mode: BottomBarSearchAutoExpandMode) {
        viewModelScope.launch {
            SettingsManager.setBottomBarSearchAutoExpandMode(context, mode)
        }
    }

    fun setBottomBarSearchLayoutMode(mode: BottomBarSearchLayoutMode) {
        viewModelScope.launch {
            SettingsManager.setBottomBarSearchLayoutMode(context, mode)
        }
    }

    fun toggleAndroidNativeLiquidGlass(enabled: Boolean) {
        viewModelScope.launch {
            SettingsManager.setAndroidNativeLiquidGlassEnabled(context, enabled)
            if (enabled) {
                SettingsManager.setBottomBarBlurEnabled(context, false)
                SettingsManager.setHeaderBlurEnabled(context, false)
            }
        }
    }

    fun setLiquidGlassStyle(style: com.android.purebilibili.core.store.LiquidGlassStyle) {
        viewModelScope.launch {
            SettingsManager.setLiquidGlassStyle(context, style)
            val mode = resolveLegacyLiquidGlassMode(style)
            val strength = resolveDefaultLiquidGlassStrength(mode)
            SettingsManager.setLiquidGlassMode(context, mode)
            SettingsManager.setLiquidGlassStrength(context, strength)
            SettingsManager.setLiquidGlassProgress(
                context,
                resolveLegacyLiquidGlassProgress(mode = mode, strength = strength)
            )
        }
    }

    fun setLiquidGlassMode(mode: LiquidGlassMode) {
        viewModelScope.launch {
            SettingsManager.setLiquidGlassMode(context, mode)
        }
    }

    fun setLiquidGlassStrength(strength: Float) {
        viewModelScope.launch {
            SettingsManager.setLiquidGlassStrength(context, strength)
        }
    }

    fun setLiquidGlassProgress(progress: Float) {
        viewModelScope.launch {
            SettingsManager.setLiquidGlassProgress(context, progress)
        }
    }

    fun setLiquidGlassPreviewImageUri(uri: String?) {
        viewModelScope.launch {
            SettingsManager.setLiquidGlassPreviewImageUri(context, uri)
        }
    }

    fun setLiquidGlassAdvancedSettings(settings: LiquidGlassAdvancedSettings) {
        viewModelScope.launch {
            SettingsManager.setLiquidGlassAdvancedSettings(context, settings)
        }
    }

    fun setLiquidGlassReadabilityMode(mode: LiquidGlassReadabilityMode) {
        viewModelScope.launch {
            LiquidGlassSettingsStore.setReadabilityMode(context, mode)
        }
    }

    // [New] 平板导航模式
    fun toggleTabletUseSidebar(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setTabletUseSidebar(context, value)
        }
    }

    // [New] 网格列数
    fun setGridColumnCount(count: Int) {
        viewModelScope.launch {
            SettingsManager.setGridColumnCount(context, count)
        }
    }

    fun setHomeFeedCardWidthPreset(preset: HomeFeedCardWidthPreset) {
        viewModelScope.launch {
            SettingsManager.setHomeFeedCardWidthPreset(context, preset)
        }
    }
    

}

internal class SettingsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// Move DisplayMode enum here to be accessible
enum class DisplayMode(val title: String, val description: String, val value: Int) {
    DoubleGrid(title = "多列网格", description = "按屏幕宽度排列多列视频", value = 0),
    SingleColumn(title = "单列视频", description = "每行显示一个视频", value = 1)
}
