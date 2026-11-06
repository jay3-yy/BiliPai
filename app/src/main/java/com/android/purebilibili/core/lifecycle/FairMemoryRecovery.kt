package com.android.purebilibili.core.lifecycle

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.AudioPlaybackCheckpoint
import com.android.purebilibili.core.store.MultiProcessPreferences
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.feature.home.HomeFeedMemorySnapshot
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class FairMemoryNavigationState(
    val backStack: List<BiliPaiNavKey>,
    val bottomRoute: String,
    val homeFeed: HomeFeedMemorySnapshot? = null,
)

@Serializable
internal data class FairMemoryRecoverySnapshot(
    val sessionId: String,
    val accountMid: Long,
    val navigation: FairMemoryNavigationState,
    val savedAtMs: Long,
    val playbackAccountMid: Long = accountMid,
)

/** 系统只保存定位信息；完整卡片列表保留在应用本地存储和 ViewModel 中。 */
internal fun FairMemoryRecoverySnapshot.forActivityState(): FairMemoryRecoverySnapshot =
    copy(navigation = navigation.copy(homeFeed = navigation.homeFeed?.copy(videos = emptyList())))

/** 只采样已挂载导航，最近获得前台的宿主负责后台期间的页面恢复。所有访问均在主线程。 */
internal object FairMemoryNavigationRegistry {
    private val providers = linkedMapOf<ComponentActivity, () -> FairMemoryNavigationState>()
    private val homeProviders = linkedMapOf<ComponentActivity, () -> HomeFeedMemorySnapshot?>()
    private var activeOwner: ComponentActivity? = null

    fun register(owner: ComponentActivity, capture: () -> FairMemoryNavigationState): () -> Unit {
        providers[owner] = capture
        return {
            if (providers[owner] === capture) {
                providers.remove(owner)
                if (activeOwner === owner) activeOwner = null
            }
        }
    }

    fun markActive(owner: ComponentActivity) {
        activeOwner = owner
    }

    fun registerHome(owner: ComponentActivity, capture: () -> HomeFeedMemorySnapshot?): () -> Unit {
        homeProviders[owner] = capture
        return { if (homeProviders[owner] === capture) homeProviders.remove(owner) }
    }

    fun capture(): FairMemoryNavigationState? {
        val owner = providers.keys.lastOrNull { it.hasWindowFocus() }
            ?: activeOwner?.takeIf { it in providers }
            ?: providers.keys.lastOrNull()
        val home = (owner?.let { homeProviders[it] } ?: homeProviders.values.lastOrNull())?.invoke()
        return owner?.let { providers[it]?.invoke() }?.copy(homeFeed = home)?.let(::restorableFairMemoryNavigation)
    }
}

/** 外部媒体的 launchId 只存在于进程内，恢复时退回它的来源页。 */
internal fun restorableFairMemoryNavigation(state: FairMemoryNavigationState): FairMemoryNavigationState =
    state.copy(backStack = state.backStack.filterNot { it is BiliPaiNavKey.ExternalMedia }
        .ifEmpty { listOf(BiliPaiNavKey.MainHost) })

/** 播放断点只写回同一视频，避免把尚在切换中的旧播放器位置套到新页面。 */
internal fun withFairMemoryPlaybackPosition(
    state: FairMemoryNavigationState,
    playback: AudioPlaybackCheckpoint?,
    episodeId: Long? = null,
): FairMemoryNavigationState {
    if (playback == null) return state
    val top = state.backStack.lastOrNull()
    val updated = when {
        top is BiliPaiNavKey.VideoDetail && top.bvid == playback.bvid -> top.copy(
            cid = playback.cid,
            resumePositionMs = playback.positionMs,
            sourceRoute = null,
            openId = 0L,
        )
        top is BiliPaiNavKey.BangumiPlayer && top.epId == episodeId ->
            top.copy(resumePositionMs = playback.positionMs)
        else -> return state
    }
    return state.copy(backStack = state.backStack.dropLast(1) + updated)
}

internal fun canRestoreFairMemorySnapshot(
    snapshot: FairMemoryRecoverySnapshot,
    currentSessionId: String,
    accountMid: Long,
    explicitEntry: Boolean,
    expectedVideoBvid: String? = null,
    playbackAccountMid: Long = accountMid,
): Boolean = !explicitEntry && snapshot.sessionId != currentSessionId &&
    snapshot.accountMid == accountMid && snapshot.playbackAccountMid == playbackAccountMid &&
    snapshot.navigation.backStack.isNotEmpty() &&
    (expectedVideoBvid == null ||
        (snapshot.navigation.backStack.last() as? BiliPaiNavKey.VideoDetail)?.bvid == expectedVideoBvid)

/** 只由终止通知写入；恢复记录消费一次，不把进程内 Activity 重建当作系统终止。 */
internal object FairMemoryRecoveryStore {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val processSessionId: String = UUID.randomUUID().toString()
    private val key = stringPreferencesKey("navigation_snapshot")
    private val mutex = Mutex()
    @Volatile private var currentProcessHasCheckpoint = false

    suspend fun save(
        context: Context,
        navigation: FairMemoryNavigationState?,
        accountMid: Long,
        playbackAccountMid: Long = accountMid,
    ) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                MultiProcessPreferences.get(context, "fair_memory_recovery").edit { prefs ->
                    if (navigation == null) prefs.remove(key)
                    else prefs[key] = json.encodeToString(FairMemoryRecoverySnapshot(
                        processSessionId, accountMid, navigation, System.currentTimeMillis(), playbackAccountMid,
                    ))
                }
                currentProcessHasCheckpoint = navigation != null
            }
        }

    suspend fun consume(context: Context, explicitEntry: Boolean, expectedVideoBvid: String?): FairMemoryRecoverySnapshot? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var restored: FairMemoryRecoverySnapshot? = null
                MultiProcessPreferences.get(context, "fair_memory_recovery").edit { prefs ->
                    val raw = prefs[key] ?: return@edit
                    val snapshot = try {
                        json.decodeFromString(FairMemoryRecoverySnapshot.serializer(), raw)
                    } catch (error: SerializationException) {
                        Logger.w("FairMemory", "Invalid recovery snapshot", error)
                        prefs.remove(key)
                        return@edit
                    }
                    if (snapshot.sessionId == processSessionId) return@edit
                    prefs.remove(key)
                    if (canRestoreFairMemorySnapshot(
                            snapshot, processSessionId, TokenManager.midCache ?: 0L, explicitEntry, expectedVideoBvid,
                            NetworkModule.playbackAccount()?.mid ?: TokenManager.midCache ?: 0L,
                        )) restored = snapshot
                }
                restored
            }
        }

    /** 用户忽略终止提示并继续导航后，旧现场不能在日后冷启动时重新出现。 */
    suspend fun invalidateCurrentProcessCheckpoint(context: Context) {
        if (!currentProcessHasCheckpoint) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!currentProcessHasCheckpoint) return@withLock
                MultiProcessPreferences.get(context, "fair_memory_recovery").edit { it.remove(key) }
                currentProcessHasCheckpoint = false
            }
        }
    }
}
