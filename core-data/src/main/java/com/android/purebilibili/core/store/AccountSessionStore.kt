package com.android.purebilibili.core.store

import android.content.Context
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.NavData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredAccountSession(
    val mid: Long,
    val name: String = "",
    val face: String = "",
    val sessData: String = "",
    val csrf: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val accessTokenPlatform: String = TokenManager.ACCESS_TOKEN_PLATFORM_TV,
    val buvid3: String = "",
    val isVip: Boolean = false,
    val vipLabel: String = "",
    val lastUsedAt: Long = 0L
)

data class AccountSessionSnapshot(
    val accounts: List<StoredAccountSession> = emptyList(),
    val activeAccountMid: Long? = null,
    val playbackAccountMid: Long? = null,
    val purposeAccountMids: Map<AccountPurpose, Long?> = emptyMap(),
)

object AccountSessionStore {
    private const val SP_NAME = "multi_account_sessions"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_ACTIVE_MID = "active_mid"
    private const val KEY_PLAYBACK_MID = "playback_mid"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 账号列表解析缓存。
     *
     * 账号 blob 的解密 + JSON 反序列化 + 排序是**每次读取都要付**的成本：读取路径上
     * （播放取流、画质判定、账号作用域哈希）会被高频调用，而原实现每次都重做一遍
     * AES/GCM + JSON。当配置了独立播放账号时，这条链路从「全部短路」变成「每次解密」，
     * 是「正式版（双账号）比 dev（单账号）明显更卡」的主要来源。
     *
     * 这里以**密文原文**作为有效期键：读取一次 SP 字符串（廉价）即可判定是否需要重新
     * 解密。任何写入路径都会产生新的密文，因此账号变更天然使缓存失效，不需要额外的
     * 失效通知；同时也不会返回过期账号。
     *
     * 线程安全：写入方先写 SP 再（隐式）让下次读取看到新密文，读方只读一次
     * [cachedAccountsRaw] 并与本次读到的密文比较；命中时返回不可变 List 引用，
     * 不存在部分更新的中间态。最坏情况是并发读各自多解密一次，结果一致。
     */
    @Volatile
    private var cachedAccountsRaw: String? = null

    @Volatile
    private var cachedAccounts: List<StoredAccountSession> = emptyList()

