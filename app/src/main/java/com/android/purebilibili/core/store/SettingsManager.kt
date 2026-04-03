// 文件路径: core/store/SettingsManager.kt
package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.store.home.HomeSettingsStore
import com.android.purebilibili.core.store.navigation.NavigationSettingsStore
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.theme.AppFontSizePreset
import com.android.purebilibili.core.theme.AppUiScalePreset
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.feature.settings.share.SettingsShareApplyResult
import com.android.purebilibili.feature.settings.share.SettingsShareEntryDefinition
import com.android.purebilibili.feature.settings.share.SettingsShareSection
import com.android.purebilibili.feature.settings.AppLanguage
import com.android.purebilibili.feature.settings.AppThemeMode
import com.android.purebilibili.feature.settings.DarkThemeStyle
import com.android.purebilibili.feature.settings.resolveAppLanguagePreference
import com.android.purebilibili.feature.settings.resolveDarkThemeStylePreference
import com.android.purebilibili.feature.settings.resolveThemeModePreference
import com.android.purebilibili.feature.video.danmaku.DANMAKU_DEFAULT_OPACITY
import com.android.purebilibili.feature.video.danmaku.normalizeDanmakuOpacity
import com.android.purebilibili.feature.video.danmaku.parseDanmakuBlockRules
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.ui.gesture.TwoFingerSpeedToggleState
import com.android.purebilibili.feature.video.ui.gesture.applyHorizontalTwoFingerSpeedToggle
import com.android.purebilibili.feature.video.ui.gesture.applyVerticalTwoFingerSpeedToggle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs

// 声明 DataStore 扩展属性
internal val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

internal const val DEFAULT_CRASH_TRACKING_ENABLED = true
internal const val DEFAULT_ANALYTICS_ENABLED = false
internal const val DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED = true

/**
 *  首页设置合并类 - 减少 HomeScreen 重组次数
 * 将多个独立的设置流合并为单一流，避免每个设置变化都触发重组
 */
enum class LiquidGlassStyle(val value: Int) {
    CLASSIC(0),      // BiliPai's Wavy Ripple
    SIMP_MUSIC(1),   // SimpMusic's Adaptive Lens
    IOS26(2);        // iOS26-like layered liquid glass

    companion object {
        fun fromValue(value: Int): LiquidGlassStyle = entries.find { it.value == value } ?: CLASSIC
    }
}

enum class LiquidGlassMode(val value: Int, val label: String) {
    CLEAR(0, "通透玻璃"),
    BALANCED(1, "平衡"),
    FROSTED(2, "柔和磨砂");

    companion object {
        fun fromValue(value: Int): LiquidGlassMode = entries.find { it.value == value } ?: BALANCED
    }
}

internal fun resolveLegacyLiquidGlassMode(style: LiquidGlassStyle): LiquidGlassMode = when (style) {
    LiquidGlassStyle.IOS26 -> LiquidGlassMode.CLEAR
    LiquidGlassStyle.CLASSIC -> LiquidGlassMode.BALANCED
    LiquidGlassStyle.SIMP_MUSIC -> LiquidGlassMode.FROSTED
}

internal fun resolveDefaultLiquidGlassStrength(mode: LiquidGlassMode): Float = when (mode) {
    LiquidGlassMode.CLEAR -> 0.42f
    LiquidGlassMode.BALANCED -> 0.52f
    LiquidGlassMode.FROSTED -> 0.62f
}

internal fun normalizeLiquidGlassStrength(value: Float): Float = value.coerceIn(0f, 1f)

internal fun normalizeLiquidGlassProgress(value: Float): Float = value.coerceIn(0f, 1f)

internal fun resolveLegacyLiquidGlassProgress(
    mode: LiquidGlassMode,
    strength: Float
): Float {
    val normalizedStrength = normalizeLiquidGlassStrength(strength)
    val (start, end) = when (mode) {
        LiquidGlassMode.CLEAR -> 0f to 0.32f
        LiquidGlassMode.BALANCED -> 0.34f to 0.66f
        LiquidGlassMode.FROSTED -> 0.68f to 1f
    }
    return normalizeLiquidGlassProgress(start + (end - start) * normalizedStrength)
}

internal fun resolveLegacyLiquidGlassProgress(style: LiquidGlassStyle): Float {
    val mode = resolveLegacyLiquidGlassMode(style)
    return resolveLegacyLiquidGlassProgress(
        mode = mode,
        strength = resolveDefaultLiquidGlassStrength(mode)
    )
}

internal fun resolveLiquidGlassModeFromProgress(progress: Float): LiquidGlassMode {
    val normalizedProgress = normalizeLiquidGlassProgress(progress)
    return when {
        normalizedProgress < 0.34f -> LiquidGlassMode.CLEAR
        normalizedProgress < 0.68f -> LiquidGlassMode.BALANCED
        else -> LiquidGlassMode.FROSTED
    }
}

internal fun resolveLiquidGlassStrengthFromProgress(progress: Float): Float {
    val normalizedProgress = normalizeLiquidGlassProgress(progress)
    val mode = resolveLiquidGlassModeFromProgress(normalizedProgress)
    val (start, end) = when (mode) {
        LiquidGlassMode.CLEAR -> 0f to 0.32f
        LiquidGlassMode.BALANCED -> 0.34f to 0.66f
        LiquidGlassMode.FROSTED -> 0.68f to 1f
    }
    return normalizeLiquidGlassStrength(
        if (end <= start) {
            0f
        } else {
            (normalizedProgress - start) / (end - start)
        }
    )
}

internal fun resolveLegacyLiquidGlassStyleFromProgress(progress: Float): LiquidGlassStyle {
    return when (resolveLiquidGlassModeFromProgress(progress)) {
        LiquidGlassMode.CLEAR -> LiquidGlassStyle.IOS26
        LiquidGlassMode.BALANCED -> LiquidGlassStyle.CLASSIC
        LiquidGlassMode.FROSTED -> LiquidGlassStyle.SIMP_MUSIC
    }
}

enum class HomeHeaderBlurMode(val value: Int, val label: String) {
    FOLLOW_PRESET(0, "跟随预设"),
    ALWAYS_ON(1, "始终开启"),
    ALWAYS_OFF(2, "始终关闭");

    companion object {
        fun fromValue(value: Int): HomeHeaderBlurMode {
            return entries.find { it.value == value } ?: FOLLOW_PRESET
        }
    }
}

internal fun resolveHomeHeaderBlurEnabled(
    mode: HomeHeaderBlurMode,
    uiPreset: UiPreset
): Boolean {
    return when (mode) {
        HomeHeaderBlurMode.FOLLOW_PRESET -> true
        HomeHeaderBlurMode.ALWAYS_ON -> true
        HomeHeaderBlurMode.ALWAYS_OFF -> false
    }
}

internal fun resolveHomeHeaderBlurModePreference(
    rawMode: Int?,
    legacyEnabled: Boolean?
): HomeHeaderBlurMode {
    return if (rawMode != null) {
        HomeHeaderBlurMode.fromValue(rawMode)
    } else if (legacyEnabled == false) {
        HomeHeaderBlurMode.ALWAYS_OFF
    } else {
        HomeHeaderBlurMode.FOLLOW_PRESET
    }
}

enum class PlaybackCompletionBehavior(val value: Int, val label: String) {
    CONTINUE_CURRENT_LOGIC(0, "自动连播"),
    STOP_AFTER_CURRENT(1, "播完暂停"),
    PLAY_IN_ORDER(2, "顺序播放"),
    REPEAT_ONE(3, "单个循环"),
    LOOP_PLAYLIST(4, "列表循环");

    companion object {
        fun fromValue(value: Int): PlaybackCompletionBehavior {
            return entries.find { it.value == value } ?: CONTINUE_CURRENT_LOGIC
        }
    }
}

enum class FullscreenMode(val value: Int, val label: String, val description: String) {
    AUTO(0, "自动", "按视频方向自动切换全屏方向"),
    NONE(1, "不改方向", "保持当前方向，仅切换全屏 UI"),
    VERTICAL(2, "竖屏", "进入全屏时保持竖屏"),
    HORIZONTAL(3, "横屏", "进入全屏时切换到横屏");

    companion object {
        fun fromValue(value: Int): FullscreenMode {
            return when (value) {
                // 兼容历史配置：已下线的模式统一回收为 AUTO，避免老用户进入无感知分支。
                4, 5 -> AUTO
                else -> entries.find { it.value == value } ?: AUTO
            }
        }
    }
}

enum class FullscreenAspectRatio(val value: Int, val label: String, val description: String) {
    FIT(0, "适应", "完整显示画面，尽量不裁切"),
    FILL(1, "填充", "填满屏幕，可能裁切边缘"),
    RATIO_16_9(2, "16:9", "优先按 16:9 展示画面"),
    RATIO_4_3(3, "4:3", "优先按 4:3 展示画面"),
    STRETCH(4, "拉伸", "铺满屏幕，可能导致画面变形");

    companion object {
        fun fromValue(value: Int): FullscreenAspectRatio {
            return entries.find { it.value == value } ?: FIT
        }
    }
}

enum class BottomProgressBehavior(
    val value: Int,
    val label: String,
    val description: String
) {
    ALWAYS_SHOW(0, "始终展示", "控件隐藏时始终显示底部细进度条"),
    ALWAYS_HIDE(1, "始终隐藏", "不显示底部细进度条"),
    ONLY_SHOW_FULLSCREEN(2, "仅全屏时展示", "仅横屏全屏且控件隐藏时显示"),
    ONLY_HIDE_FULLSCREEN(3, "仅全屏时隐藏", "非全屏且控件隐藏时显示");

    companion object {
        fun fromValue(value: Int): BottomProgressBehavior {
            return entries.find { it.value == value } ?: ALWAYS_SHOW
        }
    }
}

internal fun normalizeDanmakuDisplayArea(value: Float): Float {
    val normalized = value.coerceIn(0.25f, 1.0f)
    val supportedOptions = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f)
    return supportedOptions.minByOrNull { abs(it - normalized) } ?: 0.5f
}

internal fun normalizeDanmakuFontScale(value: Float): Float = value.coerceIn(0.3f, 2.0f)

internal const val DEFAULT_HOME_REFRESH_COUNT = 20
internal const val MIN_HOME_REFRESH_COUNT = 10
internal const val MAX_HOME_REFRESH_COUNT = 30

internal fun normalizeHomeRefreshCount(count: Int): Int {
    return count.coerceIn(MIN_HOME_REFRESH_COUNT, MAX_HOME_REFRESH_COUNT)
}

data class HomeSettings(
    val displayMode: Int = 0,              // 展示模式 (0=网格, 1=故事卡片)
    val isBottomBarFloating: Boolean = true,
    val bottomBarLabelMode: Int = 0,       // (0=图标+文字, 1=仅图标, 2=仅文字)
    val topTabLabelMode: Int = 2,          // (0=图标+文字, 1=仅图标, 2=仅文字)
    val isHeaderBlurEnabled: Boolean = true,
    val headerBlurMode: HomeHeaderBlurMode = HomeHeaderBlurMode.FOLLOW_PRESET,
    val isBottomBarBlurEnabled: Boolean = true,
    val isLiquidGlassEnabled: Boolean = true, // [New]
    val liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC, // [New]
    val liquidGlassMode: LiquidGlassMode = LiquidGlassMode.BALANCED,
    val liquidGlassStrength: Float = 0.52f,
    val liquidGlassProgress: Float = 0.5f,
    val isHeaderCollapseEnabled: Boolean = true, // [New] 首页顶部栏自动收缩开关
    val gridColumnCount: Int = 0, // [New] 网格列数 (0=自动, 1-6=固定)
    val cardAnimationEnabled: Boolean = false,    //  卡片进场动画（默认关闭）
    val cardTransitionEnabled: Boolean = true,    //  卡片过渡动画（默认开启）
    val videoTransitionRealtimeBlurEnabled: Boolean = true, // 视频转场实时模糊（默认开启）
    val predictiveBackAnimationEnabled: Boolean = true, // [New] 预测性返回手势支持（默认开启）
    val smartVisualGuardEnabled: Boolean = false, // [Retired] 智能流畅优先已下线，固定关闭
    val compactVideoStatsOnCover: Boolean = true, //  播放量/评论数显示在封面底部（默认开启）
    val showHomeCoverGlassBadges: Boolean = true, // 首页封面玻璃信息显示
    val showHomeInfoGlassBadges: Boolean = true, // 首页信息区玻璃标签显示
    val easterEggEnabled: Boolean = false, // 下拉刷新趣味提示开关
    //  [修复] 默认值改为 true，避免在 Flow 加载实际值之前错误触发弹窗
    // 当 Flow 加载完成后，如果实际值是 false，LaunchedEffect 会再次触发并显示弹窗
    val crashTrackingConsentShown: Boolean = true
)

internal fun resolveUiPresetPreferenceValue(rawValue: Int?): UiPreset {
    return UiPreset.fromValue(rawValue ?: UiPreset.IOS.value)
}

enum class DanmakuPanelWidthMode(val value: Int, val label: String, val widthFraction: Float) {
    FULL(0, "全宽", 1f),
    HALF(1, "半屏", 0.5f),
    THIRD(2, "1/3 屏", 1f / 3f);

    companion object {
        fun fromValue(value: Int): DanmakuPanelWidthMode =
            entries.find { it.value == value } ?: THIRD
    }
}

internal fun normalizeDanmakuFullscreenPanelWidthMode(
    mode: DanmakuPanelWidthMode
): DanmakuPanelWidthMode = DanmakuPanelWidthMode.THIRD

enum class DanmakuSettingsScope(
    val keyPrefix: String,
    val badgeLabel: String,
    val subtitle: String
) {
    PORTRAIT(
        keyPrefix = "portrait",
        badgeLabel = "竖屏专用",
        subtitle = "当前修改仅作用于竖屏观看"
    ),
    LANDSCAPE(
        keyPrefix = "landscape",
        badgeLabel = "横屏专用",
        subtitle = "当前修改仅作用于横屏观看"
    )
}

internal fun resolveDanmakuSettingsScope(isLandscape: Boolean): DanmakuSettingsScope {
    return if (isLandscape) DanmakuSettingsScope.LANDSCAPE else DanmakuSettingsScope.PORTRAIT
}

data class DanmakuSettings(
    val enabled: Boolean = true,
    val opacity: Float = DANMAKU_DEFAULT_OPACITY,
    val fontScale: Float = 1.0f,
    val speed: Float = 1.0f,
    val displayArea: Float = 0.5f,
    val mergeDuplicates: Boolean = true,
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val allowColorful: Boolean = true,
    val allowSpecial: Boolean = true,
    val smartOcclusion: Boolean = false,
    val fullscreenPanelWidthMode: DanmakuPanelWidthMode = DanmakuPanelWidthMode.THIRD,
    val blockRulesRaw: String = "",
    val blockRules: List<String> = emptyList()
)

data class AppNavigationSettings(
    val bottomBarVisibilityMode: SettingsManager.BottomBarVisibilityMode = SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE,
    val orderedVisibleTabIds: List<String> = listOf("HOME", "DYNAMIC", "HISTORY", "PROFILE"),
    val bottomBarItemColors: Map<String, Int> = emptyMap(),
    val tabletUseSidebar: Boolean = false
)

data class HomeTopTabSettings(
    val orderIds: List<String> = listOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME"),
    val visibleIds: Set<String> = setOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME")
)

