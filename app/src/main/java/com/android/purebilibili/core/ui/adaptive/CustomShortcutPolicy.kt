package com.android.purebilibili.core.ui.adaptive

import android.view.KeyEvent
import androidx.compose.runtime.compositionLocalOf

enum class ShortcutScope {
    App,
    Player,
}

data class CustomKeyBinding(
    val keyCode: Int,
    val modifiers: Int = 0,
)

enum class CustomShortcutSlot(
    val storageId: String,
    val scope: ShortcutScope,
    val defaultBinding: CustomKeyBinding,
) {
    AppOpenSearch("app_open_search", ShortcutScope.App, CustomKeyBinding(KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_ON)),
    AppOpenSettings("app_open_settings", ShortcutScope.App, CustomKeyBinding(KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON)),
    PlayerPlayPause("player_play_pause", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_SPACE)),
    PlayerTheaterMode("player_theater_mode", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_T)),
    PlayerFullscreen("player_fullscreen", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_F)),
    PlayerMute("player_mute", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_M)),
    PlayerDanmaku("player_danmaku", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_D)),
    PlayerLike("player_like", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_Q)),
    PlayerCoin("player_coin", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_W)),
    PlayerFavorite("player_favorite", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_E)),
    PlayerTriple("player_triple", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_R)),
    PlayerScreenshot("player_screenshot", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_S)),
    PlayerLock("player_lock", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_L)),
    PlayerPreviousPart("player_prev_part", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_LEFT_BRACKET)),
    PlayerNextPart("player_next_part", ShortcutScope.Player, CustomKeyBinding(KeyEvent.KEYCODE_RIGHT_BRACKET)),
    ;

    companion object {
        private val byId = entries.associateBy { it.storageId }
        fun fromStorageId(id: String): CustomShortcutSlot? = byId[id]
    }
}

sealed interface CustomKeyValidationResult {
    data object Valid : CustomKeyValidationResult
    data class Invalid(val reason: String) : CustomKeyValidationResult
}

private const val SUPPORTED_MODIFIER_MASK =
    KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON

private val MODIFIER_ONLY_KEY_CODES = setOf(
    KeyEvent.KEYCODE_SHIFT_LEFT,
    KeyEvent.KEYCODE_SHIFT_RIGHT,
    KeyEvent.KEYCODE_CTRL_LEFT,
    KeyEvent.KEYCODE_CTRL_RIGHT,
    KeyEvent.KEYCODE_ALT_LEFT,
    KeyEvent.KEYCODE_ALT_RIGHT,
    KeyEvent.KEYCODE_META_LEFT,
    KeyEvent.KEYCODE_META_RIGHT,
    KeyEvent.KEYCODE_CAPS_LOCK,
    KeyEvent.KEYCODE_NUM_LOCK,
    KeyEvent.KEYCODE_SCROLL_LOCK,
    KeyEvent.KEYCODE_FUNCTION,
)

private val RESERVED_SYSTEM_KEY_CODES = setOf(
    KeyEvent.KEYCODE_UNKNOWN,
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_APP_SWITCH,
    KeyEvent.KEYCODE_POWER,
    KeyEvent.KEYCODE_ESCAPE,
)

private val TEXT_EDITING_CTRL_KEYS = setOf(
    KeyEvent.KEYCODE_A,
    KeyEvent.KEYCODE_C,
    KeyEvent.KEYCODE_V,
    KeyEvent.KEYCODE_X,
    KeyEvent.KEYCODE_Z,
)

fun normalizeShortcutModifiers(metaState: Int): Int = metaState and SUPPORTED_MODIFIER_MASK

fun validateCustomKeyBinding(
    slot: CustomShortcutSlot,
    binding: CustomKeyBinding,
): CustomKeyValidationResult {
    if (binding.keyCode in MODIFIER_ONLY_KEY_CODES) {
        return CustomKeyValidationResult.Invalid("请按下具体按键，不能仅按修饰键")
    }
    if (binding.keyCode in RESERVED_SYSTEM_KEY_CODES) {
        return CustomKeyValidationResult.Invalid("该按键为系统保留键，无法绑定")
    }
    val modifiers = normalizeShortcutModifiers(binding.modifiers)
    return when (slot.scope) {
        ShortcutScope.App -> {
            if (modifiers != KeyEvent.META_CTRL_ON) {
                CustomKeyValidationResult.Invalid("应用级快捷键需使用 Ctrl 组合键")
            } else if (binding.keyCode in TEXT_EDITING_CTRL_KEYS) {
                CustomKeyValidationResult.Invalid("该组合键为文本编辑默认快捷键，无法占用")
            } else {
                CustomKeyValidationResult.Valid
            }
        }
        ShortcutScope.Player -> {
            if (modifiers != 0 && modifiers != KeyEvent.META_SHIFT_ON) {
                CustomKeyValidationResult.Invalid("播放器快捷键仅支持单键或 Shift 组合")
            } else {
                CustomKeyValidationResult.Valid
            }
        }
    }
}

