// 文件路径: core/store/SettingsManager.kt
package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.feature.settings.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

// 声明 DataStore 扩展属性
private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

/**
 *  首页设置合并类 - 减少 HomeScreen 重组次数
 * 将多个独立的设置流合并为单一流，避免每个设置变化都触发重组
 */
data class HomeSettings(
    val displayMode: Int = 0,              // 展示模式 (0=网格, 1=故事卡片)
    val isBottomBarFloating: Boolean = true,
    val bottomBarLabelMode: Int = 0,       // (0=图标+文字, 1=仅图标, 2=仅文字)
    val isHeaderBlurEnabled: Boolean = true,
    val isBottomBarBlurEnabled: Boolean = true,
    val cardAnimationEnabled: Boolean = false,    //  卡片进场动画（默认关闭）
    val cardTransitionEnabled: Boolean = true,   //  卡片过渡动画（默认开启）
    //  [修复] 默认值改为 true，避免在 Flow 加载实际值之前错误触发弹窗
    // 当 Flow 加载完成后，如果实际值是 false，LaunchedEffect 会再次触发并显示弹窗
    val crashTrackingConsentShown: Boolean = true
)

object SettingsManager {
    // 键定义
    private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
    private val KEY_HW_DECODE = booleanPreferencesKey("hw_decode")
    private val KEY_THEME_MODE = intPreferencesKey("theme_mode_v2")
    private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val KEY_BG_PLAY = booleanPreferencesKey("bg_play")
    //  [新增] 手势灵敏度和主题色
    private val KEY_GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
    //  [新增] 双击跳转秒数 (可分开设置快进和后退)
    private val KEY_SEEK_FORWARD_SECONDS = intPreferencesKey("seek_forward_seconds")
    private val KEY_SEEK_BACKWARD_SECONDS = intPreferencesKey("seek_backward_seconds")
    //  [新增] 长按倍速 (默认 2.0x)
    private val KEY_LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")
    private val KEY_THEME_COLOR_INDEX = intPreferencesKey("theme_color_index")
    //  [新增] 应用图标 Key (Blue, Red, Green...)
    private val KEY_APP_ICON = androidx.datastore.preferences.core.stringPreferencesKey("app_icon_key")
    //  [新增] 底部栏样式 (true=悬浮, false=贴底)
    private val KEY_BOTTOM_BAR_FLOATING = booleanPreferencesKey("bottom_bar_floating")
    //  [新增] 底栏显示模式 (0=图标+文字, 1=仅图标, 2=仅文字)
    private val KEY_BOTTOM_BAR_LABEL_MODE = intPreferencesKey("bottom_bar_label_mode")
    //  [新增] 模糊效果开关
    private val KEY_HEADER_BLUR_ENABLED = booleanPreferencesKey("header_blur_enabled")
    private val KEY_BOTTOM_BAR_BLUR_ENABLED = booleanPreferencesKey("bottom_bar_blur_enabled")
    // � [新增] 模糊强度 (ULTRA_THIN, THIN, THICK)
    private val KEY_BLUR_INTENSITY = stringPreferencesKey("blur_intensity")
    // � [合并] 首页展示模式 (0=Grid, 1=Story, 2=Glass)
    private val KEY_DISPLAY_MODE = intPreferencesKey("display_mode")
    //  [新增] 卡片动画开关
    private val KEY_CARD_ANIMATION_ENABLED = booleanPreferencesKey("card_animation_enabled")
    //  [新增] 卡片过渡动画开关
    private val KEY_CARD_TRANSITION_ENABLED = booleanPreferencesKey("card_transition_enabled")
    //  [合并] 崩溃追踪同意弹窗
    private val KEY_CRASH_TRACKING_CONSENT_SHOWN = booleanPreferencesKey("crash_tracking_consent_shown")
    //  [新增] 底栏自定义 - 顺序和可见性
    private val KEY_BOTTOM_BAR_ORDER = stringPreferencesKey("bottom_bar_order")  // 逗号分隔的项目顺序
    private val KEY_BOTTOM_BAR_VISIBLE_TABS = stringPreferencesKey("bottom_bar_visible_tabs")  // 逗号分隔的可见项目
    private val KEY_BOTTOM_BAR_ITEM_COLORS = stringPreferencesKey("bottom_bar_item_colors")  //  格式: HOME:0,DYNAMIC:1,...

