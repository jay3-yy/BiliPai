package com.android.purebilibili.core.ui.adaptive

import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.annotation.StringRes
import com.android.purebilibili.R

/**
 * 全局键盘快捷键解析出的应用级动作。
 *
 * 只声明 Ctrl 组合键，避免与官方默认快捷键（TextField 的 Ctrl+A/C/X/V/Z、
 * LazyColumn 的 PageUp/PageDown）以及文本输入冲突：普通字符键由聚焦的
 * TextField 自行消费，根本不会进入本策略的 Ctrl 分支。
 */
sealed interface AppShortcutAction {
    data object OpenSearch : AppShortcutAction
    data object OpenSettings : AppShortcutAction

    /** 按当前底栏可见顺序切换到第 [index] 项（0 起始），复用底栏点击语义。 */
    data class SelectBottomNavItem(val index: Int) : AppShortcutAction
}

/**
 * 快捷键对照表条目：设置帮助页与系统快捷键助手（Keyboard Shortcuts Helper）
 * 共用同一数据源，保证展示、上报与解析三者一致。
 */
data class AppShortcutDefinition(
    val action: AppShortcutAction,
    /** 展示用按键组合，如 "Ctrl+K / Ctrl+F"。 */
    val keysLabel: String,
    /** 系统快捷键助手上报用单个字符。 */
    val helperCharacter: Char,
    /** 系统快捷键助手上报用修饰键位。 */
    val helperMetaState: Int,
)

val APP_SHORTCUT_DEFINITIONS: List<AppShortcutDefinition> = listOf(
    AppShortcutDefinition(
        action = AppShortcutAction.OpenSearch,
        keysLabel = "Ctrl+K / Ctrl+F",
        helperCharacter = 'K',
        helperMetaState = KeyEvent.META_CTRL_ON,
    ),
    AppShortcutDefinition(
        action = AppShortcutAction.OpenSettings,
        keysLabel = "Ctrl+,",
        helperCharacter = ',',
        helperMetaState = KeyEvent.META_CTRL_ON,
    ),
    AppShortcutDefinition(
        action = AppShortcutAction.SelectBottomNavItem(0),
        keysLabel = "Ctrl+1 – Ctrl+9",
        helperCharacter = '1',
        helperMetaState = KeyEvent.META_CTRL_ON,
    ),
)

/**
 * 解析 Activity 级全局快捷键。仅接受无重复的首个 ACTION_DOWN，且必须是
 * 纯 Ctrl 组合：Alt/Meta 交给系统，Shift 修饰不属于本表，普通按键放行。
 */
fun resolveAppShortcutAction(
    event: KeyEvent,
    overrides: CustomShortcutOverrides = CustomShortcutOverrides(),
): AppShortcutAction? {
    if (event.action != KeyEvent.ACTION_DOWN) return null
    if (event.repeatCount != 0) return null
    val modifiers = normalizeShortcutModifiers(event.metaState)
    if (modifiers != KeyEvent.META_CTRL_ON) {
        return null
    }
    val pressed = CustomKeyBinding(keyCode = event.keyCode, modifiers = modifiers)
    if (pressed == overrides.bindingFor(CustomShortcutSlot.AppOpenSearch)) {
        return AppShortcutAction.OpenSearch
    }
    if (pressed == overrides.bindingFor(CustomShortcutSlot.AppOpenSettings)) {
        return AppShortcutAction.OpenSettings
    }
    // 未自定义打开搜索时保留 Ctrl+F 兼容别名
    if (!overrides.isCustomized(CustomShortcutSlot.AppOpenSearch) && event.keyCode == KeyEvent.KEYCODE_F) {
        return AppShortcutAction.OpenSearch
    }
    return when (event.keyCode) {
        KeyEvent.KEYCODE_1 -> AppShortcutAction.SelectBottomNavItem(0)
        KeyEvent.KEYCODE_2 -> AppShortcutAction.SelectBottomNavItem(1)
        KeyEvent.KEYCODE_3 -> AppShortcutAction.SelectBottomNavItem(2)
        KeyEvent.KEYCODE_4 -> AppShortcutAction.SelectBottomNavItem(3)
        KeyEvent.KEYCODE_5 -> AppShortcutAction.SelectBottomNavItem(4)
        KeyEvent.KEYCODE_6 -> AppShortcutAction.SelectBottomNavItem(5)
        KeyEvent.KEYCODE_7 -> AppShortcutAction.SelectBottomNavItem(6)
        KeyEvent.KEYCODE_8 -> AppShortcutAction.SelectBottomNavItem(7)
        KeyEvent.KEYCODE_9 -> AppShortcutAction.SelectBottomNavItem(8)
        KeyEvent.KEYCODE_NUMPAD_1 -> AppShortcutAction.SelectBottomNavItem(0)
        KeyEvent.KEYCODE_NUMPAD_2 -> AppShortcutAction.SelectBottomNavItem(1)
        KeyEvent.KEYCODE_NUMPAD_3 -> AppShortcutAction.SelectBottomNavItem(2)
        KeyEvent.KEYCODE_NUMPAD_4 -> AppShortcutAction.SelectBottomNavItem(3)
        KeyEvent.KEYCODE_NUMPAD_5 -> AppShortcutAction.SelectBottomNavItem(4)
        KeyEvent.KEYCODE_NUMPAD_6 -> AppShortcutAction.SelectBottomNavItem(5)
        KeyEvent.KEYCODE_NUMPAD_7 -> AppShortcutAction.SelectBottomNavItem(6)
        KeyEvent.KEYCODE_NUMPAD_8 -> AppShortcutAction.SelectBottomNavItem(7)
        KeyEvent.KEYCODE_NUMPAD_9 -> AppShortcutAction.SelectBottomNavItem(8)
        else -> null
    }
}

