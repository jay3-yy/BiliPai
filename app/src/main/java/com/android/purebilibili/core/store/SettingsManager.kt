// 文件路径: core/store/SettingsManager.kt
package com.android.purebilibili.core.store

import android.content.Context
import com.android.purebilibili.BuildConfig
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppListItemStyle
import com.android.purebilibili.core.ui.components.AppSingleChoicePresentation
import com.android.purebilibili.core.ui.resolveAppIconStylePreference
import com.android.purebilibili.core.ui.resolveAppListItemStylePreference
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionSpeed
import com.android.purebilibili.core.ui.transition.normalizeVideoSharedTransitionCustomDurationMillis
import com.android.purebilibili.core.store.home.HomeSettingsStore
import com.android.purebilibili.core.store.home.liquidGlassReadabilityModePreferencesKey
import com.android.purebilibili.core.store.navigation.NavigationSettingsStore
import com.android.purebilibili.core.store.navigation.bottomBarItemLabelsPreferencesKey
import com.android.purebilibili.core.store.navigation.miuixPredictiveBackMaxProgressPercentPreferencesKey
import com.android.purebilibili.core.store.navigation.parseBottomBarItemLabels
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.store.player.defaultAudioQualityPreferenceKey
import com.android.purebilibili.core.theme.AppFontSizePreset
import com.android.purebilibili.core.theme.AppUiScalePreset
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.theme.normalizeThemeColorIndex
import com.android.purebilibili.core.theme.resolveColorSpecPreference
import com.android.purebilibili.core.theme.resolvePaletteStylePreference
import com.android.purebilibili.data.model.response.LiveFavoriteTagEntry
import com.android.purebilibili.feature.settings.share.SettingsShareApplyResult
import com.android.purebilibili.feature.settings.share.SettingsShareEntryDefinition
import com.android.purebilibili.feature.settings.share.SettingsShareSection
import com.android.purebilibili.feature.settings.AppLanguage
import com.android.purebilibili.feature.settings.AppThemeMode
import com.android.purebilibili.feature.settings.DarkThemeStyle
import com.android.purebilibili.feature.settings.Md3ColorSource
import com.android.purebilibili.feature.settings.normalizeMd3CustomColorHex
import com.android.purebilibili.feature.settings.resolveAppLanguagePreference
import com.android.purebilibili.feature.settings.resolveDarkThemeStylePreference
import com.android.purebilibili.feature.settings.resolveMd3ColorSourcePreference
import com.android.purebilibili.feature.settings.resolveThemeModePreference
import com.android.purebilibili.feature.screenshot.AppScreenshotCaptureMode
import com.android.purebilibili.feature.screenshot.AppScreenshotGestureMode
import com.android.purebilibili.feature.video.ui.components.CollectionSortMode
import com.android.purebilibili.feature.video.danmaku.DANMAKU_DEFAULT_OPACITY
import com.android.purebilibili.feature.video.danmaku.normalizeDanmakuOpacity
import com.android.purebilibili.feature.video.danmaku.parseDanmakuBlockRules
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.normalizeSubtitleVerticalOffsetFraction
import com.android.purebilibili.feature.video.ui.gesture.TwoFingerSpeedToggleState
import com.android.purebilibili.feature.video.ui.gesture.applyHorizontalTwoFingerSpeedToggle
import com.android.purebilibili.feature.video.ui.gesture.applyVerticalTwoFingerSpeedToggle
import com.android.purebilibili.core.util.ENHANCED_DIAGNOSTIC_LOG_PREF_KEY
import com.android.purebilibili.core.util.ENHANCED_DIAGNOSTIC_LOG_PREFS_NAME
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs

// 声明 DataStore 扩展属性
internal val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

internal const val DEFAULT_CRASH_TRACKING_ENABLED = true
internal const val DEFAULT_ANALYTICS_ENABLED = true
internal const val DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED = true
internal const val DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED = false
internal const val DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED = false

internal fun resolveDefaultPlayerDiagnosticLoggingEnabled(isDebugBuild: Boolean): Boolean {
    return !isDebugBuild
}

internal val DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED: Boolean =
    resolveDefaultPlayerDiagnosticLoggingEnabled(BuildConfig.DEBUG)

/**
 *  首页设置合并类 - 减少 HomeScreen 重组次数
 * 将多个独立的设置流合并为单一流，避免每个设置变化都触发重组
 */
enum class LiquidGlassStyle(val value: Int) {
    CLASSIC(0),      // BiliPai's Wavy Ripple
    SUKISU(1),       // SukiSU floating bottom bar glass
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

enum class LiquidGlassAdvancedPreset(val value: Int, val label: String) {
    READABLE(0, "清晰"),
    BALANCED(1, "均衡"),
    PRISM(2, "棱镜"),
    CUSTOM(3, "自定");

