package com.android.purebilibili.feature.nearby

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppPrimaryButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.core.ui.motion.continuityTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import kotlinx.coroutines.delay

/**
 * 全新「设备接力」流转面板：
 * - 无冲突垂直拖拽手势，完全契合 BottomSheet 滚动与下滑关闭直觉；
 * - 顶部精简状态与开关 → 本机当前播放紧凑卡 → 一键流转设备列表（每台设备单一清晰主操作）→ 附近设备与折叠手动配对；
 * - 完整适配 MD3 / Miuix / 液态玻璃以及手机、平板与窄分屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NearbyDevicesPanel(
    state: NearbyState,
    ready: Boolean,
    enabled: Boolean,
    autoAcceptTrusted: Boolean,
    onAutoAcceptTrustedChange: (Boolean) -> Unit,
    onUpdateDeviceName: (String) -> Unit,
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
    onPull: (NearbyPeer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var showEndpoint by remember { mutableStateOf(false) }
    var showManualPairing by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var editingDeviceName by remember { mutableStateOf(false) }
    var deviceNameDraft by remember(state.localDeviceName) { mutableStateOf(state.localDeviceName) }
    val clipboard = LocalClipboardManager.current
    val reduceMotion = rememberSystemReduceMotion()
    val haptic = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    val hapticEnabled = LocalAppThemeConfig.current.hapticFeedbackEnabled
    val accent = AppContentColors.accent()

    val discoveredUnpaired = remember(state.discovered, state.peers) {
        state.discovered.filter { found -> state.peers.none { it.pin == found.pin } }
    }
    val discoveredPins = remember(state.discovered) {
        state.discovered.map { it.pin }.toSet()
    }
    val sortedPeers = remember(state.peers, state.connectedPins, discoveredPins) {
        state.peers.sortedWith(
            compareByDescending<NearbyPeer> { it.pin in state.connectedPins }
                .thenByDescending { it.pin in discoveredPins }
        )
    }

    AppModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .heightIn(max = 680.dp)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. 顶栏：标题 + 本机可编辑名称徽标 + 状态指示 + 总开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppText(
                            text = NEARBY_TITLE,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = AppContentColors.primaryText(),
                        )
                        NearbyGlassPill(
                            nativeContainerColor = if (state.receiving) accent.copy(alpha = 0.14f)
                            else AppSurfaceTokens.surfaceContainerHighest(),
                            glassContainerColor = if (state.receiving) accent.copy(alpha = 0.16f) else null,
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        deviceNameDraft = state.localDeviceName
                                        editingDeviceName = !editingDeviceName
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                AppText(
                                    text = state.localDeviceName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = if (state.receiving) accent else AppContentColors.secondaryText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                AppIcon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "修改本机名称",
                                    tint = if (state.receiving) accent else AppContentColors.secondaryText(),
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = state.status,
                        transitionSpec = {
                            fadeIn(continuityTween(if (reduceMotion) 0 else 160)) togetherWith
                                fadeOut(continuityTween(if (reduceMotion) 0 else 120))
                        },
                        label = "nearbyPanelStatus",
                    ) { statusText ->
                        AppText(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AppSwitch(
                    checked = enabled || state.receiving,
                    onCheckedChange = { checked ->
                        if (checked) onEnable() else onDisable()
                    },
                    enabled = ready && !state.busy,
                )
            }

            // 本机设备名称内联编辑区
            AnimatedVisibility(
                visible = editingDeviceName,
                enter = if (reduceMotion) fadeIn(continuityTween(0)) else expandVertically(continuityTween(180)) + fadeIn(continuityTween(160)),
                exit = if (reduceMotion) fadeOut(continuityTween(0)) else shrinkVertically(continuityTween(160)) + fadeOut(continuityTween(120)),
            ) {
                NearbySheetCardSurface(
                    nativeColor = AppSurfaceTokens.surfaceContainer(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppOutlinedTextField(
                            value = deviceNameDraft,
                            onValueChange = { deviceNameDraft = it.take(24) },
                            label = { AppText("本机显示名称（${state.localFormFactor.label}）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppTextButton(onClick = {
                                onUpdateDeviceName("")
                                editingDeviceName = false
                            }) {
                                AppText("恢复默认")
                            }
                            AppTextButton(onClick = { editingDeviceName = false }) {
                                AppText("取消")
                            }
                            AppTextButton(onClick = {
                                onUpdateDeviceName(deviceNameDraft)
                                editingDeviceName = false
                            }) {
                                AppText("保存")
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 2. 已配对设备选择列表（点击已连接设备 → 收起弹窗并在屏幕中央展开大视频卡片舞台）
                if (sortedPeers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NearbySectionHeader(
                            title = if (video != null && state.receiving) "选择接力设备" else "我的设备",
                            badge = "${sortedPeers.size}",
                        )
                        sortedPeers.forEach { peer ->
                            val isConnected = peer.pin in state.connectedPins
                            val isOnline = isConnected || peer.pin in discoveredPins
                            val remotePlayback = state.remotePlaying[peer.pin]
                            val remoteSameAsLocal = remotePlayback != null &&
                                video != null &&
                                remotePlayback.video.sameContentAs(video)

                            NearbyDeviceTransferCard(
                                peer = peer,
                                isConnected = isConnected,
                                isOnline = isOnline,
                                canPush = video != null && state.receiving,
                                receiving = state.receiving,
                                busy = state.busy,
                                remotePlayback = remotePlayback,
                                remoteSameAsLocal = remoteSameAsLocal,
                                onConnect = { onConnect(peer) },
                                onPush = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onSend(peer)
                                },
                                onPull = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onPull(peer)
                                },
                                onForget = { onForget(peer) },
                            )
                        }
                    }
                } else if (state.receiving) {
                    NearbySheetCardSurface(
                        nativeColor = AppSurfaceTokens.surfaceContainer(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            AppText(
                                text = "暂未配对设备",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = AppContentColors.primaryText(),
                            )
                            AppText(
                                text = "在另一台手机或平板上打开 BiliPai 的「设备接力」并连接同一 Wi-Fi，即可在下方自动发现或扫码完成加密配对。",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                            )
                        }
                    }
                }

                // 3. 接收行为偏好：受信任设备推送自动接续
                if (state.receiving || sortedPeers.isNotEmpty()) {
                    NearbySheetCardSurface(
                        nativeColor = AppSurfaceTokens.surfaceContainer(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AppText(
                                    text = "受信任设备自动接续",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = AppContentColors.primaryText(),
                                )
                                AppText(
                                    text = "收到已配对设备推送时，下落展示后自动开始播放",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                            }
                            AppSwitch(
                                checked = autoAcceptTrusted,
                                onCheckedChange = onAutoAcceptTrustedChange,
                            )
                        }
                    }
                }

                // 4. 附近局域网发现的新设备 + 扫码 / 二维码 / 配对码配对
                if (state.receiving) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Wifi,
                                    contentDescription = null,
                                    tint = AppContentColors.secondaryText(),
                                    modifier = Modifier.size(15.dp),
                                )
                                AppText(
                                    text = "附近设备",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    color = AppContentColors.secondaryText(),
                                )
                                AppText(
                                    text = if (discoveredUnpaired.isEmpty()) "· 扫描中" else "· ${discoveredUnpaired.size} 台",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppContentColors.secondaryText(),
                                )
                            }
                            AppTextButton(
                                onClick = { showQrScanner = true },
                                enabled = !state.busy,
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.QrCode2,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(4.dp))
                                AppText("扫码配对")
                            }
                        }

                        discoveredUnpaired.forEach { peer ->
                            NearbySheetCardSurface(
                                nativeColor = AppSurfaceTokens.surfaceContainerHigh(),
                                elevated = true,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(AppSurfaceTokens.surfaceContainerHighest()),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppIcon(
                                            imageVector = nearbyDeviceIcon(peer.formFactor),
                                            contentDescription = null,
                                            tint = AppContentColors.primaryText(),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        AppText(
                                            text = peer.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = AppContentColors.primaryText(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        AppText(
                                            text = "${peer.formFactor.label} · ${peer.host}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppContentColors.secondaryText(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    AppTextButton(
                                        onClick = { onNearbyPair(peer) },
                                        enabled = !state.busy,
                                    ) {
                                        AppText("配对")
                                    }
                                }
                            }
                        }

                        // 二维码 / 配对码手动配对折叠区
                        NearbySheetCardSurface(
                            nativeColor = AppSurfaceTokens.surfaceContainer(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showManualPairing = !showManualPairing }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        AppText(
                                            text = "二维码 / 配对码配对",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = AppContentColors.primaryText(),
                                        )
                                        state.localEndpoint?.let { addr ->
                                            AppText(
                                                text = "本机 ${state.localDeviceName} · $addr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppContentColors.secondaryText(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    AppIcon(
                                        imageVector = if (showManualPairing) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = if (showManualPairing) "收起" else "展开",
                                        tint = AppContentColors.secondaryText(),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                AnimatedVisibility(
                                    visible = showManualPairing,
                                    enter = if (reduceMotion) fadeIn(continuityTween(0)) else expandVertically(continuityTween(200)) + fadeIn(continuityTween(180)),
                                    exit = if (reduceMotion) fadeOut(continuityTween(0)) else shrinkVertically(continuityTween(180)) + fadeOut(continuityTween(140)),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AppText(
                                            text = "支持扫码直连或粘贴配对码；真机与模拟器配对时，请由真机生成后在模拟器中扫描或粘贴（手机无法直连模拟器 10.0.2.x 地址）。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppContentColors.secondaryText(),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AppTextButton(
                                                onClick = { onPairingCode(endpoint) },
                                                enabled = !state.busy,
                                            ) {
                                                AppText("生成二维码")
                                            }
                                            AppTextButton(
                                                onClick = { showQrScanner = true },
                                                enabled = !state.busy,
                                            ) {
                                                AppText("扫码")
                                            }
                                            AppTextButton(onClick = { showEndpoint = !showEndpoint }) {
                                                AppText(if (showEndpoint) "隐藏地址" else "自定义地址")
                                            }
                                        }
                                        state.pairingCode?.let { pairingCode ->
                                            NearbyPairingQrCard(
                                                pairingCode = pairingCode,
                                                onCopyText = {
                                                    clipboard.setText(AnnotatedString(pairingCode))
                                                },
                                            )
                                        }
                                        if (showEndpoint) {
                                            AppOutlinedTextField(
                                                value = endpoint,
                                                onValueChange = { endpoint = it.take(80) },
                                                label = { AppText("接收地址（IP:端口，可留空）") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        AppOutlinedTextField(
                                            value = code,
                                            onValueChange = { code = it.take(2048) },
                                            label = { AppText("粘贴另一台设备的配对码") },
                                            maxLines = 3,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AppTextButton(onClick = {
                                                code = clipboard.getText()?.text.orEmpty().take(2048)
                                            }) {
                                                AppIcon(
                                                    imageVector = Icons.Outlined.ContentPaste,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.size(4.dp))
                                                AppText("粘贴")
                                            }
                                            AppTextButton(
                                                onClick = { onPair(code) },
                                                enabled = code.isNotBlank() && !state.busy,
                                            ) {
                                                AppText("开始配对")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        NearbyQrScannerSheet(
            onDismiss = { showQrScanner = false },
            onScannedPairCode = { scanned ->
                showQrScanner = false
                onPair(scanned)
            },
        )
    }
}

@Composable
private fun NearbySectionHeader(
    title: String,
    badge: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = AppContentColors.secondaryText(),
        )
        NearbyGlassPill(
            nativeContainerColor = AppSurfaceTokens.surfaceContainerHighest(),
        ) {
            AppText(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** 每台已配对设备一张清爽卡片：点击右侧主操作即可流转或连接，下方按需展开对端播放拉回条。 */