/** 供系统快捷键助手（Keyboard Shortcuts Helper）展示的播放器快捷键定义。 */
data class PlayerShortcutHelperDefinition(
    @StringRes val labelRes: Int,
    val keyCode: Int,
    val modifiers: Int = 0,
)

val PLAYER_SHORTCUT_HELPER_DEFINITIONS: List<PlayerShortcutHelperDefinition> = listOf(
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_play_pause, KeyEvent.KEYCODE_SPACE),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_seek_short, KeyEvent.KEYCODE_DPAD_RIGHT),
    PlayerShortcutHelperDefinition(
        R.string.keyboard_shortcut_player_seek_long,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.META_SHIFT_ON,
    ),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_volume, KeyEvent.KEYCODE_DPAD_UP),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_fullscreen, KeyEvent.KEYCODE_F),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_mute, KeyEvent.KEYCODE_M),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_danmaku, KeyEvent.KEYCODE_D),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_like_coin_fav, KeyEvent.KEYCODE_Q),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_screenshot, KeyEvent.KEYCODE_S),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_lock, KeyEvent.KEYCODE_L),
    PlayerShortcutHelperDefinition(R.string.keyboard_shortcut_player_parts, KeyEvent.KEYCODE_RIGHT_BRACKET),
    PlayerShortcutHelperDefinition(
        R.string.keyboard_shortcut_player_speed,
        KeyEvent.KEYCODE_2,
        KeyEvent.META_SHIFT_ON,
    ),
)

/** 构建上报给系统快捷键助手的应用级与播放器级分组，供 MainActivity 与 VideoActivity 共用。 */
fun buildAppKeyboardShortcutGroups(
    context: Context,
    overrides: CustomShortcutOverrides = CustomShortcutOverrides(),
): List<KeyboardShortcutGroup> {
    val searchBinding = overrides.bindingFor(CustomShortcutSlot.AppOpenSearch)
    val settingsBinding = overrides.bindingFor(CustomShortcutSlot.AppOpenSettings)
    val appShortcuts = listOf(
        KeyboardShortcutInfo(
            context.getString(R.string.keyboard_shortcut_open_search),
            searchBinding.keyCode,
            searchBinding.modifiers,
        ),
        KeyboardShortcutInfo(
            context.getString(R.string.keyboard_shortcut_open_settings),
            settingsBinding.keyCode,
            settingsBinding.modifiers,
        ),
        KeyboardShortcutInfo(
            context.getString(R.string.keyboard_shortcut_select_bottom_nav),
            KeyEvent.KEYCODE_1,
            KeyEvent.META_CTRL_ON,
        ),
    )
    val playerShortcuts = PLAYER_SHORTCUT_HELPER_DEFINITIONS.map { definition ->
        val slot = when (definition.labelRes) {
            R.string.keyboard_shortcut_player_play_pause -> CustomShortcutSlot.PlayerPlayPause
            R.string.keyboard_shortcut_player_fullscreen -> CustomShortcutSlot.PlayerFullscreen
            R.string.keyboard_shortcut_player_mute -> CustomShortcutSlot.PlayerMute
            R.string.keyboard_shortcut_player_danmaku -> CustomShortcutSlot.PlayerDanmaku
            R.string.keyboard_shortcut_player_like_coin_fav -> CustomShortcutSlot.PlayerLike
            R.string.keyboard_shortcut_player_screenshot -> CustomShortcutSlot.PlayerScreenshot
            R.string.keyboard_shortcut_player_lock -> CustomShortcutSlot.PlayerLock
            R.string.keyboard_shortcut_player_parts -> CustomShortcutSlot.PlayerNextPart
            else -> null
        }
        val effectiveBinding = slot?.let { overrides.bindingFor(it) }
        KeyboardShortcutInfo(
            context.getString(definition.labelRes),
            effectiveBinding?.keyCode ?: definition.keyCode,
            effectiveBinding?.modifiers ?: definition.modifiers,
        )
    }
    return listOf(
        KeyboardShortcutGroup(context.getString(R.string.keyboard_shortcut_group_app), appShortcuts),
        KeyboardShortcutGroup(context.getString(R.string.keyboard_shortcuts_section_player), playerShortcuts),
    )
}
