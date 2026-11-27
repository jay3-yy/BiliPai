package com.android.purebilibili.feature.nearby

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.android.purebilibili.R
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class NearbyHandoffController(
    val openDevices: () -> Unit,
    val openDeviceManager: () -> Unit = openDevices,
    val status: StateFlow<NearbyHandoffStatus> = MutableStateFlow(NearbyHandoffStatus()),
)
internal val LocalNearbyHandoff = staticCompositionLocalOf<NearbyHandoffController?> { null }
internal data class NearbyFlight(
    val video: NearbyVideo,
    val incoming: Boolean,
    val peerName: String = "",
)

/** 播放器顶栏（竖屏 / 横屏 / 液态玻璃通用）图标内容，已连接可信设备时在右上角附带在线状态点。 */
@Composable
internal fun NearbyTopBarIconContent(
    tint: Color = Color.White,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val controller = LocalNearbyHandoff.current
    val status = controller?.status?.collectAsStateWithLifecycle()?.value ?: NearbyHandoffStatus()
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val accent = AppContentColors.selectionAccent(liquidGlassEnabled)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            painter = painterResource(R.drawable.ic_settings_nearby_handoff_24),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        if (status.connectedCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

/** 详情页「简介 / 评论」标签栏右侧智能入口：仅在设备接力开启时出现，已连接设备时高亮并展示在线点。 */
@Composable
internal fun NearbySmartTabIconButton(modifier: Modifier = Modifier) {
    val controller = LocalNearbyHandoff.current ?: return
    val status by controller.status.collectAsStateWithLifecycle()
    val reduceMotion = rememberSystemReduceMotion()
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val accent = AppContentColors.selectionAccent(liquidGlassEnabled)
    AnimatedVisibility(
        visible = NEARBY_HANDOFF_ENABLED && status.enabled,
        enter = if (reduceMotion) EnterTransition.None else fadeIn() + expandHorizontally(expandFrom = Alignment.End),
        exit = if (reduceMotion) ExitTransition.None else fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
    ) {
        AppIconButton(
            onClick = controller.openDevices,
            modifier = modifier
                .width(40.dp)
                .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(
                    painter = painterResource(R.drawable.ic_settings_nearby_handoff_24),
                    contentDescription = NEARBY_TITLE,
                    tint = if (status.connectedCount > 0) accent else AppContentColors.secondaryText(),
                    modifier = Modifier.size(20.dp),
                )
                if (status.connectedCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
            }
        }
    }
}

/** 适用于全屏/竖屏视频画面之上的半透明胶囊入口，保证任意画面亮度下的可读性。 */
@Composable
internal fun NearbyOverlayChipButton(modifier: Modifier = Modifier) {
    val controller = LocalNearbyHandoff.current ?: return
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.44f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(onClick = controller.openDevices)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AppIcon(
            painter = painterResource(R.drawable.ic_settings_nearby_handoff_24),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        AppText(
            text = NEARBY_TITLE,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
internal fun NearbyHandoffHost(
    miniPlayer: MiniPlayerManager,
    onOpenVideo: (NearbyVideo) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val runtime = remember(context) { NearbyHandoffRuntime.getInstance(context) }
    val session = runtime.session
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsStateWithLifecycle()
    val ready by runtime.ready.collectAsStateWithLifecycle()
    val enabled by runtime.enabled.collectAsStateWithLifecycle()
    val autoAcceptTrusted by runtime.autoAcceptTrusted.collectAsStateWithLifecycle()
    val dismissedRemoteKeys by runtime.dismissedRemoteKeys.collectAsStateWithLifecycle()
    val bridgeRevision by NearbyPlaybackBridge.revision.collectAsStateWithLifecycle()
    val audioNowPlayingActive by AudioNowPlayingSession.active.collectAsStateWithLifecycle()
    val localVideoDanmakuEnabled by remember(context) {
        SettingsManager.getDanmakuEnabled(context, DanmakuSettingsScope.PORTRAIT)
    }.collectAsStateWithLifecycle(initialValue = true)
    val localLiveDanmakuEnabled by remember(context) {
        SettingsManager.getLiveDanmakuEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val currentVideoDanmaku by rememberUpdatedState(localVideoDanmakuEnabled)
    val currentLiveDanmaku by rememberUpdatedState(localLiveDanmakuEnabled)
    val hostToken = remember { Any() }

    var panelOpen by remember { mutableStateOf(false) }
    var stagedSend by remember { mutableStateOf<Pair<NearbyPeer, NearbyVideo>?>(null) }
    var receivingStage by remember { mutableStateOf("准备播放") }
    var receivingProgress by remember { mutableFloatStateOf(0.28f) }
    var receivingId by remember { mutableStateOf<String?>(null) }
    var autoAcceptedIncomingId by remember { mutableStateOf<String?>(null) }
    var syncedDanmakuTransfer by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var receiveAudioOnlyOverride by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var activePull by remember { mutableStateOf<NearbyPullSession?>(null) }
    var outgoingPill by remember { mutableStateOf<NearbyTransferPillState?>(null) }
    var completedPill by remember { mutableStateOf<NearbyTransferPillState?>(null) }
    var preferenceHint by remember { mutableStateOf<NearbyPreferenceHint?>(null) }
    var outgoingJob by remember { mutableStateOf<Job?>(null) }
    var flight by remember { mutableStateOf<NearbyFlight?>(null) }
    var localMediaSnapshot by remember { mutableStateOf<NearbyVideo?>(null) }
    var backdropBlurProvider by remember { mutableStateOf<(() -> Float)?>(null) }
    val reduceMotion = rememberSystemReduceMotion()
    val stageBackdropBlurEnabled = !reduceMotion && !isLowBlurBudgetForced()
    val haptic = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    val hapticEnabled = LocalAppThemeConfig.current.hapticFeedbackEnabled
    val currentOnOpenVideo by rememberUpdatedState(onOpenVideo)

    fun readLocalMedia(): NearbyVideo? = NearbyPlaybackBridge.readCurrentMedia(
        miniPlayer = miniPlayer,
        videoDanmakuEnabled = currentVideoDanmaku,
        liveDanmakuEnabled = currentLiveDanmaku,
    )

    suspend fun applyDanmakuPreference(media: NearbyVideo, enabled: Boolean) {
        if (media.isLive) {
            SettingsManager.setLiveDanmakuEnabled(context, enabled)
        } else {
            SettingsManager.setDanmakuEnabled(context, enabled, DanmakuSettingsScope.PORTRAIT)
            SettingsManager.setDanmakuEnabled(context, enabled, DanmakuSettingsScope.LANDSCAPE)
        }
    }

    fun showTransferCompleted(
        peerName: String,
        media: NearbyVideo,
        outgoing: Boolean,
        postHint: NearbyPreferenceHint? = null,
    ) {
        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        val tag = nearbyToken()
        completedPill = NearbyTransferPillState(
            id = tag,
            peerName = peerName,
            media = media,
            stageLabel = "完成",
            progress = 1f,
            outgoing = outgoing,
            completed = true,
            onCancel = { completedPill = null },
        )
        scope.launch {
            delay(560)
            if (completedPill?.id == tag) completedPill = null
            if (postHint != null) {
                preferenceHint = postHint
                delay(8_000)
                if (preferenceHint?.id == postHint.id) preferenceHint = null
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        runtime.setEnabled(granted)
        if (!granted) session.status("需要局域网权限才能连接其他设备")
    }
    val enable: () -> Unit = {
        if (needsNearbyLocalNetworkPermission(context)) {
            permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            runtime.setEnabled(true)
        }
    }
    val controller = remember(runtime, session, miniPlayer) {
        NearbyHandoffController(
            openDevices = {
                val currentMedia = readLocalMedia()
                val snapshotState = session.state.value
                val directPeer = resolveDirectStageTargetPeer(
                    enabled = runtime.enabled.value && snapshotState.receiving,
                    peers = snapshotState.peers,
                    connectedPins = snapshotState.connectedPins,
                    localMedia = currentMedia,
                )
                if (directPeer != null && currentMedia != null && outgoingJob?.isActive != true) {
                    panelOpen = false
                    stagedSend = directPeer to NearbyPlaybackBridge.attachQueueContext(currentMedia)
                } else {
                    panelOpen = true
                }
            },
            openDeviceManager = {
                stagedSend = null
                panelOpen = true
            },
            status = runtime.status,
        )
    }

    // 跨 MainActivity 与 VideoActivity 共享唯一运行时，由前台引用计数控制监听生命周期。
    DisposableEffect(runtime, lifecycleOwner, hostToken) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> runtime.attachForegroundHost(hostToken)
                Lifecycle.Event.ON_STOP -> {
                    receivingId = null
                    stagedSend = null
                    preferenceHint = null
                    outgoingJob?.cancel()
                    outgoingJob = null
                    outgoingPill = null
                    runtime.detachForegroundHost(hostToken)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            runtime.attachForegroundHost(hostToken)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            outgoingJob?.cancel()
            runtime.detachForegroundHost(hostToken)
        }
    }

    // 注册本机作为“被拉取方”的实时媒体快照读取与交接后暂停回调（始终在主线程操作 ExoPlayer）。
    DisposableEffect(session, miniPlayer) {
        val provider = object : NearbyPullProvider {
            override suspend fun readCurrentMedia(): NearbyVideo? = withContext(Dispatchers.Main.immediate) {
                readLocalMedia()?.let { NearbyPlaybackBridge.attachQueueContext(it) }
            }

            override suspend fun pauseAfterHandoff(expected: NearbyVideo) {
                withContext(Dispatchers.Main.immediate) {
                    val player = NearbyPlaybackBridge.resolvePlayerForMedia(miniPlayer, expected)
                    if (player != null) {
                        player.pause()
                    }
                    session.updateLocalPresence(readLocalMedia())
                }
            }
        }
        session.setPullProvider(provider)
        onDispose { session.setPullProvider(null) }
    }

    DisposableEffect(session, panelOpen) {
        session.setPairingVisible(panelOpen)
        onDispose { session.setPairingVisible(false) }
    }

    LaunchedEffect(state.pairingCode) {
        if (state.pairingCode != null) {
            delay(300_000)
            session.cancelPairing()
        }
    }

    // 仅在存在已连接信任设备且应用处于前台时低频同步本机播放状态（含画质与弹幕偏好变化），零空转。
    LaunchedEffect(
        session,
        miniPlayer,
        lifecycleOwner,
        state.connectedPins,
        bridgeRevision,
        miniPlayer.currentBvid,
        miniPlayer.currentCid,
        miniPlayer.isLiveMode,
        miniPlayer.currentRoomId,
        localVideoDanmakuEnabled,
        localLiveDanmakuEnabled,
        audioNowPlayingActive,
        panelOpen,
    ) {
        localMediaSnapshot = readLocalMedia()
        if (state.connectedPins.isEmpty()) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastKey: String? = null
            var lastPlaying: Boolean? = null
            var lastPosBucket = -1L
            var lastQualityId = -1
            var lastAudioQualityId = -1
            var lastAudioLang: String? = null
            var lastSpeed = -1f
            var lastDanmaku: Boolean? = null
            var lastAudioOnly: Boolean? = null
            while (true) {
                val current = readLocalMedia()
                localMediaSnapshot = current
                val key = current?.contentKey
                val playing = current?.playing
                val posBucket = if (current == null || current.isLive) 0L else current.positionMs / 5_000L
                val qualityId = current?.qualityId ?: -1
                val audioQualityId = current?.audioQualityId ?: -1
                val audioLang = current?.audioLang
                val speed = current?.speed ?: -1f
                val danmaku = current?.danmakuEnabled
                val audioOnly = current?.audioOnly
                if (key != lastKey || playing != lastPlaying || posBucket != lastPosBucket ||
                    qualityId != lastQualityId || audioQualityId != lastAudioQualityId ||
                    audioLang != lastAudioLang || speed != lastSpeed || danmaku != lastDanmaku ||
                    audioOnly != lastAudioOnly
                ) {
                    session.updateLocalPresence(current)
                    lastKey = key
                    lastPlaying = playing
                    lastPosBucket = posBucket
                    lastQualityId = qualityId
                    lastAudioQualityId = audioQualityId
                    lastAudioLang = audioLang
                    lastSpeed = speed
                    lastDanmaku = danmaku
                    lastAudioOnly = audioOnly
                }
                delay(6_000)
            }
        }
    }

    suspend fun runReceiveTransfer(
        transferId: String,
        initialMedia: NearbyVideo,
        peerName: String,
        skipIntroFlight: Boolean,
        isStillActive: () -> Boolean,
        fetchLatest: suspend () -> NearbyVideo,
        onStageChange: (String, Float) -> Unit,
        onComplete: suspend (Boolean) -> Boolean,
    ) {
        var preparedPlayer: Player? = null
        var committedMedia: NearbyVideo? = null
        var committedAt = 0L
        var succeeded = false
        var receiverTakenOver = false
        try {
            onStageChange("准备${initialMedia.mediaTypeLabel}", 0.28f)
            session.status("正在准备 ${initialMedia.title.ifBlank { initialMedia.mediaTypeLabel }}…")
            withTimeout(45_000) {
                if (!skipIntroFlight) {
                    flight = NearbyFlight(initialMedia, incoming = true, peerName = peerName)
                    delay(NEARBY_FLIGHT_INCOMING_MS.toLong())
                    flight = null
                }
                NearbyPlaybackBridge.restoreQueueContext(initialMedia)
                val targetAudioOnly = receiveAudioOnlyOverride
                    ?.takeIf { it.first == transferId }
                    ?.second
                val mediaToOpen = if (initialMedia.isVideo && targetAudioOnly != null) {
                    initialMedia.copy(audioOnly = targetAudioOnly)
                } else {
                    initialMedia
                }
                currentOnOpenVideo(mediaToOpen)
                // 必须等接收端实际播放到交接进度，发送端才会暂停。
                while (true) {
                    check(isStillActive()) { "接力请求已结束" }
                    val player = NearbyPlaybackBridge.resolvePlayerForMedia(miniPlayer, initialMedia)
                    if (preparedPlayer != null && player !== preparedPlayer) {
                        receiverTakenOver = true
                        error("接收端已切换内容")
                    }
                    if (player != null) {
                        check(player.playerError == null) { "接收端播放失败" }
                        if (player.playbackState != Player.STATE_READY) {
                            delay(200)
                            continue
                        }
                        if (initialMedia.isLive) {
                            if (preparedPlayer !== player) {
                                preparedPlayer = player
                                committedMedia = initialMedia
                                committedAt = SystemClock.elapsedRealtime()
                                player.play()
                                onStageChange("等待直播画面就绪", 0.85f)
                            }
                            if (player.isPlaying) break
                        } else if (preparedPlayer !== player) {
                            // 页面和媒体已就绪后，通过原连接取得发送端最新进度。
                            player.pause()
                            preparedPlayer = player
                            onStageChange("同步最新进度", 0.66f)
                            session.status("已就绪，正在同步最新进度…")
                            val latest = fetchLatest()
                            check(
                                isStillActive() &&
                                    NearbyPlaybackBridge.resolvePlayerForMedia(miniPlayer, latest) === player
                            )
                            check(player.duration <= 0L || latest.positionMs <= player.duration + 3_000L)
                            committedMedia = latest
                            committedAt = SystemClock.elapsedRealtime()
                            player.setPlaybackSpeed(latest.speed)
                            player.seekTo(latest.positionMs)
                            player.play()
                            onStageChange("等待播放确认", 0.92f)
                        } else {
                            val latest = requireNotNull(committedMedia)
                            if (!player.playWhenReady ||
                                player.playbackParameters.speed != latest.speed ||
                                !nearbyPlaybackPositionMatches(
                                    latest,
                                    player.currentPosition,
                                    SystemClock.elapsedRealtime() - committedAt,
                                )
                            ) {
                                receiverTakenOver = true
                                error("接收端播放状态已改变")
                            }
                            if (player.isPlaying) break
                        }
                    }
                    delay(200)
                }
                onStageChange("接续完成", 1f)
                check(onComplete(true)) { "接力请求已结束" }
                succeeded = true
                session.status("已接续 ${initialMedia.title.ifBlank { initialMedia.mediaTypeLabel }}")
                val sourceForHint = committedMedia ?: initialMedia
                val actualLocal = readLocalMedia()?.takeIf { it.sameContentAs(sourceForHint) }
                val effectiveLocalDanmaku = syncedDanmakuTransfer
                    ?.takeIf { it.first == transferId }
                    ?.second
                    ?: if (sourceForHint.isLive) currentLiveDanmaku else currentVideoDanmaku
                val hint = buildNearbyPreferenceHint(
                    id = nearbyToken(),
                    peerName = peerName,
                    source = sourceForHint,
                    localActual = actualLocal,
                    localDanmakuEnabled = effectiveLocalDanmaku,
                    availableQualityIds = NearbyPlaybackBridge.availableQualityIdsFor(miniPlayer, sourceForHint),
                    availableAudioQualityIds = NearbyPlaybackBridge.availableAudioQualityIdsFor(miniPlayer, sourceForHint),
                    availableAudioLangs = NearbyPlaybackBridge.availableAudioLangsFor(miniPlayer, sourceForHint),
                )
                showTransferCompleted(
                    peerName = peerName,
                    media = initialMedia,
                    outgoing = false,
                    postHint = hint,
                )
            }
        } catch (_: TimeoutCancellationException) {
            onComplete(false)
            session.status("接续播放超时，原设备继续播放")
        } catch (cancelled: CancellationException) {
            onComplete(false)
            throw cancelled
        } catch (_: Exception) {
            onComplete(false)
            session.status("未能完成接续，原设备继续播放")
        } finally {
            // 只停止本次尚未确认的播放；用户已经切换内容或接管操作时不再控制播放器。
            val player = preparedPlayer
            runCatching {
                val latest = committedMedia
                if (!succeeded && !receiverTakenOver && player != null &&
                    NearbyPlaybackBridge.resolvePlayerForMedia(miniPlayer, initialMedia) === player &&
                    (latest == null || latest.isLive || (player.playbackParameters.speed == latest.speed &&
                        nearbyPlaybackPositionMatches(
                            latest,
                            player.currentPosition,
                            SystemClock.elapsedRealtime() - committedAt,
                        )))
                ) {
                    player.pause()
                }
            }
            flight = null
        }
    }

    // 开启「受信任设备自动接续」时，收到已配对设备推送立即进入下落过场并自动开始播放。
    val incoming = state.incoming
    LaunchedEffect(incoming?.id, autoAcceptTrusted, receivingId) {
        val request = incoming ?: return@LaunchedEffect
        if (!autoAcceptTrusted || request.answer.isCompleted || receivingId != null) return@LaunchedEffect
        panelOpen = false
        stagedSend = null
        autoAcceptedIncomingId = request.id
        receivingStage = "准备${request.video.mediaTypeLabel}"
        receivingProgress = 0.28f
        receivingId = request.id
    }

    // 接收主动推送来的接力请求（手动确认时已在 NearbyReceiveStageOverlay 下落落位；自动接续时播放下落过场）
    LaunchedEffect(incoming?.id, receivingId) {
        if (incoming == null || incoming.answer.isCompleted || receivingId != incoming.id) return@LaunchedEffect
        val autoAccepted = autoAcceptedIncomingId == incoming.id
        try {
            runReceiveTransfer(
                transferId = incoming.id,
                initialMedia = incoming.video,
                peerName = incoming.peer.name,
                skipIntroFlight = !autoAccepted,
                isStillActive = { !incoming.answer.isCompleted },
                fetchLatest = incoming.latest,
                onStageChange = { stage, pct ->
                    receivingStage = stage
                    receivingProgress = pct
                },
                onComplete = { accepted -> incoming.answer.complete(accepted) },
            )
        } finally {
            if (autoAcceptedIncomingId == incoming.id) autoAcceptedIncomingId = null
            if (syncedDanmakuTransfer?.first == incoming.id) syncedDanmakuTransfer = null
            if (receiveAudioOnlyOverride?.first == incoming.id) receiveAudioOnlyOverride = null
            receivingId = null
        }
    }

    // 主动拉取远端设备正在播放的内容（无前期确认舞台，直接演绎从上方自然下落过场）
    val pullSession = activePull
    LaunchedEffect(pullSession?.transferId) {
        val currentPull = pullSession ?: return@LaunchedEffect
        try {
            runReceiveTransfer(
                transferId = currentPull.transferId,
                initialMedia = currentPull.initialMedia,
                peerName = currentPull.peer.name,
                skipIntroFlight = false,
                isStillActive = { activePull?.transferId == currentPull.transferId },
                fetchLatest = currentPull.fetchLatest,
                onStageChange = { stage, pct ->
                    receivingStage = stage
                    receivingProgress = pct
                },
                onComplete = { accepted ->
                    currentPull.finish(accepted)
                    true
                },
            )
        } finally {
            if (activePull?.transferId == currentPull.transferId) {
                activePull = null
            }
        }
    }

    val startPullFromPeer: (NearbyPeer) -> Unit = { peer ->
        if (activePull == null && incoming == null && outgoingPill == null) {
            scope.launch {
                panelOpen = false
                try {
                    val pull = session.requestPull(peer)
                    receivingStage = "准备${pull.initialMedia.mediaTypeLabel}"
                    receivingProgress = 0.28f
                    activePull = pull
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    session.status(nearbyConnectionHint(error, NearbyConnectionStage.CONFIRM))
                }
            }
        }
    }

    val activeTransferPill: NearbyTransferPillState? = when {
        incoming != null && receivingId == incoming.id && !incoming.answer.isCompleted -> {
            NearbyTransferPillState(
                id = incoming.id,
                peerName = incoming.peer.name,
                media = incoming.video,
                stageLabel = receivingStage,
                progress = receivingProgress,
                outgoing = false,
                onCancel = {
                    incoming.answer.complete(false)
                    receivingId = null
                    session.status("已取消接收，原设备继续播放")
                },
            )
        }
        pullSession != null -> {
            NearbyTransferPillState(
                id = pullSession.transferId,
                peerName = pullSession.peer.name,
                media = pullSession.initialMedia,
                stageLabel = receivingStage,
                progress = receivingProgress,
                outgoing = false,
                onCancel = {
                    activePull = null
                    scope.launch { pullSession.finish(false) }
                    session.status("已取消接力，原设备继续播放")
                },
            )
        }
        outgoingPill != null -> outgoingPill
        completedPill != null -> completedPill
        else -> null
    }

    val bannerCandidate = if (
        !panelOpen && stagedSend == null && state.approval == null &&
        incoming == null && activeTransferPill == null && preferenceHint == null
    ) {
        selectNearbyBannerCandidate(
            remotePlaying = state.remotePlaying,
            localMedia = localMediaSnapshot,
            dismissedKeys = dismissedRemoteKeys,
        )
    } else null

    fun launchConfirmedSend(peer: NearbyPeer, media: NearbyVideo) {
        val player = NearbyPlaybackBridge.resolvePlayerForMedia(miniPlayer, media) ?: return
        if (outgoingJob?.isActive == true) return
        val transferTag = nearbyToken()
        val job = scope.launch {
            var playbackChanged = false
            val listener = object : Player.Listener {
                override fun onEvents(p: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                        events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED) ||
                        events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                    ) {
                        playbackChanged = true
                    }
                }
            }
            player.addListener(listener)
            // 起飞动画已在全屏大卡片舞台内无缝冲入顶边，这里直接承接为顶部进度胶囊。
            outgoingPill = NearbyTransferPillState(
                id = transferTag,
                peerName = peer.name,
                media = media,
                stageLabel = "等待对方确认",
                progress = 0.45f,
                outgoing = true,
                onCancel = {
                    outgoingJob?.cancel()
                    outgoingPill = null
                    session.status("已取消发送，当前设备继续播放")
                },
            )
            try {
                val started = session.send(peer, media) {
                    withContext(Dispatchers.Main.immediate) {
                        outgoingPill = outgoingPill?.copy(
                            stageLabel = "正在同步最新进度",
                            progress = 0.85f,
                        )
                        check(!playbackChanged && NearbyPlaybackBridge.resolvePlayerForMedia(miniPlayer, media) === player) {
                            "发送端播放状态已改变"
                        }
                        requireNotNull(readLocalMedia()).also {
                            require(it.sameContentAs(media))
                        }
                    }
                }
                outgoingPill = null
                if (started && !playbackChanged &&
                    NearbyPlaybackBridge.resolvePlayerForMedia(miniPlayer, media) === player
                ) {
                    player.pause()
                    session.updateLocalPresence(readLocalMedia())
                    showTransferCompleted(peerName = peer.name, media = media, outgoing = true)
                } else if (started) {
                    showTransferCompleted(peerName = peer.name, media = media, outgoing = true)
                }
                if (!started) {
                    flight = NearbyFlight(media, incoming = true, peerName = peer.name)
                    delay(NEARBY_FLIGHT_INCOMING_MS.toLong())
                    panelOpen = true
                }
            } catch (cancelled: CancellationException) {
                outgoingPill = null
                throw cancelled
            } catch (error: Exception) {
                outgoingPill = null
                session.status(nearbyConnectionHint(error, NearbyConnectionStage.CONFIRM) + "；当前设备继续播放")
                flight = NearbyFlight(media, incoming = true, peerName = peer.name)
                delay(NEARBY_FLIGHT_INCOMING_MS.toLong())
                panelOpen = true
            } finally {
                player.removeListener(listener)
                outgoingPill = null
                flight = null
            }
        }
        outgoingJob = job
    }

    val pendingIncoming = incoming?.takeIf { !autoAcceptTrusted && receivingId != it.id && !it.answer.isCompleted }
    val stageVisualActive = stagedSend != null || pendingIncoming != null || flight != null

    Box(modifier = modifier.fillMaxSize()) {
        // 仅在全屏流转舞台或过场活跃时对底层页面施加预缓存高斯模糊，结束后归 null 零离屏开销。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (stageVisualActive && stageBackdropBlurEnabled) {
                        Modifier.nearbyBackdropBlur(
                            blurFractionProvider = { backdropBlurProvider?.invoke() ?: 0f },
                            maxBlurDp = 20.dp,
                            enabled = true,
                            steps = 12,
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            CompositionLocalProvider(LocalNearbyHandoff provides controller, content = content)
        }

        NearbyRemotePlaybackBanner(
            playback = bannerCandidate,
            busy = state.busy || activeTransferPill != null,
            onPull = startPullFromPeer,
            onDismiss = { dismissed ->
                runtime.dismissRemoteBanner(dismissed.peer.pin, dismissed.video)
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 发送与接收均使用非阻塞顶部进度胶囊，不遮挡底层加载中的视频画面。
        NearbyTransferStatusPill(
            transfer = activeTransferPill,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 接力完成后的弹幕、画质与音轨（杜比/Hi-Res/多语言）偏好差异提示。
        NearbyPreferenceHintBanner(
            hint = preferenceHint?.takeIf {
                activeTransferPill == null && stagedSend == null && pendingIncoming == null && flight == null
            },
            onSyncDanmaku = { currentHint, targetEnabled ->
                scope.launch { applyDanmakuPreference(currentHint.media, targetEnabled) }
                preferenceHint = currentHint.copy(targetDanmakuEnabled = null).takeIf {
                    it.targetQualityId != null || it.targetAudioQualityId != null || it.targetAudioLang != null
                }
                session.status(if (targetEnabled) "已同步开启弹幕" else "已同步关闭弹幕")
            },
            onSwitchQuality = { currentHint, targetQn ->
                val switched = NearbyPlaybackBridge.requestQualitySwitch(currentHint.media, targetQn)
                preferenceHint = currentHint.copy(targetQualityId = null, targetQualityLabel = "").takeIf {
                    it.targetDanmakuEnabled != null || it.targetAudioQualityId != null || it.targetAudioLang != null
                }
                if (switched) {
                    val label = currentHint.targetQualityLabel.ifBlank { "对端画质" }
                    session.status("已切换至 $label")
                }
            },
            onSwitchAudioQuality = { currentHint, targetAudioId ->
                val switched = NearbyPlaybackBridge.requestAudioQualitySwitch(currentHint.media, targetAudioId)
                preferenceHint = currentHint.copy(targetAudioQualityId = null, targetAudioQualityLabel = "").takeIf {
                    it.targetDanmakuEnabled != null || it.targetQualityId != null || it.targetAudioLang != null
                }
                if (switched) {
                    val label = currentHint.targetAudioQualityLabel.ifBlank { "对端音质" }
                    session.status("已切换至 $label")
                }
            },
            onSwitchAudioLang = { currentHint, targetLang ->
                val switched = NearbyPlaybackBridge.requestAudioLangSwitch(currentHint.media, targetLang)
                preferenceHint = currentHint.copy(targetAudioLang = null, targetAudioLangLabel = "").takeIf {
                    it.targetDanmakuEnabled != null || it.targetQualityId != null || it.targetAudioQualityId != null
                }
                if (switched) {
                    val label = currentHint.targetAudioLangLabel.ifBlank { "对端音轨" }
                    session.status("已切换至 $label")
                }
            },
            onDismiss = { preferenceHint = null },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (panelOpen) {
            NearbyDevicesPanel(
                state = state,
                ready = ready,
                enabled = enabled,
                autoAcceptTrusted = autoAcceptTrusted,
                onAutoAcceptTrustedChange = runtime::setAutoAcceptTrusted,
                onUpdateDeviceName = runtime::updateDeviceName,
                onDismiss = { panelOpen = false; session.cancelPairing() },
                onEnable = enable,
                onDisable = { runtime.setEnabled(false) },
                onPairingCode = { host ->
                    scope.launch {
                        try { session.createPairingCode(host) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { session.status("无法生成配对码，请检查局域网地址") }
                    }
                },
                onPair = { code ->
                    scope.launch {
                        try { session.pair(code) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { session.status(nearbyConnectionHint(error, NearbyConnectionStage.PREPARE)) }
                    }
                },
                onConnect = { peer ->
                    scope.launch {
                        try { session.connectTrusted(peer) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { session.status(nearbyConnectionHint(error, NearbyConnectionStage.CONFIRM)) }
                    }
                },
                onForget = { peer ->
                    scope.launch {
                        try { session.forget(peer) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { session.status("未能解除配对，请重试") }
                    }
                },
                onNearbyPair = { peer ->
                    scope.launch {
                        try { session.pairNearby(peer) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { session.status(nearbyConnectionHint(error, NearbyConnectionStage.PREPARE)) }
                    }
                },
                video = readLocalMedia(),
                onSend = { peer ->
                    val media = readLocalMedia()
                    if (media != null && outgoingJob?.isActive != true) {
                        // 第一步选定设备 → 收起弹窗，第二步在全屏中央展示大视频卡片舞台。
                        panelOpen = false
                        stagedSend = peer to NearbyPlaybackBridge.attachQueueContext(media)
                    }
                },
                onPull = startPullFromPeer,
            )
        }

        stagedSend?.let { (targetPeer, targetMedia) ->
            NearbySendStageOverlay(
                peer = targetPeer,
                video = targetMedia,
                canSwitchDevice = state.peers.size > 1,
                onBindBackdropBlurProvider = { provider -> backdropBlurProvider = provider },
                onSwitchDevice = {
                    stagedSend = null
                    panelOpen = true
                },
                onConfirmSend = {
                    val refreshed = readLocalMedia()
                        ?.takeIf { it.sameContentAs(targetMedia) }
                        ?.let { NearbyPlaybackBridge.attachQueueContext(it) }
                        ?: targetMedia
                    stagedSend = null
                    launchConfirmedSend(targetPeer, refreshed)
                },
                onCancel = { stagedSend = null },
            )
        }

        state.approval?.takeUnless { it.answer.isCompleted }?.let { approval ->
            val codeGroups = approval.verificationCode.split(" ").filter { it.isNotBlank() }
            AppAlertDialog(
                onDismissRequest = { approval.answer.complete(false) },
                title = { AppText("核对设备身份") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppText(
                            text = "请核对两端显示的 8 组校验码完全一致后再允许配对。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                        AppText(
                            text = "${approval.peer.name}（${approval.peer.formFactor.label}）",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = AppContentColors.primaryText(),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            codeGroups.chunked(4).forEach { rowGroups ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    rowGroups.forEach { group ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(AppSurfaceTokens.surfaceContainerHigh())
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AppText(
                                                text = group,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                                color = AppContentColors.accent(),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { AppDialogAction({ approval.answer.complete(true) }) { AppText("一致，允许配对") } },
                dismissButton = { AppDialogAction({ approval.answer.complete(false) }) { AppText("拒绝") } },
            )
        }

        pendingIncoming?.let { request ->
            NearbyReceiveStageOverlay(
                request = request,
                localDanmakuEnabled = if (request.video.isLive) localLiveDanmakuEnabled else localVideoDanmakuEnabled,
                onBindBackdropBlurProvider = { provider -> backdropBlurProvider = provider },
                onAccept = { syncDanmaku, openAsAudioOnly ->
                    panelOpen = false
                    if (request.video.isVideo) {
                        receiveAudioOnlyOverride = request.id to openAsAudioOnly
                    }
                    if (syncDanmaku && !openAsAudioOnly && request.video.danmakuEnabled != null) {
                        val targetEnabled = request.video.danmakuEnabled
                        syncedDanmakuTransfer = request.id to targetEnabled
                        scope.launch { applyDanmakuPreference(request.video, targetEnabled) }
                    }
                    receivingStage = "准备${if (request.video.isVideo && openAsAudioOnly) "听视频" else request.video.mediaTypeLabel}"
                    receivingProgress = 0.28f
                    receivingId = request.id
                },
                onReject = { request.answer.complete(false) },
            )
        }

        flight?.let {
            NearbyFlightOverlay(
                flight = it,
                onBindBackdropBlurProvider = { provider -> backdropBlurProvider = provider },
            )
        }
    }
}
