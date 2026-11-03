package com.android.purebilibili.feature.nearby

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.motion.continuityTween
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NearbyDevicesPanel(
    state: NearbyState,
    ready: Boolean,
    onDismiss: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onPairingCode: (String) -> Unit,
    onPair: (String) -> Unit,
    onForget: (NearbyPeer) -> Unit,
    onConnect: (NearbyPeer) -> Unit,
    onNearbyPair: (NearbyPeer) -> Unit,
    video: NearbyVideo?,
    onSend: (NearbyPeer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var showEndpoint by remember { mutableStateOf(false) }
    var showManualPairing by remember { mutableStateOf(false) }
    var selectedPin by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val selected = state.peers.firstOrNull { it.pin == selectedPin } ?: state.peers.singleOrNull()
    AppModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.fillMaxWidth().widthIn(max = 600.dp).heightIn(max = 680.dp)
            .imePadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppText(NEARBY_TITLE, style = MaterialTheme.typography.titleLarge)
            AppText(state.status, style = MaterialTheme.typography.bodySmall)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppText("两端连接同一局域网并打开此面板，选择设备、核对身份后即可加密接续播放。", style = MaterialTheme.typography.bodyMedium)
            state.localEndpoint?.let { AppText("本机接收地址：$it", style = MaterialTheme.typography.bodySmall) }
            Row {
                AppTextButton(onClick = if (state.receiving) onDisable else onEnable, enabled = ready && !state.busy) {
                    AppText(if (state.receiving) "关闭接收" else "开启设备接力")
                }
            }
            if (state.receiving) {
                AppText("正在发现局域网设备；首次配对需在两端核对相同的校验码。", style = MaterialTheme.typography.bodySmall)
                state.discovered.filter { found -> state.peers.none { it.pin == found.pin } }.forEach { peer ->
                    AppTextButton(onClick = { onNearbyPair(peer) }, enabled = !state.busy) { AppText("配对 ${peer.name}") }
                }
                AppTextButton(onClick = { showManualPairing = !showManualPairing }) { AppText("无法发现？手动配对") }
            }
            if (showManualPairing && state.receiving) {
                AppTextButton(onClick = { onPairingCode(endpoint) }, enabled = !state.busy) { AppText("生成配对码") }
            }
            state.pairingCode?.takeIf { showManualPairing }?.let { pairingCode ->
                AppText("配对码 5 分钟内有效，仅供你要配对的设备使用。", style = MaterialTheme.typography.bodySmall)
                AppTextButton(onClick = { clipboard.setText(AnnotatedString(pairingCode)) }) { AppText("复制配对码") }
            }
            if (state.receiving && showManualPairing) {
                AppText("手机与模拟器配对：优先由实体手机生成配对码，复制到模拟器后点击配对。连接保持期间，手机也可通过同一连接回传视频。模拟器可能无法自动发现局域网设备。",
                    style = MaterialTheme.typography.bodySmall)
                AppTextButton(onClick = { showEndpoint = !showEndpoint }) { AppText("手动连接地址") }
                if (showEndpoint) AppOutlinedTextField(value = endpoint, onValueChange = { endpoint = it.take(80) },
                    label = { AppText("接收地址（IP:端口，可留空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AppOutlinedTextField(value = code, onValueChange = { code = it.take(2048) },
                    label = { AppText("另一台设备的配对码") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Row {
                    AppTextButton(onClick = { code = clipboard.getText()?.text.orEmpty().take(2048) }) { AppText("粘贴") }
                    AppTextButton(onClick = { onPair(code) }, enabled = code.isNotBlank() && !state.busy) { AppText("配对") }
                }
            }
            state.peers.forEach { peer ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(onClick = { selectedPin = peer.pin }, modifier = Modifier.weight(1f)) {
                        AppText((if (selected?.pin == peer.pin) "✓ " else "") + peer.name + if (peer.pin in state.connectedPins) " · 已连接" else " · 未连接")
                    }
                    if (peer.pin !in state.connectedPins) {
                        AppTextButton(onClick = { onConnect(peer) }, enabled = state.receiving && !state.busy) { AppText("连接") }
                    }
                    AppTextButton(onClick = { onForget(peer) }, enabled = !state.busy) { AppText("解除配对") }
                }
            }
            if (video != null && selected != null && state.receiving) {
                val density = LocalDensity.current
                val threshold = with(density) { 72.dp.toPx() }
                var drag by remember(video.bvid, video.cid) { mutableFloatStateOf(0f) }
                val latestSend by rememberUpdatedState(onSend)
                val latestPeer by rememberUpdatedState(selected)
                NearbyVideoCard(video, Modifier.fillMaxWidth().height(140.dp)
                    .graphicsLayer { translationY = drag; scaleX = 1f + drag / (threshold * 12); scaleY = scaleX }
                    .pointerInput(video.bvid, video.cid, selected.pin, state.busy) {
                        if (!state.busy) detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragCancel = { drag = 0f },
                            onDragEnd = {
                                val send = drag <= -threshold
                                drag = 0f
                                if (send) latestPeer?.let(latestSend)
                            },
                        ) { change, amount ->
                            change.consume()
                            drag = (drag + amount).coerceIn(-threshold * 1.5f, 0f)
                        }
                    })
                AppText("上滑卡片，发送到 ${selected.name}", style = MaterialTheme.typography.bodySmall)
                AppTextButton(onClick = { onSend(selected) }, enabled = !state.busy) { AppText("发送当前视频") }
            } else if (state.peers.isNotEmpty() && video == null) {
                AppText("打开一个视频后，可在播放器中发送接力。", style = MaterialTheme.typography.bodySmall)
            }
            }
        }
    }
}

@Composable
private fun NearbyVideoCard(video: NearbyVideo, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(video.cover, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
        AppText(video.title, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            color = Color.White, maxLines = 2, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun NearbyFlightOverlay(flight: NearbyFlight, modifier: Modifier = Modifier) {
    val progress = remember(flight) { Animatable(0f) }
    val reduceMotion = rememberSystemReduceMotion()
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val blurEnabled = !reduceMotion && !isLowBlurBudgetForced()
    LaunchedEffect(flight, reduceMotion) {
        progress.animateTo(1f, continuityTween(if (reduceMotion) 0 else NEARBY_FLIGHT_DURATION_MS))
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val travel = constraints.maxHeight * 0.55f
        Box(Modifier.align(Alignment.Center).widthIn(max = 384.dp)
            .fillMaxWidth(0.8f).height(214.dp).graphicsLayer {
                val distance = if (flight.incoming) 1f - progress.value else progress.value
                translationY = if (reduceMotion) 0f else -travel * distance
                scaleX = 1f - distance * 0.55f
                scaleY = scaleX
                alpha = 1f - distance
            }.then(if (reduceMotion) Modifier else Modifier.nearbyLivingEdge(
                progressProvider = { progress.value },
                accent = MaterialTheme.colorScheme.primary,
                highlight = MaterialTheme.colorScheme.onSurface,
                glassEnabled = glassEnabled,
            ))) {
            NearbyVideoCard(flight.video, Modifier.matchParentSize().padding(12.dp)
                .nearbyFlightBlur(
                    distanceProvider = { if (flight.incoming) 1f - progress.value else progress.value },
                    enabled = blurEnabled,
                ))
        }
    }
}