data class CustomShortcutOverrides(
    val bindings: Map<CustomShortcutSlot, CustomKeyBinding> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = bindings.isEmpty()

    fun bindingFor(slot: CustomShortcutSlot): CustomKeyBinding =
        bindings[slot] ?: slot.defaultBinding

    fun isCustomized(slot: CustomShortcutSlot): Boolean = slot in bindings

    fun findConflict(
        slot: CustomShortcutSlot,
        binding: CustomKeyBinding,
    ): CustomShortcutSlot? {
        val normalized = binding.copy(modifiers = normalizeShortcutModifiers(binding.modifiers))
        return CustomShortcutSlot.entries.firstOrNull { candidate ->
            candidate != slot &&
                candidate.scope == slot.scope &&
                bindingFor(candidate) == normalized
        }
    }

    fun withBinding(
        slot: CustomShortcutSlot,
        binding: CustomKeyBinding,
    ): CustomShortcutOverrides {
        val normalized = binding.copy(modifiers = normalizeShortcutModifiers(binding.modifiers))
        val updated = bindings.toMutableMap()
        // 解除同作用域内占用同一按键的冲突槽位：若冲突槽原本是自定义，恢复其默认；若冲突槽的默认键被占用，则将其设为无绑定前的互换或保留显式覆写。
        val conflicting = findConflict(slot, normalized)
        if (conflicting != null) {
            if (conflicting in updated) {
                updated.remove(conflicting)
            } else {
                // 冲突槽使用的是默认键：将冲突槽与当前槽原键位互换，确保两个功能均可达且不冲突。
                updated[conflicting] = bindingFor(slot)
            }
        }
        if (normalized == slot.defaultBinding) {
            updated.remove(slot)
        } else {
            updated[slot] = normalized
        }
        return CustomShortcutOverrides(updated)
    }

    fun resetSlot(slot: CustomShortcutSlot): CustomShortcutOverrides =
        if (slot in bindings) CustomShortcutOverrides(bindings - slot) else this
}

val LocalKeyboardShortcutOverrides = compositionLocalOf { CustomShortcutOverrides() }

fun encodeCustomShortcutOverrides(overrides: CustomShortcutOverrides): String {
    if (overrides.bindings.isEmpty()) return ""
    return overrides.bindings.entries
        .sortedBy { it.key.ordinal }
        .joinToString(separator = ";") { (slot, binding) ->
            "${slot.storageId}=${binding.keyCode}:${normalizeShortcutModifiers(binding.modifiers)}"
        }
}

fun decodeCustomShortcutOverrides(raw: String?): CustomShortcutOverrides {
    if (raw.isNullOrBlank()) return CustomShortcutOverrides()
    val decoded = linkedMapOf<CustomShortcutSlot, CustomKeyBinding>()
    raw.split(';').forEach { token ->
        val parts = token.split('=')
        if (parts.size != 2) return@forEach
        val slot = CustomShortcutSlot.fromStorageId(parts[0].trim()) ?: return@forEach
        val keyParts = parts[1].split(':')
        if (keyParts.size != 2) return@forEach
        val keyCode = keyParts[0].toIntOrNull() ?: return@forEach
        val modifiers = normalizeShortcutModifiers(keyParts[1].toIntOrNull() ?: return@forEach)
        val binding = CustomKeyBinding(keyCode = keyCode, modifiers = modifiers)
        if (validateCustomKeyBinding(slot, binding) == CustomKeyValidationResult.Valid && binding != slot.defaultBinding) {
            decoded[slot] = binding
        }
    }
    return CustomShortcutOverrides(decoded)
}

const val CUSTOM_SHORTCUT_EXPORT_PREFIX = "bilipai-shortcuts:v1:"

fun exportCustomShortcutConfigText(overrides: CustomShortcutOverrides): String =
    CUSTOM_SHORTCUT_EXPORT_PREFIX + encodeCustomShortcutOverrides(overrides)

fun importCustomShortcutConfigText(raw: String?): CustomShortcutOverrides? {
    val trimmed = raw?.trim() ?: return null
    if (!trimmed.startsWith(CUSTOM_SHORTCUT_EXPORT_PREFIX)) return null
    val payload = trimmed.removePrefix(CUSTOM_SHORTCUT_EXPORT_PREFIX)
    return decodeCustomShortcutOverrides(payload)
}

fun formatCustomKeyBinding(binding: CustomKeyBinding): String {
    val modifiers = normalizeShortcutModifiers(binding.modifiers)
    val parts = ArrayList<String>(3)
    if ((modifiers and KeyEvent.META_CTRL_ON) != 0) parts += "Ctrl"
    if ((modifiers and KeyEvent.META_ALT_ON) != 0) parts += "Alt"
    if ((modifiers and KeyEvent.META_SHIFT_ON) != 0) parts += "Shift"
    if ((modifiers and KeyEvent.META_META_ON) != 0) parts += "Meta"
    parts += formatKeyCodeLabel(binding.keyCode)
    return parts.joinToString("+")
}

private fun formatKeyCodeLabel(keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_SPACE -> "Space"
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "Enter"
    KeyEvent.KEYCODE_TAB -> "Tab"
    KeyEvent.KEYCODE_DEL -> "Backspace"
    KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
    KeyEvent.KEYCODE_DPAD_LEFT -> "←"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
    KeyEvent.KEYCODE_DPAD_UP -> "↑"
    KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
    KeyEvent.KEYCODE_COMMA -> ","
    KeyEvent.KEYCODE_PERIOD -> "."
    KeyEvent.KEYCODE_SLASH -> "/"
    KeyEvent.KEYCODE_BACKSLASH -> "\\"
    KeyEvent.KEYCODE_SEMICOLON -> ";"
    KeyEvent.KEYCODE_APOSTROPHE -> "'"
    KeyEvent.KEYCODE_LEFT_BRACKET -> "["
    KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
    KeyEvent.KEYCODE_MINUS -> "-"
    KeyEvent.KEYCODE_EQUALS -> "="
    KeyEvent.KEYCODE_GRAVE -> "`"
    in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (keyCode - KeyEvent.KEYCODE_0)).toString()
    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> "Num${keyCode - KeyEvent.KEYCODE_NUMPAD_0}"
    in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "F${keyCode - KeyEvent.KEYCODE_F1 + 1}"
    else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
}
