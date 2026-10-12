package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.theme.AppUiStyle

internal enum class HomeSelectionIndicatorStyle {
    CAPSULE,
    MD3_UNDERLINE,
}

internal fun resolveHomeSelectionIndicatorStyle(
    uiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean,
    forceMaterialUnderline: Boolean = false,
): HomeSelectionIndicatorStyle = when {
    liquidGlassEnabled -> HomeSelectionIndicatorStyle.CAPSULE
    forceMaterialUnderline -> HomeSelectionIndicatorStyle.MD3_UNDERLINE
    uiStyle == AppUiStyle.MIUIX -> HomeSelectionIndicatorStyle.CAPSULE
    else -> HomeSelectionIndicatorStyle.MD3_UNDERLINE
}
