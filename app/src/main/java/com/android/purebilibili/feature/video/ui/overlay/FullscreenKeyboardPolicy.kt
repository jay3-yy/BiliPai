package com.android.purebilibili.feature.video.ui.overlay

import android.view.KeyEvent
import com.android.purebilibili.core.ui.adaptive.CustomKeyBinding
import com.android.purebilibili.core.ui.adaptive.CustomShortcutOverrides
import com.android.purebilibili.core.ui.adaptive.CustomShortcutSlot
import com.android.purebilibili.core.ui.adaptive.normalizeShortcutModifiers

internal enum class FullscreenShortcutKey {
    Space,
    Left,
    Right,
    Escape,
    KeyF,
    KeyM,
    KeyD,
    KeyL,
    Other,
}

internal enum class FullscreenKeyboardAction {
    PlayPause,
    SeekBackward,
    SeekForward,
    CloseTopLayer,
    ToggleFullscreen,
    ToggleMute,
    ToggleDanmaku,
    ToggleLock,
    None,
}

internal fun resolveFullscreenKeyboardAction(
    key: FullscreenShortcutKey,
    isKeyDown: Boolean,
    hasCommandModifier: Boolean,
    shortcutsEnabled: Boolean,
): FullscreenKeyboardAction {
    if (!isKeyDown || hasCommandModifier || !shortcutsEnabled) {
        return FullscreenKeyboardAction.None
    }
    return when (key) {
        FullscreenShortcutKey.Space -> FullscreenKeyboardAction.PlayPause
        FullscreenShortcutKey.Left -> FullscreenKeyboardAction.SeekBackward
        FullscreenShortcutKey.Right -> FullscreenKeyboardAction.SeekForward
        FullscreenShortcutKey.Escape -> FullscreenKeyboardAction.CloseTopLayer
        FullscreenShortcutKey.KeyF -> FullscreenKeyboardAction.ToggleFullscreen
        FullscreenShortcutKey.KeyM -> FullscreenKeyboardAction.ToggleMute
        FullscreenShortcutKey.KeyD -> FullscreenKeyboardAction.ToggleDanmaku
        FullscreenShortcutKey.KeyL -> FullscreenKeyboardAction.ToggleLock
        FullscreenShortcutKey.Other -> FullscreenKeyboardAction.None
    }
}

/** 结合自定义快捷键覆写解析全屏覆层按键，支持单键与 Shift 组合自定义。 */
internal fun resolveFullscreenShortcutKey(
    keyCode: Int,
    modifiers: Int,
    overrides: CustomShortcutOverrides = CustomShortcutOverrides(),
): FullscreenShortcutKey {
    val normalized = normalizeShortcutModifiers(modifiers)
    if (
        (normalized and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON)) != 0
    ) {
        return FullscreenShortcutKey.Other
    }
    val pressed = CustomKeyBinding(keyCode = keyCode, modifiers = normalized)
    return when {
        pressed == overrides.bindingFor(CustomShortcutSlot.PlayerPlayPause) -> FullscreenShortcutKey.Space
        pressed == overrides.bindingFor(CustomShortcutSlot.PlayerFullscreen) -> FullscreenShortcutKey.KeyF
        pressed == overrides.bindingFor(CustomShortcutSlot.PlayerMute) -> FullscreenShortcutKey.KeyM
        pressed == overrides.bindingFor(CustomShortcutSlot.PlayerDanmaku) -> FullscreenShortcutKey.KeyD
        pressed == overrides.bindingFor(CustomShortcutSlot.PlayerLock) -> FullscreenShortcutKey.KeyL
        normalized != 0 -> FullscreenShortcutKey.Other
        keyCode == KeyEvent.KEYCODE_ESCAPE -> FullscreenShortcutKey.Escape
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> FullscreenShortcutKey.Left
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> FullscreenShortcutKey.Right
        keyCode == KeyEvent.KEYCODE_K && !overrides.isCustomized(CustomShortcutSlot.PlayerPlayPause) ->
            FullscreenShortcutKey.Space
        (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
            !overrides.isCustomized(CustomShortcutSlot.PlayerFullscreen) ->
            FullscreenShortcutKey.KeyF
        else -> FullscreenShortcutKey.Other
    }
}
