package com.android.purebilibili.feature.cast

import android.media.MediaRoute2Info
import android.media.MediaRoute2ProviderService
import android.media.RouteDiscoveryPreference
import android.media.RoutingSessionInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.nearby.NearbyDeviceFormFactor
import com.android.purebilibili.feature.nearby.NearbyHandoffRuntime
import com.android.purebilibili.feature.nearby.NearbyPlaybackBridge
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 供活跃播放器页面挂载定向 DLNA 投屏执行器的桥接层，避免后台丢事件或误报会话已建立。 */
internal object OutputSwitcherRouteBridge {
    private val dlnaHandoffHandler =
        AtomicReference<(suspend (OutputSwitcherRouteTarget.Dlna) -> Result<Unit>)?>(null)

    fun registerDlnaHandoffHandler(
        handler: suspend (OutputSwitcherRouteTarget.Dlna) -> Result<Unit>,
    ): () -> Unit {
        dlnaHandoffHandler.set(handler)
        return { dlnaHandoffHandler.compareAndSet(handler, null) }
    }

    suspend fun executeDlnaHandoff(target: OutputSwitcherRouteTarget.Dlna): Result<Unit> {
        val handler = dlnaHandoffHandler.get()
            ?: return Result.failure(IllegalStateException("当前没有正在播放的视频可供 DLNA 投屏"))
        return handler(target)
    }
}

/**
 * 系统媒体输出切换器（Output Switcher / MediaRouter2）路由提供服务。
 *
 * 严格遵循以下约束：
 * - 仅在系统打开输出切换器且下发相关发现偏好时按需触发 DLNA/Nearby 路由收集；
 * - Android 17+ / 13+ / 12 严格受 hasRawLocalNetworkAccess 权限门控，未授权不打开任何局域网 socket；
 * - 仅在真实 DLNA 投屏或 Nearby 接力成功后才调用 notifySessionCreated / notifySessionUpdated，失败或取消明确回报失败原因；
 * - Google Cast 仍由现有稳定插件独立承载，不在此处重写或抢占。
 */
