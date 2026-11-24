package com.android.purebilibili.feature.onboarding

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.PureBiliBiliTheme
import com.android.purebilibili.core.theme.ThemeColorNames
import com.android.purebilibili.core.theme.ThemeColors
import com.android.purebilibili.core.theme.formatMd3CustomColorHex
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import com.android.purebilibili.core.ui.ProvideAppThemeConfig
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppHorizontalPager
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceGroup
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.FloatingBottomBar
import com.android.purebilibili.feature.home.components.FloatingBottomBarColors
import com.android.purebilibili.feature.home.components.FloatingBottomBarItem
import com.android.purebilibili.feature.home.components.FloatingBottomBarMode
import com.android.purebilibili.feature.home.components.resolveMaterialBottomBarIcon
import com.android.purebilibili.feature.settings.AppThemeMode
import com.android.purebilibili.feature.settings.miuixAmbientGradientBackground
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val ONBOARDING_GLASS_PREVIEW_ITEMS = listOf(
    BottomNavItem.HOME,
    BottomNavItem.DYNAMIC,
    BottomNavItem.HISTORY,
    BottomNavItem.PROFILE,
)

private val THEME_MODE_ORDER = listOf(
    AppThemeMode.FOLLOW_SYSTEM,
    AppThemeMode.LIGHT,
    AppThemeMode.DARK,
)

private val THEME_MODE_SEGMENT_LABELS = listOf("跟随系统", "浅色", "深色")

private val LIQUID_GLASS_SEGMENT_LABELS = listOf("液态玻璃", "原生材质")

private val HOME_DISPLAY_MODE_LABELS = listOf("双列网格", "单列大图")

private val BOTTOM_BAR_LABEL_MODE_LABELS = listOf("图标+文字", "仅图标", "仅文字")

/**
 * 首次启动与重放引导：
 * - 支持左右滑动翻页（AppHorizontalPager）、系统返回键回退、点击指示器跳转与跳过；
 * - 实时预览外观风格、深浅模式与主题配色、液态玻璃折射（Android 13+）以及首页与底栏布局；
 * - 优先复用 BottomBarLiquidSegmentedControl、AppPreferenceGroup、AppSwitchPreference、FloatingBottomBar。
 */
