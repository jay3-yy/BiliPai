package com.android.purebilibili.feature.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class SettingsVisualSpec(
    val screenHorizontalPadding: Dp = 16.dp,
    val sectionTopSpacing: Dp = 20.dp,
    val sectionBottomSpacing: Dp = 8.dp,
    val groupCornerRadius: Dp = 14.dp,
    val categoryIconBubbleSize: Dp = 36.dp,
    val categoryIconSize: Dp = 20.dp,
    val categoryRowVerticalPadding: Dp = 12.dp,
    val largeTitleFontSize: androidx.compose.ui.unit.TextUnit = 34.sp,
    val largeTitleBottomPadding: Dp = 8.dp,
    val searchBarVerticalPadding: Dp = 10.dp,
)

internal fun resolveSettingsVisualSpec(): SettingsVisualSpec = SettingsVisualSpec()
