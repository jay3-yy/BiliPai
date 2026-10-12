// 文件路径: feature/dynamic/components/DynamicTopBar.kt
package com.android.purebilibili.feature.dynamic.components
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.purebilibili.core.ui.components.LocalElasticPressEnabled
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.rememberAppElasticPressState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
//  Material Icons
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.rememberAppGridLayoutIcon
import com.android.purebilibili.core.ui.rememberAppListLayoutIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.feature.dynamic.resolveDynamicTopBarHorizontalPadding
import com.android.purebilibili.feature.dynamic.resolveDynamicTopBarLiquidTabSpec
import com.android.purebilibili.feature.home.components.DynamicPublishSkinDecoration
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import java.io.File
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import dev.chrisbanes.haze.HazeState

//  动态页面布局模式
enum class DynamicDisplayMode {
    SIDEBAR,
    SIDEBAR_RIGHT,
    HORIZONTAL,
    DRAWER_LEFT,
    DRAWER_RIGHT
}

internal fun DynamicDisplayMode.isHorizontalUserList(): Boolean = this == DynamicDisplayMode.HORIZONTAL

internal fun DynamicDisplayMode.isFixedSidebar(): Boolean =
    this == DynamicDisplayMode.SIDEBAR || this == DynamicDisplayMode.SIDEBAR_RIGHT

internal fun DynamicDisplayMode.isRightAligned(): Boolean =
    this == DynamicDisplayMode.SIDEBAR_RIGHT || this == DynamicDisplayMode.DRAWER_RIGHT

internal fun DynamicDisplayMode.isDrawer(): Boolean =
    this == DynamicDisplayMode.DRAWER_LEFT || this == DynamicDisplayMode.DRAWER_RIGHT

internal fun resolveDynamicDisplayModeLabel(mode: DynamicDisplayMode): String = when (mode) {
    DynamicDisplayMode.SIDEBAR -> "左侧竖条"
    DynamicDisplayMode.SIDEBAR_RIGHT -> "右侧竖条"
    DynamicDisplayMode.HORIZONTAL -> "顶部横条"
    DynamicDisplayMode.DRAWER_LEFT -> "左侧抽屉"
    DynamicDisplayMode.DRAWER_RIGHT -> "右侧抽屉"
}

/**
 *  带Tab的顶栏
 */
