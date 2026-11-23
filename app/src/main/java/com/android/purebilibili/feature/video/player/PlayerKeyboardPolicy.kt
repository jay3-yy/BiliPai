package com.android.purebilibili.feature.video.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import com.android.purebilibili.core.ui.adaptive.CustomKeyBinding
import com.android.purebilibili.core.ui.adaptive.CustomShortcutOverrides
import com.android.purebilibili.core.ui.adaptive.CustomShortcutSlot
import com.android.purebilibili.core.ui.adaptive.LocalKeyboardShortcutOverrides
import com.android.purebilibili.core.ui.adaptive.normalizeShortcutModifiers

sealed interface PlayerKeyAction {
    data object PlayPause : PlayerKeyAction
    data class SeekRelative(val deltaMs: Long) : PlayerKeyAction
    data class SeekPercent(val fraction: Float) : PlayerKeyAction
    data object VolumeUp : PlayerKeyAction
    data object VolumeDown : PlayerKeyAction
    data object ToggleMute : PlayerKeyAction
    data object ToggleTheaterMode : PlayerKeyAction
    data object ToggleFullscreen : PlayerKeyAction
    data object ToggleDanmaku : PlayerKeyAction
    data object ToggleLike : PlayerKeyAction
    data object Coin : PlayerKeyAction
    data object ToggleFavorite : PlayerKeyAction
    data object TripleAction : PlayerKeyAction
    data object TakeScreenshot : PlayerKeyAction
    data object ToggleScreenLock : PlayerKeyAction
    data object PreviousPart : PlayerKeyAction
    data object NextPart : PlayerKeyAction
    data class SetSpeed(val speed: Float) : PlayerKeyAction
}

internal const val KEYBOARD_SEEK_SHORT_STEP_MS = 5_000L
internal const val KEYBOARD_SEEK_LONG_STEP_MS = 10_000L

private fun CustomShortcutSlot.toPlayerKeyAction(): PlayerKeyAction? = when (this) {
    CustomShortcutSlot.PlayerPlayPause -> PlayerKeyAction.PlayPause
    CustomShortcutSlot.PlayerTheaterMode -> PlayerKeyAction.ToggleTheaterMode
    CustomShortcutSlot.PlayerFullscreen -> PlayerKeyAction.ToggleFullscreen
    CustomShortcutSlot.PlayerMute -> PlayerKeyAction.ToggleMute
    CustomShortcutSlot.PlayerDanmaku -> PlayerKeyAction.ToggleDanmaku
    CustomShortcutSlot.PlayerLike -> PlayerKeyAction.ToggleLike
    CustomShortcutSlot.PlayerCoin -> PlayerKeyAction.Coin
    CustomShortcutSlot.PlayerFavorite -> PlayerKeyAction.ToggleFavorite
    CustomShortcutSlot.PlayerTriple -> PlayerKeyAction.TripleAction
    CustomShortcutSlot.PlayerScreenshot -> PlayerKeyAction.TakeScreenshot
    CustomShortcutSlot.PlayerLock -> PlayerKeyAction.ToggleScreenLock
    CustomShortcutSlot.PlayerPreviousPart -> PlayerKeyAction.PreviousPart
    CustomShortcutSlot.PlayerNextPart -> PlayerKeyAction.NextPart
    else -> null
}

internal fun resolvePlayerKeyAction(
    event: KeyEvent,
    isScreenLocked: Boolean = false,
    isInPipMode: Boolean = false,
    isTextInputActive: Boolean = false,
    overrides: CustomShortcutOverrides = CustomShortcutOverrides(),
): PlayerKeyAction? {
    if (event.type != KeyEventType.KeyDown) return null
    if (isInPipMode || isTextInputActive) return null

    // Do not intercept system-level shortcuts (Ctrl, Alt, Meta/Cmd)
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
        return null
    }

    val nativeEvent = event.nativeKeyEvent
    val pressed = CustomKeyBinding(
        keyCode = nativeEvent.keyCode,
        modifiers = normalizeShortcutModifiers(nativeEvent.metaState),
    )

    // Screen locked: only the configured lock/unlock key is accepted
    if (isScreenLocked) {
        return if (pressed == overrides.bindingFor(CustomShortcutSlot.PlayerLock)) {
            PlayerKeyAction.ToggleScreenLock
        } else {
            null
        }
    }

    // 优先匹配所有可自定义播放器键位槽（含默认与用户自定义覆写）
    for (slot in CustomShortcutSlot.entries) {
        val action = slot.toPlayerKeyAction() ?: continue
        if (pressed == overrides.bindingFor(slot)) {
            return action
        }
    }

    val shift = event.isShiftPressed

    if (shift) {
        return when (event.key) {
            Key.DirectionLeft -> PlayerKeyAction.SeekRelative(-KEYBOARD_SEEK_LONG_STEP_MS)
            Key.DirectionRight -> PlayerKeyAction.SeekRelative(KEYBOARD_SEEK_LONG_STEP_MS)
            Key.One, Key.NumPad1 -> PlayerKeyAction.SetSpeed(1.0f)
            Key.Two, Key.NumPad2 -> PlayerKeyAction.SetSpeed(2.0f)
            else -> null
        }
    }

    return when (event.key) {
        Key.K -> if (!overrides.isCustomized(CustomShortcutSlot.PlayerPlayPause)) PlayerKeyAction.PlayPause else null
        Key.Enter, Key.NumPadEnter ->
            if (!overrides.isCustomized(CustomShortcutSlot.PlayerFullscreen)) PlayerKeyAction.ToggleFullscreen else null
        Key.DirectionLeft -> PlayerKeyAction.SeekRelative(-KEYBOARD_SEEK_SHORT_STEP_MS)
        Key.DirectionRight -> PlayerKeyAction.SeekRelative(KEYBOARD_SEEK_SHORT_STEP_MS)
        Key.J -> PlayerKeyAction.SeekRelative(-KEYBOARD_SEEK_LONG_STEP_MS)
        Key.DirectionUp -> PlayerKeyAction.VolumeUp
        Key.DirectionDown -> PlayerKeyAction.VolumeDown
        Key.Zero, Key.NumPad0 -> PlayerKeyAction.SeekPercent(0.0f)
        Key.One, Key.NumPad1 -> PlayerKeyAction.SeekPercent(0.1f)
        Key.Two, Key.NumPad2 -> PlayerKeyAction.SeekPercent(0.2f)
        Key.Three, Key.NumPad3 -> PlayerKeyAction.SeekPercent(0.3f)
        Key.Four, Key.NumPad4 -> PlayerKeyAction.SeekPercent(0.4f)
        Key.Five, Key.NumPad5 -> PlayerKeyAction.SeekPercent(0.5f)
        Key.Six, Key.NumPad6 -> PlayerKeyAction.SeekPercent(0.6f)
        Key.Seven, Key.NumPad7 -> PlayerKeyAction.SeekPercent(0.7f)
        Key.Eight, Key.NumPad8 -> PlayerKeyAction.SeekPercent(0.8f)
        Key.Nine, Key.NumPad9 -> PlayerKeyAction.SeekPercent(0.9f)
        else -> null
    }
}

