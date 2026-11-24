package com.android.purebilibili.core.ui.renderer.glass

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.liquidControlThemeColor

/**
 * 液态玻璃渲染线的圆润磨砂输入框：
 * 保留完整的输入法、前后缀、焦点与校验语义，统一提供连续曲率轮廓、轻磨砂填充与高光细边。
 */
@Composable
internal fun AppGlassOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    textStyle: TextStyle,
    label: (@Composable () -> Unit)?,
    placeholder: (@Composable () -> Unit)?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    prefix: (@Composable () -> Unit)?,
    suffix: (@Composable () -> Unit)?,
    supportingText: (@Composable () -> Unit)?,
    isError: Boolean,
    visualTransformation: VisualTransformation,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    interactionSource: MutableInteractionSource,
    focusRequester: FocusRequester?,
    shape: Shape,
    colors: TextFieldColors,
) {
    val dark = isAppInDarkTheme()
    val accent = liquidControlThemeColor()
    val resolvedShape = if (shape == OutlinedTextFieldDefaults.shape) {
        AppGlassTokens.continuousShape(AppGlassTokens.CompactCornerRadius)
    } else shape
    val fill = AppGlassTokens.neutralPillFill(dark)
    val glassColors = if (colors == OutlinedTextFieldDefaults.colors()) {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill.copy(alpha = fill.alpha * 0.6f),
            errorContainerColor = fill,
            focusedBorderColor = accent.copy(alpha = 0.85f),
            unfocusedBorderColor = AppGlassTokens.cardRimColor(dark),
            disabledBorderColor = AppGlassTokens.cardRimColor(dark).copy(alpha = 0.5f),
            cursorColor = accent,
            focusedLabelColor = accent,
            unfocusedLabelColor = AppContentColors.secondaryText(),
            focusedTextColor = AppContentColors.primaryText(),
            unfocusedTextColor = AppContentColors.primaryText(),
            focusedPlaceholderColor = AppContentColors.secondaryText(),
            unfocusedPlaceholderColor = AppContentColors.secondaryText(),
        )
    } else colors

    val fieldModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else modifier

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = resolvedShape,
        colors = glassColors,
    )
}
