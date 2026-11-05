package com.android.purebilibili.feature.search

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppIconButtonDefaults
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorationEnabled
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.feature.home.components.HomeTopChromeRenderMode
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig
import com.android.purebilibili.feature.home.components.homeTopBottomBarMatchedSurface
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import com.android.purebilibili.feature.home.components.resolveHomeTopEdgeButtonShape
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.rememberAppSearchIcon
import androidx.compose.ui.text.style.TextOverflow
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.motion.continuityTween

// 顶部搜索栏：输入状态持有选区，搜索逻辑只订阅文字变化。
@Composable
fun SearchTopBar(
    textFieldState: androidx.compose.foundation.text.input.TextFieldState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onClearQuery: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    placeholder: String = resolveSearchDefaultPlaceholder(),
    suggestedKeyword: String = "",
    focusRequester: androidx.compose.ui.focus.FocusRequester = remember { androidx.compose.ui.focus.FocusRequester() },
    autoFocusEnabled: Boolean = true,
    reducedMotionBudget: Boolean = false,
    entryMotionSpec: SearchEntryMotionSpec? = null,
    entryMotionKey: Int = 0,
    onEntryMotionFinished: (Int) -> Unit = {},
    exitMotionSpec: SearchEntryMotionSpec? = null,
    exitMotionKey: Int = 0,
    onExitMotionFinished: (Int) -> Unit = {},
    isScrollInProgressProvider: () -> Boolean = { false },
    liquidGlassEnabled: Boolean = false,
    miuixBackdrop: MiuixBackdrop? = null,
    includeStatusBarPadding: Boolean = true,
    trailingActions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val componentSpacing = resolveSearchComponentSpacingSpec(LocalAppUiStyle.current)
    val topChromePolicy = rememberAppTopChromePolicy()
    val chromeSpec = remember(topChromePolicy) { resolveSearchChromeVisualSpec(topChromePolicy) }
    val inputHeightDp = rememberSearchInputHeightDp(chromeSpec.inputHeightDp)
    val topBarRowMinHeightDp = remember(inputHeightDp) {
        resolveSearchTopBarRowMinHeightDp(inputHeightDp)
    }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val inputText = textFieldState.text.toString()
    val backIcon = rememberAppBackIcon()
    val density = LocalDensity.current
    val entryMotionProgress = remember { Animatable(1f) }
    val latestOnEntryMotionFinished by rememberUpdatedState(onEntryMotionFinished)
    val latestOnExitMotionFinished by rememberUpdatedState(onExitMotionFinished)
    val backLabel = stringResource(R.string.common_back)
    val resolvedSubmitKeyword = remember(inputText, suggestedKeyword) {
        resolveSearchSubmitKeyword(
            query = inputText,
            suggestedKeyword = suggestedKeyword
        )
    }
    val canSubmit = resolvedSubmitKeyword.isNotBlank()
    val liquidGlassRenderConfig = LocalLiquidGlassRenderConfig.current
    val glassActive = liquidGlassEnabled && miuixBackdrop != null && !isLowBlurBudgetForced()
    val glassRenderMode = if (glassActive) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
    } else {
        HomeTopChromeRenderMode.PLAIN
    }
    val backElasticState = com.android.purebilibili.core.ui.components.rememberAppElasticPressState()
    fun Modifier.searchTopChromeGlass(
        shape: androidx.compose.ui.graphics.Shape,
        controlHeightDp: Int,
        interactionState: com.android.purebilibili.core.ui.components.AppElasticPressState? = null,
    ): Modifier {
        if (!glassActive) return this
        return homeTopBottomBarMatchedSurface(
            renderMode = glassRenderMode,
            shape = shape,
            hazeState = null,
            miuixBackdrop = miuixBackdrop,
            liquidGlassStyle = com.android.purebilibili.core.store.LiquidGlassStyle.CLASSIC,
            liquidGlassTuning = liquidGlassRenderConfig.tuning,
            liquidGlassPreset = liquidGlassRenderConfig.preset,
            motionTier = MotionTier.Normal,
            isScrolling = isScrollInProgressProvider(),
            isTransitionRunning = false,
            forceLowBlurBudget = false,
            drawShellLens = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(controlHeightDp.toFloat()),
            interactionState = interactionState,
        )
    }

    LaunchedEffect(entryMotionKey, entryMotionSpec) {
        val spec = entryMotionSpec
        if (spec == null) {
            entryMotionProgress.snapTo(1f)
            return@LaunchedEffect
        }
        entryMotionProgress.snapTo(0f)
        if (spec.durationMillis <= 0) {
            entryMotionProgress.snapTo(1f)
        } else {
            entryMotionProgress.animateTo(
                targetValue = 1f,
                animationSpec = continuityTween(spec.durationMillis)
            )
        }
        latestOnEntryMotionFinished(entryMotionKey)
    }
    LaunchedEffect(exitMotionKey, exitMotionSpec) {
        val spec = exitMotionSpec
        if (exitMotionKey <= 0 || spec == null) return@LaunchedEffect
        if (spec.durationMillis <= 0) {
            entryMotionProgress.snapTo(0f)
        } else {
            entryMotionProgress.animateTo(
                targetValue = 0f,
                animationSpec = continuityTween(spec.durationMillis)
            )
        }
        latestOnExitMotionFinished(exitMotionKey)
    }
    LaunchedEffect(autoFocusEnabled, focusRequester) {
        if (autoFocusEnabled) {
            // Focus is available after composition; do not queue it behind an app timer.
            runCatching { focusRequester.requestFocus() }
        }
    }
    val activeMotionSpec = entryMotionSpec ?: exitMotionSpec
    val entryMotionModifier = if (activeMotionSpec != null) {
        Modifier.graphicsLayer {
            val progress = entryMotionProgress.value
            val spec = activeMotionSpec
            alpha = lerp(spec.initialAlpha, 1f, progress)
            scaleX = lerp(spec.initialScale, 1f, progress)
            scaleY = lerp(spec.initialScale, 1f, progress)
            translationY = with(density) {
                spec.initialTranslationYDp.dp.toPx()
            } * (1f - progress)
            transformOrigin = TransformOrigin(
                spec.transformOriginPivotX,
                spec.transformOriginPivotY
            )
        }
    } else {
        Modifier
    }

    // 外层只负责排列；MD3、Miuix 与玻璃控件都可把弹性绘制伸到布局边界外。
    // 输入框与按钮自己保留圆角裁切，不用整个顶栏的 Surface 再裁一次。
    Box(modifier = modifier.fillMaxWidth()) {
        Column {
            if (includeStatusBarPadding) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }

            Row(
                modifier = Modifier
                    .responsiveContentWidth()
                    .heightIn(min = topBarRowMinHeightDp.dp)
                    .padding(horizontal = componentSpacing.horizontalPaddingDp.dp, vertical = (SEARCH_TOP_BAR_VERTICAL_PADDING_DP / 2).dp)
                    .padding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal).asPaddingValues())
                    .then(entryMotionModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val inputShape = resolveSearchInputShape(topChromePolicy)
                val actionShape = resolveHomeTopEdgeButtonShape(topChromePolicy)
                SearchTopBarIconButton(
                    onClick = onBack,
                    elasticState = backElasticState,
                    shape = actionShape,
                    transformInBackdrop = glassActive,
                    modifier = Modifier
                        .size(chromeSpec.clearActionSizeDp.dp)
                        .searchTopChromeGlass(actionShape, chromeSpec.clearActionSizeDp, backElasticState)
                ) {
                    AppIcon(
                        backIcon,
                        contentDescription = backLabel,
                        tint = AppContentColors.primaryText(),
                        modifier = Modifier.size(chromeSpec.actionIconSizeDp.dp)
                    )
                }

                Spacer(modifier = Modifier.width(componentSpacing.actionGapDp.dp))

                val containerColor = if (glassActive) {
                    Color.Transparent
                } else if (chromeSpec.useFilledSearchAction) {
                    AppSurfaceTokens.surfaceContainerHigh()
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
                SearchTopBarInputField(
                    state = textFieldState,
                    onSearch = {
                        if (canSubmit) onSearch(resolvedSubmitKeyword)
                    },
                    onClear = onClearQuery,
                    placeholder = placeholder,
                    containerColor = containerColor,
                    fieldShape = inputShape,
                    focusRequester = focusRequester,
                    interactionSource = searchInteractionSource,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(inputHeightDp.dp)
                        // 编辑区保持固定坐标，点按定位光标不触发整框回弹和按压叠光。
                        .searchTopChromeGlass(inputShape, inputHeightDp)
                        .onFocusChanged { onFocusChanged(it.isFocused) }
                )

                if (trailingActions != null) {
                    Spacer(modifier = Modifier.width(chromeSpec.horizontalGapDp.dp))
                    trailingActions()
                } else {
                    val submitElasticState = com.android.purebilibili.core.ui.components.rememberAppElasticPressState()
                    Spacer(modifier = Modifier.width(chromeSpec.horizontalGapDp.dp))
                    SearchTopBarIconButton(
                        onClick = { onSearch(resolvedSubmitKeyword) },
                        elasticState = submitElasticState,
                        shape = actionShape,
                        transformInBackdrop = glassActive,
                        enabled = canSubmit,
                        modifier = Modifier
                            .size(chromeSpec.submitActionSizeDp.dp)
                            .searchTopChromeGlass(actionShape, chromeSpec.submitActionSizeDp, submitElasticState),
                    ) {
                        AppIcon(
                            imageVector = rememberAppSearchIcon(),
                            contentDescription = stringResource(R.string.common_search),
                            tint = AppContentColors.secondaryText().copy(alpha = if (canSubmit) 1f else 0.35f),
                            modifier = Modifier.size(chromeSpec.actionIconSizeDp.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTopBarIconButton(
    onClick: () -> Unit,
    elasticState: com.android.purebilibili.core.ui.components.AppElasticPressState,
    shape: androidx.compose.ui.graphics.Shape,
    transformInBackdrop: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elasticFeedbackEnabled: Boolean = enabled,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.sizeIn(
            minWidth = AppChromeSizeTokens.MinimumTouchTarget,
            minHeight = AppChromeSizeTokens.MinimumTouchTarget,
        ),
        contentAlignment = Alignment.Center,
    ) {
        // The surface owns motion; never move only the glyph inside a fixed clipping shell.
        Box(
            modifier = modifier.appElasticPress(
                enabled = elasticFeedbackEnabled,
                state = elasticState,
                transformInBackdrop = transformInBackdrop,
                highlightShape = shape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.android.purebilibili.core.ui.components.LocalElasticPressEnabled provides false,
            ) {
                AppIconButton(
                    onClick = onClick,
                    // drawBackdrop already transforms the surface and its descendants.
                    modifier = Modifier.matchParentSize(),
                    enabled = enabled,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SearchTopBarInputField(
    state: androidx.compose.foundation.text.input.TextFieldState,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    containerColor: Color,
    fieldShape: androidx.compose.ui.graphics.Shape,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Use AppSurfaceTokens so capsule text keeps contrast in both themes.
    val contentColor = AppSurfaceTokens.onSurface()
    val placeholderColor = AppSurfaceTokens.onSurfaceVariantSummary()
    val focusBorderColor = AppSurfaceTokens.primary()
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = contentColor,
        // Explicit line height avoids type-scale clipping in single-line fields.
        lineHeight = SEARCH_INPUT_LINE_HEIGHT_SP.sp
    )
    val cursorBrush = androidx.compose.ui.graphics.SolidColor(focusBorderColor)

    // 所有材质共用同一个编辑状态，选区与输入法组合文字不通过父级重组回传。
    androidx.compose.foundation.text.BasicTextField(
        state = state,
        modifier = modifier
            .focusRequester(focusRequester)
            .then(
                if (containerColor.alpha > 0.001f) {
                    Modifier
                        .clip(fieldShape)
                        .background(containerColor, fieldShape)
                } else {
                    Modifier
                }
            )
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = focusBorderColor,
                        shape = fieldShape
                    )
                } else {
                    Modifier
                }
            ),
        textStyle = textStyle,
        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
        cursorBrush = cursorBrush,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { onSearch() },
        interactionSource = interactionSource,
        decorator = { innerTextField ->
            // This is the search page's shared edit affordance, not a glass geometry override.
            // Native MD3/Miuix and liquid chrome keep their existing field dimensions and padding.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) {
                        AppText(
                            text = placeholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = textStyle,
                            color = placeholderColor
                        )
                    }
                    // Keep text contrast inside the transparent liquid capsule.
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides contentColor
                    ) {
                        innerTextField()
                    }
                }
                if (state.text.isNotEmpty()) {
                    // The field owns the only surface; never decorate its clear icon as a top-bar dock.
                    CompositionLocalProvider(LocalAppTopBarIconDecorationEnabled provides false) {
                        AppIconButton(
                            onClick = onClear,
                            modifier = Modifier.size(32.dp),
                            colors = AppIconButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                        ) {
                            AppIcon(
                                imageVector = rememberAppClearIcon(),
                                contentDescription = stringResource(R.string.common_clear),
                                tint = placeholderColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    )
}
