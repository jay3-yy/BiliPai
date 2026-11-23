package com.android.purebilibili.core.ui.adaptive

import android.view.KeyEvent
import com.android.purebilibili.feature.video.ui.overlay.FullscreenShortcutKey
import com.android.purebilibili.feature.video.ui.overlay.resolveFullscreenShortcutKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomShortcutPolicyTest {

    @Test
    fun validatesAppAndPlayerScopeRules() {
        // 应用级要求纯 Ctrl 且不占用标准文本编辑快捷键
        assertEquals(
            CustomKeyValidationResult.Valid,
            validateCustomKeyBinding(
                CustomShortcutSlot.AppOpenSearch,
                CustomKeyBinding(KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON),
            ),
        )
        assertIs<CustomKeyValidationResult.Invalid>(
            validateCustomKeyBinding(
                CustomShortcutSlot.AppOpenSearch,
                CustomKeyBinding(KeyEvent.KEYCODE_G, 0),
            ),
        )
        assertIs<CustomKeyValidationResult.Invalid>(
            validateCustomKeyBinding(
                CustomShortcutSlot.AppOpenSearch,
                CustomKeyBinding(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON),
            ),
        )

        // 播放器级允许单键或 Shift 组合，拒绝 Ctrl/Alt/系统保留键
        assertEquals(
            CustomKeyValidationResult.Valid,
            validateCustomKeyBinding(
                CustomShortcutSlot.PlayerPlayPause,
                CustomKeyBinding(KeyEvent.KEYCODE_P, 0),
            ),
        )
        assertEquals(
            CustomKeyValidationResult.Valid,
            validateCustomKeyBinding(
                CustomShortcutSlot.PlayerFullscreen,
                CustomKeyBinding(KeyEvent.KEYCODE_F, KeyEvent.META_SHIFT_ON),
            ),
        )
        assertIs<CustomKeyValidationResult.Invalid>(
            validateCustomKeyBinding(
                CustomShortcutSlot.PlayerMute,
                CustomKeyBinding(KeyEvent.KEYCODE_M, KeyEvent.META_CTRL_ON),
            ),
        )
        assertIs<CustomKeyValidationResult.Invalid>(
            validateCustomKeyBinding(
                CustomShortcutSlot.PlayerMute,
                CustomKeyBinding(KeyEvent.KEYCODE_ESCAPE, 0),
            ),
        )
    }

    @Test
    fun resolvesConflictBySwappingDefaultBindingsAndRoundTripsEncoding() {
        // 将全屏绑定到原本属于静音的 M 键，静音应自动互换到原全屏键 F
        val updated = CustomShortcutOverrides().withBinding(
            CustomShortcutSlot.PlayerFullscreen,
            CustomKeyBinding(KeyEvent.KEYCODE_M),
        )
        assertEquals(CustomKeyBinding(KeyEvent.KEYCODE_M), updated.bindingFor(CustomShortcutSlot.PlayerFullscreen))
        assertEquals(CustomKeyBinding(KeyEvent.KEYCODE_F), updated.bindingFor(CustomShortcutSlot.PlayerMute))

        val encoded = encodeCustomShortcutOverrides(updated)
        val decoded = decodeCustomShortcutOverrides(encoded)
        assertEquals(updated, decoded)

        // 恢复默认后不再出现在覆写表里
        val reset = updated.resetSlot(CustomShortcutSlot.PlayerFullscreen)
        assertEquals(CustomShortcutSlot.PlayerFullscreen.defaultBinding, reset.bindingFor(CustomShortcutSlot.PlayerFullscreen))
    }

    @Test
    fun appShortcutResolutionRespectsCustomOverrides() {
        val overrides = CustomShortcutOverrides().withBinding(
            CustomShortcutSlot.AppOpenSearch,
            CustomKeyBinding(KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON),
        )
        val ctrlG = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_G, 0, KeyEvent.META_CTRL_ON)
        val ctrlK = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_K, 0, KeyEvent.META_CTRL_ON)
        assertEquals(AppShortcutAction.OpenSearch, resolveAppShortcutAction(ctrlG, overrides))
        assertNull(resolveAppShortcutAction(ctrlK, overrides))
    }

    @Test
    fun formatsReadableKeyLabels() {
        assertEquals("Ctrl+K", formatCustomKeyBinding(CustomKeyBinding(KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_ON)))
        assertEquals("Space", formatCustomKeyBinding(CustomKeyBinding(KeyEvent.KEYCODE_SPACE)))
        assertEquals("Shift+F", formatCustomKeyBinding(CustomKeyBinding(KeyEvent.KEYCODE_F, KeyEvent.META_SHIFT_ON)))
        assertTrue(formatCustomKeyBinding(CustomKeyBinding(KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON)) == "Ctrl+,")
    }

    @Test
    fun fullscreenShortcutResolutionRespectsCustomOverridesAndShiftBindings() {
        val overrides = CustomShortcutOverrides()
            .withBinding(CustomShortcutSlot.PlayerPlayPause, CustomKeyBinding(KeyEvent.KEYCODE_P))
            .withBinding(
                CustomShortcutSlot.PlayerFullscreen,
                CustomKeyBinding(KeyEvent.KEYCODE_F, KeyEvent.META_SHIFT_ON),
            )
        assertEquals(
            FullscreenShortcutKey.Space,
            resolveFullscreenShortcutKey(KeyEvent.KEYCODE_P, 0, overrides),
        )
        // 自定义后旧默认键 Space 与 K 不再触发播放暂停
        assertEquals(
            FullscreenShortcutKey.Other,
            resolveFullscreenShortcutKey(KeyEvent.KEYCODE_SPACE, 0, overrides),
        )
        assertEquals(
            FullscreenShortcutKey.Other,
            resolveFullscreenShortcutKey(KeyEvent.KEYCODE_K, 0, overrides),
        )
        // Shift 组合键全屏生效，单按 F 不再触发
        assertEquals(
            FullscreenShortcutKey.KeyF,
            resolveFullscreenShortcutKey(KeyEvent.KEYCODE_F, KeyEvent.META_SHIFT_ON, overrides),
        )
        assertEquals(
            FullscreenShortcutKey.Other,
            resolveFullscreenShortcutKey(KeyEvent.KEYCODE_F, 0, overrides),
        )
    }

    @Test
    fun exportsAndImportsShortcutConfigWithPrefixValidation() {
        val overrides = CustomShortcutOverrides()
            .withBinding(CustomShortcutSlot.AppOpenSearch, CustomKeyBinding(KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON))
            .withBinding(CustomShortcutSlot.PlayerMute, CustomKeyBinding(KeyEvent.KEYCODE_N))
        val exported = exportCustomShortcutConfigText(overrides)
        assertTrue(exported.startsWith(CUSTOM_SHORTCUT_EXPORT_PREFIX))
        assertEquals(overrides, importCustomShortcutConfigText("  $exported \n"))
        assertNull(importCustomShortcutConfigText("not-a-shortcut-config"))
        assertNull(importCustomShortcutConfigText(null))
    }
}
