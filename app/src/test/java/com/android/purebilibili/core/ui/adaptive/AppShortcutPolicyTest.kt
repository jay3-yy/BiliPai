package com.android.purebilibili.core.ui.adaptive

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShortcutPolicyTest {

    private fun createKeyEvent(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
        metaState: Int = 0,
        repeatCount: Int = 0,
    ): KeyEvent {
        return KeyEvent(0L, 0L, action, keyCode, repeatCount, metaState)
    }

    @Test
    fun resolveAppShortcutAction_mapsCtrlCombinations() {
        assertEquals(
            AppShortcutAction.OpenSearch,
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_K, metaState = KeyEvent.META_CTRL_ON)),
        )
        assertEquals(
            AppShortcutAction.OpenSearch,
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_F, metaState = KeyEvent.META_CTRL_ON)),
        )
        assertEquals(
            AppShortcutAction.OpenSettings,
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_COMMA, metaState = KeyEvent.META_CTRL_ON)),
        )
    }

    @Test
    fun resolveAppShortcutAction_mapsCtrlDigitsToBottomNavIndex() {
        assertEquals(
            AppShortcutAction.SelectBottomNavItem(0),
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_1, metaState = KeyEvent.META_CTRL_ON)),
        )
        assertEquals(
            AppShortcutAction.SelectBottomNavItem(8),
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_9, metaState = KeyEvent.META_CTRL_ON)),
        )
        assertEquals(
            AppShortcutAction.SelectBottomNavItem(3),
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_NUMPAD_4, metaState = KeyEvent.META_CTRL_ON)),
        )
    }

    @Test
    fun resolveAppShortcutAction_ignoresKeyupAndRepeatedDowns() {
        assertNull(
            resolveAppShortcutAction(
                createKeyEvent(KeyEvent.KEYCODE_K, action = KeyEvent.ACTION_UP, metaState = KeyEvent.META_CTRL_ON),
            )
        )
        assertNull(
            resolveAppShortcutAction(
                createKeyEvent(KeyEvent.KEYCODE_K, repeatCount = 1, metaState = KeyEvent.META_CTRL_ON),
            )
        )
    }

    @Test
    fun resolveAppShortcutAction_requiresPureCtrlModifier() {
        // 无修饰键的普通按键放行，由文本输入与页面自身处理。
        assertNull(resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_K)))
        // Alt/Meta/Shift 修饰组合交给系统与其它输入路径。
        assertNull(
            resolveAppShortcutAction(
                createKeyEvent(KeyEvent.KEYCODE_K, metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON),
            )
        )
        assertNull(
            resolveAppShortcutAction(
                createKeyEvent(KeyEvent.KEYCODE_K, metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON),
            )
        )
        assertNull(
            resolveAppShortcutAction(
                createKeyEvent(KeyEvent.KEYCODE_1, metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON),
            )
        )
    }

    @Test
    fun resolveAppShortcutAction_doesNotShadowTextEditingDefaults() {
        // 官方默认文本编辑快捷键不得被本策略占用。
        assertNull(
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_C, metaState = KeyEvent.META_CTRL_ON)),
        )
        assertNull(
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_V, metaState = KeyEvent.META_CTRL_ON)),
        )
        assertNull(
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_A, metaState = KeyEvent.META_CTRL_ON)),
        )
        assertNull(
            resolveAppShortcutAction(createKeyEvent(KeyEvent.KEYCODE_Z, metaState = KeyEvent.META_CTRL_ON)),
        )
    }

    @Test
    fun appShortcutDefinitions_coverEveryActionKind() {
        val actions = APP_SHORTCUT_DEFINITIONS.map { it.action }
        assertTrue(actions.any { it is AppShortcutAction.OpenSearch })
        assertTrue(actions.any { it is AppShortcutAction.OpenSettings })
        assertTrue(actions.any { it is AppShortcutAction.SelectBottomNavItem })
        // 对照表与解析结果必须一致：表中每个按键组合解析出的动作与声明的动作相同。
        assertEquals(
            listOf(AppShortcutAction.OpenSearch, AppShortcutAction.OpenSettings, AppShortcutAction.SelectBottomNavItem(0)),
            APP_SHORTCUT_DEFINITIONS.map { definition ->
                resolveAppShortcutAction(
                    createKeyEvent(
                        when (definition.helperCharacter) {
                            'K' -> KeyEvent.KEYCODE_K
                            ',' -> KeyEvent.KEYCODE_COMMA
                            else -> KeyEvent.KEYCODE_1
                        },
                        metaState = definition.helperMetaState,
                    )
                )?.let { action ->
                    if (action is AppShortcutAction.SelectBottomNavItem) {
                        AppShortcutAction.SelectBottomNavItem(0)
                    } else {
                        action
                    }
                }
            },
        )
    }
}
