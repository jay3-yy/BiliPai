package com.android.purebilibili.feature.nearby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppPopupSurface
import com.android.purebilibili.core.ui.AppPopupSurfaceType
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppPrimaryButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.continuityTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlinx.coroutines.launch

internal data class NearbyTransferPillState(
    val id: String,
    val peerName: String,
    val media: NearbyVideo,
    val stageLabel: String,
    val progress: Float,
    val outgoing: Boolean,
    val completed: Boolean = false,
    val onCancel: () -> Unit,
)

/**
 * 顶部非侵入式「附近设备正在播放」接力浮条：
 * - 自动适配 MD3、Miuix 与液态玻璃弹出表面；
 * - 支持向上轻滑关闭，松手未达阈值弹性回弹（全在 graphicsLayer 阶段读取，不触发逐帧重组）；
 * - 仅使用一次性入场/退场动画，无无限循环动画。
 */
@Composable
internal fun NearbyRemotePlaybackBanner(
    playback: NearbyRemotePlayback?,
    busy: Boolean,
    onPull: (NearbyPeer) -> Unit,
    onDismiss: (NearbyRemotePlayback) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val enterAnim = if (reduceMotion) fadeIn(continuityTween(0)) else {
        fadeIn(continuityTween(220)) + slideInVertically(continuityTween(260)) { -it / 2 }
    }
    val exitAnim = if (reduceMotion) fadeOut(continuityTween(0)) else {
        fadeOut(continuityTween(180)) + slideOutVertically(continuityTween(200)) { -it / 2 }
    }

    AnimatedVisibility(
        visible = playback != null,
        enter = enterAnim,
        exit = exitAnim,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val current = playback ?: return@AnimatedVisibility
        val media = current.video
        val density = LocalDensity.current
        val dismissThresholdPx = with(density) { 36.dp.toPx() }
        val dragOffsetY = remember(current.peer.pin, media.contentKey) { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val latestCurrent by rememberUpdatedState(current)
        val latestOnDismiss by rememberUpdatedState(onDismiss)

        LaunchedEffect(current.peer.pin, media.contentKey) {
            dragOffsetY.snapTo(0f)
        }

        val estimatedPosMs = estimateNearbyPlaybackPositionMs(media, System.currentTimeMillis())
        val prefSummary = formatNearbyPreferenceSummary(media, includeSpeed = true)
        val statusSubtitle = buildString {
            append("来自 ")
            append(current.peer.name)
            append(" · ")
            if (media.isLive) {
                if (media.uname.isNotBlank()) append("${media.uname} · ")
                append("直播中")
            } else {
                append(if (media.playing) "播放至 " else "暂停在 ")
                append(formatNearbyDuration(estimatedPosMs))
            }
            if (prefSummary.isNotBlank()) {
                append(" · ")
                append(prefSummary)
            }
        }

        AppPopupSurface(
            type = AppPopupSurfaceType.MENU,
            shape = AppShapes.container(ContainerLevel.Card),
            containerColor = AppSurfaceTokens.surfaceContainerHigh(),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .graphicsLayer {
                    val y = dragOffsetY.value
                    translationY = y
                    val dismissFraction = (-y / (dismissThresholdPx * 2.5f)).coerceIn(0f, 1f)
                    alpha = 1f - dismissFraction * 0.45f
                }
                .pointerInput(current.peer.pin, media.contentKey, reduceMotion) {
                    detectVerticalDragGestures(
                        onDragCancel = {
                            scope.launch {
                                if (reduceMotion) dragOffsetY.snapTo(0f)
                                else dragOffsetY.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        onDragEnd = {
                            if (dragOffsetY.value <= -dismissThresholdPx) {
                                latestOnDismiss(latestCurrent)
                            } else {
                                scope.launch {
                                    if (reduceMotion) dragOffsetY.snapTo(0f)
                                    else dragOffsetY.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val next = (dragOffsetY.value + dragAmount).coerceIn(-dismissThresholdPx * 2.5f, 0f)
                        scope.launch { dragOffsetY.snapTo(next) }
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppContentColors.secondaryText().copy(alpha = 0.28f)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppSurfaceTokens.surfaceContainerHighest()),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = media.cover,
                        animationSpec = continuityTween(if (reduceMotion) 0 else 200),
                        label = "nearbyBannerCover",
                    ) { coverUrl ->
                        if (coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                modifier = Modifier.size(width = 64.dp, height = 42.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(width = 64.dp, height = 42.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(
                                    imageVector = nearbyMediaIcon(media),
                                    contentDescription = null,
                                    tint = AppContentColors.secondaryText(),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        AppText(
                            text = media.mediaTypeLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppIcon(
                            imageVector = nearbyDeviceIcon(current.peer.formFactor),
                            contentDescription = null,
                            tint = AppContentColors.accent(),
                            modifier = Modifier.size(14.dp),
                        )
                        AppText(
                            text = statusSubtitle,
                            color = AppContentColors.accent(),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AppText(
                        text = media.title.ifBlank { media.mediaTypeLabel },
                        color = AppContentColors.primaryText(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                AppPrimaryButton(
                    text = "接力",
                    onClick = { onPull(current.peer) },
                    enabled = !busy,
                    leadingIcon = Icons.Outlined.PlayArrow,
                )

                AppIconButton(
                    onClick = { onDismiss(current) },
                    modifier = Modifier.size(32.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "暂不接力",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(18.dp),
                    )
                }
                }
            }
        }
    }
}

/**
 * 非阻塞顶部接力进度胶囊（发送等待确认 / 接收准备播放共用）：
 * - 替代遮挡视频画面的居中阻塞弹窗，与卡片飞出终点/飞入起点位置衔接；
 * - 使用确定性阶段进度条平滑推进，无无限循环动画消耗。
 */
@Composable
internal fun NearbyTransferStatusPill(
    transfer: NearbyTransferPillState?,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val enterAnim = if (reduceMotion) fadeIn(continuityTween(0)) else {
        fadeIn(continuityTween(180)) + slideInVertically(continuityTween(220)) { -it / 2 }
    }
    val exitAnim = if (reduceMotion) fadeOut(continuityTween(0)) else {
        fadeOut(continuityTween(160)) + slideOutVertically(continuityTween(180)) { -it / 2 }
    }

    AnimatedVisibility(
        visible = transfer != null,
        enter = enterAnim,
        exit = exitAnim,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val current = transfer ?: return@AnimatedVisibility
        val animatedProgress by animateFloatAsState(
            targetValue = current.progress.coerceIn(0f, 1f),
            animationSpec = continuityTween(if (reduceMotion) 0 else 280),
            label = "nearbyTransferProgress",
        )
        val header = when {
            current.completed && current.outgoing -> "已接力到 ${current.peerName}"
            current.completed -> "已接续播放"
            current.outgoing -> "正在接力到 ${current.peerName} · ${current.stageLabel}"
            else -> "正在从 ${current.peerName} 接力 · ${current.stageLabel}"
        }

        AppPopupSurface(
            type = AppPopupSurfaceType.MENU,
            shape = AppShapes.container(ContainerLevel.Card),
            containerColor = AppSurfaceTokens.surfaceContainerHigh(),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppSurfaceTokens.surfaceContainerHighest()),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (current.media.cover.isNotBlank()) {
                            AsyncImage(
                                model = current.media.cover,
                                contentDescription = null,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AppIcon(
                                imageVector = nearbyMediaIcon(current.media),
                                contentDescription = null,
                                tint = AppContentColors.secondaryText(),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AppText(
                            text = header,
                            color = AppContentColors.accent(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppText(
                            text = current.media.title.ifBlank { current.media.mediaTypeLabel },
                            color = AppContentColors.primaryText(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (current.completed) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(AppContentColors.accent().copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "接力完成",
                                tint = AppContentColors.accent(),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        AppTextButton(onClick = current.onCancel) {
                            AppText("取消")
                        }
                    }
                }

                AppLinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }
        }
    }
}

/**
 * 接力完成后的弹幕与画质偏好差异提示条：
 * - 非阻塞悬浮于顶部，支持向上轻滑或点关闭，不打断正常观影；
 * - 仅在存在真实差异时展示，支持一键同步弹幕开关或切换至对端画质（若本机当前内容支持）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NearbyPreferenceHintBanner(
    hint: NearbyPreferenceHint?,
    onSyncDanmaku: (NearbyPreferenceHint, Boolean) -> Unit,
    onSwitchQuality: (NearbyPreferenceHint, Int) -> Unit,
    onSwitchAudioQuality: (NearbyPreferenceHint, Int) -> Unit,
    onSwitchAudioLang: (NearbyPreferenceHint, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val enterAnim = if (reduceMotion) fadeIn(continuityTween(0)) else {
        fadeIn(continuityTween(200)) + slideInVertically(continuityTween(240)) { -it / 2 }
    }
    val exitAnim = if (reduceMotion) fadeOut(continuityTween(0)) else {
        fadeOut(continuityTween(160)) + slideOutVertically(continuityTween(180)) { -it / 2 }
    }

    AnimatedVisibility(
        visible = hint != null,
        enter = enterAnim,
        exit = exitAnim,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val current = hint ?: return@AnimatedVisibility
        val density = LocalDensity.current
        val dismissThresholdPx = with(density) { 32.dp.toPx() }
        val dragOffsetY = remember(current.id) { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val latestOnDismiss by rememberUpdatedState(onDismiss)

        LaunchedEffect(current.id) {
            dragOffsetY.snapTo(0f)
        }

        AppPopupSurface(
            type = AppPopupSurfaceType.MENU,
            shape = AppShapes.container(ContainerLevel.Card),
            containerColor = AppSurfaceTokens.surfaceContainerHigh(),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .graphicsLayer {
                    val y = dragOffsetY.value
                    translationY = y
                    val dismissFraction = (-y / (dismissThresholdPx * 2.5f)).coerceIn(0f, 1f)
                    alpha = 1f - dismissFraction * 0.45f
                }
                .pointerInput(current.id, reduceMotion) {
                    detectVerticalDragGestures(
                        onDragCancel = {
                            scope.launch {
                                if (reduceMotion) dragOffsetY.snapTo(0f)
                                else dragOffsetY.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        onDragEnd = {
                            if (dragOffsetY.value <= -dismissThresholdPx) {
                                latestOnDismiss()
                            } else {
                                scope.launch {
                                    if (reduceMotion) dragOffsetY.snapTo(0f)
                                    else dragOffsetY.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val next = (dragOffsetY.value + dragAmount).coerceIn(-dismissThresholdPx * 2.5f, 0f)
                        scope.launch { dragOffsetY.snapTo(next) }
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppContentColors.accent().copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = AppContentColors.accent(),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AppText(
                            text = current.headline,
                            color = AppContentColors.primaryText(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppText(
                            text = current.detail,
                            color = AppContentColors.secondaryText(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AppIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭偏好提示",
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val hasActions = current.targetQualityId != null ||
                    current.targetAudioQualityId != null ||
                    current.targetAudioLang != null ||
                    current.targetDanmakuEnabled != null
                if (hasActions) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        current.targetQualityId?.let { qn ->
                            val label = current.targetQualityLabel.ifBlank { "对端画质" }
                            AppTextButton(onClick = { onSwitchQuality(current, qn) }) {
                                AppText("切至 $label")
                            }
                        }
                        current.targetAudioQualityId?.let { audioId ->
                            val label = current.targetAudioQualityLabel.ifBlank { "对端音质" }
                            AppTextButton(onClick = { onSwitchAudioQuality(current, audioId) }) {
                                AppText("切至 $label")
                            }
                        }
                        current.targetAudioLang?.let { langCode ->
                            val label = current.targetAudioLangLabel.ifBlank { "对端音轨" }
                            AppTextButton(onClick = { onSwitchAudioLang(current, langCode) }) {
                                AppText("切至 $label")
                            }
                        }
                        current.targetDanmakuEnabled?.let { targetEnabled ->
                            AppTextButton(onClick = { onSyncDanmaku(current, targetEnabled) }) {
                                AppText(if (targetEnabled) "同步开弹幕" else "同步关弹幕")
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun nearbyDeviceIcon(formFactor: NearbyDeviceFormFactor): ImageVector = when (formFactor) {
    NearbyDeviceFormFactor.TV -> Icons.Outlined.Tv
    NearbyDeviceFormFactor.TABLET, NearbyDeviceFormFactor.PHONE -> Icons.Outlined.Devices
}

internal fun nearbyMediaIcon(media: NearbyVideo): ImageVector = when {
    media.isBangumi -> Icons.Outlined.Movie
    media.isLive -> Icons.Outlined.LiveTv
    media.audioOnly -> Icons.Outlined.Headphones
    else -> Icons.Outlined.SmartDisplay
}