@Composable
private fun NearbyDeviceTransferCard(
    peer: NearbyPeer,
    isConnected: Boolean,
    isOnline: Boolean,
    canPush: Boolean,
    receiving: Boolean,
    busy: Boolean,
    remotePlayback: NearbyRemotePlayback?,
    remoteSameAsLocal: Boolean,
    onConnect: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onForget: () -> Unit,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val cardShape = AppShapes.container(ContainerLevel.Card)
    val accent = AppContentColors.accent()
    val baseContainer = AppSurfaceTokens.surfaceContainerHigh()
    var confirmForget by remember(peer.pin) { mutableStateOf(false) }
    LaunchedEffect(confirmForget) {
        if (confirmForget) {
            delay(3_000)
            confirmForget = false
        }
    }
    val connectedTint by animateColorAsState(
        targetValue = if (isConnected) accent.copy(alpha = 0.06f) else Color.Transparent,
        animationSpec = continuityTween(if (reduceMotion) 0 else 180),
        label = "nearbyDeviceConnectedTint",
    )

    NearbySheetCardSurface(
        nativeColor = baseContainer,
        shape = cardShape,
        elevated = true,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canPush && isConnected && !busy) {
                    Modifier.clickable(onClick = onPush)
                } else {
                    Modifier
                }
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(connectedTint)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 设备图标 + 右下角在线状态圆点
                Box(modifier = Modifier.size(42.dp)) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (isConnected) accent.copy(alpha = 0.16f)
                                else AppSurfaceTokens.surfaceContainerHighest()
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            imageVector = nearbyDeviceIcon(peer.formFactor),
                            contentDescription = null,
                            tint = if (isConnected) accent else AppContentColors.secondaryText(),
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(baseContainer)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConnected -> accent
                                    isOnline -> AppContentColors.secondaryText()
                                    else -> AppContentColors.secondaryText().copy(alpha = 0.32f)
                                }
                            ),
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText(
                        text = peer.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = AppContentColors.primaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val statusText = when {
                        isConnected -> "${peer.formFactor.label} · 已加密连接"
                        isOnline -> "${peer.formFactor.label} · 局域网在线"
                        else -> "${peer.formFactor.label} · 未连接"
                    }
                    AppText(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) accent else AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (canPush && isConnected) {
                        AppPrimaryButton(
                            text = "接力",
                            onClick = onPush,
                            enabled = !busy,
                            leadingIcon = Icons.Outlined.SwapHoriz,
                        )
                    } else if (!isConnected) {
                        AppTextButton(
                            onClick = onConnect,
                            enabled = receiving && !busy,
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            AppText("连接")
                        }
                    }
                    if (confirmForget) {
                        AppTextButton(
                            onClick = {
                                confirmForget = false
                                onForget()
                            },
                            enabled = !busy,
                        ) {
                            AppText(
                                text = "确认解除",
                                color = AppSurfaceTokens.error(),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    } else {
                        AppIconButton(
                            onClick = { confirmForget = true },
                            enabled = !busy,
                            modifier = Modifier.size(34.dp),
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "解除配对",
                                tint = AppContentColors.secondaryText(),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // 对端正在播放副条：支持一键拉回本机
            if (remotePlayback != null) {
                val remoteMedia = remotePlayback.video
                val estPos = estimateNearbyPlaybackPositionMs(remoteMedia, System.currentTimeMillis())
                NearbySheetCardSurface(
                    nativeColor = AppSurfaceTokens.surfaceContainer(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppSurfaceTokens.surfaceContainerHighest()),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (remoteMedia.cover.isNotBlank()) {
                                AsyncImage(
                                    model = remoteMedia.cover,
                                    contentDescription = null,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                AppIcon(
                                    imageVector = nearbyMediaIcon(remoteMedia),
                                    contentDescription = null,
                                    tint = AppContentColors.secondaryText(),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AppText(
                                text = remoteMedia.title.ifBlank { remoteMedia.mediaTypeLabel },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = AppContentColors.primaryText(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val prefSummary = formatNearbyPreferenceSummary(remoteMedia, includeSpeed = true)
                            val detail = buildString {
                                if (remoteSameAsLocal) append("同内容 · ")
                                if (remoteMedia.isLive) {
                                    append("直播")
                                    if (remoteMedia.uname.isNotBlank()) append(" · ${remoteMedia.uname}")
                                } else {
                                    append(if (remoteMedia.playing) "对端播放至 " else "对端暂停在 ")
                                    append(formatNearbyDuration(estPos))
                                }
                                if (prefSummary.isNotBlank()) {
                                    append(" · ")
                                    append(prefSummary)
                                }
                            }
                            AppText(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppContentColors.secondaryText(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AppTextButton(
                            onClick = onPull,
                            enabled = receiving && !busy,
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(2.dp))
                            AppText("拉回本机")
                        }
                    }
                }
            }
        }
    }
}

/** 流转过场中的精致封面卡片（平整高光描边，无锯齿）。 */
@Composable
private fun NearbyFlightCard(
    video: NearbyVideo,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, Color.White.copy(alpha = 0.22f), cardShape),
    ) {
        if (video.cover.isNotBlank()) {
            AsyncImage(
                model = video.cover,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.20f),
                        0.45f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    )
                )
        )
        // 顶部流转方向微标
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.56f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            AppIcon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
            AppText(
                text = caption,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            AppText(
                text = video.title.ifBlank { video.mediaTypeLabel },
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            val detail = if (video.isLive) {
                if (video.uname.isNotBlank()) "${video.mediaTypeLabel} · ${video.uname}" else video.mediaTypeLabel
            } else {
                "${video.mediaTypeLabel} · ${formatNearbyDuration(video.positionMs)}"
            }
            AppText(
                text = detail,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 重写后的全屏接力流转过场动画：
 * - 接收 / 回落：顶边前倾俯冲落位（带柔和微过冲）→ 黄金清晰停顿 → 轻盈扩展消散，
 *   同步联动底层页面高斯模糊与中央封面色彩高斯弥散光晕。
 */
@Composable
internal fun NearbyFlightOverlay(
    flight: NearbyFlight,
    onBindBackdropBlurProvider: ((() -> Float)?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(flight) { Animatable(0f) }
    val reduceMotion = rememberSystemReduceMotion()
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val blurEnabled = !reduceMotion && !isLowBlurBudgetForced()
    val accent = AppContentColors.accent()
    val density = LocalDensity.current
    val durationMs = if (flight.incoming) NEARBY_FLIGHT_INCOMING_MS else NEARBY_FLIGHT_OUTGOING_MS
    val latestBindBlur by rememberUpdatedState(onBindBackdropBlurProvider)

    LaunchedEffect(flight, reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, continuityTween(durationMs))
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val travel = (constraints.maxHeight * 0.44f).coerceAtLeast(180f)
        val caption = buildString {
            append(if (flight.incoming) "接力来自" else "流转至")
            if (flight.peerName.isNotBlank()) append(" ${flight.peerName}")
        }

        DisposableEffect(flight, travel, reduceMotion) {
            latestBindBlur {
                resolveNearbyFlightTransform(progress.value, flight.incoming, travel, reduceMotion).backdropBlurFraction
            }
            onDispose { latestBindBlur(null) }
        }

        // 柔和暗角衬底
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = resolveNearbyFlightTransform(progress.value, flight.incoming, travel, reduceMotion)
                    alpha = t.scrimAlpha
                }
                .background(Color.Black)
        )

        // 中央封面无缝羽化氛围高斯光晕
        NearbyAmbientCoverGlow(
            coverUrl = flight.video.cover,
            accent = accent,
            blurEnabled = blurEnabled,
            alphaProvider = {
                resolveNearbyFlightTransform(progress.value, flight.incoming, travel, reduceMotion).ambientGlowAlpha
            },
            translationYProvider = {
                resolveNearbyFlightTransform(progress.value, flight.incoming, travel, reduceMotion).translationYPx
            },
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(width = 560.dp, height = 460.dp),
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.86f)
                .height(216.dp)
                .graphicsLayer {
                    val t = resolveNearbyFlightTransform(progress.value, flight.incoming, travel, reduceMotion)
                    cameraDistance = 14f * density.density
                    translationY = t.translationYPx
                    scaleX = t.scale
                    scaleY = t.scale
                    rotationX = t.rotationXDeg
                    alpha = t.alpha
                }
                .then(
                    if (reduceMotion) Modifier else Modifier.nearbyCleanPulseRing(
                        haloProgressProvider = {
                            resolveNearbyFlightTransform(progress.value, flight.incoming, travel, reduceMotion).haloProgress
                        },
                        haloAlphaProvider = {
                            resolveNearbyFlightTransform(progress.value, flight.incoming, travel, reduceMotion).haloAlpha
                        },
                        accent = accent,
                        highlight = Color.White,
                        glassEnabled = glassEnabled,
                        cornerRadiusDp = 22.dp,
                    )
                )
        ) {
            NearbyFlightCard(
                video = flight.video,
                caption = caption,
                modifier = Modifier
                    .matchParentSize()
                    .nearbyFlightBlur(
                        blurFractionProvider = {
                            resolveNearbyFlightTransform(progress.value, flight.incoming, travel, reduceMotion).blurFraction
                        },
                        enabled = blurEnabled,
                        maxBlurDp = 10.dp,
                    ),
            )
        }
    }
}
