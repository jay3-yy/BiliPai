package com.android.purebilibili.core.ui

import com.android.purebilibili.core.ui.wallpaper.LocalGlobalWallpaperBackdropSource
import com.android.purebilibili.core.ui.wallpaper.sharedGlobalWallpaperBackground
import com.android.purebilibili.core.ui.blur.ChromeBackdropSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import com.android.purebilibili.core.ui.components.AppChromeOverflowHost
import com.android.purebilibili.core.ui.components.AppChromeOverflowSlot
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.resolveAndroidNativeChromeTokens
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior as rememberMiuixTopAppBarScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior as MiuixTopAppBarScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

fun isNativeMiuixEnabled(
    uiStyle: AppUiStyle
): Boolean = uiStyle == AppUiStyle.MIUIX

@Composable
fun rememberIsNativeMiuixEnabled(): Boolean {
    return isNativeMiuixEnabled(
        uiStyle = LocalAppUiStyle.current
    )
}

enum class AdaptiveTopAppBarStyle {
    SMALL,
    CENTERED,
    LARGE
}

/** Neutral app-owned handle that keeps Miuix scroll types out of feature code. */
@Stable
class AppTopBarCollapseBehavior internal constructor(
    internal val miuixScrollBehavior: MiuixTopAppBarScrollBehavior,
)

@Composable
fun rememberAppTopBarCollapseBehavior(): AppTopBarCollapseBehavior {
    val miuixScrollBehavior = rememberMiuixTopAppBarScrollBehavior()
    return remember(miuixScrollBehavior) {
        AppTopBarCollapseBehavior(miuixScrollBehavior)
    }
}

fun Modifier.appTopBarNestedScroll(
    behavior: AppTopBarCollapseBehavior?,
): Modifier = if (behavior != null) {
    nestedScroll(behavior.miuixScrollBehavior.nestedScrollConnection)
} else {
    this
}

data class AdaptiveTopAppBarChromeSpec(
    val containerCornerRadiusDp: Int,
    val scrolledContainerAlpha: Float,
    val scrolledTonalElevationDp: Int,
    val motionScale: Float
)

val LocalGlobalWallpaperBackdropVisible = compositionLocalOf { false }

/** Set only around chrome whose background is drawn by the shared immersive blur layer. */
val LocalImmersiveTopChromeActive = compositionLocalOf { false }

fun resolveGlobalWallpaperProtectiveColor(
    baseColor: Color,
    lightAlpha: Float = 0.74f,
    darkAlpha: Float = 0.80f
): Color {
    val alpha = if (baseColor.luminance() > 0.5f) lightAlpha else darkAlpha
    return baseColor.copy(alpha = alpha.coerceIn(0f, 1f))
}

fun resolveAdaptiveTopAppBarChromeSpec(
    uiStyle: AppUiStyle
): AdaptiveTopAppBarChromeSpec {
    val chromeTokens = resolveAndroidNativeChromeTokens(uiStyle)
    return AdaptiveTopAppBarChromeSpec(
        containerCornerRadiusDp = chromeTokens.containerCornerRadiusDp,
        scrolledContainerAlpha = 1f,
        scrolledTonalElevationDp = 0,
        motionScale = chromeTokens.motionScale
    )
}

fun resolveAdaptiveScaffoldContainerColor(
    requestedContainerColor: Color,
    defaultBackgroundColor: Color,
    globalWallpaperVisible: Boolean
): Color {
    return if (globalWallpaperVisible && requestedContainerColor == defaultBackgroundColor) {
        resolveGlobalWallpaperProtectiveColor(
            baseColor = requestedContainerColor,
            lightAlpha = 0.66f,
            darkAlpha = 0.72f
        )
    } else {
        requestedContainerColor
    }
}

