package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors

/** Content-sized menu geometry, independent of the popup's native surface and positioning. */
@Composable
fun AppCompactMenuColumn(
    modifier: Modifier = Modifier,
    minWidth: Dp = 120.dp,
    maxWidth: Dp = 288.dp,
    // DropdownMenu already owns scrolling and measures its content with unbounded height.
    scrollable: Boolean = true,
    overscrollEnabled: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollModifier = if (scrollable && LocalAppGlassMenuScope.current == null) {
        if (overscrollEnabled) {
            Modifier.verticalScroll(scrollState)
        } else {
            Modifier.verticalScroll(state = scrollState, overscrollEffect = null)
        }
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .widthIn(min = minWidth, max = maxWidth)
            .width(IntrinsicSize.Max)
            .then(scrollModifier)
            .padding(vertical = if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
                top.yukonga.miuix.kmp.basic.DropdownDefaults.FirstLastVerticalPadding -
                    top.yukonga.miuix.kmp.basic.DropdownDefaults.MiddleVerticalPadding
            } else 4.dp),
        content = content,
    )
}

/** A compact action that grows naturally for wrapped descriptions and larger system fonts. */
@Composable
fun AppCompactMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    dismissOnClick: Boolean = true,
) {
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS, onClick)
    val glassScope = LocalAppGlassMenuScope.current
    val menuModifier = modifier.glassMenuItem(enabled, dismissOnClick, feedbackClick).glassMenuSelectedItem(selected)
    if (glassScope == null && com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
        val palette = top.yukonga.miuix.kmp.basic.DropdownDefaults.dropdownColors()
        AppNativeDropdownMenuItem(
            modifier = menuModifier,
            text = {
                AppText(text, tapToCopyEnabled = false)
                if (summary != null) {
                    AppText(
                        summary,
                        color = when {
                            !enabled -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            selected -> palette.selectedSummaryColor
                            else -> palette.summaryColor
                        },
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                        tapToCopyEnabled = false,
                    )
                }
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            onClick = feedbackClick,
            enabled = enabled,
            selected = selected,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = top.yukonga.miuix.kmp.basic.DropdownDefaults.InsideHorizontalPadding,
                vertical = top.yukonga.miuix.kmp.basic.DropdownDefaults.MiddleVerticalPadding,
            ),
            interactionSource = remember { MutableInteractionSource() },
        )
        return
    }
    val contentColor = when {
        !enabled -> AppContentColors.disabled()
        selected -> AppContentColors.accent()
        else -> AppContentColors.primaryText()
    }
    val summaryColor = if (enabled) AppContentColors.secondaryText() else contentColor
    AppExactTouchTargets {
        AppSurface(
            onClick = {
                if (enabled) {
                    if (glassScope != null) glassScope.dispatch(feedbackClick, dismissOnClick) else feedbackClick()
                }
            },
            enabled = enabled,
            modifier = menuModifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { this.selected = selected },
            color = Color.Transparent,
            contentColor = contentColor,
        ) {
            ProvideAppActionContentColor(contentColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center,
                            propagateMinConstraints = true,
                        ) { leadingIcon() }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AppText(
                            text = text,
                            color = contentColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            tapToCopyEnabled = false,
                        )
                        if (summary != null) {
                            AppText(
                                text = summary,
                                color = summaryColor,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                tapToCopyEnabled = false,
                            )
                        }
                    }
                    if (trailingIcon != null || selected) {
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center,
                            propagateMinConstraints = true,
                        ) {
                            if (trailingIcon != null) {
                                trailingIcon()
                            } else {
                                AppIcon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