    private fun readAccountsRaw(context: Context): String =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNTS, null)
            .orEmpty()

    /** 仅读一次 SP 字符串，密文未变则复用已解析列表（跳过 AES + JSON + 排序）。 */
    private fun decodeAccounts(raw: String): List<StoredAccountSession> {
        val encrypted = raw.let(SessionStorageCipher::decrypt)
        if (encrypted.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredAccountSession>>(encrypted)
        }.getOrDefault(emptyList()).sortedByDescending { it.lastUsedAt }
    }

    /**
     * 缓存是否可安全写入。
     *
     * Keystore 偶发不可用会让 [SessionStorageCipher.decrypt] 退化为空串，此时若照常缓存，
     * 会把一次临时失败固化成「无账号」直到下次写入。密文非空却解析为空即视为失败，
     * 跳过缓存，让下一次读取可以重试。
     */
    private fun canCacheDecodedAccounts(raw: String, decoded: List<StoredAccountSession>): Boolean =
        raw.isBlank() || decoded.isNotEmpty()

    fun getAccounts(context: Context): List<StoredAccountSession> {
        val raw = readAccountsRaw(context)
        cachedAccountsRaw?.let { cachedRaw ->
            if (cachedRaw == raw) return cachedAccounts
        }
        return decodeAndCacheAccounts(raw)
    }

    private fun decodeAndCacheAccounts(raw: String): List<StoredAccountSession> {
        val decoded = decodeAccounts(raw)
        if (canCacheDecodedAccounts(raw, decoded)) {
            // 顺序敏感：先写内容、后写键。并发读者读到「旧键 + 新内容」会判为不命中并
            // 重新解析（结果一致）；反之读到「新键 + 旧内容」则会错误命中过期账号。
            cachedAccounts = decoded
            cachedAccountsRaw = raw
        }
        return decoded
    }

    /** Reads all account-session UI data in one storage pass. Callers should use an IO dispatcher. */
    fun readSnapshot(context: Context): AccountSessionSnapshot = AccountSessionSnapshot(
        accounts = getAccounts(context),
        activeAccountMid = getActiveAccountMid(context),
        playbackAccountMid = getPlaybackAccountMid(context),
        purposeAccountMids = AccountPurpose.entries.associateWith { getPurposeAccountMid(context, it) },
    )

    fun getActiveAccountMid(context: Context): Long? {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_ACTIVE_MID, 0L)
            .takeIf { it > 0L }
    }

    /**
     * The optional account whose server-side entitlement is used only while
     * requesting playback URLs. A missing value means "use the main account".
     */
    fun getPlaybackAccountMid(context: Context): Long? {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PLAYBACK_MID, 0L)
            .takeIf { it > 0L }
    }

    /**
     * 播放账号解析缓存。
     *
     * 命中条件 = 「密文未变 + 播放账号 mid 未变」。播放账号的解析结果只由这两者决定
     * （见下方查找逻辑），因此无需在每次账号写入时手工失效：密文或 mid 一旦变化，
     * 缓存键自然不同。这样播放取流路径上的每一次查询都退化为「1 次 SP 字符串读 +
     * 1 次 SP long 读」，不再做 AES + JSON + 线性查找。
     */
    @Volatile
    private var cachedPlaybackRaw: String? = null

    @Volatile
    private var cachedPlaybackMid: Long? = null

    @Volatile
    private var cachedPlaybackAccount: StoredAccountSession? = null

    fun getPlaybackAccount(context: Context): StoredAccountSession? {
        val mid = getPlaybackAccountMid(context) ?: return null
        val raw = readAccountsRaw(context)
        if (cachedPlaybackRaw == raw && cachedPlaybackMid == mid) {
            return cachedPlaybackAccount
        }
        // 复用同一次 raw 读取：避免这里再读一遍 SP，也避免与 getAccounts 的缓存发生竞争。
        val accounts = if (cachedAccountsRaw == raw) cachedAccounts else decodeAndCacheAccounts(raw)
        val resolved = accounts.firstOrNull { it.mid == mid && it.sessData.isNotBlank() }
        // 密文非空却解析为空 = 解密临时失败；不缓存，允许下次重试。
        if (canCacheDecodedAccounts(raw, accounts)) {
            // 顺序敏感：先写解析结果，最后写键。并发读者读到「旧键 + 新结果」会判为不命中
            // 并重新解析；反之会错误命中过期账号。
            cachedPlaybackAccount = resolved
            cachedPlaybackMid = mid
            cachedPlaybackRaw = raw
        }
        return resolved
    }

    /** Selects an already-verified local account for playback without switching the app account. */
    fun setPlaybackAccountMid(context: Context, mid: Long?): Boolean {
        if (mid != null && getAccounts(context).none { it.mid == mid && it.sessData.isNotBlank() }) {
            return false
        }
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (mid == null) remove(KEY_PLAYBACK_MID) else putLong(KEY_PLAYBACK_MID, mid)
            }
            .apply()
        NetworkModule.clearPlaybackAccountClient()
        return true
    }

    private const val UNINITIALIZED_PURPOSE_MID = Long.MIN_VALUE

    @Volatile
    private var cachedWatchHistoryPurposeMid: Long = UNINITIALIZED_PURPOSE_MID

    fun getPurposeAccountMid(context: Context, purpose: AccountPurpose): Long? {
        if (purpose == AccountPurpose.WATCH_HISTORY) {
            val cached = cachedWatchHistoryPurposeMid
            if (cached != UNINITIALIZED_PURPOSE_MID) {
                return cached.takeIf { it > 0L }
            }
            val loaded = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getLong(purpose.storageKey, 0L)
            cachedWatchHistoryPurposeMid = loaded
            return loaded.takeIf { it > 0L }
        }
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getLong(purpose.storageKey, 0L).takeIf { it > 0L }
    }

    fun getPurposeAccount(context: Context, purpose: AccountPurpose): StoredAccountSession? {
        if (purpose == AccountPurpose.PLAYBACK) return getPlaybackAccount(context)
        val mid = getPurposeAccountMid(context, purpose) ?: return null
        return getAccounts(context).firstOrNull { it.mid == mid && it.sessData.isNotBlank() }
    }

    fun setPurposeAccountMid(context: Context, purpose: AccountPurpose, mid: Long?): Boolean {
        if (purpose == AccountPurpose.PLAYBACK) return setPlaybackAccountMid(context, mid)
        if (mid != null && getAccounts(context).none { it.mid == mid && it.sessData.isNotBlank() }) return false
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit().apply {
            if (mid == null) remove(purpose.storageKey) else putLong(purpose.storageKey, mid)
        }.apply()
        if (purpose == AccountPurpose.WATCH_HISTORY) {
            cachedWatchHistoryPurposeMid = mid ?: 0L
        }
        return true
    }

    fun clearActiveAccount(context: Context) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_MID)
            .apply()
        NetworkModule.clearRuntimeCookies()
        NetworkModule.evictHttpCache(context)
    }

    fun removeAccount(context: Context, mid: Long): Boolean {
        val current = getAccounts(context)
        if (current.none { it.mid == mid }) return false

        current.firstOrNull { it.mid == mid }?.let { account ->
            com.android.purebilibili.core.network.AccountCookieStore.remove(
                context, com.android.purebilibili.core.network.AccountCookieSession.from(account)
            )
        }
        val updated = current.filterNot { it.mid == mid }
        persistAccounts(context, updated)

        val editor = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
        if (getActiveAccountMid(context) == mid) {
            editor.remove(KEY_ACTIVE_MID)
        }
        if (getPlaybackAccountMid(context) == mid) {
            editor.remove(KEY_PLAYBACK_MID)
            NetworkModule.clearPlaybackAccountClient()
        }
        AccountPurpose.entries.forEach { purpose ->
            if (getPurposeAccountMid(context, purpose) == mid) {
                editor.remove(purpose.storageKey)
                if (purpose == AccountPurpose.WATCH_HISTORY) {
                    cachedWatchHistoryPurposeMid = 0L
                }
            }
        }
        editor.apply()
        return true
    }

    suspend fun upsertCurrentAccount(
        context: Context,
        navData: NavData? = null
    ): StoredAccountSession? {
        val mid = navData?.mid?.takeIf { it > 0L } ?: TokenManager.midCache ?: return null
        val sessData = TokenManager.sessDataCache?.takeIf { it.isNotBlank() } ?: return null
        val existing = getAccounts(context).associateBy { it.mid }
        val previous = existing[mid]
        val timestamp = System.currentTimeMillis()

        val updated = StoredAccountSession(
            mid = mid,
            name = navData?.uname?.ifBlank { previous?.name.orEmpty() } ?: previous?.name.orEmpty(),
            face = navData?.face?.ifBlank { previous?.face.orEmpty() } ?: previous?.face.orEmpty(),
            sessData = sessData,
            csrf = TokenManager.csrfCache.orEmpty(),
            accessToken = TokenManager.accessTokenCache.orEmpty(),
            refreshToken = TokenManager.refreshTokenCache.orEmpty(),
            accessTokenPlatform = TokenManager.accessTokenPlatformCache,
            buvid3 = TokenManager.buvid3Cache.orEmpty(),
            isVip = navData?.vip?.status == 1 || TokenManager.isVipCache,
            vipLabel = navData?.vip?.label?.text.orEmpty().ifBlank { previous?.vipLabel.orEmpty() },
            lastUsedAt = timestamp
        )

        val merged = existing.values
            .filterNot { it.mid == mid }
            .plus(updated)
            .sortedByDescending { it.lastUsedAt }
        persistAccounts(context, merged)
        setActiveAccountMid(context, mid)
        return updated
    }

    /** Refreshes only the avatar; session credentials and account selection remain untouched. */
    fun updateAccountAvatar(context: Context, mid: Long, face: String) {
        if (mid <= 0L || face.isBlank()) return
        val accounts = getAccounts(context)
        val previous = accounts.firstOrNull { it.mid == mid } ?: return
        if (previous.face == face) return
        persistAccounts(context, accounts.map { if (it.mid == mid) it.copy(face = face) else it })
    }

    suspend fun activateAccount(context: Context, mid: Long): Boolean {
        val target = getAccounts(context).firstOrNull { it.mid == mid } ?: return false
        if (target.sessData.isBlank()) return false

        NetworkModule.clearRuntimeCookies()
        NetworkModule.evictHttpCache(context)
        TokenManager.applyStoredSession(
            context = context,
            sessData = target.sessData,
            csrf = target.csrf,
            mid = target.mid,
            accessToken = target.accessToken,
            refreshToken = target.refreshToken,
            accessTokenPlatform = target.accessTokenPlatform,
            buvid3 = target.buvid3,
            isVip = target.isVip
        )

        val timestamp = System.currentTimeMillis()
        val updatedAccounts = getAccounts(context).map {
            if (it.mid == mid) it.copy(lastUsedAt = timestamp) else it
        }.sortedByDescending { it.lastUsedAt }
        persistAccounts(context, updatedAccounts)
        setActiveAccountMid(context, mid)
        return true
    }

    /** Imports a session received through an authenticated BiliPai transfer. */
    suspend fun importTransferredSession(
        context: Context,
        bundle: com.android.purebilibili.data.session.BiliPaiSessionBundle,
    ): Boolean {
        if (bundle.mid <= 0L || bundle.sessData.isBlank()) return false
        NetworkModule.clearRuntimeCookies()
        NetworkModule.evictHttpCache(context)
        TokenManager.applyStoredSession(
            context = context,
            sessData = bundle.sessData,
            csrf = bundle.csrf,
            mid = bundle.mid,
            accessToken = bundle.accessToken,
            refreshToken = bundle.refreshToken,
            accessTokenPlatform = bundle.accessTokenPlatform,
            buvid3 = bundle.buvid3,
            isVip = bundle.isVip,
        )
        return upsertCurrentAccount(context)?.mid == bundle.mid
    }

    private fun persistAccounts(
        context: Context,
        accounts: List<StoredAccountSession>
    ) {
        val payload = json.encodeToString(accounts)
        SessionStorageCipher.encrypt(payload)?.let { encrypted ->
            context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCOUNTS, encrypted)
                .apply()
        }
    }

    private fun setActiveAccountMid(context: Context, mid: Long) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ACTIVE_MID, mid)
            .apply()
    }
}