data class PlayerInteractionSettings(
    val gestureSensitivity: Float = 1.0f,
    val doubleTapLikeEnabled: Boolean = true,
    val doubleTapSeekEnabled: Boolean = true,
    val portraitSwipeToFullscreenEnabled: Boolean = true,
    val centerSwipeToFullscreenEnabled: Boolean = true,
    val slideVolumeBrightnessEnabled: Boolean = true,
    val setSystemBrightnessEnabled: Boolean = false,
    val pipNoDanmakuEnabled: Boolean = false,
    val seekForwardSeconds: Int = 10,
    val seekBackwardSeconds: Int = 10,
    val fullscreenSwipeSeekSeconds: Int = 15,
    val fullscreenSwipeSeekEnabled: Boolean = true,
    val fullscreenGestureReverse: Boolean = false,
    val autoEnterFullscreenEnabled: Boolean = false,
    val autoExitFullscreenEnabled: Boolean = true,
    val fixedFullscreenAspectRatio: FullscreenAspectRatio = FullscreenAspectRatio.FIT,
    val subtitleAutoPreference: SubtitleAutoPreference = SubtitleAutoPreference.OFF,
    val longPressSpeed: Float = 2.0f,
    val twoFingerVerticalSpeedEnabled: Boolean = false,
    val twoFingerHorizontalSpeedEnabled: Boolean = false,
    val hiResLongPressCompatHintShown: Boolean = false
)

private sealed interface ShareablePreferenceDefinition {
    val entryDefinition: SettingsShareEntryDefinition

    fun read(preferences: Preferences): JsonElement?

    fun write(preferences: MutablePreferences, value: JsonElement): Boolean
}

private class BooleanShareablePreferenceDefinition(
    private val key: Preferences.Key<Boolean>,
    section: SettingsShareSection
) : ShareablePreferenceDefinition {
    override val entryDefinition = SettingsShareEntryDefinition(
        storageKey = key.name,
        section = section
    )

    override fun read(preferences: Preferences): JsonElement? {
        return preferences[key]?.let(::JsonPrimitive)
    }

    override fun write(preferences: MutablePreferences, value: JsonElement): Boolean {
        val parsed = value.jsonPrimitive.booleanOrNull ?: return false
        preferences[key] = parsed
        return true
    }
}

private class IntShareablePreferenceDefinition(
    private val key: Preferences.Key<Int>,
    section: SettingsShareSection
) : ShareablePreferenceDefinition {
    override val entryDefinition = SettingsShareEntryDefinition(
        storageKey = key.name,
        section = section
    )

    override fun read(preferences: Preferences): JsonElement? {
        return preferences[key]?.let(::JsonPrimitive)
    }

    override fun write(preferences: MutablePreferences, value: JsonElement): Boolean {
        val parsed = value.jsonPrimitive.intOrNull ?: return false
        preferences[key] = parsed
        return true
    }
}

private class FloatShareablePreferenceDefinition(
    private val key: Preferences.Key<Float>,
    section: SettingsShareSection
) : ShareablePreferenceDefinition {
    override val entryDefinition = SettingsShareEntryDefinition(
        storageKey = key.name,
        section = section
    )

    override fun read(preferences: Preferences): JsonElement? {
        return preferences[key]?.let(::JsonPrimitive)
    }

    override fun write(preferences: MutablePreferences, value: JsonElement): Boolean {
        val parsed = value.jsonPrimitive.content.toFloatOrNull() ?: return false
        preferences[key] = parsed
        return true
    }
}

private class StringShareablePreferenceDefinition(
    private val key: Preferences.Key<String>,
    section: SettingsShareSection
) : ShareablePreferenceDefinition {
    override val entryDefinition = SettingsShareEntryDefinition(
        storageKey = key.name,
        section = section
    )

    override fun read(preferences: Preferences): JsonElement? {
        return preferences[key]?.let(::JsonPrimitive)
    }

    override fun write(preferences: MutablePreferences, value: JsonElement): Boolean {
        preferences[key] = value.jsonPrimitive.content
        return true
    }
}

internal fun mapHomeSettingsFromPreferences(preferences: Preferences): HomeSettings {
    return SettingsManager.mapHomeSettingsFromPreferences(preferences)
}

internal fun mapDanmakuSettingsFromPreferences(
    preferences: Preferences,
    scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
): DanmakuSettings {
    return SettingsManager.mapDanmakuSettingsFromPreferences(preferences, scope)
}

internal fun mapAppNavigationSettingsFromPreferences(preferences: Preferences): AppNavigationSettings {
    return SettingsManager.mapAppNavigationSettingsFromPreferences(preferences)
}

internal fun mapHomeTopTabSettingsFromPreferences(preferences: Preferences): HomeTopTabSettings {
    return SettingsManager.mapHomeTopTabSettingsFromPreferences(preferences)
}

internal fun mapPlayerInteractionSettingsFromPreferences(
    preferences: Preferences
): PlayerInteractionSettings {
    return SettingsManager.mapPlayerInteractionSettingsFromPreferences(preferences)
}

object SettingsManager {
    // 键定义
    private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
    private val KEY_PLAYBACK_COMPLETION_BEHAVIOR = intPreferencesKey("playback_completion_behavior")
    private val KEY_HW_DECODE = booleanPreferencesKey("hw_decode")
    private val KEY_THEME_MODE = intPreferencesKey("theme_mode_v2")
    private val KEY_DARK_THEME_STYLE = intPreferencesKey("dark_theme_style_v1")
    private val KEY_APP_LANGUAGE = intPreferencesKey("app_language_v1")
    private val KEY_UI_PRESET = intPreferencesKey("ui_preset")
    private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val KEY_BG_PLAY = booleanPreferencesKey("bg_play")
    //  [新增] 触感反馈 (默认开启)
    private val KEY_HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
    //  [新增] 手势灵敏度和主题色
    private val KEY_GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
    private val KEY_SLIDE_VOLUME_BRIGHTNESS_ENABLED = booleanPreferencesKey("slide_volume_brightness_enabled")
    private val KEY_SET_SYSTEM_BRIGHTNESS = booleanPreferencesKey("set_system_brightness")
    private val KEY_PIP_NO_DANMAKU = booleanPreferencesKey("pip_no_danmaku")
    private val KEY_SEARCH_HOT_SECTION_ENABLED = booleanPreferencesKey("search_hot_section_enabled")
    //  [新增] 双击跳转秒数 (可分开设置快进和后退)
    private val KEY_DOUBLE_TAP_SEEK_ENABLED = booleanPreferencesKey("double_tap_seek_enabled")
    private val KEY_SEEK_FORWARD_SECONDS = intPreferencesKey("seek_forward_seconds")
    private val KEY_SEEK_BACKWARD_SECONDS = intPreferencesKey("seek_backward_seconds")
    //  [新增] 长按倍速 (默认 2.0x)
    private val KEY_LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")
    private val KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED =
        booleanPreferencesKey("two_finger_vertical_speed_enabled")
    private val KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED =
        booleanPreferencesKey("two_finger_horizontal_speed_enabled")
    private val KEY_HI_RES_LONG_PRESS_COMPAT_HINT_SHOWN =
        booleanPreferencesKey("hi_res_long_press_compat_hint_shown")
    //  [新增] 默认播放速度/记忆上次播放速度
    private val KEY_DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
    private val KEY_REMEMBER_LAST_PLAYBACK_SPEED = booleanPreferencesKey("remember_last_playback_speed")
    private val KEY_LAST_PLAYBACK_SPEED = floatPreferencesKey("last_playback_speed")
    private val KEY_THEME_COLOR_INDEX = intPreferencesKey("theme_color_index")
    private val KEY_APP_FONT_SIZE_PRESET = intPreferencesKey("app_font_size_preset")
    private val KEY_APP_UI_SCALE_PRESET = intPreferencesKey("app_ui_scale_preset")
    private val KEY_APP_DPI_OVERRIDE_PERCENT = intPreferencesKey("app_dpi_override_percent")
    //  [新增] 应用图标 Key (Blue, Red, Green...)
    private val KEY_APP_ICON = androidx.datastore.preferences.core.stringPreferencesKey("app_icon_key")
    //  [新增] 底部栏样式 (true=悬浮, false=贴底)
    private val KEY_BOTTOM_BAR_FLOATING = booleanPreferencesKey("bottom_bar_floating")
    //  [新增] 底栏显示模式 (0=图标+文字, 1=仅图标, 2=仅文字)
    private val KEY_BOTTOM_BAR_LABEL_MODE = intPreferencesKey("bottom_bar_label_mode")
    //  [新增] 顶部标签显示模式 (0=图标+文字, 1=仅图标, 2=仅文字)
    private val KEY_TOP_TAB_LABEL_MODE = intPreferencesKey("top_tab_label_mode")
    //  [新增] 顶部标签自定义 - 顺序和可见性
    private val KEY_TOP_TAB_ORDER = stringPreferencesKey("top_tab_order")
    private val KEY_TOP_TAB_VISIBLE_TABS = stringPreferencesKey("top_tab_visible_tabs")
    
    //  [新增] 开屏壁纸
    private val KEY_SPLASH_WALLPAPER_URI = stringPreferencesKey("splash_wallpaper_uri")
    private val KEY_SPLASH_WALLPAPER_HISTORY = stringPreferencesKey("splash_wallpaper_history")
    private val KEY_SPLASH_RANDOM_POOL_URIS = stringPreferencesKey("splash_random_pool_uris")
    private val KEY_SPLASH_ENABLED = booleanPreferencesKey("splash_enabled")
    private val KEY_SPLASH_RANDOM_ENABLED = booleanPreferencesKey("splash_random_enabled")
    private val KEY_SPLASH_ICON_ANIMATION_ENABLED = booleanPreferencesKey("splash_icon_animation_enabled")
    private val KEY_SPLASH_ALIGNMENT_MOBILE = floatPreferencesKey("splash_alignment_mobile")
    private val KEY_SPLASH_ALIGNMENT_TABLET = floatPreferencesKey("splash_alignment_tablet")
    private const val SPLASH_PREFS = "splash_prefs"
    private const val SPLASH_PREFS_KEY_WALLPAPER_URI = "wallpaper_uri"
    private const val SPLASH_PREFS_KEY_WALLPAPER_HISTORY = "wallpaper_history"
    private const val SPLASH_PREFS_KEY_RANDOM_POOL_URIS = "random_pool_uris"
    private const val SPLASH_PREFS_KEY_ENABLED = "enabled"
    private const val SPLASH_PREFS_KEY_RANDOM_ENABLED = "random_enabled"
    private const val SPLASH_PREFS_KEY_ICON_ANIMATION_ENABLED = "icon_animation_enabled"
    private const val SPLASH_PREFS_KEY_ALIGNMENT_MOBILE = "alignment_mobile"
    private const val SPLASH_PREFS_KEY_ALIGNMENT_TABLET = "alignment_tablet"

    //  [New] 解锁高画质 (Bypass client-side checks) - REVERTED
    // private val KEY_UNLOCK_HIGH_QUALITY = booleanPreferencesKey("unlock_high_quality")
    
    object BottomBarLabelMode {
        const val SELECTED = 0 // 兼容 AppNavigation 的调用
        const val ICON_AND_TEXT = 0
        const val ICON_ONLY = 1
        const val TEXT_ONLY = 2
    }

    object TopTabLabelMode {
        const val ICON_AND_TEXT = 0
        const val ICON_ONLY = 1
        const val TEXT_ONLY = 2
    }

    private const val DEFAULT_TOP_TAB_ORDER = "RECOMMEND,FOLLOW,POPULAR,LIVE,GAME"
    private const val DEFAULT_TOP_TAB_VISIBLE = "RECOMMEND,FOLLOW,POPULAR,LIVE,GAME"
    //  [新增] 模糊效果开关
    private val KEY_HEADER_BLUR_ENABLED = booleanPreferencesKey("header_blur_enabled")
    private val KEY_HOME_HEADER_BLUR_MODE = intPreferencesKey("home_header_blur_mode")
    //  [新增] 首页顶部栏自动收缩 (Shrink)
    private val KEY_HEADER_COLLAPSE_ENABLED = booleanPreferencesKey("header_collapse_enabled")
    private val KEY_BOTTOM_BAR_BLUR_ENABLED = booleanPreferencesKey("bottom_bar_blur_enabled")
    //  [New] Liquid Glass Effect Toggle (Default On)
    private val KEY_LIQUID_GLASS_ENABLED = booleanPreferencesKey("liquid_glass_enabled")
    
    // MOVED KEY_LIQUID_GLASS_STYLE down to where enum is defined to avoid forward reference issues if Kotlin 
    // but better to keep keys together. 
    // For simplicity, I will use getLiquidGlassStyle() helper in the flow below.