internal fun calculateSeekTargetPositionMs(
    currentPositionMs: Long,
    durationMs: Long,
    action: PlayerKeyAction,
): Long? {
    if (durationMs <= 0L) return null
    val current = currentPositionMs.coerceAtLeast(0L)
    return when (action) {
        is PlayerKeyAction.SeekRelative -> {
            (current + action.deltaMs).coerceIn(0L, durationMs)
        }
        is PlayerKeyAction.SeekPercent -> {
            (durationMs * action.fraction).toLong().coerceIn(0L, durationMs)
        }
        else -> null
    }
}

/**
 * 为番剧、离线等独立播放页面提供统一的硬件键盘快捷键支持（空格播放/暂停、
 * 方向键快进退/音量、M 静音、F/Enter 全屏、D 弹幕、Shift+1/2 倍速、0-9 进度跳转）。
 */
@Composable
internal fun Modifier.basicPlayerKeyboardShortcuts(
    player: Player?,
    isInPipMode: Boolean = false,
    isTextInputActive: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    onToggleDanmaku: (() -> Unit)? = null,
): Modifier {
    val context = LocalContext.current
    var volumeBeforeMute by remember { mutableFloatStateOf(1f) }
    val currentPlayer by rememberUpdatedState(player)
    val currentInPip by rememberUpdatedState(isInPipMode)
    val currentTextInputActive by rememberUpdatedState(isTextInputActive)
    val currentToggleFullscreen by rememberUpdatedState(onToggleFullscreen)
    val currentToggleDanmaku by rememberUpdatedState(onToggleDanmaku)
    val currentOverrides by rememberUpdatedState(LocalKeyboardShortcutOverrides.current)

    return this
        .focusable()
        .onKeyEvent { event ->
            val targetPlayer = currentPlayer ?: return@onKeyEvent false
            val action = resolvePlayerKeyAction(
                event = event,
                isInPipMode = currentInPip,
                isTextInputActive = currentTextInputActive,
                overrides = currentOverrides,
            ) ?: return@onKeyEvent false

            when (action) {
                PlayerKeyAction.PlayPause -> {
                    if (targetPlayer.isPlaying) targetPlayer.pause() else targetPlayer.play()
                    true
                }
                is PlayerKeyAction.SeekRelative,
                is PlayerKeyAction.SeekPercent -> {
                    val targetPosition = calculateSeekTargetPositionMs(
                        currentPositionMs = targetPlayer.currentPosition,
                        durationMs = targetPlayer.duration,
                        action = action,
                    ) ?: return@onKeyEvent false
                    targetPlayer.seekTo(targetPosition)
                    true
                }
                PlayerKeyAction.VolumeUp -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI,
                    )
                    true
                }
                PlayerKeyAction.VolumeDown -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI,
                    )
                    true
                }
                PlayerKeyAction.ToggleMute -> {
                    if (targetPlayer.volume > 0f) {
                        volumeBeforeMute = targetPlayer.volume
                        targetPlayer.volume = 0f
                    } else {
                        targetPlayer.volume = volumeBeforeMute.coerceAtLeast(0.5f)
                    }
                    true
                }
                PlayerKeyAction.ToggleFullscreen -> {
                    currentToggleFullscreen?.invoke() != null
                }
                PlayerKeyAction.ToggleDanmaku -> {
                    currentToggleDanmaku?.invoke() != null
                }
                is PlayerKeyAction.SetSpeed -> {
                    targetPlayer.setPlaybackSpeed(action.speed)
                    true
                }
                else -> false
            }
        }
}
