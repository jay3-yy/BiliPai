// 文件路径: core/store/TokenManager.kt
package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

val Context.dataStore: DataStore<Preferences>
    get() = MultiProcessPreferences.get(
        this, "user_prefs", listOf(TokenManager.notificationSessionMigration(applicationContext))
    )

data class AccountSessionIdentity(val mid: Long? = null, val generation: Long = 0L)

object TokenManager {
    private val _accountIdentity = MutableStateFlow(AccountSessionIdentity())
    val accountIdentity = _accountIdentity.asStateFlow()

    private fun publishAccountIdentity() {
        val mid = midCache?.takeIf { it > 0L }
        _accountIdentity.update {
            if (it.mid == mid) it else AccountSessionIdentity(mid, it.generation + 1L)
        }
    }
    private val SESSDATA_KEY = stringPreferencesKey("sessdata")
    private val BUVID3_KEY = stringPreferencesKey("buvid3")
    private val MID_KEY = longPreferencesKey("session_mid")
    private val CSRF_KEY = stringPreferencesKey("session_csrf")
    private val ACCESS_KEY = stringPreferencesKey("session_access_token")
    private val REFRESH_KEY = stringPreferencesKey("session_refresh_token")
    private val PLATFORM_KEY = stringPreferencesKey("session_access_platform")
    private val SESSION_MIGRATED_KEY = booleanPreferencesKey("notification_session_migrated")
    private var lastNotificationPreferences: Preferences? = null
    private val notificationSessionRefreshMutex = Mutex()

