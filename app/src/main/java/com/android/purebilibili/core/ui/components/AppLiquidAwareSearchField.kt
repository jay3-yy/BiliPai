package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.isSpecified
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.ProvideAppHapticScene
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import top.yukonga.miuix.kmp.blur.Backdrop

/** Native search input with an optional liquid glass surface behind it. */
@Composable
fun AppLiquidAwareSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    onSearch: () -> Unit = {},
    onClear: () -> Unit = { onQueryChange("") },
    autoFocusEnabled: Boolean = false,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
    backdrop: Backdrop? = null,
    liquidContentContainerColor: Color = Color.Transparent,
    isScrollInProgressProvider: () -> Boolean = { false },
    leadingIconHorizontalOffset: Dp = 0.dp,
    presentation: AppSearchFieldPresentation = AppSearchFieldPresentation.STANDARD,
    compactHeight: Dp = Dp.Unspecified,
    inheritPopupGlass: Boolean = false,
    captureShellForContent: Boolean = true,
) {
    val liquidHeight = if (compactHeight.isSpecified && compactHeight > 0.dp) {
        compactHeight
    } else {
        AppChromeSizeTokens.LiquidSearchFieldHeightDp.dp
    }
    // 弹窗自身已提供模糊材质；子输入框只叠加轻量边界，不采样空白背景。
    // 该分支仅由弹窗入口显式启用，玻璃关闭时仍走原生输入框。
    if (inheritPopupGlass && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
        (com.android.purebilibili.core.ui.LocalAppPopupWindowMaterialEnabled.current ||
            com.android.purebilibili.core.ui.blur.LocalAppGlassContainer.current != null)
    ) {
        val outline = com.android.purebilibili.core.ui.AppContentColors.primaryText()
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(outline.copy(alpha = 0.06f))
                .border(1.dp, outline.copy(alpha = 0.14f), CircleShape),
        ) {
            CompactLiquidSearchInput(
                query = query, onQueryChange = onQueryChange, placeholder = placeholder,
                height = liquidHeight, showClearAction = presentation != AppSearchFieldPresentation.TOP_BAR,
                onSearch = onSearch, onClear = onClear, focusRequester = focusRequester,
                autoFocusEnabled = autoFocusEnabled, interactionSource = interactionSource,
                leadingIconHorizontalOffset = leadingIconHorizontalOffset,
            )
        }
        return
    }
    AppSearchFieldDock(
        modifier = modifier,
        backdrop = backdrop,
        liquidHeight = liquidHeight,
        isScrollInProgressProvider = isScrollInProgressProvider,
        captureShellForContent = captureShellForContent,
    ) { liquidChromeActive ->
        if (liquidChromeActive) {
            CompactLiquidSearchInput(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                height = liquidHeight,
                showClearAction = presentation != AppSearchFieldPresentation.TOP_BAR,
                onSearch = onSearch,
                onClear = onClear,
                focusRequester = focusRequester,
                autoFocusEnabled = autoFocusEnabled,
                interactionSource = interactionSource,
                leadingIconHorizontalOffset = leadingIconHorizontalOffset,
            )
        } else {
            // All three materials now share compact inline geometry. Theme colors and
            // native MD3/Miuix shapes remain owned by the compact input renderer.
            AppCompactInputField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = placeholder,
                height = liquidHeight,
                containerColor = com.android.purebilibili.core.ui.AppSurfaceTokens.searchContainer(),
                focusRequester = focusRequester,
                autoFocusEnabled = autoFocusEnabled,
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                leadingIcon = {
                    AppIcon(Icons.Default.Search, contentDescription = null,
                        modifier = Modifier.padding(start = leadingIconHorizontalOffset).size(22.dp))
                },
                trailingIcon = if (presentation != AppSearchFieldPresentation.TOP_BAR && query.isNotEmpty()) {
                    { AppIconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                        AppIcon(Icons.Default.Clear, contentDescription = "清除搜索内容")
                    } }
                } else null,
            )
        }
    }
}

