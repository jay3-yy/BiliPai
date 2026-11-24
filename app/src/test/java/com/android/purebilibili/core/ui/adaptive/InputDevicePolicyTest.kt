package com.android.purebilibili.core.ui.adaptive

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.util.AppWindowAdaptiveInfo
import com.android.purebilibili.core.util.WindowHeightSizeClass
import com.android.purebilibili.core.util.WindowSizeClass
import com.android.purebilibili.core.util.WindowWidthSizeClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputDevicePolicyTest {
    private val window = WindowSizeClass(
        widthSizeClass = WindowWidthSizeClass.Expanded,
        heightSizeClass = WindowHeightSizeClass.Medium,
        widthDp = 1000.dp,
        heightDp = 700.dp,
    )

    @Test
    fun precisePointerEnablesHoverWithoutRequiringKeyboard() {
        val policy = resolveInputDevicePolicy(
            AppWindowAdaptiveInfo(
                windowSizeClass = window,
                precisePointerConnected = true,
            ),
        )

        assertTrue(policy.enableHoverEffects)
        assertFalse(policy.enableKeyboardNavigation)
        assertTrue(policy.enablePointerFocus)
    }

    @Test
    fun hardwareKeyboardEnablesNavigationWithoutHover() {
        val policy = resolveInputDevicePolicy(
            AppWindowAdaptiveInfo(
                windowSizeClass = window,
                hardwareKeyboardConnected = true,
            ),
        )

        assertFalse(policy.enableHoverEffects)
        assertTrue(policy.enableKeyboardNavigation)
        assertTrue(policy.enablePointerFocus)
        assertFalse(policy.enableGamepadControl)
    }

    @Test
    fun gamepadConnectionEnablesNavigationHoverAndGamepadControl() {
        val policy = resolveInputDevicePolicy(
            AppWindowAdaptiveInfo(
                windowSizeClass = window,
                gamepadConnected = true,
            ),
        )

        assertTrue(policy.enableHoverEffects)
        assertTrue(policy.enableKeyboardNavigation)
        assertTrue(policy.enablePointerFocus)
        assertTrue(policy.enableGamepadControl)
    }
}
