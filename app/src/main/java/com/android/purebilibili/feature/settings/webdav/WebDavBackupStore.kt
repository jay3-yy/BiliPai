package com.android.purebilibili.feature.settings.webdav

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.webDavBackupDataStore by preferencesDataStore(name = "webdav_backup_prefs")

data class WebDavBackupConfig(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remoteDir: String = DEFAULT_WEBDAV_REMOTE_DIR,
    val enabled: Boolean = false,
    val maxRemoteBackups: Int = DEFAULT_WEBDAV_MAX_REMOTE_BACKUPS,
)

object WebDavBackupStore {
    private val KEY_BASE_URL = stringPreferencesKey("webdav_base_url")
    private val KEY_USERNAME = stringPreferencesKey("webdav_username")
    private val KEY_PASSWORD = stringPreferencesKey("webdav_password")
    private val KEY_REMOTE_DIR = stringPreferencesKey("webdav_remote_dir")
    private val KEY_ENABLED = booleanPreferencesKey("webdav_enabled")
    private val KEY_MAX_REMOTE_BACKUPS = intPreferencesKey("webdav_max_remote_backups")

    fun getConfig(context: Context): Flow<WebDavBackupConfig> {
        return context.webDavBackupDataStore.data.map { prefs ->
            WebDavBackupConfig(
                baseUrl = normalizeWebDavBaseUrl(prefs[KEY_BASE_URL] ?: ""),
                username = prefs[KEY_USERNAME] ?: "",
                password = prefs[KEY_PASSWORD] ?: "",
                remoteDir = normalizeWebDavRemoteDir(prefs[KEY_REMOTE_DIR] ?: DEFAULT_WEBDAV_REMOTE_DIR),
                enabled = prefs[KEY_ENABLED] ?: false,
                maxRemoteBackups = (prefs[KEY_MAX_REMOTE_BACKUPS] ?: DEFAULT_WEBDAV_MAX_REMOTE_BACKUPS).coerceIn(0, 100),
            )
        }
    }

    suspend fun setConfig(context: Context, config: WebDavBackupConfig) {
        context.webDavBackupDataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = normalizeWebDavBaseUrl(config.baseUrl)
            prefs[KEY_USERNAME] = config.username.trim()
            prefs[KEY_PASSWORD] = config.password
            prefs[KEY_REMOTE_DIR] = normalizeWebDavRemoteDir(config.remoteDir)
            prefs[KEY_ENABLED] = config.enabled
            prefs[KEY_MAX_REMOTE_BACKUPS] = config.maxRemoteBackups.coerceIn(0, 100)
        }
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.webDavBackupDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    suspend fun setMaxRemoteBackups(context: Context, maxRemoteBackups: Int) {
        context.webDavBackupDataStore.edit { prefs ->
            prefs[KEY_MAX_REMOTE_BACKUPS] = maxRemoteBackups.coerceIn(0, 100)
        }
    }
}