@RequiresApi(Build.VERSION_CODES.R)
class BiliPaiMediaRoute2ProviderService : MediaRoute2ProviderService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val destroying = AtomicBoolean(false)
    private var discoveryJob: Job? = null
    private var nearbyObserveJob: Job? = null

    private val dlnaTargets = ConcurrentHashMap<String, OutputSwitcherRouteTarget.Dlna>()
    private val nearbyTargets = ConcurrentHashMap<String, OutputSwitcherRouteTarget.Nearby>()
    private val nearbyFormFactors = ConcurrentHashMap<String, NearbyDeviceFormFactor>()
    private val sessionTargets = ConcurrentHashMap<String, OutputSwitcherRouteTarget>()
    private val pendingRequestJobs = ConcurrentHashMap<Long, Job>()

    override fun onDiscoveryPreferenceChanged(preference: RouteDiscoveryPreference) {
        val shouldDiscover = shouldDiscoverOutputSwitcherRoutes(
            preferredFeatures = preference.preferredFeatures,
            hasLocalNetworkPermission = hasRawLocalNetworkAccess(applicationContext),
        )
        if (!shouldDiscover) {
            stopDiscoveryAndClear()
            return
        }
        startDiscovery()
    }

    override fun onCreateSession(
        requestId: Long,
        packageName: String,
        routeId: String,
        sessionHints: Bundle?,
    ) {
        val target = parseOutputSwitcherRouteId(
            routeId = routeId,
            dlnaLookup = { usn -> dlnaTargets[usn] },
            nearbyLookup = { pin -> nearbyTargets[pin] },
        )
        if (target == null) {
            notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
            return
        }

        val job = serviceScope.launch {
            try {
                val result = withTimeout(HANDOFF_TIMEOUT_MS) {
                    performTargetHandoff(target)
                }
                if (result.isSuccess) {
                    val sessionId = "bilipai-route-$requestId"
                    val sessionName = when (target) {
                        is OutputSwitcherRouteTarget.Dlna -> target.friendlyName
                        is OutputSwitcherRouteTarget.Nearby -> target.deviceName
                    }
                    sessionTargets[sessionId] = target
                    val sessionInfo = RoutingSessionInfo.Builder(sessionId, packageName)
                        .setName(sessionName)
                        .addSelectedRoute(routeId)
                        .build()
                    notifySessionCreated(requestId, sessionInfo)
                } else {
                    notifyRequestFailed(
                        requestId,
                        resolveOutputSwitcherFailureReason(result.exceptionOrNull()),
                    )
                }
            } catch (cancelled: CancellationException) {
                if (!destroying.get()) {
                    notifyRequestFailed(requestId, REASON_REJECTED)
                }
                throw cancelled
            } catch (error: Exception) {
                notifyRequestFailed(requestId, resolveOutputSwitcherFailureReason(error))
            } finally {
                pendingRequestJobs.remove(requestId)
            }
        }
        pendingRequestJobs[requestId] = job
    }

    override fun onReleaseSession(requestId: Long, sessionId: String) {
        val releasedTarget = sessionTargets.remove(sessionId)
        if (releasedTarget is OutputSwitcherRouteTarget.Dlna) {
            SsdpCastClient.clearPlaybackSession()
        }
        notifySessionReleased(sessionId)
    }

    override fun onSelectRoute(requestId: Long, sessionId: String, routeId: String) {
        notifyRequestFailed(requestId, REASON_REJECTED)
    }

    override fun onDeselectRoute(requestId: Long, sessionId: String, routeId: String) {
        notifyRequestFailed(requestId, REASON_REJECTED)
    }

    override fun onTransferToRoute(requestId: Long, sessionId: String, routeId: String) {
        val target = parseOutputSwitcherRouteId(
            routeId = routeId,
            dlnaLookup = { usn -> dlnaTargets[usn] },
            nearbyLookup = { pin -> nearbyTargets[pin] },
        )
        if (target == null) {
            notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
            return
        }
        val existing = getSessionInfo(sessionId)
        if (existing == null) {
            notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
            return
        }

        val job = serviceScope.launch {
            try {
                val result = withTimeout(HANDOFF_TIMEOUT_MS) {
                    performTargetHandoff(target)
                }
                if (result.isSuccess) {
                    val previous = sessionTargets.put(sessionId, target)
                    if (previous is OutputSwitcherRouteTarget.Dlna && target !is OutputSwitcherRouteTarget.Dlna) {
                        SsdpCastClient.clearPlaybackSession()
                    }
                    val updatedName = when (target) {
                        is OutputSwitcherRouteTarget.Dlna -> target.friendlyName
                        is OutputSwitcherRouteTarget.Nearby -> target.deviceName
                    }
                    val updated = RoutingSessionInfo.Builder(existing)
                        .setName(updatedName)
                        .clearSelectedRoutes()
                        .addSelectedRoute(routeId)
                        .build()
                    notifySessionUpdated(updated)
                } else {
                    notifyRequestFailed(
                        requestId,
                        resolveOutputSwitcherFailureReason(result.exceptionOrNull()),
                    )
                }
            } catch (cancelled: CancellationException) {
                if (!destroying.get()) {
                    notifyRequestFailed(requestId, REASON_REJECTED)
                }
                throw cancelled
            } catch (error: Exception) {
                notifyRequestFailed(requestId, resolveOutputSwitcherFailureReason(error))
            } finally {
                pendingRequestJobs.remove(requestId)
            }
        }
        pendingRequestJobs[requestId] = job
    }

    override fun onSetRouteVolume(requestId: Long, routeId: String, volume: Int) {
        notifyRequestFailed(requestId, REASON_REJECTED)
    }

    override fun onSetSessionVolume(requestId: Long, sessionId: String, volume: Int) {
        notifyRequestFailed(requestId, REASON_REJECTED)
    }

    override fun onDestroy() {
        destroying.set(true)
        pendingRequestJobs.values.forEach { it.cancel() }
        pendingRequestJobs.clear()
        sessionTargets.clear()
        stopDiscoveryAndClear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun performTargetHandoff(target: OutputSwitcherRouteTarget): Result<Unit> =
        when (target) {
            is OutputSwitcherRouteTarget.Dlna -> OutputSwitcherRouteBridge.executeDlnaHandoff(target)
            is OutputSwitcherRouteTarget.Nearby -> executeNearbyHandoff(target)
        }

    private suspend fun executeNearbyHandoff(target: OutputSwitcherRouteTarget.Nearby): Result<Unit> {
        val appContext = applicationContext
        val runtime = NearbyHandoffRuntime.getInstance(appContext)
        val snapshot = runtime.session.state.value
        val peer = snapshot.peers.firstOrNull { it.pin == target.pin && it.pin in snapshot.connectedPins }
            ?: return Result.failure(NoSuchElementException("目标接力设备已离线：${target.deviceName}"))
        val miniPlayer = MiniPlayerManager.getInstance(appContext)
        val initialMedia = withContext(Dispatchers.Main.immediate) {
            NearbyPlaybackBridge.readCurrentMedia(miniPlayer)
                ?.let(NearbyPlaybackBridge::attachQueueContext)
        } ?: return Result.failure(IllegalStateException("当前没有正在播放的视频可接力"))

        val handedOff = runtime.session.send(
            peer = peer,
            video = initialMedia,
            latest = {
                withContext(Dispatchers.Main.immediate) {
                    NearbyPlaybackBridge.readCurrentMedia(miniPlayer)
                        ?.let(NearbyPlaybackBridge::attachQueueContext)
                        ?: initialMedia
                }
            },
        )
        if (!handedOff) {
            return Result.failure(IllegalStateException("对端取消或未接受接力请求"))
        }
        withContext(Dispatchers.Main.immediate) {
            NearbyPlaybackBridge.resolvePlayerForMedia(miniPlayer, initialMedia)?.pause()
        }
        return Result.success(Unit)
    }

    private fun startDiscovery() {
        if (nearbyObserveJob?.isActive != true) {
            nearbyObserveJob = serviceScope.launch {
                val runtime = NearbyHandoffRuntime.getInstance(applicationContext)
                runtime.session.state.collect { state ->
                    nearbyTargets.clear()
                    nearbyFormFactors.clear()
                    state.peers
                        .filter { it.pin in state.connectedPins }
                        .forEach { peer ->
                            nearbyTargets[peer.pin] = OutputSwitcherRouteTarget.Nearby(
                                pin = peer.pin,
                                deviceName = peer.name,
                            )
                            nearbyFormFactors[peer.pin] = NearbyDeviceFormFactor.fromWire(peer.deviceType)
                        }
                    publishRoutes()
                }
            }
        }

        if (discoveryJob?.isActive == true) return
        discoveryJob = serviceScope.launch {
            try {
                SsdpDiscovery.discover(
                    context = applicationContext,
                    timeoutMs = 5_000,
                    onDevice = { device ->
                        val friendlyName = device.server
                            .substringBefore('/')
                            .trim()
                            .ifBlank { "DLNA 投屏设备" }
                        serviceScope.launch {
                            dlnaTargets[device.usn] = OutputSwitcherRouteTarget.Dlna(
                                usn = device.usn,
                                location = device.location,
                                friendlyName = friendlyName,
                            )
                            publishRoutes()
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.d(TAG, "Output Switcher DLNA discovery skipped: ${error.message}")
            }
        }
    }

    private fun stopDiscoveryAndClear() {
        discoveryJob?.cancel()
        discoveryJob = null
        nearbyObserveJob?.cancel()
        nearbyObserveJob = null
        dlnaTargets.clear()
        nearbyTargets.clear()
        nearbyFormFactors.clear()
        notifyRoutes(emptyList())
    }

    private fun publishRoutes() {
        val routes = buildList {
            nearbyTargets.values.forEach { target ->
                val formFactor = nearbyFormFactors[target.pin] ?: NearbyDeviceFormFactor.PHONE
                add(
                    MediaRoute2Info.Builder(buildNearbyRouteId(target.pin), target.deviceName)
                        .addFeature(FEATURE_BILIPAI_NEARBY_HANDOFF)
                        .addFeature(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK)
                        .setDescription("BiliPai 设备接力")
                        .setType(resolveNearbyRouteDeviceType(formFactor))
                        .setConnectionState(MediaRoute2Info.CONNECTION_STATE_CONNECTED)
                        .build()
                )
            }
            dlnaTargets.values.forEach { target ->
                add(
                    MediaRoute2Info.Builder(buildDlnaRouteId(target.usn), target.friendlyName)
                        .addFeature(FEATURE_BILIPAI_DLNA_CAST)
                        .addFeature(MediaRoute2Info.FEATURE_REMOTE_VIDEO_PLAYBACK)
                        .setDescription("BiliPai DLNA 投屏")
                        .setType(MediaRoute2Info.TYPE_REMOTE_TV)
                        .setConnectionState(MediaRoute2Info.CONNECTION_STATE_DISCONNECTED)
                        .build()
                )
            }
        }
        notifyRoutes(routes)
    }

    companion object {
        private const val TAG = "BiliPaiMediaRoute2"
        private const val HANDOFF_TIMEOUT_MS = 12_000L
    }
}