    internal fun notificationSessionMigration(context: Context) = object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences): Boolean =
            currentData[SESSION_MIGRATED_KEY] != true

        override suspend fun migrate(currentData: Preferences): Preferences {
            val backup = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            return currentData.toMutablePreferences().apply {
                // Preserve existing encrypted session values and the original file location.
                if (this[SESSDATA_KEY] == null) backup.getString(SP_KEY_SESS, null)?.let { this[SESSDATA_KEY] = it }
                if (this[BUVID3_KEY] == null) backup.getString(SP_KEY_BUVID, null)?.let { this[BUVID3_KEY] = it }
                this[MID_KEY] = backup.getLong(SP_KEY_MID, 0L)
                backup.getString(SP_KEY_CSRF, null)?.let { this[CSRF_KEY] = it }
                backup.getString(SP_KEY_ACCESS_TOKEN, null)?.let { this[ACCESS_KEY] = it }
                backup.getString(SP_KEY_REFRESH_TOKEN, null)?.let { this[REFRESH_KEY] = it }
                this[PLATFORM_KEY] = backup.getString(SP_KEY_ACCESS_TOKEN_PLATFORM, ACCESS_TOKEN_PLATFORM_TV)
                    ?: ACCESS_TOKEN_PLATFORM_TV
                this[SESSION_MIGRATED_KEY] = true
            }.toPreferences()
        }

        override suspend fun cleanUp() = Unit
    }

    /** Read-only service-process session; never restores or writes SharedPreferences. */
    suspend fun refreshServiceSession(context: Context): Unit = notificationSessionRefreshMutex.withLock {
        applyNotificationSession(context.dataStore.data.first())
    }

    /** The caller owns this collection and cancels it with its Service lifecycle. */
    suspend fun observeServiceSession(context: Context, onAccountChanged: () -> Unit) {
        var observedMid: Long? = null
        var initialized = false
        context.dataStore.data.collect {
            // A queued observer emission must not overwrite a newer on-demand refresh.
            refreshServiceSession(context)
            val currentMid = midCache
            if (!initialized || observedMid != currentMid) onAccountChanged()
            observedMid = currentMid
            initialized = true
        }
    }

    private fun applyNotificationSession(prefs: Preferences) {
        synchronized(this) {
            if (prefs == lastNotificationPreferences) return
            val previousSession = sessDataCache
            sessDataCache = prefs[SESSDATA_KEY]?.let(SessionStorageCipher::decrypt)?.ifBlank { null }
            buvid3Cache = prefs[BUVID3_KEY]?.let(SessionStorageCipher::decrypt)?.ifBlank { null }
            csrfCache = prefs[CSRF_KEY]?.let(SessionStorageCipher::decrypt)?.ifBlank { null }
            midCache = prefs[MID_KEY]?.takeIf { it > 0L }
            accessTokenCache = prefs[ACCESS_KEY]?.let(SessionStorageCipher::decrypt)?.ifBlank { null }
            refreshTokenCache = prefs[REFRESH_KEY]?.let(SessionStorageCipher::decrypt)?.ifBlank { null }
            accessTokenPlatformCache = prefs[PLATFORM_KEY] ?: ACCESS_TOKEN_PLATFORM_TV
            if (previousSession != sessDataCache) com.android.purebilibili.core.network.NetworkModule.clearRuntimeCookies()
            publishAccountIdentity()
            // Retry decryption on transient Keystore failures instead of caching an empty session.
            if (prefs[SESSDATA_KEY].isNullOrBlank() || sessDataCache != null) lastNotificationPreferences = prefs
        }
    }


    //  [新增] SharedPreferences 备份，解决冷启动时 DataStore 异步加载慢导致 ApiClient 无 Cookie 的问题
    private const val SP_NAME = "token_backup_sp"
    private const val SP_KEY_SESS = "sessdata_backup"
    private const val SP_KEY_BUVID = "buvid3_backup"
    private const val SP_KEY_CSRF = "bili_jct_backup"  //  新增 CSRF 持久化
    private const val SP_KEY_MID = "mid_backup"        //  新增 MID 持久化
    private const val SP_KEY_ACCESS_TOKEN = "access_token_backup"  //  [新增] APP access_token
    private const val SP_KEY_REFRESH_TOKEN = "refresh_token_backup"  //  [新增] APP refresh_token
    private const val SP_KEY_ACCESS_TOKEN_PLATFORM = "access_token_platform_backup"

    const val ACCESS_TOKEN_PLATFORM_TV = "tv"
    const val ACCESS_TOKEN_PLATFORM_ANDROID = "android"

    @Volatile
    var sessDataCache: String? = null
        private set

    //  [修复]：移除了 private set，允许 ApiClient 生成临时 ID 后写入
    @Volatile
    var buvid3Cache: String? = null
    
    //  [新增] VIP 状态缓存 (1=有效大会员, 0=非VIP)
    @Volatile
    var isVipCache: Boolean = false
    
    //  [新增] CSRF Token 缓存 (bili_jct)
    @Volatile
    var csrfCache: String? = null
    
    //  [新增] 用户 MID 缓存
    @Volatile
    var midCache: Long? = null
        private set
    
    //  [新增] APP access_token - 用于调用 APP API 获取高画质视频流
    @Volatile
    var accessTokenCache: String? = null
        private set
    
    //  [新增] APP refresh_token - 用于刷新 access_token
    @Volatile
    var refreshTokenCache: String? = null
        private set

    @Volatile
    var accessTokenPlatformCache: String = ACCESS_TOKEN_PLATFORM_TV
        private set

    // 恢复在 IO 线程异步执行：AndroidKeyStore 首次加载(低端机 10–50ms)+5 次 AES/GCM 解密
    // 不再占住 Application.onCreate 的主线程。网络线程在读取会话缓存前通过 [awaitRestore]
    // 等待恢复完成（通常已结束，等待时间为 0），请求不会以匿名身份发出。
    @Volatile
    private var restoreStarted = false

    @Volatile
    private var restoreCompleted = false
    private val restoreLatch = CountDownLatch(1)

    fun init(context: Context) {
        restoreStarted = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                restoreSessionBackup(context)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.android.purebilibili.core.network.CoreDataLog.e("TokenManager", "Unable to restore session backup", e)
            } finally {
                restoreCompleted = true
                restoreLatch.countDown()
            }

            // 2. 启动 DataStore 监听 (主要数据源)——保持先恢复备份、后监听 DataStore 的顺序
            context.dataStore.data.collect { prefs ->
                val dsSess = prefs[SESSDATA_KEY]?.let(SessionStorageCipher::decrypt)
                val dsBuvid = prefs[BUVID3_KEY]?.let(SessionStorageCipher::decrypt)

                // 更新内存 -  [修复] 只有 DataStore 有值时才更新，避免覆盖 SP 的备份值
                if (!dsSess.isNullOrEmpty()) {
                    sessDataCache = dsSess
                }

                if (dsBuvid == null) {
                    val newBuvid = generateBuvid3()
                    saveBuvid3(context, newBuvid)
                } else {
                    buvid3Cache = dsBuvid
                }

                val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                //  数据同步：如果 DataStore 有值但 SP 没值 (或值不同)，同步写入 SP (从 V1 迁移到 V2)
                if (sessDataCache != null && sessDataCache != sp.getString(SP_KEY_SESS, null)) {
                    sessDataCache?.let(SessionStorageCipher::encrypt)?.let { encrypted ->
                        sp.edit().putString(SP_KEY_SESS, encrypted).apply()
                    }
                }
                if (buvid3Cache != null && buvid3Cache != sp.getString(SP_KEY_BUVID, null)) {
                    buvid3Cache?.let(SessionStorageCipher::encrypt)?.let { encrypted ->
                        sp.edit().putString(SP_KEY_BUVID, encrypted).apply()
                    }
                }
            }
        }
    }

    /**
     * 阻塞调用线程直到异步备份恢复完成（上限 2 秒）。仅供网络线程（OkHttp CookieJar、
     * 取流签名路径）在读会话缓存前调用；恢复通常在首个请求前早已完成，等待时间为 0。
     * 不要在主线程调用。恢复从未启动（如崩溃恢复模式）时立即返回。
     */
    fun awaitRestore(timeoutMs: Long = 2_000L) {
        if (restoreCompleted || !restoreStarted) return
        runCatching { restoreLatch.await(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    private fun restoreSessionBackup(context: Context) {
        // 1. 读取 SP 备份并解密到内存
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sessDataCache = sp.getString(SP_KEY_SESS, null)?.let(SessionStorageCipher::decrypt)
        buvid3Cache = sp.getString(SP_KEY_BUVID, null)?.let(SessionStorageCipher::decrypt)
        csrfCache = sp.getString(SP_KEY_CSRF, null)?.let(SessionStorageCipher::decrypt)  //  读取 CSRF
        midCache = sp.getLong(SP_KEY_MID, 0L).takeIf { it > 0 }  //  读取 MID
        accessTokenCache = sp.getString(SP_KEY_ACCESS_TOKEN, null)?.let(SessionStorageCipher::decrypt)  //  读取 access_token
        refreshTokenCache = sp.getString(SP_KEY_REFRESH_TOKEN, null)?.let(SessionStorageCipher::decrypt)  //  读取 refresh_token
        accessTokenPlatformCache = sp.getString(
            SP_KEY_ACCESS_TOKEN_PLATFORM,
            ACCESS_TOKEN_PLATFORM_TV
        ) ?: ACCESS_TOKEN_PLATFORM_TV

        publishAccountIdentity()

        com.android.purebilibili.core.network.CoreDataLog.d(
            "TokenManager",
            "init: hasSession=${!sessDataCache.isNullOrBlank()}, hasAccessToken=${!accessTokenCache.isNullOrBlank()}, mid=$midCache"
        )
    }
    
    //  [新增] 保存 CSRF Token
    suspend fun saveCsrf(context: Context, csrf: String) {
        csrfCache = csrf
        val encryptedCsrf = SessionStorageCipher.encrypt(csrf)
        encryptedCsrf?.let { encrypted ->
            context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(SP_KEY_CSRF, encrypted).apply()
        }
        context.dataStore.edit { prefs ->
            encryptedCsrf?.let { prefs[CSRF_KEY] = it }
        }
        com.android.purebilibili.core.network.CoreDataLog.d("TokenManager", "saveCsrf")
    }
    
    //  [新增] 保存用户 MID
    suspend fun saveMid(context: Context, mid: Long) {
        midCache = mid.takeIf { it > 0L }
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putLong(SP_KEY_MID, mid).apply()
        context.dataStore.edit { it[MID_KEY] = midCache ?: 0L }
        publishAccountIdentity()
        com.android.purebilibili.core.network.CoreDataLog.d("TokenManager", " saveMid: $mid")
    }
    
    //  [新增] 保存 APP access_token 和 refresh_token - 高画质鉴权登录后调用
    suspend fun saveAccessToken(
        context: Context,
        accessToken: String,
        refreshToken: String,
        platform: String = ACCESS_TOKEN_PLATFORM_TV
    ) {
        accessTokenCache = accessToken
        refreshTokenCache = refreshToken
        accessTokenPlatformCache = platform
        val encryptedAccessToken = SessionStorageCipher.encrypt(accessToken)
        val encryptedRefreshToken = SessionStorageCipher.encrypt(refreshToken)
        val editor = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
        encryptedAccessToken?.let { editor.putString(SP_KEY_ACCESS_TOKEN, it) }
        encryptedRefreshToken?.let { editor.putString(SP_KEY_REFRESH_TOKEN, it) }
        editor.putString(SP_KEY_ACCESS_TOKEN_PLATFORM, platform).apply()
        context.dataStore.edit { prefs ->
            encryptedAccessToken?.let { prefs[ACCESS_KEY] = it }
            encryptedRefreshToken?.let { prefs[REFRESH_KEY] = it }
            prefs[PLATFORM_KEY] = platform
        }
        com.android.purebilibili.core.network.CoreDataLog.d("TokenManager", "saveAccessToken")
    }

    suspend fun clearAccessToken(context: Context) {
        accessTokenCache = null
        refreshTokenCache = null
        accessTokenPlatformCache = ACCESS_TOKEN_PLATFORM_TV
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(SP_KEY_ACCESS_TOKEN)
            .remove(SP_KEY_REFRESH_TOKEN)
            .remove(SP_KEY_ACCESS_TOKEN_PLATFORM)
            .apply()
        context.dataStore.edit {
            it.remove(ACCESS_KEY)
            it.remove(REFRESH_KEY)
            it.remove(PLATFORM_KEY)
        }
    }

    fun saveVipStatus(isVip: Boolean) {
        isVipCache = isVip
    }

    suspend fun saveCookies(context: Context, sessData: String) {
        sessDataCache = sessData
        com.android.purebilibili.core.network.CoreDataLog.d("TokenManager", "saveCookies")
        
        // 1. 存入 SP (同步/快速)
        SessionStorageCipher.encrypt(sessData)?.let { encrypted ->
            context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(SP_KEY_SESS, encrypted).apply()
        }

        // 2. 存入 DataStore (异步/持久)
        SessionStorageCipher.encrypt(sessData)?.let { encrypted ->
            context.dataStore.edit { prefs -> prefs[SESSDATA_KEY] = encrypted }
        }
    }

    suspend fun saveBuvid3(context: Context, buvid3: String) {
        buvid3Cache = buvid3
        
        // 1. 存入 SP
        SessionStorageCipher.encrypt(buvid3)?.let { encrypted ->
            context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit().putString(SP_KEY_BUVID, encrypted).apply()
        }

        // 2. 存入 DataStore
        SessionStorageCipher.encrypt(buvid3)?.let { encrypted ->
            context.dataStore.edit { prefs -> prefs[BUVID3_KEY] = encrypted }
        }
    }

    suspend fun applyStoredSession(
        context: Context,
        sessData: String,
        csrf: String,
        mid: Long,
        accessToken: String,
        refreshToken: String,
        accessTokenPlatform: String = ACCESS_TOKEN_PLATFORM_TV,
        buvid3: String,
        isVip: Boolean
    ) {
        val encryptedSession = SessionStorageCipher.encrypt(sessData)
        val encryptedCsrf = SessionStorageCipher.encrypt(csrf)
        val encryptedAccess = SessionStorageCipher.encrypt(accessToken)
        val encryptedRefresh = SessionStorageCipher.encrypt(refreshToken)
        val selectedBuvid = buvid3.ifBlank { buvid3Cache?.takeIf { it.isNotBlank() } ?: generateBuvid3() }
        val encryptedBuvid = SessionStorageCipher.encrypt(selectedBuvid)
        sessDataCache = sessData
        csrfCache = csrf.ifBlank { null }
        midCache = mid.takeIf { it > 0L }
        buvid3Cache = selectedBuvid
        accessTokenCache = accessToken.ifBlank { null }
        refreshTokenCache = refreshToken.ifBlank { null }
        this.accessTokenPlatformCache = accessTokenPlatform
        isVipCache = isVip

        val editor = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
            .putLong(SP_KEY_MID, midCache ?: 0L)
            .putString(SP_KEY_ACCESS_TOKEN_PLATFORM, accessTokenPlatformCache)
        encryptedSession?.let { editor.putString(SP_KEY_SESS, it) }
        encryptedBuvid?.let { editor.putString(SP_KEY_BUVID, it) }
        encryptedCsrf?.let { editor.putString(SP_KEY_CSRF, it) }
        encryptedAccess?.let { editor.putString(SP_KEY_ACCESS_TOKEN, it) }
        encryptedRefresh?.let { editor.putString(SP_KEY_REFRESH_TOKEN, it) }
        editor.apply()

        // One transaction prevents the main observer or :message from combining identities.
        // If encryption is unavailable, keep the active in-memory login but suspend remote auth.
        context.dataStore.edit { prefs ->
            if (encryptedSession != null) prefs[SESSDATA_KEY] = encryptedSession else prefs.remove(SESSDATA_KEY)
            if (encryptedBuvid != null) prefs[BUVID3_KEY] = encryptedBuvid else prefs.remove(BUVID3_KEY)
            if (encryptedCsrf != null) prefs[CSRF_KEY] = encryptedCsrf else prefs.remove(CSRF_KEY)
            if (encryptedAccess != null) prefs[ACCESS_KEY] = encryptedAccess else prefs.remove(ACCESS_KEY)
            if (encryptedRefresh != null) prefs[REFRESH_KEY] = encryptedRefresh else prefs.remove(REFRESH_KEY)
            prefs[MID_KEY] = midCache ?: 0L
            prefs[PLATFORM_KEY] = accessTokenPlatformCache
        }
        publishAccountIdentity()
    }

    fun getSessData(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs -> prefs[SESSDATA_KEY]?.let(SessionStorageCipher::decrypt) }
    }

    suspend fun clear(context: Context) {
        sessDataCache = null
        buvid3Cache = null
        csrfCache = null
        midCache = null
        isVipCache = false
        accessTokenCache = null  //  [新增] 清除 access_token
        refreshTokenCache = null
        accessTokenPlatformCache = ACCESS_TOKEN_PLATFORM_TV
        publishAccountIdentity()
        
        // 清除 SP
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(SP_KEY_SESS)
            .remove(SP_KEY_BUVID)
            .remove(SP_KEY_CSRF)
            .remove(SP_KEY_MID)
            .remove(SP_KEY_ACCESS_TOKEN)
            .remove(SP_KEY_REFRESH_TOKEN)
            .apply()

        // 清除 DataStore
        context.dataStore.edit {
            it.remove(SESSDATA_KEY)
            it.remove(BUVID3_KEY)
            it.remove(MID_KEY)
            it.remove(CSRF_KEY)
            it.remove(ACCESS_KEY)
            it.remove(REFRESH_KEY)
            it.remove(PLATFORM_KEY)
        }
    }

    private fun generateBuvid3(): String {
        return UUID.randomUUID().toString().replace("-", "") + "infoc"
    }
}