// 设置搜索显式选用同一份编辑状态，各材质仍沿用自己的输入布局。
@Composable
internal fun AppLiquidAwareSearchTopBarField(
    state: TextFieldState,
    placeholder: String,
    onSearch: () -> Unit,
    autoFocusEnabled: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester, autoFocusEnabled) {
        if (autoFocusEnabled) requester.requestFocus()
    }
    val height = AppChromeSizeTokens.LiquidSearchFieldHeightDp.dp
    AppSearchFieldDock(
        modifier = modifier,
        backdrop = backdrop,
        liquidHeight = height,
        isScrollInProgressProvider = { false },
        captureShellForContent = false,
    ) { liquidChromeActive ->
        if (liquidChromeActive) {
            val contentColor = AppContentColors.primaryText()
            CompactLiquidSearchRow(height, leadingIconHorizontalOffset = 0.dp) { hintColor ->
                ProvideAppHapticScene(AppHapticScene.TEXT) {
                    BasicTextField(
                        state = state,
                        modifier = Modifier.weight(1f).focusRequester(requester).padding(vertical = 6.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        cursorBrush = SolidColor(contentColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { onSearch() },
                        decorator = { innerTextField ->
                            CompactLiquidSearchText(placeholder, state.text.isEmpty(), hintColor, innerTextField)
                        },
                    )
                }
            }
        } else {
            AppCompactSearchInputField(
                state = state,
                onSearch = onSearch,
                modifier = Modifier.fillMaxWidth().focusRequester(requester),
                placeholder = placeholder,
                height = height,
                leadingIcon = {
                    AppIcon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                },
            )
        }
    }
}

@Composable
private fun AppSearchFieldDock(
    modifier: Modifier,
    backdrop: Backdrop?,
    liquidHeight: Dp,
    isScrollInProgressProvider: () -> Boolean,
    captureShellForContent: Boolean,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    BottomBarMatchedReusableLiquidDock(
        shape = CircleShape,
        modifier = modifier,
        backdrop = backdrop,
        reuseEnabled = true,
        elasticFeedbackEnabled = true,
        elasticFeedbackStrength = APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH,
        useNeutralLiquidContainer = true,
        drawShellLens = true,
        shellLensIntensity = resolveFloatingDockGeometryScale(liquidHeight.value),
        isScrollInProgressProvider = isScrollInProgressProvider,
        captureShellForContent = captureShellForContent,
        content = content,
    )
}

@Composable
private fun CompactLiquidSearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    height: Dp,
    showClearAction: Boolean,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester?,
    autoFocusEnabled: Boolean,
    interactionSource: MutableInteractionSource?,
    leadingIconHorizontalOffset: Dp,
) {
    val requester = focusRequester ?: remember { FocusRequester() }
    LaunchedEffect(requester, autoFocusEnabled) {
        if (autoFocusEnabled) requester.requestFocus()
    }
    val contentColor = AppContentColors.primaryText()
    CompactLiquidSearchRow(height, leadingIconHorizontalOffset) { hintColor ->
        ProvideAppHapticScene(AppHapticScene.TEXT) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(requester).padding(vertical = 6.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                singleLine = true,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(contentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    CompactLiquidSearchText(placeholder, query.isEmpty(), hintColor, innerTextField)
                },
            )
        }
        if (showClearAction && query.isNotEmpty()) {
            AppIconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                AppIcon(Icons.Default.Clear, contentDescription = "清除搜索内容", tint = hintColor)
            }
        }
    }
}

@Composable
private fun CompactLiquidSearchRow(
    height: Dp,
    leadingIconHorizontalOffset: Dp,
    content: @Composable RowScope.(Color) -> Unit,
) {
    val hintColor = AppContentColors.secondaryText()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(
            Icons.Default.Search,
            contentDescription = null,
            tint = hintColor,
            modifier = Modifier.padding(start = leadingIconHorizontalOffset).size(24.dp),
        )
        content(hintColor)
    }
}

@Composable
private fun CompactLiquidSearchText(
    placeholder: String,
    isEmpty: Boolean,
    hintColor: Color,
    innerTextField: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.CenterStart) {
        if (isEmpty) {
            AppText(
                placeholder,
                color = hintColor,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        innerTextField()
    }
}
