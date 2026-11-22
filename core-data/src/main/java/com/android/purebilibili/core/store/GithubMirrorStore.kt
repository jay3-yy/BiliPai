package com.android.purebilibili.core.store

import android.content.Context
import com.android.purebilibili.core.network.policy.GithubMirrorSettings
import com.android.purebilibili.core.network.policy.GithubMirrorSource
import com.android.purebilibili.core.network.policy.normalizeGithubMirrorPrefix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sync-readable GitHub mirror prefs for OkHttp interceptors.
 * SharedPreferences only — mirrors Settings DataStore without blocking network threads.
 */
object GithubMirrorStore {
    private const val PREFS = "github_mirror_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SOURCE = "source"
    private const val KEY_CUSTOM_PREFIX = "custom_prefix"

    @Volatile
    private var cached: GithubMirrorSettings = GithubMirrorSettings()

    @Volatile
    private var initialized: Boolean = false

    private val mutableSettings = MutableStateFlow(GithubMirrorSettings())

    val settings: StateFlow<GithubMirrorSettings> = mutableSettings.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        val loaded = load(context.applicationContext)
        cached = loaded
        mutableSettings.value = loaded
        initialized = true
    }

    fun getSync(): GithubMirrorSettings = cached

    fun getSync(context: Context): GithubMirrorSettings {
        if (!initialized) {
            init(context)
        }
        return cached
    }

    fun save(context: Context, settings: GithubMirrorSettings) {
        val appContext = context.applicationContext
        val normalized = settings.copy(
            customPrefix = normalizeGithubMirrorPrefix(settings.customPrefix).orEmpty(),
        )
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, normalized.enabled)
            .putString(KEY_SOURCE, normalized.source.name)
            .putString(KEY_CUSTOM_PREFIX, normalized.customPrefix)
            .apply()
        cached = normalized
        mutableSettings.value = normalized
    }

    private fun load(context: Context): GithubMirrorSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return GithubMirrorSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            source = GithubMirrorSource.fromValue(prefs.getString(KEY_SOURCE, null)),
            customPrefix = normalizeGithubMirrorPrefix(prefs.getString(KEY_CUSTOM_PREFIX, "").orEmpty()).orEmpty(),
        )
    }
}
