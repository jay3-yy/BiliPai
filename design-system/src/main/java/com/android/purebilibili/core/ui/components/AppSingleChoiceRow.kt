package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.ui.AppChromeSizeTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.graphics.Shape
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role

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
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS, onClick)
    // 选择状态由可点击整行承载，尾部标记不重复成为无障碍节点。
    val rowModifier = modifier.semantics(mergeDescendants = true) {
        this.selected = selected
        role = Role.RadioButton
        if (!enabled) disabled()
    }
    if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
        val scheme = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
        val rowContentColor = when {
            !enabled -> scheme.disabledOnSecondaryVariant
            selected -> scheme.primary
            else -> scheme.onBackground
        }
        top.yukonga.miuix.kmp.basic.BasicComponent(
            modifier = rowModifier,
            onClick = feedbackClick,
            enabled = enabled,
            role = androidx.compose.ui.semantics.Role.RadioButton,
            endActions = {
                AppRadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            },
        ) {
            ProvideAppActionContentColor(rowContentColor) {
                Row(verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
        return
    }
    AppSurface(
        onClick = feedbackClick,
        enabled = enabled,
        modifier = rowModifier,
        shape = shape,
        color = AppSurfaceTokens.surfaceContainerHigh(),
        contentColor = AppSurfaceTokens.onSurfaceContainerHigh(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppChromeSizeTokens.CompactRowMinHeightDp.dp)
                .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Small),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            AppRadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