    companion object {
        fun fromValue(value: Int): LiquidGlassAdvancedPreset =
            entries.find { it.value == value } ?: BALANCED
    }
}

data class LiquidGlassAdvancedSettings(
    val preset: LiquidGlassAdvancedPreset = LiquidGlassAdvancedPreset.BALANCED,
    val progressiveBlurRadius: Float = 0.40f,
    val progressiveBlurExtent: Float = 1.0f,
    val progressiveBlurCurve: Float = 0.55f,
    val contentReadability: Float = 0.62f,
    val chromaticAberration: Float = 0.56f,
    val contentDistortion: Float = 0.45f,
)

internal fun normalizeLiquidGlassAdvancedValue(value: Float, fallback: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

internal fun resolveLiquidGlassAdvancedPreset(
    preset: LiquidGlassAdvancedPreset,
): LiquidGlassAdvancedSettings = when (preset) {
    LiquidGlassAdvancedPreset.READABLE -> LiquidGlassAdvancedSettings(
        preset = preset,
        progressiveBlurRadius = 0.25f,
        progressiveBlurExtent = 0.6f,
        progressiveBlurCurve = 0.4f,
        contentReadability = 1f,
        chromaticAberration = 0.08f,
        contentDistortion = 0f,
    )
    LiquidGlassAdvancedPreset.BALANCED -> LiquidGlassAdvancedSettings(
        preset = preset,
        progressiveBlurRadius = 0.40f,
        progressiveBlurExtent = 1.0f,
        progressiveBlurCurve = 0.55f,
        contentReadability = 0.62f,
        chromaticAberration = 0.56f,
        contentDistortion = 0.45f,
    )
    LiquidGlassAdvancedPreset.PRISM -> LiquidGlassAdvancedSettings(
        preset = preset,
        progressiveBlurRadius = 0.8f,
        progressiveBlurExtent = 0.9f,
        progressiveBlurCurve = 0.65f,
        contentReadability = 0.72f,
        chromaticAberration = 0.96f,
        contentDistortion = 1f,
    )
    LiquidGlassAdvancedPreset.CUSTOM -> LiquidGlassAdvancedSettings(preset = preset)
}

internal fun resolveLiquidGlassAdvancedSettings(
    presetValue: Int?,
    progressiveBlurRadius: Float? = null,
    progressiveBlurExtent: Float? = null,
    progressiveBlurCurve: Float? = null,
    contentReadability: Float?,
    chromaticAberration: Float?,
    contentDistortion: Float?,
): LiquidGlassAdvancedSettings {
    val preset = presetValue
        ?.let(LiquidGlassAdvancedPreset::fromValue)
        ?: LiquidGlassAdvancedPreset.BALANCED
    val defaults = resolveLiquidGlassAdvancedPreset(preset)
    if (preset != LiquidGlassAdvancedPreset.CUSTOM) return defaults
    return defaults.copy(
        progressiveBlurRadius = normalizeLiquidGlassAdvancedValue(
            progressiveBlurRadius ?: defaults.progressiveBlurRadius,
            defaults.progressiveBlurRadius,
        ),
        progressiveBlurExtent = normalizeLiquidGlassAdvancedValue(
            progressiveBlurExtent ?: defaults.progressiveBlurExtent,
            defaults.progressiveBlurExtent,
        ),
        progressiveBlurCurve = normalizeLiquidGlassAdvancedValue(
            progressiveBlurCurve ?: defaults.progressiveBlurCurve,
            defaults.progressiveBlurCurve,
        ),
        contentReadability = normalizeLiquidGlassAdvancedValue(
            contentReadability ?: defaults.contentReadability,
            defaults.contentReadability,
        ),
        chromaticAberration = normalizeLiquidGlassAdvancedValue(
            chromaticAberration ?: defaults.chromaticAberration,
            defaults.chromaticAberration,
        ),
        contentDistortion = normalizeLiquidGlassAdvancedValue(
            contentDistortion ?: defaults.contentDistortion,
            defaults.contentDistortion,
        ),
    )
}

internal fun resolveLegacyLiquidGlassMode(style: LiquidGlassStyle): LiquidGlassMode = when (style) {
    LiquidGlassStyle.IOS26 -> LiquidGlassMode.CLEAR
    LiquidGlassStyle.CLASSIC -> LiquidGlassMode.BALANCED
    LiquidGlassStyle.SUKISU -> LiquidGlassMode.BALANCED
}

internal fun resolveDefaultLiquidGlassStrength(mode: LiquidGlassMode): Float = when (mode) {
    LiquidGlassMode.CLEAR -> 0.42f
    LiquidGlassMode.BALANCED -> 0.52f
    LiquidGlassMode.FROSTED -> 0.62f
}

internal fun normalizeLiquidGlassStrength(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0.52f

internal fun normalizeLiquidGlassProgress(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f

/** 长按倍速提示整体缩放（0.8×–1.5×，默认 1.0×）。 */
internal const val LONG_PRESS_SPEED_HINT_SCALE_MIN = 0.8f
internal const val LONG_PRESS_SPEED_HINT_SCALE_MAX = 1.5f
internal const val LONG_PRESS_SPEED_HINT_DEFAULT_SCALE = 1.0f
internal const val LONG_PRESS_SPEED_HINT_ALPHA_MIN = 0.3f
internal const val LONG_PRESS_SPEED_HINT_ALPHA_MAX = 1.0f
internal const val LONG_PRESS_SPEED_HINT_DEFAULT_ALPHA = 0.5f
internal const val LONG_PRESS_SPEED_HINT_STEP = 0.05f

internal fun normalizeLongPressSpeedHintScale(value: Float): Float =
    if (!value.isFinite()) LONG_PRESS_SPEED_HINT_DEFAULT_SCALE
    else value.coerceIn(LONG_PRESS_SPEED_HINT_SCALE_MIN, LONG_PRESS_SPEED_HINT_SCALE_MAX)

/** 长按倍速提示背景/内容透明度（0.3–1.0，默认 0.5）。 */
internal fun normalizeLongPressSpeedHintAlpha(value: Float): Float =
    if (!value.isFinite()) LONG_PRESS_SPEED_HINT_DEFAULT_ALPHA
    else value.coerceIn(LONG_PRESS_SPEED_HINT_ALPHA_MIN, LONG_PRESS_SPEED_HINT_ALPHA_MAX)

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

/** Prefer the v2 continuous value, falling back to the complete legacy material selection. */
internal fun resolveStoredLiquidGlassProgress(
    progress: Float?,
    legacyModeValue: Int?,
    legacyStrength: Float?,
    legacyStyleValue: Int?,
): Float {
    progress?.let { return normalizeLiquidGlassProgress(it) }
    val legacyStyle = LiquidGlassStyle.fromValue(
        legacyStyleValue ?: LiquidGlassStyle.SUKISU.value
    )
    val mode = legacyModeValue
        ?.let(LiquidGlassMode::fromValue)
        ?: resolveLegacyLiquidGlassMode(legacyStyle)
    val strength = normalizeLiquidGlassStrength(
        legacyStrength ?: resolveDefaultLiquidGlassStrength(mode)
    )
    return resolveLegacyLiquidGlassProgress(mode, strength)
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
        LiquidGlassMode.FROSTED -> LiquidGlassStyle.SUKISU
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

enum class PortraitPlayerCollapseMode(val value: Int, val label: String, val description: String) {
    OFF(0, "关闭", "不自动缩小播放器"),
    INTRO_ONLY(1, "竖屏", "竖屏视频评论区或简介上滑时缩小播放器"),
    COMMENT_ONLY(2, "横屏", "仅横屏视频详情页滚动时缩小播放器"),
    BOTH(3, "全部", "横竖屏视频都使用播放器缩小策略"),
    PAUSED_ONLY(4, "暂停时", "横竖屏视频暂停后，下滑评论或简介可缩小播放器");

    val enablesPortraitVideo: Boolean
        get() = this == INTRO_ONLY || this == BOTH || this == PAUSED_ONLY

    val enablesLandscapeVideo: Boolean
        get() = this == COMMENT_ONLY || this == BOTH || this == PAUSED_ONLY

    fun enablesVideoOrientation(isVerticalVideo: Boolean): Boolean {
        return if (isVerticalVideo) enablesPortraitVideo else enablesLandscapeVideo
    }

    val enablesIntro: Boolean
        get() = this != OFF

    val enablesComment: Boolean
        get() = this != OFF

    companion object {
        fun fromValue(value: Int): PortraitPlayerCollapseMode {
            return entries.find { it.value == value } ?: OFF
        }

        fun fromLegacySwipeHide(enabled: Boolean): PortraitPlayerCollapseMode {
            return if (enabled) INTRO_ONLY else OFF
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
            return entries.find { it.value == value } ?: ALWAYS_HIDE
        }
    }
}

enum class PlayerProgressPlacement(
    val value: Int,
    val label: String
) {
    ABOVE_CONTROLS(0, "控制栏上方"),
    BOTTOM_EDGE(1, "视频最底部");

    companion object {
        fun fromValue(value: Int): PlayerProgressPlacement {
            return entries.find { it.value == value } ?: ABOVE_CONTROLS
        }
    }
}

data class PlayerControlVisibilitySettings(
    val showCastButton: Boolean = true,
    val showFollowButton: Boolean = true
)

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

enum class HomeFeedCardWidthPreset(
    val value: Int,
    val label: String,
    val minCardWidthDp: Int?
) {
    AUTO(0, "自动", null),
    COMPACT(1, "紧凑", 160),
    BALANCED(2, "均衡", 200),
    WIDE(3, "宽卡片", 260),
    ULTRA_WIDE(4, "超宽", 320);

    companion object {
        fun fromValue(value: Int): HomeFeedCardWidthPreset =
            entries.find { it.value == value } ?: AUTO
    }
}

/**
 * 双列视频卡封面框三档（全局一份设置，均居中 Crop）：
 * - [CURRENT] 16:9：与 CDN 投稿源同比例，标准封面几乎不裁
 * - [OFFICIAL] 4:3：更高列表框，左右会裁
 * - [BILIPAI] 16:10：默认信息流封面比例
 */
enum class HomeFeedCardStyle(val value: Int, val label: String, val subtitle: String) {
    CURRENT(0, "16:9", "完整显示，接近投稿源图"),
    OFFICIAL(1, "4:3", "更高列表框，左右居中裁切"),
    BILIPAI(2, "16:10", "默认信息流封面比例");

    companion object {
        fun fromValue(value: Int): HomeFeedCardStyle =
            entries.find { it.value == value } ?: BILIPAI
    }
}

enum class HomeDurationStyle(val value: Int, val label: String) {
    OUTSIDE_COVER(0, "封面外"),
    OVERLAY_TEXT_ONLY(1, "封面内无底色"),
    HIDDEN(2, "隐藏");

    companion object {
        fun fromValue(value: Int): HomeDurationStyle =
            entries.find { it.value == value } ?: OUTSIDE_COVER
    }
}

/**
 * 首页/列表视频卡片标签（播放量、时长、信息区）表面效果。
 * 轻模糊在滚动时会降级为软玻璃，避免列表掉帧。
 */
enum class HomeCardBadgeEffectMode(
    val value: Int,
    val label: String,
    val subtitle: String
) {
    OFF(0, "关闭", "纯文字标签，性能最好"),
    SOFT_GLASS(1, "软玻璃", "半透明描边拟态，无实时采样"),
    LIGHT_BLUR(2, "实时模糊", "开发中，请勿使用：Haze 采样壁纸/背景");

    companion object {
        fun fromValue(value: Int): HomeCardBadgeEffectMode =
            entries.find { it.value == value } ?: SOFT_GLASS
    }
}

/**
 * Card info strip (title + UP under cover) glass — independent of cover badge pills.
 * Realtime blur (Haze) and realtime liquid glass (LayerBackdrop) are separate modes.
 */
enum class HomeCardInfoGlassMode(
    val value: Int,
    val label: String,
    val subtitle: String
) {
    OFF(0, "关闭", "实色/轻 tint，性能最好（推荐）"),
    REALTIME_BLUR(1, "实时模糊", "开发中，请勿使用：Haze 采样壁纸磨砂"),
    REALTIME_LIQUID_GLASS(2, "实时液态玻璃", "开发中，请勿使用：折射液态玻璃"),
    BLUR_AND_LIQUID(3, "模糊+液态", "开发中，请勿使用：Haze + 液态叠加");

    val usesRealtimeBlur: Boolean
        get() = this == REALTIME_BLUR || this == BLUR_AND_LIQUID

    val usesRealtimeLiquidGlass: Boolean
        get() = this == REALTIME_LIQUID_GLASS || this == BLUR_AND_LIQUID

    companion object {
        fun fromValue(value: Int): HomeCardInfoGlassMode =
            entries.find { it.value == value } ?: OFF
    }
}

enum class BottomBarLiquidGlassPreset(
    val value: Int,
    val label: String,
    val description: String
) {
    BILIPAI_TUNED(
        0,
        "BiliPai 调校",
        "保留当前多层折射、色散和指示器动效"
    ),
    IOS26_REFINED(
        1,
        "iOS 26 玻璃",
        "厚边折射 + 顶光高亮环，无色散，沿用 BiliPai 指示器滑动与配色"
    );

    companion object {
        fun fromValue(value: Int): BottomBarLiquidGlassPreset =
            entries.find { it.value == value } ?: BILIPAI_TUNED
    }
}

data class HomeSettings(
    val displayMode: Int = 0,              // 展示模式 (0=网格, 1=故事卡片)
    val isBottomBarFloating: Boolean = true,
    val navigationIconCrossScaleEnabled: Boolean = true,
    val bottomBarLabelMode: Int = 0,       // (0=图标+文字, 1=仅图标, 2=仅文字)
    val topTabLabelMode: Int = 2,          // (0=图标+文字, 1=仅图标, 2=仅文字)
    val homeTopRightAction: HomeTopRightAction = HomeTopRightAction.SETTINGS,
    val homeTopLayoutOrder: HomeTopLayoutOrder = HomeTopLayoutOrder.SEARCH_THEN_TABS,
    val isHeaderBlurEnabled: Boolean = true,
    val headerBlurMode: HomeHeaderBlurMode = HomeHeaderBlurMode.FOLLOW_PRESET,
    val isBottomBarBlurEnabled: Boolean = false,
    val isTopBarLiquidGlassEnabled: Boolean = false,
    val isHomeSearchLiquidGlassEnabled: Boolean = false,
    val isBottomBarLiquidGlassEnabled: Boolean = false,
    val bottomBarLiquidGlassPreset: BottomBarLiquidGlassPreset =
        BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    val isBottomBarSearchEnabled: Boolean = false,
    val bottomBarSearchAutoExpandMode: BottomBarSearchAutoExpandMode =
        BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
    val bottomBarSearchLayoutMode: BottomBarSearchLayoutMode =
        BottomBarSearchLayoutMode.FULL_DOCK,
    val androidNativeLiquidGlassEnabled: Boolean = true,
    val liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC, // [New]
    val liquidGlassMode: LiquidGlassMode = LiquidGlassMode.BALANCED,
    val liquidGlassStrength: Float = 0.52f,
    val liquidGlassProgress: Float = 0.5f,
    val liquidGlassReadabilityMode: LiquidGlassReadabilityMode =
        LiquidGlassReadabilityMode.STABLE,
    val liquidGlassAdvancedSettings: LiquidGlassAdvancedSettings = LiquidGlassAdvancedSettings(),
    val homeHeaderCollapseMode: HomeHeaderCollapseMode = HomeHeaderCollapseMode.BOTH,
    val homeBarHideType: HomeBarHideType = HomeBarHideType.SYNC,
    val commonListHeaderCollapseMode: CommonListHeaderCollapseMode =
        CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL,
    val isHeaderCollapseEnabled: Boolean = true,
    val showPgcTimeline: Boolean = true,
    val gridColumnCount: Int = 0, // [New] 网格列数 (0=自动, 1-6=固定)
    val homeFeedCardWidthPreset: HomeFeedCardWidthPreset = HomeFeedCardWidthPreset.AUTO,
    val homeFeedCardStyle: HomeFeedCardStyle = HomeFeedCardStyle.BILIPAI,
    val homeHeroCarouselEnabled: Boolean = true,
    val homeHeroCarouselAutoplayEnabled: Boolean = false,
    val cardAnimationEnabled: Boolean = false,    //  卡片进场动画（默认关闭）
    val cardTransitionEnabled: Boolean = true,    //  卡片过渡动画（默认开启）
    val videoSharedTransitionSpeed: VideoSharedTransitionSpeed = VideoSharedTransitionSpeed.STANDARD,
    val videoSharedTransitionCustomDurationMillis: Int =
        VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS,
    // [Retired] 旧的首页 feed「智能流畅优先」，固定关闭。
    // 运行时视觉守卫是另一套机制，见 [runtimeVisualGuardEnabled]。
    val smartVisualGuardEnabled: Boolean = false,
    // 运行时视觉守卫：连续掉帧时自动降级毛玻璃/液态玻璃/景深。
    // 影响面覆盖全 App 视觉，必须保留 kill switch——某机型 JankStats 读数异常时可关闭。
    val runtimeVisualGuardEnabled: Boolean = true,
    val compactVideoStatsOnCover: Boolean = false, // 播放/弹幕位于信息区，不叠加在封面上
    val lowQualityHomeCoverInDataSaver: Boolean = false, // 省流量时首页封面使用低清晰度
    // 卡片标签 / 信息区玻璃效果已下线，保留字段仅为兼容旧数据结构。
    val showHomeCoverGlassBadges: Boolean = false,
    val showHomeInfoGlassBadges: Boolean = false,
    val homeCardBadgeEffectMode: HomeCardBadgeEffectMode = HomeCardBadgeEffectMode.OFF,
    val homeCardInfoGlassMode: HomeCardInfoGlassMode = HomeCardInfoGlassMode.OFF,
    val homeWallpaperEffectMode: HomeWallpaperEffectMode = HomeWallpaperEffectMode.SOFT_BLUR,
    val homeWallpaperEffectScope: HomeWallpaperEffectScope = HomeWallpaperEffectScope.HOME_ONLY,
    val showHomeUpBadges: Boolean = false, // 首页和相关推荐 UP 主标识显示(默认关闭,设置后全局生效)
    val showHomeUpAvatars: Boolean = false, // 首页视频卡片 UP 主头像显示(默认关闭,设置后全局生效)
    val showFullVideoCardContent: Boolean = false, // 视频卡片标题完整展示(默认关闭,设置后全局生效)
    val homeDurationStyle: HomeDurationStyle = HomeDurationStyle.OUTSIDE_COVER,
    val easterEggEnabled: Boolean = false, // 下拉刷新趣味提示开关
    //  [修复] 默认值改为 true，避免在 Flow 加载实际值之前错误触发弹窗
    // 当 Flow 加载完成后，如果实际值是 false，LaunchedEffect 会再次触发并显示弹窗
    val crashTrackingConsentShown: Boolean = true
) {
    val isLiquidGlassEnabled: Boolean
        get() = androidNativeLiquidGlassEnabled
}

data class AppThemeSettings(
    val uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val darkThemeStyle: DarkThemeStyle = DarkThemeStyle.DEFAULT,
    val appLanguage: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
    val md3ColorSource: Md3ColorSource = Md3ColorSource.FOLLOW_WALLPAPER,
    val md3CustomColorHex: String = "#007AFF",
    val themeRoleOverrides: ThemeRoleOverrides = ThemeRoleOverrides(),
    val colorStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
    val themeColorIndex: Int = 0,
    val appFontSizePreset: AppFontSizePreset = AppFontSizePreset.DEFAULT,
    val appFontFileName: String = "",
    val appUiScalePreset: AppUiScalePreset = AppUiScalePreset.STANDARD,
    val appDpiOverridePercent: Int = 0,
    val appGestureScreenshotEnabled: Boolean = false,
    val appScreenshotGestureMode: AppScreenshotGestureMode =
        AppScreenshotGestureMode.TOP_RIGHT_TWO_FINGER_LONG_PRESS,
    val appScreenshotCaptureMode: AppScreenshotCaptureMode =
        AppScreenshotCaptureMode.FULL_WINDOW,
    val appIconStyle: AppIconStyle = AppIconStyle.AUTO,
    val appListItemStyle: AppListItemStyle = AppListItemStyle.AUTO,
    val singleChoicePresentation: AppSingleChoicePresentation =
        AppSingleChoicePresentation.WINDOW_POPUP,
)

data class ThemeModeRoleOverrides(
    val backgroundHex: String,
    val primaryTextHex: String,
    val secondaryTextHex: String,
    val controlAccentHex: String
)

data class ThemeRoleOverrides(
    val enabled: Boolean = false,
    val light: ThemeModeRoleOverrides = ThemeModeRoleOverrides(
        backgroundHex = "#FFFDF8",
        primaryTextHex = "#1C1B1F",
        secondaryTextHex = "#49454F",
        controlAccentHex = "#0061A4"
    ),
    val dark: ThemeModeRoleOverrides = ThemeModeRoleOverrides(
        backgroundHex = "#121212",
        primaryTextHex = "#E6E1E5",
        secondaryTextHex = "#CAC4D0",
        controlAccentHex = "#9ECAFF"
    )
)

enum class BottomBarSearchAutoExpandMode(val value: Int, val label: String) {
    EXPAND_WHEN_SCROLLING_DOWN(0, "下滑展开"),
    EXPAND_AT_HOME_TOP(1, "顶部展开"),
    DISABLED(2, "不自动展开");

    companion object {
        fun fromValue(value: Int): BottomBarSearchAutoExpandMode =
            entries.find { it.value == value } ?: EXPAND_AT_HOME_TOP
    }
}

enum class BottomBarSearchLayoutMode(val value: Int, val label: String) {
    FULL_DOCK(0, "完整底栏"),
    HOME_AND_SEARCH(1, "首页与搜索");

    companion object {
        fun fromValue(value: Int): BottomBarSearchLayoutMode =
            entries.find { it.value == value } ?: FULL_DOCK
    }
}

enum class HomeWallpaperEffectMode(val value: Int, val label: String) {
    OFF(0, "关闭"),
    SOFT_BLUR(1, "轻微模糊"),
    ORIGINAL(2, "原图"),
    STRONG_BLUR(3, "强模糊");

    companion object {
        fun fromValue(value: Int): HomeWallpaperEffectMode =
            entries.find { it.value == value } ?: SOFT_BLUR
    }
}

enum class HomeWallpaperEffectScope(val value: Int, val label: String) {
    HOME_ONLY(0, "仅首页"),
    GLOBAL(1, "全局");

    companion object {
        fun fromValue(value: Int): HomeWallpaperEffectScope =
            entries.find { it.value == value } ?: HOME_ONLY
    }
}

enum class HomeTopRightAction(val value: Int, val label: String) {
    SETTINGS(0, "设置"),
    INBOX(1, "消息");

    companion object {
        fun fromValue(value: Int): HomeTopRightAction =
            entries.find { it.value == value } ?: SETTINGS
    }
}

enum class HomeTopLayoutOrder(val value: Int, val label: String) {
    SEARCH_THEN_TABS(0, "搜索在上"),
    TABS_THEN_SEARCH(1, "标签在上");

    companion object {
        fun fromValue(value: Int): HomeTopLayoutOrder =
            entries.find { it.value == value } ?: SEARCH_THEN_TABS
    }
}

enum class HomeHeaderCollapseMode(
    val value: Int,
    val label: String,
    val description: String,
    val collapseSearch: Boolean,
    val collapseTabs: Boolean
) {
    SEARCH_ONLY(0, "仅搜索", "列表下滑时只收起搜索行，标签页保持显示", true, false),
    TABS_ONLY(1, "仅标签", "列表下滑时只收起标签页，搜索行保持显示", false, true),
    BOTH(2, "都折叠", "搜索行和标签页都会随列表下滑收起", true, true),
    OFF(3, "都不折叠", "搜索行和标签页始终展开", false, false);

    val hasAnyCollapse: Boolean
        get() = collapseSearch || collapseTabs

    val hideTopBar: Boolean
        get() = collapseSearch

    companion object {
        fun fromValue(value: Int): HomeHeaderCollapseMode =
            entries.find { it.value == value } ?: BOTH

        fun fromLegacyBoolean(value: Boolean): HomeHeaderCollapseMode =
            if (value) BOTH else OFF
    }
}

enum class HomeBarHideType(
    val value: Int,
    val label: String,
    val description: String
) {
    SYNC(0, "同步", "顶栏高度跟随列表滑动，上滑展开、下滑收起"),
    INSTANT(1, "即时", "识别滑动方向后，顶栏整段收起或展开");

    companion object {
        fun fromValue(value: Int): HomeBarHideType =
            entries.find { it.value == value } ?: SYNC
    }
}

enum class CommonListHeaderCollapseMode(
    val value: Int,
    val label: String,
    val description: String
) {
    ALWAYS_VISIBLE(0, "始终显示", "历史记录和收藏夹等通用列表的顶部栏保持展开"),
    SHOW_ON_REVERSE_SCROLL(1, "上滑时显示", "向下浏览时折叠，反向上滑时恢复"),
    SHOW_AT_TOP_ONLY(2, "仅回顶显示", "向下浏览时折叠，仅回到列表顶部时恢复");

    companion object {
        fun fromValue(value: Int): CommonListHeaderCollapseMode =
            entries.find { it.value == value } ?: SHOW_ON_REVERSE_SCROLL
    }
}

internal fun resolveHomeHeaderCollapseModeForTopBarHide(
    hideTopBar: Boolean
): HomeHeaderCollapseMode {
    return if (hideTopBar) HomeHeaderCollapseMode.BOTH else HomeHeaderCollapseMode.OFF
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveHomeHeaderCollapseModeForTopTabs(
    currentMode: HomeHeaderCollapseMode,
    collapseTabs: Boolean
): HomeHeaderCollapseMode {
    return resolveHomeHeaderCollapseModeForTopBarHide(currentMode.hideTopBar)
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveHomeHeaderCollapseModeForSearch(
    currentMode: HomeHeaderCollapseMode,
    collapseSearch: Boolean
): HomeHeaderCollapseMode {
    return resolveHomeHeaderCollapseModeForTopBarHide(collapseSearch)
}

internal fun resolveUiPresetPreferenceValue(rawValue: Int?): UiPreset {
    return UiPreset.fromValue(rawValue ?: UiPreset.MD3.value)
}

internal fun resolveAndroidNativeVariantPreferenceValue(rawValue: Int?): AndroidNativeVariant {
    return AndroidNativeVariant.fromValue(rawValue ?: AndroidNativeVariant.MATERIAL3.value)
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

enum class PortraitDanmakuDisplayAreaMode(val value: Int, val label: String) {
    VIDEO_VIEWPORT(0, "视频画面"),
    SCREEN_TOP(1, "屏幕顶部");

    companion object {
        fun fromValue(value: Int): PortraitDanmakuDisplayAreaMode =
            entries.find { it.value == value } ?: VIDEO_VIEWPORT
    }
}

enum class TabletCommentPanelWidthPreset(
    val value: Int,
    val label: String
) {
    COMPACT(0, "窄"),
    STANDARD(1, "标准"),
    WIDE(2, "宽"),
    ULTRA_WIDE(3, "超宽");

    companion object {
        fun fromValue(value: Int): TabletCommentPanelWidthPreset =
            entries.find { it.value == value } ?: STANDARD
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
    val fontWeight: Int = 5,
    val strokeWidth: Float = 1.5f,
    val lineHeight: Float = 1.6f,
    val scrollDurationSeconds: Float = 7.0f,
    val staticDurationSeconds: Float = 4.0f,
    val scrollFixedVelocity: Boolean = false,
    val staticDanmakuToScroll: Boolean = false,
    val massiveMode: Boolean = false,
    val mergeDuplicates: Boolean = true,
    val duplicateMergeWindowMs: Int = 500,
    val duplicateMergeCountThreshold: Int = 2,
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val allowColorful: Boolean = true,
    val allowSpecial: Boolean = true,
    val hideInteractiveCommands: Boolean = false,
    val blockAttentionCommands: Boolean = false,
    val smartOcclusion: Boolean = false,
    val portraitDisplayAreaMode: PortraitDanmakuDisplayAreaMode =
        PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT,
    val fullscreenPanelWidthMode: DanmakuPanelWidthMode = DanmakuPanelWidthMode.THIRD,
    val blockRulesRaw: String = "",
    val blockRules: List<String> = emptyList()
)

data class AppNavigationSettings(
    val bottomBarVisibilityMode: SettingsManager.BottomBarVisibilityMode = SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE,
    val orderedVisibleTabIds: List<String> = listOf("HOME", "DYNAMIC", "HISTORY", "LISTEN_VIDEO", "PROFILE"),
    val bottomBarItemColors: Map<String, Int> = emptyMap(),
    val bottomBarItemLabels: Map<String, String> = emptyMap(),
    val tabletUseSidebar: Boolean = false,
    val sidebarExpanded: Boolean = true,
    val sidebarAccountSwitcherEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val predictiveBackAnimationStyle: String = "miuix",
    val predictiveBackExitDirection: String = "always_right",
    val miuixTransitionBlurEnabled: Boolean = true,
    val miuixPredictiveBackMaxProgressPercent: Int = 100,
    val videoSharedReturnGestureFollowEnabled: Boolean = true,
)

internal data class BottomTabMigrationResult(
    val order: List<String>,
    val visible: Set<String>,
    val markComplete: Boolean
)

internal fun resolveListenVideoBottomTabMigration(
    order: List<String>,
    visible: Set<String>,
    migrationComplete: Boolean
): BottomTabMigrationResult {
    if (migrationComplete) {
        return BottomTabMigrationResult(order, visible, markComplete = false)
    }
    if ("LISTEN_VIDEO" in order || "LISTEN_VIDEO" in visible || visible.size >= 5) {
        return BottomTabMigrationResult(order, visible, markComplete = true)
    }
    val insertionIndex = order.indexOf("PROFILE").takeIf { it >= 0 } ?: order.size
    val migratedOrder = order.toMutableList().apply {
        add(insertionIndex, "LISTEN_VIDEO")
    }
    return BottomTabMigrationResult(
        order = migratedOrder,
        visible = visible + "LISTEN_VIDEO",
        markComplete = true
    )
}

data class HomeTopTabSettings(
    val orderIds: List<String> = listOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME"),
    val visibleIds: Set<String> = setOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME")
)

/**
 * 自动退出全屏策略。
 * - [OFF]：不自动退
 * - [CURRENT_PART]：当前分P/单视频结束就退（旧「开」语义中的激进行为）
 * - [ALL_PARTS]：还有下一段可连播时保持全屏，全部播完再退（默认）
 */
enum class AutoExitFullscreenMode(val value: Int, val label: String, val subtitle: String) {
    OFF(0, "关闭", "播放结束不自动退出全屏"),
    CURRENT_PART(1, "当前分P结束", "每个分P/视频播完就退出全屏"),
    ALL_PARTS(2, "全部连播结束", "合集/分P/列表全部播完再退出全屏");

    companion object {
        fun fromValue(value: Int): AutoExitFullscreenMode =
            entries.find { it.value == value } ?: ALL_PARTS

        /** 兼容旧布尔：true→全部结束再退，false→关闭 */
        fun fromLegacyEnabled(enabled: Boolean): AutoExitFullscreenMode =
            if (enabled) ALL_PARTS else OFF
    }
}

internal fun resolveAutoExitFullscreenMode(
    modeValue: Int?,
    legacyEnabled: Boolean?,
): AutoExitFullscreenMode {
    if (modeValue != null) return AutoExitFullscreenMode.fromValue(modeValue)
    return AutoExitFullscreenMode.fromLegacyEnabled(legacyEnabled ?: true)
}

data class PlayerInteractionSettings(
    val gestureSensitivity: Float = 1.0f,
    val doubleTapLikeEnabled: Boolean = true,
    val doubleTapSeekEnabled: Boolean = false,
    val portraitSwipeToFullscreenEnabled: Boolean = true,
    val centerSwipeToFullscreenEnabled: Boolean = true,
    val slideVolumeBrightnessEnabled: Boolean = true,
    val setSystemBrightnessEnabled: Boolean = false,
    val pipNoDanmakuEnabled: Boolean = false,
    val seekForwardSeconds: Int = 10,
    val seekBackwardSeconds: Int = 10,
    val inlineSwipeSeekSeconds: Int = 30,
    val fullscreenSwipeSeekSeconds: Int = 15,
    val fullscreenSwipeSeekEnabled: Boolean = true,
    val fullscreenGestureReverse: Boolean = false,
    val hideVideoPageStatusBar: Boolean = false,
    /** 竖屏详情横屏视频上下黑边动态模糊，默认开启。 */
    val portraitLetterboxAmbientHaze: Boolean = true,
    val tabletCommentPanelWidthPreset: TabletCommentPanelWidthPreset =
        TabletCommentPanelWidthPreset.STANDARD,
    val autoEnterFullscreenEnabled: Boolean = false,
    val autoExitFullscreenEnabled: Boolean = true,
    /**
     * 自动退出全屏粒度。旧布尔 [autoExitFullscreenEnabled]=true 映射为 [ALL_PARTS]，
     * 避免连播下一P 时被 STATE_ENDED 踢回竖屏。
     */
    val autoExitFullscreenMode: AutoExitFullscreenMode = AutoExitFullscreenMode.ALL_PARTS,
    val fixedFullscreenAspectRatio: FullscreenAspectRatio = FullscreenAspectRatio.FIT,
    val subtitleAutoPreference: SubtitleAutoPreference = SubtitleAutoPreference.OFF,
    val longPressSpeed: Float = 2.0f,
    val longPressSpeedLockEnabled: Boolean = false,
    val longPressSpeedLockHintShown: Boolean = false,
    /**
     * 长按倍速浮层是否显示关闭（×）按钮。默认 false（始终隐藏），
     * 不在设置页暴露，避免第二指点 × 打断长按加速。
     */
    val longPressSpeedHintCloseEnabled: Boolean = false,
    val longPressSpeedHintHidden: Boolean = false,
    val longPressSpeedHintScale: Float = 1.0f,
    val longPressSpeedHintAlpha: Float = 0.5f,
    val subtitleVerticalOffsetFraction: Float = 0.0f,
    /** Vertical offset for portrait immersive / story subtitles (independent of landscape). */
    val subtitlePortraitVerticalOffsetFraction: Float = 0.0f,
    /** Prevent player gestures from accidentally moving subtitle overlays. */
    val subtitlePositionLocked: Boolean = true,
    val twoFingerVerticalSpeedEnabled: Boolean = false,
    val twoFingerHorizontalSpeedEnabled: Boolean = false,
    val hiResLongPressCompatHintShown: Boolean = false,
    val directPortraitStoryEntry: Boolean = false,
    val launchToPortraitFeedOnStartup: Boolean = false
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

internal fun mapAppThemeSettingsFromPreferences(preferences: Preferences): AppThemeSettings {
    return SettingsManager.mapAppThemeSettingsFromPreferences(preferences)
}

internal fun mapDanmakuSettingsFromPreferences(
    preferences: Preferences,
    scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
): DanmakuSettings {
    return SettingsManager.mapDanmakuSettingsFromPreferences(preferences, scope)
}

internal fun mapAppNavigationSettingsFromPreferences(
    preferences: Preferences,
    defaultTabletUseSidebar: Boolean = false
): AppNavigationSettings {
    return SettingsManager.mapAppNavigationSettingsFromPreferences(
        preferences = preferences,
        defaultTabletUseSidebar = defaultTabletUseSidebar
    )
}

/**
 * 平板导航首启默认值：按设备类型预设（平板默认侧栏，手机默认底栏）。
 * 与 [SettingsManager.getHorizontalAdaptationEnabled] 的 isTablet 预设机制一致。
 */
internal fun defaultTabletUseSidebar(isTabletDevice: Boolean): Boolean = isTabletDevice

internal fun mapHomeTopTabSettingsFromPreferences(preferences: Preferences): HomeTopTabSettings {
    return SettingsManager.mapHomeTopTabSettingsFromPreferences(preferences)
}

internal fun mapPlayerInteractionSettingsFromPreferences(
    preferences: Preferences
): PlayerInteractionSettings {
    return SettingsManager.mapPlayerInteractionSettingsFromPreferences(preferences)
}

internal fun decodeCollectionSubscriptionIds(rawValue: String?): Set<String> {
    return rawValue
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()
}

internal fun encodeCollectionSubscriptionIds(collectionIds: Set<String>): String {
    return collectionIds
        .filter { it.isNotBlank() }
        .sorted()
        .joinToString(",")
}

internal fun toggleCollectionSubscription(
    subscribedCollectionIds: Set<String>,
    collectionId: Long
): Set<String> {
    val normalizedId = collectionId.takeIf { it > 0L }?.toString() ?: return subscribedCollectionIds
    return if (normalizedId in subscribedCollectionIds) {
        subscribedCollectionIds - normalizedId
    } else {
        subscribedCollectionIds + normalizedId
    }
}

internal fun setCollectionSubscription(
    subscribedCollectionIds: Set<String>,
    collectionId: Long,
    subscribed: Boolean
): Set<String> {
    val normalizedId = collectionId.takeIf { it > 0L }?.toString() ?: return subscribedCollectionIds
    return if (subscribed) {
        subscribedCollectionIds + normalizedId
    } else {
        subscribedCollectionIds - normalizedId
    }
}

internal fun decodeCollectionSortPreferences(rawValue: String?): Map<Long, CollectionSortMode> {
    if (rawValue.isNullOrBlank()) return emptyMap()
    return runCatching {
        Json.parseToJsonElement(rawValue).jsonObject.entries.mapNotNull { (key, value) ->
            val collectionId = key.toLongOrNull() ?: return@mapNotNull null
            val sortMode = runCatching {
                CollectionSortMode.valueOf(value.jsonPrimitive.content)
            }.getOrNull() ?: return@mapNotNull null
            collectionId to sortMode
        }.toMap()
    }.getOrDefault(emptyMap())
}

internal fun encodeCollectionSortPreferences(
    preferences: Map<Long, CollectionSortMode>
): String {
    if (preferences.isEmpty()) return "{}"
    return preferences.entries
        .sortedBy { it.key }
        .joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}"
        ) { entry ->
            "\"${entry.key}\":\"${entry.value.name}\""
        }
}

object SettingsManager {
    const val DEFAULT_AUTO_CACHE_CLEAR_THRESHOLD_GB = 5
    enum class AutoCacheClearInterval(val days: Int, val label: String) {
        NEVER(0, "从不"),
        WEEKLY(7, "每周"),
        MONTHLY(30, "每月")
    }
    // 键定义
    private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
    private val KEY_PLAYBACK_COMPLETION_BEHAVIOR = intPreferencesKey("playback_completion_behavior")
    private val KEY_HW_DECODE = booleanPreferencesKey("hw_decode")
    private val KEY_THEME_MODE = intPreferencesKey("theme_mode_v2")
    private val KEY_DARK_THEME_STYLE = intPreferencesKey("dark_theme_style_v1")
    private val KEY_APP_LANGUAGE = intPreferencesKey("app_language_v1")
    private val KEY_UI_PRESET = intPreferencesKey("ui_preset")
    private val KEY_ANDROID_NATIVE_VARIANT = intPreferencesKey("android_native_variant_v1")
    private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val KEY_MD3_COLOR_SOURCE = stringPreferencesKey("md3_color_source")
    private val KEY_MD3_CUSTOM_COLOR_HEX = stringPreferencesKey("md3_custom_color_hex")
    private val KEY_THEME_ROLE_OVERRIDES_ENABLED =
        booleanPreferencesKey("theme_role_overrides_enabled")
    private val KEY_THEME_LIGHT_BACKGROUND = stringPreferencesKey("theme_light_background")
    private val KEY_THEME_LIGHT_PRIMARY_TEXT = stringPreferencesKey("theme_light_primary_text")
    private val KEY_THEME_LIGHT_SECONDARY_TEXT = stringPreferencesKey("theme_light_secondary_text")
    private val KEY_THEME_LIGHT_CONTROL_ACCENT = stringPreferencesKey("theme_light_control_accent")
    private val KEY_THEME_DARK_BACKGROUND = stringPreferencesKey("theme_dark_background")
    private val KEY_THEME_DARK_PRIMARY_TEXT = stringPreferencesKey("theme_dark_primary_text")
    private val KEY_THEME_DARK_SECONDARY_TEXT = stringPreferencesKey("theme_dark_secondary_text")
    private val KEY_THEME_DARK_CONTROL_ACCENT = stringPreferencesKey("theme_dark_control_accent")
    private val KEY_THEME_COLOR_STYLE = stringPreferencesKey("theme_color_style")
    private val KEY_THEME_COLOR_SPEC = stringPreferencesKey("theme_color_spec")
    private val KEY_SINGLE_CHOICE_PRESENTATION =
        stringPreferencesKey("single_choice_presentation")
    private val KEY_BG_PLAY = booleanPreferencesKey("bg_play")
    //  [新增] 触感反馈 (默认开启)
    private val KEY_HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
    private val KEY_GLOBAL_TEXT_TAP_COPY_ENABLED =
        booleanPreferencesKey("global_text_tap_copy_enabled")
    //  [新增] 手势灵敏度和主题色
    private val KEY_GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
    private val KEY_SLIDE_VOLUME_BRIGHTNESS_ENABLED = booleanPreferencesKey("slide_volume_brightness_enabled")
    private val KEY_SET_SYSTEM_BRIGHTNESS = booleanPreferencesKey("set_system_brightness")
    private val KEY_PIP_NO_DANMAKU = booleanPreferencesKey("pip_no_danmaku")
    private val KEY_DANMAKU_CLOUD_SYNC_ENABLED = booleanPreferencesKey("danmaku_cloud_sync_enabled")
    private val KEY_SHOW_PLAYER_CAST_BUTTON = booleanPreferencesKey("show_player_cast_button")
    private val KEY_SHOW_VIDEO_FOLLOW_BUTTON = booleanPreferencesKey("show_video_follow_button")
    private val KEY_PLAYER_PROGRESS_PLACEMENT = intPreferencesKey("player_progress_placement")
    private val KEY_SEARCH_HOT_SECTION_ENABLED = booleanPreferencesKey("search_hot_section_enabled")
    private val KEY_SEARCH_DISCOVER_SECTION_ENABLED = booleanPreferencesKey("search_discover_section_enabled")
    //  [新增] 双击跳转秒数 (可分开设置快进和后退)
    private val KEY_DOUBLE_TAP_SEEK_ENABLED = booleanPreferencesKey("double_tap_seek_enabled")
    private val KEY_SEEK_FORWARD_SECONDS = intPreferencesKey("seek_forward_seconds")
    private val KEY_SEEK_BACKWARD_SECONDS = intPreferencesKey("seek_backward_seconds")
    //  [新增] 长按倍速 (默认 2.0x)
    private val KEY_LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")
    private val KEY_LONG_PRESS_SPEED_HINT_CLOSE_ENABLED =
        booleanPreferencesKey("long_press_speed_hint_close_enabled")
    private val KEY_LONG_PRESS_SPEED_HINT_HIDDEN =
        booleanPreferencesKey("long_press_speed_hint_hidden")
    private val KEY_LONG_PRESS_SPEED_LOCK_ENABLED =
        booleanPreferencesKey("long_press_speed_lock_enabled")
    private val KEY_LONG_PRESS_SPEED_LOCK_HINT_SHOWN =
        booleanPreferencesKey("long_press_speed_lock_hint_shown")
    private val KEY_LONG_PRESS_SPEED_HINT_SCALE =
        floatPreferencesKey("long_press_speed_hint_scale")
    private val KEY_LONG_PRESS_SPEED_HINT_ALPHA =
        floatPreferencesKey("long_press_speed_hint_alpha")
    private val KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED =
        booleanPreferencesKey("two_finger_vertical_speed_enabled")
    private val KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED =
        booleanPreferencesKey("two_finger_horizontal_speed_enabled")
    private val KEY_HI_RES_LONG_PRESS_COMPAT_HINT_SHOWN =
        booleanPreferencesKey("hi_res_long_press_compat_hint_shown")
    private val KEY_SUBTITLE_VERTICAL_OFFSET_FRACTION =
        floatPreferencesKey("subtitle_vertical_offset_fraction")
    private val KEY_SUBTITLE_PORTRAIT_VERTICAL_OFFSET_FRACTION =
        floatPreferencesKey("subtitle_portrait_vertical_offset_fraction")
    private val KEY_SUBTITLE_POSITION_LOCKED =
        booleanPreferencesKey("subtitle_position_locked")
    //  [新增] 默认播放速度/记忆上次播放速度
    private val KEY_DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
    private val KEY_REMEMBER_LAST_PLAYBACK_SPEED = booleanPreferencesKey("remember_last_playback_speed")
    private val KEY_LAST_PLAYBACK_SPEED = floatPreferencesKey("last_playback_speed")
    private val KEY_THEME_COLOR_INDEX = intPreferencesKey("theme_color_index")
    private val KEY_APP_FONT_SIZE_PRESET = intPreferencesKey("app_font_size_preset")
    private val KEY_APP_FONT_FILE_NAME = stringPreferencesKey("app_font_file_name")
    private val KEY_APP_FONT_DISPLAY_NAME = stringPreferencesKey("app_font_display_name")
    private val KEY_APP_UI_SCALE_PRESET = intPreferencesKey("app_ui_scale_preset")
    private val KEY_APP_DPI_OVERRIDE_PERCENT = intPreferencesKey("app_dpi_override_percent")
    //  [新增] 应用图标 Key (Blue, Red, Green...)
    private val KEY_APP_ICON = androidx.datastore.preferences.core.stringPreferencesKey("app_icon_key")
    private val KEY_APP_ICON_APPEARANCE = intPreferencesKey("app_icon_appearance")
    private val KEY_APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
    private val KEY_APP_LIST_ITEM_STYLE = stringPreferencesKey("app_list_item_style")
    //  [新增] 底部栏样式 (true=悬浮, false=贴底)
    private val KEY_BOTTOM_BAR_FLOATING = booleanPreferencesKey("bottom_bar_floating")
    private val KEY_NAVIGATION_ICON_CROSS_SCALE_ENABLED =
        booleanPreferencesKey("navigation_icon_cross_scale_enabled")
    //  [新增] 底栏显示模式 (0=图标+文字, 1=仅图标, 2=仅文字)
    private val KEY_BOTTOM_BAR_LABEL_MODE = intPreferencesKey("bottom_bar_label_mode")
    //  [新增] 顶部标签显示模式 (0=图标+文字, 1=仅图标, 2=仅文字)
    private val KEY_TOP_TAB_LABEL_MODE = intPreferencesKey("top_tab_label_mode")
    private val KEY_HOME_TOP_RIGHT_ACTION = intPreferencesKey("home_top_right_action")
    //  [新增] 顶部标签自定义 - 顺序和可见性
    private val KEY_TOP_TAB_ORDER = stringPreferencesKey("top_tab_order")
    private val KEY_TOP_TAB_VISIBLE_TABS = stringPreferencesKey("top_tab_visible_tabs")
    private val KEY_DYNAMIC_TAB_VISIBLE_TABS = stringPreferencesKey("dynamic_tab_visible_tabs")
    private val KEY_DYNAMIC_IMAGE_PREVIEW_TEXT_VISIBLE =
        booleanPreferencesKey("dynamic_image_preview_text_visible")
    private val KEY_DYNAMIC_ALL_TAB_HORIZONTAL_USER_LIST_VISIBLE =
        booleanPreferencesKey("dynamic_all_tab_horizontal_user_list_visible")
    private val KEY_DYNAMIC_TOP_BAR_COLLAPSE_ON_SCROLL =
        booleanPreferencesKey("dynamic_top_bar_collapse_on_scroll")
    private val KEY_LIVE_FAVORITE_TAGS = stringPreferencesKey("live_favorite_tags")
    
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
    /** 首页顶部 dock 标签数量上限。 */
    const val MAX_TOP_TABS = 5
    private const val DEFAULT_DYNAMIC_TAB_VISIBLE = "all,video,pgc,article,up"
    //  [新增] 模糊效果开关
    private val KEY_HEADER_BLUR_ENABLED = booleanPreferencesKey("header_blur_enabled")
    private val KEY_HOME_HEADER_BLUR_MODE = intPreferencesKey("home_header_blur_mode")
    private val KEY_HEADER_COLLAPSE_ENABLED = booleanPreferencesKey("header_collapse_enabled")
    private val KEY_HOME_HEADER_COLLAPSE_MODE = intPreferencesKey("home_header_collapse_mode")
    private val KEY_HOME_BAR_HIDE_TYPE = intPreferencesKey("home_bar_hide_type")
    private val KEY_SHOW_PGC_TIMELINE = booleanPreferencesKey("show_pgc_timeline")
    private val KEY_COMMON_LIST_HEADER_COLLAPSE_MODE =
        intPreferencesKey("common_list_header_collapse_mode")
    private val KEY_HOME_TOP_LAYOUT_ORDER = intPreferencesKey("home_top_layout_order")
    private val KEY_BOTTOM_BAR_BLUR_ENABLED = booleanPreferencesKey("bottom_bar_blur_enabled")
    private val KEY_TOP_BAR_LIQUID_GLASS_ENABLED = booleanPreferencesKey("top_bar_liquid_glass_enabled")
    private val KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED =
        booleanPreferencesKey("home_search_liquid_glass_enabled")
    private val KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED = booleanPreferencesKey("bottom_bar_liquid_glass_enabled")
    private val KEY_BOTTOM_BAR_SEARCH_ENABLED = booleanPreferencesKey("bottom_bar_search_enabled")
    private val KEY_BOTTOM_BAR_SEARCH_AUTO_EXPAND_MODE =
        intPreferencesKey("bottom_bar_search_auto_expand_mode")
    private val KEY_BOTTOM_BAR_SEARCH_LAYOUT_MODE =
        intPreferencesKey("bottom_bar_search_layout_mode")
    private val KEY_ANDROID_NATIVE_LIQUID_GLASS_ENABLED =
        booleanPreferencesKey("android_native_liquid_glass_enabled")
    private val KEY_LEGACY_ANDROID_NATIVE_TOP_TAB_LIQUID_GLASS_ENABLED =
        booleanPreferencesKey("android_native_top_tab_liquid_glass_enabled")
    //  Legacy shared Liquid Glass toggle, kept as migration fallback.
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
    private val KEY_HOME_FEED_CARD_WIDTH_PRESET =
        intPreferencesKey("home_feed_card_width_preset")
    private val KEY_HOME_FEED_CARD_STYLE = intPreferencesKey("home_feed_card_style")
    private val KEY_HOME_HERO_CAROUSEL_ENABLED =
        booleanPreferencesKey("home_hero_carousel_enabled")
    private val KEY_HOME_HERO_CAROUSEL_AUTOPLAY_ENABLED =
        booleanPreferencesKey("home_hero_carousel_autoplay_enabled")
    //  [新增] 卡片动画开关
    private val KEY_CARD_ANIMATION_ENABLED = booleanPreferencesKey("card_animation_enabled")
    //  [新增] 卡片过渡动画开关
    private val KEY_CARD_TRANSITION_ENABLED = booleanPreferencesKey("card_transition_enabled")
    private val KEY_RELATED_VIDEO_TRANSITION_ENABLED =
        booleanPreferencesKey("related_video_transition_enabled")
    // 实时画面转场：SDR 用 TextureView 一镜到底；HDR 仍 SurfaceView（不降画质），morph 走封面
    private val KEY_LIVE_SURFACE_CARD_TRANSITION_ENABLED =
        booleanPreferencesKey("live_surface_card_transition_enabled")
    private val KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED =
        booleanPreferencesKey("video_transition_realtime_blur_enabled")
    private val KEY_VIDEO_SHARED_TRANSITION_SPEED =
        intPreferencesKey("video_shared_transition_speed")
    private val KEY_VIDEO_SHARED_TRANSITION_CUSTOM_DURATION_MILLIS =
        intPreferencesKey("video_shared_transition_custom_duration_millis")
    //  [新增] 界面入场动画 master 开关(全 App 统一入场动效),默认开启
    private val KEY_UI_ENTRANCE_ANIMATION_ENABLED =
        booleanPreferencesKey("ui_entrance_animation_enabled")
    // [New] 运行时视觉降级守卫开关
    private val KEY_SMART_VISUAL_GUARD_ENABLED = booleanPreferencesKey("smart_visual_guard_enabled")
    // 新 key：旧 key 的 false 是「已下线」的产物，不应被迁移成「用户主动关闭」。
    private val KEY_RUNTIME_VISUAL_GUARD_ENABLED =
        booleanPreferencesKey("runtime_visual_guard_enabled")
    //  [新增] 视频卡片统计信息贴封面开关
    private val KEY_COMPACT_VIDEO_STATS_ON_COVER = booleanPreferencesKey("compact_video_stats_on_cover")
    private val KEY_LOW_QUALITY_HOME_COVER_IN_DATA_SAVER =
        booleanPreferencesKey("low_quality_home_cover_in_data_saver")
    private val KEY_HOME_COVER_GLASS_BADGES_VISIBLE = booleanPreferencesKey("home_cover_glass_badges_visible")
    private val KEY_HOME_INFO_GLASS_BADGES_VISIBLE = booleanPreferencesKey("home_info_glass_badges_visible")
    private val KEY_HOME_CARD_BADGE_EFFECT_MODE = intPreferencesKey("home_card_badge_effect_mode")
    private val KEY_HOME_CARD_INFO_GLASS_MODE = intPreferencesKey("home_card_info_glass_mode")
    private val KEY_HOME_WALLPAPER_URI = stringPreferencesKey("home_wallpaper_uri")
    private val KEY_HOME_WALLPAPER_EFFECT_MODE = intPreferencesKey("home_wallpaper_effect_mode")
    private val KEY_HOME_WALLPAPER_EFFECT_SCOPE = intPreferencesKey("home_wallpaper_effect_scope")
    private val KEY_HOME_UP_BADGES_VISIBLE = booleanPreferencesKey("home_up_badges_visible")
    private val KEY_HOME_UP_AVATARS_VISIBLE = booleanPreferencesKey("home_up_avatars_visible")
    private val KEY_FULL_VIDEO_CARD_CONTENT_VISIBLE =
        booleanPreferencesKey("full_video_card_content_visible")
    private val KEY_HOME_VIDEO_DURATION_BADGES_VISIBLE =
        booleanPreferencesKey("home_video_duration_badges_visible")
    private val KEY_HOME_DURATION_STYLE = intPreferencesKey("home_duration_style")
    //  [合并] 崩溃追踪同意弹窗
    private val KEY_CRASH_TRACKING_CONSENT_SHOWN = booleanPreferencesKey("crash_tracking_consent_shown")
    private val KEY_LIQUID_GLASS_MODE = intPreferencesKey("liquid_glass_mode")
    private val KEY_LIQUID_GLASS_STRENGTH = floatPreferencesKey("liquid_glass_strength")
    // V2 stores the continuous material position; reads migrate the complete legacy selection
    // when this key is absent so upgrades preserve the user's clear/balanced/frosted intent.
    private val KEY_LIQUID_GLASS_PROGRESS = floatPreferencesKey("liquid_glass_material_progress_v2")
    private val KEY_LIQUID_GLASS_PREVIEW_IMAGE_URI =
        stringPreferencesKey("liquid_glass_preview_image_uri")
    private val KEY_LIQUID_GLASS_ADVANCED_PRESET =
        intPreferencesKey("liquid_glass_advanced_preset")
    private val KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_RADIUS =
        floatPreferencesKey("liquid_glass_progressive_blur_radius")
    private val KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_EXTENT =
        floatPreferencesKey("liquid_glass_progressive_blur_extent")
    private val KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_CURVE =
        floatPreferencesKey("liquid_glass_progressive_blur_curve")
    private val KEY_LIQUID_GLASS_CONTENT_READABILITY =
        floatPreferencesKey("liquid_glass_content_readability")
    private val KEY_LIQUID_GLASS_CHROMATIC_ABERRATION =
        floatPreferencesKey("liquid_glass_chromatic_aberration")
    private val KEY_LIQUID_GLASS_CONTENT_DISTORTION =
        floatPreferencesKey("liquid_glass_content_distortion")
    private val KEY_PROGRESSIVE_TOP_BLUR_ENABLED =
        booleanPreferencesKey("progressive_top_blur_enabled")
    //  [新增] 底栏自定义 - 顺序和可见性
    private val KEY_BOTTOM_BAR_ORDER = stringPreferencesKey("bottom_bar_order")  // 逗号分隔的项目顺序
    private val KEY_BOTTOM_BAR_VISIBLE_TABS = stringPreferencesKey("bottom_bar_visible_tabs")  // 逗号分隔的可见项目
    private val KEY_BOTTOM_BAR_ITEM_COLORS = stringPreferencesKey("bottom_bar_item_colors")  //  格式: HOME:0,DYNAMIC:1,...
    private const val DEFAULT_BOTTOM_BAR_ORDER = "HOME,DYNAMIC,HISTORY,LISTEN_VIDEO,PROFILE"
    private const val DEFAULT_BOTTOM_BAR_VISIBLE_TABS = "HOME,DYNAMIC,HISTORY,LISTEN_VIDEO,PROFILE"
    // 评论默认排序（2=最新,3=最热）
    private val KEY_COMMENT_DEFAULT_SORT_MODE = intPreferencesKey("comment_default_sort_mode")
    private val KEY_COMMENT_FRAUD_DETECTION_ENABLED =
        booleanPreferencesKey("comment_fraud_detection_enabled")
    private val KEY_AUTO_CACHE_CLEAR_INTERVAL = intPreferencesKey("auto_cache_clear_interval_days")
    private val KEY_AUTO_CACHE_CLEAR_THRESHOLD_GB = intPreferencesKey("auto_cache_clear_threshold_gb")
    private val KEY_LAST_AUTO_CACHE_CLEAR_AT = longPreferencesKey("last_auto_cache_clear_at")
    private val KEY_COMMENT_MEMBER_DECORATIONS_ENABLED =
        booleanPreferencesKey("comment_member_decorations_enabled")
    private val KEY_SUB_REPLY_LOADED_COUNT_ENABLED =
        booleanPreferencesKey("sub_reply_loaded_count_enabled")
    private val KEY_IMAGE_PREVIEW_LONG_PRESS_SAVE_ENABLED =
        booleanPreferencesKey("image_preview_long_press_save_enabled")
    private val KEY_IMAGE_PREVIEW_3D_PAGE_ENABLED =
        booleanPreferencesKey("image_preview_3d_page_enabled")
    //  [新增] 离开播放页后停止播放（优先于小窗/画中画模式）
    private val KEY_STOP_PLAYBACK_ON_EXIT = booleanPreferencesKey("stop_playback_on_exit")
    private val KEY_BACKGROUND_PLAYBACK_ENABLED = booleanPreferencesKey("background_playback_enabled")
    private val KEY_AUDIO_FOCUS_ENABLED = booleanPreferencesKey("audio_focus_enabled")
    private val KEY_AUDIO_MODE_AUTO_PIP_ENABLED = booleanPreferencesKey("audio_mode_auto_pip_enabled")
    private val KEY_AUDIO_NOW_PLAYING_BAR_ENABLED = booleanPreferencesKey("audio_now_playing_bar_enabled")
    private val KEY_VIDEO_AI_SUMMARY_ENTRY_ENABLED = booleanPreferencesKey("video_ai_summary_entry_enabled")
    private val KEY_VIDEO_NOTE_ENABLED = booleanPreferencesKey("video_note_enabled")
    private val KEY_VIDEO_NOTE_DEFAULT_COLLAPSED = booleanPreferencesKey("video_note_default_collapsed")
    private val KEY_VIDEO_INFO_DEFAULT_EXPANDED = booleanPreferencesKey("video_info_default_expanded")
    private const val VIDEO_NOTE_CACHE_PREFS = "video_note_settings"
    private const val CACHE_KEY_VIDEO_NOTE_ENABLED = "video_note_enabled"
    private const val CACHE_KEY_VIDEO_NOTE_DEFAULT_COLLAPSED = "video_note_default_collapsed"
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
        val headerCollapseMode = preferences[KEY_HOME_HEADER_COLLAPSE_MODE]
            ?.let(HomeHeaderCollapseMode::fromValue)
            ?: HomeHeaderCollapseMode.fromLegacyBoolean(
                preferences[KEY_HEADER_COLLAPSE_ENABLED] ?: true
            )
        val legacyLiquidGlassEnabled = preferences[KEY_LIQUID_GLASS_ENABLED] ?: false
        val legacyLiquidGlassStyle = LiquidGlassStyle.fromValue(
            preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.SUKISU.value
        )
        val liquidGlassMode = preferences[KEY_LIQUID_GLASS_MODE]
            ?.let(LiquidGlassMode::fromValue)
            ?: resolveLegacyLiquidGlassMode(legacyLiquidGlassStyle)
        val liquidGlassStrength = normalizeLiquidGlassStrength(
            preferences[KEY_LIQUID_GLASS_STRENGTH]
                ?: resolveDefaultLiquidGlassStrength(liquidGlassMode)
        )
        val liquidGlassProgress = resolveStoredLiquidGlassProgress(
            progress = preferences[KEY_LIQUID_GLASS_PROGRESS],
            legacyModeValue = preferences[KEY_LIQUID_GLASS_MODE],
            legacyStrength = preferences[KEY_LIQUID_GLASS_STRENGTH],
            legacyStyleValue = preferences[KEY_LIQUID_GLASS_STYLE],
        )
        val liquidGlassReadabilityMode = LiquidGlassReadabilityMode.fromValue(
            preferences[liquidGlassReadabilityModePreferencesKey]
                ?: LiquidGlassReadabilityMode.STABLE.value
        )
        val liquidGlassAdvancedSettings = resolveLiquidGlassAdvancedSettings(
            presetValue = preferences[KEY_LIQUID_GLASS_ADVANCED_PRESET],
            progressiveBlurRadius = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_RADIUS],
            progressiveBlurExtent = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_EXTENT],
            progressiveBlurCurve = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_CURVE],
            contentReadability = preferences[KEY_LIQUID_GLASS_CONTENT_READABILITY],
            chromaticAberration = preferences[KEY_LIQUID_GLASS_CHROMATIC_ABERRATION],
            contentDistortion = preferences[KEY_LIQUID_GLASS_CONTENT_DISTORTION],
        )
        return HomeSettings(
            displayMode = preferences[KEY_DISPLAY_MODE] ?: 0,
            isBottomBarFloating = preferences[KEY_BOTTOM_BAR_FLOATING] ?: true,
            navigationIconCrossScaleEnabled =
                preferences[KEY_NAVIGATION_ICON_CROSS_SCALE_ENABLED] ?: true,
            bottomBarLabelMode = preferences[KEY_BOTTOM_BAR_LABEL_MODE] ?: BottomBarLabelMode.ICON_AND_TEXT,
            topTabLabelMode = preferences[KEY_TOP_TAB_LABEL_MODE] ?: TopTabLabelMode.TEXT_ONLY,
            homeTopRightAction = HomeTopRightAction.fromValue(
                preferences[KEY_HOME_TOP_RIGHT_ACTION] ?: HomeTopRightAction.SETTINGS.value
            ),
            homeTopLayoutOrder = HomeTopLayoutOrder.fromValue(
                preferences[KEY_HOME_TOP_LAYOUT_ORDER] ?: HomeTopLayoutOrder.SEARCH_THEN_TABS.value
            ),
            isHeaderBlurEnabled = headerBlurMode != HomeHeaderBlurMode.ALWAYS_OFF,
            headerBlurMode = headerBlurMode,
            isBottomBarBlurEnabled = preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] ?: false,
            isTopBarLiquidGlassEnabled = preferences[KEY_TOP_BAR_LIQUID_GLASS_ENABLED] ?: false,
            isHomeSearchLiquidGlassEnabled =
                preferences[KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED]
                    ?: (preferences[KEY_TOP_BAR_LIQUID_GLASS_ENABLED] ?: false),
            isBottomBarLiquidGlassEnabled = preferences[KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED] ?: legacyLiquidGlassEnabled,
            isBottomBarSearchEnabled = preferences[KEY_BOTTOM_BAR_SEARCH_ENABLED] ?: false,
            bottomBarSearchAutoExpandMode = BottomBarSearchAutoExpandMode.fromValue(
                preferences[KEY_BOTTOM_BAR_SEARCH_AUTO_EXPAND_MODE]
                    ?: BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP.value
            ),
            bottomBarSearchLayoutMode = BottomBarSearchLayoutMode.fromValue(
                preferences[KEY_BOTTOM_BAR_SEARCH_LAYOUT_MODE]
                    ?: BottomBarSearchLayoutMode.FULL_DOCK.value
            ),
            androidNativeLiquidGlassEnabled =
                preferences[KEY_ANDROID_NATIVE_LIQUID_GLASS_ENABLED]
                    ?: true,
            liquidGlassStyle = legacyLiquidGlassStyle,
            liquidGlassMode = liquidGlassMode,
            liquidGlassStrength = liquidGlassStrength,
            liquidGlassProgress = liquidGlassProgress,
            liquidGlassReadabilityMode = liquidGlassReadabilityMode,
            liquidGlassAdvancedSettings = liquidGlassAdvancedSettings,
            homeHeaderCollapseMode = headerCollapseMode,
            homeBarHideType = HomeBarHideType.fromValue(
                preferences[KEY_HOME_BAR_HIDE_TYPE] ?: HomeBarHideType.SYNC.value
            ),
            commonListHeaderCollapseMode = CommonListHeaderCollapseMode.fromValue(
                preferences[KEY_COMMON_LIST_HEADER_COLLAPSE_MODE]
                    ?: CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL.value
            ),
            isHeaderCollapseEnabled = headerCollapseMode.hasAnyCollapse,
            showPgcTimeline = preferences[KEY_SHOW_PGC_TIMELINE] ?: true,
            gridColumnCount = preferences[KEY_GRID_COLUMN_COUNT] ?: 0,
            homeFeedCardWidthPreset = HomeFeedCardWidthPreset.fromValue(
                preferences[KEY_HOME_FEED_CARD_WIDTH_PRESET] ?: HomeFeedCardWidthPreset.AUTO.value
            ),
            homeFeedCardStyle = HomeFeedCardStyle.fromValue(
                preferences[KEY_HOME_FEED_CARD_STYLE] ?: HomeFeedCardStyle.BILIPAI.value
            ),
            homeHeroCarouselEnabled = preferences[KEY_HOME_HERO_CAROUSEL_ENABLED] ?: true,
            homeHeroCarouselAutoplayEnabled =
                preferences[KEY_HOME_HERO_CAROUSEL_AUTOPLAY_ENABLED] ?: false,
            cardAnimationEnabled = preferences[KEY_CARD_ANIMATION_ENABLED] ?: false,
            cardTransitionEnabled = preferences[KEY_CARD_TRANSITION_ENABLED] ?: true,
            videoSharedTransitionSpeed = VideoSharedTransitionSpeed.fromValue(
                preferences[KEY_VIDEO_SHARED_TRANSITION_SPEED]
                    ?: VideoSharedTransitionSpeed.STANDARD.value
            ),
            videoSharedTransitionCustomDurationMillis =
                normalizeVideoSharedTransitionCustomDurationMillis(
                    preferences[KEY_VIDEO_SHARED_TRANSITION_CUSTOM_DURATION_MILLIS]
                        ?: VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS
                ),
            smartVisualGuardEnabled = false,
            runtimeVisualGuardEnabled =
                preferences[KEY_RUNTIME_VISUAL_GUARD_ENABLED] ?: true,
            compactVideoStatsOnCover = preferences[KEY_COMPACT_VIDEO_STATS_ON_COVER] ?: false,
            lowQualityHomeCoverInDataSaver =
                preferences[KEY_LOW_QUALITY_HOME_COVER_IN_DATA_SAVER] ?: false,
            // 已下线：忽略旧数据，确保历史上开启过实时模糊/液态玻璃的用户不会继续走该路径。
            showHomeCoverGlassBadges = false,
            showHomeInfoGlassBadges = false,
            homeCardBadgeEffectMode = HomeCardBadgeEffectMode.OFF,
            homeCardInfoGlassMode = HomeCardInfoGlassMode.OFF,
            homeWallpaperEffectMode = HomeWallpaperEffectMode.fromValue(
                preferences[KEY_HOME_WALLPAPER_EFFECT_MODE] ?: HomeWallpaperEffectMode.SOFT_BLUR.value
            ),
            homeWallpaperEffectScope = HomeWallpaperEffectScope.fromValue(
                preferences[KEY_HOME_WALLPAPER_EFFECT_SCOPE] ?: HomeWallpaperEffectScope.HOME_ONLY.value
            ),
            showHomeUpBadges = preferences[KEY_HOME_UP_BADGES_VISIBLE] ?: false,
            showHomeUpAvatars = preferences[KEY_HOME_UP_AVATARS_VISIBLE] ?: false,
            showFullVideoCardContent = preferences[KEY_FULL_VIDEO_CARD_CONTENT_VISIBLE] ?: false,
            homeDurationStyle = preferences[KEY_HOME_DURATION_STYLE]
                ?.let(HomeDurationStyle::fromValue)
                ?: if (preferences[KEY_HOME_VIDEO_DURATION_BADGES_VISIBLE] ?: true) {
                    HomeDurationStyle.OUTSIDE_COVER
                } else {
                    HomeDurationStyle.HIDDEN
                },
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
        // 旧版本可能保存超过上限的可见标签：按用户顺序裁剪到 MAX_TOP_TABS，
        // 保证运行时展示与设置界面上限一致。
        val cappedVisibleIds = if (visibleIds.size <= MAX_TOP_TABS) {
            visibleIds
        } else {
            orderIds.filter { it in visibleIds }.take(MAX_TOP_TABS).toSet()
        }
        return HomeTopTabSettings(
            orderIds = orderIds,
            visibleIds = cappedVisibleIds
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
            doubleTapSeekEnabled = preferences[KEY_DOUBLE_TAP_SEEK_ENABLED] ?: false,
            portraitSwipeToFullscreenEnabled = preferences[KEY_PORTRAIT_SWIPE_TO_FULLSCREEN] ?: true,
            centerSwipeToFullscreenEnabled = preferences[KEY_CENTER_SWIPE_TO_FULLSCREEN] ?: true,
            slideVolumeBrightnessEnabled = preferences[KEY_SLIDE_VOLUME_BRIGHTNESS_ENABLED] ?: true,
            setSystemBrightnessEnabled = preferences[KEY_SET_SYSTEM_BRIGHTNESS] ?: false,
            pipNoDanmakuEnabled = preferences[KEY_PIP_NO_DANMAKU] ?: false,
            seekForwardSeconds = (preferences[KEY_SEEK_FORWARD_SECONDS] ?: 10).coerceIn(1, 60),
            seekBackwardSeconds = (preferences[KEY_SEEK_BACKWARD_SECONDS] ?: 10).coerceIn(1, 60),
            inlineSwipeSeekSeconds = normalizeInlineSwipeSeekSeconds(
                preferences[KEY_INLINE_SWIPE_SEEK_SECONDS] ?: 30
            ),
            fullscreenSwipeSeekSeconds = normalizeFullscreenSwipeSeekSeconds(
                preferences[KEY_FULLSCREEN_SWIPE_SEEK_SECONDS] ?: 15
            ),
            fullscreenSwipeSeekEnabled = preferences[KEY_FULLSCREEN_SWIPE_SEEK_ENABLED] ?: true,
            fullscreenGestureReverse = preferences[KEY_FULLSCREEN_GESTURE_REVERSE] ?: false,
            hideVideoPageStatusBar = preferences[KEY_HIDE_VIDEO_PAGE_STATUS_BAR] ?: false,
            portraitLetterboxAmbientHaze =
                preferences[KEY_PORTRAIT_LETTERBOX_AMBIENT_HAZE] ?: true,
            tabletCommentPanelWidthPreset = TabletCommentPanelWidthPreset.fromValue(
                preferences[KEY_TABLET_COMMENT_PANEL_WIDTH_PRESET]
                    ?: TabletCommentPanelWidthPreset.STANDARD.value
            ),
            autoEnterFullscreenEnabled = preferences[KEY_AUTO_ENTER_FULLSCREEN] ?: false,
            autoExitFullscreenEnabled = preferences[KEY_AUTO_EXIT_FULLSCREEN] ?: true,
            autoExitFullscreenMode = resolveAutoExitFullscreenMode(
                modeValue = preferences[KEY_AUTO_EXIT_FULLSCREEN_MODE],
                legacyEnabled = preferences[KEY_AUTO_EXIT_FULLSCREEN],
            ),
            fixedFullscreenAspectRatio = FullscreenAspectRatio.fromValue(
                preferences[KEY_FULLSCREEN_ASPECT_RATIO] ?: FullscreenAspectRatio.FIT.value
            ),
            subtitleAutoPreference = SubtitleAutoPreference.entries.getOrElse(
                preferences[KEY_SUBTITLE_AUTO_PREFERENCE] ?: SubtitleAutoPreference.OFF.ordinal
            ) { SubtitleAutoPreference.OFF },
            longPressSpeed = normalizeLongPressSpeed(
                preferences[KEY_LONG_PRESS_SPEED] ?: DEFAULT_LONG_PRESS_SPEED
            ),
            longPressSpeedLockEnabled = preferences[KEY_LONG_PRESS_SPEED_LOCK_ENABLED] ?: false,
            longPressSpeedLockHintShown = preferences[KEY_LONG_PRESS_SPEED_LOCK_HINT_SHOWN] ?: false,
            longPressSpeedHintCloseEnabled =
                preferences[KEY_LONG_PRESS_SPEED_HINT_CLOSE_ENABLED] ?: false,
            longPressSpeedHintHidden = preferences[KEY_LONG_PRESS_SPEED_HINT_HIDDEN] ?: false,
            longPressSpeedHintScale = normalizeLongPressSpeedHintScale(
                preferences[KEY_LONG_PRESS_SPEED_HINT_SCALE] ?: LONG_PRESS_SPEED_HINT_DEFAULT_SCALE
            ),
            longPressSpeedHintAlpha = normalizeLongPressSpeedHintAlpha(
                preferences[KEY_LONG_PRESS_SPEED_HINT_ALPHA] ?: LONG_PRESS_SPEED_HINT_DEFAULT_ALPHA
            ),
            subtitleVerticalOffsetFraction = normalizeSubtitleVerticalOffsetFraction(
                preferences[KEY_SUBTITLE_VERTICAL_OFFSET_FRACTION] ?: 0.0f
            ),
            subtitlePortraitVerticalOffsetFraction = normalizeSubtitleVerticalOffsetFraction(
                preferences[KEY_SUBTITLE_PORTRAIT_VERTICAL_OFFSET_FRACTION] ?: 0.0f
            ),
            subtitlePositionLocked = preferences[KEY_SUBTITLE_POSITION_LOCKED] ?: true,
            twoFingerVerticalSpeedEnabled = preferences[KEY_TWO_FINGER_VERTICAL_SPEED_ENABLED] ?: false,
            twoFingerHorizontalSpeedEnabled = preferences[KEY_TWO_FINGER_HORIZONTAL_SPEED_ENABLED] ?: false,
            hiResLongPressCompatHintShown = preferences[KEY_HI_RES_LONG_PRESS_COMPAT_HINT_SHOWN] ?: false,
            directPortraitStoryEntry = preferences[KEY_AUTO_PORTRAIT_FULLSCREEN] ?: false,
            launchToPortraitFeedOnStartup = preferences[KEY_LAUNCH_TO_PORTRAIT_FEED_ON_STARTUP] ?: false
        )
    }

    fun getPlayerInteractionSettings(context: Context): Flow<PlayerInteractionSettings> =
        context.settingsDataStore.data
            .map(::mapPlayerInteractionSettingsFromPreferences)
            .distinctUntilChanged()

    fun getPlayerControlVisibilitySettings(
        context: Context
    ): Flow<PlayerControlVisibilitySettings> = context.settingsDataStore.data
        .map { preferences ->
            PlayerControlVisibilitySettings(
                showCastButton = preferences[KEY_SHOW_PLAYER_CAST_BUTTON] ?: true,
                showFollowButton = preferences[KEY_SHOW_VIDEO_FOLLOW_BUTTON] ?: true
            )
        }
        .distinctUntilChanged()

    suspend fun setShowPlayerCastButton(context: Context, visible: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_PLAYER_CAST_BUTTON] = visible }
    }

    suspend fun setShowVideoFollowButton(context: Context, visible: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_VIDEO_FOLLOW_BUTTON] = visible }
    }

