package com.android.purebilibili.core.ui.adaptive

import android.view.KeyEvent

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
fun resolveAppShortcutAction(event: KeyEvent): AppShortcutAction? {
    if (event.action != KeyEvent.ACTION_DOWN) return null
    if (event.repeatCount != 0) return null
    if (!event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed) {
        return null
    }
    return when (event.keyCode) {
        KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_F -> AppShortcutAction.OpenSearch
        KeyEvent.KEYCODE_COMMA -> AppShortcutAction.OpenSettings
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
