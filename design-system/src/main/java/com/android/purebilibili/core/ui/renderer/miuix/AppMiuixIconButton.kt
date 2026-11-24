package com.android.purebilibili.core.ui.renderer.miuix

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.android.purebilibili.core.ui.components.LocalAppIconButtonGlassSurfaceProvided
import com.android.purebilibili.core.ui.components.AppIconButtonColors
import com.android.purebilibili.core.ui.components.AppIconButtonVariant
import com.android.purebilibili.core.ui.components.LocalAppActionContentColor
import com.android.purebilibili.core.ui.components.appDesktopFocusableItemVisuals
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor

@Composable
internal fun AppMiuixIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    colors: AppIconButtonColors?,
    interactionSource: MutableInteractionSource?,
    variant: AppIconButtonVariant,
    content: @Composable () -> Unit,
) {
    val neutralContentColor = LocalAppActionContentColor.current.takeOrElse { MiuixLocalContentColor.current }
    val defaultColors = when (variant) {
        AppIconButtonVariant.Standard -> AppIconButtonColors(
            containerColor = Color.Unspecified,
            contentColor = neutralContentColor,
            disabledContainerColor = Color.Unspecified,
            disabledContentColor = neutralContentColor.copy(alpha = 0.38f),
        )
        AppIconButtonVariant.Filled -> MiuixButtonDefaults.buttonColorsPrimary().let {
            AppIconButtonColors(
                containerColor = it.color,
                contentColor = it.contentColor,
                disabledContainerColor = it.disabledColor,
                disabledContentColor = it.disabledContentColor,
            )
        }
    }
    val requestedColors = colors ?: AppIconButtonColors()
    val backgroundColor = if (enabled) {
        requestedColors.containerColor.takeOrElse { defaultColors.containerColor }
    } else {
        requestedColors.disabledContainerColor.takeOrElse { defaultColors.disabledContainerColor }
    }
    val contentColor = if (enabled) {
        requestedColors.contentColor.takeOrElse { defaultColors.contentColor }
    } else {
        requestedColors.disabledContentColor.takeOrElse { defaultColors.disabledContentColor }
    }
    val pointerMirror = if (interactionSource != null) {
        Modifier.mirrorMiuixPointerPressInteractions(
            interactionSource = interactionSource,
            enabled = enabled,
        )
    } else {
        Modifier
    }
    val cornerRadius = IconButtonDefaults.CornerRadius
    // Leave explicit sizes to the caller and default sizing to the native control.
    val nativeModifier = modifier
        .appDesktopFocusableItemVisuals(
            enabled = enabled,
            shape = RoundedCornerShape(cornerRadius),
        )
        .then(pointerMirror)

    CompositionLocalProvider(
        MiuixLocalContentColor provides contentColor,
        androidx.compose.material3.LocalContentColor provides contentColor,
        com.android.purebilibili.core.ui.LocalAppButtonSlotPalette provides
            if (LocalAppIconButtonGlassSurfaceProvided.current) null else com.android.purebilibili.core.ui.AppButtonSlotPalette(
                containerColor = backgroundColor.takeOrElse { Color.Transparent },
                contentColor = contentColor,
                enabled = enabled,
            ),
    ) {
        MiuixIconButton(
            onClick = onClick,
            modifier = nativeModifier,
            enabled = enabled,
            backgroundColor = if (LocalAppIconButtonGlassSurfaceProvided.current) Color.Transparent else backgroundColor,
            cornerRadius = cornerRadius,
            content = content,
        )
    }
}

/**
 * Mirrors pointer presses into a caller-owned source without consuming input or adding click
 * behavior. Keyboard, D-pad, and semantic clicks remain native, but their pressed interaction is
 * intentionally not exported because Miuix IconButton does not expose its interaction source.
 */
private fun Modifier.mirrorMiuixPointerPressInteractions(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
): Modifier {
    if (!enabled) return this
    return pointerInput(interactionSource) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val press = PressInteraction.Press(down.position)
            interactionSource.tryEmit(press)
            var released = false
            try {
                released = waitForUpOrCancellation(PointerEventPass.Initial) != null
            } finally {
                interactionSource.tryEmit(
                    if (released) {
                        PressInteraction.Release(press)
                    } else {
                        PressInteraction.Cancel(press)
                    },
                )
            }
        }
    }
}
