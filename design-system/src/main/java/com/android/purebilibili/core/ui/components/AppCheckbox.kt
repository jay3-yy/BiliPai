package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.AppThemeRenderer
import com.android.purebilibili.core.ui.rememberAppThemeRenderer
import com.android.purebilibili.core.ui.renderer.glass.AppGlassCheckbox
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3Checkbox
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixCheckbox

@Immutable
data class AppCheckboxColors(
    val checkedColor: Color,
    val uncheckedColor: Color,
)

object AppCheckboxDefaults {
    fun colors(
        checkedColor: Color,
        uncheckedColor: Color,
    ): AppCheckboxColors = AppCheckboxColors(
        checkedColor = checkedColor,
        uncheckedColor = uncheckedColor,
    )
}

@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: AppCheckboxColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    // Miuix and the glass switch retain their own toggle/drag feedback.
    val materialChange = if (onCheckedChange != null) {
        appValueChangeWithFeedback<Boolean>(
            scene = com.android.purebilibili.core.ui.AppHapticScene.CONTROLS,
            feedbackType = { if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff },
            onValueChange = onCheckedChange,
        )
    } else null
    when (rememberAppThemeRenderer()) {
        AppThemeRenderer.MATERIAL3 -> AppMaterial3Checkbox(
            checked = checked,
            onCheckedChange = materialChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        )
        AppThemeRenderer.MIUIX -> AppMiuixCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
        )
        AppThemeRenderer.LIQUID_GLASS -> AppGlassCheckbox(
            checked = checked,
            onCheckedChange = materialChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        )
    }
}
