package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.ui.unit.dp

/**
 * Theme-native single-choice row.
 *
 * Selection is expressed by [AppRadioButton], which delegates to Miuix or Material 3.
 * The row deliberately keeps one semantic container in both states so feature code never
 * has to hand-pair a selected background with its content colors.
 */
@Composable
fun AppSingleChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppShapes.container(ContainerLevel.Chip),
    content: @Composable RowScope.() -> Unit,
) {
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        color = AppSurfaceTokens.surfaceContainerHigh(),
        contentColor = AppSurfaceTokens.onSurfaceContainerHigh(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Small),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            AppRadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
        }
    }
}
