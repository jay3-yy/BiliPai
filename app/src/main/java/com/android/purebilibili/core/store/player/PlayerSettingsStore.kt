package com.android.purebilibili.core.store.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.resolvePreferredPlaybackSpeed as resolvePreferredPlaybackSpeedPolicy
import com.android.purebilibili.core.store.DEFAULT_LONG_PRESS_SPEED
import com.android.purebilibili.core.store.nearestPlaybackSpeed
import com.android.purebilibili.core.store.normalizeLongPressSpeed
import com.android.purebilibili.core.store.normalizePlaybackSpeedOptions
import com.android.purebilibili.core.store.resolvePlaybackSpeedOptions
import com.android.purebilibili.core.store.normalizePlaybackSpeed as normalizePlaybackSpeedPolicy
import com.android.purebilibili.core.store.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

const val DEFAULT_AUDIO_QUALITY_FOLLOW_LAST = -2

internal val defaultAudioQualityPreferenceKey = intPreferencesKey("default_audio_quality")
internal val longPressSpeedPreferenceKey = floatPreferencesKey("long_press_speed")
internal val playbackSpeedOptionsPreferenceKey = stringPreferencesKey("playback_speed_options")

object PlayerSettingsStore {
    enum class PlayerInsightMode {
        OFF,
        SMART,
        ALWAYS
    }

    private val keyDefaultPlaybackSpeed = floatPreferencesKey("default_playback_speed")
    private val keyRememberLastPlaybackSpeed = booleanPreferencesKey("remember_last_playback_speed")
    private val keyLastPlaybackSpeed = floatPreferencesKey("last_playback_speed")
    private val keyPreferredPlayerVolume = floatPreferencesKey("preferred_player_volume")
    private val keyPlayerInsightMode = stringPreferencesKey("player_insight_mode")
    private val keyNativeMiuixPlayerPopups = booleanPreferencesKey("native_miuix_player_popups")
    private const val playbackSpeedCachePrefs = "playback_speed_cache"
    private const val cacheKeyDefaultPlaybackSpeed = "default_speed"
    private const val cacheKeyRememberLastSpeed = "remember_last_speed"
    private const val cacheKeyLastPlaybackSpeed = "last_speed"
    private const val cacheKeyPreferredPlayerVolume = "preferred_player_volume"
    private const val audioQualityCachePrefs = "quality_settings"
    private const val cacheKeyAudioQuality = "audio_quality"
    private const val cacheKeyDefaultAudioQuality = "default_audio_quality"
    private const val legacyAppPrefs = "app_prefs"
    private const val legacyShowStatsKey = "show_stats"
    private const val cachePlayerInsightModeKey = "player_insight_mode_cache"

    const val PLAYER_VOLUME_STEP = 0.02f

    fun getNativeMiuixPlayerPopups(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[keyNativeMiuixPlayerPopups] ?: true
        }

