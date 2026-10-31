package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal data class ContentImageSettings(
    val listQuality: Int = 0,
    val previewQuality: Int = 0,
    val darkDimPercent: Int = 0,
)

internal enum class ScrollFeedbackPreset(val value: Int, val label: String) {
    STANDARD(0, "标准"), GENTLE(1, "稳重"), LIVELY(2, "灵动");
    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

internal object ContentDisplayPreferences {
    val listQualityKey = intPreferencesKey("content_list_image_quality")
    val previewQualityKey = intPreferencesKey("content_preview_image_quality")
    val darkDimKey = intPreferencesKey("content_dark_image_dim_percent")
    val springKey = intPreferencesKey("scroll_feedback_preset")
    val rememberPlayerAdjustmentsKey = booleanPreferencesKey("remember_player_adjustments")
    val rotationSensitivityKey = intPreferencesKey("player_rotation_sensitivity")
    val playerControlsTimeoutKey = intPreferencesKey("player_controls_timeout_seconds")
    val dynamicVisibilityCheckKey = booleanPreferencesKey("dynamic_auto_visibility_check")
    val keepRecommendKey = booleanPreferencesKey("recommend_keep_old_content")
    private val legacyIncremental = booleanPreferencesKey("incremental_timeline_refresh")
    private val legacyTip = booleanPreferencesKey("home_refresh_tip_visible")
    private val qualityValues = setOf(0, 40, 70, 90, 100)
    private val dimValues = setOf(0, 10, 20, 30)

    @Volatile var rememberAdjustmentsSnapshot: Boolean? = null
        private set

    @Volatile var imageSnapshot = ContentImageSettings()
        private set

    fun normalizeQuality(value: Int): Int = value.takeIf { it in qualityValues } ?: 0
    fun normalizeDim(value: Int): Int = value.takeIf { it in dimValues } ?: 0

    fun images(context: Context) = context.settingsDataStore.data.map { prefs ->
        ContentImageSettings(
            normalizeQuality(prefs[listQualityKey] ?: 0),
            normalizeQuality(prefs[previewQualityKey] ?: 0),
            normalizeDim(prefs[darkDimKey] ?: 0),
        )
    }.distinctUntilChanged()

    fun publishImageSnapshot(settings: ContentImageSettings) { imageSnapshot = settings }

    fun spring(context: Context) = context.settingsDataStore.data
        .map { ScrollFeedbackPreset.fromValue(it[springKey] ?: 0) }.distinctUntilChanged()

    // 首次采用独立开关时延续旧版实际行为，不改变动态流原有的增量配置。
    fun keepRecommend(context: Context) = context.settingsDataStore.data.map { prefs ->
        prefs[keepRecommendKey] ?: resolveLegacyRecommendRetention(prefs[legacyIncremental], prefs[legacyTip])
    }.distinctUntilChanged().onStart {
        context.settingsDataStore.edit { prefs ->
            if (prefs[keepRecommendKey] == null) {
                prefs[keepRecommendKey] = resolveLegacyRecommendRetention(prefs[legacyIncremental], prefs[legacyTip])
            }
        }
    }

    suspend fun setKeepRecommend(context: Context, keep: Boolean) {
        context.settingsDataStore.edit { it[keepRecommendKey] = keep }
    }

    suspend fun setImage(context: Context, key: Preferences.Key<Int>, value: Int) {
        require(key == listQualityKey || key == previewQualityKey || key == darkDimKey)
        context.settingsDataStore.edit { it[key] = if (key == darkDimKey) normalizeDim(value) else normalizeQuality(value) }
    }

    fun syncRememberPlayerAdjustments(preferences: Preferences) {
        rememberAdjustmentsSnapshot = preferences[rememberPlayerAdjustmentsKey] ?: true
    }

    fun rememberPlayerAdjustments(context: Context) = context.settingsDataStore.data
        .map { (it[rememberPlayerAdjustmentsKey] ?: true).also { value -> rememberAdjustmentsSnapshot = value } }
        .distinctUntilChanged()

    suspend fun setRememberPlayerAdjustments(context: Context, remember: Boolean) {
        context.settingsDataStore.edit { it[rememberPlayerAdjustmentsKey] = remember }
        rememberAdjustmentsSnapshot = remember
    }

    fun rotationSensitivity(context: Context) = context.settingsDataStore.data.map {
        com.android.purebilibili.core.util.PlayerRotationSensitivity.fromValue(it[rotationSensitivityKey] ?: 0)
    }.distinctUntilChanged()

    suspend fun setRotationSensitivity(context: Context, value: com.android.purebilibili.core.util.PlayerRotationSensitivity) {
        context.settingsDataStore.edit { it[rotationSensitivityKey] = value.value }
    }

    fun playerControlsTimeout(context: Context) = context.settingsDataStore.data.map {
        normalizePlayerControlsTimeout(it[playerControlsTimeoutKey] ?: 0)
    }.distinctUntilChanged()

    fun normalizePlayerControlsTimeout(value: Int): Int = value.takeIf { it in setOf(-1, 0, 8, 15, 30) } ?: 0

    suspend fun setPlayerControlsTimeout(context: Context, seconds: Int) {
        context.settingsDataStore.edit { it[playerControlsTimeoutKey] = normalizePlayerControlsTimeout(seconds) }
    }

    fun dynamicVisibilityCheck(context: Context) = context.settingsDataStore.data
        .map { it[dynamicVisibilityCheckKey] ?: false }.distinctUntilChanged()

    suspend fun setDynamicVisibilityCheck(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicVisibilityCheckKey] = enabled }
    }

    suspend fun setSpring(context: Context, preset: ScrollFeedbackPreset) {
        context.settingsDataStore.edit { it[springKey] = preset.value }
    }
}

internal fun resolveLegacyRecommendRetention(incremental: Boolean?, tip: Boolean?): Boolean =
    (incremental ?: false) || (tip ?: true)