    /**
     *  合并首页相关设置为单一 Flow
     * 避免 HomeScreen 中多个 collectAsState 导致频繁重组
     */
    fun getHomeSettings(context: Context): Flow<HomeSettings> {
        val displayModeFlow = context.settingsDataStore.data.map { it[KEY_DISPLAY_MODE] ?: 0 }
        val bottomBarFloatingFlow = context.settingsDataStore.data.map { it[KEY_BOTTOM_BAR_FLOATING] ?: true }
        val bottomBarLabelModeFlow = context.settingsDataStore.data.map { it[KEY_BOTTOM_BAR_LABEL_MODE] ?: 0 }  // 默认图标+文字
        val headerBlurFlow = context.settingsDataStore.data.map { it[KEY_HEADER_BLUR_ENABLED] ?: true }
        val bottomBarBlurFlow = context.settingsDataStore.data.map { it[KEY_BOTTOM_BAR_BLUR_ENABLED] ?: true }
        val crashConsentFlow = context.settingsDataStore.data.map { it[KEY_CRASH_TRACKING_CONSENT_SHOWN] ?: false }
        val cardAnimationFlow = context.settingsDataStore.data.map { it[KEY_CARD_ANIMATION_ENABLED] ?: false }
        val cardTransitionFlow = context.settingsDataStore.data.map { it[KEY_CARD_TRANSITION_ENABLED] ?: true }  // 默认开启
        
        // 🔧 Kotlin combine() 最多支持 5 个参数，使用嵌套 combine
        val firstFiveFlow = combine(
            displayModeFlow,
            bottomBarFloatingFlow,
            bottomBarLabelModeFlow,
            headerBlurFlow,
            bottomBarBlurFlow
        ) { displayMode, floating, labelMode, headerBlur, bottomBlur ->
            HomeSettings(
                displayMode = displayMode,
                isBottomBarFloating = floating,
                bottomBarLabelMode = labelMode,
                isHeaderBlurEnabled = headerBlur,
                isBottomBarBlurEnabled = bottomBlur,
                cardAnimationEnabled = false, // 临时占位
                cardTransitionEnabled = false, // 临时占位
                crashTrackingConsentShown = false // 临时占位
            )
        }
        
        val extraFlow = combine(crashConsentFlow, cardAnimationFlow, cardTransitionFlow) { consent, cardAnim, cardTransition ->
            Triple(consent, cardAnim, cardTransition)
        }
        
        return combine(firstFiveFlow, extraFlow) { settings, extra ->
            settings.copy(
                crashTrackingConsentShown = extra.first,
                cardAnimationEnabled = extra.second,
                cardTransitionEnabled = extra.third
            )
        }
    }