@Composable
fun FirstLaunchSetupScreen(
    onFinished: () -> Unit,
    onFinishedAndLogin: (() -> Unit)? = null,
    isReplay: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sdkInt = Build.VERSION.SDK_INT
    val steps = remember(sdkInt) { resolveFirstLaunchSetupSteps(sdkInt) }
    val currentUiStyle = LocalAppUiStyle.current
    val currentThemeConfig = LocalAppThemeConfig.current
    val systemInDarkTheme = isSystemInDarkTheme()
    val reduceMotion = LocalSystemReduceMotion.current

    var draft by remember {
        mutableStateOf(
            resolveInitialFirstLaunchSetupDraft(
                currentUiStyle = currentUiStyle,
                currentLiquidGlassEnabled = currentThemeConfig.liquidGlassEnabled,
                sdkInt = sdkInt,
            )
        )
    }
    var userEditedDraft by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(context, sdkInt) {
        val initialThemeMode = SettingsManager.getThemeMode(context).first()
        val initialDynamicColor = SettingsManager.getDynamicColor(context).first()
        val initialColorIndex = SettingsManager.getThemeColorIndex(context).first()
        val initialDisplayMode = SettingsManager.getDisplayMode(context).first()
        val initialBottomBarFloating = SettingsManager.getBottomBarFloating(context).first()
        val initialBottomBarLabelMode = SettingsManager.getBottomBarLabelMode(context).first()
        if (!userEditedDraft) {
            draft = resolveInitialFirstLaunchSetupDraft(
                currentUiStyle = currentUiStyle,
                currentLiquidGlassEnabled = currentThemeConfig.liquidGlassEnabled,
                sdkInt = sdkInt,
                currentThemeMode = initialThemeMode,
                currentDynamicColor = initialDynamicColor,
                currentThemeColorIndex = initialColorIndex,
                currentDisplayMode = initialDisplayMode,
                currentBottomBarFloating = initialBottomBarFloating,
                currentBottomBarLabelMode = initialBottomBarLabelMode,
            )
        }
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { steps.size })
    val currentStep = steps[pagerState.currentPage.coerceIn(0, steps.lastIndex)]
    val resolvedDarkTheme = resolveFirstLaunchSetupDarkTheme(
        themeMode = draft.themeMode,
        systemInDarkTheme = systemInDarkTheme,
    )

    val previewLiquidGlass = shouldAllowHomeChromeLiquidGlass(sdkInt) &&
        draft.liquidGlassEnabled &&
        currentStep != FirstLaunchSetupStep.THEME_STYLE
    val previewConfig = remember(currentThemeConfig, previewLiquidGlass) {
        currentThemeConfig.copy(
            liquidGlassEnabled = previewLiquidGlass,
            playerGlassEnabled = previewLiquidGlass,
            popupGlassEnabled = previewLiquidGlass,
            nowPlayingGlassEnabled = previewLiquidGlass,
        )
    }

    fun updateDraft(transform: (FirstLaunchSetupDraft) -> FirstLaunchSetupDraft) {
        userEditedDraft = true
        draft = transform(draft)
    }

    fun animateToPage(targetPage: Int) {
        val clamped = targetPage.coerceIn(0, steps.lastIndex)
        scope.launch {
            if (reduceMotion) {
                pagerState.scrollToPage(clamped)
            } else {
                pagerState.animateScrollToPage(clamped)
            }
        }
    }

    fun persistAndFinish(openLogin: Boolean = false) {
        if (isSaving) return
        isSaving = true
        scope.launch {
            try {
                if (shouldPersistLiquidGlassChoice(sdkInt)) {
                    SettingsManager.setThemePresentation(
                        context = context,
                        uiStyle = draft.uiStyle,
                        liquidGlassEnabled = draft.liquidGlassEnabled,
                    )
                } else {
                    SettingsManager.setUiStyle(context, draft.uiStyle)
                }
                SettingsManager.setThemeMode(context, draft.themeMode)
                if (shouldOfferDynamicColorOption(sdkInt) && draft.dynamicColor) {
                    SettingsManager.setDynamicColor(context, true)
                } else {
                    SettingsManager.setThemeColorIndex(context, draft.themeColorIndex)
                }
                context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putInt("display_mode", draft.displayMode)
                    .apply()
                SettingsManager.setDisplayMode(context, draft.displayMode)
                SettingsManager.setBottomBarFloating(context, draft.bottomBarFloating)
                SettingsManager.setBottomBarLabelMode(context, draft.bottomBarLabelMode)
                if (!isReplay) {
                    FirstLaunchSetupStore.markCompleted(context)
                }
                if (openLogin && onFinishedAndLogin != null) {
                    onFinishedAndLogin()
                } else {
                    onFinished()
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun skipOnboarding() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            try {
                if (!isReplay) {
                    FirstLaunchSetupStore.markCompleted(context)
                }
                onFinished()
            } finally {
                isSaving = false
            }
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0 || isReplay) {
        if (pagerState.currentPage > 0) {
            animateToPage(pagerState.currentPage - 1)
        } else if (isReplay) {
            onFinished()
        }
    }

    val previewCustomColorHex = remember(draft.themeColorIndex) {
        formatMd3CustomColorHex(ThemeColors.getOrElse(draft.themeColorIndex) { ThemeColors.first() })
    }

    PureBiliBiliTheme(
        uiStyle = draft.uiStyle,
        themeMode = draft.themeMode,
        darkTheme = resolvedDarkTheme,
        dynamicColor = draft.dynamicColor,
        themeColorIndex = draft.themeColorIndex,
        md3CustomColorHex = previewCustomColorHex,
        liquidGlassEnabled = previewLiquidGlass,
        themeTransitionEnabled = !reduceMotion,
        syncLauncherIcon = false,
    ) {
        ProvideAppThemeConfig(config = previewConfig) {
            FirstLaunchSetupContent(
                steps = steps,
                pagerState = pagerState,
                currentStep = currentStep,
                draft = draft,
                darkTheme = resolvedDarkTheme,
                sdkInt = sdkInt,
                reduceMotion = reduceMotion,
                isSaving = isSaving,
                isReplay = isReplay,
                onSelectUiStyle = { style -> updateDraft { it.copy(uiStyle = style) } },
                onSelectThemeMode = { mode -> updateDraft { it.copy(themeMode = mode) } },
                onToggleDynamicColor = { enabled -> updateDraft { it.copy(dynamicColor = enabled) } },
                onSelectThemeColorIndex = { idx -> updateDraft { it.copy(themeColorIndex = idx, dynamicColor = false) } },
                onSelectLiquidGlass = { enabled -> updateDraft { it.copy(liquidGlassEnabled = enabled) } },
                onSelectDisplayMode = { mode -> updateDraft { it.copy(displayMode = mode) } },
                onToggleBottomBarFloating = { floating -> updateDraft { it.copy(bottomBarFloating = floating) } },
                onSelectBottomBarLabelMode = { mode -> updateDraft { it.copy(bottomBarLabelMode = mode) } },
                onPreviousStep = { animateToPage(pagerState.currentPage - 1) },
                onSelectStepIndex = ::animateToPage,
                onNextOrFinish = {
                    if (pagerState.currentPage < steps.lastIndex) {
                        animateToPage(pagerState.currentPage + 1)
                    } else {
                        persistAndFinish()
                    }
                },
                onFinishAndLogin = if (!isReplay && onFinishedAndLogin != null) {
                    { persistAndFinish(openLogin = true) }
                } else null,
                onSkip = if (!isReplay) ::skipOnboarding else null,
                onCloseReplay = if (isReplay) onFinished else null,
            )
        }
    }
}

@Composable
private fun FirstLaunchSetupContent(
    steps: List<FirstLaunchSetupStep>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    currentStep: FirstLaunchSetupStep,
    draft: FirstLaunchSetupDraft,
    darkTheme: Boolean,
    sdkInt: Int,
    reduceMotion: Boolean,
    isSaving: Boolean,
    isReplay: Boolean,
    onSelectUiStyle: (AppUiStyle) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectThemeColorIndex: (Int) -> Unit,
    onSelectLiquidGlass: (Boolean) -> Unit,
    onSelectDisplayMode: (Int) -> Unit,
    onToggleBottomBarFloating: (Boolean) -> Unit,
    onSelectBottomBarLabelMode: (Int) -> Unit,
    onPreviousStep: () -> Unit,
    onSelectStepIndex: (Int) -> Unit,
    onNextOrFinish: () -> Unit,
    onFinishAndLogin: (() -> Unit)? = null,
    onSkip: (() -> Unit)?,
    onCloseReplay: (() -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current
    val localHazeState = rememberRecoverableHazeState()
    val blurSupported = shouldAllowRenderEffectBackedHazeEffect(sdkInt)
    val useMiuixAmbient = resolveFirstLaunchBackgroundUsesMiuixAmbient(
        uiStyle = draft.uiStyle,
        currentStep = currentStep,
        liquidGlassEnabled = draft.liquidGlassEnabled,
    )
    val stepIndex = pagerState.currentPage.coerceIn(0, steps.lastIndex)
    val hasPrevious = stepIndex > 0
    val hasNext = stepIndex < steps.lastIndex
    val standardFloatSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.standardSpec()
    val emphasizedFloatSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.emphasizedSpec()
    val expressiveFloatSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.expressiveSpec()

    val entranceState = remember(reduceMotion) {
        MutableTransitionState(reduceMotion).apply { targetState = true }
    }
    val entranceTransition = rememberTransition(entranceState, label = "setupEntrance")
    val entranceAlphaState = entranceTransition.animateFloat(
        transitionSpec = { emphasizedFloatSpec },
        label = "setupEntranceAlpha",
    ) { shown -> if (shown) 1f else 0f }
    val entranceOffsetPxState = entranceTransition.animateFloat(
        transitionSpec = {
            if (reduceMotion) snap()
            else if (draft.uiStyle == AppUiStyle.MIUIX) AppMotionTokens.folmeDefaultSpring()
            else AppMotionTokens.emphasizedSpec()
        },
        label = "setupEntranceOffset",
    ) { shown -> if (shown) 0f else 24f }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景柔光层：位于 hazeSource 下保持静态柔光，避免每帧触发 Haze 模糊重算；流动感由选中态 Miuix 样机承担。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = localHazeState)
        ) {
            Crossfade(
                targetState = useMiuixAmbient,
                animationSpec = emphasizedFloatSpec,
                label = "onboardingAmbientBackground",
            ) { miuixMode ->
                if (miuixMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                miuixAmbientGradientBackground(
                                    background = AppSurfaceTokens.background(),
                                    dark = darkTheme,
                                    motionAllowed = false,
                                )
                            )
                    )
                } else {
                    Md3AmbientBackground(
                        darkTheme = darkTheme,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .graphicsLayer {
                    alpha = entranceAlphaState.value
                    translationY = entranceOffsetPxState.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 极简顶栏：左侧返回、右侧跳过或关闭
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(40.dp),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = hasPrevious,
                    enter = if (reduceMotion) {
                        EnterTransition.None
                    } else {
                        fadeIn(standardFloatSpec) + scaleIn(standardFloatSpec, initialScale = 0.88f)
                    },
                    exit = if (reduceMotion) {
                        ExitTransition.None
                    } else {
                        fadeOut(expressiveFloatSpec) + scaleOut(expressiveFloatSpec, targetScale = 0.88f)
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    MinimalCircleButton(
                        hazeState = localHazeState,
                        blurSupported = blurSupported,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPreviousStep()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "上一步",
                            tint = AppContentColors.primaryText(),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                if (onCloseReplay != null) {
                    MinimalCircleButton(
                        hazeState = localHazeState,
                        blurSupported = blurSupported,
                        onClick = onCloseReplay,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = AppContentColors.primaryText(),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else if (onSkip != null) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hasNext,
                        enter = if (reduceMotion) EnterTransition.None else fadeIn(standardFloatSpec),
                        exit = if (reduceMotion) ExitTransition.None else fadeOut(expressiveFloatSpec),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        AppTextButton(
                            onClick = onSkip,
                            enabled = !isSaving,
                        ) {
                            AppText(
                                text = "跳过",
                                style = MaterialTheme.typography.labelLarge,
                                color = AppContentColors.secondaryText(),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 居中大标题
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(emphasizedFloatSpec) togetherWith fadeOut(expressiveFloatSpec)
                    }
                },
                label = "setupTitle",
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
            ) { step ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppText(
                        text = when (step) {
                            FirstLaunchSetupStep.THEME_STYLE -> "外观风格"
                            FirstLaunchSetupStep.APPEARANCE_COLOR -> "深浅与配色"
                            FirstLaunchSetupStep.LIQUID_GLASS -> "液态玻璃"
                            FirstLaunchSetupStep.HOME_LAYOUT -> "首页与底栏"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppContentColors.primaryText(),
                        textAlign = TextAlign.Center,
                    )
                    AppText(
                        text = when (step) {
                            FirstLaunchSetupStep.THEME_STYLE -> "选择你喜欢的界面语言，之后可随时在设置中更改"
                            FirstLaunchSetupStep.APPEARANCE_COLOR -> "实时预览深浅模式与主题强调色，打造顺眼的视觉基调"
                            FirstLaunchSetupStep.LIQUID_GLASS -> "为悬浮底栏与控件赋予通透光学折射质感"
                            FirstLaunchSetupStep.HOME_LAYOUT -> "挑选每天高频使用的视频排版与底部导航形态"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.secondaryText(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 中央可横向滑动的主舞台（同步门控非活跃页的装饰动画）
            AppHorizontalPager(
                state = pagerState,
                key = { page -> steps[page].name },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
            ) { page ->
                val pageVisualWorkActive = remember(pagerState, page) {
                    derivedStateOf {
                        pagerState.settledPage == page && !pagerState.isScrollInProgress
                    }
                }
                CompositionLocalProvider(
                    LocalVisualWorkAllowed provides (LocalVisualWorkAllowed.current && pageVisualWorkActive.value),
                ) {
                    when (steps[page]) {
                        FirstLaunchSetupStep.THEME_STYLE -> MinimalThemePickerStage(
                            selectedStyle = draft.uiStyle,
                            onSelectStyle = { style ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectUiStyle(style)
                            },
                            darkTheme = darkTheme,
                            reduceMotion = reduceMotion,
                        )

                        FirstLaunchSetupStep.APPEARANCE_COLOR -> AppearanceColorStage(
                            themeMode = draft.themeMode,
                            dynamicColor = draft.dynamicColor,
                            themeColorIndex = draft.themeColorIndex,
                            offerDynamicColor = shouldOfferDynamicColorOption(sdkInt),
                            reduceMotion = reduceMotion,
                            onSelectThemeMode = { mode ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectThemeMode(mode)
                            },
                            onToggleDynamicColor = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleDynamicColor(enabled)
                            },
                            onSelectThemeColorIndex = { idx ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectThemeColorIndex(idx)
                            },
                        )

                        FirstLaunchSetupStep.LIQUID_GLASS -> MinimalLiquidGlassStage(
                            liquidGlassEnabled = draft.liquidGlassEnabled,
                            onSelectLiquidGlass = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectLiquidGlass(enabled)
                            },
                            darkTheme = darkTheme,
                            reduceMotion = reduceMotion,
                        )

                        FirstLaunchSetupStep.HOME_LAYOUT -> HomeLayoutStage(
                            displayMode = draft.displayMode,
                            bottomBarFloating = draft.bottomBarFloating,
                            bottomBarLabelMode = draft.bottomBarLabelMode,
                            darkTheme = darkTheme,
                            reduceMotion = reduceMotion,
                            onSelectDisplayMode = { mode ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectDisplayMode(mode)
                            },
                            onToggleBottomBarFloating = { floating ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleBottomBarFloating(floating)
                            },
                            onSelectBottomBarLabelMode = { mode ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectBottomBarLabelMode(mode)
                            },
                        )
                    }
                }
            }

            // 可点击胶囊进度指示器
            if (steps.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                ) {
                    repeat(steps.size) { index ->
                        val active = index == stepIndex
                        val pillWidth by animateDpAsState(
                            targetValue = if (active) 20.dp else 6.dp,
                            animationSpec = if (reduceMotion) snap() else AppMotionTokens.standardSpec(),
                            label = "stepPillWidth",
                        )
                        val dotColorState = animateColorAsState(
                            targetValue = if (active) {
                                AppContentColors.accent()
                            } else {
                                AppContentColors.primaryText().copy(alpha = 0.22f)
                            },
                            animationSpec = if (reduceMotion) snap() else AppMotionTokens.standardSpec(),
                            label = "stepPillColor",
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(pillWidth)
                                .clip(CircleShape)
                                .drawBehind { drawRect(dotColorState.value) }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (!active) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectStepIndex(index)
                                        }
                                    },
                                )
                        )
                    }
                }
            }

            // 底部唯一主按钮
            val ctaLabel = when {
                hasNext -> "继续"
                isReplay -> "应用更改"
                else -> "开始使用"
            }
            AppButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNextOrFinish()
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .height(52.dp),
            ) {
                AnimatedContent(
                    targetState = ctaLabel,
                    transitionSpec = {
                        if (reduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(standardFloatSpec) togetherWith fadeOut(expressiveFloatSpec)
                        }
                    },
                    label = "setupCtaLabel",
                ) { label ->
                    AppText(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!hasNext && !isReplay && onFinishAndLogin != null) {
                AppTextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFinishAndLogin()
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .padding(top = 4.dp),
                ) {
                    AppText(
                        text = "完成并立即登录（解锁高画质与推荐）",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 步骤 1：两台极简抽象手机样机直接点击即选。
 */
@Composable
private fun MinimalThemePickerStage(
    selectedStyle: AppUiStyle,
    onSelectStyle: (AppUiStyle) -> Unit,
    darkTheme: Boolean,
    reduceMotion: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ThemeDeviceMockupOption(
                label = "Miuix",
                style = AppUiStyle.MIUIX,
                selected = selectedStyle == AppUiStyle.MIUIX,
                darkTheme = darkTheme,
                reduceMotion = reduceMotion,
                onClick = { onSelectStyle(AppUiStyle.MIUIX) },
                modifier = Modifier.weight(1f),
            )
            ThemeDeviceMockupOption(
                label = "Material 3",
                style = AppUiStyle.MATERIAL3,
                selected = selectedStyle == AppUiStyle.MATERIAL3,
                darkTheme = darkTheme,
                reduceMotion = reduceMotion,
                onClick = { onSelectStyle(AppUiStyle.MATERIAL3) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        val footnoteEnterSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.standardSpec()
        val footnoteExitSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.expressiveSpec()
        AnimatedContent(
            targetState = selectedStyle,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(footnoteEnterSpec) togetherWith fadeOut(footnoteExitSpec)
                }
            },
            label = "themeFootnote",
        ) { style ->
            AppText(
                text = if (style == AppUiStyle.MIUIX) {
                    "超椭圆连续曲率、弹簧回弹与流动柔光渐变"
                } else {
                    "Material You 动态取色与原生极简层次"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/**
 * 步骤 2：深浅模式与主题配色（复用 AppNativeSegmentedControl、AppPreferenceGroup、AppSwitchPreference）。
 */
@Composable
private fun AppearanceColorStage(
    themeMode: AppThemeMode,
    dynamicColor: Boolean,
    themeColorIndex: Int,
    offerDynamicColor: Boolean,
    reduceMotion: Boolean,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectThemeColorIndex: (Int) -> Unit,
) {
    val activeColorName = if (dynamicColor) {
        "跟随系统壁纸"
    } else {
        ThemeColorNames.getOrElse(themeColorIndex) { ThemeColorNames.first() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 深浅模式分段选择器（复用项目玻璃/原生自适应分段控件，非玻璃 Miuix 不叠加底栏 dock）
        val themeModeIndex = THEME_MODE_ORDER.indexOf(themeMode).coerceAtLeast(0)
        BottomBarLiquidSegmentedControl(
            items = THEME_MODE_SEGMENT_LABELS,
            selectedIndex = themeModeIndex,
            onSelected = { idx ->
                THEME_MODE_ORDER.getOrNull(idx)?.let(onSelectThemeMode)
            },
            forceEqualWidth = true,
            nativeSegmentedControl = true,
            centerNativeContent = true,
            drawMiuixNonGlassTrack = false,
            liquidGlassAdaptIndicatorToLabel = false,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 强调色预设卡片 + 可选壁纸动态取色
        AppPreferenceGroup(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = "主题强调色",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        text = activeColorName,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.accent(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FIRST_LAUNCH_ACCENT_PRESET_INDICES.forEach { presetIndex ->
                        val swatchColor = ThemeColors[presetIndex]
                        val selected = !dynamicColor && themeColorIndex == presetIndex
                        AccentColorSwatch(
                            color = swatchColor,
                            label = ThemeColorNames[presetIndex],
                            selected = selected,
                            reduceMotion = reduceMotion,
                            onClick = { onSelectThemeColorIndex(presetIndex) },
                        )
                    }
                }
            }

            if (offerDynamicColor) {
                AppPreferenceDivider()
                AppSwitchPreference(
                    icon = ImageVector.vectorResource(R.drawable.ms_wallpaper_24),
                    title = "跟随系统壁纸取色",
                    subtitle = "自动提取壁纸主色生成莫奈配色方案",
                    checked = dynamicColor,
                    onCheckedChange = onToggleDynamicColor,
                )
            }
        }
    }
}

@Composable
private fun AccentColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val scaleState = animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = if (reduceMotion) snap() else AppMotionTokens.folmeDefaultSpring(),
        label = "swatchScale",
    )
    val ringColorState = animateColorAsState(
        targetValue = if (selected) color else Color.Transparent,
        animationSpec = if (reduceMotion) snap() else AppMotionTokens.standardSpec(),
        label = "swatchRing",
    )

    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val ringColor = ringColorState.value
                if (ringColor.alpha > 0f) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.32f),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = if (reduceMotion) EnterTransition.None else fadeIn(AppMotionTokens.standardSpec()) + scaleIn(
                AppMotionTokens.standardSpec(),
                initialScale = 0.6f,
            ),
            exit = if (reduceMotion) ExitTransition.None else fadeOut(AppMotionTokens.expressiveSpec()) + scaleOut(
                AppMotionTokens.expressiveSpec(),
                targetScale = 0.6f,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ThemeDeviceMockupOption(
    label: String,
    style: AppUiStyle,
    selected: Boolean,
    darkTheme: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = AppContentColors.accent()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (reduceMotion) snap() else AppMotionTokens.folmePressSpring(),
        label = "mockupPressScale",
    )
    val selectionTransition = updateTransition(targetState = selected, label = "mockupSelection")
    val spatialFloatSpec: FiniteAnimationSpec<Float> = when {
        reduceMotion -> snap()
        style == AppUiStyle.MIUIX -> AppMotionTokens.folmeDefaultSpring()
        else -> AppMotionTokens.resolveStandardSpec(style)
    }
    val spatialDpSpec: FiniteAnimationSpec<Dp> = when {
        reduceMotion -> snap()
        style == AppUiStyle.MIUIX -> AppMotionTokens.folmeDefaultSpring()
        else -> AppMotionTokens.resolveStandardSpec(style)
    }
    val colorSpec: FiniteAnimationSpec<Color> = if (reduceMotion) snap() else AppMotionTokens.resolveStandardSpec(style)

    val scaleState = selectionTransition.animateFloat(
        transitionSpec = { spatialFloatSpec },
        label = "mockupScale",
    ) { isSelected -> if (isSelected) 1.0f else 0.96f }
    val frameAlphaState = selectionTransition.animateFloat(
        transitionSpec = { spatialFloatSpec },
        label = "mockupAlpha",
    ) { isSelected -> if (isSelected) 1.0f else 0.72f }
    val borderColorState = selectionTransition.animateColor(
        transitionSpec = { colorSpec },
        label = "mockupBorder",
    ) { isSelected ->
        if (isSelected) {
            accent
        } else {
            Color.White.copy(alpha = if (darkTheme) 0.14f else 0.55f)
        }
    }
    val borderWidthState = selectionTransition.animateDp(
        transitionSpec = { spatialDpSpec },
        label = "mockupBorderWidth",
    ) { isSelected -> if (isSelected) 2.5.dp else 1.dp }

    val deviceCornerRadius = if (style == AppUiStyle.MIUIX) 28.dp else 22.dp
    val deviceShape = RoundedCornerShape(deviceCornerRadius)
    val radioIconSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.resolveStandardSpec(style)

    Column(
        modifier = modifier
            .graphicsLayer {
                val combinedScale = scaleState.value * pressScaleState.value
                scaleX = combinedScale
                scaleY = combinedScale
                alpha = frameAlphaState.value
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(deviceShape)
                .drawWithContent {
                    drawContent()
                    val strokePx = borderWidthState.value.toPx()
                    val halfStroke = strokePx / 2f
                    val radiusPx = (deviceCornerRadius.toPx() - halfStroke).coerceAtLeast(0f)
                    drawRoundRect(
                        color = borderColorState.value,
                        topLeft = Offset(halfStroke, halfStroke),
                        size = Size(size.width - strokePx, size.height - strokePx),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = strokePx),
                    )
                }
        ) {
            if (style == AppUiStyle.MIUIX) {
                MiuixMiniDeviceCanvas(
                    darkTheme = darkTheme,
                    motionAllowed = resolveFirstLaunchMockupAmbientMotion(
                        style = style,
                        selected = selected,
                        reduceMotion = reduceMotion,
                    ),
                )
            } else {
                Md3MiniDeviceCanvas(darkTheme = darkTheme)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppText(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) AppContentColors.primaryText() else AppContentColors.secondaryText(),
            )
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(radioIconSpec) + scaleIn(radioIconSpec, initialScale = 0.8f)) togetherWith
                            (fadeOut(radioIconSpec) + scaleOut(radioIconSpec, targetScale = 0.8f))
                    }
                },
                label = "mockupRadioIcon",
            ) { isSelected ->
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) accent else AppContentColors.primaryText().copy(alpha = 0.28f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Miuix 样机内部：招牌三色柔光渐变 + 超椭圆大圆角卡片 + 紧凑悬浮胶囊底栏。 */
@Composable
private fun MiuixMiniDeviceCanvas(
    darkTheme: Boolean,
    motionAllowed: Boolean,
) {
    val bg = if (darkTheme) Color(0xFF121218) else Color(0xFFF6F6FA)
    val cardColor = if (darkTheme) Color(0xFF1F212B).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.90f)
    val accent = AppContentColors.accent()
    val mutedBar = if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                miuixAmbientGradientBackground(
                    background = bg,
                    dark = darkTheme,
                    motionAllowed = motionAllowed,
                )
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(if (darkTheme) Color.White.copy(alpha = 0.82f) else Color(0xFF1A1B20).copy(alpha = 0.82f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(CircleShape)
                        .background(cardColor)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor)
                        .padding(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.72f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(mutedBar)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardColor)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(cardColor)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.28f))
                )
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(mutedBar)
                    )
                }
            }
        }
    }
}

/** Material 3 样机内部：HCT 莫奈色板层次 + 标准几何圆角 + 药丸选中态导航栏。 */
@Composable
private fun Md3MiniDeviceCanvas(
    darkTheme: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface
    val card = scheme.surfaceContainer
    val primaryContainer = scheme.primaryContainer
    val secondaryContainer = scheme.secondaryContainer
    val tertiaryContainer = scheme.tertiaryContainer
    val mutedBar = scheme.onSurface.copy(alpha = 0.16f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHigh)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(primaryContainer)
                        .padding(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(scheme.onPrimaryContainer.copy(alpha = 0.72f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(scheme.onPrimaryContainer.copy(alpha = 0.28f))
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tertiaryContainer)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(card)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(card)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(secondaryContainer)
                )
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(mutedBar)
                    )
                }
            }
        }
    }
}

/**
 * 步骤 3（Android 13+）：高清全出血折射舞台 + 复用 AppNativeSegmentedControl 切换材质。
 */
@Composable
private fun MinimalLiquidGlassStage(
    liquidGlassEnabled: Boolean,
    onSelectLiquidGlass: (Boolean) -> Unit,
    darkTheme: Boolean,
    reduceMotion: Boolean,
) {
    var previewTabIndex by remember { mutableIntStateOf(0) }
    var userInteracted by remember { mutableStateOf(false) }
    val previewGate = rememberDecorativeAnimationGate()
    val backdropSource = rememberChromeBackdropSource()
    val autoDemoActive = shouldRunLiquidGlassPreviewAutoDemo(
        liquidGlassEnabled = liquidGlassEnabled,
        reduceMotion = reduceMotion,
        userInteracted = userInteracted,
        gateActive = previewGate.active,
    )
    LaunchedEffect(autoDemoActive) {
        if (!autoDemoActive) return@LaunchedEffect
        delay(700L)
        for (targetIndex in intArrayOf(1, 2, 0)) {
            previewTabIndex = targetIndex
            delay(1150L)
        }
    }

    val artworkRes = if (darkTheme) {
        R.drawable.liquid_glass_preview_prismatic
    } else {
        R.drawable.liquid_glass_preview_sky
    }
    val glassContainerColor = if (darkTheme) {
        Color(0xFF1A1E28).copy(alpha = 0.48f)
    } else {
        Color.White.copy(alpha = 0.56f)
    }
    val solidContainerColor = AppSurfaceTokens.cardContainer()
    val accentColor = AppContentColors.accent()
    val targetUnselectedContentColor = if (liquidGlassEnabled) {
        if (darkTheme) Color.White.copy(alpha = 0.80f) else Color(0xFF1F2430).copy(alpha = 0.76f)
    } else {
        AppContentColors.secondaryText()
    }
    val colorSpec: FiniteAnimationSpec<Color> = if (reduceMotion) snap() else AppMotionTokens.standardSpec()
    val containerColor by animateColorAsState(
        targetValue = if (liquidGlassEnabled) glassContainerColor else solidContainerColor,
        animationSpec = colorSpec,
        label = "glassPreviewContainer",
    )
    val indicatorColor by animateColorAsState(
        targetValue = accentColor.copy(alpha = if (liquidGlassEnabled) 0.24f else 0.16f),
        animationSpec = colorSpec,
        label = "glassPreviewIndicator",
    )
    val unselectedContentColor by animateColorAsState(
        targetValue = targetUnselectedContentColor,
        animationSpec = colorSpec,
        label = "glassPreviewContent",
    )
    val stageShape = AppShapes.container(ContainerLevel.Floating)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp)
                .then(previewGate.modifier)
                .clip(stageShape)
                .border(
                    BorderStroke(
                        0.75.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (darkTheme) 0.26f else 0.60f),
                                Color.White.copy(alpha = 0.04f),
                            )
                        ),
                    ),
                    stageShape,
                )
        ) {
            Image(
                painter = painterResource(artworkRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(backdropSource.modifier),
            )

            key(liquidGlassEnabled) {
                FloatingBottomBar(
                    selectedIndex = { previewTabIndex },
                    onSelected = {
                        userInteracted = true
                        previewTabIndex = it
                    },
                    onIndicatorDragStateChanged = { dragging ->
                        if (dragging) userInteracted = true
                    },
                    backdrop = if (liquidGlassEnabled) backdropSource.backdrop else null,
                    tabsCount = ONBOARDING_GLASS_PREVIEW_ITEMS.size,
                    mode = if (liquidGlassEnabled) {
                        FloatingBottomBarMode.LiquidGlass
                    } else {
                        FloatingBottomBarMode.None
                    },
                    contentHorizontalPadding = com.android.purebilibili.core.ui.resolveLiquidGlassIndicatorHorizontalInsetDp(
                        requestedInsetDp = 4f,
                        liquidGlassEnabled = liquidGlassEnabled,
                    ).dp,
                    colors = FloatingBottomBarColors(
                        containerColor = containerColor,
                        indicatorColor = indicatorColor,
                        contentColor = unselectedContentColor,
                        activeContentColor = accentColor,
                    ),
                    shellHeight = 54.dp,
                    indicatorHeight = 44.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    ONBOARDING_GLASS_PREVIEW_ITEMS.forEachIndexed { index, item ->
                        val selected = index == previewTabIndex
                        FloatingBottomBarItem(
                            onClick = {
                                userInteracted = true
                                previewTabIndex = index
                            },
                            selected = selected,
                            itemIndex = index,
                        ) {
                            Icon(
                                imageVector = resolveMaterialBottomBarIcon(item = item, selected = selected),
                                contentDescription = item.label,
                                tint = if (selected) accentColor else unselectedContentColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        BottomBarLiquidSegmentedControl(
            items = LIQUID_GLASS_SEGMENT_LABELS,
            selectedIndex = if (liquidGlassEnabled) 0 else 1,
            onSelected = { idx -> onSelectLiquidGlass(idx == 0) },
            forceEqualWidth = true,
            nativeSegmentedControl = true,
            centerNativeContent = true,
            drawMiuixNonGlassTrack = false,
            liquidGlassAdaptIndicatorToLabel = false,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))

        val footnoteEnterSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.standardSpec()
        val footnoteExitSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.expressiveSpec()
        AnimatedContent(
            targetState = liquidGlassEnabled,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(footnoteEnterSpec) togetherWith fadeOut(footnoteExitSpec)
                }
            },
            label = "glassFootnote",
        ) { enabled ->
            AppText(
                text = if (enabled) {
                    "点按或拖动上方底栏体验实时光学折射与透光层次"
                } else {
                    "保持纯粹原生实色表面，更轻盈省电"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 步骤 4：首页与底栏高频布局设置 + 实时微缩预览（复用 BottomBarLiquidSegmentedControl / AppPreferenceGroup / AppSwitchPreference）。
 */
@Composable
private fun HomeLayoutStage(
    displayMode: Int,
    bottomBarFloating: Boolean,
    bottomBarLabelMode: Int,
    darkTheme: Boolean,
    reduceMotion: Boolean,
    onSelectDisplayMode: (Int) -> Unit,
    onToggleBottomBarFloating: (Boolean) -> Unit,
    onSelectBottomBarLabelMode: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeLayoutMiniPreview(
            displayMode = displayMode,
            bottomBarFloating = bottomBarFloating,
            bottomBarLabelMode = bottomBarLabelMode,
            darkTheme = darkTheme,
            reduceMotion = reduceMotion,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        BottomBarLiquidSegmentedControl(
            items = HOME_DISPLAY_MODE_LABELS,
            selectedIndex = if (displayMode == 1) 1 else 0,
            onSelected = onSelectDisplayMode,
            forceEqualWidth = true,
            nativeSegmentedControl = true,
            centerNativeContent = true,
            drawMiuixNonGlassTrack = false,
            liquidGlassAdaptIndicatorToLabel = false,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))

        AppPreferenceGroup(modifier = Modifier.fillMaxWidth()) {
            AppSwitchPreference(
                icon = ImageVector.vectorResource(R.drawable.ms_bottom_panel_open_24),
                title = "悬浮胶囊底栏",
                subtitle = if (bottomBarFloating) "悬浮居中胶囊样式，留出通透视野" else "贴底铺满样式，单手切换更稳妥",
                checked = bottomBarFloating,
                onCheckedChange = onToggleBottomBarFloating,
            )
            AppPreferenceDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ms_label_24),
                        contentDescription = null,
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(20.dp),
                    )
                    AppText(
                        text = "底栏标签显示",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = AppContentColors.primaryText(),
                    )
                }
                BottomBarLiquidSegmentedControl(
                    items = BOTTOM_BAR_LABEL_MODE_LABELS,
                    selectedIndex = bottomBarLabelMode.coerceIn(0, 2),
                    onSelected = onSelectBottomBarLabelMode,
                    forceEqualWidth = true,
                    nativeSegmentedControl = true,
                    centerNativeContent = true,
                    drawMiuixNonGlassTrack = false,
                    liquidGlassAdaptIndicatorToLabel = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeLayoutMiniPreview(
    displayMode: Int,
    bottomBarFloating: Boolean,
    bottomBarLabelMode: Int,
    darkTheme: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = AppContentColors.accent()
    val surfaceColor = AppSurfaceTokens.cardContainer().copy(alpha = if (darkTheme) 0.78f else 0.90f)
    val cardFill = AppContentColors.primaryText().copy(alpha = if (darkTheme) 0.10f else 0.06f)
    val titleBar = AppContentColors.primaryText().copy(alpha = if (darkTheme) 0.28f else 0.20f)
    val subBar = AppContentColors.primaryText().copy(alpha = if (darkTheme) 0.14f else 0.10f)
    val dpSpec: FiniteAnimationSpec<Dp> = if (reduceMotion) snap() else AppMotionTokens.standardSpec()
    val barHorizontalPadding by animateDpAsState(
        targetValue = if (bottomBarFloating) 16.dp else 0.dp,
        animationSpec = dpSpec,
        label = "previewBarHorizontalPadding",
    )
    val barBottomPadding by animateDpAsState(
        targetValue = if (bottomBarFloating) 10.dp else 0.dp,
        animationSpec = dpSpec,
        label = "previewBarBottomPadding",
    )
    val barCornerRadius by animateDpAsState(
        targetValue = if (bottomBarFloating) 18.dp else 0.dp,
        animationSpec = dpSpec,
        label = "previewBarCornerRadius",
    )
    val enterSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.standardSpec()
    val exitSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else AppMotionTokens.expressiveSpec()
    val previewShape = AppShapes.container(ContainerLevel.Card)

    AppSurface(
        color = surfaceColor,
        shape = previewShape,
        border = BorderStroke(0.75.dp, AppContentColors.primaryText().copy(alpha = if (darkTheme) 0.12f else 0.08f)),
        modifier = modifier.height(184.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = displayMode == 1,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(enterSpec) togetherWith fadeOut(exitSpec)
                    }
                },
                label = "homeLayoutPreviewGrid",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 56.dp),
            ) { isSingleColumn ->
                if (isSingleColumn) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(74.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardFill)
                                .padding(10.dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.58f)
                                        .height(7.dp)
                                        .clip(CircleShape)
                                        .background(titleBar)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.36f)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(subBar)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cardFill)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                repeat(2) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cardFill)
                                            .padding(7.dp),
                                        verticalArrangement = Arrangement.Bottom,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.72f)
                                                .height(6.dp)
                                                .clip(CircleShape)
                                                .background(titleBar)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 实时联动底栏预览（悬浮胶囊 vs 贴底导航 + 图标/文字组合）
            val barBg = if (bottomBarFloating) {
                AppSurfaceTokens.groupedListContainer().copy(alpha = 0.94f)
            } else {
                AppSurfaceTokens.groupedListContainer()
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = barHorizontalPadding)
                    .padding(bottom = barBottomPadding)
                    .height(40.dp)
                    .clip(RoundedCornerShape(barCornerRadius))
                    .background(barBg)
                    .border(
                        width = if (bottomBarFloating) 0.5.dp else 0.dp,
                        color = if (bottomBarFloating) {
                            AppContentColors.primaryText().copy(alpha = 0.10f)
                        } else Color.Transparent,
                        shape = RoundedCornerShape(barCornerRadius),
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                ONBOARDING_GLASS_PREVIEW_ITEMS.forEachIndexed { index, item ->
                    val active = index == 0
                    val tint = if (active) accent else AppContentColors.secondaryText()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (bottomBarLabelMode != 2) {
                            Icon(
                                imageVector = resolveMaterialBottomBarIcon(item = item, selected = active),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(if (bottomBarLabelMode == 1) 17.dp else 14.dp),
                            )
                        }
                        if (bottomBarLabelMode != 1) {
                            AppText(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalCircleButton(
    hazeState: HazeState,
    blurSupported: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bg = AppSurfaceTokens.cardContainer().copy(alpha = if (blurSupported) 0.36f else 0.78f)
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (blurSupported) {
                    Modifier.unifiedBlur(
                        hazeState = hazeState,
                        enabled = true,
                        shape = CircleShape,
                        surfaceType = BlurSurfaceType.GENERIC,
                    )
                } else Modifier
            )
            .background(bg)
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun Md3AmbientBackground(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val background = scheme.background
    val primaryGlow = scheme.primaryContainer
    val tertiaryGlow = scheme.tertiaryContainer
    val secondaryGlow = scheme.secondaryContainer
    Box(
        modifier = modifier.drawWithCache {
            val wash = Brush.verticalGradient(
                0f to primaryGlow.copy(alpha = if (darkTheme) 0.36f else 0.48f),
                0.5f to secondaryGlow.copy(alpha = if (darkTheme) 0.18f else 0.28f),
                1f to background,
            )
            val cornerGlow = Brush.radialGradient(
                colors = listOf(
                    tertiaryGlow.copy(alpha = if (darkTheme) 0.42f else 0.56f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.85f, size.height * 0.15f),
                radius = size.maxDimension * 0.70f,
            )
            onDrawBehind {
                drawRect(background)
                drawRect(wash)
                drawRect(cornerGlow)
            }
        }
    )
}