    suspend fun setNativeMiuixPlayerPopups(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyNativeMiuixPlayerPopups] = enabled
        }
    }

    fun normalizePlayerVolume(volume: Float): Float {
        val stepCount = (volume.coerceIn(0f, 1f) / PLAYER_VOLUME_STEP).toInt()
        val lower = stepCount * PLAYER_VOLUME_STEP
        val upper = ((stepCount + 1) * PLAYER_VOLUME_STEP).coerceAtMost(1f)
        return if (volume - lower < upper - volume) lower else upper
    }

    fun normalizePlaybackSpeed(speed: Float): Float {
        return normalizePlaybackSpeedPolicy(speed)
    }

    fun resolvePreferredPlaybackSpeed(
        defaultSpeed: Float,
        rememberLastSpeed: Boolean,
        lastSpeed: Float
    ): Float {
        return resolvePreferredPlaybackSpeedPolicy(
            defaultSpeed = defaultSpeed,
            rememberLastSpeed = rememberLastSpeed,
            lastSpeed = lastSpeed
        )
    }

    internal fun playbackSpeedOptions(preferences: Preferences): List<Float> =
        resolvePlaybackSpeedOptions(
            storedValues = preferences[playbackSpeedOptionsPreferenceKey],
            legacyDefaultSpeed = preferences[keyDefaultPlaybackSpeed] ?: 1f,
            legacyLastSpeed = preferences[keyLastPlaybackSpeed] ?: 1f
        )

    fun getPlaybackSpeedOptions(context: Context): Flow<List<Float>> =
        context.settingsDataStore.data.map(::playbackSpeedOptions)

    suspend fun addPlaybackSpeedOption(context: Context, speed: Float) {
        updatePlaybackSpeedOptions(context) { options -> options + speed }
    }

    suspend fun removePlaybackSpeedOption(context: Context, speed: Float) {
        updatePlaybackSpeedOptions(context) { options -> options - speed }
    }

    private suspend fun updatePlaybackSpeedOptions(
        context: Context,
        change: (List<Float>) -> List<Float>
    ) {
        val preferences = context.settingsDataStore.edit { values ->
            val options = normalizePlaybackSpeedOptions(change(playbackSpeedOptions(values)))
            values[playbackSpeedOptionsPreferenceKey] = options.joinToString(",")
            reconcilePlaybackSpeedSelections(values, options)
        }
        syncPlaybackSpeedCache(context, preferences)
    }

    internal fun reconcilePlaybackSpeedSelections(
        values: MutablePreferences,
        options: List<Float> = playbackSpeedOptions(values)
    ) {
        values[keyDefaultPlaybackSpeed] =
            nearestPlaybackSpeed(values[keyDefaultPlaybackSpeed] ?: 1f, options)
        values[longPressSpeedPreferenceKey] =
            normalizeLongPressSpeed(values[longPressSpeedPreferenceKey] ?: DEFAULT_LONG_PRESS_SPEED)
        values[keyLastPlaybackSpeed] =
            nearestPlaybackSpeed(values[keyLastPlaybackSpeed] ?: 1f, options)
    }

    fun getLongPressSpeed(context: Context): Flow<Float> =
        context.settingsDataStore.data.map { preferences ->
            normalizeLongPressSpeed(preferences[longPressSpeedPreferenceKey] ?: DEFAULT_LONG_PRESS_SPEED)
        }

    suspend fun setLongPressSpeed(context: Context, speed: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[longPressSpeedPreferenceKey] = normalizeLongPressSpeed(speed)
        }
    }

    fun getDefaultPlaybackSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            nearestPlaybackSpeed(
                preferences[keyDefaultPlaybackSpeed] ?: 1f, playbackSpeedOptions(preferences)
            )
        }

    suspend fun setDefaultPlaybackSpeed(context: Context, speed: Float) {
        val preferences = context.settingsDataStore.edit { values ->
            values[keyDefaultPlaybackSpeed] = nearestPlaybackSpeed(speed, playbackSpeedOptions(values))
        }
        syncPlaybackSpeedCache(context, preferences)
    }

    internal fun syncPlaybackSpeedCache(context: Context, preferences: Preferences) {
        val options = playbackSpeedOptions(preferences)
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putFloat(
                cacheKeyDefaultPlaybackSpeed,
                nearestPlaybackSpeed(preferences[keyDefaultPlaybackSpeed] ?: 1f, options)
            )
            .putBoolean(cacheKeyRememberLastSpeed, preferences[keyRememberLastPlaybackSpeed] ?: false)
            .putFloat(
                cacheKeyLastPlaybackSpeed,
                nearestPlaybackSpeed(preferences[keyLastPlaybackSpeed] ?: 1f, options)
            )
            .apply()
    }

    fun getRememberLastPlaybackSpeed(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[keyRememberLastPlaybackSpeed] ?: false }

    suspend fun setRememberLastPlaybackSpeed(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyRememberLastPlaybackSpeed] = enabled
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(cacheKeyRememberLastSpeed, enabled)
            .apply()
    }

    fun getLastPlaybackSpeed(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            nearestPlaybackSpeed(
                preferences[keyLastPlaybackSpeed] ?: 1f, playbackSpeedOptions(preferences)
            )
        }

    suspend fun setLastPlaybackSpeed(context: Context, speed: Float) {
        val preferences = context.settingsDataStore.edit { values ->
            values[keyLastPlaybackSpeed] = nearestPlaybackSpeed(speed, playbackSpeedOptions(values))
        }
        syncPlaybackSpeedCache(context, preferences)
    }

    fun getPreferredPlaybackSpeed(context: Context): Flow<Float> = combine(
        getDefaultPlaybackSpeed(context),
        getRememberLastPlaybackSpeed(context),
        getLastPlaybackSpeed(context)
    ) { defaultSpeed, rememberLast, lastSpeed ->
        resolvePreferredPlaybackSpeed(
            defaultSpeed = defaultSpeed,
            rememberLastSpeed = rememberLast,
            lastSpeed = lastSpeed
        )
    }

    fun getPreferredPlaybackSpeedSync(context: Context): Float {
        val prefs = context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
        val defaultSpeed = normalizePlaybackSpeed(prefs.getFloat(cacheKeyDefaultPlaybackSpeed, 1.0f))
        val rememberLast = prefs.getBoolean(cacheKeyRememberLastSpeed, false)
        val lastSpeed = normalizePlaybackSpeed(prefs.getFloat(cacheKeyLastPlaybackSpeed, 1.0f))
        return resolvePreferredPlaybackSpeed(
            defaultSpeed = defaultSpeed,
            rememberLastSpeed = rememberLast,
            lastSpeed = lastSpeed
        )
    }

    fun getPreferredPlayerVolume(context: Context): Flow<Float> = context.settingsDataStore.data
        .map { preferences ->
            normalizePlayerVolume(preferences[keyPreferredPlayerVolume] ?: 1.0f)
        }

    suspend fun setPreferredPlayerVolume(context: Context, volume: Float) {
        val normalized = normalizePlayerVolume(volume)
        context.settingsDataStore.edit { preferences ->
            preferences[keyPreferredPlayerVolume] = normalized
        }
        context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putFloat(cacheKeyPreferredPlayerVolume, normalized)
            .apply()
    }

    fun getPreferredPlayerVolumeSync(context: Context): Float {
        return normalizePlayerVolume(
            context.getSharedPreferences(playbackSpeedCachePrefs, Context.MODE_PRIVATE)
                .getFloat(cacheKeyPreferredPlayerVolume, 1.0f)
        )
    }

    fun getDefaultAudioQuality(context: Context): Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[defaultAudioQualityPreferenceKey] ?: DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
        }

    suspend fun setDefaultAudioQuality(context: Context, value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[defaultAudioQualityPreferenceKey] = value
        }
        context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .edit()
            .putInt(cacheKeyDefaultAudioQuality, value)
            .commit()
    }

    fun getCachedDefaultAudioQuality(context: Context): Int {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getInt(cacheKeyDefaultAudioQuality, DEFAULT_AUDIO_QUALITY_FOLLOW_LAST)
    }

    fun getCachedLastSelectedAudioQuality(context: Context): Int {
        return context.getSharedPreferences(audioQualityCachePrefs, Context.MODE_PRIVATE)
            .getInt(cacheKeyAudioQuality, -1)
    }

    fun getPlayerInsightMode(context: Context): Flow<PlayerInsightMode> = context.settingsDataStore.data
        .map { preferences ->
            val legacyPreferences = context.getSharedPreferences(legacyAppPrefs, Context.MODE_PRIVATE)
            resolvePlayerInsightMode(
                storedMode = preferences[keyPlayerInsightMode],
                legacyPreferencePresent = legacyPreferences.contains(legacyShowStatsKey),
                legacyStatsEnabled = legacyPreferences.getBoolean(legacyShowStatsKey, false)
            )
        }

    suspend fun setPlayerInsightMode(context: Context, mode: PlayerInsightMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyPlayerInsightMode] = mode.name
        }
        context.getSharedPreferences(legacyAppPrefs, Context.MODE_PRIVATE)
            .edit()
            .putString(cachePlayerInsightModeKey, mode.name)
            .putBoolean(legacyShowStatsKey, mode != PlayerInsightMode.OFF)
            .apply()
    }

    fun getPlayerInsightModeSync(context: Context): PlayerInsightMode {
        val preferences = context.getSharedPreferences(legacyAppPrefs, Context.MODE_PRIVATE)
        return resolvePlayerInsightMode(
            storedMode = preferences.getString(cachePlayerInsightModeKey, null),
            legacyPreferencePresent = preferences.contains(legacyShowStatsKey),
            legacyStatsEnabled = preferences.getBoolean(legacyShowStatsKey, false)
        )
    }

    internal fun resolvePlayerInsightMode(
        storedMode: String?,
        legacyPreferencePresent: Boolean,
        legacyStatsEnabled: Boolean
    ): PlayerInsightMode {
        PlayerInsightMode.entries.firstOrNull { it.name == storedMode }?.let { return it }
        // 全新安装 / 无历史偏好：默认关闭，避免首播叠加洞察浮层
        if (!legacyPreferencePresent) return PlayerInsightMode.OFF
        return if (legacyStatsEnabled) {
            PlayerInsightMode.ALWAYS
        } else {
            PlayerInsightMode.OFF
        }
    }
}
