package com.android.purebilibili.feature.settings.ui

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AppTopBarStyle
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AdaptivePlainTooltipBox
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.LocalSetBottomBarVisible
import com.android.purebilibili.core.ui.appTopBarNestedScroll
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.rememberAppTopBarCollapseBehavior
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppPreferenceIconTreatment
import com.android.purebilibili.core.ui.components.AppPreferenceGroupPresentation
import com.android.purebilibili.core.ui.components.LocalAppPreferenceIconTreatment
import com.android.purebilibili.core.ui.components.LocalAppPreferenceGroupPresentation
import com.android.purebilibili.feature.settings.SettingsBottomBarScrollState
import com.android.purebilibili.feature.settings.SettingsBottomBarScrollTracker
import com.android.purebilibili.feature.settings.SettingsPageScrollHost
import com.android.purebilibili.feature.settings.reduceSettingsBottomBarScroll
import kotlinx.coroutines.flow.distinctUntilChanged

internal val LocalSettingsTopContentPadding = staticCompositionLocalOf { 0.dp }

@Composable
internal fun settingsScrollContentPadding(
    extraTop: androidx.compose.ui.unit.Dp = 0.dp,
    extraBottom: androidx.compose.ui.unit.Dp = 0.dp,
    extraHorizontal: androidx.compose.ui.unit.Dp = 0.dp,
    extraVertical: androidx.compose.ui.unit.Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = extraHorizontal,
    end = extraHorizontal,
    top = LocalSettingsTopContentPadding.current + extraTop + extraVertical,
    bottom = extraBottom + extraVertical,
)

