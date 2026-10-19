package com.android.purebilibili.core.ui.renderer.material3

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.android.purebilibili.core.theme.ACCESSIBLE_UI_MIN_CONTRAST
import com.android.purebilibili.core.theme.calculateContrastRatio
import com.android.purebilibili.core.theme.opaqueCompositeOver
import com.android.purebilibili.core.theme.resolveAccessibleContainerColors
import com.android.purebilibili.core.ui.components.appDesktopInteractionVisuals

@Composable
internal fun AppMaterial3Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    thumbContent: (@Composable () -> Unit)?,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    showThumbIcon: Boolean,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val interactive = enabled && onCheckedChange != null
    val nativeColors = SwitchDefaults.colors()
    // Native MD3 only: onPrimary is a text role and may be black for a light
    // custom accent. Keep light-surface handles light independently of that role.
    // Glass and Miuix retain their separate renderer policies and geometry.
    val scheme = MaterialTheme.colorScheme
    val checkedThumbColor = if (scheme.surface.luminance() > 0.5f) {
        Color.White
    } else {
        nativeColors.checkedThumbColor
    }
    val checkedThumbBackground = opaqueCompositeOver(checkedThumbColor, nativeColors.checkedTrackColor)
    // Resolve the check mark against the actual handle, including custom accents.
    val checkedIconColor = if (calculateContrastRatio(
            opaqueCompositeOver(nativeColors.checkedIconColor, checkedThumbBackground),
            checkedThumbBackground,
        ) >= ACCESSIBLE_UI_MIN_CONTRAST
    ) {
        nativeColors.checkedIconColor
    } else {
        resolveAccessibleContainerColors(
            containerColor = checkedThumbColor,
            contentColor = nativeColors.checkedIconColor,
            backgroundColor = nativeColors.checkedTrackColor,
            fallbackContentColors = listOf(nativeColors.checkedTrackColor, scheme.onSurface, Color.White, Color.Black),
            minimumContrast = ACCESSIBLE_UI_MIN_CONTRAST,
        ).contentColor
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.appDesktopInteractionVisuals(resolvedInteractionSource, interactive),
        thumbContent = thumbContent ?: if (showThumbIcon) {
            {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        },
        enabled = enabled,
        colors = nativeColors.copy(
            checkedThumbColor = checkedThumbColor,
            checkedIconColor = checkedIconColor,
        ),
        interactionSource = resolvedInteractionSource,
    )
}