    // --- Auto Play ---
    fun getAutoPlay(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_PLAY] ?: true }

    suspend fun setAutoPlay(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_PLAY] = value }
        // 🔧 [修复] 同步到 SharedPreferences，供同步读取使用
        context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .edit().putBoolean("auto_play_enabled", value).apply()
    }
    
    // 🔧 [修复] 同步读取自动播放设置（用于 PlayerViewModel）
    fun getAutoPlaySync(context: Context): Boolean {
        return context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .getBoolean("auto_play_enabled", true)  // 默认开启
    }

    // --- HW Decode ---
    fun getHwDecode(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HW_DECODE] ?: true }

    suspend fun setHwDecode(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_HW_DECODE] = value }
        //  同步到 SharedPreferences，供同步读取使用
        context.getSharedPreferences("hw_decode_cache", Context.MODE_PRIVATE)
            .edit().putBoolean("hw_decode_enabled", value).apply()
    }

    // --- Theme Mode ---
    fun getThemeMode(context: Context): Flow<AppThemeMode> = context.settingsDataStore.data
        .map { preferences ->
            val modeInt = preferences[KEY_THEME_MODE] ?: AppThemeMode.FOLLOW_SYSTEM.value
            AppThemeMode.fromValue(modeInt)
        }

    suspend fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.value }
        //  同步到 SharedPreferences，供 PureApplication 同步读取使用
        // 使用 commit() 确保立即写入
        val success = context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE)
            .edit().putInt("theme_mode", mode.value).commit()
        com.android.purebilibili.core.util.Logger.d("SettingsManager", " Theme mode saved: ${mode.value} (${mode.label}), success=$success")
        
        //  同时应用到 AppCompatDelegate，使当前运行时生效
        val nightMode = when (mode) {
            AppThemeMode.FOLLOW_SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    // --- Dynamic Color ---
    fun getDynamicColor(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DYNAMIC_COLOR] ?: true }

    suspend fun setDynamicColor(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_DYNAMIC_COLOR] = value }
    }

    /**
     * @deprecated 此设置已被 MiniPlayerMode 替代
     * 请使用 getMiniPlayerMode() 和 setMiniPlayerMode() 替代
     * - MiniPlayerMode.SYSTEM_PIP 相当于 bgPlay = true
     * - MiniPlayerMode.OFF 相当于 bgPlay = false
     */
    @Deprecated("Use getMiniPlayerMode() instead", ReplaceWith("getMiniPlayerMode(context)"))
    fun getBgPlay(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BG_PLAY] ?: false }

    @Deprecated("Use setMiniPlayerMode() instead", ReplaceWith("setMiniPlayerMode(context, mode)"))
    suspend fun setBgPlay(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BG_PLAY] = value }
    }

    //  [新增] --- 手势灵敏度 (0.5 ~ 2.0, 默认 1.0) ---
    fun getGestureSensitivity(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_GESTURE_SENSITIVITY] ?: 1.0f }

    suspend fun setGestureSensitivity(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_GESTURE_SENSITIVITY] = value.coerceIn(0.5f, 2.0f) 
        }
    }

    //  [新增] --- 双击跳转秒数 ---
    fun getSeekForwardSeconds(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SEEK_FORWARD_SECONDS] ?: 10 }

    suspend fun setSeekForwardSeconds(context: Context, seconds: Int) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_SEEK_FORWARD_SECONDS] = seconds.coerceIn(1, 60)
        }
    }

    fun getSeekBackwardSeconds(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SEEK_BACKWARD_SECONDS] ?: 10 }

    suspend fun setSeekBackwardSeconds(context: Context, seconds: Int) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_SEEK_BACKWARD_SECONDS] = seconds.coerceIn(1, 60)
        }
    }

    //  [新增] --- 长按倍速 (默认 2.0x) ---
    fun getLongPressSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_LONG_PRESS_SPEED] ?: 2.0f }

    suspend fun setLongPressSpeed(context: Context, speed: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_LONG_PRESS_SPEED] = speed.coerceIn(1.5f, 3.0f)
        }
    }

    //  [新增] --- 主题色索引 (0-5, 默认 0 = BiliPink) ---
    fun getThemeColorIndex(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_THEME_COLOR_INDEX] ?: 0 }

    suspend fun setThemeColorIndex(context: Context, index: Int) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_THEME_COLOR_INDEX] = index.coerceIn(0, 5)
        }
    }
    
    
    //  --- 首页展示模式 功能方法 ---
    
    fun getDisplayMode(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DISPLAY_MODE] ?: 0 }

    suspend fun setDisplayMode(context: Context, mode: Int) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_DISPLAY_MODE] = mode
        }
    }
    
    //  [新增] --- 卡片进场动画开关 ---
    fun getCardAnimationEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_CARD_ANIMATION_ENABLED] ?: false }  // 默认关闭

    suspend fun setCardAnimationEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_CARD_ANIMATION_ENABLED] = value }
    }
    
    //  [新增] --- 卡片过渡动画开关 ---
    fun getCardTransitionEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_CARD_TRANSITION_ENABLED] ?: true }  // 默认开启

    suspend fun setCardTransitionEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_CARD_TRANSITION_ENABLED] = value }
    }

    //  [新增] --- 应用图标 ---
    fun getAppIcon(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_APP_ICON] ?: "icon_3d" }

    suspend fun setAppIcon(context: Context, iconKey: String) {
        // 1. Write to DataStore (suspends until persisted)
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_APP_ICON] = iconKey
        }
        
        // 2. Write to SharedPreferences synchronously using commit()
        // This is critical because changing the app icon (activity-alias) often kills the process immediately.
        // apply() is asynchronous and might not finish before the process dies.
        val success = context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE)
            .edit().putString("current_icon", iconKey).commit()
            
        com.android.purebilibili.core.util.Logger.d("SettingsManager", "App icon saved: $iconKey, persisted to prefs: $success")
    }
    
    //  同步读取当前图标设置（用于 Application 启动时同步）
    fun getAppIconSync(context: Context): String {
        return context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE)
            .getString("current_icon", "icon_3d") ?: "icon_3d"
    }

    //  [新增] --- 底部栏样式 ---
    fun getBottomBarFloating(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_FLOATING] ?: true }

    suspend fun setBottomBarFloating(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BOTTOM_BAR_FLOATING] = value }
    }
    
    //  [新增] --- 底栏显示模式 (0=图标+文字, 1=仅图标, 2=仅文字) ---
    fun getBottomBarLabelMode(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_LABEL_MODE] ?: 0 }  // 默认图标+文字

    suspend fun setBottomBarLabelMode(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BOTTOM_BAR_LABEL_MODE] = value }
    }
    
    //  [新增] --- 搜索框模糊效果 ---
    fun getHeaderBlurEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HEADER_BLUR_ENABLED] ?: true }

    suspend fun setHeaderBlurEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_HEADER_BLUR_ENABLED] = value }
    }
    
    //  [新增] --- 底栏模糊效果 ---
    fun getBottomBarBlurEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] ?: true }

    suspend fun setBottomBarBlurEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] = value }
    }
    
    //  [修复] --- 模糊强度 (THIN, THICK, APPLE_DOCK) ---
    fun getBlurIntensity(context: Context): Flow<BlurIntensity> = context.settingsDataStore.data
        .map { preferences ->
            when (preferences[KEY_BLUR_INTENSITY]) {
                "THICK" -> BlurIntensity.THICK
                "APPLE_DOCK" -> BlurIntensity.APPLE_DOCK  //  修复：添加 APPLE_DOCK 支持
                else -> BlurIntensity.THIN  // 默认标准
            }
        }

    suspend fun setBlurIntensity(context: Context, intensity: BlurIntensity) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_BLUR_INTENSITY] = intensity.name
        }
    }
    
    // ==========  弹幕设置 ==========
    
    private const val DANMAKU_DEFAULTS_VERSION = 2
    private const val DEFAULT_DANMAKU_OPACITY = 0.85f
    private const val DEFAULT_DANMAKU_FONT_SCALE = 1.0f
    private const val DEFAULT_DANMAKU_SPEED = 1.0f
    private const val DEFAULT_DANMAKU_AREA = 0.5f
    
    private val KEY_DANMAKU_ENABLED = booleanPreferencesKey("danmaku_enabled")
    private val KEY_DANMAKU_OPACITY = floatPreferencesKey("danmaku_opacity")
    private val KEY_DANMAKU_FONT_SCALE = floatPreferencesKey("danmaku_font_scale")
    private val KEY_DANMAKU_SPEED = floatPreferencesKey("danmaku_speed")
    private val KEY_DANMAKU_AREA = floatPreferencesKey("danmaku_area")
    private val KEY_DANMAKU_DEFAULTS_VERSION = intPreferencesKey("danmaku_defaults_version")
    
    // --- 弹幕开关 ---
    fun getDanmakuEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_ENABLED] ?: true }

    suspend fun setDanmakuEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_DANMAKU_ENABLED] = value }
    }
    
    // --- 弹幕透明度 (0.0 ~ 1.0, 默认 0.85) ---
    fun getDanmakuOpacity(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_OPACITY] ?: DEFAULT_DANMAKU_OPACITY }

    suspend fun setDanmakuOpacity(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_DANMAKU_OPACITY] = value.coerceIn(0.0f, 1.0f)
        }
    }
    
    // --- 弹幕字体大小 (0.5 ~ 2.0, 默认 1.0) ---
    fun getDanmakuFontScale(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_FONT_SCALE] ?: DEFAULT_DANMAKU_FONT_SCALE }

    suspend fun setDanmakuFontScale(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_DANMAKU_FONT_SCALE] = value.coerceIn(0.5f, 2.0f)
        }
    }
    
    // --- 弹幕速度 (0.5 ~ 3.0, 默认 1.0 适中) ---
    fun getDanmakuSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_SPEED] ?: DEFAULT_DANMAKU_SPEED }

    suspend fun setDanmakuSpeed(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_DANMAKU_SPEED] = value.coerceIn(0.5f, 3.0f)
        }
    }
    
    // --- 弹幕显示区域 (0.25, 0.5, 0.75, 1.0, 默认 0.5) ---
    fun getDanmakuArea(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_AREA] ?: DEFAULT_DANMAKU_AREA }

    suspend fun setDanmakuArea(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_DANMAKU_AREA] = value.coerceIn(0.25f, 1.0f)
        }
    }
    
    // 强制更新弹幕默认值（覆盖已有设置，版本升级时触发一次）
    suspend fun forceDanmakuDefaults(context: Context) {
        context.settingsDataStore.edit { preferences ->
            val currentVersion = preferences[KEY_DANMAKU_DEFAULTS_VERSION] ?: 0
            if (currentVersion < DANMAKU_DEFAULTS_VERSION) {
                preferences[KEY_DANMAKU_OPACITY] = DEFAULT_DANMAKU_OPACITY
                preferences[KEY_DANMAKU_FONT_SCALE] = DEFAULT_DANMAKU_FONT_SCALE
                preferences[KEY_DANMAKU_SPEED] = DEFAULT_DANMAKU_SPEED
                preferences[KEY_DANMAKU_AREA] = DEFAULT_DANMAKU_AREA
                preferences[KEY_DANMAKU_DEFAULTS_VERSION] = DANMAKU_DEFAULTS_VERSION
            }
        }
    }
    
    // ==========  实验性功能 ==========
    
    private val KEY_AUTO_1080P = booleanPreferencesKey("exp_auto_1080p")
    private val KEY_AUTO_SKIP_OP_ED = booleanPreferencesKey("exp_auto_skip_op_ed")
    private val KEY_PREFETCH_VIDEO = booleanPreferencesKey("exp_prefetch_video")
    private val KEY_DOUBLE_TAP_LIKE = booleanPreferencesKey("exp_double_tap_like")
    
    // --- 已登录用户默认 1080P ---
    fun getAuto1080p(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_1080P] ?: true }  // 默认开启

    suspend fun setAuto1080p(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_1080P] = value }
    }
    
    // --- 自动跳过片头片尾 ---
    fun getAutoSkipOpEd(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_SKIP_OP_ED] ?: false }

    suspend fun setAutoSkipOpEd(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_SKIP_OP_ED] = value }
    }
    
    // --- 预加载下一个视频 ---
    fun getPrefetchVideo(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_PREFETCH_VIDEO] ?: false }

    suspend fun setPrefetchVideo(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_PREFETCH_VIDEO] = value }
    }
    
    // --- 双击点赞 ---
    fun getDoubleTapLike(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DOUBLE_TAP_LIKE] ?: true }  // 默认开启

    suspend fun setDoubleTapLike(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_DOUBLE_TAP_LIKE] = value }
    }
    
    // ========== 📱 竖屏全屏设置 ==========
    
    private val KEY_PORTRAIT_FULLSCREEN_ENABLED = booleanPreferencesKey("portrait_fullscreen_enabled")
    private val KEY_AUTO_PORTRAIT_FULLSCREEN = booleanPreferencesKey("auto_portrait_fullscreen")
    private val KEY_VERTICAL_VIDEO_RATIO = floatPreferencesKey("vertical_video_ratio")
    
    // --- 竖屏全屏功能开关 (默认开启) ---
    fun getPortraitFullscreenEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_PORTRAIT_FULLSCREEN_ENABLED] ?: true }

    suspend fun setPortraitFullscreenEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_PORTRAIT_FULLSCREEN_ENABLED] = value }
    }
    
    // --- 竖屏视频自动进入全屏 (默认关闭) ---
    fun getAutoPortraitFullscreen(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_PORTRAIT_FULLSCREEN] ?: false }

    suspend fun setAutoPortraitFullscreen(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_PORTRAIT_FULLSCREEN] = value }
    }
    
    // --- 竖屏视频判断比例 (高度/宽度 > ratio 视为竖屏，默认 1.0) ---
    fun getVerticalVideoRatio(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_VERTICAL_VIDEO_RATIO] ?: 1.0f }

    suspend fun setVerticalVideoRatio(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_VERTICAL_VIDEO_RATIO] = value.coerceIn(0.8f, 1.5f)  // 合理范围
        }
    }
    
    //  同步读取竖屏全屏设置
    fun isPortraitFullscreenEnabledSync(context: Context): Boolean {
        // 使用默认值 true（与 Flow 版本一致）
        val prefs = context.getSharedPreferences("portrait_fullscreen_cache", Context.MODE_PRIVATE)
        return prefs.getBoolean("enabled", true)
    }
    
    // ========== 🌐 网络感知画质设置 ==========
    
    private val KEY_WIFI_QUALITY = intPreferencesKey("wifi_default_quality")

    private val KEY_MOBILE_QUALITY = intPreferencesKey("mobile_default_quality")
    //  [New] Video Codec & Audio Quality
    private val KEY_VIDEO_CODEC = stringPreferencesKey("video_codec_preference")
    private val KEY_AUDIO_QUALITY = intPreferencesKey("audio_quality_preference")
    
    // --- WiFi 默认画质 (默认 80 = 1080P) ---
    fun getWifiQuality(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_WIFI_QUALITY] ?: 80 }

    suspend fun setWifiQuality(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_WIFI_QUALITY] = value }
        //  同步到 SharedPreferences，供 NetworkUtils 同步读取
        // 使用 commit() 确保立即写入
        val success = context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .edit().putInt("wifi_quality", value).commit()
        com.android.purebilibili.core.util.Logger.d("SettingsManager", " WiFi 画质已设置: $value (写入成功: $success)")
    }
    
    // --- 流量默认画质 (默认 64 = 720P) ---
    fun getMobileQuality(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_MOBILE_QUALITY] ?: 64 }

    suspend fun setMobileQuality(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_MOBILE_QUALITY] = value }
        //  同步到 SharedPreferences，供 NetworkUtils 同步读取
        // 使用 commit() 确保立即写入
        val success = context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .edit().putInt("mobile_quality", value).commit()
        com.android.purebilibili.core.util.Logger.d("SettingsManager", " 流量画质已设置: $value (写入成功: $success)")
    }
    
    //  同步读取画质设置（用于 PlayerViewModel）
    fun getWifiQualitySync(context: Context): Int {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getInt("wifi_quality", 80)
    }
    
    fun getMobileQualitySync(context: Context): Int {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getInt("mobile_quality", 64)
    }

    // --- Video Codec Preference (Default: HEVC/hev1) ---
    // Values: "avc1" (AVC), "hev1" (HEVC), "av01" (AV1)
    fun getVideoCodec(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_VIDEO_CODEC] ?: "hev1" }

    suspend fun setVideoCodec(context: Context, value: String) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_VIDEO_CODEC] = value }
        // Sync to SharedPreferences for synchronous access
        context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .edit().putString("video_codec", value).apply()
    }

    fun getVideoCodecSync(context: Context): String {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getString("video_codec", "hev1") ?: "hev1"
    }

    // --- Audio Quality Preference (Default: 30280 = 192K) ---
    // Special Values: -1 (Auto/Highest)
    fun getAudioQuality(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> 
            val value = preferences[KEY_AUDIO_QUALITY] ?: -1
            com.android.purebilibili.core.util.Logger.d("SettingsManager", "📻 getAudioQuality Flow emitting: $value")
            value 
        }

    suspend fun setAudioQuality(context: Context, value: Int) {
        com.android.purebilibili.core.util.Logger.d("SettingsManager", "📻 setAudioQuality called with: $value")
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_AUDIO_QUALITY] = value 
            com.android.purebilibili.core.util.Logger.d("SettingsManager", "📻 setAudioQuality DataStore written: $value")
        }
        // Sync to SharedPreferences for synchronous access - Use commit() to ensure immediate write
        val result = context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .edit().putInt("audio_quality", value).commit()
        com.android.purebilibili.core.util.Logger.d("SettingsManager", "📻 setAudioQuality SharedPrefs committed: $value, success=$result")
    }

    fun getAudioQualitySync(context: Context): Int {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getInt("audio_quality", -1)
    }
    
    // ==========  空降助手 (SponsorBlock) ==========
    
    private val KEY_SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")
    private val KEY_SPONSOR_BLOCK_AUTO_SKIP = booleanPreferencesKey("sponsor_block_auto_skip")
    
    // --- 空降助手开关 ---
    fun getSponsorBlockEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SPONSOR_BLOCK_ENABLED] ?: false }  // 默认关闭

    suspend fun setSponsorBlockEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_SPONSOR_BLOCK_ENABLED] = value }
        //  [修复] 同步到PluginStore，使插件系统能正确识别空降助手状态
        com.android.purebilibili.core.plugin.PluginManager.setEnabled("sponsor_block", value)
    }
    
    // --- 自动跳过（true=自动跳过, false=显示提示按钮）---
    fun getSponsorBlockAutoSkip(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SPONSOR_BLOCK_AUTO_SKIP] ?: true }  // 默认自动跳过

    suspend fun setSponsorBlockAutoSkip(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_SPONSOR_BLOCK_AUTO_SKIP] = value }
    }
    
    // ==========  崩溃追踪 (Crashlytics) ==========
    
    private val KEY_CRASH_TRACKING_ENABLED = booleanPreferencesKey("crash_tracking_enabled")
    // KEY_CRASH_TRACKING_CONSENT_SHOWN 已在顶部定义
    
    // --- 崩溃追踪开关 ---
    fun getCrashTrackingEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_CRASH_TRACKING_ENABLED] ?: true }  // 默认开启

    suspend fun setCrashTrackingEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_CRASH_TRACKING_ENABLED] = value }
        //  同步到 SharedPreferences，供 Application 同步读取
        context.getSharedPreferences("crash_tracking", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply()
    }
    
    // --- 崩溃追踪首次提示是否已显示 ---
    fun getCrashTrackingConsentShown(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_CRASH_TRACKING_CONSENT_SHOWN] ?: false }

    suspend fun setCrashTrackingConsentShown(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_CRASH_TRACKING_CONSENT_SHOWN] = value }
    }
    
    // ==========  用户行为分析 (Analytics) ==========
    
    private val KEY_ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
    
    // --- Analytics 开关 (与崩溃追踪共享设置) ---
    fun getAnalyticsEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_ANALYTICS_ENABLED] ?: true }  // 默认开启

    suspend fun setAnalyticsEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_ANALYTICS_ENABLED] = value }
        //  同步到 SharedPreferences，供 Application 同步读取
        context.getSharedPreferences("analytics_tracking", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply()
    }
    
    // ==========  隐私无痕模式 ==========
    
    private val KEY_PRIVACY_MODE_ENABLED = booleanPreferencesKey("privacy_mode_enabled")
    
    // --- 隐私无痕模式开关 (启用后不记录播放历史和搜索历史) ---
    fun getPrivacyModeEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_PRIVACY_MODE_ENABLED] ?: false }  // 默认关闭

    suspend fun setPrivacyModeEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_PRIVACY_MODE_ENABLED] = value }
        //  同步到 SharedPreferences，供同步读取使用 (VideoRepository 等)
        context.getSharedPreferences("privacy_mode", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply()
    }
    
    //  同步读取隐私模式状态（用于非协程环境）
    fun isPrivacyModeEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences("privacy_mode", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)
    }
    
    // ==========  小窗播放模式 ==========
    
    private val KEY_MINI_PLAYER_MODE = intPreferencesKey("mini_player_mode")
    
    /**
     *  小窗播放模式（3 种）
     * - OFF: 默认模式（官方B站行为：切到桌面后台播放，返回主页停止）
     * - IN_APP_ONLY: 应用内小窗（返回主页时显示悬浮小窗）
     * - SYSTEM_PIP: 系统画中画（切到桌面时自动进入画中画模式）
     */
    enum class MiniPlayerMode(val value: Int, val label: String, val description: String) {
        OFF(0, "默认", "切到桌面后台播放，返回主页停止"),
        IN_APP_ONLY(1, "应用内小窗", "返回主页时显示悬浮小窗"),
        SYSTEM_PIP(2, "画中画", "切到桌面进入系统画中画");
        
        companion object {
            fun fromValue(value: Int): MiniPlayerMode = when(value) {
                1 -> IN_APP_ONLY
                2 -> SYSTEM_PIP
                else -> OFF
            }
        }
    }
    
    // --- 小窗模式设置 ---
    fun getMiniPlayerMode(context: Context): Flow<MiniPlayerMode> = context.settingsDataStore.data
        .map { preferences -> 
            MiniPlayerMode.fromValue(preferences[KEY_MINI_PLAYER_MODE] ?: MiniPlayerMode.OFF.value)
        }

    suspend fun setMiniPlayerMode(context: Context, mode: MiniPlayerMode) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_MINI_PLAYER_MODE] = mode.value 
        }
        //  同步到 SharedPreferences，供 MiniPlayerManager 同步读取
        context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .edit().putInt("mode", mode.value).apply()
    }
    
    //  同步读取小窗模式（用于 MiniPlayerManager）
    fun getMiniPlayerModeSync(context: Context): MiniPlayerMode {
        val value = context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .getInt("mode", MiniPlayerMode.OFF.value)
        return MiniPlayerMode.fromValue(value)
    }
    
    // ==========  底栏显示模式 ==========
    
    private val KEY_BOTTOM_BAR_VISIBILITY_MODE = intPreferencesKey("bottom_bar_visibility_mode")
    
    /**
     *  底栏显示模式
     * - SCROLL_HIDE: 上滑隐藏，下滑显示
     * - ALWAYS_VISIBLE: 始终显示（默认）
     * - ALWAYS_HIDDEN: 永久隐藏
     */
    enum class BottomBarVisibilityMode(val value: Int, val label: String, val description: String) {
        SCROLL_HIDE(0, "上滑隐藏", "上滑时隐藏底栏，下滑时显示"),
        ALWAYS_VISIBLE(1, "始终显示", "底栏始终可见"),
        ALWAYS_HIDDEN(2, "永久隐藏", "完全隐藏底栏");
        
        companion object {
            fun fromValue(value: Int): BottomBarVisibilityMode = entries.find { it.value == value } ?: ALWAYS_VISIBLE
        }
    }
    
    // --- 底栏显示模式设置 ---
    fun getBottomBarVisibilityMode(context: Context): Flow<BottomBarVisibilityMode> = context.settingsDataStore.data
        .map { preferences -> 
            BottomBarVisibilityMode.fromValue(preferences[KEY_BOTTOM_BAR_VISIBILITY_MODE] ?: BottomBarVisibilityMode.ALWAYS_VISIBLE.value)
        }

    suspend fun setBottomBarVisibilityMode(context: Context, mode: BottomBarVisibilityMode) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_BOTTOM_BAR_VISIBILITY_MODE] = mode.value 
        }
    }
    
    // ==========  下载路径设置 ==========
    
    private val KEY_DOWNLOAD_PATH = stringPreferencesKey("download_path")
    
    /**
     *  获取用户自定义下载路径
     * 返回 null 表示使用默认路径
     */
    fun getDownloadPath(context: Context): Flow<String?> = context.settingsDataStore.data
        .map { preferences -> 
            preferences[KEY_DOWNLOAD_PATH]
        }
    
    /**
     *  设置自定义下载路径
     * 传入 null 重置为默认路径
     */
    suspend fun setDownloadPath(context: Context, path: String?) {
        context.settingsDataStore.edit { preferences -> 
            if (path != null) {
                preferences[KEY_DOWNLOAD_PATH] = path
            } else {
                preferences.remove(KEY_DOWNLOAD_PATH)
            }
        }
    }
    
    /**
     *  获取默认下载路径描述
     */
    fun getDefaultDownloadPath(context: Context): String {
        return context.getExternalFilesDir(null)?.absolutePath + "/downloads"
    }
    
    // ========== 📉 省流量模式 ==========
    
    private val KEY_DATA_SAVER_MODE = intPreferencesKey("data_saver_mode")
    
    /**
     *  省流量模式
     * - OFF: 关闭省流量
     * - MOBILE_ONLY: 仅移动数据时启用（默认）
     * - ALWAYS: 始终启用
     */
    enum class DataSaverMode(val value: Int, val label: String, val description: String) {
        OFF(0, "关闭", "不限制流量使用"),
        MOBILE_ONLY(1, "仅移动数据", "使用移动数据时自动省流量"),
        ALWAYS(2, "始终开启", "始终使用省流量模式");
        
        companion object {
            fun fromValue(value: Int): DataSaverMode = entries.find { it.value == value } ?: MOBILE_ONLY
        }
    }
    
    // --- 省流量模式设置 ---
    fun getDataSaverMode(context: Context): Flow<DataSaverMode> = context.settingsDataStore.data
        .map { preferences -> 
            DataSaverMode.fromValue(preferences[KEY_DATA_SAVER_MODE] ?: DataSaverMode.MOBILE_ONLY.value)
        }

    suspend fun setDataSaverMode(context: Context, mode: DataSaverMode) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_DATA_SAVER_MODE] = mode.value 
        }
        //  同步到 SharedPreferences，供同步读取使用
        context.getSharedPreferences("data_saver", Context.MODE_PRIVATE)
            .edit().putInt("mode", mode.value).apply()
    }
    
    //  同步读取省流量模式
    fun getDataSaverModeSync(context: Context): DataSaverMode {
        val value = context.getSharedPreferences("data_saver", Context.MODE_PRIVATE)
            .getInt("mode", DataSaverMode.MOBILE_ONLY.value)
        return DataSaverMode.fromValue(value)
    }
    
    /**
     *  判断当前是否应该启用省流量
     * 根据模式和当前网络状态判断
     */
    fun isDataSaverActive(context: Context): Boolean {
        val mode = getDataSaverModeSync(context)
        return when (mode) {
            DataSaverMode.OFF -> false
            DataSaverMode.ALWAYS -> true
            DataSaverMode.MOBILE_ONLY -> {
                // 检测当前网络是否为移动数据
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                    as android.net.ConnectivityManager
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                // 如果是蜂窝网络，则启用省流量
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
            }
        }
    }
    
    //  [新增] --- 底栏顺序配置 ---
    // 默认顺序: HOME,DYNAMIC,HISTORY,PROFILE
    fun getBottomBarOrder(context: Context): Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val orderString = prefs[KEY_BOTTOM_BAR_ORDER] ?: "HOME,DYNAMIC,HISTORY,PROFILE"
        orderString.split(",").filter { it.isNotBlank() }
    }
    
    suspend fun setBottomBarOrder(context: Context, order: List<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_BOTTOM_BAR_ORDER] = order.joinToString(",")
        }
    }
    
    //  [新增] --- 底栏可见项配置 ---
    // 默认可见: HOME,DYNAMIC,HISTORY,PROFILE
    // 可选项: HOME,DYNAMIC,HISTORY,PROFILE,FAVORITE,LIVE,WATCHLATER
    fun getBottomBarVisibleTabs(context: Context): Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        val tabsString = prefs[KEY_BOTTOM_BAR_VISIBLE_TABS] ?: "HOME,DYNAMIC,HISTORY,PROFILE"
        tabsString.split(",").filter { it.isNotBlank() }.toSet()
    }
    
    suspend fun setBottomBarVisibleTabs(context: Context, tabs: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_BOTTOM_BAR_VISIBLE_TABS] = tabs.joinToString(",")
        }
    }
    
    //  [新增] 获取有序的可见底栏项目列表
    fun getOrderedVisibleTabs(context: Context): Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val orderString = prefs[KEY_BOTTOM_BAR_ORDER] ?: "HOME,DYNAMIC,HISTORY,PROFILE"
        val tabsString = prefs[KEY_BOTTOM_BAR_VISIBLE_TABS] ?: "HOME,DYNAMIC,HISTORY,PROFILE"
        val order = orderString.split(",").filter { it.isNotBlank() }
        val visibleSet = tabsString.split(",").filter { it.isNotBlank() }.toSet()
        order.filter { it in visibleSet }
    }
    
    //  [新增] --- 底栏项目颜色自定义 ---
    /**
     * 获取所有底栏项目的颜色索引映射
     * @return Map<项目ID, 颜色索引>
     */
    fun getBottomBarItemColors(context: Context): Flow<Map<String, Int>> = context.settingsDataStore.data.map { prefs ->
        val colorsString = prefs[KEY_BOTTOM_BAR_ITEM_COLORS] ?: ""
        if (colorsString.isBlank()) {
            emptyMap()  // 返回空 Map，使用默认颜色
        } else {
            colorsString.split(",")
                .filter { it.contains(":") }
                .associate { pair ->
                    val (id, index) = pair.split(":")
                    id to (index.toIntOrNull() ?: 0)
                }
        }
    }
    
    /**
     * 设置单个底栏项目的颜色索引
     */
    suspend fun setBottomBarItemColor(context: Context, itemId: String, colorIndex: Int) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_BOTTOM_BAR_ITEM_COLORS] ?: ""
            val colorMap = if (current.isBlank()) {
                mutableMapOf()
            } else {
                current.split(",")
                    .filter { it.contains(":") }
                    .associate { pair ->
                        val (id, index) = pair.split(":")
                        id to (index.toIntOrNull() ?: 0)
                    }.toMutableMap()
            }
            colorMap[itemId] = colorIndex
            prefs[KEY_BOTTOM_BAR_ITEM_COLORS] = colorMap.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }
    
    // ==========  彩蛋设置 ==========
    
    private val KEY_EASTER_EGG_ENABLED = booleanPreferencesKey("easter_egg_enabled")
    
    // --- 彩蛋功能开关（控制下拉刷新趣味提示等）---
    fun getEasterEggEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_EASTER_EGG_ENABLED] ?: false }  // 默认关闭

    suspend fun setEasterEggEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_EASTER_EGG_ENABLED] = value }
        //  同步到 SharedPreferences，供同步读取使用
        context.getSharedPreferences("easter_egg", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply()
    }
    
    //  同步读取彩蛋开关（用于 ViewModel）
    fun isEasterEggEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences("easter_egg", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)  // 默认关闭
    }
    
    // ==========  播放器设置 ==========
    
    private val KEY_SWIPE_HIDE_PLAYER = booleanPreferencesKey("swipe_hide_player")
    
    // --- 上滑隐藏播放器开关 ---
    fun getSwipeHidePlayerEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SWIPE_HIDE_PLAYER] ?: false }  // 默认关闭

    suspend fun setSwipeHidePlayerEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_SWIPE_HIDE_PLAYER] = value }
    }
    
    // ==========  界面自定义设置 ==========
    
    private val KEY_CORNER_RADIUS_SCALE = floatPreferencesKey("corner_radius_scale")
    private val KEY_FONT_SCALE = floatPreferencesKey("ui_font_scale")
    private val KEY_UI_SCALE = floatPreferencesKey("ui_scale")
    
    /**
     *  圆角大小比例 (0.5 ~ 1.5, 默认 1.0)
     * 控制全局 UI 圆角大小
     */
    fun getCornerRadiusScale(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_CORNER_RADIUS_SCALE] ?: 1.0f }

    suspend fun setCornerRadiusScale(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_CORNER_RADIUS_SCALE] = value.coerceIn(0.5f, 1.5f) 
        }
    }
    
    //  同步读取圆角比例
    fun getCornerRadiusScaleSync(context: Context): Float {
        return context.getSharedPreferences("ui_customization", Context.MODE_PRIVATE)
            .getFloat("corner_radius_scale", 1.0f)
    }
    
    /**
     *  字体大小比例 (0.8 ~ 1.4, 默认 1.0)
     * 控制全局字体大小
     */
    fun getFontScale(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_FONT_SCALE] ?: 1.0f }

    suspend fun setFontScale(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_FONT_SCALE] = value.coerceIn(0.8f, 1.4f) 
        }
        //  同步到 SharedPreferences
        context.getSharedPreferences("ui_customization", Context.MODE_PRIVATE)
            .edit().putFloat("font_scale", value).apply()
    }
    
    //  同步读取字体比例
    fun getFontScaleSync(context: Context): Float {
        return context.getSharedPreferences("ui_customization", Context.MODE_PRIVATE)
            .getFloat("font_scale", 1.0f)
    }
    
    /**
     *  UI 整体缩放比例 (0.9 ~ 1.2, 默认 1.0)
     * 控制 UI 元素整体大小
     */
    fun getUIScale(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_UI_SCALE] ?: 1.0f }

    suspend fun setUIScale(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_UI_SCALE] = value.coerceIn(0.9f, 1.2f) 
        }
        //  同步到 SharedPreferences
        context.getSharedPreferences("ui_customization", Context.MODE_PRIVATE)
            .edit().putFloat("ui_scale", value).apply()
    }
    
    //  同步读取 UI 缩放
    fun getUIScaleSync(context: Context): Float {
        return context.getSharedPreferences("ui_customization", Context.MODE_PRIVATE)
            .getFloat("ui_scale", 1.0f)
    }
    
    // ========== 📱 平板导航模式 ==========
    
    private val KEY_TABLET_NAVIGATION_MODE = booleanPreferencesKey("tablet_use_sidebar")
    
    /**
     *  平板导航模式
     * - false: 使用底栏（默认，与手机一致）
     * - true: 使用侧边栏
     */
    fun getTabletUseSidebar(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_TABLET_NAVIGATION_MODE] ?: false }  // 默认使用底栏

    suspend fun setTabletUseSidebar(context: Context, useSidebar: Boolean) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_TABLET_NAVIGATION_MODE] = useSidebar 
        }
    }
    
    // ========== [问题12] 视频操作按钮可见性 ==========
    
    private val KEY_HIDE_TRIPLE_BUTTON = booleanPreferencesKey("hide_triple_button")
    private val KEY_HIDE_CACHE_BUTTON = booleanPreferencesKey("hide_cache_button")
    
    /**
     *  隐藏三连按钮开关
     * - false: 显示（默认）
     * - true: 隐藏
     */
    fun getHideTripleButton(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HIDE_TRIPLE_BUTTON] ?: false }

    suspend fun setHideTripleButton(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_HIDE_TRIPLE_BUTTON] = value 
        }
    }
    
    /**
     *  隐藏缓存按钮开关
     * - false: 显示（默认）
     * - true: 隐藏
     */
    fun getHideCacheButton(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HIDE_CACHE_BUTTON] ?: false }

    suspend fun setHideCacheButton(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_HIDE_CACHE_BUTTON] = value 
        }
    }
    
    // ========== [问题3] 动态页布局方向 ==========
    
    private val KEY_DYNAMIC_PAGE_LAYOUT_DIRECTION = intPreferencesKey("dynamic_page_layout_direction")
    
    /**
     *  动态页布局方向
     * - 0: 左侧（默认，适合左撇子）
     * - 1: 右侧（适合右撇子）
     */
    enum class DynamicLayoutDirection(val value: Int, val label: String) {
        LEFT(0, "左侧"),
        RIGHT(1, "右侧");
        
        companion object {
            fun fromValue(value: Int): DynamicLayoutDirection = entries.find { it.value == value } ?: LEFT
        }
    }
    
    fun getDynamicLayoutDirection(context: Context): Flow<DynamicLayoutDirection> = context.settingsDataStore.data
        .map { preferences -> 
            DynamicLayoutDirection.fromValue(preferences[KEY_DYNAMIC_PAGE_LAYOUT_DIRECTION] ?: 0)
        }

    suspend fun setDynamicLayoutDirection(context: Context, direction: DynamicLayoutDirection) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_DYNAMIC_PAGE_LAYOUT_DIRECTION] = direction.value 
        }
    }
}
