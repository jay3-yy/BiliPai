package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.ProvideAppEntryIconColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Style-neutral preference entry points.
 *
 * The first migration phase delegates to the proven adaptive renderers so callers can move
 * away from historical IOS* names without changing layout, interaction, or persistence.
 */
@Composable
fun AppPreferenceSectionTitle(title: String) = AdaptivePreferenceSectionTitleRenderer(title)

enum class AppPreferenceGroupPresentation {
    CARD,
    FLAT,
}

enum class AppPreferenceGroupSegment {
    WHOLE,
    FIRST,
    MIDDLE,
    LAST,
}

@Composable
fun AppPreferenceGroup(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shape: Shape? = null,
    border: BorderStroke? = null,
    presentation: AppPreferenceGroupPresentation = AppPreferenceGroupPresentation.CARD,
    segment: AppPreferenceGroupSegment = AppPreferenceGroupSegment.WHOLE,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 分组即“同级”边界：组内入口按声明顺序分配多彩色，重新进入分组时从头取色
    ProvideAppEntryIconColors {
        AdaptivePreferenceGroupRenderer(
            modifier = modifier,
            containerColor = containerColor,
            shape = shape,
            border = border,
            presentation = presentation,
            segment = segment,
            content = content,
        )
    }
}

/** 标题附加操作独立响应点击；未提供插槽时保留原有玻璃和原生设置行布局。 */
@Composable
fun AppSwitchPreference(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    iconTint: Color = AppContentColors.secondaryText(),
    textColor: Color = AppContentColors.primaryText(),
    subtitleColor: Color = AppContentColors.secondaryText(),
    titleTrailingContent: (@Composable () -> Unit)? = null,
) = AdaptiveSwitchPreferenceContent(
    icon = icon,
    title = title,
    subtitle = subtitle,
    checked = checked,
    onCheckedChange = onCheckedChange,
    enabled = enabled,
    iconTint = iconTint,
    textColor = textColor,
    subtitleColor = subtitleColor,
    titleTrailingContent = titleTrailingContent,
)

@Composable
fun AppSliderPreference(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: String? = null,
    iconTint: Color = AppContentColors.secondaryText(),
    textColor: Color = AppContentColors.primaryText(),
    subtitleColor: Color = AppContentColors.secondaryText(),
    valueColor: Color = AppContentColors.primaryText(),
) = AdaptiveSliderPreferenceRenderer(
    icon = icon,
    title = title,
    subtitle = subtitle,
    value = value,
    onValueChange = onValueChange,
    valueRange = valueRange,
    steps = steps,
    valueLabel = valueLabel,
    iconTint = iconTint,
    textColor = textColor,
    subtitleColor = subtitleColor,
    valueColor = valueColor,
)

/** [contentModifier] wraps the row content inside its click feedback for shared transitions. */
@Composable
fun AppPreference(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    copyValue: String? = null,
    onClick: (() -> Unit)? = null,
    iconTint: Color = AppContentColors.secondaryText(),
    textColor: Color = AppContentColors.primaryText(),
    subtitleColor: Color = AppContentColors.secondaryText(),
    valueColor: Color = AppContentColors.secondaryText(),
    chevronTint: Color = AppContentColors.secondaryText().copy(alpha = 0.72f),
    centered: Boolean = false,
    enableCopy: Boolean = false,
    onCopyRequest: ((text: String, label: String?) -> Unit)? = null,
    showChevron: Boolean = true,
    contentModifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) = AdaptivePreferenceContent(
    icon = icon,
    iconPainter = iconPainter,
    title = title,
    subtitle = subtitle,
    value = value,
    copyValue = copyValue,
    onClick = onClick,
    iconTint = iconTint,
    textColor = textColor,
    subtitleColor = subtitleColor,
    valueColor = valueColor,
    chevronTint = chevronTint,
    centered = centered,
    enableCopy = enableCopy,
    onCopyRequest = onCopyRequest,
    showChevron = showChevron,
    contentModifier = contentModifier,
    trailingContent = trailingContent,
)

@Composable
fun AppPreferenceDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 66.dp,
) = AdaptivePreferenceDividerRenderer(modifier = modifier, startIndent = startIndent)

@Composable
fun AppPreferenceGridItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    iconTint: Color = AppContentColors.secondaryText(),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = AppContentColors.primaryText(),
    modifier: Modifier = Modifier,
) = AdaptivePreferenceGridItemRenderer(
    icon = icon,
    title = title,
    onClick = onClick,
    iconTint = iconTint,
    containerColor = containerColor,
    contentColor = contentColor,
    modifier = modifier,
)

enum class AppSearchFieldPresentation {
    STANDARD,
    TOP_BAR,
}

@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    onSearch: () -> Unit = {},
    onClear: () -> Unit = { onQueryChange("") },
    presentation: AppSearchFieldPresentation = AppSearchFieldPresentation.STANDARD,
    autoFocusEnabled: Boolean = false,
    focusRequester: FocusRequester? = null,
    containerColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    leadingIconHorizontalOffset: Dp = 0.dp,
) = AdaptiveSearchFieldRenderer(
    query = query,
    onQueryChange = onQueryChange,
    modifier = modifier,
    placeholder = placeholder,
    containerColor = containerColor,
    forceExpandedInput = presentation == AppSearchFieldPresentation.TOP_BAR,
    onSearch = onSearch,
    onClear = onClear,
    showClearAction = presentation != AppSearchFieldPresentation.TOP_BAR,
    autoFocusEnabled = autoFocusEnabled,
    focusRequester = focusRequester,
    interactionSource = interactionSource,
    leadingIconHorizontalOffset = leadingIconHorizontalOffset,
)

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    supportingText: @Composable (() -> Unit)? = null,
) = AdaptiveTextFieldRenderer(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    label = label,
    placeholder = placeholder,
    singleLine = singleLine,
    minLines = minLines,
    maxLines = maxLines,
    isError = isError,
    supportingText = supportingText,
    keyboardOptions = keyboardOptions,
)