fun resolveGlobalWallpaperChromeColor(
    requestedColor: Color,
    defaultBackgroundColor: Color,
    defaultSurfaceColor: Color,
    globalWallpaperVisible: Boolean
): Color {
    if (!globalWallpaperVisible || requestedColor.alpha == 0f) return requestedColor
    val requestedOpaque = requestedColor.copy(alpha = 1f)
    return if (
        requestedOpaque == defaultBackgroundColor.copy(alpha = 1f) ||
        requestedOpaque == defaultSurfaceColor.copy(alpha = 1f)
    ) {
        resolveGlobalWallpaperProtectiveColor(
            baseColor = requestedOpaque,
            lightAlpha = 0.74f,
            darkAlpha = 0.80f
        )
    } else {
        requestedColor
    }
}

@Composable
fun globalWallpaperAwareChromeColor(color: Color): Color {
    return resolveGlobalWallpaperChromeColor(
        requestedColor = color,
        defaultBackgroundColor = MaterialTheme.colorScheme.background,
        defaultSurfaceColor = MaterialTheme.colorScheme.surface,
        globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    )
}

@Composable
fun Modifier.globalWallpaperAwareBackground(
    color: Color = MaterialTheme.colorScheme.background
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val wallpaperSource = LocalGlobalWallpaperBackdropSource.current
    val semanticBackground = color.alpha == 1f && (
        color == scheme.background || color == scheme.surface
    )
    return if (LocalGlobalWallpaperBackdropVisible.current && wallpaperSource != null && semanticBackground) {
        // Replay the opaque wallpaper itself instead of accumulating protective fills.
        sharedGlobalWallpaperBackground(color)
    } else if (LocalGlobalWallpaperBackdropVisible.current) {
        background(
            resolveAdaptiveScaffoldContainerColor(
                requestedContainerColor = color,
                defaultBackgroundColor = MaterialTheme.colorScheme.background,
                globalWallpaperVisible = true
            )
        )
    } else {
        background(color)
    }
}