    fun getPlayerProgressPlacement(context: Context): Flow<PlayerProgressPlacement> =
        context.settingsDataStore.data.map { preferences ->
            PlayerProgressPlacement.fromValue(
                preferences[KEY_PLAYER_PROGRESS_PLACEMENT]
                    ?: PlayerProgressPlacement.ABOVE_CONTROLS.value
            )
        }.distinctUntilChanged()

    suspend fun setPlayerProgressPlacement(
        context: Context,
        placement: PlayerProgressPlacement
    ) {
        context.settingsDataStore.edit { it[KEY_PLAYER_PROGRESS_PLACEMENT] = placement.value }
    }

    // --- Auto Play on Enter (Click to Play) ---
    private val KEY_CLICK_TO_PLAY = booleanPreferencesKey("click_to_play")
    private val KEY_RESUME_PLAYBACK_PROMPT_ENABLED = booleanPreferencesKey("resume_playback_prompt_enabled")
    private val KEY_SPACE_PLAYED_VIDEO_LOCATE_PROMPT_ENABLED =
        booleanPreferencesKey("space_played_video_locate_prompt_enabled")
    private const val RESUME_PROMPT_CACHE_PREFS = "resume_prompt_cache"
    private const val CACHE_KEY_RESUME_PROMPT_ENABLED = "resume_prompt_enabled"
    private const val CACHE_KEY_RESUME_PROMPT_SHOWN = "resume_prompt_shown"
    private const val HI_RES_LONG_PRESS_HINT_CACHE_PREFS = "hi_res_long_press_hint_cache"
    private const val CACHE_KEY_HI_RES_LONG_PRESS_HINT_SHOWN = "hi_res_long_press_hint_shown"
    private const val LONG_PRESS_SPEED_LOCK_CACHE_PREFS = "long_press_speed_lock_cache"
    private const val CACHE_KEY_LONG_PRESS_SPEED_LOCK_ENABLED = "long_press_speed_lock_enabled"
    private const val CACHE_KEY_LONG_PRESS_SPEED_LOCK_HINT_SHOWN = "long_press_speed_lock_hint_shown"
    private const val CACHE_KEY_LONG_PRESS_SPEED_HINT_CLOSE_ENABLED =
        "long_press_speed_hint_close_enabled"
    private const val CACHE_KEY_LONG_PRESS_SPEED_HINT_HIDDEN = "long_press_speed_hint_hidden"
    private const val CACHE_KEY_LONG_PRESS_SPEED_HINT_SCALE = "long_press_speed_hint_scale"
    private const val CACHE_KEY_LONG_PRESS_SPEED_HINT_ALPHA = "long_press_speed_hint_alpha"
    private const val VIDEO_PAGE_STATUS_BAR_CACHE_PREFS = "video_page_status_bar_cache"
    private const val CACHE_KEY_HIDE_VIDEO_PAGE_STATUS_BAR = "hide_video_page_status_bar"

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

    fun getSpacePlayedVideoLocatePromptEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_SPACE_PLAYED_VIDEO_LOCATE_PROMPT_ENABLED] ?: true }

    suspend fun setSpacePlayedVideoLocatePromptEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SPACE_PLAYED_VIDEO_LOCATE_PROMPT_ENABLED] = enabled
        }
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
    private const val CACHE_KEY_PLAYBACK_COMPLETION_BEHAVIOR = "playback_completion_behavior"

    @Volatile
    private var playbackCompletionBehaviorMemoryCache: Int? = null

    fun getAutoPlay(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_PLAY] ?: true }

    suspend fun setAutoPlay(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_PLAY] = value }
        // 🔧 [修复] 同步到 SharedPreferences，供同步读取使用
        context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
            .edit().putBoolean("auto_play_enabled", value).apply()
    }
    
    // 🔧 [修复] 同步读取自动播放设置（用于 VideoPlaybackViewModel）
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
        context.settingsDataStore.data
            .map { preferences ->
                val value = preferences[KEY_PLAYBACK_COMPLETION_BEHAVIOR]
                    ?: PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC.value
                PlaybackCompletionBehavior.fromValue(value)
            }
            .onEach { behavior ->
                // Flow（UI）与 Sync（播完回调）对齐：缓存 + 回写 SP，修复「界面顺序播放、实际单循」.
                rememberPlaybackCompletionBehavior(behavior)
                healPlaybackCompletionSharedPreferences(context, behavior)
            }
            .distinctUntilChanged()

    suspend fun setPlaybackCompletionBehavior(
        context: Context,
        behavior: PlaybackCompletionBehavior
    ) {
        rememberPlaybackCompletionBehavior(behavior)
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PLAYBACK_COMPLETION_BEHAVIOR] = behavior.value
        }
        healPlaybackCompletionSharedPreferences(context, behavior)
    }

    fun getPlaybackCompletionBehaviorSync(context: Context): PlaybackCompletionBehavior {
        val prefs = context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
        val sharedPreferencesValue = if (prefs.contains(CACHE_KEY_PLAYBACK_COMPLETION_BEHAVIOR)) {
            prefs.getInt(
                CACHE_KEY_PLAYBACK_COMPLETION_BEHAVIOR,
                PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC.value
            )
        } else {
            null
        }
        return resolvePlaybackCompletionBehaviorSyncSource(
            memoryCacheValue = playbackCompletionBehaviorMemoryCache,
            sharedPreferencesValue = sharedPreferencesValue,
        )
    }

    private fun rememberPlaybackCompletionBehavior(behavior: PlaybackCompletionBehavior) {
        playbackCompletionBehaviorMemoryCache = behavior.value
    }

    private fun healPlaybackCompletionSharedPreferences(
        context: Context,
        behavior: PlaybackCompletionBehavior,
    ) {
        val prefs = context.getSharedPreferences("auto_play_cache", Context.MODE_PRIVATE)
        val current = if (prefs.contains(CACHE_KEY_PLAYBACK_COMPLETION_BEHAVIOR)) {
            prefs.getInt(
                CACHE_KEY_PLAYBACK_COMPLETION_BEHAVIOR,
                PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC.value
            )
        } else {
            null
        }
        if (!shouldHealPlaybackCompletionSharedPreferences(behavior.value, current)) {
            return
        }
        prefs.edit()
            .putInt(CACHE_KEY_PLAYBACK_COMPLETION_BEHAVIOR, behavior.value)
            .apply()
    }

    // --- HW Decode ---
    fun getHwDecode(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HW_DECODE] ?: true }

    suspend fun setHwDecode(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_HW_DECODE] = value }
        // 同步播放器创建路径的内存/SharedPreferences 缓存，避免切换后仍按旧解码设置建播放器。
        PlayerSettingsCache.setHwDecodeEnabled(context, value)
    }

    internal fun mapAppThemeSettingsFromPreferences(preferences: Preferences): AppThemeSettings {
        val rawDpiOverride = preferences[KEY_APP_DPI_OVERRIDE_PERCENT] ?: 0
        val defaultRoleOverrides = ThemeRoleOverrides()
        // 两值运行时模型：优先新键；缺失时回退旧键解析并归一化。
        // 新用户缺失主题键默认 Material 3；历史/非法组合由迁移表兼容为 MIUIX。
        val uiStyle = resolveThemeSelectionFromPreferences(
            preferences,
            KEY_UI_PRESET,
            KEY_ANDROID_NATIVE_VARIANT
        )
        return AppThemeSettings(
            uiStyle = uiStyle,
            themeMode = resolveThemeModePreference(
                preferences[KEY_THEME_MODE] ?: AppThemeMode.FOLLOW_SYSTEM.value
            ),
            darkThemeStyle = resolveDarkThemeStylePreference(
                darkThemeStyleValue = preferences[KEY_DARK_THEME_STYLE],
                legacyThemeModeValue = preferences[KEY_THEME_MODE]
            ),
            appLanguage = resolveAppLanguagePreference(preferences[KEY_APP_LANGUAGE]),
            md3ColorSource = resolveMd3ColorSourcePreference(
                sourceValue = preferences[KEY_MD3_COLOR_SOURCE],
                legacyDynamicColorEnabled = preferences[KEY_DYNAMIC_COLOR]
            ),
            md3CustomColorHex = normalizeMd3CustomColorHex(preferences[KEY_MD3_CUSTOM_COLOR_HEX]),
            themeRoleOverrides = ThemeRoleOverrides(
                enabled = preferences[KEY_THEME_ROLE_OVERRIDES_ENABLED] ?: false,
                light = ThemeModeRoleOverrides(
                    backgroundHex = normalizeMd3CustomColorHex(
                        preferences[KEY_THEME_LIGHT_BACKGROUND],
                        defaultRoleOverrides.light.backgroundHex
                    ),
                    primaryTextHex = normalizeMd3CustomColorHex(
                        preferences[KEY_THEME_LIGHT_PRIMARY_TEXT],
                        defaultRoleOverrides.light.primaryTextHex
                    ),
                    secondaryTextHex = normalizeMd3CustomColorHex(
                        preferences[KEY_THEME_LIGHT_SECONDARY_TEXT],
                        defaultRoleOverrides.light.secondaryTextHex
                    ),
                    controlAccentHex = normalizeMd3CustomColorHex(
                        preferences[KEY_THEME_LIGHT_CONTROL_ACCENT],
                        defaultRoleOverrides.light.controlAccentHex
                    )
                ),
                dark = ThemeModeRoleOverrides(
                    backgroundHex = normalizeMd3CustomColorHex(
                        preferences[KEY_THEME_DARK_BACKGROUND],
                        defaultRoleOverrides.dark.backgroundHex
                    ),
                    primaryTextHex = normalizeMd3CustomColorHex(
                        preferences[KEY_THEME_DARK_PRIMARY_TEXT],
                        defaultRoleOverrides.dark.primaryTextHex
                    ),
                    secondaryTextHex = normalizeMd3CustomColorHex(
                        preferences[KEY_THEME_DARK_SECONDARY_TEXT],
                        defaultRoleOverrides.dark.secondaryTextHex
                    ),
                    controlAccentHex = normalizeMd3CustomColorHex(
                        preferences[KEY_THEME_DARK_CONTROL_ACCENT],
                        defaultRoleOverrides.dark.controlAccentHex
                    )
                )
            ),
            colorStyle = resolvePaletteStylePreference(preferences[KEY_THEME_COLOR_STYLE]),
            colorSpec = resolveColorSpecPreference(preferences[KEY_THEME_COLOR_SPEC]),
            themeColorIndex = normalizeThemeColorIndex(preferences[KEY_THEME_COLOR_INDEX] ?: 0),
            appFontSizePreset = AppFontSizePreset.fromValue(
                preferences[KEY_APP_FONT_SIZE_PRESET] ?: AppFontSizePreset.DEFAULT.value
            ),
            appFontFileName = preferences[KEY_APP_FONT_FILE_NAME].orEmpty(),
            appUiScalePreset = AppUiScalePreset.fromValue(
                preferences[KEY_APP_UI_SCALE_PRESET] ?: AppUiScalePreset.STANDARD.value
            ),
            appDpiOverridePercent = if (rawDpiOverride == 0) {
                0
            } else {
                rawDpiOverride.coerceIn(85, 115)
            },
            appGestureScreenshotEnabled = preferences[KEY_APP_GESTURE_SCREENSHOT_ENABLED] ?: false,
            appScreenshotGestureMode = AppScreenshotGestureMode.fromValue(
                preferences[KEY_APP_SCREENSHOT_GESTURE_MODE]
                    ?: AppScreenshotGestureMode.TOP_RIGHT_TWO_FINGER_LONG_PRESS.value
            ),
            appScreenshotCaptureMode = AppScreenshotCaptureMode.fromValue(
                preferences[KEY_APP_SCREENSHOT_CAPTURE_MODE]
                    ?: AppScreenshotCaptureMode.FULL_WINDOW.value
            ),
            singleChoicePresentation = AppSingleChoicePresentation.fromStorageValue(
                preferences[KEY_SINGLE_CHOICE_PRESENTATION]
            ),
            appIconStyle = resolveAppIconStylePreference(preferences[KEY_APP_ICON_STYLE]),
            appListItemStyle = resolveAppListItemStylePreference(preferences[KEY_APP_LIST_ITEM_STYLE])
        )
    }

    fun getAppThemeSettings(context: Context): Flow<AppThemeSettings> = flow {
        ensureThemeSelectionMigrated(context, KEY_UI_PRESET, KEY_ANDROID_NATIVE_VARIANT)
        emitAll(
            context.settingsDataStore.data
                .map(::mapAppThemeSettingsFromPreferences)
                .distinctUntilChanged()
        )
    }

    fun getInitialAppThemeSettings(context: Context): AppThemeSettings {
        return AppThemeSettings(
            appLanguage = getAppLanguageSync(context)
        )
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
        val success = editSettingsAndCommitPrefs(
            context, "theme_cache",
            editSettings = {
                resolvedDarkThemeStyle = resolveDarkThemeStylePreference(
                    darkThemeStyleValue = this[KEY_DARK_THEME_STYLE],
                    legacyThemeModeValue = this[KEY_THEME_MODE]
                )
                if (this[KEY_DARK_THEME_STYLE] == null) {
                    this[KEY_DARK_THEME_STYLE] = resolvedDarkThemeStyle.value
                }
                this[KEY_THEME_MODE] = mode.value
            },
            editPrefs = {
                putInt("theme_mode", mode.value)
                putInt("dark_theme_style", resolvedDarkThemeStyle.value)
            }
        )
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
        editSettingsAndCommitPrefs(
            context, "theme_cache",
            editSettings = { this[KEY_APP_LANGUAGE] = appLanguage.value },
            editPrefs = { putInt("app_language", appLanguage.value) },
        )
    }

    suspend fun setAppIconStyle(context: Context, style: AppIconStyle) {
        editSettingsAndCommitPrefs(
            context, "theme_cache",
            editSettings = { this[KEY_APP_ICON_STYLE] = style.name },
            editPrefs = { putString("app_icon_style", style.name) },
        )
    }

    suspend fun setAppListItemStyle(context: Context, style: AppListItemStyle) {
        editSettingsAndCommitPrefs(
            context, "theme_cache",
            editSettings = { this[KEY_APP_LIST_ITEM_STYLE] = style.name },
            editPrefs = { putString("app_list_item_style", style.name) },
        )
    }

    suspend fun setSingleChoicePresentation(
        context: Context,
        presentation: AppSingleChoicePresentation,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SINGLE_CHOICE_PRESENTATION] = presentation.storageValue
        }
    }

    fun getAppLanguageSync(context: Context): AppLanguage {
        val rawValue = context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE)
            .getInt("app_language", AppLanguage.FOLLOW_SYSTEM.value)
        return resolveAppLanguagePreference(rawValue)
    }

    fun getAppIconStyle(context: Context): Flow<AppIconStyle> =
        context.settingsDataStore.data.map { preferences ->
            resolveAppIconStylePreference(preferences[KEY_APP_ICON_STYLE])
        }

    fun getAppListItemStyle(context: Context): Flow<AppListItemStyle> =
        context.settingsDataStore.data.map { preferences ->
            resolveAppListItemStylePreference(preferences[KEY_APP_LIST_ITEM_STYLE])
        }

    fun getSingleChoicePresentation(context: Context): Flow<AppSingleChoicePresentation> =
        context.settingsDataStore.data.map { preferences ->
            AppSingleChoicePresentation.fromStorageValue(
                preferences[KEY_SINGLE_CHOICE_PRESENTATION]
            )
        }

    suspend fun setDarkThemeStyle(context: Context, style: DarkThemeStyle) {
        val success = editSettingsAndCommitPrefs(
            context, "theme_cache",
            editSettings = { this[KEY_DARK_THEME_STYLE] = style.value },
            editPrefs = { putInt("dark_theme_style", style.value) },
        )
        com.android.purebilibili.core.util.Logger.d(
            "SettingsManager",
            " Dark theme style saved: ${style.value} (${style.label}), success=$success"
        )
    }

    fun getUiStyle(context: Context): Flow<AppUiStyle> = flow {
        ensureThemeSelectionMigrated(context, KEY_UI_PRESET, KEY_ANDROID_NATIVE_VARIANT)
        emitAll(
            context.settingsDataStore.data
                .map { preferences ->
                    resolveThemeSelectionFromPreferences(
                        preferences,
                        KEY_UI_PRESET,
                        KEY_ANDROID_NATIVE_VARIANT
                    )
                }
                .distinctUntilChanged()
        )
    }

    suspend fun setUiStyle(context: Context, uiStyle: AppUiStyle) {
        // 只写新稳定键，不再双写旧键；两值模型不存在非法运行时值。
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_THEME_SELECTION] = uiStyle.name
            preferences.remove(KEY_UI_PRESET)
            preferences.remove(KEY_ANDROID_NATIVE_VARIANT)
        }
    }

    // --- Dynamic Color ---
    fun getDynamicColor(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            resolveMd3ColorSourcePreference(
                sourceValue = preferences[KEY_MD3_COLOR_SOURCE],
                legacyDynamicColorEnabled = preferences[KEY_DYNAMIC_COLOR]
            ) == Md3ColorSource.FOLLOW_WALLPAPER
        }

    suspend fun setDynamicColor(context: Context, value: Boolean) {
        setMd3ColorSource(
            context = context,
            source = if (value) Md3ColorSource.FOLLOW_WALLPAPER else Md3ColorSource.CUSTOM
        )
    }

    fun getMd3ColorSource(context: Context): Flow<Md3ColorSource> = context.settingsDataStore.data
        .map { preferences ->
            resolveMd3ColorSourcePreference(
                sourceValue = preferences[KEY_MD3_COLOR_SOURCE],
                legacyDynamicColorEnabled = preferences[KEY_DYNAMIC_COLOR]
            )
        }

    suspend fun setMd3ColorSource(context: Context, source: Md3ColorSource) = context.settingsDataStore.edit { preferences ->
        preferences[KEY_MD3_COLOR_SOURCE] = source.name
        // 保持旧 key 同步，避免旧入口或导入旧配置时出现来源状态不一致。
        preferences[KEY_DYNAMIC_COLOR] = source == Md3ColorSource.FOLLOW_WALLPAPER
    }

    fun getMd3CustomColorHex(context: Context): Flow<String> = context.settingsDataStore.data.map {
        normalizeMd3CustomColorHex(it[KEY_MD3_CUSTOM_COLOR_HEX])
    }

    suspend fun setMd3CustomColorHex(context: Context, hex: String) = context.settingsDataStore.edit { preferences ->
        preferences[KEY_MD3_CUSTOM_COLOR_HEX] = normalizeMd3CustomColorHex(hex)
    }

    suspend fun applyMd3CustomColor(context: Context, hex: String) = context.settingsDataStore.edit { preferences ->
        preferences[KEY_MD3_COLOR_SOURCE] = Md3ColorSource.CUSTOM.name
        preferences[KEY_DYNAMIC_COLOR] = false
        preferences[KEY_MD3_CUSTOM_COLOR_HEX] = normalizeMd3CustomColorHex(hex)
    }

    fun getThemeRoleOverrides(context: Context): Flow<ThemeRoleOverrides> =
        context.settingsDataStore.data.map { preferences ->
            mapAppThemeSettingsFromPreferences(preferences).themeRoleOverrides
        }

    suspend fun setThemeRoleOverrides(context: Context, overrides: ThemeRoleOverrides) {
        val defaults = ThemeRoleOverrides()
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_THEME_ROLE_OVERRIDES_ENABLED] = overrides.enabled
            preferences[KEY_THEME_LIGHT_BACKGROUND] = normalizeMd3CustomColorHex(
                overrides.light.backgroundHex,
                defaults.light.backgroundHex
            )
            preferences[KEY_THEME_LIGHT_PRIMARY_TEXT] = normalizeMd3CustomColorHex(
                overrides.light.primaryTextHex,
                defaults.light.primaryTextHex
            )
            preferences[KEY_THEME_LIGHT_SECONDARY_TEXT] = normalizeMd3CustomColorHex(
                overrides.light.secondaryTextHex,
                defaults.light.secondaryTextHex
            )
            preferences[KEY_THEME_LIGHT_CONTROL_ACCENT] = normalizeMd3CustomColorHex(
                overrides.light.controlAccentHex,
                defaults.light.controlAccentHex
            )
            preferences[KEY_THEME_DARK_BACKGROUND] = normalizeMd3CustomColorHex(
                overrides.dark.backgroundHex,
                defaults.dark.backgroundHex
            )
            preferences[KEY_THEME_DARK_PRIMARY_TEXT] = normalizeMd3CustomColorHex(
                overrides.dark.primaryTextHex,
                defaults.dark.primaryTextHex
            )
            preferences[KEY_THEME_DARK_SECONDARY_TEXT] = normalizeMd3CustomColorHex(
                overrides.dark.secondaryTextHex,
                defaults.dark.secondaryTextHex
            )
            preferences[KEY_THEME_DARK_CONTROL_ACCENT] = normalizeMd3CustomColorHex(
                overrides.dark.controlAccentHex,
                defaults.dark.controlAccentHex
            )
        }
    }

    fun getThemeColorStyle(context: Context): Flow<PaletteStyle> = context.settingsDataStore.data
        .map { preferences -> resolvePaletteStylePreference(preferences[KEY_THEME_COLOR_STYLE]) }

    suspend fun setThemeColorStyle(context: Context, style: PaletteStyle) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_THEME_COLOR_STYLE] = style.name }
    }

    fun getThemeColorSpec(context: Context): Flow<ColorSpec.SpecVersion> = context.settingsDataStore.data
        .map { preferences -> resolveColorSpecPreference(preferences[KEY_THEME_COLOR_SPEC]) }

    suspend fun setThemeColorSpec(context: Context, spec: ColorSpec.SpecVersion) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_THEME_COLOR_SPEC] = spec.name }
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

    fun getAppFontFileName(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_APP_FONT_FILE_NAME].orEmpty() }

    fun getAppFontDisplayName(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_APP_FONT_DISPLAY_NAME].orEmpty() }

    suspend fun setAppFontFile(
        context: Context,
        fileName: String,
        displayName: String
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_APP_FONT_FILE_NAME] = fileName
            preferences[KEY_APP_FONT_DISPLAY_NAME] = displayName
        }
    }

    suspend fun clearAppFontFile(context: Context) {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(KEY_APP_FONT_FILE_NAME)
            preferences.remove(KEY_APP_FONT_DISPLAY_NAME)
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

    fun getGlobalTextTapCopyEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_GLOBAL_TEXT_TAP_COPY_ENABLED] ?: false }

    suspend fun setGlobalTextTapCopyEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_GLOBAL_TEXT_TAP_COPY_ENABLED] = value
        }
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

    fun getDanmakuCloudSyncEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_CLOUD_SYNC_ENABLED] ?: true }

    suspend fun setDanmakuCloudSyncEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DANMAKU_CLOUD_SYNC_ENABLED] = value
        }
    }

    //  [新增] --- 双击跳转秒数 ---
    fun getDoubleTapSeekEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DOUBLE_TAP_SEEK_ENABLED] ?: false } // 新用户默认关闭，已保存用户不受影响

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

    //  [新增] --- 长按倍速 (默认 2.0x) ---
    fun getLongPressSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences -> normalizeLongPressSpeed(preferences[KEY_LONG_PRESS_SPEED] ?: DEFAULT_LONG_PRESS_SPEED) }

    suspend fun setLongPressSpeed(context: Context, speed: Float) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_LONG_PRESS_SPEED] = normalizeLongPressSpeed(speed)
        }
    }

    fun getLongPressSpeedLockEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_LONG_PRESS_SPEED_LOCK_ENABLED] ?: false }

    suspend fun setLongPressSpeedLockEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LONG_PRESS_SPEED_LOCK_ENABLED] = enabled
        }
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_LONG_PRESS_SPEED_LOCK_ENABLED, enabled)
            .apply()
    }

    fun getLongPressSpeedLockEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_LONG_PRESS_SPEED_LOCK_ENABLED, false)
    }

    fun getLongPressSpeedLockHintShown(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_LONG_PRESS_SPEED_LOCK_HINT_SHOWN] ?: false }

    suspend fun setLongPressSpeedLockHintShown(context: Context, shown: Boolean) {
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_LONG_PRESS_SPEED_LOCK_HINT_SHOWN, shown)
            .apply()
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LONG_PRESS_SPEED_LOCK_HINT_SHOWN] = shown
        }
    }

    fun getLongPressSpeedLockHintShownSync(context: Context): Boolean {
        return context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_LONG_PRESS_SPEED_LOCK_HINT_SHOWN, false)
    }

    /** 隐藏开关：是否在长按倍速浮层显示 ×。默认 false。不进入设置 UI。 */
    fun getLongPressSpeedHintCloseEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_LONG_PRESS_SPEED_HINT_CLOSE_ENABLED] ?: false }

    suspend fun setLongPressSpeedHintCloseEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LONG_PRESS_SPEED_HINT_CLOSE_ENABLED] = enabled
        }
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_LONG_PRESS_SPEED_HINT_CLOSE_ENABLED, enabled)
            .apply()
    }

    fun getLongPressSpeedHintCloseEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_LONG_PRESS_SPEED_HINT_CLOSE_ENABLED, false)
    }

    fun getLongPressSpeedHintHidden(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_LONG_PRESS_SPEED_HINT_HIDDEN] ?: false }

    suspend fun setLongPressSpeedHintHidden(context: Context, hidden: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LONG_PRESS_SPEED_HINT_HIDDEN] = hidden
        }
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_LONG_PRESS_SPEED_HINT_HIDDEN, hidden)
            .apply()
    }

    fun getLongPressSpeedHintHiddenSync(context: Context): Boolean =
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_LONG_PRESS_SPEED_HINT_HIDDEN, false)

    fun getLongPressSpeedHintScale(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeLongPressSpeedHintScale(
                preferences[KEY_LONG_PRESS_SPEED_HINT_SCALE] ?: LONG_PRESS_SPEED_HINT_DEFAULT_SCALE
            )
        }

    suspend fun setLongPressSpeedHintScale(context: Context, scale: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LONG_PRESS_SPEED_HINT_SCALE] = normalizeLongPressSpeedHintScale(scale)
        }
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(CACHE_KEY_LONG_PRESS_SPEED_HINT_SCALE, normalizeLongPressSpeedHintScale(scale))
            .apply()
    }

    fun getLongPressSpeedHintScaleSync(context: Context): Float =
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .getFloat(CACHE_KEY_LONG_PRESS_SPEED_HINT_SCALE, LONG_PRESS_SPEED_HINT_DEFAULT_SCALE)
            .let(::normalizeLongPressSpeedHintScale)

    fun getLongPressSpeedHintAlpha(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeLongPressSpeedHintAlpha(
                preferences[KEY_LONG_PRESS_SPEED_HINT_ALPHA] ?: LONG_PRESS_SPEED_HINT_DEFAULT_ALPHA
            )
        }

    suspend fun setLongPressSpeedHintAlpha(context: Context, alpha: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LONG_PRESS_SPEED_HINT_ALPHA] = normalizeLongPressSpeedHintAlpha(alpha)
        }
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(CACHE_KEY_LONG_PRESS_SPEED_HINT_ALPHA, normalizeLongPressSpeedHintAlpha(alpha))
            .apply()
    }

    fun getLongPressSpeedHintAlphaSync(context: Context): Float =
        context.getSharedPreferences(LONG_PRESS_SPEED_LOCK_CACHE_PREFS, Context.MODE_PRIVATE)
            .getFloat(CACHE_KEY_LONG_PRESS_SPEED_HINT_ALPHA, LONG_PRESS_SPEED_HINT_DEFAULT_ALPHA)
            .let(::normalizeLongPressSpeedHintAlpha)

    fun getSubtitleVerticalOffsetFraction(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeSubtitleVerticalOffsetFraction(
                preferences[KEY_SUBTITLE_VERTICAL_OFFSET_FRACTION] ?: 0.0f
            )
        }

    suspend fun setSubtitleVerticalOffsetFraction(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SUBTITLE_VERTICAL_OFFSET_FRACTION] =
                normalizeSubtitleVerticalOffsetFraction(value)
        }
    }

    fun getSubtitlePortraitVerticalOffsetFraction(context: Context): Flow<Float> =
        context.settingsDataStore.data
            .map { preferences ->
                normalizeSubtitleVerticalOffsetFraction(
                    preferences[KEY_SUBTITLE_PORTRAIT_VERTICAL_OFFSET_FRACTION] ?: 0.0f
                )
            }

    suspend fun setSubtitlePortraitVerticalOffsetFraction(context: Context, value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SUBTITLE_PORTRAIT_VERTICAL_OFFSET_FRACTION] =
                normalizeSubtitleVerticalOffsetFraction(value)
        }
    }

    fun getSubtitlePositionLocked(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_SUBTITLE_POSITION_LOCKED] ?: true
        }

    suspend fun setSubtitlePositionLocked(context: Context, locked: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SUBTITLE_POSITION_LOCKED] = locked
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

    fun getPreferredPlayerVolume(context: Context): Flow<Float> =
        PlayerSettingsStore.getPreferredPlayerVolume(context)

    suspend fun setPreferredPlayerVolume(context: Context, volume: Float) {
        PlayerSettingsStore.setPreferredPlayerVolume(context, volume)
    }

    fun getPreferredPlayerVolumeSync(context: Context): Float =
        PlayerSettingsStore.getPreferredPlayerVolumeSync(context)

    fun getPlayerInsightMode(context: Context): Flow<PlayerSettingsStore.PlayerInsightMode> =
        PlayerSettingsStore.getPlayerInsightMode(context)

    suspend fun setPlayerInsightMode(context: Context, mode: PlayerSettingsStore.PlayerInsightMode) {
        PlayerSettingsStore.setPlayerInsightMode(context, mode)
    }

    fun getPlayerInsightModeSync(context: Context): PlayerSettingsStore.PlayerInsightMode =
        PlayerSettingsStore.getPlayerInsightModeSync(context)

    //  [新增] --- 主题色索引 (默认 0 = 经典蓝) ---
    fun getThemeColorIndex(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> normalizeThemeColorIndex(preferences[KEY_THEME_COLOR_INDEX] ?: 0) }

    suspend fun setThemeColorIndex(context: Context, index: Int) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_THEME_COLOR_INDEX] = normalizeThemeColorIndex(index)
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

    fun getHomeFeedCardWidthPreset(context: Context): Flow<HomeFeedCardWidthPreset> =
        context.settingsDataStore.data
            .map { preferences ->
                HomeFeedCardWidthPreset.fromValue(
                    preferences[KEY_HOME_FEED_CARD_WIDTH_PRESET]
                        ?: HomeFeedCardWidthPreset.AUTO.value
                )
            }

    suspend fun setHomeFeedCardWidthPreset(context: Context, preset: HomeFeedCardWidthPreset) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_FEED_CARD_WIDTH_PRESET] = preset.value
        }
    }

    fun getHomeFeedCardStyle(context: Context): Flow<HomeFeedCardStyle> =
        context.settingsDataStore.data.map { preferences ->
            HomeFeedCardStyle.fromValue(
                preferences[KEY_HOME_FEED_CARD_STYLE] ?: HomeFeedCardStyle.BILIPAI.value
            )
        }

    suspend fun setHomeFeedCardStyle(context: Context, style: HomeFeedCardStyle) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_FEED_CARD_STYLE] = style.value
        }
    }

    fun getCommonListHeaderCollapseMode(context: Context): Flow<CommonListHeaderCollapseMode> =
        context.settingsDataStore.data.map { preferences ->
            CommonListHeaderCollapseMode.fromValue(
                preferences[KEY_COMMON_LIST_HEADER_COLLAPSE_MODE]
                    ?: CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL.value
            )
        }

    suspend fun setCommonListHeaderCollapseMode(
        context: Context,
        mode: CommonListHeaderCollapseMode
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_COMMON_LIST_HEADER_COLLAPSE_MODE] = mode.value
        }
    }

    fun getHomeHeroCarouselEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_HOME_HERO_CAROUSEL_ENABLED] ?: true
        }

    suspend fun setHomeHeroCarouselEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_HERO_CAROUSEL_ENABLED] = value
        }
    }

    fun getHomeHeroCarouselAutoplayEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_HOME_HERO_CAROUSEL_AUTOPLAY_ENABLED] ?: false
        }

    suspend fun setHomeHeroCarouselAutoplayEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_HERO_CAROUSEL_AUTOPLAY_ENABLED] = value
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

    /** 相关推荐进入下一层详情时是否复用卡片 Morph；关闭后交给默认导航过渡。 */
    fun getRelatedVideoTransitionEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_RELATED_VIDEO_TRANSITION_ENABLED] ?: true
        }

    suspend fun setRelatedVideoTransitionEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_RELATED_VIDEO_TRANSITION_ENABLED] = value
        }
    }

    /** 默认关闭：稳妥封面过渡；开启后 SDR 可用实时画面双向 morph，HDR 仍不强制 TextureView。 */
    fun getLiveSurfaceCardTransitionEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_LIVE_SURFACE_CARD_TRANSITION_ENABLED] ?: false }

    suspend fun setLiveSurfaceCardTransitionEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LIVE_SURFACE_CARD_TRANSITION_ENABLED] = value
        }
    }

    fun getVideoTransitionRealtimeBlurEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED] ?: false }

    suspend fun setVideoTransitionRealtimeBlurEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED] = value
        }
    }

    fun getVideoSharedTransitionSpeed(context: Context): Flow<VideoSharedTransitionSpeed> =
        context.settingsDataStore.data
            .map { preferences ->
                VideoSharedTransitionSpeed.fromValue(
                    preferences[KEY_VIDEO_SHARED_TRANSITION_SPEED]
                        ?: VideoSharedTransitionSpeed.STANDARD.value
                )
            }
            .distinctUntilChanged()

    suspend fun setVideoSharedTransitionSpeed(
        context: Context,
        speed: VideoSharedTransitionSpeed
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_SHARED_TRANSITION_SPEED] = speed.value
        }
    }

    fun getVideoSharedTransitionCustomDurationMillis(context: Context): Flow<Int> =
        context.settingsDataStore.data
            .map { preferences ->
                normalizeVideoSharedTransitionCustomDurationMillis(
                    preferences[KEY_VIDEO_SHARED_TRANSITION_CUSTOM_DURATION_MILLIS]
                        ?: VIDEO_SHARED_TRANSITION_CUSTOM_DEFAULT_MILLIS
                )
            }
            .distinctUntilChanged()

    suspend fun setVideoSharedTransitionCustomDurationMillis(
        context: Context,
        durationMillis: Int
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_SHARED_TRANSITION_CUSTOM_DURATION_MILLIS] =
                normalizeVideoSharedTransitionCustomDurationMillis(durationMillis)
        }
    }

    //  [新增] --- 界面入场动画 master 开关(全 App 统一入场动效) ---
    fun getUiEntranceAnimationEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_UI_ENTRANCE_ANIMATION_ENABLED] ?: true }  // 默认开启

    suspend fun setUiEntranceAnimationEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_UI_ENTRANCE_ANIMATION_ENABLED] = value }
    }

    fun getSmartVisualGuardEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { false }

    suspend fun setSmartVisualGuardEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SMART_VISUAL_GUARD_ENABLED] = false
        }
    }

    /** 运行时视觉守卫总开关。默认开启；关闭后 Tracker 立即复位并停止采样判定。 */
    fun getRuntimeVisualGuardEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_RUNTIME_VISUAL_GUARD_ENABLED] ?: true }

    suspend fun setRuntimeVisualGuardEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_RUNTIME_VISUAL_GUARD_ENABLED] = value
        }
    }

    //  [新增] --- 视频卡片统计信息贴封面 ---
    fun getCompactVideoStatsOnCover(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_COMPACT_VIDEO_STATS_ON_COVER] ?: false }

    suspend fun setCompactVideoStatsOnCover(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_COMPACT_VIDEO_STATS_ON_COVER] = value }
    }

    fun getLowQualityHomeCoverInDataSaver(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_LOW_QUALITY_HOME_COVER_IN_DATA_SAVER] ?: false }

    suspend fun setLowQualityHomeCoverInDataSaver(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LOW_QUALITY_HOME_COVER_IN_DATA_SAVER] = value
        }
    }

    fun getHomeCoverGlassBadgesVisible(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            resolveHomeCardBadgeEffectMode(preferences) != HomeCardBadgeEffectMode.OFF
        }

    suspend fun setHomeCoverGlassBadgesVisible(context: Context, value: Boolean) {
        setHomeCardBadgeEffectMode(
            context,
            if (value) HomeCardBadgeEffectMode.SOFT_GLASS else HomeCardBadgeEffectMode.OFF
        )
    }

    fun getHomeInfoGlassBadgesVisible(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            resolveHomeCardBadgeEffectMode(preferences) != HomeCardBadgeEffectMode.OFF
        }

    suspend fun setHomeInfoGlassBadgesVisible(context: Context, value: Boolean) {
        setHomeCardBadgeEffectMode(
            context,
            if (value) HomeCardBadgeEffectMode.SOFT_GLASS else HomeCardBadgeEffectMode.OFF
        )
    }

    fun getHomeCardBadgeEffectMode(context: Context): Flow<HomeCardBadgeEffectMode> =
        context.settingsDataStore.data.map { preferences ->
            resolveHomeCardBadgeEffectMode(preferences)
        }

    suspend fun setHomeCardBadgeEffectMode(context: Context, mode: HomeCardBadgeEffectMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_CARD_BADGE_EFFECT_MODE] = mode.value
            val enabled = mode != HomeCardBadgeEffectMode.OFF
            preferences[KEY_HOME_COVER_GLASS_BADGES_VISIBLE] = enabled
            preferences[KEY_HOME_INFO_GLASS_BADGES_VISIBLE] = enabled
        }
    }

    private fun resolveHomeCardBadgeEffectMode(preferences: Preferences): HomeCardBadgeEffectMode {
        preferences[KEY_HOME_CARD_BADGE_EFFECT_MODE]?.let { raw ->
            return HomeCardBadgeEffectMode.fromValue(raw)
        }
        // Legacy: both old toggles defaulted true when unset; retired path forced OFF in map.
        // Prefer soft glass when either legacy flag is still true.
        val cover = preferences[KEY_HOME_COVER_GLASS_BADGES_VISIBLE]
        val info = preferences[KEY_HOME_INFO_GLASS_BADGES_VISIBLE]
        return if (cover == false && info == false) {
            HomeCardBadgeEffectMode.OFF
        } else {
            HomeCardBadgeEffectMode.SOFT_GLASS
        }
    }

    fun getHomeCardInfoGlassMode(context: Context): Flow<HomeCardInfoGlassMode> =
        context.settingsDataStore.data.map { preferences ->
            resolveHomeCardInfoGlassMode(preferences)
        }

    suspend fun setHomeCardInfoGlassMode(context: Context, mode: HomeCardInfoGlassMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_CARD_INFO_GLASS_MODE] = mode.value
        }
    }

    private fun resolveHomeCardInfoGlassMode(preferences: Preferences): HomeCardInfoGlassMode {
        preferences[KEY_HOME_CARD_INFO_GLASS_MODE]?.let { raw ->
            return HomeCardInfoGlassMode.fromValue(raw)
        }
        // WIP realtime glass defaults off — do not auto-enable from badge mode.
        return HomeCardInfoGlassMode.OFF
    }

    fun getHomeWallpaperUri(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HOME_WALLPAPER_URI] ?: "" }

    suspend fun setHomeWallpaperUri(context: Context, uri: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_WALLPAPER_URI] = uri
        }
    }

    fun getHomeWallpaperEffectMode(context: Context): Flow<HomeWallpaperEffectMode> = context.settingsDataStore.data
        .map { preferences ->
            HomeWallpaperEffectMode.fromValue(
                preferences[KEY_HOME_WALLPAPER_EFFECT_MODE] ?: HomeWallpaperEffectMode.SOFT_BLUR.value
            )
        }

    suspend fun setHomeWallpaperEffectMode(context: Context, mode: HomeWallpaperEffectMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_WALLPAPER_EFFECT_MODE] = mode.value
        }
    }

    fun getHomeWallpaperEffectScope(context: Context): Flow<HomeWallpaperEffectScope> = context.settingsDataStore.data
        .map { preferences ->
            HomeWallpaperEffectScope.fromValue(
                preferences[KEY_HOME_WALLPAPER_EFFECT_SCOPE] ?: HomeWallpaperEffectScope.HOME_ONLY.value
            )
        }

    suspend fun setHomeWallpaperEffectScope(context: Context, scope: HomeWallpaperEffectScope) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_WALLPAPER_EFFECT_SCOPE] = scope.value
        }
    }

    fun getHomeUpBadgesVisible(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HOME_UP_BADGES_VISIBLE] ?: false }

    suspend fun setHomeUpBadgesVisible(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_UP_BADGES_VISIBLE] = value
        }
    }

    fun getHomeUpAvatarsVisible(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HOME_UP_AVATARS_VISIBLE] ?: false }

    suspend fun setHomeUpAvatarsVisible(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_UP_AVATARS_VISIBLE] = value
        }
    }

    fun getFullVideoCardContentVisible(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_FULL_VIDEO_CARD_CONTENT_VISIBLE] ?: false }

    suspend fun setFullVideoCardContentVisible(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_FULL_VIDEO_CARD_CONTENT_VISIBLE] = value
        }
    }

    fun getHomeDurationStyle(context: Context): Flow<HomeDurationStyle> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_HOME_DURATION_STYLE]
                ?.let(HomeDurationStyle::fromValue)
                ?: if (preferences[KEY_HOME_VIDEO_DURATION_BADGES_VISIBLE] ?: true) {
                    HomeDurationStyle.OUTSIDE_COVER
                } else {
                    HomeDurationStyle.HIDDEN
                }
        }

    suspend fun setHomeDurationStyle(context: Context, style: HomeDurationStyle) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_DURATION_STYLE] = style.value
            preferences[KEY_HOME_VIDEO_DURATION_BADGES_VISIBLE] = style != HomeDurationStyle.HIDDEN
        }
    }

    //  [新增] --- 应用图标 ---
    fun getAppIcon(context: Context): Flow<String> = context.settingsDataStore.data
        .map { preferences -> normalizeAppIconKey(preferences[KEY_APP_ICON]) }

    suspend fun setAppIcon(context: Context, iconKey: String) {
        val normalizedKey = normalizeAppIconKey(iconKey)
        val success = editSettingsAndCommitPrefs(
            context, "app_icon_cache",
            editSettings = { this[KEY_APP_ICON] = normalizedKey },
            editPrefs = { putString("current_icon", normalizedKey) },
        )
            
        com.android.purebilibili.core.util.Logger.d("SettingsManager", "App icon saved: $iconKey -> $normalizedKey, persisted to prefs: $success")
    }

    fun getAppIconAppearance(context: Context): Flow<AppIconAppearance> =
        context.settingsDataStore.data.map { preferences ->
            resolveAppIconAppearance(preferences[KEY_APP_ICON_APPEARANCE] ?: 0)
        }

    suspend fun setAppIconAppearance(context: Context, appearance: AppIconAppearance) {
        editSettingsAndCommitPrefs(
            context, "app_icon_cache",
            editSettings = { this[KEY_APP_ICON_APPEARANCE] = appearance.storedValue },
            editPrefs = { putInt("appearance", appearance.storedValue) },
        )
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
        editSettingsAndCommitPrefs(
            context, SPLASH_PREFS,
            editSettings = { this[KEY_SPLASH_ICON_ANIMATION_ENABLED] = value },
            editPrefs = { putBoolean(SPLASH_PREFS_KEY_ICON_ANIMATION_ENABLED, value) },
        )
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

    fun getAppIconAppearanceSync(context: Context): AppIconAppearance {
        val storedValue = context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE)
            .getInt("appearance", AppIconAppearance.FOLLOW_SYSTEM.storedValue)
        return resolveAppIconAppearance(storedValue)
    }

    //  [新增] --- 底部栏样式 ---
    fun getBottomBarFloating(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_FLOATING] ?: true }

    suspend fun setBottomBarFloating(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BOTTOM_BAR_FLOATING] = value }
    }

    fun getNavigationIconCrossScaleEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_NAVIGATION_ICON_CROSS_SCALE_ENABLED] ?: false
        }

    suspend fun setNavigationIconCrossScaleEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_NAVIGATION_ICON_CROSS_SCALE_ENABLED] = value
        }
    }

    fun getSearchHotSectionEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SEARCH_HOT_SECTION_ENABLED] ?: true }

    suspend fun setSearchHotSectionEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_SEARCH_HOT_SECTION_ENABLED] = value }
    }

    fun getSearchDiscoverSectionEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SEARCH_DISCOVER_SECTION_ENABLED] ?: true }

    suspend fun setSearchDiscoverSectionEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_SEARCH_DISCOVER_SECTION_ENABLED] = value }
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

    fun getHomeTopRightAction(context: Context): Flow<HomeTopRightAction> = context.settingsDataStore.data
        .map { preferences ->
            HomeTopRightAction.fromValue(
                preferences[KEY_HOME_TOP_RIGHT_ACTION] ?: HomeTopRightAction.SETTINGS.value
            )
        }

    suspend fun setHomeTopRightAction(context: Context, action: HomeTopRightAction) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_TOP_RIGHT_ACTION] = action.value
        }
    }

    fun getHomeTopLayoutOrder(context: Context): Flow<HomeTopLayoutOrder> = context.settingsDataStore.data
        .map { preferences ->
            HomeTopLayoutOrder.fromValue(
                preferences[KEY_HOME_TOP_LAYOUT_ORDER] ?: HomeTopLayoutOrder.SEARCH_THEN_TABS.value
            )
        }

    suspend fun setHomeTopLayoutOrder(context: Context, order: HomeTopLayoutOrder) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_TOP_LAYOUT_ORDER] = order.value
        }
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

    fun getDynamicTabVisibleTabs(context: Context): Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        val tabsString = prefs[KEY_DYNAMIC_TAB_VISIBLE_TABS] ?: DEFAULT_DYNAMIC_TAB_VISIBLE
        tabsString.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setDynamicTabVisibleTabs(context: Context, tabs: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_TAB_VISIBLE_TABS] = tabs.joinToString(",")
        }
    }

    fun getDynamicImagePreviewTextVisible(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_DYNAMIC_IMAGE_PREVIEW_TEXT_VISIBLE] ?: true
        }

    suspend fun setDynamicImagePreviewTextVisible(context: Context, visible: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_IMAGE_PREVIEW_TEXT_VISIBLE] = visible
        }
    }

    fun getDynamicAllTabHorizontalUserListVisible(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_DYNAMIC_ALL_TAB_HORIZONTAL_USER_LIST_VISIBLE] ?: false
        }

    suspend fun setDynamicAllTabHorizontalUserListVisible(context: Context, visible: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_ALL_TAB_HORIZONTAL_USER_LIST_VISIBLE] = visible
        }
    }

    /**
     * When true, the dynamic Tab top bar collapses as soon as the feed leaves the top.
     * Default false: keep the Tab bar pinned (horizontal UP list still collapses separately).
     */
    fun getDynamicTopBarCollapseOnScroll(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_DYNAMIC_TOP_BAR_COLLAPSE_ON_SCROLL] ?: false
        }

    suspend fun setDynamicTopBarCollapseOnScroll(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_TOP_BAR_COLLAPSE_ON_SCROLL] = enabled
        }
    }

    fun getLiveFavoriteTags(context: Context): Flow<List<LiveFavoriteTagEntry>> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[KEY_LIVE_FAVORITE_TAGS].orEmpty()
        if (raw.isBlank()) {
            emptyList()
        } else {
            runCatching { Json.decodeFromString<List<LiveFavoriteTagEntry>>(raw) }.getOrDefault(emptyList())
        }
    }

    suspend fun setLiveFavoriteTags(context: Context, tags: List<LiveFavoriteTagEntry>) {
        val normalized = tags
            .filter { it.parentAreaId > 0 && it.areaId >= 0 && it.title.isNotBlank() }
            .distinctBy { it.parentAreaId to it.areaId }
            .take(12)
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LIVE_FAVORITE_TAGS] = if (normalized.isEmpty()) {
                ""
            } else {
                Json.encodeToString(normalized)
            }
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
            if (value) {
                preferences[KEY_PROGRESSIVE_TOP_BLUR_ENABLED] = false
            }
        }
    }

    fun getProgressiveTopBlurEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_PROGRESSIVE_TOP_BLUR_ENABLED] ?: false
        }

    suspend fun setProgressiveTopBlurEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PROGRESSIVE_TOP_BLUR_ENABLED] = value
            if (value) {
                preferences[KEY_HEADER_BLUR_ENABLED] = false
                preferences[KEY_HOME_HEADER_BLUR_MODE] = HomeHeaderBlurMode.ALWAYS_OFF.value
            }
        }
    }

    suspend fun setTopBarVisualEffects(
        context: Context,
        headerBlurEnabled: Boolean,
        progressiveTopBlurEnabled: Boolean
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HEADER_BLUR_ENABLED] = headerBlurEnabled
            preferences[KEY_PROGRESSIVE_TOP_BLUR_ENABLED] = progressiveTopBlurEnabled
            preferences[KEY_HOME_HEADER_BLUR_MODE] = if (headerBlurEnabled) {
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
    
    // 首页顶栏：始终显示，或离开顶部后收起、单击底栏首页回顶后再出现。
    fun getHomeHeaderCollapseMode(context: Context): Flow<HomeHeaderCollapseMode> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_HOME_HEADER_COLLAPSE_MODE]
                ?.let(HomeHeaderCollapseMode::fromValue)
                ?: HomeHeaderCollapseMode.fromLegacyBoolean(
                    preferences[KEY_HEADER_COLLAPSE_ENABLED] ?: true
                )
        }

    suspend fun setHomeHeaderCollapseMode(context: Context, mode: HomeHeaderCollapseMode) {
        val normalized = resolveHomeHeaderCollapseModeForTopBarHide(mode.hideTopBar)
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_HEADER_COLLAPSE_MODE] = normalized.value
            preferences[KEY_HEADER_COLLAPSE_ENABLED] = normalized.hideTopBar
        }
    }

    fun getShowPgcTimeline(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_SHOW_PGC_TIMELINE] ?: true
        }

    suspend fun setShowPgcTimeline(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_PGC_TIMELINE] = value
        }
    }

    fun getHomeBarHideType(context: Context): Flow<HomeBarHideType> =
        context.settingsDataStore.data.map { preferences ->
            HomeBarHideType.fromValue(
                preferences[KEY_HOME_BAR_HIDE_TYPE] ?: HomeBarHideType.SYNC.value
            )
        }

    suspend fun setHomeBarHideType(context: Context, type: HomeBarHideType) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_BAR_HIDE_TYPE] = type.value
        }
    }

    fun getHeaderCollapseEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_HOME_HEADER_COLLAPSE_MODE]
                ?.let(HomeHeaderCollapseMode::fromValue)
                ?.hasAnyCollapse
                ?: (preferences[KEY_HEADER_COLLAPSE_ENABLED] ?: true)
        }

    suspend fun setHeaderCollapseEnabled(context: Context, value: Boolean) {
        setHomeHeaderCollapseMode(
            context,
            resolveHomeHeaderCollapseModeForTopBarHide(value),
        )
    }
    
    //  [新增] --- 底栏模糊效果 ---
    fun getBottomBarBlurEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] ?: false }

    suspend fun setBottomBarBlurEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] = value }
    }

    suspend fun setBottomBarVisualEffects(
        context: Context,
        blurEnabled: Boolean,
        liquidGlassEnabled: Boolean
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BOTTOM_BAR_BLUR_ENABLED] = blurEnabled
            preferences[KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED] = liquidGlassEnabled
        }
    }
    
    //  [New] --- Liquid Glass Effect ---

    private val KEY_LIQUID_GLASS_STYLE = intPreferencesKey("liquid_glass_style")

    fun getLiquidGlassEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            val legacy = preferences[KEY_LIQUID_GLASS_ENABLED] ?: true
            val bottom = preferences[KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED] ?: legacy
            bottom
        }

    suspend fun setLiquidGlassEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LIQUID_GLASS_ENABLED] = value
            preferences[KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED] = value
        }
    }

    fun getTopBarLiquidGlassEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_TOP_BAR_LIQUID_GLASS_ENABLED] ?: false
        }

    suspend fun setTopBarLiquidGlassEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_TOP_BAR_LIQUID_GLASS_ENABLED] = value
        }
    }

    fun getHomeSearchLiquidGlassEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED]
                ?: (preferences[KEY_TOP_BAR_LIQUID_GLASS_ENABLED] ?: false)
        }

    suspend fun setHomeSearchLiquidGlassEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED] = value
        }
    }

    fun getBottomBarLiquidGlassEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED] ?: (preferences[KEY_LIQUID_GLASS_ENABLED] ?: false)
        }

    suspend fun setBottomBarLiquidGlassEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED] = value
        }
    }

    fun getBottomBarSearchEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_BOTTOM_BAR_SEARCH_ENABLED] ?: false }

    suspend fun setBottomBarSearchEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BOTTOM_BAR_SEARCH_ENABLED] = value
        }
    }

    fun getBottomBarSearchAutoExpandMode(context: Context): Flow<BottomBarSearchAutoExpandMode> =
        context.settingsDataStore.data
            .map { preferences ->
                BottomBarSearchAutoExpandMode.fromValue(
                    preferences[KEY_BOTTOM_BAR_SEARCH_AUTO_EXPAND_MODE]
                        ?: BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP.value
                )
            }

    suspend fun setBottomBarSearchAutoExpandMode(
        context: Context,
        value: BottomBarSearchAutoExpandMode
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BOTTOM_BAR_SEARCH_AUTO_EXPAND_MODE] = value.value
        }
    }

    fun getBottomBarSearchLayoutMode(context: Context): Flow<BottomBarSearchLayoutMode> =
        context.settingsDataStore.data
            .map { preferences ->
                BottomBarSearchLayoutMode.fromValue(
                    preferences[KEY_BOTTOM_BAR_SEARCH_LAYOUT_MODE]
                        ?: BottomBarSearchLayoutMode.FULL_DOCK.value
                )
            }

    suspend fun setBottomBarSearchLayoutMode(
        context: Context,
        value: BottomBarSearchLayoutMode
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BOTTOM_BAR_SEARCH_LAYOUT_MODE] = value.value
        }
    }

    fun getAndroidNativeLiquidGlassEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences ->
                preferences[KEY_ANDROID_NATIVE_LIQUID_GLASS_ENABLED]
                    ?: true
            }

    suspend fun setAndroidNativeLiquidGlassEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_ANDROID_NATIVE_LIQUID_GLASS_ENABLED] = value
        }
    }
    
    fun getLiquidGlassStyle(context: Context): Flow<LiquidGlassStyle> = context.settingsDataStore.data
        .map { preferences ->
            LiquidGlassStyle.fromValue(
                preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.SUKISU.value
            )
        }

    suspend fun setLiquidGlassStyle(context: Context, style: LiquidGlassStyle) {
        context.settingsDataStore.edit { preferences ->
            val mode = resolveLegacyLiquidGlassMode(style)
            val strength = resolveDefaultLiquidGlassStrength(mode)
            preferences[KEY_LIQUID_GLASS_STYLE] = style.value
            preferences[KEY_LIQUID_GLASS_MODE] = mode.value
            preferences[KEY_LIQUID_GLASS_STRENGTH] = strength
            preferences[KEY_LIQUID_GLASS_PROGRESS] = resolveLegacyLiquidGlassProgress(mode, strength)
        }
    }

    fun getLiquidGlassMode(context: Context): Flow<LiquidGlassMode> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_LIQUID_GLASS_MODE]
                ?.let(LiquidGlassMode::fromValue)
                ?: resolveLegacyLiquidGlassMode(
                    LiquidGlassStyle.fromValue(
                        preferences[KEY_LIQUID_GLASS_STYLE] ?: LiquidGlassStyle.SUKISU.value
                    )
                )
        }

    suspend fun setLiquidGlassMode(context: Context, mode: LiquidGlassMode) {
        context.settingsDataStore.edit { preferences ->
            val strength = normalizeLiquidGlassStrength(
                preferences[KEY_LIQUID_GLASS_STRENGTH] ?: resolveDefaultLiquidGlassStrength(mode)
            )
            val progress = resolveLegacyLiquidGlassProgress(mode, strength)
            preferences[KEY_LIQUID_GLASS_MODE] = mode.value
            preferences[KEY_LIQUID_GLASS_STRENGTH] = strength
            preferences[KEY_LIQUID_GLASS_PROGRESS] = progress
            preferences[KEY_LIQUID_GLASS_STYLE] = resolveLegacyLiquidGlassStyleFromProgress(progress).value
        }
    }

    fun getLiquidGlassStrength(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            val mode = preferences[KEY_LIQUID_GLASS_MODE]
                ?.let(LiquidGlassMode::fromValue)
                ?: LiquidGlassMode.BALANCED
            normalizeLiquidGlassStrength(
                preferences[KEY_LIQUID_GLASS_STRENGTH]
                    ?: resolveDefaultLiquidGlassStrength(mode)
            )
        }

    suspend fun setLiquidGlassStrength(context: Context, strength: Float) {
        context.settingsDataStore.edit { preferences ->
            val normalizedStrength = normalizeLiquidGlassStrength(strength)
            val mode = preferences[KEY_LIQUID_GLASS_MODE]
                ?.let(LiquidGlassMode::fromValue)
                ?: LiquidGlassMode.BALANCED
            val progress = resolveLegacyLiquidGlassProgress(mode, normalizedStrength)
            preferences[KEY_LIQUID_GLASS_STRENGTH] = normalizedStrength
            preferences[KEY_LIQUID_GLASS_PROGRESS] = progress
            preferences[KEY_LIQUID_GLASS_MODE] = mode.value
            preferences[KEY_LIQUID_GLASS_STYLE] = resolveLegacyLiquidGlassStyleFromProgress(progress).value
        }
    }

    fun getLiquidGlassProgress(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            resolveStoredLiquidGlassProgress(
                progress = preferences[KEY_LIQUID_GLASS_PROGRESS],
                legacyModeValue = preferences[KEY_LIQUID_GLASS_MODE],
                legacyStrength = preferences[KEY_LIQUID_GLASS_STRENGTH],
                legacyStyleValue = preferences[KEY_LIQUID_GLASS_STYLE],
            )
        }

    suspend fun setLiquidGlassProgress(context: Context, progress: Float) {
        context.settingsDataStore.edit { preferences ->
            val normalizedProgress = normalizeLiquidGlassProgress(progress)
            preferences[KEY_LIQUID_GLASS_PROGRESS] = normalizedProgress
            preferences[KEY_LIQUID_GLASS_MODE] = resolveLiquidGlassModeFromProgress(normalizedProgress).value
            preferences[KEY_LIQUID_GLASS_STRENGTH] = resolveLiquidGlassStrengthFromProgress(normalizedProgress)
            preferences[KEY_LIQUID_GLASS_STYLE] = resolveLegacyLiquidGlassStyleFromProgress(normalizedProgress).value
        }
    }

    fun getLiquidGlassPreviewImageUri(context: Context): Flow<String?> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_LIQUID_GLASS_PREVIEW_IMAGE_URI]?.takeIf(String::isNotBlank)
        }

    suspend fun setLiquidGlassPreviewImageUri(context: Context, uri: String?) {
        context.settingsDataStore.edit { preferences ->
            val normalizedUri = uri?.trim()?.takeIf(String::isNotEmpty)
            if (normalizedUri == null) {
                preferences.remove(KEY_LIQUID_GLASS_PREVIEW_IMAGE_URI)
            } else {
                preferences[KEY_LIQUID_GLASS_PREVIEW_IMAGE_URI] = normalizedUri
            }
        }
    }

    fun getLiquidGlassAdvancedSettings(context: Context): Flow<LiquidGlassAdvancedSettings> =
        context.settingsDataStore.data.map { preferences ->
            resolveLiquidGlassAdvancedSettings(
                presetValue = preferences[KEY_LIQUID_GLASS_ADVANCED_PRESET],
                progressiveBlurRadius = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_RADIUS],
                progressiveBlurExtent = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_EXTENT],
                progressiveBlurCurve = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_CURVE],
                contentReadability = preferences[KEY_LIQUID_GLASS_CONTENT_READABILITY],
                chromaticAberration = preferences[KEY_LIQUID_GLASS_CHROMATIC_ABERRATION],
                contentDistortion = preferences[KEY_LIQUID_GLASS_CONTENT_DISTORTION],
            )
        }

    suspend fun setLiquidGlassAdvancedSettings(
        context: Context,
        settings: LiquidGlassAdvancedSettings,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LIQUID_GLASS_ADVANCED_PRESET] = settings.preset.value
            preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_RADIUS] =
                normalizeLiquidGlassAdvancedValue(settings.progressiveBlurRadius, 0.5f)
            preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_EXTENT] =
                normalizeLiquidGlassAdvancedValue(settings.progressiveBlurExtent, 0.75f)
            preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_CURVE] =
                normalizeLiquidGlassAdvancedValue(settings.progressiveBlurCurve, 0.5f)
            preferences[KEY_LIQUID_GLASS_CONTENT_READABILITY] =
                normalizeLiquidGlassAdvancedValue(settings.contentReadability, 0.62f)
            preferences[KEY_LIQUID_GLASS_CHROMATIC_ABERRATION] =
                normalizeLiquidGlassAdvancedValue(settings.chromaticAberration, 0.56f)
            preferences[KEY_LIQUID_GLASS_CONTENT_DISTORTION] =
                normalizeLiquidGlassAdvancedValue(settings.contentDistortion, 0.45f)
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
            "plugins", "plugin", "plugin_center" -> "PLUGINS"
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

    fun getBottomBarItemLabels(context: Context): Flow<Map<String, String>> =
        NavigationSettingsStore.observeBottomBarItemLabels(context)

    suspend fun setBlurIntensity(context: Context, intensity: BlurIntensity) {
        context.settingsDataStore.edit { preferences -> 
            preferences[KEY_BLUR_INTENSITY] = intensity.name
        }
    }
    
    // ==========  弹幕设置 ==========
    
    private const val DANMAKU_DEFAULTS_VERSION = 5
    // v4: restore navigation icon cross-scale as the default without resetting other visual choices.
    private const val HOME_VISUAL_DEFAULTS_VERSION = 4
    private const val DEFAULT_DANMAKU_OPACITY = DANMAKU_DEFAULT_OPACITY
    private const val DEFAULT_DANMAKU_FONT_SCALE = 1.0f
    private const val DEFAULT_DANMAKU_SPEED = 1.0f
    private const val DEFAULT_DANMAKU_AREA = 0.5f
    private const val DEFAULT_DANMAKU_FONT_WEIGHT = 5
    private const val DEFAULT_DANMAKU_STROKE_WIDTH = 1.5f
    private const val DEFAULT_DANMAKU_LINE_HEIGHT = 1.6f
    private const val DEFAULT_DANMAKU_SCROLL_DURATION_SECONDS = 7.0f
    private const val DEFAULT_DANMAKU_STATIC_DURATION_SECONDS = 4.0f
    private const val DEFAULT_DANMAKU_DUPLICATE_MERGE_WINDOW_MS = 500
    private const val DEFAULT_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD = 2

    private fun normalizeDanmakuFontWeight(value: Int?): Int {
        return (value ?: DEFAULT_DANMAKU_FONT_WEIGHT).coerceIn(1, 9)
    }

    private fun normalizeDanmakuStrokeWidth(value: Float?): Float {
        val raw = value ?: DEFAULT_DANMAKU_STROKE_WIDTH
        if (!raw.isFinite()) return DEFAULT_DANMAKU_STROKE_WIDTH
        return raw.coerceIn(0f, 5f)
    }

    private fun normalizeDanmakuLineHeight(value: Float?): Float {
        val raw = value ?: DEFAULT_DANMAKU_LINE_HEIGHT
        if (!raw.isFinite()) return DEFAULT_DANMAKU_LINE_HEIGHT
        return raw.coerceIn(1.0f, 3.0f)
    }

    private fun normalizeDanmakuScrollDurationSeconds(value: Float?): Float {
        val raw = value ?: DEFAULT_DANMAKU_SCROLL_DURATION_SECONDS
        if (!raw.isFinite()) return DEFAULT_DANMAKU_SCROLL_DURATION_SECONDS
        return raw.coerceIn(1.0f, 50.0f)
    }

    private fun normalizeDanmakuStaticDurationSeconds(value: Float?): Float {
        val raw = value ?: DEFAULT_DANMAKU_STATIC_DURATION_SECONDS
        if (!raw.isFinite()) return DEFAULT_DANMAKU_STATIC_DURATION_SECONDS
        return raw.coerceIn(1.0f, 50.0f)
    }

    private fun normalizeDanmakuDuplicateMergeWindowMs(value: Int?): Int {
        val raw = value ?: DEFAULT_DANMAKU_DUPLICATE_MERGE_WINDOW_MS
        return raw.coerceIn(100, 3000)
    }

    private fun normalizeDanmakuDuplicateMergeCountThreshold(value: Int?): Int {
        val raw = value ?: DEFAULT_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD
        return raw.coerceIn(2, 10)
    }

    private fun buildScopedDanmakuKeyName(
        scope: DanmakuSettingsScope,
        suffix: String
    ): String = "danmaku_${scope.keyPrefix}_$suffix"
    
    private val KEY_DANMAKU_ENABLED = booleanPreferencesKey("danmaku_enabled")
    private val KEY_DANMAKU_OPACITY = floatPreferencesKey("danmaku_opacity")
    private val KEY_DANMAKU_FONT_SCALE = floatPreferencesKey("danmaku_font_scale")
    private val KEY_DANMAKU_SPEED = floatPreferencesKey("danmaku_speed")
    private val KEY_DANMAKU_AREA = floatPreferencesKey("danmaku_area")
    private val KEY_DANMAKU_FONT_WEIGHT = intPreferencesKey("danmaku_font_weight")
    private val KEY_DANMAKU_STROKE_WIDTH = floatPreferencesKey("danmaku_stroke_width")
    private val KEY_DANMAKU_LINE_HEIGHT = floatPreferencesKey("danmaku_line_height")
    private val KEY_DANMAKU_SCROLL_DURATION_SECONDS =
        floatPreferencesKey("danmaku_scroll_duration_seconds")
    private val KEY_DANMAKU_STATIC_DURATION_SECONDS =
        floatPreferencesKey("danmaku_static_duration_seconds")
    private val KEY_DANMAKU_SCROLL_FIXED_VELOCITY =
        booleanPreferencesKey("danmaku_scroll_fixed_velocity")
    private val KEY_DANMAKU_STATIC_TO_SCROLL =
        booleanPreferencesKey("danmaku_static_to_scroll")
    private val KEY_DANMAKU_MASSIVE_MODE = booleanPreferencesKey("danmaku_massive_mode")
    private val KEY_DANMAKU_ALLOW_SCROLL = booleanPreferencesKey("danmaku_allow_scroll")
    private val KEY_DANMAKU_ALLOW_TOP = booleanPreferencesKey("danmaku_allow_top")
    private val KEY_DANMAKU_ALLOW_BOTTOM = booleanPreferencesKey("danmaku_allow_bottom")
    private val KEY_DANMAKU_ALLOW_COLORFUL = booleanPreferencesKey("danmaku_allow_colorful")
    private val KEY_DANMAKU_ALLOW_SPECIAL = booleanPreferencesKey("danmaku_allow_special")
    private val KEY_DANMAKU_BLOCK_ATTENTION_COMMANDS =
        booleanPreferencesKey("danmaku_block_attention_commands")
    private val KEY_DANMAKU_SMART_OCCLUSION = booleanPreferencesKey("danmaku_smart_occlusion")
    private val KEY_DANMAKU_FULLSCREEN_PANEL_WIDTH_MODE =
        intPreferencesKey("danmaku_fullscreen_panel_width_mode")
    private val KEY_DANMAKU_BLOCK_RULES = stringPreferencesKey("danmaku_block_rules")
    private val KEY_DANMAKU_MERGE_DUPLICATES = booleanPreferencesKey("danmaku_merge_duplicates")
    private val KEY_DANMAKU_DUPLICATE_MERGE_WINDOW_MS =
        intPreferencesKey("danmaku_duplicate_merge_window_ms")
    private val KEY_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD =
        intPreferencesKey("danmaku_duplicate_merge_count_threshold")
    private val KEY_DANMAKU_SEND_COLOR = intPreferencesKey("danmaku_send_color")
    private val KEY_DANMAKU_SEND_MODE = intPreferencesKey("danmaku_send_mode")
    private val KEY_DANMAKU_SEND_FONT_SIZE = intPreferencesKey("danmaku_send_font_size")
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
    private fun keyDanmakuPortraitDisplayAreaMode() =
        intPreferencesKey(
            buildScopedDanmakuKeyName(DanmakuSettingsScope.PORTRAIT, "display_area_mode")
        )
    private fun keyDanmakuFontWeight(scope: DanmakuSettingsScope) =
        intPreferencesKey(buildScopedDanmakuKeyName(scope, "font_weight"))
    private fun keyDanmakuStrokeWidth(scope: DanmakuSettingsScope) =
        floatPreferencesKey(buildScopedDanmakuKeyName(scope, "stroke_width"))
    private fun keyDanmakuLineHeight(scope: DanmakuSettingsScope) =
        floatPreferencesKey(buildScopedDanmakuKeyName(scope, "line_height"))
    private fun keyDanmakuScrollDurationSeconds(scope: DanmakuSettingsScope) =
        floatPreferencesKey(buildScopedDanmakuKeyName(scope, "scroll_duration_seconds"))
    private fun keyDanmakuStaticDurationSeconds(scope: DanmakuSettingsScope) =
        floatPreferencesKey(buildScopedDanmakuKeyName(scope, "static_duration_seconds"))
    private fun keyDanmakuScrollFixedVelocity(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "scroll_fixed_velocity"))
    private fun keyDanmakuStaticToScroll(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "static_to_scroll"))
    private fun keyDanmakuMassiveMode(scope: DanmakuSettingsScope) =
        booleanPreferencesKey(buildScopedDanmakuKeyName(scope, "massive_mode"))
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
    private fun keyDanmakuDuplicateMergeWindowMs(scope: DanmakuSettingsScope) =
        intPreferencesKey(buildScopedDanmakuKeyName(scope, "duplicate_merge_window_ms"))
    private fun keyDanmakuDuplicateMergeCountThreshold(scope: DanmakuSettingsScope) =
        intPreferencesKey(buildScopedDanmakuKeyName(scope, "duplicate_merge_count_threshold"))

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
            fontWeight = normalizeDanmakuFontWeight(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuFontWeight(scope),
                    legacyKey = KEY_DANMAKU_FONT_WEIGHT,
                    defaultValue = DEFAULT_DANMAKU_FONT_WEIGHT
                )
            ),
            strokeWidth = normalizeDanmakuStrokeWidth(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuStrokeWidth(scope),
                    legacyKey = KEY_DANMAKU_STROKE_WIDTH,
                    defaultValue = DEFAULT_DANMAKU_STROKE_WIDTH
                )
            ),
            lineHeight = normalizeDanmakuLineHeight(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuLineHeight(scope),
                    legacyKey = KEY_DANMAKU_LINE_HEIGHT,
                    defaultValue = DEFAULT_DANMAKU_LINE_HEIGHT
                )
            ),
            scrollDurationSeconds = normalizeDanmakuScrollDurationSeconds(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuScrollDurationSeconds(scope),
                    legacyKey = KEY_DANMAKU_SCROLL_DURATION_SECONDS,
                    defaultValue = DEFAULT_DANMAKU_SCROLL_DURATION_SECONDS
                )
            ),
            staticDurationSeconds = normalizeDanmakuStaticDurationSeconds(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuStaticDurationSeconds(scope),
                    legacyKey = KEY_DANMAKU_STATIC_DURATION_SECONDS,
                    defaultValue = DEFAULT_DANMAKU_STATIC_DURATION_SECONDS
                )
            ),
            scrollFixedVelocity = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuScrollFixedVelocity(scope),
                legacyKey = KEY_DANMAKU_SCROLL_FIXED_VELOCITY,
                defaultValue = false
            ),
            staticDanmakuToScroll = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuStaticToScroll(scope),
                legacyKey = KEY_DANMAKU_STATIC_TO_SCROLL,
                defaultValue = false
            ),
            massiveMode = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuMassiveMode(scope),
                legacyKey = KEY_DANMAKU_MASSIVE_MODE,
                defaultValue = false
            ),
            mergeDuplicates = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuMergeDuplicates(scope),
                legacyKey = KEY_DANMAKU_MERGE_DUPLICATES,
                defaultValue = true
            ),
            duplicateMergeWindowMs = normalizeDanmakuDuplicateMergeWindowMs(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuDuplicateMergeWindowMs(scope),
                    legacyKey = KEY_DANMAKU_DUPLICATE_MERGE_WINDOW_MS,
                    defaultValue = DEFAULT_DANMAKU_DUPLICATE_MERGE_WINDOW_MS
                )
            ),
            duplicateMergeCountThreshold = normalizeDanmakuDuplicateMergeCountThreshold(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuDuplicateMergeCountThreshold(scope),
                    legacyKey = KEY_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD,
                    defaultValue = DEFAULT_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD
                )
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
            hideInteractiveCommands = preferences[KEY_DANMAKU_BLOCK_ATTENTION_COMMANDS] ?: false,
            blockAttentionCommands = preferences[KEY_DANMAKU_BLOCK_ATTENTION_COMMANDS] ?: false,
            smartOcclusion = readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuSmartOcclusion(scope),
                legacyKey = KEY_DANMAKU_SMART_OCCLUSION,
                defaultValue = false
            ),
            portraitDisplayAreaMode = if (scope == DanmakuSettingsScope.PORTRAIT) {
                PortraitDanmakuDisplayAreaMode.fromValue(
                    preferences[keyDanmakuPortraitDisplayAreaMode()]
                        ?: PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT.value
                )
            } else {
                PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT
            },
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

    suspend fun setPortraitDanmakuDisplayAreaMode(
        context: Context,
        value: PortraitDanmakuDisplayAreaMode
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuPortraitDisplayAreaMode()] = value.value
        }
    }

    fun getDanmakuFontWeight(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuFontWeight(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuFontWeight(scope),
                    legacyKey = KEY_DANMAKU_FONT_WEIGHT,
                    defaultValue = DEFAULT_DANMAKU_FONT_WEIGHT
                )
            )
        }

    suspend fun setDanmakuFontWeight(
        context: Context,
        value: Int,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuFontWeight(scope)] = normalizeDanmakuFontWeight(value)
        }
    }

    fun getDanmakuStrokeWidth(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuStrokeWidth(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuStrokeWidth(scope),
                    legacyKey = KEY_DANMAKU_STROKE_WIDTH,
                    defaultValue = DEFAULT_DANMAKU_STROKE_WIDTH
                )
            )
        }

    suspend fun setDanmakuStrokeWidth(
        context: Context,
        value: Float,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuStrokeWidth(scope)] = normalizeDanmakuStrokeWidth(value)
        }
    }

    fun getDanmakuLineHeight(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuLineHeight(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuLineHeight(scope),
                    legacyKey = KEY_DANMAKU_LINE_HEIGHT,
                    defaultValue = DEFAULT_DANMAKU_LINE_HEIGHT
                )
            )
        }

    suspend fun setDanmakuLineHeight(
        context: Context,
        value: Float,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuLineHeight(scope)] = normalizeDanmakuLineHeight(value)
        }
    }

    fun getDanmakuScrollDurationSeconds(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuScrollDurationSeconds(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuScrollDurationSeconds(scope),
                    legacyKey = KEY_DANMAKU_SCROLL_DURATION_SECONDS,
                    defaultValue = DEFAULT_DANMAKU_SCROLL_DURATION_SECONDS
                )
            )
        }

    suspend fun setDanmakuScrollDurationSeconds(
        context: Context,
        value: Float,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuScrollDurationSeconds(scope)] =
                normalizeDanmakuScrollDurationSeconds(value)
        }
    }

    fun getDanmakuStaticDurationSeconds(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuStaticDurationSeconds(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuStaticDurationSeconds(scope),
                    legacyKey = KEY_DANMAKU_STATIC_DURATION_SECONDS,
                    defaultValue = DEFAULT_DANMAKU_STATIC_DURATION_SECONDS
                )
            )
        }

    suspend fun setDanmakuStaticDurationSeconds(
        context: Context,
        value: Float,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuStaticDurationSeconds(scope)] =
                normalizeDanmakuStaticDurationSeconds(value)
        }
    }

    fun getDanmakuScrollFixedVelocity(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuScrollFixedVelocity(scope),
                legacyKey = KEY_DANMAKU_SCROLL_FIXED_VELOCITY,
                defaultValue = false
            )
        }

    suspend fun setDanmakuScrollFixedVelocity(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuScrollFixedVelocity(scope)] = value
        }
    }

    fun getDanmakuStaticToScroll(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuStaticToScroll(scope),
                legacyKey = KEY_DANMAKU_STATIC_TO_SCROLL,
                defaultValue = false
            )
        }

    suspend fun setDanmakuStaticToScroll(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuStaticToScroll(scope)] = value
        }
    }

    fun getDanmakuMassiveMode(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            readScopedDanmakuPreference(
                preferences = preferences,
                scopeKey = keyDanmakuMassiveMode(scope),
                legacyKey = KEY_DANMAKU_MASSIVE_MODE,
                defaultValue = false
            )
        }

    suspend fun setDanmakuMassiveMode(
        context: Context,
        value: Boolean,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuMassiveMode(scope)] = value
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

    fun getDanmakuHideInteractiveCommands(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_DANMAKU_BLOCK_ATTENTION_COMMANDS] ?: false }
            .distinctUntilChanged()

    suspend fun setDanmakuHideInteractiveCommands(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DANMAKU_BLOCK_ATTENTION_COMMANDS] = value
        }
    }

    fun getDanmakuBlockAttentionCommands(context: Context): Flow<Boolean> =
        getDanmakuHideInteractiveCommands(context)

    suspend fun setDanmakuBlockAttentionCommands(context: Context, value: Boolean) {
        setDanmakuHideInteractiveCommands(context, value)
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
        val trailingNewlines = value.takeLastWhile { it == '\n' || it == '\r' }
        val parsed = parseDanmakuBlockRules(value)
        val normalized = if (parsed.isEmpty()) {
            if (value.isBlank()) "" else value
        } else {
            parsed.joinToString(separator = "\n") + trailingNewlines
        }
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuBlockRules(scope)] = normalized
        }
    }

    fun getDanmakuSendColor(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_SEND_COLOR] ?: 16777215 }

    suspend fun setDanmakuSendColor(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DANMAKU_SEND_COLOR] = value
        }
    }

    fun getDanmakuSendMode(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_SEND_MODE] ?: 1 }

    suspend fun setDanmakuSendMode(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DANMAKU_SEND_MODE] = value
        }
    }

    fun getDanmakuSendFontSize(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_DANMAKU_SEND_FONT_SIZE] ?: 25 }

    suspend fun setDanmakuSendFontSize(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DANMAKU_SEND_FONT_SIZE] = value
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

    fun getDanmakuDuplicateMergeWindowMs(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuDuplicateMergeWindowMs(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuDuplicateMergeWindowMs(scope),
                    legacyKey = KEY_DANMAKU_DUPLICATE_MERGE_WINDOW_MS,
                    defaultValue = DEFAULT_DANMAKU_DUPLICATE_MERGE_WINDOW_MS
                )
            )
        }

    suspend fun setDanmakuDuplicateMergeWindowMs(
        context: Context,
        value: Int,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuDuplicateMergeWindowMs(scope)] =
                normalizeDanmakuDuplicateMergeWindowMs(value)
        }
    }

    fun getDanmakuDuplicateMergeCountThreshold(
        context: Context,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeDanmakuDuplicateMergeCountThreshold(
                readScopedDanmakuPreference(
                    preferences = preferences,
                    scopeKey = keyDanmakuDuplicateMergeCountThreshold(scope),
                    legacyKey = KEY_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD,
                    defaultValue = DEFAULT_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD
                )
            )
        }

    suspend fun setDanmakuDuplicateMergeCountThreshold(
        context: Context,
        value: Int,
        scope: DanmakuSettingsScope = DanmakuSettingsScope.PORTRAIT
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDanmakuDuplicateMergeCountThreshold(scope)] =
                normalizeDanmakuDuplicateMergeCountThreshold(value)
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
                preferences[KEY_DANMAKU_FONT_WEIGHT] = DEFAULT_DANMAKU_FONT_WEIGHT
                preferences[KEY_DANMAKU_STROKE_WIDTH] = DEFAULT_DANMAKU_STROKE_WIDTH
                preferences[KEY_DANMAKU_LINE_HEIGHT] = DEFAULT_DANMAKU_LINE_HEIGHT
                preferences[KEY_DANMAKU_SCROLL_DURATION_SECONDS] =
                    DEFAULT_DANMAKU_SCROLL_DURATION_SECONDS
                preferences[KEY_DANMAKU_STATIC_DURATION_SECONDS] =
                    DEFAULT_DANMAKU_STATIC_DURATION_SECONDS
                preferences[KEY_DANMAKU_SCROLL_FIXED_VELOCITY] = false
                preferences[KEY_DANMAKU_STATIC_TO_SCROLL] = false
                preferences[KEY_DANMAKU_MASSIVE_MODE] = false
                preferences[KEY_DANMAKU_DUPLICATE_MERGE_WINDOW_MS] =
                    DEFAULT_DANMAKU_DUPLICATE_MERGE_WINDOW_MS
                preferences[KEY_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD] =
                    DEFAULT_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD
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
     * 目标：默认开启底栏悬浮、导航图标交叉缩放、顶/底液态玻璃、顶部模糊，
     * 并覆盖底栏项目，确保“推荐”（HOME）恢复为第一项。版本标记写入后不再重复覆盖。
     */
    suspend fun ensureHomeVisualDefaults(context: Context) {
        context.settingsDataStore.edit { preferences ->
            val currentVersion = preferences[KEY_HOME_VISUAL_DEFAULTS_VERSION] ?: 0
            if (currentVersion < HOME_VISUAL_DEFAULTS_VERSION) {
                if (currentVersion < 3) {
                    preferences[KEY_BOTTOM_BAR_FLOATING] = true
                    preferences[KEY_LIQUID_GLASS_ENABLED] = true
                    preferences[KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED] = true
                    preferences[KEY_TOP_BAR_LIQUID_GLASS_ENABLED] = true
                    preferences[KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED] = true
                    preferences[KEY_HEADER_BLUR_ENABLED] = true
                    preferences[KEY_BOTTOM_BAR_ORDER] = DEFAULT_BOTTOM_BAR_ORDER
                    preferences[KEY_BOTTOM_BAR_VISIBLE_TABS] = DEFAULT_BOTTOM_BAR_VISIBLE_TABS
                }
                preferences[KEY_NAVIGATION_ICON_CROSS_SCALE_ENABLED] = true
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
        MOBILE(1, "移动端 (App)", "使用手机端推荐算法，需登录"),
        MERGED(2, "合并 (App+Web)", "同时使用 Web 与移动端推荐算法，移动端需登录，失败自动回退");
        
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
    private val KEY_LAUNCH_TO_PORTRAIT_FEED_ON_STARTUP = booleanPreferencesKey("launch_to_portrait_feed_on_startup")
    private val KEY_PORTRAIT_ONLY_VERTICAL_RECOMMENDATIONS =
        booleanPreferencesKey("portrait_only_vertical_recommendations")
    private const val PORTRAIT_STARTUP_CACHE_PREFS = "portrait_startup_cache"
    private const val CACHE_KEY_LAUNCH_TO_PORTRAIT_FEED = "enabled"
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

    /** Whether the portrait pager should exclude landscape recommendations (default disabled). */
    fun getPortraitOnlyVerticalRecommendations(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_PORTRAIT_ONLY_VERTICAL_RECOMMENDATIONS] ?: false
        }

    suspend fun setPortraitOnlyVerticalRecommendations(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PORTRAIT_ONLY_VERTICAL_RECOMMENDATIONS] = value
        }
    }

    fun getLaunchToPortraitFeedOnStartup(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_LAUNCH_TO_PORTRAIT_FEED_ON_STARTUP] ?: false }

    suspend fun setLaunchToPortraitFeedOnStartup(context: Context, value: Boolean) {
        editSettingsAndCommitPrefs(
            context = context,
            name = PORTRAIT_STARTUP_CACHE_PREFS,
            editSettings = { this[KEY_LAUNCH_TO_PORTRAIT_FEED_ON_STARTUP] = value },
            editPrefs = { putBoolean(CACHE_KEY_LAUNCH_TO_PORTRAIT_FEED, value) },
        )
    }

    /**
     * 冷启动快速路径。返回 null 表示缓存缺失，调用方必须挂起读取 DataStore 后再决定
     * 初始导航，不能把 null 当成默认 false；自动备份只恢复 DataStore、不恢复这个缓存。
     */
    fun getCachedLaunchToPortraitFeedOnStartup(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(PORTRAIT_STARTUP_CACHE_PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(CACHE_KEY_LAUNCH_TO_PORTRAIT_FEED)) {
            prefs.getBoolean(CACHE_KEY_LAUNCH_TO_PORTRAIT_FEED, false)
        } else null
    }

    suspend fun resolveLaunchToPortraitFeedOnStartup(context: Context): Boolean {
        getCachedLaunchToPortraitFeedOnStartup(context)?.let { return it }
        return withContext(Dispatchers.IO) {
            val value = context.settingsDataStore.data.first()[KEY_LAUNCH_TO_PORTRAIT_FEED_ON_STARTUP] ?: false
            context.getSharedPreferences(PORTRAIT_STARTUP_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(CACHE_KEY_LAUNCH_TO_PORTRAIT_FEED, value).apply()
            value
        }
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
    private val KEY_SUBSCRIBED_COLLECTION_IDS = stringPreferencesKey("subscribed_collection_ids")
    private val KEY_COLLECTION_SORT_PREFERENCES = stringPreferencesKey("collection_sort_preferences")
    
    // --- WiFi 默认画质 (默认 80 = 1080P) ---
    fun getWifiQuality(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_WIFI_QUALITY] ?: 80 }

    suspend fun setWifiQuality(context: Context, value: Int) {
        val success = editSettingsAndCommitPrefs(
            context, "quality_settings",
            editSettings = { this[KEY_WIFI_QUALITY] = value },
            editPrefs = { putInt("wifi_quality", value) },
        )
        com.android.purebilibili.core.util.Logger.d("SettingsManager", " WiFi 画质已设置: $value (写入成功: $success)")
    }
    
    // --- 流量默认画质 (默认 64 = 720P) ---
    fun getMobileQuality(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_MOBILE_QUALITY] ?: 64 }

    suspend fun setMobileQuality(context: Context, value: Int) {
        val success = editSettingsAndCommitPrefs(
            context, "quality_settings",
            editSettings = { this[KEY_MOBILE_QUALITY] = value },
            editPrefs = { putInt("mobile_quality", value) },
        )
        com.android.purebilibili.core.util.Logger.d("SettingsManager", " 流量画质已设置: $value (写入成功: $success)")
    }
    
    //  同步读取画质设置（用于 VideoPlaybackViewModel）
    fun getWifiQualitySync(context: Context): Int {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getInt("wifi_quality", 80)
    }
    
    fun getMobileQualitySync(context: Context): Int {
        return context.getSharedPreferences("quality_settings", Context.MODE_PRIVATE)
            .getInt("mobile_quality", 64)
    }

    fun getSubscribedCollectionIds(context: Context): Flow<Set<String>> = context.settingsDataStore.data
        .map { preferences -> decodeCollectionSubscriptionIds(preferences[KEY_SUBSCRIBED_COLLECTION_IDS]) }
        .distinctUntilChanged()

    fun isCollectionSubscribed(context: Context, collectionId: Long): Flow<Boolean> {
        val normalizedId = collectionId.takeIf { it > 0L }?.toString()
            ?: return kotlinx.coroutines.flow.flowOf(false)
        return getSubscribedCollectionIds(context)
            .map { subscribedIds -> normalizedId in subscribedIds }
            .distinctUntilChanged()
    }

    suspend fun toggleCollectionSubscription(context: Context, collectionId: Long) {
        if (collectionId <= 0L) return
        context.settingsDataStore.edit { preferences ->
            val current = decodeCollectionSubscriptionIds(preferences[KEY_SUBSCRIBED_COLLECTION_IDS])
            preferences[KEY_SUBSCRIBED_COLLECTION_IDS] = encodeCollectionSubscriptionIds(
                toggleCollectionSubscription(current, collectionId)
            )
        }
    }

    suspend fun setCollectionSubscription(context: Context, collectionId: Long, subscribed: Boolean) {
        if (collectionId <= 0L) return
        context.settingsDataStore.edit { preferences ->
            val current = decodeCollectionSubscriptionIds(preferences[KEY_SUBSCRIBED_COLLECTION_IDS])
            preferences[KEY_SUBSCRIBED_COLLECTION_IDS] = encodeCollectionSubscriptionIds(
                setCollectionSubscription(current, collectionId, subscribed)
            )
        }
    }

    fun getCollectionSortPreferences(context: Context): Flow<Map<Long, CollectionSortMode>> =
        context.settingsDataStore.data
            .map { preferences -> decodeCollectionSortPreferences(preferences[KEY_COLLECTION_SORT_PREFERENCES]) }
            .distinctUntilChanged()

    fun getCollectionSortMode(context: Context, collectionId: Long): Flow<CollectionSortMode> {
        if (collectionId <= 0L) return kotlinx.coroutines.flow.flowOf(CollectionSortMode.ASCENDING)
        return getCollectionSortPreferences(context)
            .map { preferences -> preferences[collectionId] ?: CollectionSortMode.ASCENDING }
            .distinctUntilChanged()
    }

    suspend fun setCollectionSortMode(
        context: Context,
        collectionId: Long,
        sortMode: CollectionSortMode
    ) {
        if (collectionId <= 0L) return
        context.settingsDataStore.edit { preferences ->
            val current = decodeCollectionSortPreferences(
                preferences[KEY_COLLECTION_SORT_PREFERENCES]
            ).toMutableMap()
            if (sortMode == CollectionSortMode.ASCENDING) {
                current.remove(collectionId)
            } else {
                current[collectionId] = sortMode
            }
            preferences[KEY_COLLECTION_SORT_PREFERENCES] = encodeCollectionSortPreferences(current)
        }
    }
    
    // --- 🚀 自动最高画质 (开启后忽略上方设置，始终选择最高可用画质) ---
    private val KEY_AUTO_HIGHEST_QUALITY = booleanPreferencesKey("auto_highest_quality")
    private val KEY_BILI_DIRECTED_TRAFFIC = booleanPreferencesKey("bili_directed_traffic")
    
    fun getAutoHighestQuality(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_HIGHEST_QUALITY] ?: false }  // 默认关闭
    
    suspend fun setAutoHighestQuality(context: Context, value: Boolean) {
        editSettingsAndCommitPrefs(
            context, "quality_settings",
            editSettings = { this[KEY_AUTO_HIGHEST_QUALITY] = value },
            editPrefs = { putBoolean("auto_highest_quality", value) },
        )
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
        editSettingsAndCommitPrefs(
            context, "quality_settings",
            editSettings = { this[KEY_BILI_DIRECTED_TRAFFIC] = value },
            editPrefs = { putBoolean("bili_directed_traffic", value) },
        )
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
        val result = editSettingsAndCommitPrefs(
            context, "quality_settings",
            editSettings = {
                this[KEY_AUDIO_QUALITY] = value
                com.android.purebilibili.core.util.Logger.d("SettingsManager", "📻 setAudioQuality DataStore written: $value")
            },
            editPrefs = { putInt("audio_quality", value) },
        )
        com.android.purebilibili.core.util.Logger.d("SettingsManager", "📻 setAudioQuality SharedPrefs committed: $value, success=$result")
    }

    // --- 评论默认排序 (2=最新,3=最热) ---
    fun getCommentDefaultSortMode(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            val value = preferences[KEY_COMMENT_DEFAULT_SORT_MODE] ?: 3
            if (value == 2 || value == 3) value else 3
        }

    suspend fun setCommentDefaultSortMode(context: Context, value: Int) {
        val normalized = if (value == 2 || value == 3) value else 3
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
        return if (value == 2 || value == 3) value else 3
    }

    fun getCommentFraudDetectionEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_COMMENT_FRAUD_DETECTION_ENABLED] ?: true }

    suspend fun setCommentFraudDetectionEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_COMMENT_FRAUD_DETECTION_ENABLED] = enabled
        }
    }

    fun getAutoCacheClearInterval(context: Context): Flow<AutoCacheClearInterval> =
        context.settingsDataStore.data.map { preferences ->
            val days = preferences[KEY_AUTO_CACHE_CLEAR_INTERVAL] ?: AutoCacheClearInterval.NEVER.days
            AutoCacheClearInterval.entries.firstOrNull { it.days == days } ?: AutoCacheClearInterval.NEVER
        }

    suspend fun setAutoCacheClearInterval(context: Context, interval: AutoCacheClearInterval) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUTO_CACHE_CLEAR_INTERVAL] = interval.days
        }
    }

    fun getAutoCacheClearThresholdGb(context: Context): Flow<Int> =
        context.settingsDataStore.data.map { preferences ->
            (preferences[KEY_AUTO_CACHE_CLEAR_THRESHOLD_GB]
                ?: DEFAULT_AUTO_CACHE_CLEAR_THRESHOLD_GB).coerceIn(1, 20)
        }

    suspend fun setAutoCacheClearThresholdGb(context: Context, thresholdGb: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUTO_CACHE_CLEAR_THRESHOLD_GB] = thresholdGb.coerceIn(1, 20)
        }
    }

    suspend fun getLastAutoCacheClearAt(context: Context): Long =
        context.settingsDataStore.data.first()[KEY_LAST_AUTO_CACHE_CLEAR_AT] ?: 0L

    suspend fun setLastAutoCacheClearAt(context: Context, timestamp: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LAST_AUTO_CACHE_CLEAR_AT] = timestamp.coerceAtLeast(0L)
        }
    }

    fun getCommentMemberDecorationsEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_COMMENT_MEMBER_DECORATIONS_ENABLED] ?: false }

    suspend fun setCommentMemberDecorationsEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_COMMENT_MEMBER_DECORATIONS_ENABLED] = enabled
        }
    }

    fun getSubReplyLoadedCountEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_SUB_REPLY_LOADED_COUNT_ENABLED] ?: false }

    suspend fun setSubReplyLoadedCountEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SUB_REPLY_LOADED_COUNT_ENABLED] = enabled
        }
    }

    fun getImagePreviewLongPressSaveEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_IMAGE_PREVIEW_LONG_PRESS_SAVE_ENABLED] ?: true }

    suspend fun setImagePreviewLongPressSaveEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_IMAGE_PREVIEW_LONG_PRESS_SAVE_ENABLED] = enabled
        }
    }

    fun getImagePreview3dPageEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_IMAGE_PREVIEW_3D_PAGE_ENABLED] ?: false }

    suspend fun setImagePreview3dPageEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_IMAGE_PREVIEW_3D_PAGE_ENABLED] = enabled
        }
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
    private val KEY_ENHANCED_DIAGNOSTIC_LOGGING_ENABLED =
        booleanPreferencesKey("enhanced_diagnostic_logging_enabled")
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

    // --- 增强诊断日志（默认关闭，用户主动开启）---
    fun getEnhancedDiagnosticLoggingEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_ENHANCED_DIAGNOSTIC_LOGGING_ENABLED] ?: false
        }

    suspend fun setEnhancedDiagnosticLoggingEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_ENHANCED_DIAGNOSTIC_LOGGING_ENABLED] = value
        }
        // Application 冷启动阶段需要同步读取，避免错过启动期诊断信息。
        context.getSharedPreferences(ENHANCED_DIAGNOSTIC_LOG_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENHANCED_DIAGNOSTIC_LOG_PREF_KEY, value)
            .apply()
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
    private val KEY_APP_UPDATE_CHANNEL = intPreferencesKey("app_update_channel")

    /**
     *  更新检测渠道
     * - STABLE: 正式版渠道，仅检测稳定版本
     * - BETA: 测试版渠道，同时检测预发布（Beta / RC）版本
     */
    enum class AppUpdateChannel(val value: Int, val label: String, val description: String) {
        STABLE(0, "正式版", "仅检测稳定版本"),
        BETA(1, "测试版", "同时检测测试版与预发布版本");

        companion object {
            fun fromValue(value: Int): AppUpdateChannel = entries.find { it.value == value } ?: STABLE
        }
    }

    fun getAutoCheckAppUpdate(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUTO_CHECK_APP_UPDATE] ?: true } // 默认开启

    suspend fun setAutoCheckAppUpdate(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_AUTO_CHECK_APP_UPDATE] = value }
    }

    // --- 更新检测渠道 (默认正式版) ---
    fun getAppUpdateChannel(context: Context): Flow<AppUpdateChannel> = context.settingsDataStore.data
        .map { preferences ->
            AppUpdateChannel.fromValue(preferences[KEY_APP_UPDATE_CHANNEL] ?: AppUpdateChannel.STABLE.value)
        }

    suspend fun setAppUpdateChannel(context: Context, channel: AppUpdateChannel) {
        context.settingsDataStore.edit { preferences -> preferences[KEY_APP_UPDATE_CHANNEL] = channel.value }
    }
    
    // ==========  隐私无痕模式 ==========
    
    private val KEY_PRIVACY_MODE_ENABLED = booleanPreferencesKey("privacy_mode_enabled")
    private val KEY_PRIVACY_CONTENT_AUTHENTICATION_ENABLED =
        booleanPreferencesKey("privacy_content_authentication_enabled")
    
    private val KEY_SEARCH_SUGGESTIONS_ENABLED = booleanPreferencesKey("search_suggestions_enabled")

    fun getSearchSuggestionsEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SEARCH_SUGGESTIONS_ENABLED] ?: true }

    suspend fun setSearchSuggestionsEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SEARCH_SUGGESTIONS_ENABLED] = value }
    }

    // --- 不记录播放历史和搜索历史 ---
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

    // --- 进入收藏、历史等隐私内容前使用系统认证 ---
    fun getPrivacyContentAuthenticationEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_PRIVACY_CONTENT_AUTHENTICATION_ENABLED] ?: false }

    suspend fun setPrivacyContentAuthenticationEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PRIVACY_CONTENT_AUTHENTICATION_ENABLED] = value
        }
    }
    
    // ==========  小窗播放模式 ==========
    
    private val KEY_MINI_PLAYER_MODE = intPreferencesKey("mini_player_mode")
    
    /**
     *  小窗播放模式
     * - OFF: 默认模式（官方B站行为：切到桌面后台播放，返回主页停止）
     * - IN_APP_ONLY: 应用内小窗（返回主页时显示悬浮小窗）
     * - SYSTEM_PIP: 系统画中画（切到桌面时自动进入画中画模式）
     * - IN_APP_AND_SYSTEM_PIP: 应用内小窗 + 系统画中画
     */
    enum class MiniPlayerMode(val value: Int, val label: String, val description: String) {
        OFF(0, "默认", "切到桌面后台播放，返回主页停止"),
        IN_APP_ONLY(1, "应用内小窗", "返回主页时显示悬浮小窗"),
        SYSTEM_PIP(2, "画中画", "切到桌面进入系统画中画"),
        IN_APP_AND_SYSTEM_PIP(3, "小窗+画中画", "返回主页显示小窗，切到桌面进入画中画");

        val supportsInAppMiniPlayer: Boolean
            get() = this == IN_APP_ONLY || this == IN_APP_AND_SYSTEM_PIP

        val supportsSystemPip: Boolean
            get() = this == SYSTEM_PIP || this == IN_APP_AND_SYSTEM_PIP
        
        companion object {
            fun fromValue(value: Int): MiniPlayerMode = when(value) {
                1 -> IN_APP_ONLY
                2 -> SYSTEM_PIP
                3 -> IN_APP_AND_SYSTEM_PIP
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

    fun getAudioNowPlayingBarEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_AUDIO_NOW_PLAYING_BAR_ENABLED] ?: true }
        .onEach { value ->
            context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("audio_now_playing_bar_enabled", value)
                .apply()
        }

    suspend fun setAudioNowPlayingBarEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUDIO_NOW_PLAYING_BAR_ENABLED] = value
        }
        context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("audio_now_playing_bar_enabled", value)
            .apply()
    }

    fun getAudioNowPlayingBarEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
            .getBoolean("audio_now_playing_bar_enabled", true)
    }

    internal fun shouldEnableAudioModeAutoPipToggle(mode: MiniPlayerMode): Boolean {
        return mode.supportsSystemPip
    }

    fun getVideoAiSummaryEntryEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_VIDEO_AI_SUMMARY_ENTRY_ENABLED] ?: true }

    suspend fun setVideoAiSummaryEntryEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_AI_SUMMARY_ENTRY_ENABLED] = enabled
        }
    }

    fun getVideoNoteEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_VIDEO_NOTE_ENABLED] ?: true }

    suspend fun setVideoNoteEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_NOTE_ENABLED] = enabled
        }
        context.getSharedPreferences(VIDEO_NOTE_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_VIDEO_NOTE_ENABLED, enabled)
            .apply()
    }

    fun getVideoNoteEnabledSync(context: Context): Boolean {
        return context.getSharedPreferences(VIDEO_NOTE_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_VIDEO_NOTE_ENABLED, true)
    }

    fun getVideoNoteDefaultCollapsed(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_VIDEO_NOTE_DEFAULT_COLLAPSED] ?: false }

    suspend fun setVideoNoteDefaultCollapsed(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_NOTE_DEFAULT_COLLAPSED] = enabled
        }
        context.getSharedPreferences(VIDEO_NOTE_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_VIDEO_NOTE_DEFAULT_COLLAPSED, enabled)
            .apply()
    }

    fun getVideoNoteDefaultCollapsedSync(context: Context): Boolean {
        return context.getSharedPreferences(VIDEO_NOTE_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_VIDEO_NOTE_DEFAULT_COLLAPSED, false)
    }

    fun getVideoInfoDefaultExpanded(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_VIDEO_INFO_DEFAULT_EXPANDED] ?: true }

    suspend fun setVideoInfoDefaultExpanded(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_VIDEO_INFO_DEFAULT_EXPANDED] = enabled
        }
    }

    // ==========  底栏显示模式 ==========
    
    private val KEY_BOTTOM_BAR_VISIBILITY_MODE = intPreferencesKey("bottom_bar_visibility_mode")
    
    /**
     *  底栏显示模式
     * - SCROLL_HIDE: 向下浏览时隐藏，向上返回时显示
     * - ALWAYS_VISIBLE: 始终显示（默认）
     * - ALWAYS_HIDDEN: 永久隐藏
     */
    enum class BottomBarVisibilityMode(val value: Int, val label: String, val description: String) {
        SCROLL_HIDE(0, "向下浏览时隐藏", "浏览更下方内容时隐藏，向上返回时显示"),
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
    private val KEY_IMAGE_SAVE_TREE_URI = stringPreferencesKey("image_save_tree_uri")
    
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
        editSettingsAndCommitPrefs(
            context, "download_prefs",
            editSettings = {
                if (path != null) this[KEY_DOWNLOAD_PATH] = path else remove(KEY_DOWNLOAD_PATH)
            },
            editPrefs = { putString("path", path) },
        )
    }

    fun getDownloadExportTreeUri(context: Context): Flow<String?> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_DOWNLOAD_EXPORT_TREE_URI]
        }

    suspend fun setDownloadExportTreeUri(context: Context, uri: String?) {
        editSettingsAndCommitPrefs(
            context, "download_prefs",
            editSettings = {
                if (uri != null) this[KEY_DOWNLOAD_EXPORT_TREE_URI] = uri else remove(KEY_DOWNLOAD_EXPORT_TREE_URI)
            },
            editPrefs = { putString("tree_uri", uri) },
        )
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

    fun getImageSaveTreeUri(context: Context): Flow<String?> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_IMAGE_SAVE_TREE_URI]
        }

    suspend fun setImageSaveTreeUri(context: Context, uri: String?) {
        editSettingsAndCommitPrefs(
            context, "image_save_prefs",
            editSettings = {
                if (uri != null) this[KEY_IMAGE_SAVE_TREE_URI] = uri else remove(KEY_IMAGE_SAVE_TREE_URI)
            },
            editPrefs = { putString("tree_uri", uri) },
        )
    }

    fun getImageSaveTreeUriSync(context: Context): String? {
        return context.getSharedPreferences("image_save_prefs", Context.MODE_PRIVATE)
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
        val visible = tabsString.split(",").filter { it.isNotBlank() }
        resolveOrderedVisibleBottomTabs(order, visible)
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

    suspend fun setBottomBarItemLabel(context: Context, itemId: String, label: String) =
        NavigationSettingsStore.setBottomBarItemLabel(context, itemId, label)

    suspend fun clearBottomBarItemLabels(context: Context) =
        NavigationSettingsStore.clearBottomBarItemLabels(context)
    
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
    private val KEY_PORTRAIT_PLAYER_COLLAPSE_MODE = intPreferencesKey("portrait_player_collapse_mode")
    /** Auto-pause when the detail player is swipe-collapsed; default on. */
    private val KEY_PAUSE_ON_PLAYER_COLLAPSE = booleanPreferencesKey("pause_on_player_collapse")
    private val KEY_PORTRAIT_SWIPE_TO_FULLSCREEN = booleanPreferencesKey("portrait_swipe_to_fullscreen")
    private val KEY_CENTER_SWIPE_TO_FULLSCREEN = booleanPreferencesKey("center_swipe_to_fullscreen")
    private val KEY_INLINE_SWIPE_SEEK_SECONDS = intPreferencesKey("inline_swipe_seek_seconds")
    private val KEY_FULLSCREEN_SWIPE_SEEK_ENABLED = booleanPreferencesKey("fullscreen_swipe_seek_enabled")
    private val KEY_FULLSCREEN_SWIPE_SEEK_SECONDS = intPreferencesKey("fullscreen_swipe_seek_seconds")
    private val KEY_FULLSCREEN_GESTURE_REVERSE = booleanPreferencesKey("fullscreen_gesture_reverse")
    private val KEY_HIDE_VIDEO_PAGE_STATUS_BAR = booleanPreferencesKey("hide_video_page_status_bar")
    private val KEY_PORTRAIT_LETTERBOX_AMBIENT_HAZE =
        booleanPreferencesKey("portrait_letterbox_ambient_haze")
    private val KEY_TABLET_COMMENT_PANEL_WIDTH_PRESET =
        intPreferencesKey("tablet_comment_panel_width_preset")
    private val KEY_AUTO_ENTER_FULLSCREEN = booleanPreferencesKey("auto_enter_fullscreen")
    private val KEY_AUTO_EXIT_FULLSCREEN = booleanPreferencesKey("auto_exit_fullscreen")
    private val KEY_AUTO_EXIT_FULLSCREEN_MODE = intPreferencesKey("auto_exit_fullscreen_mode")
    private val KEY_SHOW_FULLSCREEN_LOCK_BUTTON = booleanPreferencesKey("show_fullscreen_lock_button")
    private val KEY_SHOW_FULLSCREEN_SCREENSHOT_BUTTON = booleanPreferencesKey("show_fullscreen_screenshot_button")
    private val KEY_APP_GESTURE_SCREENSHOT_ENABLED =
        booleanPreferencesKey("app_gesture_screenshot_enabled")
    private val KEY_APP_SCREENSHOT_GESTURE_MODE =
        intPreferencesKey("app_screenshot_gesture_mode")
    private val KEY_APP_SCREENSHOT_CAPTURE_MODE =
        intPreferencesKey("app_screenshot_capture_mode")
    private val KEY_SHOW_FULLSCREEN_BATTERY_LEVEL = booleanPreferencesKey("show_fullscreen_battery_level")
    private val KEY_SHOW_FULLSCREEN_TIME = booleanPreferencesKey("show_fullscreen_time")
    private val KEY_SHOW_FULLSCREEN_ACTION_ITEMS = booleanPreferencesKey("show_fullscreen_action_items")
    private val KEY_SHOW_ONLINE_COUNT = booleanPreferencesKey("show_online_count")
    private val KEY_SHOW_PROFILE_EDIT_BUTTON = booleanPreferencesKey("show_profile_edit_button")
    private val KEY_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT =
        intPreferencesKey("comment_collapsed_reply_preview_limit")
    private val KEY_PLAYER_DIAGNOSTIC_LOGGING_ENABLED =
        booleanPreferencesKey("player_diagnostic_logging_enabled")
    private val KEY_DASH_SEGMENT_REQUESTS_ENABLED =
        booleanPreferencesKey("dash_segment_requests_enabled")
    private val KEY_PLAYBACK_CDN_PREFERENCE = stringPreferencesKey("playback_cdn_preference")
    private val KEY_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED =
        booleanPreferencesKey("quality_switch_failure_dialog_enabled")
    private val KEY_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED =
        booleanPreferencesKey("quality_switch_failure_dialog_once_enabled")
    private val KEY_QUALITY_SWITCH_FAILURE_DIALOG_SHOWN =
        booleanPreferencesKey("quality_switch_failure_dialog_shown")
    private val KEY_SUBTITLE_AUTO_PREFERENCE = intPreferencesKey("subtitle_auto_preference")
    private val KEY_BOTTOM_PROGRESS_BEHAVIOR = intPreferencesKey("bottom_progress_behavior")
    private val KEY_PROGRESS_PEAK_DANMAKU_ENABLED =
        booleanPreferencesKey("progress_peak_danmaku_enabled")
    private val KEY_HORIZONTAL_ADAPTATION = booleanPreferencesKey("horizontal_adaptation_enabled")
    private val KEY_FULLSCREEN_MODE = intPreferencesKey("fullscreen_mode")
    private val KEY_FULLSCREEN_ASPECT_RATIO = intPreferencesKey("fullscreen_aspect_ratio")
    private val INLINE_SWIPE_SEEK_OPTIONS = listOf(5, 10, 15, 30, 60)
    private val FULLSCREEN_SWIPE_SEEK_OPTIONS = listOf(10, 15, 20, 30)
    const val DEFAULT_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT = 3
    private const val COMMENT_PREVIEW_CACHE_PREFS = "comment_preview_cache"
    private const val CACHE_KEY_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT = "comment_collapsed_reply_preview_limit"

    fun normalizeCommentCollapsedReplyPreviewLimit(value: Int): Int = value.coerceIn(1, 10)

    internal fun resolvePortraitPlayerCollapseModePreference(
        rawMode: Int?,
        legacySwipeHide: Boolean?
    ): PortraitPlayerCollapseMode {
        return rawMode?.let(PortraitPlayerCollapseMode::fromValue)
            ?: legacySwipeHide?.let(PortraitPlayerCollapseMode::fromLegacySwipeHide)
            ?: PortraitPlayerCollapseMode.INTRO_ONLY
    }

    internal fun resolveSwipeHidePlayerEnabledPreference(
        rawMode: Int?,
        legacySwipeHide: Boolean?
    ): Boolean {
        return resolvePortraitPlayerCollapseModePreference(rawMode, legacySwipeHide) != PortraitPlayerCollapseMode.OFF
    }
    
    // --- 播放器滚动缩小方向策略 ---
    fun getPortraitPlayerCollapseMode(context: Context): Flow<PortraitPlayerCollapseMode> =
        context.settingsDataStore.data.map { preferences ->
            resolvePortraitPlayerCollapseModePreference(
                rawMode = preferences[KEY_PORTRAIT_PLAYER_COLLAPSE_MODE],
                legacySwipeHide = preferences[KEY_SWIPE_HIDE_PLAYER]
            )
        }

    suspend fun setPortraitPlayerCollapseMode(
        context: Context,
        mode: PortraitPlayerCollapseMode
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PORTRAIT_PLAYER_COLLAPSE_MODE] = mode.value
            preferences[KEY_SWIPE_HIDE_PLAYER] = mode != PortraitPlayerCollapseMode.OFF
        }
    }

    // --- 播放器缩小后自动暂停（默认开启） ---
    fun getPauseOnPlayerCollapseEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_PAUSE_ON_PLAYER_COLLAPSE] ?: true
        }

    suspend fun setPauseOnPlayerCollapseEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PAUSE_ON_PLAYER_COLLAPSE] = value
        }
    }

    // --- 上滑隐藏播放器开关 ---
    fun getSwipeHidePlayerEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            resolveSwipeHidePlayerEnabledPreference(
                rawMode = preferences[KEY_PORTRAIT_PLAYER_COLLAPSE_MODE],
                legacySwipeHide = preferences[KEY_SWIPE_HIDE_PLAYER]
            )
        }

    suspend fun setSwipeHidePlayerEnabled(context: Context, value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SWIPE_HIDE_PLAYER] = value
            preferences[KEY_PORTRAIT_PLAYER_COLLAPSE_MODE] =
                PortraitPlayerCollapseMode.fromLegacySwipeHide(value).value
        }
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

    // --- 非全屏左右滑动调进度范围（秒，默认 30） ---
    fun getInlineSwipeSeekSeconds(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            val raw = preferences[KEY_INLINE_SWIPE_SEEK_SECONDS] ?: 30
            normalizeInlineSwipeSeekSeconds(raw)
        }

    suspend fun setInlineSwipeSeekSeconds(context: Context, seconds: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_INLINE_SWIPE_SEEK_SECONDS] = normalizeInlineSwipeSeekSeconds(seconds)
        }
    }

    private fun normalizeInlineSwipeSeekSeconds(seconds: Int): Int {
        return INLINE_SWIPE_SEEK_OPTIONS.minByOrNull { option -> abs(option - seconds) } ?: 30
    }

    // --- 横屏左右滑动精细调进度开关（默认开启） ---
    fun getFullscreenSwipeSeekEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_FULLSCREEN_SWIPE_SEEK_ENABLED] ?: true }

    suspend fun setFullscreenSwipeSeekEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_FULLSCREEN_SWIPE_SEEK_ENABLED] = enabled
        }
    }

    // --- 横屏左右滑动调进度范围（秒，默认 15） ---
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

    fun getHideVideoPageStatusBar(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_HIDE_VIDEO_PAGE_STATUS_BAR] ?: false }
        .onEach { enabledFromDataStore ->
            context.getSharedPreferences(VIDEO_PAGE_STATUS_BAR_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(CACHE_KEY_HIDE_VIDEO_PAGE_STATUS_BAR, enabledFromDataStore)
                .apply()
        }

    suspend fun setHideVideoPageStatusBar(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_HIDE_VIDEO_PAGE_STATUS_BAR] = enabled
        }
        context.getSharedPreferences(VIDEO_PAGE_STATUS_BAR_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CACHE_KEY_HIDE_VIDEO_PAGE_STATUS_BAR, enabled)
            .apply()
    }

    fun getHideVideoPageStatusBarSync(context: Context): Boolean {
        return context.getSharedPreferences(VIDEO_PAGE_STATUS_BAR_CACHE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(CACHE_KEY_HIDE_VIDEO_PAGE_STATUS_BAR, false)
    }

    fun getPortraitLetterboxAmbientHaze(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map { preferences -> preferences[KEY_PORTRAIT_LETTERBOX_AMBIENT_HAZE] ?: true }

    suspend fun setPortraitLetterboxAmbientHaze(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PORTRAIT_LETTERBOX_AMBIENT_HAZE] = enabled
        }
    }

    fun getPortraitLetterboxAmbientHazeSync(context: Context): Boolean {
        // 无独立 cache；冷启动先用默认 true，DataStore 回填后以 Flow 为准。
        return true
    }

    fun getTabletCommentPanelWidthPreset(context: Context): Flow<TabletCommentPanelWidthPreset> =
        context.settingsDataStore.data
            .map { preferences ->
                TabletCommentPanelWidthPreset.fromValue(
                    preferences[KEY_TABLET_COMMENT_PANEL_WIDTH_PRESET]
                        ?: TabletCommentPanelWidthPreset.STANDARD.value
                )
            }

    suspend fun setTabletCommentPanelWidthPreset(
        context: Context,
        preset: TabletCommentPanelWidthPreset
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_TABLET_COMMENT_PANEL_WIDTH_PRESET] = preset.value
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
        .map { preferences ->
            resolveAutoExitFullscreenMode(
                modeValue = preferences[KEY_AUTO_EXIT_FULLSCREEN_MODE],
                legacyEnabled = preferences[KEY_AUTO_EXIT_FULLSCREEN],
            ) != AutoExitFullscreenMode.OFF
        }

    fun getAutoExitFullscreenMode(context: Context): Flow<AutoExitFullscreenMode> =
        context.settingsDataStore.data.map { preferences ->
            resolveAutoExitFullscreenMode(
                modeValue = preferences[KEY_AUTO_EXIT_FULLSCREEN_MODE],
                legacyEnabled = preferences[KEY_AUTO_EXIT_FULLSCREEN],
            )
        }

    suspend fun setAutoExitFullscreen(context: Context, enabled: Boolean) {
        setAutoExitFullscreenMode(
            context,
            AutoExitFullscreenMode.fromLegacyEnabled(enabled),
        )
    }

    suspend fun setAutoExitFullscreenMode(context: Context, mode: AutoExitFullscreenMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_AUTO_EXIT_FULLSCREEN_MODE] = mode.value
            preferences[KEY_AUTO_EXIT_FULLSCREEN] = mode != AutoExitFullscreenMode.OFF
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

    fun getAppGestureScreenshotEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_APP_GESTURE_SCREENSHOT_ENABLED] ?: false }

    suspend fun setAppGestureScreenshotEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_APP_GESTURE_SCREENSHOT_ENABLED] = enabled
        }
    }

    fun getAppScreenshotGestureMode(context: Context): Flow<AppScreenshotGestureMode> =
        context.settingsDataStore.data.map { preferences ->
            AppScreenshotGestureMode.fromValue(
                preferences[KEY_APP_SCREENSHOT_GESTURE_MODE]
                    ?: AppScreenshotGestureMode.TOP_RIGHT_TWO_FINGER_LONG_PRESS.value
            )
        }

    suspend fun setAppScreenshotGestureMode(context: Context, mode: AppScreenshotGestureMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_APP_SCREENSHOT_GESTURE_MODE] = mode.value
        }
    }

    fun getAppScreenshotCaptureMode(context: Context): Flow<AppScreenshotCaptureMode> =
        context.settingsDataStore.data.map { preferences ->
            AppScreenshotCaptureMode.fromValue(
                preferences[KEY_APP_SCREENSHOT_CAPTURE_MODE]
                    ?: AppScreenshotCaptureMode.FULL_WINDOW.value
            )
        }

    suspend fun setAppScreenshotCaptureMode(context: Context, mode: AppScreenshotCaptureMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_APP_SCREENSHOT_CAPTURE_MODE] = mode.value
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

    fun getShowProfileEditButton(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SHOW_PROFILE_EDIT_BUTTON] ?: false }

    suspend fun setShowProfileEditButton(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SHOW_PROFILE_EDIT_BUTTON] = enabled
        }
    }

    fun getCommentCollapsedReplyPreviewLimit(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizeCommentCollapsedReplyPreviewLimit(
                preferences[KEY_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT]
                    ?: DEFAULT_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT
            )
        }

    suspend fun setCommentCollapsedReplyPreviewLimit(context: Context, value: Int) {
        val normalized = normalizeCommentCollapsedReplyPreviewLimit(value)
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT] = normalized
        }
        context.getSharedPreferences(COMMENT_PREVIEW_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(CACHE_KEY_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT, normalized)
            .apply()
    }

    fun getCommentCollapsedReplyPreviewLimitSync(context: Context): Int {
        return normalizeCommentCollapsedReplyPreviewLimit(
            context.getSharedPreferences(COMMENT_PREVIEW_CACHE_PREFS, Context.MODE_PRIVATE)
                .getInt(
                    CACHE_KEY_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT,
                    DEFAULT_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT
                )
        )
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

    fun getDashSegmentRequestsEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_DASH_SEGMENT_REQUESTS_ENABLED]
                ?: DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED
        }

    suspend fun setDashSegmentRequestsEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DASH_SEGMENT_REQUESTS_ENABLED] = enabled
        }
        PlayerSettingsCache.setDashSegmentRequestsEnabled(context, enabled)
    }

    fun getPlaybackCdnPreference(context: Context): Flow<String> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_PLAYBACK_CDN_PREFERENCE] ?: "base_url"
        }

    suspend fun setPlaybackCdnPreference(context: Context, preference: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PLAYBACK_CDN_PREFERENCE] = preference
        }
    }

    fun getQualitySwitchFailureDialogEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED]
                ?: DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED
        }

    suspend fun setQualitySwitchFailureDialogEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_QUALITY_SWITCH_FAILURE_DIALOG_ENABLED] = enabled
        }
    }

    fun getQualitySwitchFailureDialogOnceEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED]
                ?: DEFAULT_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED
        }

    suspend fun setQualitySwitchFailureDialogOnceEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_QUALITY_SWITCH_FAILURE_DIALOG_ONCE_ENABLED] = enabled
            if (!enabled) {
                preferences.remove(KEY_QUALITY_SWITCH_FAILURE_DIALOG_SHOWN)
            }
        }
    }

    fun getQualitySwitchFailureDialogShown(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_QUALITY_SWITCH_FAILURE_DIALOG_SHOWN] ?: false
        }

    suspend fun markQualitySwitchFailureDialogShown(context: Context) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_QUALITY_SWITCH_FAILURE_DIALOG_SHOWN] = true
        }
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
                    ?: BottomProgressBehavior.ALWAYS_HIDE.value
            )
        }

    suspend fun setBottomProgressBehavior(context: Context, behavior: BottomProgressBehavior) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BOTTOM_PROGRESS_BEHAVIOR] = behavior.value
        }
    }

    /** 进度条上的 PBP 弹幕峰值曲线，默认关闭。 */
    fun getProgressPeakDanmakuEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[KEY_PROGRESS_PEAK_DANMAKU_ENABLED] ?: false
        }

    suspend fun setProgressPeakDanmakuEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_PROGRESS_PEAK_DANMAKU_ENABLED] = enabled
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
    private val KEY_SIDEBAR_EXPANDED = booleanPreferencesKey("sidebar_expanded")
    private val KEY_SIDEBAR_ACCOUNT_SWITCHER_ENABLED =
        booleanPreferencesKey("sidebar_account_switcher_enabled")
    private val KEY_PREDICTIVE_BACK_ENABLED = booleanPreferencesKey("predictive_back_enabled")
    private val KEY_PREDICTIVE_BACK_ANIMATION_STYLE = stringPreferencesKey("predictive_back_animation_style")
    private val KEY_PREDICTIVE_BACK_EXIT_DIRECTION = stringPreferencesKey("predictive_back_exit_direction")
    private val KEY_MIUIX_TRANSITION_BLUR_ENABLED =
        booleanPreferencesKey("miuix_transition_blur_enabled")
    private val KEY_VIDEO_SHARED_RETURN_GESTURE_FOLLOW_ENABLED =
        booleanPreferencesKey("video_shared_return_gesture_follow_enabled")
    
    /**
     *  平板导航模式
     * - false: 使用底栏
     * - true: 使用侧边栏
     *
     * 未写入偏好时：平板/大屏设备默认开启侧边栏（与横屏适配的设备预设一致）；
     * 手机仍默认底栏。手机上即使为 true 也不会生效，因侧栏仍受 WindowSizeClass 门禁约束。
     */
    fun getTabletUseSidebar(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEY_TABLET_NAVIGATION_MODE]
                ?: defaultTabletUseSidebar(isTabletConfiguration(context))
        }

    fun getSidebarAccountSwitcherEnabled(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SIDEBAR_ACCOUNT_SWITCHER_ENABLED] ?: true }

    internal fun mapAppNavigationSettingsFromPreferences(
        preferences: Preferences,
        defaultTabletUseSidebar: Boolean = false
    ): AppNavigationSettings {
        val orderString = preferences[KEY_BOTTOM_BAR_ORDER] ?: DEFAULT_BOTTOM_BAR_ORDER
        val tabsString = preferences[KEY_BOTTOM_BAR_VISIBLE_TABS] ?: DEFAULT_BOTTOM_BAR_VISIBLE_TABS
        val order = orderString.split(",").filter { it.isNotBlank() }
        val visible = tabsString.split(",").filter { it.isNotBlank() }
        return AppNavigationSettings(
            bottomBarVisibilityMode = BottomBarVisibilityMode.fromValue(
                preferences[KEY_BOTTOM_BAR_VISIBILITY_MODE] ?: BottomBarVisibilityMode.ALWAYS_VISIBLE.value
            ),
            orderedVisibleTabIds = resolveOrderedVisibleBottomTabs(order, visible),
            bottomBarItemColors = parseBottomBarItemColors(preferences[KEY_BOTTOM_BAR_ITEM_COLORS] ?: ""),
            bottomBarItemLabels = parseBottomBarItemLabels(
                preferences[bottomBarItemLabelsPreferencesKey].orEmpty()
            ),
            tabletUseSidebar = preferences[KEY_TABLET_NAVIGATION_MODE] ?: defaultTabletUseSidebar,
            sidebarExpanded = preferences[KEY_SIDEBAR_EXPANDED] ?: true,
            sidebarAccountSwitcherEnabled =
                preferences[KEY_SIDEBAR_ACCOUNT_SWITCHER_ENABLED] ?: true,
            predictiveBackEnabled = preferences[KEY_PREDICTIVE_BACK_ENABLED] ?: true,
            predictiveBackAnimationStyle = preferences[KEY_PREDICTIVE_BACK_ANIMATION_STYLE] ?: "miuix",
            predictiveBackExitDirection =
                preferences[KEY_PREDICTIVE_BACK_EXIT_DIRECTION] ?: "always_right",
            miuixTransitionBlurEnabled = preferences[KEY_MIUIX_TRANSITION_BLUR_ENABLED] ?: true,
            miuixPredictiveBackMaxProgressPercent =
                (preferences[miuixPredictiveBackMaxProgressPercentPreferencesKey] ?: 100)
                    .coerceIn(0, 100),
            videoSharedReturnGestureFollowEnabled =
                preferences[KEY_VIDEO_SHARED_RETURN_GESTURE_FOLLOW_ENABLED] ?: true,
        )
    }

    private fun resolveOrderedVisibleBottomTabs(
        order: List<String>,
        visible: List<String>
    ): List<String> {
        val visibleSet = visible.toSet()
        val orderedVisible = order.filter { it in visibleSet }
        val missingVisible = visible.filterNot { it in orderedVisible }
        return orderedVisible + missingVisible
    }

    fun getAppNavigationSettings(context: Context): Flow<AppNavigationSettings> {
        return NavigationSettingsStore.observe(context)
    }

    suspend fun setTabletUseSidebar(context: Context, useSidebar: Boolean) {
        NavigationSettingsStore.setTabletUseSidebar(context, useSidebar)
    }

    suspend fun setSidebarExpanded(context: Context, expanded: Boolean) {
        NavigationSettingsStore.setSidebarExpanded(context, expanded)
    }

    suspend fun setSidebarAccountSwitcherEnabled(context: Context, enabled: Boolean) {
        NavigationSettingsStore.setSidebarAccountSwitcherEnabled(context, enabled)
    }

    suspend fun setPredictiveBackEnabled(context: Context, enabled: Boolean) {
        NavigationSettingsStore.setPredictiveBackEnabled(context, enabled)
    }

    suspend fun setPredictiveBackAnimationStyle(context: Context, style: String) {
        NavigationSettingsStore.setPredictiveBackAnimationStyle(context, style)
    }

    suspend fun setPredictiveBackExitDirection(context: Context, direction: String) {
        NavigationSettingsStore.setPredictiveBackExitDirection(context, direction)
    }

    suspend fun setMiuixTransitionBlurEnabled(context: Context, enabled: Boolean) {
        NavigationSettingsStore.setMiuixTransitionBlurEnabled(context, enabled)
    }

    suspend fun setMiuixPredictiveBackMaxProgressPercent(context: Context, percent: Int) {
        NavigationSettingsStore.setMiuixPredictiveBackMaxProgressPercent(context, percent)
    }

    suspend fun setVideoSharedReturnGestureFollowEnabled(context: Context, enabled: Boolean) {
        NavigationSettingsStore.setVideoSharedReturnGestureFollowEnabled(context, enabled)
    }

    fun getFullScreenSwipeBackEnabled(context: Context): Flow<Boolean> =
        NavigationSettingsStore.getFullScreenSwipeBackEnabled(context)

    suspend fun setFullScreenSwipeBackEnabled(context: Context, enabled: Boolean) {
        NavigationSettingsStore.setFullScreenSwipeBackEnabled(context, enabled)
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

    // ========== [新增] 动态 Feed 布局模式（对齐 BiliPai dynamicsWaterfallFlow） ==========

    private val KEY_DYNAMIC_FEED_LAYOUT_MODE = intPreferencesKey("dynamic_feed_layout_mode")

    /**
     *  动态 Feed 布局模式
     * - 0: 瀑布流（默认，多列自适应）
     * - 1: 列表（单列居中）
     */
    enum class DynamicFeedLayoutMode(val value: Int, val label: String) {
        WATERFALL(0, "瀑布流"),
        LIST(1, "列表");

        companion object {
            fun fromValue(value: Int): DynamicFeedLayoutMode = entries.find { it.value == value } ?: WATERFALL
        }
    }

    fun getDynamicFeedLayoutMode(context: Context): Flow<DynamicFeedLayoutMode> = context.settingsDataStore.data
        .map { preferences ->
            DynamicFeedLayoutMode.fromValue(preferences[KEY_DYNAMIC_FEED_LAYOUT_MODE] ?: 0)
        }

    suspend fun setDynamicFeedLayoutMode(context: Context, mode: DynamicFeedLayoutMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_DYNAMIC_FEED_LAYOUT_MODE] = mode.value
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
            StringShareablePreferenceDefinition(KEY_THEME_SELECTION, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_THEME_MODE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_DARK_THEME_STYLE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_APP_LANGUAGE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_DYNAMIC_COLOR, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_MD3_COLOR_SOURCE, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_MD3_CUSTOM_COLOR_HEX, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_THEME_ROLE_OVERRIDES_ENABLED, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_LIGHT_BACKGROUND, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_LIGHT_PRIMARY_TEXT, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_LIGHT_SECONDARY_TEXT, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_LIGHT_CONTROL_ACCENT, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_DARK_BACKGROUND, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_DARK_PRIMARY_TEXT, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_DARK_SECONDARY_TEXT, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_DARK_CONTROL_ACCENT, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_COLOR_STYLE, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_THEME_COLOR_SPEC, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(
                KEY_SINGLE_CHOICE_PRESENTATION,
                SettingsShareSection.APPEARANCE,
            ),
            IntShareablePreferenceDefinition(KEY_THEME_COLOR_INDEX, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_APP_ICON, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_APP_ICON_APPEARANCE, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_APP_ICON_STYLE, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_APP_LIST_ITEM_STYLE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_BOTTOM_BAR_FLOATING, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(
                KEY_NAVIGATION_ICON_CROSS_SCALE_ENABLED,
                SettingsShareSection.APPEARANCE,
            ),
            IntShareablePreferenceDefinition(KEY_BOTTOM_BAR_LABEL_MODE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_TOP_TAB_LABEL_MODE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_HOME_TOP_RIGHT_ACTION, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_TOP_TAB_ORDER, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_TOP_TAB_VISIBLE_TABS, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_DYNAMIC_TAB_VISIBLE_TABS, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_HEADER_BLUR_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_BOTTOM_BAR_BLUR_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(
                KEY_TOP_BAR_LIQUID_GLASS_ENABLED,
                SettingsShareSection.APPEARANCE,
            ),
            BooleanShareablePreferenceDefinition(
                KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED,
                SettingsShareSection.APPEARANCE,
            ),
            BooleanShareablePreferenceDefinition(KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_BOTTOM_BAR_SEARCH_LAYOUT_MODE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(
                KEY_ANDROID_NATIVE_LIQUID_GLASS_ENABLED,
                SettingsShareSection.APPEARANCE
            ),
            BooleanShareablePreferenceDefinition(KEY_LIQUID_GLASS_ENABLED, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_LIQUID_GLASS_STYLE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_LIQUID_GLASS_MODE, SettingsShareSection.APPEARANCE),
            FloatShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_STRENGTH,
                SettingsShareSection.APPEARANCE,
            ),
            FloatShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_PROGRESS,
                SettingsShareSection.APPEARANCE,
            ),
            IntShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_ADVANCED_PRESET,
                SettingsShareSection.APPEARANCE,
            ),
            FloatShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_RADIUS,
                SettingsShareSection.APPEARANCE,
            ),
            FloatShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_EXTENT,
                SettingsShareSection.APPEARANCE,
            ),
            FloatShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_CURVE,
                SettingsShareSection.APPEARANCE,
            ),
            IntShareablePreferenceDefinition(
                liquidGlassReadabilityModePreferencesKey,
                SettingsShareSection.APPEARANCE,
            ),
            FloatShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_CONTENT_READABILITY,
                SettingsShareSection.APPEARANCE,
            ),
            FloatShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_CHROMATIC_ABERRATION,
                SettingsShareSection.APPEARANCE,
            ),
            FloatShareablePreferenceDefinition(
                KEY_LIQUID_GLASS_CONTENT_DISTORTION,
                SettingsShareSection.APPEARANCE,
            ),
            StringShareablePreferenceDefinition(KEY_BLUR_INTENSITY, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_DISPLAY_MODE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_GRID_COLUMN_COUNT, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(
                KEY_HOME_FEED_CARD_WIDTH_PRESET,
                SettingsShareSection.APPEARANCE
            ),
            IntShareablePreferenceDefinition(KEY_HOME_FEED_CARD_STYLE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_HOME_HERO_CAROUSEL_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_HOME_HERO_CAROUSEL_AUTOPLAY_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_CARD_ANIMATION_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_UI_ENTRANCE_ANIMATION_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_CARD_TRANSITION_ENABLED, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(
                KEY_LIVE_SURFACE_CARD_TRANSITION_ENABLED,
                SettingsShareSection.APPEARANCE
            ),
            BooleanShareablePreferenceDefinition(
                KEY_VIDEO_TRANSITION_REALTIME_BLUR_ENABLED,
                SettingsShareSection.APPEARANCE
            ),
            BooleanShareablePreferenceDefinition(
                KEY_MIUIX_TRANSITION_BLUR_ENABLED,
                SettingsShareSection.APPEARANCE,
            ),
            IntShareablePreferenceDefinition(
                miuixPredictiveBackMaxProgressPercentPreferencesKey,
                SettingsShareSection.APPEARANCE,
            ),
            BooleanShareablePreferenceDefinition(
                KEY_VIDEO_SHARED_RETURN_GESTURE_FOLLOW_ENABLED,
                SettingsShareSection.APPEARANCE,
            ),
            IntShareablePreferenceDefinition(KEY_VIDEO_SHARED_TRANSITION_SPEED, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(
                KEY_VIDEO_SHARED_TRANSITION_CUSTOM_DURATION_MILLIS,
                SettingsShareSection.APPEARANCE
            ),
            BooleanShareablePreferenceDefinition(KEY_COMPACT_VIDEO_STATS_ON_COVER, SettingsShareSection.APPEARANCE),
            StringShareablePreferenceDefinition(KEY_HOME_WALLPAPER_URI, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_HOME_WALLPAPER_EFFECT_MODE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_HOME_UP_BADGES_VISIBLE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_HOME_UP_AVATARS_VISIBLE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_FULL_VIDEO_CARD_CONTENT_VISIBLE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_HOME_VIDEO_DURATION_BADGES_VISIBLE, SettingsShareSection.APPEARANCE),
            IntShareablePreferenceDefinition(KEY_HOME_DURATION_STYLE, SettingsShareSection.APPEARANCE),
            BooleanShareablePreferenceDefinition(KEY_SHOW_PROFILE_EDIT_BUTTON, SettingsShareSection.APPEARANCE),

            BooleanShareablePreferenceDefinition(KEY_AUTO_PLAY, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_PLAYBACK_COMPLETION_BEHAVIOR, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_HW_DECODE, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_BG_PLAY, SettingsShareSection.PLAYBACK),
            FloatShareablePreferenceDefinition(KEY_DEFAULT_PLAYBACK_SPEED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_REMEMBER_LAST_PLAYBACK_SPEED, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_COMMENT_DEFAULT_SORT_MODE, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_COMMENT_FRAUD_DETECTION_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_COMMENT_MEMBER_DECORATIONS_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_IMAGE_PREVIEW_LONG_PRESS_SAVE_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_IMAGE_PREVIEW_3D_PAGE_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_STOP_PLAYBACK_ON_EXIT, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_BACKGROUND_PLAYBACK_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_AUDIO_FOCUS_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_VIDEO_AI_SUMMARY_ENTRY_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_VIDEO_NOTE_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_VIDEO_NOTE_DEFAULT_COLLAPSED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_VIDEO_INFO_DEFAULT_EXPANDED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_CLICK_TO_PLAY, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_RESUME_PLAYBACK_PROMPT_ENABLED, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(
                KEY_SPACE_PLAYED_VIDEO_LOCATE_PROMPT_ENABLED,
                SettingsShareSection.PLAYBACK
            ),
            BooleanShareablePreferenceDefinition(KEY_AUTO_ROTATE_ENABLED, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_WIFI_QUALITY, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_MOBILE_QUALITY, SettingsShareSection.PLAYBACK),
            StringShareablePreferenceDefinition(KEY_VIDEO_CODEC, SettingsShareSection.PLAYBACK),
            StringShareablePreferenceDefinition(KEY_VIDEO_SECOND_CODEC, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_AUDIO_QUALITY, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(defaultAudioQualityPreferenceKey, SettingsShareSection.PLAYBACK),
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
            BooleanShareablePreferenceDefinition(
                KEY_PROGRESS_PEAK_DANMAKU_ENABLED,
                SettingsShareSection.PLAYBACK,
            ),
            BooleanShareablePreferenceDefinition(KEY_HORIZONTAL_ADAPTATION, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_HIDE_VIDEO_PAGE_STATUS_BAR, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_TABLET_COMMENT_PANEL_WIDTH_PRESET, SettingsShareSection.PLAYBACK),
            BooleanShareablePreferenceDefinition(KEY_SHOW_ONLINE_COUNT, SettingsShareSection.PLAYBACK),
            IntShareablePreferenceDefinition(KEY_COMMENT_COLLAPSED_REPLY_PREVIEW_LIMIT, SettingsShareSection.PLAYBACK),

            BooleanShareablePreferenceDefinition(KEY_HAPTIC_FEEDBACK_ENABLED, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(
                KEY_GLOBAL_TEXT_TAP_COPY_ENABLED,
                SettingsShareSection.GESTURE,
            ),
            FloatShareablePreferenceDefinition(KEY_GESTURE_SENSITIVITY, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SLIDE_VOLUME_BRIGHTNESS_ENABLED, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SET_SYSTEM_BRIGHTNESS, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_DOUBLE_TAP_SEEK_ENABLED, SettingsShareSection.GESTURE),
            IntShareablePreferenceDefinition(KEY_SEEK_FORWARD_SECONDS, SettingsShareSection.GESTURE),
            IntShareablePreferenceDefinition(KEY_SEEK_BACKWARD_SECONDS, SettingsShareSection.GESTURE),
            FloatShareablePreferenceDefinition(KEY_LONG_PRESS_SPEED, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_LONG_PRESS_SPEED_LOCK_ENABLED, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_LONG_PRESS_SPEED_HINT_HIDDEN, SettingsShareSection.GESTURE),
            FloatShareablePreferenceDefinition(
                KEY_SUBTITLE_VERTICAL_OFFSET_FRACTION,
                SettingsShareSection.GESTURE
            ),
            FloatShareablePreferenceDefinition(
                KEY_SUBTITLE_PORTRAIT_VERTICAL_OFFSET_FRACTION,
                SettingsShareSection.GESTURE
            ),
            BooleanShareablePreferenceDefinition(
                KEY_SUBTITLE_POSITION_LOCKED,
                SettingsShareSection.GESTURE
            ),
            BooleanShareablePreferenceDefinition(KEY_PIP_NO_DANMAKU, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_DOUBLE_TAP_LIKE, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_SWIPE_HIDE_PLAYER, SettingsShareSection.GESTURE),
            IntShareablePreferenceDefinition(KEY_PORTRAIT_PLAYER_COLLAPSE_MODE, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_PAUSE_ON_PLAYER_COLLAPSE, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_PORTRAIT_SWIPE_TO_FULLSCREEN, SettingsShareSection.GESTURE),
            BooleanShareablePreferenceDefinition(KEY_CENTER_SWIPE_TO_FULLSCREEN, SettingsShareSection.GESTURE),
            IntShareablePreferenceDefinition(KEY_INLINE_SWIPE_SEEK_SECONDS, SettingsShareSection.GESTURE),
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
            IntShareablePreferenceDefinition(KEY_DANMAKU_FONT_WEIGHT, SettingsShareSection.DANMAKU),
            FloatShareablePreferenceDefinition(KEY_DANMAKU_STROKE_WIDTH, SettingsShareSection.DANMAKU),
            FloatShareablePreferenceDefinition(KEY_DANMAKU_LINE_HEIGHT, SettingsShareSection.DANMAKU),
            FloatShareablePreferenceDefinition(
                KEY_DANMAKU_SCROLL_DURATION_SECONDS,
                SettingsShareSection.DANMAKU
            ),
            FloatShareablePreferenceDefinition(
                KEY_DANMAKU_STATIC_DURATION_SECONDS,
                SettingsShareSection.DANMAKU
            ),
            BooleanShareablePreferenceDefinition(
                KEY_DANMAKU_SCROLL_FIXED_VELOCITY,
                SettingsShareSection.DANMAKU
            ),
            BooleanShareablePreferenceDefinition(
                KEY_DANMAKU_STATIC_TO_SCROLL,
                SettingsShareSection.DANMAKU
            ),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_MASSIVE_MODE, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_SCROLL, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_TOP, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_BOTTOM, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_COLORFUL, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_ALLOW_SPECIAL, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_BLOCK_ATTENTION_COMMANDS, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_SMART_OCCLUSION, SettingsShareSection.DANMAKU),
            StringShareablePreferenceDefinition(KEY_DANMAKU_BLOCK_RULES, SettingsShareSection.DANMAKU),
            BooleanShareablePreferenceDefinition(KEY_DANMAKU_MERGE_DUPLICATES, SettingsShareSection.DANMAKU),
            IntShareablePreferenceDefinition(KEY_DANMAKU_DUPLICATE_MERGE_WINDOW_MS, SettingsShareSection.DANMAKU),
            IntShareablePreferenceDefinition(
                KEY_DANMAKU_DUPLICATE_MERGE_COUNT_THRESHOLD,
                SettingsShareSection.DANMAKU
            ),

            StringShareablePreferenceDefinition(KEY_BOTTOM_BAR_ORDER, SettingsShareSection.NAVIGATION),
            StringShareablePreferenceDefinition(KEY_BOTTOM_BAR_VISIBLE_TABS, SettingsShareSection.NAVIGATION),
            StringShareablePreferenceDefinition(KEY_BOTTOM_BAR_ITEM_COLORS, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(KEY_BOTTOM_BAR_VISIBILITY_MODE, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(KEY_HOME_TOP_LAYOUT_ORDER, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(KEY_HOME_HEADER_COLLAPSE_MODE, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(
                KEY_COMMON_LIST_HEADER_COLLAPSE_MODE,
                SettingsShareSection.NAVIGATION
            ),
            BooleanShareablePreferenceDefinition(KEY_HEADER_COLLAPSE_ENABLED, SettingsShareSection.NAVIGATION),
            BooleanShareablePreferenceDefinition(KEY_SHOW_PGC_TIMELINE, SettingsShareSection.NAVIGATION),
            BooleanShareablePreferenceDefinition(KEY_TABLET_NAVIGATION_MODE, SettingsShareSection.NAVIGATION),
            BooleanShareablePreferenceDefinition(KEY_SIDEBAR_EXPANDED, SettingsShareSection.NAVIGATION),
            BooleanShareablePreferenceDefinition(
                KEY_SIDEBAR_ACCOUNT_SWITCHER_ENABLED,
                SettingsShareSection.NAVIGATION
            ),
            IntShareablePreferenceDefinition(KEY_DYNAMIC_PAGE_LAYOUT_DIRECTION, SettingsShareSection.NAVIGATION),
            IntShareablePreferenceDefinition(KEY_FEED_API_TYPE, SettingsShareSection.NAVIGATION),
            BooleanShareablePreferenceDefinition(KEY_INCREMENTAL_TIMELINE_REFRESH, SettingsShareSection.NAVIGATION),
            BooleanShareablePreferenceDefinition(
                KEY_DYNAMIC_IMAGE_PREVIEW_TEXT_VISIBLE,
                SettingsShareSection.NAVIGATION
            ),
            BooleanShareablePreferenceDefinition(
                KEY_DYNAMIC_ALL_TAB_HORIZONTAL_USER_LIST_VISIBLE,
                SettingsShareSection.NAVIGATION
            ),
            BooleanShareablePreferenceDefinition(
                KEY_DYNAMIC_TOP_BAR_COLLAPSE_ON_SCROLL,
                SettingsShareSection.NAVIGATION
            ),
            IntShareablePreferenceDefinition(KEY_HOME_REFRESH_COUNT, SettingsShareSection.NAVIGATION)
        )
    }

    fun getShareableSettingsEntryDefinitions(): List<SettingsShareEntryDefinition> {
        return shareableSettingDefinitions.map { it.entryDefinition }
    }

    private val liquidGlassShareableStorageKeys: Set<String> by lazy {
        setOf(
            KEY_ANDROID_NATIVE_LIQUID_GLASS_ENABLED.name,
            KEY_LIQUID_GLASS_ENABLED.name,
            KEY_TOP_BAR_LIQUID_GLASS_ENABLED.name,
            KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED.name,
            KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED.name,
            KEY_LIQUID_GLASS_STYLE.name,
            KEY_LIQUID_GLASS_MODE.name,
            KEY_LIQUID_GLASS_STRENGTH.name,
            KEY_LIQUID_GLASS_PROGRESS.name,
            KEY_LIQUID_GLASS_ADVANCED_PRESET.name,
            KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_RADIUS.name,
            KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_EXTENT.name,
            KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_CURVE.name,
            liquidGlassReadabilityModePreferencesKey.name,
            KEY_LIQUID_GLASS_CONTENT_READABILITY.name,
            KEY_LIQUID_GLASS_CHROMATIC_ABERRATION.name,
            KEY_LIQUID_GLASS_CONTENT_DISTORTION.name,
        )
    }

    fun getLiquidGlassShareableSettingsEntryDefinitions(): List<SettingsShareEntryDefinition> {
        return getShareableSettingsEntryDefinitions().filter { definition ->
            definition.storageKey in liquidGlassShareableStorageKeys
        }
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

    suspend fun exportLiquidGlassShareableSettingsSnapshot(
        context: Context,
    ): Map<String, JsonElement> {
        val preferences = context.settingsDataStore.data.first()
        val legacyEnabled = preferences[KEY_LIQUID_GLASS_ENABLED] ?: false
        val topBarEnabled = preferences[KEY_TOP_BAR_LIQUID_GLASS_ENABLED] ?: false
        val homeSearchEnabled = preferences[KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED]
            ?: topBarEnabled
        val bottomBarEnabled = preferences[KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED]
            ?: legacyEnabled
        val progress = resolveStoredLiquidGlassProgress(
            progress = preferences[KEY_LIQUID_GLASS_PROGRESS],
            legacyModeValue = preferences[KEY_LIQUID_GLASS_MODE],
            legacyStrength = preferences[KEY_LIQUID_GLASS_STRENGTH],
            legacyStyleValue = preferences[KEY_LIQUID_GLASS_STYLE],
        )
        val advancedSettings = resolveLiquidGlassAdvancedSettings(
            presetValue = preferences[KEY_LIQUID_GLASS_ADVANCED_PRESET],
            progressiveBlurRadius = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_RADIUS],
            progressiveBlurExtent = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_EXTENT],
            progressiveBlurCurve = preferences[KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_CURVE],
            contentReadability = preferences[KEY_LIQUID_GLASS_CONTENT_READABILITY],
            chromaticAberration = preferences[KEY_LIQUID_GLASS_CHROMATIC_ABERRATION],
            contentDistortion = preferences[KEY_LIQUID_GLASS_CONTENT_DISTORTION],
        )
        return linkedMapOf(
            KEY_ANDROID_NATIVE_LIQUID_GLASS_ENABLED.name to JsonPrimitive(
                preferences[KEY_ANDROID_NATIVE_LIQUID_GLASS_ENABLED] ?: true
            ),
            KEY_LIQUID_GLASS_ENABLED.name to JsonPrimitive(bottomBarEnabled),
            KEY_TOP_BAR_LIQUID_GLASS_ENABLED.name to JsonPrimitive(topBarEnabled),
            KEY_HOME_SEARCH_LIQUID_GLASS_ENABLED.name to JsonPrimitive(homeSearchEnabled),
            KEY_BOTTOM_BAR_LIQUID_GLASS_ENABLED.name to JsonPrimitive(bottomBarEnabled),
            KEY_LIQUID_GLASS_STYLE.name to JsonPrimitive(
                resolveLegacyLiquidGlassStyleFromProgress(progress).value
            ),
            KEY_LIQUID_GLASS_MODE.name to JsonPrimitive(
                resolveLiquidGlassModeFromProgress(progress).value
            ),
            KEY_LIQUID_GLASS_STRENGTH.name to JsonPrimitive(
                resolveLiquidGlassStrengthFromProgress(progress)
            ),
            KEY_LIQUID_GLASS_PROGRESS.name to JsonPrimitive(progress),
            KEY_LIQUID_GLASS_ADVANCED_PRESET.name to JsonPrimitive(
                advancedSettings.preset.value
            ),
            KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_RADIUS.name to JsonPrimitive(
                advancedSettings.progressiveBlurRadius
            ),
            KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_EXTENT.name to JsonPrimitive(
                advancedSettings.progressiveBlurExtent
            ),
            KEY_LIQUID_GLASS_PROGRESSIVE_BLUR_CURVE.name to JsonPrimitive(
                advancedSettings.progressiveBlurCurve
            ),
            liquidGlassReadabilityModePreferencesKey.name to JsonPrimitive(
                preferences[liquidGlassReadabilityModePreferencesKey]
                    ?: LiquidGlassReadabilityMode.STABLE.value
            ),
            KEY_LIQUID_GLASS_CONTENT_READABILITY.name to JsonPrimitive(
                advancedSettings.contentReadability
            ),
            KEY_LIQUID_GLASS_CHROMATIC_ABERRATION.name to JsonPrimitive(
                advancedSettings.chromaticAberration
            ),
            KEY_LIQUID_GLASS_CONTENT_DISTORTION.name to JsonPrimitive(
                advancedSettings.contentDistortion
            ),
        )
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
