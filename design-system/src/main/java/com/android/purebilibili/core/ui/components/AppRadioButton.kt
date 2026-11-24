package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.AppThemeRenderer
import com.android.purebilibili.core.ui.rememberAppThemeRenderer
import com.android.purebilibili.core.ui.renderer.glass.AppGlassRadioButton
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3RadioButton
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixRadioButton

@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val materialClick = if (onClick != null) appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS, onClick) else null
    when (rememberAppThemeRenderer()) {
        AppThemeRenderer.MATERIAL3 -> AppMaterial3RadioButton(
            selected = selected,
            onClick = materialClick,
            modifier = modifier,
            enabled = enabled,
            interactionSource = interactionSource,
        )
        AppThemeRenderer.MIUIX -> AppMiuixRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
        AppThemeRenderer.LIQUID_GLASS -> AppGlassRadioButton(
            selected = selected,
            onClick = materialClick,
            modifier = modifier,
            enabled = enabled,
            interactionSource = interactionSource,
        )
    }
}