@Composable
fun AdaptiveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentWindowInsets: WindowInsets = WindowInsets.navigationBars,
    // WebView 等独立渲染内容不能进入顶栏的背景录制；默认保留普通页面行为。
    contentBackdropEnabled: Boolean = true,
    /** 同窗页面已录制的内容源；调用方负责挂载，不能包含消费它的顶栏控件。 */
    topBarIconBackdropSource: ChromeBackdropSource? = null,
    /** 页面已负责顶栏材质时，跳过脚手架的额外模糊与正文采样；图标采样独立保留。 */
    topBarBlurHandledExternally: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val sharedWallpaper = LocalGlobalWallpaperBackdropVisible.current &&
        LocalGlobalWallpaperBackdropSource.current != null &&
        (containerColor == MaterialTheme.colorScheme.background ||
            containerColor == MaterialTheme.colorScheme.surface)
    val effectiveContainerColor = if (sharedWallpaper) Color.Transparent else resolveAdaptiveScaffoldContainerColor(
        requestedContainerColor = containerColor,
        defaultBackgroundColor = MaterialTheme.colorScheme.background,
        globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    )
    val scaffoldRenderer = resolveAdaptiveScaffoldRenderer(
        uiStyle = LocalAppUiStyle.current
    )
    // 页面渐进模糊与玻璃图标复用同一份内容录制；未就绪时也不另建临时背景源。
    // 默认仍由 scaffold 录制，关闭玻璃时不改变 MD3 / Miuix 的原生路径。
    val iconBackdropEnabled = contentBackdropEnabled && LocalAppThemeConfig.current.liquidGlassEnabled
    val localIconBackdropSource = if (iconBackdropEnabled && topBarIconBackdropSource == null) {
        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    } else null
    val iconBackdropSource = if (iconBackdropEnabled) {
        topBarIconBackdropSource ?: localIconBackdropSource
    } else null
    // 顶栏毛玻璃：由统一的「顶部模糊」开关控制；句柄为 null 时 chrome 保持原生容器色。
    val scaffoldHaze = rememberAdaptiveScaffoldHaze(
        userEnabled = contentBackdropEnabled && !topBarBlurHandledExternally &&
            LocalAppThemeConfig.current.headerBlurEnabled
    )
    val decoratedTopBar: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalAdaptiveScaffoldHaze provides scaffoldHaze,
            com.android.purebilibili.core.ui.components.LocalAppTopBarIconBackdrop provides
                iconBackdropSource?.takeIf { it.isReady }?.backdrop,
        ) { topBar() }
    }
    val recordedContent: @Composable (PaddingValues) -> Unit = { padding ->
        val hazeSourceModifier = if (scaffoldHaze != null &&
            com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current
        ) {
            Modifier.hazeSource(scaffoldHaze.state)
        } else {
            Modifier
        }
        if (localIconBackdropSource != null || sharedWallpaper) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(hazeSourceModifier)
                    .then(localIconBackdropSource?.modifier ?: Modifier)
                    .then(
                        if (sharedWallpaper) Modifier.sharedGlobalWallpaperBackground(containerColor)
                        else Modifier
                    ),
            ) { content(padding) }
        } else if (scaffoldHaze != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(hazeSourceModifier),
            ) { content(padding) }
        } else content(padding)
    }
    when (scaffoldRenderer) {
        AdaptiveScaffoldRenderer.MIUIX_SCAFFOLD_WITH_POPUP_HOST -> {
        MiuixScaffold(
            modifier = modifier,
            topBar = decoratedTopBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
            popupHost = { MiuixPopupUtils.MiuixPopupHost() },
            containerColor = effectiveContainerColor,
            contentWindowInsets = contentWindowInsets,
            content = recordedContent
        )
        }
        AdaptiveScaffoldRenderer.MATERIAL3_SCAFFOLD -> {
        Scaffold(
            modifier = modifier,
            topBar = decoratedTopBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
            containerColor = effectiveContainerColor,
            contentWindowInsets = contentWindowInsets,
            content = recordedContent
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    largeTitle: String = title,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        navigationIconContentColor = AppSurfaceTokens.onSurface(),
        actionIconContentColor = AppSurfaceTokens.onSurface(),
    ),
    style: AdaptiveTopAppBarStyle = AdaptiveTopAppBarStyle.SMALL,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    collapseBehavior: AppTopBarCollapseBehavior? = null,
    smallTitleColor: Color = Color.Unspecified,
) {
    val scaffoldHaze = LocalAdaptiveScaffoldHaze.current
    val hazeModifier = if (scaffoldHaze != null) {
        Modifier.adaptiveTopBarHazeBlur(scaffoldHaze)
    } else {
        Modifier
    }
    AppChromeOverflowHost(modifier = modifier.then(hazeModifier)) {
        NativeAdaptiveTopAppBar(
            title = title,
            subtitle = subtitle,
            largeTitle = largeTitle,
            scaffoldHaze = scaffoldHaze,
            navigationIcon = {
                CompositionLocalProvider(
                    com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorationEnabled provides true,
                    com.android.purebilibili.core.ui.components.LocalAppActionContentColor provides LocalContentColor.current,
                    top.yukonga.miuix.kmp.theme.LocalContentColor provides LocalContentColor.current,
                ) {
                    AppChromeOverflowSlot {
                        Box(Modifier.padding(end = 6.dp)) { navigationIcon() }
                    }
                }
            },
            actions = {
                AppChromeOverflowSlot {
                    CompositionLocalProvider(
                        com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorationEnabled provides true,
                        com.android.purebilibili.core.ui.components.LocalAppActionContentColor provides LocalContentColor.current,
                        top.yukonga.miuix.kmp.theme.LocalContentColor provides LocalContentColor.current,
                    ) { Row(verticalAlignment = Alignment.CenterVertically, content = actions) }
                }
            },
            colors = colors,
            style = style,
            scrollBehavior = scrollBehavior,
            collapseBehavior = collapseBehavior,
            smallTitleColor = smallTitleColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeAdaptiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    largeTitle: String = title,
    /** 脚手架注入的顶栏模糊句柄；存在时容器色置透明，由模糊样式提供材质。 */
    scaffoldHaze: AdaptiveScaffoldHazeHandle? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        navigationIconContentColor = AppSurfaceTokens.onSurface(),
        actionIconContentColor = AppSurfaceTokens.onSurface(),
    ),
    style: AdaptiveTopAppBarStyle = AdaptiveTopAppBarStyle.SMALL,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    collapseBehavior: AppTopBarCollapseBehavior? = null,
    smallTitleColor: Color = Color.Unspecified,
) {
    // Keep crowded phone chrome readable: long dynamic titles must not collapse into
    // one-character-per-line text when actions consume the remaining width.
    val narrowTitle = LocalConfiguration.current.screenWidthDp < 600
    val displayTitle = remember(title, narrowTitle) {
        if (narrowTitle && title.length > 12) title.take(11) + "…" else title
    }
    val displayLargeTitle = remember(largeTitle, narrowTitle) {
        if (narrowTitle && largeTitle.length > 12) largeTitle.take(11) + "…" else largeTitle
    }
    val uiStyle = LocalAppUiStyle.current
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    val chromeSpec = resolveAdaptiveTopAppBarChromeSpec(uiStyle)
    val effectiveColors = if (globalWallpaperVisible) {
        colors.copy(
            containerColor = resolveGlobalWallpaperChromeColor(
                requestedColor = colors.containerColor,
                defaultBackgroundColor = MaterialTheme.colorScheme.background,
                defaultSurfaceColor = MaterialTheme.colorScheme.surface,
                globalWallpaperVisible = true
            ),
            scrolledContainerColor = resolveGlobalWallpaperChromeColor(
                requestedColor = colors.scrolledContainerColor,
                defaultBackgroundColor = MaterialTheme.colorScheme.background,
                defaultSurfaceColor = MaterialTheme.colorScheme.surface,
                globalWallpaperVisible = true
            )
        )
    } else {
        colors
    }
    val topAppBarColors = when {
        LocalImmersiveTopChromeActive.current -> effectiveColors.copy(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        )
        // 模糊接管材质：容器色透明，避免实色盖住 haze 背景。
        scaffoldHaze != null -> effectiveColors.copy(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        )
        else -> effectiveColors
    }

    // 与原生顶栏实际容器色合成，透明顶栏继承外层遮色；不改变 MD3/Miuix 绘制参数。
    val iconSurfaceColor = topAppBarColors.containerColor.compositeOver(
        com.android.purebilibili.core.ui.components.LocalAppTopBarSurfaceColor.current
    )
    val decoratedNavigationIcon: @Composable () -> Unit = {
        CompositionLocalProvider(
            com.android.purebilibili.core.ui.components.LocalAppTopBarSurfaceColor provides iconSurfaceColor,
        ) { navigationIcon() }
    }
    val decoratedActions: @Composable RowScope.() -> Unit = {
        CompositionLocalProvider(
            com.android.purebilibili.core.ui.components.LocalAppTopBarSurfaceColor provides iconSurfaceColor,
        ) { actions() }
    }

    if (rememberIsNativeMiuixEnabled()) {
        SideEffect {
            // Native Miuix bars do not consume the caller's Material scroll state. Leaving its
            // default unbounded limit lets enterAlways swallow every vertical drag indefinitely.
            // Match the native bar's fixed height, including state restored from another theme.
            if (collapseBehavior == null) scrollBehavior?.state?.let { state ->
                state.heightOffsetLimit = 0f
                state.heightOffset = 0f
            }
        }
        val navigationContent =
            @Composable {
                CompositionLocalProvider(
                    LocalContentColor provides topAppBarColors.navigationIconContentColor
                ) {
                    decoratedNavigationIcon()
                }
            }
        val actionsContent: @Composable RowScope.() -> Unit = {
            CompositionLocalProvider(
                LocalContentColor provides topAppBarColors.actionIconContentColor
            ) {
                decoratedActions()
            }
        }
        when (style) {
            AdaptiveTopAppBarStyle.LARGE -> {
                MiuixTopAppBar(
                    title = displayTitle,
                    largeTitle = displayLargeTitle,
                    modifier = modifier,
                    color = topAppBarColors.containerColor,
                    navigationIcon = navigationContent,
                    actions = actionsContent,
                    scrollBehavior = collapseBehavior?.miuixScrollBehavior,
                    // Large titles share titlePadding with the collapsed title. Keep upstream
                    // insets so the expanded heading does not touch the window edge.
                )
            }

            AdaptiveTopAppBarStyle.SMALL -> {
                if (isMiuixNonGlassEnabled()) {
                    MiuixSmallTopAppBar(
                        title = displayTitle,
                        titleColor = if (smallTitleColor == Color.Unspecified) {
                            top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
                        } else smallTitleColor,
                        modifier = modifier,
                        color = topAppBarColors.containerColor,
                        navigationIcon = navigationContent,
                        actions = actionsContent,
                        titlePadding = 0.dp,
                        navigationIconPadding = 0.dp,
                        actionIconPadding = 0.dp,
                    )
                } else {
                    TopAppBar(
                        modifier = modifier,
                        title = { Text(displayTitle, color = smallTitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = navigationContent,
                        actions = actionsContent,
                        colors = topAppBarColors,
                        scrollBehavior = scrollBehavior,
                        windowInsets = WindowInsets.statusBars
                    )
                }
            }

            AdaptiveTopAppBarStyle.CENTERED -> {
                MiuixSmallTopAppBar(
                    title = displayTitle,
                    modifier = modifier,
                    color = topAppBarColors.containerColor,
                    navigationIcon = navigationContent,
                    actions = actionsContent,
                    titlePadding = 0.dp,
                    navigationIconPadding = 0.dp,
                    actionIconPadding = 0.dp,
                )
            }
        }
        return
    }

    val topBarWindowInsets = WindowInsets.statusBars
    when (style) {
        AdaptiveTopAppBarStyle.SMALL -> {
            TopAppBar(
                modifier = modifier,
                title = { Text(displayTitle, color = smallTitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = decoratedNavigationIcon,
                actions = decoratedActions,
                colors = topAppBarColors,
                scrollBehavior = scrollBehavior,
                windowInsets = topBarWindowInsets
            )
        }

        AdaptiveTopAppBarStyle.CENTERED -> {
            CenterAlignedTopAppBar(
                modifier = modifier,
                title = { Text(displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = decoratedNavigationIcon,
                actions = decoratedActions,
                colors = topAppBarColors,
                scrollBehavior = scrollBehavior,
                windowInsets = topBarWindowInsets
            )
        }

        AdaptiveTopAppBarStyle.LARGE -> {
            LargeTopAppBar(
                modifier = modifier,
                title = { Text(displayLargeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = decoratedNavigationIcon,
                actions = decoratedActions,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = topAppBarColors.containerColor,
                    scrolledContainerColor = topAppBarColors.scrolledContainerColor,
                    navigationIconContentColor = topAppBarColors.navigationIconContentColor,
                    titleContentColor = topAppBarColors.titleContentColor,
                    actionIconContentColor = topAppBarColors.actionIconContentColor,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = topBarWindowInsets
            )
        }
    }
}

/**
 * Semantic top-tab treatments consumed by feature chrome.
 *
 * The values describe interaction and geometry rather than a vendor renderer, so feature code
 * does not need to inspect the persisted UI style.
 */
enum class AppTopTabPresentation {
    MOVING_CAPSULE,
    MATERIAL_UNDERLINE,
    TONAL_CAPSULE,
}

data class AppTopChromePolicy(
    val tabPresentation: AppTopTabPresentation,
    val iconFamily: AppSemanticIconFamily,
    val iconStyle: AppIconStyle = AppIconStyle.AUTO,
    val compactChromeSpec: CompactCapsuleChromeSpec,
) {
    /** MD3 官方推荐样式强制 Material 官方字形。 */
    val effectiveIconFamily: AppSemanticIconFamily
        get() = if (iconStyle == AppIconStyle.MD3_STANDARD) {
            AppSemanticIconFamily.MATERIAL
        } else {
            iconFamily
        }
}

fun resolveAppTopChromePolicy(
    uiStyle: AppUiStyle,
    iconStyle: AppIconStyle = AppIconStyle.AUTO,
): AppTopChromePolicy = when (uiStyle) {
    AppUiStyle.MIUIX -> AppTopChromePolicy(
        // Miuix liquid glass uses the same moving dock indicator contract as the
        // Material renderer. TONAL_CAPSULE is the retired per-item filled capsule.
        tabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
        iconFamily = AppSemanticIconFamily.MATERIAL,
        iconStyle = iconStyle,
        compactChromeSpec = resolveCompactCapsuleChromeSpec(uiStyle),
    )
    AppUiStyle.MATERIAL3 -> AppTopChromePolicy(
        tabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
        iconFamily = AppSemanticIconFamily.MATERIAL,
        iconStyle = iconStyle,
        compactChromeSpec = resolveCompactCapsuleChromeSpec(uiStyle),
    )
}

@Composable
fun rememberAppTopChromePolicy(): AppTopChromePolicy {
    val uiStyle = LocalAppUiStyle.current
    val iconStyle = rememberResolvedAppIconStyle()
    return remember(uiStyle, iconStyle) {
        resolveAppTopChromePolicy(uiStyle, iconStyle)
    }
}

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentWindowInsets: WindowInsets = WindowInsets.navigationBars,
    contentBackdropEnabled: Boolean = true,
    topBarIconBackdropSource: ChromeBackdropSource? = null,
    topBarBlurHandledExternally: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) = AdaptiveScaffold(
    modifier = modifier,
    topBar = topBar,
    bottomBar = bottomBar,
    floatingActionButton = floatingActionButton,
    snackbarHost = snackbarHost,
    containerColor = containerColor,
    contentWindowInsets = contentWindowInsets,
    contentBackdropEnabled = contentBackdropEnabled,
    topBarIconBackdropSource = topBarIconBackdropSource,
    topBarBlurHandledExternally = topBarBlurHandledExternally,
    content = content,
)

enum class AppTopBarStyle {
    SMALL,
    CENTERED,
    LARGE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    largeTitle: String = title,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        navigationIconContentColor = AppSurfaceTokens.onSurface(),
        actionIconContentColor = AppSurfaceTokens.onSurface(),
    ),
    style: AppTopBarStyle = AppTopBarStyle.SMALL,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    collapseBehavior: AppTopBarCollapseBehavior? = null,
    // 仅显式调用方控制小标题的状态色；未指定时保留各原生样式的默认标题颜色。
    smallTitleColor: Color = Color.Unspecified,
) = AdaptiveTopAppBar(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    largeTitle = largeTitle,
    navigationIcon = navigationIcon,
    actions = actions,
    colors = colors,
    style = when (style) {
        AppTopBarStyle.SMALL -> AdaptiveTopAppBarStyle.SMALL
        AppTopBarStyle.CENTERED -> AdaptiveTopAppBarStyle.CENTERED
        AppTopBarStyle.LARGE -> AdaptiveTopAppBarStyle.LARGE
    },
    scrollBehavior = scrollBehavior,
    collapseBehavior = collapseBehavior,
    smallTitleColor = smallTitleColor,
)
