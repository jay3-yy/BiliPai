package com.android.purebilibili.feature.nearby

import android.content.Context
import android.os.Build
import com.android.purebilibili.feature.cast.hasRawLocalNetworkAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun needsNearbyLocalNetworkPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 37 && context.applicationInfo.targetSdkVersion >= 37 &&
        !hasRawLocalNetworkAccess(context)

/** 供播放器顶栏、标签栏图标等轻量入口订阅的只读接力状态快照。 */
internal data class NearbyHandoffStatus(
    val enabled: Boolean = false,
    val connectedCount: Int = 0,
)

/**
 * 应用级共享设备接力运行时：
 * - 跨 MainActivity 与 VideoActivity 共享唯一 NearbySession 与端口监听；
 * - 持久化用户的设备接力开关，前台切页通过短宽限期保持连接不中断；
 * - 退到后台超过宽限期立即释放监听与连接，遵守后台内存与电量纪律。
 */
internal class NearbyHandoffRuntime private constructor(private val appContext: Context) {
    val session = NearbySession(appContext)
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _autoAcceptTrusted = MutableStateFlow(false)
    val autoAcceptTrusted: StateFlow<Boolean> = _autoAcceptTrusted.asStateFlow()

    private val _dismissedRemoteKeys = MutableStateFlow<Set<String>>(emptySet())
    val dismissedRemoteKeys: StateFlow<Set<String>> = _dismissedRemoteKeys.asStateFlow()

    val status: StateFlow<NearbyHandoffStatus> = combine(_enabled, session.state) { enabledNow, sessionState ->
        NearbyHandoffStatus(
            enabled = enabledNow || sessionState.receiving,
            connectedCount = sessionState.connectedPins.size,
        )
    }.stateIn(runtimeScope, SharingStarted.Eagerly, NearbyHandoffStatus())

    private val foregroundHosts = linkedSetOf<Any>()
    private var initJob: Job? = null
    private var listenJob: Job? = null
    private var backgroundGraceJob: Job? = null

    fun ensureInitialized() {
        if (_ready.value || initJob?.isActive == true) return
        initJob = runtimeScope.launch {
            try {
                val persisted = session.initialize()
                // 未获局域网权限时跳过冷启动发现，避免系统弹出设备选择框。
                if (persisted.enabled && !needsNearbyLocalNetworkPermission(appContext)) {
                    _enabled.value = true
                }
                _ready.value = true
                _autoAcceptTrusted.value = persisted.autoAcceptTrusted
                syncListenerState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                session.status("无法读取设备身份，请稍后重试")
            }
        }
    }

    fun setEnabled(enable: Boolean) {
        _enabled.value = enable
        runtimeScope.launch { session.persistEnabled(enable) }
        if (!enable) {
            stopListeningImmediately(clearPairing = true)
        } else {
            ensureInitialized()
            syncListenerState()
        }
    }

    fun setAutoAcceptTrusted(autoAccept: Boolean) {
        _autoAcceptTrusted.value = autoAccept
        runtimeScope.launch { session.persistAutoAcceptTrusted(autoAccept) }
    }

    fun updateDeviceName(rawName: String) {
        runtimeScope.launch { session.updateDeviceName(rawName) }
    }

    fun attachForegroundHost(token: Any) {
        foregroundHosts += token
        backgroundGraceJob?.cancel()
        backgroundGraceJob = null
        ensureInitialized()
        syncListenerState()
    }

    fun detachForegroundHost(token: Any) {
        foregroundHosts -= token
        if (foregroundHosts.isEmpty()) {
            backgroundGraceJob?.cancel()
            backgroundGraceJob = runtimeScope.launch {
                // 留出 MainActivity <-> VideoActivity 切换窗口，避免打开视频时端口和 TLS 连接中断。
                delay(BACKGROUND_GRACE_MS)
                if (foregroundHosts.isEmpty()) {
                    stopListeningImmediately()
                }
            }
        }
    }

    fun dismissRemoteBanner(peerPin: String, media: NearbyVideo) {
        val key = remoteBannerDismissKey(peerPin, media)
        _dismissedRemoteKeys.update { it + key }
    }

    private fun syncListenerState() {
        if (!_ready.value || !_enabled.value || foregroundHosts.isEmpty()) return
        if (listenJob?.isActive == true) return
        listenJob = runtimeScope.launch {
            try {
                session.listen()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                session.status("无法开启局域网连接，请检查网络或端口占用后重新开启")
                _enabled.value = false
            } finally {
                session.close()
            }
        }
    }

    private fun stopListeningImmediately(clearPairing: Boolean = false) {
        backgroundGraceJob?.cancel()
        backgroundGraceJob = null
        listenJob?.cancel()
        listenJob = null
        session.close(clearPairing = clearPairing)
    }

    companion object {
        private const val BACKGROUND_GRACE_MS = 1_200L

        @Volatile private var instance: NearbyHandoffRuntime? = null

        fun getInstance(context: Context): NearbyHandoffRuntime =
            instance ?: synchronized(this) {
                instance ?: NearbyHandoffRuntime(context.applicationContext).also { instance = it }
            }
    }
}

internal fun remoteBannerDismissKey(peerPin: String, media: NearbyVideo): String =
    "$peerPin:${media.contentKey}"

/** 选择最适合在顶部浮条展示的远端播放项（忽略与本机相同内容及用户已关闭项）。 */
internal fun selectNearbyBannerCandidate(
    remotePlaying: Map<String, NearbyRemotePlayback>,
    localMedia: NearbyVideo?,
    dismissedKeys: Set<String>,
): NearbyRemotePlayback? {
    return remotePlaying.values
        .asSequence()
        .filter { item ->
            val sameAsLocal = localMedia != null && item.video.sameContentAs(localMedia)
            val dismissed = remoteBannerDismissKey(item.peer.pin, item.video) in dismissedKeys
            !sameAsLocal && !dismissed
        }
        .sortedWith(
            compareByDescending<NearbyRemotePlayback> { it.video.playing }
                .thenByDescending { it.receivedAtMs }
        )
        .firstOrNull()
}

/**
 * 播放器内点击「设备接力」时的直达策略：
 * 当接力已开启、当前正在播放内容且仅连接了唯一一台受信任设备时，直接进入中央大视频卡片流转舞台，
 * 省去在列表中点选唯一设备的一步；其余情况照常打开设备管理面板。
 */
internal fun resolveDirectStageTargetPeer(
    enabled: Boolean,
    peers: List<NearbyPeer>,
    connectedPins: Set<String>,
    localMedia: NearbyVideo?,
): NearbyPeer? {
    if (!enabled || localMedia == null) return null
    val connectedPeers = peers.filter { it.pin in connectedPins }
    return connectedPeers.singleOrNull()
}