@Composable
fun DynamicTopBarWithTabs(
    selectedTab: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    displayMode: DynamicDisplayMode = DynamicDisplayMode.SIDEBAR,
    onDisplayModeChange: (DynamicDisplayMode) -> Unit = {},
    onPublishClick: (() -> Unit)? = null,
    actionDockCollapsed: Boolean = false,
    onActionDockCollapsedChange: (Boolean) -> Unit = {},
    publishSkinDecoration: DynamicPublishSkinDecoration? = null,
    dockBackdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    indicatorPositionProvider: (() -> Float)? = null,
    isScrollInProgressProvider: () -> Boolean = { false },
    /** True when the horizontal UP user list is visible directly below the bar. */
    shouldShowHorizontalUserList: Boolean = false,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val liquidTabSpec = resolveDynamicTopBarLiquidTabSpec()
    val homeSettings by SettingsManager
        .getHomeSettings(context)
        .collectAsStateWithLifecycle(initialValue = HomeSettings())
    val liquidGlassEnabled = homeSettings.androidNativeLiquidGlassEnabled
    val liquidGlassTuning = remember(
        homeSettings.liquidGlassProgress,
        homeSettings.liquidGlassAdvancedSettings,
        homeSettings.liquidGlassReadabilityMode,
    ) {
        resolveLiquidGlassTuning(
            progress = homeSettings.liquidGlassProgress,
            advancedSettings = homeSettings.liquidGlassAdvancedSettings,
            readabilityMode = homeSettings.liquidGlassReadabilityMode,
        )
    }
    val dockShape = if (!liquidGlassEnabled && LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        AppShapes.container(ContainerLevel.Card)
    } else {
        AppShapes.container(ContainerLevel.Pill)
    }
    val dockColor = AppSurfaceTokens.surfaceContainerHigh()

    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val headerBlurEnabled = appThemeConfig.headerBlurEnabled
    val progressiveTopBlurEnabled = appThemeConfig.progressiveTopBlurEnabled
    val isProgressiveBlurActive = progressiveTopBlurEnabled && !headerBlurEnabled
    val isProgressiveFadeActive = appThemeConfig.progressiveTopFadeEnabled && !headerBlurEnabled

    BiliPaiImmersiveTopBar(
        backdrop = dockBackdrop,
        enabled = isProgressiveBlurActive,
        headerBlurActive = headerBlurEnabled &&
            hazeState?.let { recoverableBlurEnabled(it) } == true &&
            !isProgressiveBlurActive,
        fadeEnabled = isProgressiveFadeActive,
        surfaceColor = globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.background),
        // 不让顶栏的渐进模糊向下越界盖住 UP 头像或动态卡片。
        // 顶栏自身渐进模糊效果保持不变，仅收敛其向下延伸。
        extendBelowBounds = false,
        modifier = modifier.then(
            if (!isProgressiveBlurActive && headerBlurEnabled && hazeState != null) {
                Modifier
                    .unifiedBlur(hazeState = hazeState, surfaceType = BlurSurfaceType.HEADER)
                    .background(AppSurfaceTokens.cardContainer().copy(alpha = AppSurfaceTokens.FrostedScrimAlpha))
            } else Modifier
        ),
    ) {
    Column {
        Spacer(modifier = Modifier.height(statusBarHeight))

        // Dock 保留各自材质；关闭顶部模糊时由外层容器提供连续的不透明背景。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (liquidGlassEnabled) {
                        Modifier.height(liquidTabSpec.heightDp.dp)
                    } else {
                        // Native tabs may grow above 48dp with the user's font scale.
                        Modifier.heightIn(min = liquidTabSpec.heightDp.dp)
                    }
                )
                .padding(horizontal = resolveDynamicTopBarHorizontalPadding()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppThemeAdaptiveTabRow(
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.PRIMARY,
                options = tabs.mapIndexed { index, label -> AppSegmentOption(index, label) },
                selectedValue = selectedTab,
                onSelectionChange = onTabSelected,
                modifier = Modifier.weight(1f),
                scrollable = true,
                compactMiuixWhenTwoOptions = false,
                height = liquidTabSpec.heightDp.dp,
                indicatorHeight = liquidTabSpec.indicatorHeightDp.dp,
                labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
                indicatorPositionProvider = indicatorPositionProvider,
                isScrollInProgressProvider = isScrollInProgressProvider,
                dragSelectionEnabled = tabs.size > 1,
                tapPressRefractionEnabled = true,
                miuixBackdrop = dockBackdrop.takeIf { liquidGlassEnabled },
            )

            val localActionDockBackdrop = if (liquidGlassEnabled && dockBackdrop == null) {
                rememberLayerBackdrop()
            } else {
                null
            }
            val actionDockBackdrop = dockBackdrop ?: localActionDockBackdrop
            val actionElasticState = rememberAppElasticPressState()
            Box {
                if (liquidGlassEnabled && dockBackdrop == null && localActionDockBackdrop != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0f)
                            .layerBackdrop(localActionDockBackdrop)
                            .background(AppSurfaceTokens.background())
                    )
                }
                Row(
                    modifier = Modifier
                        .appElasticPress(
                            state = actionElasticState,
                            transformInBackdrop = liquidGlassEnabled,
                        )
                        .then(
                            if (liquidGlassEnabled) {
                                Modifier.biliPaiFloatingDockShell(
                                    backdrop = requireNotNull(actionDockBackdrop),
                                    interactionLayerBlock = actionElasticState.layerBlock,
                                    interactionState = actionElasticState,
                                    containerColor = dockColor,
                                    pressProgress = 0f,
                                    shape = dockShape,
                                    liquidGlassTuning = liquidGlassTuning,
                                )
                            } else {
                                Modifier.background(dockColor, dockShape)
                            }
                        )
                        .clip(dockShape),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The capsule owns deformation; child click/menu targets retain their gestures.
                    CompositionLocalProvider(LocalElasticPressEnabled provides false) {
                        AnimatedVisibility(
                            visible = !actionDockCollapsed,
                            enter = expandHorizontally(
                                expandFrom = Alignment.End,
                                animationSpec = AppMotionTokens.standardSpec(),
                            ) + fadeIn(animationSpec = AppMotionTokens.standardSpec()),
                            exit = shrinkHorizontally(
                                shrinkTowards = Alignment.End,
                                animationSpec = AppMotionTokens.standardSpec(),
                            ) + fadeOut(animationSpec = AppMotionTokens.standardSpec()),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppWindowActionMenu(
                                    groups = listOf(
                                        DynamicDisplayMode.entries.map { mode ->
                                            AppWindowAction(
                                                label = resolveDynamicDisplayModeLabel(mode),
                                                selected = displayMode == mode,
                                                onClick = { onDisplayModeChange(mode) },
                                            )
                                        },
                                    ),
                                ) {
                                    AppIcon(
                                        imageVector = if (displayMode.isHorizontalUserList())
                                            rememberAppGridLayoutIcon() else rememberAppListLayoutIcon(),
                                        contentDescription = "关注列表位置",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro)
                                    )
                                }

                                // 发布动态入口（对齐 BiliPai AppBar actions 的发布按钮）。
                                if (onPublishClick != null) {
                                    val publishInteractionSource = remember { MutableInteractionSource() }
                                    val publishPressed by publishInteractionSource.collectIsPressedAsState()
                                    val publishIconPaths = publishSkinDecoration?.iconPaths
                                    AppIconButton(
                                        onClick = onPublishClick,
                                        interactionSource = publishInteractionSource,
                                    ) {
                                        if (publishIconPaths != null) {
                                            PageAwareAsyncImage(
                                                model = File(publishIconPaths.pathFor(publishPressed)),
                                                contentDescription = "发布动态",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.size(AppSpacingTokens.DoubleExtraLarge),
                                            )
                                        } else {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(AppSpacingTokens.DoubleExtraLarge)
                                                    .then(
                                                        if (publishSkinDecoration?.hasShade == true) {
                                                            Modifier.background(
                                                                brush = Brush.verticalGradient(
                                                                    listOf(
                                                                        publishSkinDecoration.shadeTop,
                                                                        publishSkinDecoration.shadeBottom,
                                                                    )
                                                                ),
                                                                shape = CircleShape,
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    ),
                                            ) {
                                                AppIcon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = "发布动态",
                                                    tint = publishSkinDecoration?.iconTint
                                                        ?.takeUnless { it == Color.Unspecified }
                                                        ?: MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        AppIconButton(
                            onClick = { onActionDockCollapsedChange(!actionDockCollapsed) },
                        ) {
                            AppIcon(
                                imageVector = if (actionDockCollapsed) {
                                    rememberAppChevronDownIcon()
                                } else {
                                    rememberAppChevronUpIcon()
                                },
                                contentDescription = if (actionDockCollapsed) {
                                    "展开顶部操作"
                                } else {
                                    "折叠顶部操作"
                                },
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro),
                            )
                        }
                    }
                }
            }
        }
    }
}

}

internal fun resolveDynamicTabSelectedColor(primaryColor: Color): Color = primaryColor
