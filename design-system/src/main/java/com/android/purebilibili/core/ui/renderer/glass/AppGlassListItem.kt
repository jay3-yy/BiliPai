package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.ProvideAppActionContentColor

/**
 * 液态玻璃渲染线的 iOS 风格列表条目：
 * 采用 48dp 最小触控行高、16dp/11dp 舒适留白与清晰主副标题字阶层次。
 */
@Composable
internal fun AppGlassListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier,
    overlineContent: (@Composable () -> Unit)?,
    supportingContent: (@Composable () -> Unit)?,
    leadingContent: (@Composable () -> Unit)?,
    trailingContent: (@Composable () -> Unit)?,
) {
    val primaryColor = AppContentColors.primaryText()
    val secondaryColor = AppContentColors.secondaryText()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppGlassTokens.ListRowMinHeight)
            .padding(
                horizontal = AppGlassTokens.ListRowHorizontalPadding,
                vertical = AppGlassTokens.ListRowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            ProvideAppActionContentColor(primaryColor, leadingContent)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            overlineContent?.let { overline ->
                ProvideTextStyle(
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        color = secondaryColor,
                    )
                ) {
                    ProvideAppActionContentColor(secondaryColor, overline)
                }
            }
            ProvideTextStyle(
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    color = primaryColor,
                )
            ) {
                ProvideAppActionContentColor(primaryColor, headlineContent)
            }
            supportingContent?.let { supporting ->
                ProvideTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = secondaryColor,
                    )
                ) {
                    ProvideAppActionContentColor(secondaryColor, supporting)
                }
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            ProvideAppActionContentColor(secondaryColor, trailingContent)
        }
    }
}
