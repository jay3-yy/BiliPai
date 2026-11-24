package com.android.purebilibili.core.ui.adaptive

import com.android.purebilibili.core.util.AppWindowAdaptiveInfo

/**
 * 输入设备交互策略：根据指针/键盘连接状态决定是否启用 hover、焦点导航等增强交互。
 */
internal data class InputDevicePolicy(
    val enableHoverEffects: Boolean,
    val enableKeyboardNavigation: Boolean,
    val enablePointerFocus: Boolean,
    val enableGamepadControl: Boolean = false,
)

internal fun resolveInputDevicePolicy(
    adaptiveInfo: AppWindowAdaptiveInfo,
): InputDevicePolicy {
    val hasPrecisePointer = adaptiveInfo.precisePointerConnected
    val hasHardwareKeyboard = adaptiveInfo.hardwareKeyboardConnected
    val hasGamepad = adaptiveInfo.gamepadConnected

    return InputDevicePolicy(
        enableHoverEffects = hasPrecisePointer || hasGamepad,
        enableKeyboardNavigation = hasHardwareKeyboard || hasGamepad,
        enablePointerFocus = hasPrecisePointer || hasHardwareKeyboard || hasGamepad,
        enableGamepadControl = hasGamepad,
    )
}
