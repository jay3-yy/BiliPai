package com.android.purebilibili.feature.nearby

import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

internal class NearbyHandoffController(val openDevices: () -> Unit)
internal val LocalNearbyHandoff = staticCompositionLocalOf<NearbyHandoffController?> { null }
internal data class NearbyFlight(val video: NearbyVideo, val incoming: Boolean)

@Composable
internal fun NearbyDevicesButton(modifier: Modifier = Modifier) {
    val controller = LocalNearbyHandoff.current ?: return
    AppTextButton(onClick = controller.openDevices, modifier = modifier) { AppText(NEARBY_TITLE) }
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
    val session = remember(context) { NearbySession(context) }
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsStateWithLifecycle()
    var ready by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var receivingStage by remember { mutableStateOf("准备视频") }
    var receivingId by remember { mutableStateOf<String?>(null) }
    var flight by remember { mutableStateOf<NearbyFlight?>(null) }
    val currentOnOpenVideo by rememberUpdatedState(onOpenVideo)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted
        if (!granted) session.status("需要局域网权限才能连接其他设备")
    }
    val enable: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 37 && context.applicationInfo.targetSdkVersion >= 37 &&
            ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
        ) permission.launch("android.permission.ACCESS_LOCAL_NETWORK") else enabled = true
    }
    val controller = remember { NearbyHandoffController { panelOpen = true } }
    LaunchedEffect(session) {
        try { session.initialize(); ready = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { session.status("无法读取设备身份，请稍后重试") }
    }
    DisposableEffect(session, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                receivingId = null
                session.close()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); session.close() }
    }
    DisposableEffect(session, panelOpen) {
        session.setPairingVisible(panelOpen)
        onDispose { session.setPairingVisible(false) }
    }
    LaunchedEffect(state.pairingCode) {
        if (state.pairingCode != null) { delay(300_000); session.cancelPairing() }
    }
    LaunchedEffect(session, enabled, ready, lifecycleOwner) {
        if (!enabled || !ready) { session.close(); return@LaunchedEffect }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { session.listen() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                session.status("无法开启局域网连接，请检查网络或端口占用后重新开启")
                enabled = false
            }
            finally { session.close() }
        }
    }
    val incoming = state.incoming
    LaunchedEffect(incoming?.id, receivingId) {
        if (incoming == null || incoming.answer.isCompleted || receivingId != incoming.id) return@LaunchedEffect
        var preparedPlayer: androidx.media3.exoplayer.ExoPlayer? = null
        var committedVideo: NearbyVideo? = null
        var committedAt = 0L
        var succeeded = false
        var receiverTakenOver = false
        try {
            receivingStage = "准备视频"
            session.status("正在准备 ${incoming.video.title}…")
            withTimeout(45_000) {
                flight = NearbyFlight(incoming.video, incoming = true)
                delay(360)
                currentOnOpenVideo(incoming.video)
                flight = null
                // 必须等接收端实际播放到交接进度，发送端才会暂停。
                while (true) {
                    check(!incoming.answer.isCompleted) { "接力请求已结束" }
                    val player = miniPlayer.player
                    if (preparedPlayer != null && (player !== preparedPlayer ||
                        miniPlayer.currentBvid != incoming.video.bvid || miniPlayer.currentCid != incoming.video.cid)) {
                        receiverTakenOver = true
                        error("接收端已切换内容")
                    }
                    if (miniPlayer.currentBvid == incoming.video.bvid && miniPlayer.currentCid == incoming.video.cid &&
                        player != null) {
                        check(player.playerError == null) { "接收端播放失败" }
                        if (player.playbackState != androidx.media3.common.Player.STATE_READY) {
                            delay(200)
                            continue
                        }
                        if (preparedPlayer !== player) {
                            // 页面和媒体已就绪后，通过原连接取得发送端最新进度。
                            player.pause()
                            preparedPlayer = player
                            receivingStage = "同步最新进度"
                            session.status("视频已就绪，正在同步最新进度…")
                            val latest = incoming.latest()
                            check(!incoming.answer.isCompleted && miniPlayer.player === player &&
                                miniPlayer.currentBvid == latest.bvid && miniPlayer.currentCid == latest.cid)
                            check(player.duration <= 0L || latest.positionMs <= player.duration + 3000L)
                            committedVideo = latest
                            committedAt = SystemClock.elapsedRealtime()
                            player.setPlaybackSpeed(latest.speed)
                            player.seekTo(latest.positionMs)
                            player.play()
                            receivingStage = "等待播放确认"
                        } else {
                            val latest = requireNotNull(committedVideo)
                            if (!player.playWhenReady || player.playbackParameters.speed != latest.speed ||
                                !nearbyPlaybackPositionMatches(latest, player.currentPosition,
                                    SystemClock.elapsedRealtime() - committedAt)) {
                                receiverTakenOver = true
                                error("接收端播放状态已改变")
                            }
                            if (player.isPlaying) break
                        }
                    }
                    delay(200)
                }
                check(incoming.answer.complete(true)) { "接力请求已结束" }
                succeeded = true
                session.status("已接续 ${incoming.video.title}")
            }
        } catch (_: TimeoutCancellationException) {
            incoming.answer.complete(false)
            session.status("接续播放超时，发送设备继续播放")
        } catch (cancelled: CancellationException) {
            incoming.answer.complete(false)
            throw cancelled
        } catch (_: Exception) {
            incoming.answer.complete(false)
            session.status("未能完成接续，发送设备继续播放")
        } finally {
            // 只停止本次尚未确认的播放；用户已经切换内容或接管操作时不再控制播放器。
            val player = preparedPlayer
            runCatching {
                val latest = committedVideo
                if (!succeeded && !receiverTakenOver && player != null && miniPlayer.player === player &&
                    miniPlayer.currentBvid == incoming.video.bvid && miniPlayer.currentCid == incoming.video.cid &&
                    (latest == null || (player.playbackParameters.speed == latest.speed &&
                        nearbyPlaybackPositionMatches(latest, player.currentPosition,
                            SystemClock.elapsedRealtime() - committedAt)))) {
                    player.pause()
                }
            }
            receivingId = null
            flight = null
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNearbyHandoff provides controller, content = content)
        if (panelOpen) NearbyDevicesPanel(
            state = state,
            ready = ready,
            onDismiss = { panelOpen = false; session.cancelPairing() },
            onEnable = enable,
            onDisable = { enabled = false; session.close() },
            onPairingCode = { host -> scope.launch {
                try { session.createPairingCode(host) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { session.status("无法生成配对码，请检查局域网地址") }
            } },
            onPair = { code -> scope.launch {
                try { session.pair(code) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { session.status(nearbyConnectionHint(error, NearbyConnectionStage.PREPARE)) }
            } },
            onConnect = { peer -> scope.launch {
                try { session.connectTrusted(peer) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { session.status(nearbyConnectionHint(error, NearbyConnectionStage.CONFIRM)) }
            } },
            onForget = { peer -> scope.launch {
                try { session.forget(peer) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { session.status("未能解除配对，请重试") }
            } },
            onNearbyPair = { peer -> scope.launch {
                try { session.pairNearby(peer) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { session.status(nearbyConnectionHint(error, NearbyConnectionStage.PREPARE)) }
            } },
            video = currentNearbyVideo(miniPlayer),
            onSend = { peer ->
                val video = currentNearbyVideo(miniPlayer)
                val player = miniPlayer.player
                if (video != null && player != null) scope.launch {
                    panelOpen = false
                    var playbackChanged = false
                    val listener = object : androidx.media3.common.Player.Listener {
                        override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                            if (events.contains(androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY) ||
                                events.contains(androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                                events.contains(androidx.media3.common.Player.EVENT_PLAYBACK_PARAMETERS_CHANGED) ||
                                events.contains(androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED)) playbackChanged = true
                        }
                    }
                    player.addListener(listener)
                    flight = NearbyFlight(video, incoming = false)
                    try {
                        val started = session.send(peer, video) {
                            withContext(Dispatchers.Main.immediate) {
                                check(!playbackChanged && miniPlayer.player === player) { "发送端播放状态已改变" }
                                requireNotNull(currentNearbyVideo(miniPlayer)).also {
                                    require(it.bvid == video.bvid && it.cid == video.cid)
                                }
                            }
                        }
                        if (started && !playbackChanged && miniPlayer.player === player && miniPlayer.currentBvid == video.bvid &&
                            miniPlayer.currentCid == video.cid) player.pause()
                        if (!started) {
                            flight = NearbyFlight(video, incoming = true)
                            delay(360)
                            panelOpen = true
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        session.status(nearbyConnectionHint(error, NearbyConnectionStage.CONFIRM) + "；当前设备继续播放")
                        flight = NearbyFlight(video, incoming = true)
                        delay(360)
                        panelOpen = true
                    }
                    finally { player.removeListener(listener); flight = null }
                }
            },
        )
        state.approval?.takeUnless { it.answer.isCompleted }?.let { approval ->
            AppAlertDialog(
                onDismissRequest = { approval.answer.complete(false) },
                title = { AppText("允许配对？") },
                text = { AppText("请核对两端显示的完整校验码一致，再分别确认。\n设备：${approval.peer.name}\n\n${approval.verificationCode}") },
                confirmButton = { AppDialogAction({ approval.answer.complete(true) }) { AppText("校验码一致，允许") } },
                dismissButton = { AppDialogAction({ approval.answer.complete(false) }) { AppText("拒绝") } },
            )
        }
        incoming?.takeIf { receivingId == it.id && !it.answer.isCompleted }?.let { request ->
            val cancelReceiving = {
                request.answer.complete(false)
                receivingId = null
                session.status("已取消接收，发送设备继续播放")
                Unit
            }
            AppAlertDialog(
                onDismissRequest = cancelReceiving,
                title = { AppText("正在接续播放") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppText(request.video.title, maxLines = 2)
                        AppText("来自 ${request.peer.name}", style = MaterialTheme.typography.bodySmall)
                        AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        AppText(receivingStage)
                        AppText("准备视频 → 同步最新进度 → 等待播放确认", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { AppDialogAction(cancelReceiving) { AppText("取消接收") } },
            )
        }
        incoming?.takeIf { receivingId != it.id && !it.answer.isCompleted }?.let { request ->
            AppAlertDialog(
                onDismissRequest = { request.answer.complete(false) },
                title = { AppText("接续播放？") },
                text = { AppText("${request.peer.name} 发来：${request.video.title}") },
                confirmButton = { AppDialogAction({ panelOpen = false; receivingStage = "准备视频"; receivingId = request.id }) { AppText("继续播放") } },
                dismissButton = { AppDialogAction({ request.answer.complete(false) }) { AppText("暂不接收") } },
            )
        }
        flight?.let { NearbyFlightOverlay(it) }
    }
}

private fun currentNearbyVideo(mini: MiniPlayerManager): NearbyVideo? {
    if (mini.isLiveMode) return null
    val player = mini.player ?: return null
    val video = NearbyVideo(mini.currentBvid.orEmpty(), mini.currentCid, player.currentPosition.coerceAtLeast(0L),
        player.playbackParameters.speed, mini.currentTitle.take(200), normalizeNearbyCover(mini.currentCover))
    return runCatching { video.validate(); video }.getOrNull()
}