    //  [新增] 模糊强度 (ULTRA_THIN, THIN, THICK)
    private val KEY_BLUR_INTENSITY = stringPreferencesKey("blur_intensity")
    //  [合并] 首页展示模式 (0=Grid, 1=Story, 2=Glass)
    private val KEY_DISPLAY_MODE = intPreferencesKey("display_mode")
    //  [新增] 网格列数 (0=Auto)
    private val KEY_GRID_COLUMN_COUNT = intPreferencesKey("grid_column_count")
    //  [新增] 卡片动画开关
    private val KEY_CARD_ANIMATION_ENABLED = booleanPreferencesKey("card_animation_enabled")
    //  [新增] 卡片过渡动画开关
    private val KEY_CARD_TRANSITION_ENABLED = booleanPreferencesKey("card_transition_enabled")
    private val KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED =
        booleanPreferencesKey("video_transition_realtime_blur_enabled")
    // [New] 预测性返回手势支持开关
    private val KEY_PREDICTIVE_BACK_ANIMATION_ENABLED = booleanPreferencesKey("predictive_back_animation_enabled")
    // [New] 运行时视觉降级守卫开关
    private val KEY_SMART_VISUAL_GUARD_ENABLED = booleanPreferencesKey("smart_visual_guard_enabled")
    //  [新增] 视频卡片统计信息贴封面开关
    private val KEY_COMPACT_VIDEO_STATS_ON_COVER = booleanPreferencesKey("compact_video_stats_on_cover")
    private val KEY_HOME_COVER_GLASS_BADGES_VISIBLE = booleanPreferencesKey("home_cover_glass_badges_visible")
    private val KEY_HOME_INFO_GLASS_BADGES_VISIBLE = booleanPreferencesKey("home_info_glass_badges_visible")
    //  [合并] 崩溃追踪同意弹窗
    private val KEY_CRASH_TRACKING_CONSENT_SHOWN = booleanPreferencesKey("crash_tracking_consent_shown")
    private val KEY_LIQUID_GLASS_MODE = intPreferencesKey("liquid_glass_mode")
    private val KEY_LIQUID_GLASS_STRENGTH = floatPreferencesKey("liquid_glass_strength")
    private val KEY_LIQUID_GLASS_PROGRESS = floatPreferencesKey("liquid_glass_progress")
    //  [新增] 底栏自定义 - 顺序和可见性
    private val KEY_BOTTOM_BAR_ORDER = stringPreferencesKey("bottom_bar_order")  // 逗号分隔的项目顺序
    private val KEY_BOTTOM_BAR_VISIBLE_TABS = stringPreferencesKey("bottom_bar_visible_tabs")  // 逗号分隔的可见项目
    private val KEY_BOTTOM_BAR_ITEM_COLORS = stringPreferencesKey("bottom_bar_item_colors")  //  格式: HOME:0,DYNAMIC:1,...
    private const val DEFAULT_BOTTOM_BAR_ORDER = "HOME,DYNAMIC,HISTORY,PROFILE"
    private const val DEFAULT_BOTTOM_BAR_VISIBLE_TABS = "HOME,DYNAMIC,HISTORY,PROFILE"
    //  [新增] 评论默认排序（1=回复,2=最新,3=最热,4=点赞）
    private val KEY_COMMENT_DEFAULT_SORT_MODE = intPreferencesKey("comment_default_sort_mode")
    //  [新增] 离开播放页后停止播放（优先于小窗/画中画模式）
    private val KEY_STOP_PLAYBACK_ON_EXIT = booleanPreferencesKey("stop_playback_on_exit")
    private val KEY_BACKGROUND_PLAYBACK_ENABLED = booleanPreferencesKey("background_playback_enabled")
    private val KEY_AUDIO_FOCUS_ENABLED = booleanPreferencesKey("audio_focus_enabled")
    private val KEY_AUDIO_MODE_AUTO_PIP_ENABLED = booleanPreferencesKey("audio_mode_auto_pip_enabled")
    private val KEY_VIDEO_AI_SUMMARY_ENTRY_ENABLED = booleanPreferencesKey("video_ai_summary_entry_enabled")
    private const val PLAYBACK_SPEED_CACHE_PREFS = "playback_speed_cache"
    private const val CACHE_KEY_DEFAULT_PLAYBACK_SPEED = "default_speed"
    private const val CACHE_KEY_REMEMBER_LAST_SPEED = "remember_last_speed"
    private const val CACHE_KEY_LAST_PLAYBACK_SPEED = "last_speed"
    /**
     *  合并首页相关设置为单一 Flow
     * 避免 HomeScreen 中多个 collectAsState 导致频繁重组
     */
    internal fun mapHomeSettingsFromPreferences(preferences: Preferences): HomeSettings {
        val headerBlurMode = resolveHomeHeaderBlurModePreference(
            rawMode = preferences[KEY_HOME_HEADER_BLUR_MODE],
            legacyEnabled = preferences[KEY_HEADER_BLUR_ENABLED]
        )
        val legacyStyle = LiquidGlassStyle.fromValue(
            preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.CLASSIC.value
        )
        val liquidGlassMode = preferences[KEY_LIQUID_GLASS_MODE]
            ?.let(LiquidGlassMode::fromValue)
            ?: resolveLegacyLiquidGlassMode(legacyStyle)
        val liquidGlassStrength = normalizeLiquidGlassStrength(
            preferences[KEY_LIQUID_GLASS_STRENGTH] ?: resolveDefaultLiquidGlassStrength(liquidGlassMode)
        )
        val liquidGlassProgress = normalizeLiquidGlassProgress(
            preferences[KEY_LIQUID_GLASS_PROGRESS] ?: resolveLegacyLiquidGlassProgress(
                mode = liquidGlassMode,
                strength = liquidGlassStrength
            )
        )
        return HomeSettings(
            displayMode = preferences[KEY_DISPLAY_MODE] ?: 0,
            isBottomBarFloating = preferences[KEY_BOTTOM_BAR_FLOATING] ?: true,
            bottomBarLabelMode = preferences[KEY_BOTTOM_BAR_LABEL_MODE] ?: BottomBarLabelMode.ICON_AND_TEXT,
            topTabLabelMode = preferences[KEY_TOP_TAB_LABEL_MODE] ?: TopTabLabelMode.TEXT_ONLY,
            isHeaderBlurEnabled = headerBlurMode != HomeHeaderBlurMode.ALWAYS_OFF,
            headerBlurMode = headerBlurMode,
            isHeaderCollapseEnabled = preferences[KEY_HEADER_COLLAPSE_ENABLED] ?: true,
            isBottomBarBlurEnabled = preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] ?: true,
            isLiquidGlassEnabled = preferences[KEY_LIQUID_GLASS_ENABLED] ?: true,
            liquidGlassStyle = legacyStyle,
            liquidGlassMode = liquidGlassMode,
            liquidGlassStrength = liquidGlassStrength,
            liquidGlassProgress = liquidGlassProgress,
            gridColumnCount = preferences[KEY_GRID_COLUMN_COUNT] ?: 0,
            cardAnimationEnabled = preferences[KEY_CARD_ANIMATION_ENABLED] ?: false,
            cardTransitionEnabled = preferences[KEY_CARD_TRANSITION_ENABLED] ?: true,
            videoTransitionRealtimeBlurEnabled =
                preferences[KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED] ?: true,
            predictiveBackAnimationEnabled = preferences[KEY_PREDICTIVE_BACK_ANIMATION_ENABLED] ?: true,
            smartVisualGuardEnabled = false,
            compactVideoStatsOnCover = preferences[KEY_COMPACT_VIDEO_STATS_ON_COVER] ?: true,
            showHomeCoverGlassBadges = preferences[KEY_HOME_COVER_GLASS_BADGES_VISIBLE] ?: true,
            showHomeInfoGlassBadges = preferences[KEY_HOME_INFO_GLASS_BADGES_VISIBLE] ?: true,
            easterEggEnabled = preferences[KEY_EASTER_EGG_ENABLED] ?: false,
            // 保持现有运行时行为：首次未配置时按 false 返回
            crashTrackingConsentShown = preferences[KEY_CRASH_TRACKING_CONSENT_SHOWN] ?: false
        )
    }

    fun getHomeSettings(context: Context): Flow<HomeSettings> {
        return HomeSettingsStore.observe(context)
    }

    internal fun mapHomeTopTabSettingsFromPreferences(preferences: Preferences): HomeTopTabSettings {
        val orderIds = (preferences[KEY_TOP_TAB_ORDER] ?: DEFAULT_TOP_TAB_ORDER)
            .split(",")
            .filter { it.isNotBlank() }
        val visibleIds = (preferences[KEY_TOP_TAB_VISIBLE_TABS] ?: DEFAULT_TOP_TAB_VISIBLE)
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()
        return HomeTopTabSettings(
            orderIds = orderIds,
            visibleIds = visibleIds
        )
    }

    fun getHomeTopTabSettings(context: Context): Flow<HomeTopTabSettings> = context.settingsDataStore.data
        .map(::mapHomeTopTabSettingsFromPreferences)
        .distinctUntilChanged()

    internal fun mapPlayerInteractionSettingsFromPreferences(
        preferences: Preferences
    ): PlayerInteractionSettings {
        return PlayerInteractionSettings(
            gestureSensitivity = (preferences[KEY_GESTURE_SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 2.0f),
            doubleTapLikeEnabled = preferences[KEY_DOUBLE_TAP_LIKE] ?: true,
            doubleTapSeekEnabled = preferences[KEY_DOUBLE_TAP_SEEK_ENABLED] ?: true,
            portraitSwipeToFullscreenEnabled = preferences[KEY_PORTRAIT_SWIPE_TO_FULLSCREEN] ?: true,
            centerSwipeToFullscreenEnabled = preferences[KEY_CENTER_SWIPE_TO_FULLSCREEN] ?: true,
            slideVolumeBrightnessEnabled = preferences[KEY_SLIDE_VOLUME_BRIGHTNESS_ENABLED] ?: true,
            setSystemBrightnessEnabled = preferences[KEY_SET_SYSTEM_BRIGHTNESS] ?: false,
            pipNoDanmakuEnabled = preferences[KEY_PIP_NO_DANMAKU] ?: false,
            seekForwardSeconds = (preferences[KEY_SEEK_FORWARD_SECONDS] ?: 10).coerceIn(1, 60),
            seekBackwardSeconds = (preferences[KEY_SEEK_BACKWARD_SECONDS] ?: 10).coerceIn(1, 60),
            fullscreenSwipeSeekSeconds = normalizeFullscreenSwipeSeekSeconds(
                preferences[KEY_FULLSCREEN_SWIPE_SEEK_SECONDS] ?: 15
            ),
            fullscreenSwipeSeekEnabled = preferences[KEY_FULLSCREEN_SWIPE_SEEK_ENABLED] ?: true,
            fullscreenGestureReverse = preferences[KEY_FULLSCREEN_GESTURE_REVERSE] ?: false,
            autoEnterFullscreenEnabled = preferences[KEY_AUTO_ENTER_FULLSCREEN] ?: false,
            autoExitFullscreenEnabled = preferences[KEY_AUTO_EXIT_FULLSCREEN] ?: true,
            fixedFullscreenAspectRatio = FullscreenAspectRatio.fromValue(
                preferences[KEY_FULLSCREEN_ASPECT_RATIO] ?: FullscreenAspectRatio.FIT.value
            ),
            subtitleAutoPreference = SubtitleAutoPreference.entries.getOrElse(
                preferences[KEY_SUBTITLE_AUTO_PREFERENCE] ?: SubtitleAutoPreference.OFF.ordinal
            ) { SubtitleAutoPreference.OFF },
            longPressSpeed = normalizeLongPressSpeed(
                preferences[KEY_LONG_PRESS_SPEED] ?: DEFAULT_LONG_PRESS_SPEED
            ),
            twoFingerVerticalSpeedEnabled = preferences[KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED] ?: false,
            twoFingerHorizontalSpeedEnabled = preferences[KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED] ?: false,
            hiResLongPressCompatHintShown = preferences[KEY_HI_RES_LONG_PRESS_COMPAT_HINT_SHOWN] ?: false
        )
    }

    fun getPlayerInteractionSettings(context: Context): Flow<PlayerInteractionSettings> =
        context.settingsDataStore.data
            .map(::mapPlayerInteractionSettingsFromPreferences)
            .distinctUntilChanged()

    // --- Auto Play on Enter (Click to Play) ---
    private val KEY_CLICK_TO_PLAY = booleanPreferencesKey("click_to_play")
    private val KEY_RESUME_PLAYBACK_PROMPT_ENABLED = booleanPreferencesKey("resume_playback_prompt_enabled")
    private const val RESUME_PROMPT_CACHE_PREFS = "resume_prompt_cache"
    private const val CACHE_KEY_RESUME_PROMPT_ENABLED = "resume_prompt_enabled"
    private const val CACHE_KEY_RESUME_PROMPT_SHOWN = "resume_prompt_shown"
    private const val HI_RES_LONG_PRESS_HINT_CACHE_PREFS = "hi_res_long_press_hint_cache"
    private const val CACHE_KEY_HI_RES_LONG_PRESS_HINT_SHOWN = "hi_res_long_press_hint_shown"

    fun getClickToPlay(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_CLICK_TO_PLAY] ?: true }

    suspend fun setClickToPlay(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_CLICK_TO_PLAY] = value }
        // Sync to SharedPreferences for synchronous access
        context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .edit().putBoolean("click_to_play_enabled", value).apply()
    }

    fun getClickToPlaySync(context: Context): Boolean {
        return context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .getBoolean("click_to_play_enabled", true)
    }

    fun getResumePlaybackPromptEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_RESUME_PLAYBACK_PROMPT_ENABLED] ?: true }

    suspend fun setResumePlaybackPromptEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_RESUME_PLAYBACK_PROMPT_ENABLED] = value
        }
        context.getSharedPreferences(RESUME_PROMPT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_RESUME_PROMPT_ENABLED, value)
            .apply()
    }

    fun getResumePlaybackPromptEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences(RESUME_PROMPT_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_RESUME_PROMPT_ENABLED, true)
    }

    fun hasResumePlaybackPromptShown(context: Context, promptKey: String): Boolean {
        if (promptKey.isBlank()) return false
        val shownSet = context.getSharedPreferences(RESUME_PROMPT_CACHE_PREFS, Context.MODE_PRIVATE)
            .getStringSet(CACHE_KEY_RESUME_PROMPT_SHOWN, emptySet())
            .orEmpty()
        return shownSet.contains(promptKey)
    }

    fun markResumePlaybackPromptShown(context: Context, promptKey: String) {
        if (promptKey.isBlank()) return
        val prefs = context.getSharedPreferences(RESUME_PROMPT_CACHE_PREFS, Context.MODE_PRIVATE)
        val shownSet = prefs.getStringSet(CACHE_KEY_RESUME_PROMPT_SHOWN, emptySet())
            .orEmpty()
            .toMutableSet()
        if (shownSet.contains(promptKey)) return
        if (shownSet.size >= 500) {
            shownSet.clear()
        }
        shownSet.add(promptKey)
        prefs.edit()
            .putStringSet(CACHE_KEY_RESUME_PROMPT_SHOWN, shownSet)
            .apply()
    }

    // --- Auto Play Next ---
    private val KEY_EXTERNAL_PLAYLIST_AUTO_CONTINUE =
        booleanPreferencesKey("external_playlist_auto_continue")
    private const val CACHE_KEY_EXTERNAL_PLAYLIST_AUTO_CONTINUE = "external_playlist_auto_continue"

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

    fun getExternalPlaylistAutoContinue(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_EXTERNAL_PLAYLIST_AUTO_CONTINUE] ?: true }

    suspend fun setExternalPlaylistAutoContinue(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_EXTERNAL_PLAYLIST_AUTO_CONTINUE] = value
        }
        context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_EXTERNAL_PLAYLIST_AUTO_CONTINUE, value)
            .apply()
    }

    fun getExternalPlaylistAutoContinueSync(context: Context): Boolean {
        return context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_EXTERNAL_PLAYLIST_AUTO_CONTINUE, true)
    }

    fun getPlaybackCompletionBehavior(context: Context): Flow<PlaybackCompletionBehavior> =
        context.settingsDataStore.data.map { preferences ->
            val value = preferences[KEY_PLAYBACK_COMPLETION_BEHAVIOR]
                ?: PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC.value
            PlaybackCompletionBehavior.fromValue(value)
        }

    suspend fun setPlaybackCompletionBehavior(
        context: Context,
        behavior: PlaybackCompletionBehavior
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PLAYBACK_COMPLETION_BEHAVIOR] = behavior.value
        }
        context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .edit()
            .putInt("playback_completion_behavior", behavior.value)
            .apply()
    }

    fun getPlaybackCompletionBehaviorSync(context: Context): PlaybackCompletionBehavior {
        val value = context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .getInt(
                "playback_completion_behavior",
                PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC.value
            )
        return PlaybackCompletionBehavior.fromValue(value)
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
            resolveThemeModePreference(modeInt)
        }

    fun getAppLanguage(context: Context): Flow<AppLanguage> = context.settingsDataStore.data
        .map { preferences ->
            resolveAppLanguagePreference(preferences[KEY_APP_LANGUAGE])
        }

    fun getDarkThemeStyle(context: Context): Flow<DarkThemeStyle> = context.settingsDataStore.data
        .map { preferences ->
            resolveDarkThemeStylePreference(
                darkThemeStyleValue = preferences[KEY_DARK_THEME_STYLE],
                legacyThemeModeValue = preferences[KEY_THEME_MODE]
            )
        }

    suspend fun setThemeMode(context: Context, mode: AppThemeMode) {
        var resolvedDarkThemeStyle = DarkThemeStyle.DEFAULT
        context.settingsDataStore.edit { preferences ->
            resolvedDarkThemeStyle = resolveDarkThemeStylePreference(
                darkThemeStyleValue = preferences[KEY_DARK_THEME_STYLE],
                legacyThemeModeValue = preferences[KEY_THEME_MODE]
            )
            if (preferences[KEY_DARK_THEME_STYLE] == null) {
                preferences[KEY_DARK_THEME_STYLE] = resolvedDarkThemeStyle.value
            }
            preferences[KEY_THEME_MODE] = mode.value
        }
        //  同步到 SharedPreferences，供 PureApplication 同步读取使用
        // 使用 commit() 确保立即写入
        val success = context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE)
            .edit()
            .putInt("theme_mode", mode.value)
            .putInt("dark_theme_style", resolvedDarkThemeStyle.value)
            .commit()
        com.android.purebilibili.core.util.Logger.d("SettingsManager", " Theme mode saved: ${mode.value} (${mode.label}), success=$success")
        
        //  同时应用到 AppCompatDelegate，使当前运行时生效
        val nightMode = when (mode) {
            AppThemeMode.FOLLOW_SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    suspend fun setAppLanguage(context: Context, appLanguage: AppLanguage) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_APP_LANGUAGE] = appLanguage.value
        }
        context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE)
            .edit()
            .putInt("app_language", appLanguage.value)
            .commit()
    }

    fun getAppLanguageSync(context: Context): AppLanguage {
        val rawValue = context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE)
            .getInt("app_language", AppLanguage.FOLLOW_SYSTEM.value)
        return resolveAppLanguagePreference(rawValue)
    }

    suspend fun setDarkThemeStyle(context: Context, style: DarkThemeStyle) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_DARK_THEME_STYLE] = style.value }
        val success = context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE)
            .edit().putInt("dark_theme_style", style.value).commit()
        com.android.purebilibili.core.util.Logger.d(
            "SettingsManager",
            " Dark theme style saved: ${style.value} (${style.label}), success=$success"
        )
    }

    fun getUiPreset(context: Context): Flow<UiPreset> = context.settingsDataStore.data
        .map { preferences ->
            resolveUiPresetPreferenceValue(preferences[KEY_UI_PRESET])
        }

    suspend fun setUiPreset(context: Context, preset: UiPreset) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_UI_PRESET] = preset.value
        }
    }

    // --- Dynamic Color ---
    fun getDynamicColor(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DYNAMIC_COLOR] ?: true }

    suspend fun setDynamicColor(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_DYNAMIC_COLOR] = value }
    }

    fun getAppFontSizePreset(context: Context): Flow<AppFontSizePreset> = context.settingsDataStore.data
        .map { preferences ->
            AppFontSizePreset.fromValue(
                preferences[KEY_APP_FONT_SIZE_PRESET] ?: AppFontSizePreset.DEFAULT.value
            )
        }

    suspend fun setAppFontSizePreset(context: Context, preset: AppFontSizePreset) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_APP_FONT_SIZE_PRESET] = preset.value
        }
    }

    fun getAppUiScalePreset(context: Context): Flow<AppUiScalePreset> = context.settingsDataStore.data
        .map { preferences ->
            AppUiScalePreset.fromValue(
                preferences[KEY_APP_UI_SCALE_PRESET] ?: AppUiScalePreset.STANDARD.value
            )
        }

    suspend fun setAppUiScalePreset(context: Context, preset: AppUiScalePreset) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_APP_UI_SCALE_PRESET] = preset.value
        }
    }

    fun getAppDpiOverridePercent(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            val rawValue = preferences[KEY_APP_DPI_OVERRIDE_PERCENT] ?: 0
            if (rawValue == 0) 0 else rawValue.coerceIn(85, 115)
        }

    suspend fun setAppDpiOverridePercent(context: Context, percent: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_APP_DPI_OVERRIDE_PERCENT] = if (percent == 0) 0 else percent.coerceIn(85, 115)
        }
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

    //  [新增] --- 触感反馈 ---
    fun getHapticFeedbackEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HAPTIC_FEEDBACK_ENABLED] ?: true } // 默认开启

    suspend fun setHapticFeedbackEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_HAPTIC_FEEDBACK_ENABLED] = value }
        // 同步到 SharedPreferences，供同步读取 (例如 modifier 中)
        context.getSharedPreferences("haptic_cache", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply()
    }

    fun isHapticFeedbackEnabledSync(context: Context): Boolean {
        // 优先读取缓存
        return context.getSharedPreferences("haptic_cache", Context.MODE_PRIVATE)
            .getBoolean("enabled", true)
    }

    //  [新增] --- 手势灵敏度 (0.5 ~ 2.0, 默认 1.0) ---
    fun getGestureSensitivity(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_GESTURE_SENSITIVITY] ?: 1.0f }

    suspend fun setGestureSensitivity(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_GESTURE_SENSITIVITY] = value.coerceIn(0.5f, 2.0f) 
        }
    }

    fun getSlideVolumeBrightnessEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SLIDE_VOLUME_BRIGHTNESS_ENABLED] ?: true }

    suspend fun setSlideVolumeBrightnessEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SLIDE_VOLUME_BRIGHTNESS_ENABLED] = value
        }
    }

    fun getSetSystemBrightnessEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SET_SYSTEM_BRIGHTNESS] ?: false }

    suspend fun setSetSystemBrightnessEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SET_SYSTEM_BRIGHTNESS] = value
        }
    }

    fun getPipNoDanmakuEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_PIP_NO_DANMAKU] ?: false }

    suspend fun setPipNoDanmakuEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PIP_NO_DANMAKU] = value
        }
    }

    //  [新增] --- 双击跳转秒数 ---
    fun getDoubleTapSeekEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DOUBLE_TAP_SEEK_ENABLED] ?: true } // 默认开启

    suspend fun setDoubleTapSeekEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_DOUBLE_TAP_SEEK_ENABLED] = value }
    }

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

    //  [新增] --- 长按倍速 (默认 2.0x，Hi-Res 音频运行时单独限速) ---
    fun getLongPressSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> normalizeLongPressSpeed(preferences[KEY_LONG_PRESS_SPEED] ?: DEFAULT_LONG_PRESS_SPEED) }

    suspend fun setLongPressSpeed(context: Context, speed: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_LONG_PRESS_SPEED] = normalizeLongPressSpeed(speed)
        }
    }

    fun getTwoFingerVerticalSpeedEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED] ?: false }

    suspend fun setTwoFingerVerticalSpeedEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val current = TwoFingerSpeedToggleState(
                verticalEnabled = preferences[KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED] ?: false,
                horizontalEnabled = preferences[KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED] ?: false
            )
            val updated = applyVerticalTwoFingerSpeedToggle(current, enabled)
            preferences[KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED] = updated.verticalEnabled
            preferences[KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED] = updated.horizontalEnabled
        }
    }

    fun getTwoFingerHorizontalSpeedEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED] ?: false }

    suspend fun setTwoFingerHorizontalSpeedEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val current = TwoFingerSpeedToggleState(
                verticalEnabled = preferences[KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED] ?: false,
                horizontalEnabled = preferences[KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED] ?: false
            )
            val updated = applyHorizontalTwoFingerSpeedToggle(current, enabled)
            preferences[KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED] = updated.verticalEnabled
            preferences[KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED] = updated.horizontalEnabled
        }
    }

    fun getHiResLongPressCompatHintShown(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_HI_RES_LONG_PRESS_COMPAT_HINT_SHOWN] ?: false }

    suspend fun setHiResLongPressCompatHintShown(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HI_RES_LONG_PRESS_COMPAT_HINT_SHOWN] = value
        }
        context.getSharedPreferences(HI_RES_LONG_PRESS_HINT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_HI_RES_LONG_PRESS_HINT_SHOWN, value)
            .apply()
    }

    fun getHiResLongPressCompatHintShownSync(context: Context): Boolean {
        return context.getSharedPreferences(HI_RES_LONG_PRESS_HINT_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_HI_RES_LONG_PRESS_HINT_SHOWN, false)
    }

    //  [新增] --- 默认播放速度 / 记忆上次速度 ---
    fun getDefaultPlaybackSpeed(context: Context): Flow<Float> =
        PlayerSettingsStore.getDefaultPlaybackSpeed(context)

    suspend fun setDefaultPlaybackSpeed(context: Context, speed: Float) {
        PlayerSettingsStore.setDefaultPlaybackSpeed(context, speed)
    }

    fun getRememberLastPlaybackSpeed(context: Context): Flow<Boolean> =
        PlayerSettingsStore.getRememberLastPlaybackSpeed(context)

    suspend fun setRememberLastPlaybackSpeed(context: Context, enabled: Boolean) {
        PlayerSettingsStore.setRememberLastPlaybackSpeed(context, enabled)
    }

    fun getLastPlaybackSpeed(context: Context): Flow<Float> =
        PlayerSettingsStore.getLastPlaybackSpeed(context)

    suspend fun setLastPlaybackSpeed(context: Context, speed: Float) {
        PlayerSettingsStore.setLastPlaybackSpeed(context, speed)
    }

    fun getPreferredPlaybackSpeed(context: Context): Flow<Float> =
        PlayerSettingsStore.getPreferredPlaybackSpeed(context)

    fun getPreferredPlaybackSpeedSync(context: Context): Float {
        return PlayerSettingsStore.getPreferredPlaybackSpeedSync(context)
    }

    //  [新增] --- 主题色索引 (0-5, 默认 0 = BiliPink) ---
    fun getThemeColorIndex(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_THEME_COLOR_INDEX] ?: 0 }

    suspend fun setThemeColorIndex(context: Context, index: Int) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_THEME_COLOR_INDEX] = index.coerceIn(0, 9)
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

    //  [新增] --- 网格列数 ---
    fun getGridColumnCount(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_GRID_COLUMN_COUNT] ?: 0 }

    suspend fun setGridColumnCount(context: Context, count: Int) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_GRID_COLUMN_COUNT] = count
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

    fun getVideoTransitionRealtimeBlurEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED] ?: true }

    suspend fun setVideoTransitionRealtimeBlurEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED] = value
        }
    }

    fun getPredictiveBackAnimationEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_PREDICTIVE_BACK_ANIMATION_ENABLED] ?: true }

    suspend fun setPredictiveBackAnimationEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PREDICTIVE_BACK_ANIMATION_ENABLED] = value
        }
    }

    fun getSmartVisualGuardEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { false }

    suspend fun setSmartVisualGuardEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SMART_VISUAL_GUARD_ENABLED] = false
        }
    }

    //  [新增] --- 视频卡片统计信息贴封面 ---
    fun getCompactVideoStatsOnCover(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_COMPACT_VIDEO_STATS_ON_COVER] ?: true }

    suspend fun setCompactVideoStatsOnCover(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_COMPACT_VIDEO_STATS_ON_COVER] = value }
    }

    fun getHomeCoverGlassBadgesVisible(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HOME_COVER_GLASS_BADGES_VISIBLE] ?: true }

    suspend fun setHomeCoverGlassBadgesVisible(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_COVER_GLASS_BADGES_VISIBLE] = value
        }
    }

    fun getHomeInfoGlassBadgesVisible(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HOME_INFO_GLASS_BADGES_VISIBLE] ?: true }

    suspend fun setHomeInfoGlassBadgesVisible(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_INFO_GLASS_BADGES_VISIBLE] = value
        }
    }

    //  [新增] --- 应用图标 ---
    fun getAppIcon(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> normalizeAppIconKey(preferences[KEY_APP_ICON]) }

    suspend fun setAppIcon(context: Context, iconKey: String) {
        val normalizedKey = normalizeAppIconKey(iconKey)
        // 1. Write to DataStore (suspends until persisted)
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_APP_ICON] = normalizedKey
        }
        
        // 2. Write to SharedPreferences synchronously using commit()
        // This is critical because changing the app icon (activity-alias) often kills the process immediately.
        // apply() is asynchronous and might not finish before the process dies.
        val success = context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE)
            .edit().putString("current_icon", normalizedKey).commit()
            
        com.android.purebilibili.core.util.Logger.d("SettingsManager", "App icon saved: $iconKey -> $normalizedKey, persisted to prefs: $success")
    }
    
    //  [新增] --- 开屏壁纸 ---
    fun getSplashWallpaperUri(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SPLASH_WALLPAPER_URI] ?: "" }

    fun getSplashWallpaperHistory(context: Context): Flow<List<String>> = context.settingsDataStore.data
        .map { preferences -> decodeSplashWallpaperHistory(preferences[KEY_SPLASH_WALLPAPER_HISTORY] ?: "") }

    fun getSplashRandomPoolUris(context: Context): Flow<List<String>> = context.settingsDataStore.data
        .map { preferences -> decodeSplashWallpaperHistory(preferences[KEY_SPLASH_RANDOM_POOL_URIS] ?: "") }

    suspend fun setSplashWallpaperUri(context: Context, uri: String) {
        var encodedHistory = ""
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SPLASH_WALLPAPER_URI] = uri
            val existingHistory = decodeSplashWallpaperHistory(preferences[KEY_SPLASH_WALLPAPER_HISTORY] ?: "")
            val updatedHistory = appendSplashWallpaperHistory(existingHistory, uri)
            encodedHistory = encodeSplashWallpaperHistory(updatedHistory)
            preferences[KEY_SPLASH_WALLPAPER_HISTORY] = encodedHistory
        }
        // 同步到 SharedPreferences
        context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SPLASH_PREFS_KEY_WALLPAPER_URI, uri)
            .putString(SPLASH_PREFS_KEY_WALLPAPER_HISTORY, encodedHistory)
            .apply()
    }

    suspend fun setSplashWallpaperHistory(context: Context, history: List<String>) {
        val encoded = encodeSplashWallpaperHistory(history)
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SPLASH_WALLPAPER_HISTORY] = encoded
        }
        context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SPLASH_PREFS_KEY_WALLPAPER_HISTORY, encoded)
            .apply()
    }

    fun getSplashWallpaperUriSync(context: Context): String {
        return context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .getString(SPLASH_PREFS_KEY_WALLPAPER_URI, "") ?: ""
    }

    fun getSplashWallpaperHistorySync(context: Context): List<String> {
        val raw = context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .getString(SPLASH_PREFS_KEY_WALLPAPER_HISTORY, "")
            .orEmpty()
        return decodeSplashWallpaperHistory(raw)
    }

    suspend fun setSplashRandomPoolUris(context: Context, poolUris: List<String>) {
        val encoded = encodeSplashWallpaperHistory(poolUris)
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SPLASH_RANDOM_POOL_URIS] = encoded
        }
        context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SPLASH_PREFS_KEY_RANDOM_POOL_URIS, encoded)
            .apply()
    }

    fun getSplashRandomPoolUrisSync(context: Context): List<String> {
        val raw = context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .getString(SPLASH_PREFS_KEY_RANDOM_POOL_URIS, "")
            .orEmpty()
        return decodeSplashWallpaperHistory(raw)
    }

    fun isSplashEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SPLASH_ENABLED] ?: false } // 默认关闭

    fun getSplashRandomEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SPLASH_RANDOM_ENABLED] ?: false }

    fun getSplashIconAnimationEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SPLASH_ICON_ANIMATION_ENABLED] ?: true }

    suspend fun setSplashEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_SPLASH_ENABLED] = value 
        }
        // 同步到 SharedPreferences
        context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(SPLASH_PREFS_KEY_ENABLED, value).apply()
    }
    
    fun isSplashEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .getBoolean(SPLASH_PREFS_KEY_ENABLED, false)
    }

    suspend fun setSplashRandomEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SPLASH_RANDOM_ENABLED] = value
        }
        context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SPLASH_PREFS_KEY_RANDOM_ENABLED, value)
            .apply()
    }

    fun isSplashRandomEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .getBoolean(SPLASH_PREFS_KEY_RANDOM_ENABLED, false)
    }

    suspend fun setSplashIconAnimationEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SPLASH_ICON_ANIMATION_ENABLED] = value
        }
        context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SPLASH_PREFS_KEY_ICON_ANIMATION_ENABLED, value)
            .apply()
    }

    fun isSplashIconAnimationEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .getBoolean(SPLASH_PREFS_KEY_ICON_ANIMATION_ENABLED, true)
    }

    fun getSplashAlignment(context: Context, isTablet: Boolean): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            if (isTablet) {
                preferences[KEY_SPLASH_ALIGNMENT_TABLET] ?: 0f
            } else {
                preferences[KEY_SPLASH_ALIGNMENT_MOBILE] ?: 0f
            }
        }

    suspend fun setSplashAlignment(context: Context, isTablet: Boolean, bias: Float) {
        val coerced = bias.coerceIn(-1f, 1f)
        context.settingsDataStore.edit { preferences ->
            val key = if (isTablet) KEY_SPLASH_ALIGNMENT_TABLET else KEY_SPLASH_ALIGNMENT_MOBILE
            preferences[key] = coerced
        }
        val prefsKey = if (isTablet) SPLASH_PREFS_KEY_ALIGNMENT_TABLET else SPLASH_PREFS_KEY_ALIGNMENT_MOBILE
        context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(prefsKey, coerced)
            .apply()
    }

    fun getSplashAlignmentSync(context: Context, isTablet: Boolean): Float {
        val prefsKey = if (isTablet) SPLASH_PREFS_KEY_ALIGNMENT_TABLET else SPLASH_PREFS_KEY_ALIGNMENT_MOBILE
        return context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
            .getFloat(prefsKey, 0f)
    }


    
    //  同步读取当前图标设置（用于 Application 启动时同步）
    fun getAppIconSync(context: Context): String {
        return normalizeAppIconKey(
            context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE)
                .getString("current_icon", DEFAULT_APP_ICON_KEY)
        )
    }

    //  [新增] --- 底部栏样式 ---
    fun getBottomBarFloating(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_FLOATING] ?: true }

    suspend fun setBottomBarFloating(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BOTTOM_BAR_FLOATING] = value }
    }

    fun getSearchHotSectionEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SEARCH_HOT_SECTION_ENABLED] ?: true }

    suspend fun setSearchHotSectionEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_SEARCH_HOT_SECTION_ENABLED] = value }
    }
    
    //  [新增] --- 底栏显示模式 (0=图标+文字, 1=仅图标, 2=仅文字) ---
    fun getBottomBarLabelMode(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_LABEL_MODE] ?: 0 }  // 默认图标+文字

    suspend fun setBottomBarLabelMode(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BOTTOM_BAR_LABEL_MODE] = value }
    }

    //  [新增] --- 顶部标签显示模式 (0=图标+文字, 1=仅图标, 2=仅文字) ---
    fun getTopTabLabelMode(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_TOP_TAB_LABEL_MODE] ?: TopTabLabelMode.TEXT_ONLY }

    suspend fun setTopTabLabelMode(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_TOP_TAB_LABEL_MODE] = value }
    }

    //  [新增] --- 顶部标签顺序配置 ---
    fun getTopTabOrder(context: Context): Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val orderString = prefs[KEY_TOP_TAB_ORDER] ?: DEFAULT_TOP_TAB_ORDER
        orderString.split(",").filter { it.isNotBlank() }
    }

    suspend fun setTopTabOrder(context: Context, order: List<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_TOP_TAB_ORDER] = order.joinToString(",")
        }
    }

    //  [新增] --- 顶部标签可见项配置 ---
    fun getTopTabVisibleTabs(context: Context): Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        val tabsString = prefs[KEY_TOP_TAB_VISIBLE_TABS] ?: DEFAULT_TOP_TAB_VISIBLE
        tabsString.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setTopTabVisibleTabs(context: Context, tabs: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_TOP_TAB_VISIBLE_TABS] = tabs.joinToString(",")
        }
    }
    
    //  [新增] --- 搜索框模糊效果 ---
    fun getHeaderBlurEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            val mode = resolveHomeHeaderBlurModePreference(
                rawMode = preferences[KEY_HOME_HEADER_BLUR_MODE],
                legacyEnabled = preferences[KEY_HEADER_BLUR_ENABLED]
            )
            mode != HomeHeaderBlurMode.ALWAYS_OFF
        }

    suspend fun setHeaderBlurEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HEADER_BLUR_ENABLED] = value
            preferences[KEY_HOME_HEADER_BLUR_MODE] = if (value) {
                HomeHeaderBlurMode.FOLLOW_PRESET.value
            } else {
                HomeHeaderBlurMode.ALWAYS_OFF.value
            }
        }
    }

    fun getHomeHeaderBlurMode(context: Context): Flow<HomeHeaderBlurMode> = context.settingsDataStore.data
        .map { preferences ->
            resolveHomeHeaderBlurModePreference(
                rawMode = preferences[KEY_HOME_HEADER_BLUR_MODE],
                legacyEnabled = preferences[KEY_HEADER_BLUR_ENABLED]
            )
        }

    suspend fun setHomeHeaderBlurMode(context: Context, mode: HomeHeaderBlurMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_HEADER_BLUR_MODE] = mode.value
            preferences[KEY_HEADER_BLUR_ENABLED] = mode != HomeHeaderBlurMode.ALWAYS_OFF
        }
    }
    
    //  [新增] --- 首页顶部栏自动收缩 ---
    fun getHeaderCollapseEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HEADER_COLLAPSE_ENABLED] ?: true }

    suspend fun setHeaderCollapseEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_HEADER_COLLAPSE_ENABLED] = value }
    }
    
    //  [新增] --- 底栏模糊效果 ---
    fun getBottomBarBlurEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] ?: true }

    suspend fun setBottomBarBlurEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] = value }
    }
    
    //  [New] --- Liquid Glass Effect ---
    
    private val KEY_LIQUID_GLASS_STYLE = intPreferencesKey("liquid_glass_style")

    fun getLiquidGlassEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_LIQUID_GLASS_ENABLED] ?: true }

    suspend fun setLiquidGlassEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_LIQUID_GLASS_ENABLED] = value }
    }
    
    fun getLiquidGlassStyle(context: Context): Flow<LiquidGlassStyle> = context.settingsDataStore.data
        .map { preferences -> 
            LiquidGlassStyle.fromValue(preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.CLASSIC.value)
        }

    suspend fun setLiquidGlassStyle(context: Context, style: LiquidGlassStyle) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_LIQUID_GLASS_STYLE] = style.value }
    }

    fun getLiquidGlassMode(context: Context): Flow<LiquidGlassMode> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_LIQUID_GLASS_MODE]
                ?.let(LiquidGlassMode::fromValue)
                ?: resolveLegacyLiquidGlassMode(
                    LiquidGlassStyle.fromValue(
                        preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.CLASSIC.value
                    )
                )
        }

    suspend fun setLiquidGlassMode(context: Context, mode: LiquidGlassMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LIQUID_GLASS_MODE] = mode.value
            val strength = preferences[KEY_LIQUID_GLASS_STRENGTH] ?: resolveDefaultLiquidGlassStrength(mode)
            preferences[KEY_LIQUID_GLASS_PROGRESS] = resolveLegacyLiquidGlassProgress(mode, strength)
        }
    }

    fun getLiquidGlassStrength(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            val mode = preferences[KEY_LIQUID_GLASS_MODE]
                ?.let(LiquidGlassMode::fromValue)
                ?: resolveLegacyLiquidGlassMode(
                    LiquidGlassStyle.fromValue(
                        preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.CLASSIC.value
                    )
                )
            normalizeLiquidGlassStrength(
                preferences[KEY_LIQUID_GLASS_STRENGTH] ?: resolveDefaultLiquidGlassStrength(mode)
            )
        }

    suspend fun setLiquidGlassStrength(context: Context, strength: Float) {
        context.settingsDataStore.edit { preferences ->
            val normalizedStrength = normalizeLiquidGlassStrength(strength)
            preferences[KEY_LIQUID_GLASS_STRENGTH] = normalizedStrength
            val mode = preferences[KEY_LIQUID_GLASS_MODE]
                ?.let(LiquidGlassMode::fromValue)
                ?: resolveLegacyLiquidGlassMode(
                    LiquidGlassStyle.fromValue(
                        preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.CLASSIC.value
                    )
                )
            preferences[KEY_LIQUID_GLASS_PROGRESS] = resolveLegacyLiquidGlassProgress(mode, normalizedStrength)
        }
    }

    fun getLiquidGlassProgress(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeLiquidGlassProgress(
                preferences[KEY_LIQUID_GLASS_PROGRESS]
                    ?: resolveLegacyLiquidGlassProgress(
                        mode = preferences[KEY_LIQUID_GLASS_MODE]
                            ?.let(LiquidGlassMode::fromValue)
                            ?: resolveLegacyLiquidGlassMode(
                                LiquidGlassStyle.fromValue(
                                    preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.CLASSIC.value
                                )
                            ),
                        strength = preferences[KEY_LIQUID_GLASS_STRENGTH]
                            ?: resolveDefaultLiquidGlassStrength(
                                preferences[KEY_LIQUID_GLASS_MODE]
                                    ?.let(LiquidGlassMode::fromValue)
                                    ?: resolveLegacyLiquidGlassMode(
                                        LiquidGlassStyle.fromValue(
                                            preferences[KEY_LIQUID_GLASS_STYLE]
                                                ?: LiquidGlassStyle.CLASSIC.value
                                        )
                                    )
                            )
                    )
            )
        }

    suspend fun setLiquidGlassProgress(context: Context, progress: Float) {
        context.settingsDataStore.edit { preferences ->
            val normalizedProgress = normalizeLiquidGlassProgress(progress)
            val mode = resolveLiquidGlassModeFromProgress(normalizedProgress)
            preferences[KEY_LIQUID_GLASS_PROGRESS] = normalizedProgress
            preferences[KEY_LIQUID_GLASS_MODE] = mode.value
            preferences[KEY_LIQUID_GLASS_STRENGTH] = resolveLiquidGlassStrengthFromProgress(normalizedProgress)
            preferences[KEY_LIQUID_GLASS_STYLE] = resolveLegacyLiquidGlassStyleFromProgress(normalizedProgress).value
        }
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

    //  [新增] 获取底栏可见项目
    fun getVisibleBottomBarItems(context: Context): Flow<Set<String>> = context.settingsDataStore.data
        .map { preferences ->
            val itemsString = preferences[KEY_BOTTOM_BAR_VISIBLE_TABS]
            if (itemsString.isNullOrEmpty()) {
                // 默认可见项
                setOf("HOME", "DYNAMIC", "STORY", "HISTORY", "PROFILE") 
            } else {
                itemsString.split(",").toSet()
            }
        }

    private fun normalizeBottomBarColorItemId(rawId: String): String {
        val id = rawId.trim()
        if (id.isBlank()) return ""
        return when (id.lowercase()) {
            "home" -> "HOME"
            "dynamic" -> "DYNAMIC"
            "story", "shortvideo", "short_video" -> "STORY"
            "history" -> "HISTORY"
            "profile", "mine", "my" -> "PROFILE"
            "favorite", "favourite" -> "FAVORITE"
            "live" -> "LIVE"
            "watchlater", "watch_later" -> "WATCHLATER"
            "settings" -> "SETTINGS"
            else -> id.uppercase()
        }
    }

    private fun parseBottomBarItemColors(colorString: String): Map<String, Int> {
        if (colorString.isBlank()) return emptyMap()
        return colorString.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val itemId = normalizeBottomBarColorItemId(parts[0])
            if (itemId.isBlank()) return@mapNotNull null
            itemId to (parts[1].trim().toIntOrNull() ?: 0)
        }.toMap()
    }

    //  [新增] 获取底栏项目颜色配置
    fun getBottomBarItemColors(context: Context): Flow<Map<String, Int>> = context.settingsDataStore.data
        .map { preferences -> parseBottomBarItemColors(preferences[KEY_BOTTOM_BAR_ITEM_COLORS] ?: "") }

    suspend fun setBlurIntensity(context: Context, intensity: BlurIntensity) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_BLUR_INTENSITY] = intensity.name
        }
    }
    
    // ==========  弹幕设置 ==========
    
    private const val DANMAKU_DEFAULTS_VERSION = 4
    private const val HOME_VISUAL_DEFAULTS_VERSION = 1
    private const val DEFAULT_DANMAKU_OPACITY = DANMAKU_DEFAULT_OPACITY
    private const val DEFAULT_DANMAKU_FONT_SCALE = 1.0f
    private const val DEFAULT_DANMAKU_SPEED = 1.0f
    private const val DEFAULT_DANMAKU_AREA = 0.5f

    private fun buildScopedDanmakuKeyName(
        scope: DanmakuSettingsScope,
        suffix: String
    ): String = "danmaku_${scope.keyPrefix}_$suffix"
    
    private val KEY_DANMAKU_ENABLED = booleanPreferencesKey("danmaku_enabled")
    private val KEY_DANMAKU_OPACITY = floatPreferencesKey("danmaku_opacity")
    private val KEY_DANMAKU_FONT_SCALE = floatPreferencesKey("danmaku_font_scale")
    private val KEY_DANMAKU_SPEED = floatPreferencesKey("danmaku_speed")
    private val KEY_DANMAKU_AREA = floatPreferencesKey("danmaku_area")
    private val KEY_DANMAKU_ALLOW_SCROLL = booleanPreferencesKey("danmaku_allow_scroll")
    private val KEY_DANMAKU_ALLOW_TOP = booleanPreferencesKey("danmaku_allow_top")
    private val KEY_DANMAKU_ALLOW_BOTTOM = booleanPreferencesKey("danmaku_allow_bottom")
    private val KEY_DANMAKU_ALLOW_COLORFUL = booleanPreferencesKey("danmaku_allow_colorful")
    private val KEY_DANMAKU_ALLOW_SPECIAL = booleanPreferencesKey("danmaku_allow_special")
    private val KEY_DANMAKU_SMART_OCCLUSION = booleanPreferencesKey("danmaku_smart_occlusion")
    private val KEY_DANMAKU_FULLSCREEN_PANEL_WIDTH_MODE =
        intPreferencesKey("danmaku_fullscreen_panel_width_mode")
    private val KEY_DANMAKU_BLOCK_RULES = stringPreferencesKey("danmaku_block_rules")
    private val KEY_DANMAKU_MERGE_DUPLICATES = booleanPreferencesKey("danmaku_merge_duplicates")
    private val KEY_DANMAKU_DEFAULTS_VERSION = intPreferencesKey("danmaku_defaults_version")
    private val KEY_HOME_VISUAL_DEFAULTS_VERSION = intPreferencesKey("home_visual_defaults_version")

    private fun keyDanmakuEnabled(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "enabled"))
    private fun keyDanmakuOpacity(scope: DanmakuSettingsScope) =
        floatPreferencesKey(buildScopedDanmakuKeyName(scope, "opacity"))
    private fun keyDanmakuFontScale(scope: DanmakuSettingsScope) =
        floatPreferencesKey(buildScopedDanmakuKeyName(scope, "font_scale"))
    private fun keyDanmakuSpeed(scope: DanmakuSettingsScope) =
        floatPreferencesKey(buildScopedDanmakuKeyName(scope, "speed"))
    private fun keyDanmakuArea(scope: DanmakuSettingsScope) =
        floatPreferencesKey(buildScopedDanmakuKeyName(scope, "area"))
    private fun keyDanmakuAllowScroll(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "allow_scroll"))
    private fun keyDanmakuAllowTop(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "allow_top"))
    private fun keyDanmakuAllowBottom(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "allow_bottom"))
    private fun keyDanmakuAllowColorful(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "allow_colorful"))
    private fun keyDanmakuAllowSpecial(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "allow_special"))
    private fun keyDanmakuSmartOcclusion(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "smart_occlusion"))
    private fun keyDanmakuBlockRules(scope: DanmakuSettingsScope) =
        stringPreferencesKey(buildScopedDanmakuKeyName(scope, "block_rules"))
    private fun keyDanmakuMergeDuplicates(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "merge_duplicates"))

    private fun <T> readScopedDanmakuPreference(
        preferences: Preferences,
        scopeKey: Preferences.Key<T>,
        legacyKey: Preferences.Key<T>,
        defaultValue: T
    ): T {
        return preferences[scopeKey] ?: preferences[legacyKey] ?: defaultValue
    }

    internal fun mapDanmakuSettingsFromPreferences(
        preferences: Preferences,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): DanmakuSettings {
        val blockRulesRaw = readScopedDanmakuPreference(
            preferences = preferences,
            scopeKey = keyDanmakuBlockRules(scope),
            legacyKey = KEY_DANMAKU_BLOCK_RULES,
            defaultValue = ""
        )
        return DanmakuSettings(
            enabled = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuEnabled(scope),
                legacyKey = KEY_DANMAKU_ENABLED,
                defaultValue = true
            ),
            opacity = normalizeDanmakuOpacity(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuOpacity(scope),
                    legacyKey = KEY_DANMAKU_OPACITY,
                    defaultValue = DEFAULT_DANMAKU_OPACITY
                )
            ),
            fontScale = normalizeDanmakuFontScale(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuFontScale(scope),
                    legacyKey = KEY_DANMAKU_FONT_SCALE,
                    defaultValue = DEFAULT_DANMAKU_FONT_SCALE
                )
            ),
            speed = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuSpeed(scope),
                legacyKey = KEY_DANMAKU_SPEED,
                defaultValue = DEFAULT_DANMAKU_SPEED
            ),
            displayArea = normalizeDanmakuDisplayArea(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuArea(scope),
                    legacyKey = KEY_DANMAKU_AREA,
                    defaultValue = DEFAULT_DANMAKU_AREA
                )
            ),
            mergeDuplicates = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuMergeDuplicates(scope),
                legacyKey = KEY_DANMAKU_MERGE_DUPLICATES,
                defaultValue = true
            ),
            allowScroll = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowScroll(scope),
                legacyKey = KEY_DANMAKU_ALLOW_SCROLL,
                defaultValue = true
            ),
            allowTop = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowTop(scope),
                legacyKey = KEY_DANMAKU_ALLOW_TOP,
                defaultValue = true
            ),
            allowBottom = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowBottom(scope),
                legacyKey = KEY_DANMAKU_ALLOW_BOTTOM,
                defaultValue = true
            ),
            allowColorful = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowColorful(scope),
                legacyKey = KEY_DANMAKU_ALLOW_COLORFUL,
                defaultValue = true
            ),
            allowSpecial = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowSpecial(scope),
                legacyKey = KEY_DANMAKU_ALLOW_SPECIAL,
                defaultValue = true
            ),
            smartOcclusion = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuSmartOcclusion(scope),
                legacyKey = KEY_DANMAKU_SMART_OCCLUSION,
                defaultValue = false
            ),
            fullscreenPanelWidthMode = normalizeDanmakuFullscreenPanelWidthMode(
                DanmakuPanelWidthMode.fromValue(
                    preferences[KEY_DANMAKU_FULLSCREEN_PANEL_WIDTH_MODE]
                        ?: DanmakuPanelWidthMode.THIRD.value
                )
            ),
            blockRulesRaw = blockRulesRaw,
            blockRules = parseDanmakuBlockRules(blockRulesRaw)
        )
    }

    fun getDanmakuSettings(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<DanmakuSettings> {
        return context.settingsDataStore.data
            .map { preferences -> mapDanmakuSettingsFromPreferences(preferences, scope) }
            .distinctUntilChanged()
    }
    
    // --- 弹幕开关 ---
    fun getDanmakuEnabled(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuEnabled(scope),
                legacyKey = KEY_DANMAKU_ENABLED,
                defaultValue = true
            )
        }

    suspend fun setDanmakuEnabled(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuEnabled(scope)] = value
        }
    }
    
    // --- 弹幕透明度 (0.0 ~ 1.0, 默认 0.85) ---
    fun getDanmakuOpacity(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuOpacity(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuOpacity(scope),
                    legacyKey = KEY_DANMAKU_OPACITY,
                    defaultValue = DEFAULT_DANMAKU_OPACITY
                )
            )
        }

    suspend fun setDanmakuOpacity(
        context: Context,
        value: Float,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuOpacity(scope)] = normalizeDanmakuOpacity(value)
        }
    }
    
    // --- 弹幕字体大小 (0.3 ~ 2.0, 默认 1.0) ---
    fun getDanmakuFontScale(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuFontScale(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuFontScale(scope),
                    legacyKey = KEY_DANMAKU_FONT_SCALE,
                    defaultValue = DEFAULT_DANMAKU_FONT_SCALE
                )
            )
        }

    suspend fun setDanmakuFontScale(
        context: Context,
        value: Float,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuFontScale(scope)] = normalizeDanmakuFontScale(value)
        }
    }
    
    // --- 弹幕速度 (0.5 ~ 3.0, 默认 1.0 适中) ---
    fun getDanmakuSpeed(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuSpeed(scope),
                legacyKey = KEY_DANMAKU_SPEED,
                defaultValue = DEFAULT_DANMAKU_SPEED
            )
        }

    suspend fun setDanmakuSpeed(
        context: Context,
        value: Float,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuSpeed(scope)] = value.coerceIn(0.5f, 3.0f)
        }
    }
    
    // --- 弹幕显示区域 (0.25, 0.5, 0.75, 1.0, 默认 0.5) ---
    fun getDanmakuArea(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuDisplayArea(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuArea(scope),
                    legacyKey = KEY_DANMAKU_AREA,
                    defaultValue = DEFAULT_DANMAKU_AREA
                )
            )
        }

    suspend fun setDanmakuArea(
        context: Context,
        value: Float,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuArea(scope)] = normalizeDanmakuDisplayArea(value)
        }
    }

    // --- 弹幕类型过滤 (true=显示/不屏蔽) ---
    fun getDanmakuAllowScroll(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowScroll(scope),
                legacyKey = KEY_DANMAKU_ALLOW_SCROLL,
                defaultValue = true
            )
        }

    suspend fun setDanmakuAllowScroll(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuAllowScroll(scope)] = value
        }
    }

    fun getDanmakuAllowTop(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowTop(scope),
                legacyKey = KEY_DANMAKU_ALLOW_TOP,
                defaultValue = true
            )
        }

    suspend fun setDanmakuAllowTop(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuAllowTop(scope)] = value
        }
    }

    fun getDanmakuAllowBottom(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowBottom(scope),
                legacyKey = KEY_DANMAKU_ALLOW_BOTTOM,
                defaultValue = true
            )
        }

    suspend fun setDanmakuAllowBottom(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuAllowBottom(scope)] = value
        }
    }

    fun getDanmakuAllowColorful(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowColorful(scope),
                legacyKey = KEY_DANMAKU_ALLOW_COLORFUL,
                defaultValue = true
            )
        }

    suspend fun setDanmakuAllowColorful(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuAllowColorful(scope)] = value
        }
    }

    fun getDanmakuAllowSpecial(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuAllowSpecial(scope),
                legacyKey = KEY_DANMAKU_ALLOW_SPECIAL,
                defaultValue = true
            )
        }

    suspend fun setDanmakuAllowSpecial(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuAllowSpecial(scope)] = value
        }
    }

    fun getDanmakuSmartOcclusion(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuSmartOcclusion(scope),
                legacyKey = KEY_DANMAKU_SMART_OCCLUSION,
                defaultValue = false
            )
        }

    suspend fun setDanmakuSmartOcclusion(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuSmartOcclusion(scope)] = value
        }
    }

    fun getDanmakuFullscreenPanelWidthMode(context: Context): Flow<DanmakuPanelWidthMode> =
        context.settingsDataStore.data.map { preferences ->
            normalizeDanmakuFullscreenPanelWidthMode(
                DanmakuPanelWidthMode.fromValue(
                    preferences[KEY_DANMAKU_FULLSCREEN_PANEL_WIDTH_MODE]
                        ?: DanmakuPanelWidthMode.THIRD.value
                )
            )
        }

    suspend fun setDanmakuFullscreenPanelWidthMode(
        context: Context,
        value: DanmakuPanelWidthMode
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DANMAKU_FULLSCREEN_PANEL_WIDTH_MODE] =
                normalizeDanmakuFullscreenPanelWidthMode(value).value
        }
    }

    fun getDanmakuBlockRulesRaw(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuBlockRules(scope),
                legacyKey = KEY_DANMAKU_BLOCK_RULES,
                defaultValue = ""
            )
        }

    fun getDanmakuBlockRules(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<List<String>> = context.settingsDataStore.data
        .map { preferences ->
            parseDanmakuBlockRules(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuBlockRules(scope),
                    legacyKey = KEY_DANMAKU_BLOCK_RULES,
                    defaultValue = ""
                )
            )
        }

    suspend fun setDanmakuBlockRulesRaw(
        context: Context,
        value: String,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        val normalized = parseDanmakuBlockRules(value).joinToString(separator = "\n")
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuBlockRules(scope)] = normalized
        }
    }
    
    // --- 弹幕合并重复 (默认开启) ---
    fun getDanmakuMergeDuplicates(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuMergeDuplicates(scope),
                legacyKey = KEY_DANMAKU_MERGE_DUPLICATES,
                defaultValue = true
            )
        }
        
    suspend fun setDanmakuMergeDuplicates(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences -> 
            preferences[keyDanmakuMergeDuplicates(scope)] = value
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
                preferences[KEY_DANMAKU_ALLOW_SCROLL] = true
                preferences[KEY_DANMAKU_ALLOW_TOP] = true
                preferences[KEY_DANMAKU_ALLOW_BOTTOM] = true
                preferences[KEY_DANMAKU_ALLOW_COLORFUL] = true
                preferences[KEY_DANMAKU_ALLOW_SPECIAL] = true
                preferences[KEY_DANMAKU_SMART_OCCLUSION] = false
                preferences[KEY_DANMAKU_DEFAULTS_VERSION] = DANMAKU_DEFAULTS_VERSION
            }
        }
    }

    /**
     * 启动时一次性迁移首页视觉默认值（仅在版本未迁移时覆盖）。
     * 目标：默认开启底栏悬浮、液态玻璃、顶部模糊。
     */
    suspend fun ensureHomeVisualDefaults(context: Context) {
        context.settingsDataStore.edit { preferences ->
            val currentVersion = preferences[KEY_HOME_VISUAL_DEFAULTS_VERSION] ?: 0
            if (currentVersion < HOME_VISUAL_DEFAULTS_VERSION) {
                preferences[KEY_BOTTOM_BAR_FLOATING] = true
                preferences[KEY_LIQUID_GLASS_ENABLED] = true
                preferences[KEY_HEADER_BLUR_ENABLED] = true
                preferences[KEY_HOME_VISUAL_DEFAULTS_VERSION] = HOME_VISUAL_DEFAULTS_VERSION
            }
        }
    }

    // ==========  推荐流 API 类型 ==========
    
    private val KEY_FEED_API_TYPE = intPreferencesKey("feed_api_type")
    private val KEY_INCREMENTAL_TIMELINE_REFRESH = booleanPreferencesKey("incremental_timeline_refresh")
    private val KEY_HOME_REFRESH_COUNT = intPreferencesKey("home_refresh_count")
    
    /**
     *  推荐流 API 类型
     * - WEB: 平板端/Web API (x/web-interface/wbi/index/top/feed/rcmd)，使用 WBI 签名
     * - MOBILE: 移动端 API (x/v2/feed/index)，使用 appkey+sign 签名，需要 access_token
     */
    enum class FeedApiType(val value: Int, val label: String, val description: String) {
        WEB(0, "网页端 (Web)", "使用 Web 推荐算法"),
        MOBILE(1, "移动端 (App)", "使用手机端推荐算法，需登录");
        
        companion object {
            fun fromValue(value: Int): FeedApiType = entries.find { it.value == value } ?: WEB
        }
    }
    
    // --- 推荐流类型设置 ---
    fun getFeedApiType(context: Context): Flow<FeedApiType> = context.settingsDataStore.data
        .map { preferences -> 
            FeedApiType.fromValue(preferences[KEY_FEED_API_TYPE] ?: FeedApiType.WEB.value)
        }

    suspend fun setFeedApiType(context: Context, type: FeedApiType) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_FEED_API_TYPE] = type.value 
        }
        //  同步到 SharedPreferences，供 VideoRepository 同步读取
        context.getSharedPreferences("feed_api", Context.MODE_PRIVATE)
            .edit().putInt("type", type.value).apply()
    }
    
    //  同步读取推荐流类型（用于 VideoRepository）
    fun getFeedApiTypeSync(context: Context): FeedApiType {
        val value = context.getSharedPreferences("feed_api", Context.MODE_PRIVATE)
            .getInt("type", FeedApiType.WEB.value)
        return FeedApiType.fromValue(value)
    }

    // --- 时间线增量刷新开关 ---
    fun getIncrementalTimelineRefresh(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_INCREMENTAL_TIMELINE_REFRESH] ?: false }

    suspend fun setIncrementalTimelineRefresh(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_INCREMENTAL_TIMELINE_REFRESH] = value
        }
    }

    fun getHomeRefreshCount(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeHomeRefreshCount(preferences[KEY_HOME_REFRESH_COUNT] ?: DEFAULT_HOME_REFRESH_COUNT)
        }

    suspend fun setHomeRefreshCount(context: Context, count: Int) {
        val normalized = normalizeHomeRefreshCount(count)
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_REFRESH_COUNT] = normalized
        }
        context.getSharedPreferences("feed_api", Context.MODE_PRIVATE)
            .edit()
            .putInt("home_refresh_count", normalized)
            .apply()
    }

    fun getHomeRefreshCountSync(context: Context): Int {
        val value = context.getSharedPreferences("feed_api", Context.MODE_PRIVATE)
            .getInt("home_refresh_count", DEFAULT_HOME_REFRESH_COUNT)
        return normalizeHomeRefreshCount(value)
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
    // [New] Easter Egg: Enable Auto Jump after Triple Action
    private val KEY_TRIPLE_JUMP_ENABLED = booleanPreferencesKey("triple_jump_enabled")

    fun getTripleJumpEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_TRIPLE_JUMP_ENABLED] ?: false }

    suspend fun setTripleJumpEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_TRIPLE_JUMP_ENABLED] = value }
    }

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
    
    // ========== 🔄 自动旋转设置 ==========
    
    private val KEY_AUTO_ROTATE_ENABLED = booleanPreferencesKey("auto_rotate_enabled")
    
    // --- 自动横竖屏切换 (跟随手机传感器方向，默认关闭) ---
    fun getAutoRotateEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_ROTATE_ENABLED] ?: false }
    
    suspend fun setAutoRotateEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_ROTATE_ENABLED] = value }
        // 同步到 SharedPreferences，供同步读取
        context.getSharedPreferences("auto_rotate_cache", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply()
    }
    
    fun isAutoRotateEnabledSync(context: Context): Boolean {
        val prefs = context.getSharedPreferences("auto_rotate_cache", Context.MODE_PRIVATE)
        return prefs.getBoolean("enabled", false)
    }
    
    // ========== 🌐 网络感知画质设置 ==========
    
    private val KEY_WIFI_QUALITY = intPreferencesKey("wifi_default_quality")

    private val KEY_MOBILE_QUALITY = intPreferencesKey("mobile_default_quality")
    //  [New] Video Codec & Audio Quality
    private val KEY_VIDEO_CODEC = stringPreferencesKey("video_codec_preference")
    private val KEY_VIDEO_SECOND_CODEC = stringPreferencesKey("video_second_codec_preference")
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
    
    // --- 🚀 自动最高画质 (开启后忽略上方设置，始终选择最高可用画质) ---
    private val KEY_AUTO_HIGHEST_QUALITY = booleanPreferencesKey("auto_highest_quality")
    private val KEY_BILI_DIRECTED_TRAFFIC = booleanPreferencesKey("bili_directed_traffic")
    
    fun getAutoHighestQuality(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_HIGHEST_QUALITY] ?: false }  // 默认关闭
    
    suspend fun setAutoHighestQuality(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_HIGHEST_QUALITY] = value }
        //  同步到 SharedPreferences，供 NetworkUtils 同步读取
        context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("auto_highest_quality", value).commit()
        com.android.purebilibili.core.util.Logger.d("SettingsManager", "🚀 自动最高画质: $value")
    }
    
    fun getAutoHighestQualitySync(context: Context): Boolean {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getBoolean("auto_highest_quality", false)
    }

    // --- B站定向流量支持（实验性）---
    fun getBiliDirectedTrafficEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BILI_DIRECTED_TRAFFIC] ?: false }

    suspend fun setBiliDirectedTrafficEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BILI_DIRECTED_TRAFFIC] = value
        }
        context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("bili_directed_traffic", value)
            .commit()
        com.android.purebilibili.core.util.Logger.d("SettingsManager", "📶 B站定向流量支持: $value")
    }

    fun getBiliDirectedTrafficEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getBoolean("bili_directed_traffic", false)
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

    // --- Secondary Video Codec Preference (Default: AVC/avc1) ---
    fun getVideoSecondCodec(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_VIDEO_SECOND_CODEC] ?: "avc1" }

    suspend fun setVideoSecondCodec(context: Context, value: String) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_VIDEO_SECOND_CODEC] = value }
        context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .edit().putString("video_second_codec", value).apply()
    }

    fun getVideoSecondCodecSync(context: Context): String {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getString("video_second_codec", "avc1") ?: "avc1"
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

    // --- 评论默认排序 (1=回复,2=最新,3=最热,4=点赞) ---
    fun getCommentDefaultSortMode(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            val value = preferences[KEY_COMMENT_DEFAULT_SORT_MODE] ?: 3
            if (value in 1..4) value else 3
        }

    suspend fun setCommentDefaultSortMode(context: Context, value: Int) {
        val normalized = if (value in 1..4) value else 3
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_COMMENT_DEFAULT_SORT_MODE] = normalized
        }
        context.getSharedPreferences("comment_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("default_sort_mode", normalized)
            .apply()
    }

    fun getCommentDefaultSortModeSync(context: Context): Int {
        val value = context.getSharedPreferences("comment_settings", Context.MODE_PRIVATE)
            .getInt("default_sort_mode", 3)
        return if (value in 1..4) value else 3
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
        .map { preferences -> preferences[KEY_CRASH_TRACKING_ENABLED] ?: DEFAULT_CRASH_TRACKING_ENABLED }

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
    private val KEY_AUTO_CHECK_APP_UPDATE = booleanPreferencesKey("auto_check_app_update")
    
    // --- Analytics 开关 (与崩溃追踪共享设置) ---
    fun getAnalyticsEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_ANALYTICS_ENABLED] ?: DEFAULT_ANALYTICS_ENABLED }

    suspend fun setAnalyticsEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_ANALYTICS_ENABLED] = value }
        //  同步到 SharedPreferences，供 Application 同步读取
        context.getSharedPreferences("analytics_tracking", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply()
    }

    // ==========  应用更新 ==========
    fun getAutoCheckAppUpdate(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_CHECK_APP_UPDATE] ?: true } // 默认开启

    suspend fun setAutoCheckAppUpdate(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_CHECK_APP_UPDATE] = value }
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

    /**
     * 离开播放页后停止播放（优先级高于后台播放模式）
     * - true: 离开播放页立即停止，不进入小窗/画中画/后台播放
     * - false: 按后台播放模式执行（默认）
     */
    fun getStopPlaybackOnExit(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_STOP_PLAYBACK_ON_EXIT] ?: false }

    suspend fun setStopPlaybackOnExit(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_STOP_PLAYBACK_ON_EXIT] = value
        }
        // 同步到 SharedPreferences，供 MiniPlayerManager 同步读取
        context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .edit().putBoolean("stop_playback_on_exit", value).apply()
    }

    fun getStopPlaybackOnExitSync(context: Context): Boolean {
        return context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .getBoolean("stop_playback_on_exit", false)
    }

    fun getBackgroundPlaybackEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BACKGROUND_PLAYBACK_ENABLED] ?: true }

    suspend fun setBackgroundPlaybackEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BACKGROUND_PLAYBACK_ENABLED] = value
        }
        context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .edit().putBoolean("background_playback_enabled", value).apply()
    }

    fun getBackgroundPlaybackEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .getBoolean("background_playback_enabled", true)
    }

    fun getAudioFocusEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUDIO_FOCUS_ENABLED] ?: true }

    suspend fun setAudioFocusEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUDIO_FOCUS_ENABLED] = value
        }
        context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .edit().putBoolean("audio_focus_enabled", value).apply()
    }

    fun getAudioFocusEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .getBoolean("audio_focus_enabled", true)
    }

    fun getAudioModeAutoPipEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUDIO_MODE_AUTO_PIP_ENABLED] ?: false }

    suspend fun setAudioModeAutoPipEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUDIO_MODE_AUTO_PIP_ENABLED] = value
        }
        context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .edit().putBoolean("audio_mode_auto_pip_enabled", value).apply()
    }

    fun getAudioModeAutoPipEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .getBoolean("audio_mode_auto_pip_enabled", false)
    }

    internal fun shouldEnableAudioModeAutoPipToggle(mode: MiniPlayerMode): Boolean {
        return mode == MiniPlayerMode.SYSTEM_PIP
    }

    fun getVideoAiSummaryEntryEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_VIDEO_AI_SUMMARY_ENTRY_ENABLED] ?: true }

    suspend fun setVideoAiSummaryEntryEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_AI_SUMMARY_ENTRY_ENABLED] = enabled
        }
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
    private val KEY_DOWNLOAD_EXPORT_TREE_URI = stringPreferencesKey("download_export_tree_uri")
    
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
        // [修复] 同步写入 SharedPreferences，供 DownloadManager 初始化时同步读取
        context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
            .edit().putString("path", path).commit() // commit 确保立即写入
    }

    fun getDownloadExportTreeUri(context: Context): Flow<String?> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_DOWNLOAD_EXPORT_TREE_URI]
        }

    suspend fun setDownloadExportTreeUri(context: Context, uri: String?) {
        context.settingsDataStore.edit { preferences ->
            if (uri != null) {
                preferences[KEY_DOWNLOAD_EXPORT_TREE_URI] = uri
            } else {
                preferences.remove(KEY_DOWNLOAD_EXPORT_TREE_URI)
            }
        }
        context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
            .edit().putString("tree_uri", uri).commit()
    }
    
    /**
     * [修复] 同步获取自定义下载路径
     * 用于解决 DownloadManager 初始化竞态条件
     */
    fun getDownloadPathSync(context: Context): String? {
        return context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
            .getString("path", null)
    }

    fun getDownloadExportTreeUriSync(context: Context): String? {
        return context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
            .getString("tree_uri", null)
    }
    
    /**
     *  获取默认下载路径描述
     */
    fun getDefaultDownloadPath(context: Context): String {
        val baseDir = runCatching { context.getExternalFilesDir(null) }
            .getOrNull()
            ?: context.filesDir
        return File(baseDir, "downloads").absolutePath
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
        val orderString = prefs[KEY_BOTTOM_BAR_ORDER] ?: DEFAULT_BOTTOM_BAR_ORDER
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
        val tabsString = prefs[KEY_BOTTOM_BAR_VISIBLE_TABS] ?: DEFAULT_BOTTOM_BAR_VISIBLE_TABS
        tabsString.split(",").filter { it.isNotBlank() }.toSet()
    }
    
    suspend fun setBottomBarVisibleTabs(context: Context, tabs: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_BOTTOM_BAR_VISIBLE_TABS] = tabs.joinToString(",")
        }
    }
    
    //  [新增] 获取有序的可见底栏项目列表
    fun getOrderedVisibleTabs(context: Context): Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val orderString = prefs[KEY_BOTTOM_BAR_ORDER] ?: DEFAULT_BOTTOM_BAR_ORDER
        val tabsString = prefs[KEY_BOTTOM_BAR_VISIBLE_TABS] ?: DEFAULT_BOTTOM_BAR_VISIBLE_TABS
        val order = orderString.split(",").filter { it.isNotBlank() }
        val visibleSet = tabsString.split(",").filter { it.isNotBlank() }.toSet()
        order.filter { it in visibleSet }
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
            val normalizedItemId = normalizeBottomBarColorItemId(itemId)
            if (normalizedItemId.isBlank()) return@edit
            colorMap[normalizedItemId] = colorIndex
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
    private val KEY_PORTRAIT_SWIPE_TO_FULLSCREEN = booleanPreferencesKey("portrait_swipe_to_fullscreen")
    private val KEY_CENTER_SWIPE_TO_FULLSCREEN = booleanPreferencesKey("center_swipe_to_fullscreen")
    private val KEY_FULLSCREEN_SWIPE_SEEK_ENABLED = booleanPreferencesKey("fullscreen_swipe_seek_enabled")
    private val KEY_FULLSCREEN_SWIPE_SEEK_SECONDS = intPreferencesKey("fullscreen_swipe_seek_seconds")
    private val KEY_FULLSCREEN_GESTURE_REVERSE = booleanPreferencesKey("fullscreen_gesture_reverse")
    private val KEY_AUTO_ENTER_FULLSCREEN = booleanPreferencesKey("auto_enter_fullscreen")
    private val KEY_AUTO_EXIT_FULLSCREEN = booleanPreferencesKey("auto_exit_fullscreen")
    private val KEY_SHOW_FULLSCREEN_LOCK_BUTTON = booleanPreferencesKey("show_fullscreen_lock_button")
    private val KEY_SHOW_FULLSCREEN_SCREENSHOT_BUTTON = booleanPreferencesKey("show_fullscreen_screenshot_button")
    private val KEY_SHOW_FULLSCREEN_BATTERY_LEVEL = booleanPreferencesKey("show_fullscreen_battery_level")
    private val KEY_SHOW_FULLSCREEN_TIME = booleanPreferencesKey("show_fullscreen_time")
    private val KEY_SHOW_FULLSCREEN_ACTION_ITEMS = booleanPreferencesKey("show_fullscreen_action_items")
    private val KEY_SHOW_ONLINE_COUNT = booleanPreferencesKey("show_online_count")
    private val KEY_PLAYER_DIAGNOSTIC_LOGGING_ENABLED =
        booleanPreferencesKey("player_diagnostic_logging_enabled")
    private val KEY_SUBTITLE_AUTO_PREFERENCE = intPreferencesKey("subtitle_auto_preference")
    private val KEY_BOTTOM_PROGRESS_BEHAVIOR = intPreferencesKey("bottom_progress_behavior")
    private val KEY_HORIZONTAL_ADAPTATION = booleanPreferencesKey("horizontal_adaptation_enabled")
    private val KEY_FULLSCREEN_MODE = intPreferencesKey("fullscreen_mode")
    private val KEY_FULLSCREEN_ASPECT_RATIO = intPreferencesKey("fullscreen_aspect_ratio")
    private val FULLSCREEN_SWIPE_SEEK_OPTIONS = listOf(10, 15, 20, 30)
    
    // --- 上滑隐藏播放器开关 ---
    fun getSwipeHidePlayerEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SWIPE_HIDE_PLAYER] ?: false }  // 默认关闭

    suspend fun setSwipeHidePlayerEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_SWIPE_HIDE_PLAYER] = value }
    }

    // --- 竖屏上滑进入全屏（默认开启） ---
    fun getPortraitSwipeToFullscreenEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_PORTRAIT_SWIPE_TO_FULLSCREEN] ?: true }

    suspend fun setPortraitSwipeToFullscreenEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_PORTRAIT_SWIPE_TO_FULLSCREEN] = value }
    }

    // --- 中部滑动切换全屏（默认开启） ---
    fun getCenterSwipeToFullscreenEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_CENTER_SWIPE_TO_FULLSCREEN] ?: true }

    suspend fun setCenterSwipeToFullscreenEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_CENTER_SWIPE_TO_FULLSCREEN] = value }
    }

    // --- 横屏左右滑动固定步长快进/快退开关（默认开启） ---
    fun getFullscreenSwipeSeekEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_FULLSCREEN_SWIPE_SEEK_ENABLED] ?: true }

    suspend fun setFullscreenSwipeSeekEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_FULLSCREEN_SWIPE_SEEK_ENABLED] = enabled
        }
    }

    // --- 横屏左右滑动快进/快退步长（秒，默认 15） ---
    fun getFullscreenSwipeSeekSeconds(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            val raw = preferences[KEY_FULLSCREEN_SWIPE_SEEK_SECONDS] ?: 15
            normalizeFullscreenSwipeSeekSeconds(raw)
        }

    suspend fun setFullscreenSwipeSeekSeconds(context: Context, seconds: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_FULLSCREEN_SWIPE_SEEK_SECONDS] = normalizeFullscreenSwipeSeekSeconds(seconds)
        }
    }

    private fun normalizeFullscreenSwipeSeekSeconds(seconds: Int): Int {
        return FULLSCREEN_SWIPE_SEEK_OPTIONS.minByOrNull { option -> abs(option - seconds) } ?: 15
    }

    private fun isTabletConfiguration(context: Context): Boolean {
        return context.resources.configuration.smallestScreenWidthDp >= 600
    }

    fun getFullscreenGestureReverse(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_FULLSCREEN_GESTURE_REVERSE] ?: false }

    suspend fun setFullscreenGestureReverse(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_FULLSCREEN_GESTURE_REVERSE] = enabled
        }
    }

    fun getAutoEnterFullscreen(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_ENTER_FULLSCREEN] ?: false }

    suspend fun setAutoEnterFullscreen(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUTO_ENTER_FULLSCREEN] = enabled
        }
    }

    fun getAutoExitFullscreen(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_EXIT_FULLSCREEN] ?: true }

    suspend fun setAutoExitFullscreen(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUTO_EXIT_FULLSCREEN] = enabled
        }
    }

    fun getShowFullscreenLockButton(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SHOW_FULLSCREEN_LOCK_BUTTON] ?: true }

    suspend fun setShowFullscreenLockButton(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_FULLSCREEN_LOCK_BUTTON] = enabled
        }
    }

    fun getShowFullscreenScreenshotButton(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SHOW_FULLSCREEN_SCREENSHOT_BUTTON] ?: true }

    suspend fun setShowFullscreenScreenshotButton(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_FULLSCREEN_SCREENSHOT_BUTTON] = enabled
        }
    }

    fun getShowFullscreenBatteryLevel(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SHOW_FULLSCREEN_BATTERY_LEVEL] ?: true }

    suspend fun setShowFullscreenBatteryLevel(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_FULLSCREEN_BATTERY_LEVEL] = enabled
        }
    }

    fun getShowFullscreenTime(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SHOW_FULLSCREEN_TIME] ?: true }

    suspend fun setShowFullscreenTime(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_FULLSCREEN_TIME] = enabled
        }
    }

    fun getShowFullscreenActionItems(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SHOW_FULLSCREEN_ACTION_ITEMS] ?: true }

    suspend fun setShowFullscreenActionItems(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_FULLSCREEN_ACTION_ITEMS] = enabled
        }
    }

    fun getShowOnlineCount(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SHOW_ONLINE_COUNT] ?: false }

    suspend fun setShowOnlineCount(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_ONLINE_COUNT] = enabled
        }
        context.getSharedPreferences("video_overlay_cache", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("show_online_count", enabled)
            .apply()
    }

    fun getShowOnlineCountSync(context: Context): Boolean {
        return context.getSharedPreferences("video_overlay_cache", Context.MODE_PRIVATE)
            .getBoolean("show_online_count", false)
    }

    fun getPlayerDiagnosticLoggingEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_PLAYER_DIAGNOSTIC_LOGGING_ENABLED]
                ?: DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED
        }

    suspend fun setPlayerDiagnosticLoggingEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PLAYER_DIAGNOSTIC_LOGGING_ENABLED] = enabled
        }
        PlayerSettingsCache.setPlayerDiagnosticLoggingEnabled(context, enabled)
    }

    fun getSubtitleAutoPreference(context: Context): Flow<SubtitleAutoPreference> =
        context.settingsDataStore.data.map { preferences ->
            val raw = preferences[KEY_SUBTITLE_AUTO_PREFERENCE] ?: SubtitleAutoPreference.OFF.ordinal
            SubtitleAutoPreference.entries.getOrElse(raw) { SubtitleAutoPreference.OFF }
        }

    suspend fun setSubtitleAutoPreference(context: Context, preference: SubtitleAutoPreference) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SUBTITLE_AUTO_PREFERENCE] = preference.ordinal
        }
    }

    fun getBottomProgressBehavior(context: Context): Flow<BottomProgressBehavior> =
        context.settingsDataStore.data.map { preferences ->
            BottomProgressBehavior.fromValue(
                preferences[KEY_BOTTOM_PROGRESS_BEHAVIOR]
                    ?: BottomProgressBehavior.ALWAYS_SHOW.value
            )
        }

    suspend fun setBottomProgressBehavior(context: Context, behavior: BottomProgressBehavior) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BOTTOM_PROGRESS_BEHAVIOR] = behavior.value
        }
    }

    fun getHorizontalAdaptationEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_HORIZONTAL_ADAPTATION] ?: isTabletConfiguration(context)
        }

    suspend fun setHorizontalAdaptationEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HORIZONTAL_ADAPTATION] = enabled
        }
    }

    fun getFullscreenMode(context: Context): Flow<FullscreenMode> = context.settingsDataStore.data
        .map { preferences ->
            FullscreenMode.fromValue(preferences[KEY_FULLSCREEN_MODE] ?: FullscreenMode.AUTO.value)
        }

    suspend fun setFullscreenMode(context: Context, mode: FullscreenMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_FULLSCREEN_MODE] = mode.value
        }
    }

    fun getFullscreenAspectRatio(context: Context): Flow<FullscreenAspectRatio> =
        context.settingsDataStore.data.map { preferences ->
            FullscreenAspectRatio.fromValue(
                preferences[KEY_FULLSCREEN_ASPECT_RATIO] ?: FullscreenAspectRatio.FIT.value
            )
        }

    suspend fun setFullscreenAspectRatio(context: Context, ratio: FullscreenAspectRatio) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_FULLSCREEN_ASPECT_RATIO] = ratio.value
        }
    }
    

    
    /**
     *  圆角大小比例 (0.5 ~ 1.5, 默认 1.0)
     * 控制全局 UI 圆角大小
     */

    
    // ========== 📱 平板导航模式 ==========
    
    private val KEY_TABLET_NAVIGATION_MODE = booleanPreferencesKey("tablet_use_sidebar")
    
    /**
     *  平板导航模式
     * - false: 使用底栏（默认，与手机一致）
     * - true: 使用侧边栏
     */
    fun getTabletUseSidebar(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_TABLET_NAVIGATION_MODE] ?: false }  // 默认使用底栏

    internal fun mapAppNavigationSettingsFromPreferences(preferences: Preferences): AppNavigationSettings {
        val orderString = preferences[KEY_BOTTOM_BAR_ORDER] ?: DEFAULT_BOTTOM_BAR_ORDER
        val tabsString = preferences[KEY_BOTTOM_BAR_VISIBLE_TABS] ?: DEFAULT_BOTTOM_BAR_VISIBLE_TABS
        val order = orderString.split(",").filter { it.isNotBlank() }
        val visibleSet = tabsString.split(",").filter { it.isNotBlank() }.toSet()
        return AppNavigationSettings(
            bottomBarVisibilityMode = BottomBarVisibilityMode.fromValue(
                preferences[KEY_BOTTOM_BAR_VISIBILITY_MODE] ?: BottomBarVisibilityMode.ALWAYS_VISIBLE.value
            ),
            orderedVisibleTabIds = order.filter { it in visibleSet },
            bottomBarItemColors = parseBottomBarItemColors(preferences[KEY_BOTTOM_BAR_ITEM_COLORS] ?: ""),
            tabletUseSidebar = preferences[KEY_TABLET_NAVIGATION_MODE] ?: false
        )
    }

    fun getAppNavigationSettings(context: Context): Flow<AppNavigationSettings> {
        return NavigationSettingsStore.observe(context)
    }

    suspend fun setTabletUseSidebar(context: Context, useSidebar: Boolean) {
        NavigationSettingsStore.setTabletUseSidebar(context, useSidebar)
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

    // ========== [New] 个人中心自定义背景 ==========

    private val KEY_PROFILE_BG_URI = stringPreferencesKey("profile_bg_uri")
    private val KEY_PROFILE_BG_SCALE_MOBILE = floatPreferencesKey("profile_bg_scale_mobile")
    private val KEY_PROFILE_BG_SCALE_TABLET = floatPreferencesKey("profile_bg_scale_tablet")
    private val KEY_PROFILE_BG_OFFSET_X_MOBILE = floatPreferencesKey("profile_bg_offset_x_mobile")
    private val KEY_PROFILE_BG_OFFSET_X_TABLET = floatPreferencesKey("profile_bg_offset_x_tablet")

    /**
     * 获取自定义个人中心背景图 URI
     */
    fun getProfileBgUri(context: Context): Flow<String?> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_PROFILE_BG_URI] }

    /**
     * 设置自定义个人中心背景图 URI
     */
    suspend fun setProfileBgUri(context: Context, uri: String?) {
        context.settingsDataStore.edit { preferences ->
            if (uri != null) {
                preferences[KEY_PROFILE_BG_URI] = uri
            } else {
                preferences.remove(KEY_PROFILE_BG_URI)
            }
        }
    }
    private val KEY_PROFILE_BG_ALIGNMENT_MOBILE = floatPreferencesKey("profile_bg_alignment_mobile")
    private val KEY_PROFILE_BG_ALIGNMENT_TABLET = floatPreferencesKey("profile_bg_alignment_tablet")

    fun getProfileBgTransform(
        context: Context,
        isTablet: Boolean
    ): Flow<com.android.purebilibili.core.ui.wallpaper.ProfileWallpaperTransform> = context.settingsDataStore.data
        .map { preferences ->
            com.android.purebilibili.core.ui.wallpaper.sanitizeProfileWallpaperTransform(
                com.android.purebilibili.core.ui.wallpaper.ProfileWallpaperTransform(
                    scale = if (isTablet) {
                        preferences[KEY_PROFILE_BG_SCALE_TABLET] ?: 1f
                    } else {
                        preferences[KEY_PROFILE_BG_SCALE_MOBILE] ?: 1f
                    },
                    offsetX = if (isTablet) {
                        preferences[KEY_PROFILE_BG_OFFSET_X_TABLET] ?: 0f
                    } else {
                        preferences[KEY_PROFILE_BG_OFFSET_X_MOBILE] ?: 0f
                    },
                    offsetY = if (isTablet) {
                        preferences[KEY_PROFILE_BG_ALIGNMENT_TABLET] ?: 0f
                    } else {
                        preferences[KEY_PROFILE_BG_ALIGNMENT_MOBILE] ?: 0f
                    }
                )
            )
        }

    suspend fun setProfileBgTransform(
        context: Context,
        isTablet: Boolean,
        transform: com.android.purebilibili.core.ui.wallpaper.ProfileWallpaperTransform
    ) {
        val safeTransform =
            com.android.purebilibili.core.ui.wallpaper.sanitizeProfileWallpaperTransform(transform)
        context.settingsDataStore.edit { preferences ->
            val scaleKey = if (isTablet) KEY_PROFILE_BG_SCALE_TABLET else KEY_PROFILE_BG_SCALE_MOBILE
            val offsetXKey = if (isTablet) KEY_PROFILE_BG_OFFSET_X_TABLET else KEY_PROFILE_BG_OFFSET_X_MOBILE
            val offsetYKey = if (isTablet) KEY_PROFILE_BG_ALIGNMENT_TABLET else KEY_PROFILE_BG_ALIGNMENT_MOBILE
            preferences[scaleKey] = safeTransform.scale
            preferences[offsetXKey] = safeTransform.offsetX
            preferences[offsetYKey] = safeTransform.offsetY
        }
    }

    suspend fun resetProfileBgTransform(context: Context) {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(KEY_PROFILE_BG_SCALE_MOBILE)
            preferences.remove(KEY_PROFILE_BG_SCALE_TABLET)
            preferences.remove(KEY_PROFILE_BG_OFFSET_X_MOBILE)
            preferences.remove(KEY_PROFILE_BG_OFFSET_X_TABLET)
            preferences.remove(KEY_PROFILE_BG_ALIGNMENT_MOBILE)
            preferences.remove(KEY_PROFILE_BG_ALIGNMENT_TABLET)
        }
    }

    /**
     * 获取个人中心背景图对齐方式 (竖向Bias: -1.0 Top ~ 1.0 Bottom, Default 0.0 Center)
     * 分为移动端和平板端独立存储
     */
    fun getProfileBgAlignment(context: Context, isTablet: Boolean): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            if (isTablet) {
                preferences[KEY_PROFILE_BG_ALIGNMENT_TABLET] ?: 0f
            } else {
                preferences[KEY_PROFILE_BG_ALIGNMENT_MOBILE] ?: 0f
            }
        }

    suspend fun setProfileBgAlignment(context: Context, isTablet: Boolean, bias: Float) {
        val currentTransform = getProfileBgTransform(context, isTablet).first()
        setProfileBgTransform(
            context = context,
            isTablet = isTablet,
            transform = currentTransform.copy(offsetY = bias)
        )
    }

    private val shareableSettingDefinitions: List<ShareablePreferenceDefinition> by lazy {
        listOf(
            IntShareablePreferenceDefinition(KEY_UI_PRESET, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_THEME_MODE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_DARK_THEME_STYLE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_APP_LANGUAGE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_DYNAMIC_COLOR, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_THEME_COLOR_INDEX, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_APP_ICON, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_BOTTOM_BAR_FLOATING, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_BOTTOM_BAR_LABEL_MODE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_TOP_TAB_LABEL_MODE, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_TOP_TAB_ORDER, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_TOP_TAB_VISIBLE_TABS, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_HEADER_BLUR_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_HEADER_COLLAPSE_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_BOTTOM_BAR_BLUR_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_LIQUID_GLASS_ENABLED, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_LIQUID_GLASS_STYLE, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_BLUR_INTENSITY, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_DISPLAY_MODE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_GRID_COLUMN_COUNT, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_CARD_ANIMATION_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_CARD_TRANSITION_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(
                KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED,
                SettingsShareSection.APPEARANCE
            ),
            BooleanShareablePreferenceDefinition(KEY_PREDICTIVE_BACK_ANIMATION_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_COMPACT_VIDEO_STATS_ON_COVER, SettingsShareSection.APPEARANCE),

            BooleanShareablePreferenceDefinition(KEY_AUTO_PLAY, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_PLAYBACK_COMPLETION_BEHAVIOR, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_HW_DECODE, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_BG_PLAY, SettingsShareSection.PLAYBACK),
            FloatShareablePreferenceDefinition(KEY_DEFAULT_PLAYBACK_SPEED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_REMEMBER_LAST_PLAYBACK_SPEED, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_COMMENT_DEFAULT_SORT_MODE, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_STOP_PLAYBACK_ON_EXIT, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_BACKGROUND_PLAYBACK_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_AUDIO_FOCUS_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_VIDEO_AI_SUMMARY_ENTRY_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_CLICK_TO_PLAY, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_RESUME_PLAYBACK_PROMPT_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_AUTO_ROTATE_ENABLED, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_WIFI_QUALITY, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_MOBILE_QUALITY, SettingsShareSection.PLAYBACK),
            StringShareablePreferenceDefinition(KEY_VIDEO_CODEC, SettingsShareSection.PLAYBACK),
            StringShareablePreferenceDefinition(KEY_VIDEO_SECOND_CODEC, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_AUDIO_QUALITY, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_AUTO_HIGHEST_QUALITY, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_SPONSOR_BLOCK_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_SPONSOR_BLOCK_AUTO_SKIP, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_DATA_SAVER_MODE, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_PORTRAIT_FULLSCREEN_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_AUTO_PORTRAIT_FULLSCREEN, SettingsShareSection.PLAYBACK),
            FloatShareablePreferenceDefinition(KEY_VERTICAL_VIDEO_RATIO, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_FULLSCREEN_MODE, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_FULLSCREEN_ASPECT_RATIO, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_SUBTITLE_AUTO_PREFERENCE, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_BOTTOM_PROGRESS_BEHAVIOR, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_HORIZONTAL_ADAPTATION, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_SHOW_ONLINE_COUNT, SettingsShareSection.PLAYBACK),

            BooleanShareablePreferenceDefinition(KEY_HAPTIC_FEEDBACK_ENABLED, SettingsShareSection.GESTURE),
            FloatShareablePreferenceDefinition(KEY_GESTURE_SENSITIVITY, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SLIDE_VOLUME_BRIGHTNESS_ENABLED, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SET_SYSTEM_BRIGHTNESS, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_DOUBLE_TAP_SEEK_ENABLED, SettingsShareSection.GESTURE),
            IntShareablePreferenceDefinition(KEY_SEEK_FORWARD_SECONDS, SettingsShareSection.GESTURE),
            IntShareablePreferenceDefinition(KEY_SEEK_BACKWARD_SECONDS, SettingsShareSection.GESTURE),
            FloatShareablePreferenceDefinition(KEY_LONG_PRESS_SPEED, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_PIP_NO_DANMAKU, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_DOUBLE_TAP_LIKE, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SWIPE_HIDE_PLAYER, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_PORTRAIT_SWIPE_TO_FULLSCREEN, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_CENTER_SWIPE_TO_FULLSCREEN, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_FULLSCREEN_SWIPE_SEEK_ENABLED, SettingsShareSection.GESTURE),
            IntShareablePreferenceDefinition(KEY_FULLSCREEN_SWIPE_SEEK_SECONDS, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_FULLSCREEN_GESTURE_REVERSE, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_AUTO_ENTER_FULLSCREEN, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_AUTO_EXIT_FULLSCREEN, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SHOW_FULLSCREEN_LOCK_BUTTON, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SHOW_FULLSCREEN_SCREENSHOT_BUTTON, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SHOW_FULLSCREEN_BATTERY_LEVEL, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SHOW_FULLSCREEN_TIME, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SHOW_FULLSCREEN_ACTION_ITEMS, SettingsShareSection.GESTURE),

            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ENABLED, SettingsShareSection.DANMAKU),
            FloatShareablePreferenceDefinition(KEY_DANMAKU_OPACITY, SettingsShareSection.DANMAKU),
            FloatShareablePreferenceDefinition(KEY_DANMAKU_FONT_SCALE, SettingsShareSection.DANMAKU),
            FloatShareablePreferenceDefinition(KEY_DANMAKU_SPEED, SettingsShareSection.DANMAKU),
            FloatShareablePreferenceDefinition(KEY_DANMAKU_AREA, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_SCROLL, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_TOP, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_BOTTOM, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_COLORFUL, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_SPECIAL, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_SMART_OCCLUSION, SettingsShareSection.DANMAKU),
            StringShareablePreferenceDefinition(KEY_DANMAKU_BLOCK_RULES, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_MERGE_DUPLICATES, SettingsShareSection.DANMAKU),

            StringShareablePreferenceDefinition(KEY_BOTTOM_BAR_ORDER, SettingsShareSection.NAVIGATION),
            StringShareablePreferenceDefinition(KEY_BOTTOM_BAR_VISIBLE_TABS, SettingsShareSection.NAVIGATION),
            StringShareablePreferenceDefinition(KEY_BOTTOM_BAR_ITEM_COLORS, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(KEY_BOTTOM_BAR_VISIBILITY_MODE, SettingsShareSection.NAVIGATION),
            BooleanShareablePreferenceDefinition(KEY_TABLET_NAVIGATION_MODE, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(KEY_DYNAMIC_PAGE_LAYOUT_DIRECTION, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(KEY_FEED_API_TYPE, SettingsShareSection.NAVIGATION),
            BooleanShareablePreferenceDefinition(KEY_INCREMENTAL_TIMELINE_REFRESH, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(KEY_HOME_REFRESH_COUNT, SettingsShareSection.NAVIGATION)
        )
    }

    fun getShareableSettingsEntryDefinitions(): List<SettingsShareEntryDefinition> {
        return shareableSettingDefinitions.map { it.entryDefinition }
    }

    suspend fun exportShareableSettingsSnapshot(context: Context): Map<String, JsonElement> {
        val preferences = context.settingsDataStore.data.first()
        return linkedMapOf<String, JsonElement>().apply {
            shareableSettingDefinitions.forEach { definition ->
                definition.read(preferences)?.let { value ->
                    put(definition.entryDefinition.storageKey, value)
                }
            }
        }
    }

    suspend fun applyShareableSettingsSnapshot(
        context: Context,
        settings: Map<String, JsonElement>
    ): SettingsShareApplyResult {
        val definitionsByKey = shareableSettingDefinitions.associateBy { it.entryDefinition.storageKey }
        val appliedKeys = mutableListOf<String>()
        val skippedKeys = mutableListOf<String>()

        context.settingsDataStore.edit { preferences ->
            settings.forEach { (key, value) ->
                val definition = definitionsByKey[key]
                when {
                    definition == null -> skippedKeys += key
                    definition.write(preferences, value) -> appliedKeys += key
                    else -> skippedKeys += key
                }
            }
        }

        return SettingsShareApplyResult(
            appliedKeys = appliedKeys.distinct().sorted(),
            skippedKeys = skippedKeys.distinct().sorted()
        )
    }
}
