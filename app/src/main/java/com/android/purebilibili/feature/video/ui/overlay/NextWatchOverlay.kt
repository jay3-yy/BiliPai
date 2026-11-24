package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.AppContentColors

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion
import com.android.purebilibili.feature.video.playback.next.isNextWatchWindow
import com.android.purebilibili.feature.video.playback.next.nextWatchRemainingSeconds

/**
 * 计算「接下来观看」浮层的下一次唤醒间隔：
 * - 暂停或时长未知时返回 null（完全挂起等待 Player.Listener 事件，零轮询）；
 * - 距离片尾窗口较远时按当前倍速一次休眠到窗口前（上限 60s），避免全程高频空转；
 * - 进入片尾最后 6.5 秒后按 250ms 精度刷新倒计时。
 */
internal fun resolveNextWatchPollDelayMs(
    durationMs: Long,
    positionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float = 1f,
): Long? {
    if (!isPlaying || durationMs <= 0L) return null
    val remainingMs = (durationMs - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    if (remainingMs <= 6_500L) return 250L
    val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.25f, 4f) ?: 1f
    val untilWindowMs = ((remainingMs - 6_000L) / speed).toLong()
    return untilWindowMs.coerceIn(500L, 60_000L)
}

/** Event-driven while paused or far from end; polls at 250ms only inside the final 6.5s window. */
@Composable
internal fun BoxScope.NextWatchOverlay(
    player: Player,
    mediaKey: String,
    eligible: Boolean,
    resolve: () -> NextWatchSuggestion?,
    play: (NextWatchSuggestion) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hintEnabled by remember(context) { SettingsManager.getNextWatchHintEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    if (!hintEnabled) return
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val visualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val latestResolve by rememberUpdatedState(resolve)
    val latestPlay by rememberUpdatedState(play)
    var suggestion by remember(mediaKey) { mutableStateOf<NextWatchSuggestion?>(null) }
    var dismissed by remember(mediaKey) { mutableStateOf(false) }
    var visible by remember(mediaKey) { mutableStateOf(false) }
    var remainingSeconds by remember(mediaKey) { mutableIntStateOf(5) }
    LaunchedEffect(player, mediaKey, eligible, visualWorkActive, lifecycle, activity) {
        if (!eligible || !visualWorkActive) {
            visible = false
            return@LaunchedEffect
        }
        val events = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, playerEvents: Player.Events) {
                events.trySend(Unit)
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                val duration = player.duration
                val position = player.currentPosition
                val isPlaying = player.isPlaying
                val seconds = nextWatchRemainingSeconds(duration, position)
                val inWindow = eligible && !dismissed && activity?.isInPictureInPictureMode != true &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    isPlaying && seconds != null
                if (inWindow && seconds != null) remainingSeconds = seconds
                if (suggestion != null && !isNextWatchWindow(duration, position)) {
                    dismissed = true
                }
                if (inWindow && suggestion == null) suggestion = latestResolve()
                if (inWindow && suggestion != null && latestResolve() != suggestion) {
                    // Queue, mode or exclusions changed: retire this offer instead of swapping its card.
                    dismissed = true
                }
                visible = inWindow && !dismissed && suggestion != null
                val sleepMs = resolveNextWatchPollDelayMs(
                    durationMs = duration,
                    positionMs = position,
                    isPlaying = isPlaying,
                    playbackSpeed = player.playbackParameters.speed,
                )
                if (sleepMs == null) {
                    events.receive()
                } else {
                    kotlinx.coroutines.withTimeoutOrNull(sleepMs) { events.receive() }
                }
            }
        } finally {
            player.removeListener(listener)
            events.close()
        }
    }
    val target = suggestion
    if (target != null) {
        BoxWithConstraints(modifier = modifier.fillMaxSize().zIndex(119f)) {
            val compact = maxHeight < 280.dp
            val inset = if (compact) 8.dp else 20.dp
            val cardWidth = minOf((maxWidth - inset * 2).coerceAtLeast(0.dp), if (compact) 236.dp else 300.dp)
            // Upper corner avoids the subtitle band and lower transport controls.
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(if (reduceMotion) 0 else 120)),
                exit = fadeOut(tween(if (reduceMotion) 0 else 100)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(inset),
            ) {
                NextWatchCard(
                    suggestion = target,
                    remainingSeconds = remainingSeconds,
                    onClick = {
                        latestPlay(target)
                        // A stale or already-used click must not retry against another video.
                        dismissed = true
                        visible = false
                    },
                    onDismiss = { dismissed = true; visible = false },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@Composable
private fun NextWatchCard(
    suggestion: NextWatchSuggestion,
    remainingSeconds: Int,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val miuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val shape = if (miuix) AppShapes.container(ContainerLevel.Card) else androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val container = if (miuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (miuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
        else AppContentColors.primaryText()
    AppSurface(modifier = modifier, shape = shape, color = container, tonalElevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(model = suggestion.cover, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp, 36.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(text = suggestion.label, color = content,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, tapToCopyEnabled = false)
                    AppText(text = suggestion.title, color = content,
                        style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, tapToCopyEnabled = false)
                    AppText(text = "剩余 $remainingSeconds 秒", color = content.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        tapToCopyEnabled = false)
                }
            }
            Box(modifier = Modifier.size(48.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                AppIcon(imageVector = Icons.Filled.Close, contentDescription = "关闭接下来观看提示", tint = content, modifier = Modifier.size(18.dp))
            }
        }
    }
}