@Composable
internal fun SettingsBottomBarScrollEffect(listState: LazyListState) {
    val setBottomBarVisible = LocalSetBottomBarVisible.current
    val density = LocalDensity.current
    val topRevealThresholdPx = with(density) { 24.dp.roundToPx() }
    val directionThresholdPx = with(density) { 32.dp.roundToPx() }

    LaunchedEffect(
        listState,
        setBottomBarVisible,
        topRevealThresholdPx,
        directionThresholdPx,
    ) {
        var tracker = SettingsBottomBarScrollTracker(
            previousState = SettingsBottomBarScrollState(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            ),
        )
        snapshotFlow {
            SettingsBottomBarScrollState(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
            .distinctUntilChanged()
            .collect { currentState ->
                val update = reduceSettingsBottomBarScroll(
                    tracker = tracker,
                    currentState = currentState,
                    topRevealThresholdPx = topRevealThresholdPx,
                    directionThresholdPx = directionThresholdPx,
                )
                update.bottomBarVisible?.let(setBottomBarVisible)
                tracker = update.tracker
            }
    }

    DisposableEffect(setBottomBarVisible) {
        onDispose { setBottomBarVisible(true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    scrollHost: SettingsPageScrollHost = SettingsPageScrollHost.LazyColumn,
    topBarBlurEnabled: Boolean? = null,
    externalContentHandlesTopPadding: Boolean = false,
    topBarStyle: AppTopBarStyle = AppTopBarStyle.CENTERED,
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    lazyListContent: (LazyListScope.() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    if (scrollHost == SettingsPageScrollHost.LazyColumn) {
        SettingsBottomBarScrollEffect(listState)
    }
    val resolvedBottomContentPadding = maxOf(
        bottomContentPadding,
        LocalBottomBarContentPadding.current,
    )
    val appThemeConfig = LocalAppThemeConfig.current
    // 设置页导航槽补足窗口边距；大标题沿用顶栏自身的原生边距。
    val navigationStartInset = when {
        topBarStyle == AppTopBarStyle.LARGE -> 0.dp
        LocalAppUiStyle.current == AppUiStyle.MIUIX -> 12.dp
        else -> 8.dp
    }
    val headerBlurEnabled = topBarBlurEnabled ?: appThemeConfig.headerBlurEnabled
    val lowBlurBudget = isLowBlurBudgetForced()
    val nonGlassMiuix = isMiuixNonGlassEnabled()
    val collapseBehavior = if (
        nonGlassMiuix &&
        topBarStyle == AppTopBarStyle.LARGE &&
        scrollHost == SettingsPageScrollHost.LazyColumn
    ) {
        rememberAppTopBarCollapseBehavior()
    } else {
        null
    }
    val progressiveBlurEnabled = shouldUseBiliPaiProgressiveTopBlur(
        enabled = appThemeConfig.progressiveTopBlurEnabled && !headerBlurEnabled,
        hasBackdrop = true,
    ) && !lowBlurBudget
    val fadeActive = appThemeConfig.progressiveTopFadeEnabled && !headerBlurEnabled
    // Keep this backdrop in the scaffold chrome. The scroll content is its source,
    // so passing it into a search dock inside that content would create a render-layer cycle.
    val chromeSource = if (progressiveBlurEnabled) rememberChromeBackdropSource() else null
    val backdrop = chromeSource?.takeIf { it.isReady }?.backdrop
    val hazeState = if (
        headerBlurEnabled && !lowBlurBudget &&
        shouldAllowRenderEffectBackedHazeEffect(android.os.Build.VERSION.SDK_INT)
    ) rememberRecoverableHazeState().takeIf { recoverableBlurEnabled(it) } else null
    val hazeReady = hazeState != null
    val topBarBlurActive = progressiveBlurEnabled || hazeReady || fadeActive
    val pageContainerColor = when (LocalAppUiStyle.current) {
        // Miuix page canvas is `background` / chromeBackground so the top bar, split
        // pane, and list share one tone. Cards stay on surfaceContainer.
        AppUiStyle.MIUIX -> AppSurfaceTokens.chromeBackground()
        AppUiStyle.MATERIAL3 -> AppSurfaceTokens.groupedListContainer()
    }

    CompositionLocalProvider(
        LocalAppPreferenceIconTreatment provides AppPreferenceIconTreatment.FILLED,
        // Settings-only Miuix icons; shared defaults and MD3 remain unchanged in both glass states.
        com.android.purebilibili.core.ui.components.LocalAppPreferenceIconColors provides
            com.android.purebilibili.feature.settings.settingsIconColors(),
        LocalAppPreferenceGroupPresentation provides if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppPreferenceGroupPresentation.CARD
        } else {
            AppPreferenceGroupPresentation.FLAT
        },
    ) {
        AppScaffold(
            modifier = modifier.appTopBarNestedScroll(collapseBehavior),
            topBarIconBackdropSource = chromeSource,
            topBar = {
                BiliPaiImmersiveTopBar(
                    backdrop = backdrop,
                    enabled = progressiveBlurEnabled,
                    headerBlurActive = hazeReady,
                    surfaceColor = pageContainerColor,
                    fadeEnabled = fadeActive,
                ) {
                    AppTopBar(
                        title = title,
                        modifier = if (hazeState != null) {
                            Modifier
                                .unifiedBlur(
                                    hazeState = hazeState,
                                    surfaceType = BlurSurfaceType.HEADER,
                                )
                                .background(
                                    pageContainerColor
                                        .copy(alpha = AppSurfaceTokens.FrostedScrimAlpha)
                                )
                        } else Modifier,
                        navigationIcon = {
                            // 设置页自行提供导航边距；不修改共用顶栏默认值，玻璃与原生路径共用。
                            Box(modifier = Modifier.padding(start = navigationStartInset)) {
                                AdaptivePlainTooltipBox(
                                    text = backContentDescription,
                                    enabled = true,
                                ) {
                                    AppIconButton(onClick = onBack) {
                                        AppIcon(
                                            imageVector = rememberAppBackIcon(),
                                            contentDescription = backContentDescription,
                                        )
                                    }
                                }
                            }
                        },
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (!topBarBlurActive || nonGlassMiuix) {
                                pageContainerColor
                            } else {
                                Color.Transparent
                            },
                            titleContentColor = AppContentColors.primaryText(),
                            navigationIconContentColor = AppContentColors.primaryText(),
                            actionIconContentColor = AppContentColors.primaryText(),
                        ),
                        style = topBarStyle,
                        collapseBehavior = collapseBehavior,
                    )
                }
            },
            containerColor = pageContainerColor,
            contentWindowInsets = WindowInsets(0.dp),
        ) { padding ->
            val scrollModifier = Modifier
                .fillMaxSize()
                .then(chromeSource?.modifier ?: Modifier)
                .then(
                    if (hazeState != null &&
                        com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current
                    ) Modifier.hazeSourceCompat(hazeState) else Modifier
                )
                .globalWallpaperAwareBackground(pageContainerColor)

            val windowSizeClass = LocalWindowSizeClass.current
            val isExpandedScreen = windowSizeClass.isExpandedScreen

            // 液态玻璃开启后的单选弹窗宿主：玻璃卡片与页面内容同窗渲染（Backdrop
            // 采样不跨平台窗口），关闭玻璃或设备不支持时不装配，条目回落原生弹窗。
            val glassPopupAvailable = appThemeConfig.popupGlassEnabled &&
                com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass(
                    android.os.Build.VERSION.SDK_INT,
                )

            SettingsGlassChoicePopupHost(
                glassAvailable = glassPopupAvailable,
                bottomInset = resolvedBottomContentPadding,
            ) {
                when (scrollHost) {
                    SettingsPageScrollHost.LazyColumn -> {
                        Box(
                            modifier = scrollModifier,
                            contentAlignment = Alignment.TopCenter
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .then(
                                        if (isExpandedScreen) {
                                            Modifier.widthIn(max = 760.dp).fillMaxWidth()
                                        } else {
                                            Modifier.fillMaxWidth()
                                        }
                                    ),
                                contentPadding = PaddingValues(
                                    top = padding.calculateTopPadding(),
                                    bottom = maxOf(resolvedBottomContentPadding, padding.calculateBottomPadding()),
                                ),
                            ) {
                                if (header != null) {
                                    item {
                                        header()
                                    }
                                }
                                if (lazyListContent != null) {
                                    lazyListContent()
                                } else {
                                    item {
                                        content()
                                    }
                                }
                            }
                        }
                    }

                    SettingsPageScrollHost.External -> {
                        val chromeTop = padding.calculateTopPadding()
                        CompositionLocalProvider(
                            LocalSettingsTopContentPadding provides if (header != null) {
                                0.dp
                            } else {
                                chromeTop
                            },
                        ) {
                            Box(
                                modifier = scrollModifier,
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .then(
                                            if (isExpandedScreen) {
                                                Modifier.widthIn(max = 760.dp).fillMaxWidth()
                                            } else {
                                                Modifier.fillMaxWidth()
                                            }
                                        )
                                ) {
                                    if (header != null) {
                                        Box(modifier = Modifier.padding(top = chromeTop)) {
                                            header()
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f, fill = true)
                                            .fillMaxSize(),
                                    ) {
                                        content()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
