// 文件路径: feature/dynamic/components/DynamicTopBar.kt
package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
//  Material Icons
import com.android.purebilibili.feature.dynamic.resolveDynamicTopBarHorizontalPadding
import com.android.purebilibili.feature.dynamic.resolveDynamicTopBarTabHeightDp
import com.android.purebilibili.feature.home.components.DynamicPublishSkinDecoration
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import java.io.File
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconDecorationEnabled
import com.android.purebilibili.core.ui.components.LocalAppTopBarIconBackdrop
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
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
    onAuthorSearchClick: (() -> Unit)? = null,
    authorSearchSource: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    onFilterClick: (() -> Unit)? = null,
    isFilterActive: Boolean = false,
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val tabHeight = resolveDynamicTopBarTabHeightDp(density.fontScale).dp
    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val liquidGlassEnabled = appThemeConfig.liquidGlassEnabled
    val headerBlurEnabled = appThemeConfig.headerBlurEnabled
    val progressiveTopBlurEnabled = appThemeConfig.progressiveTopBlurEnabled
    val isProgressiveBlurActive = progressiveTopBlurEnabled && !headerBlurEnabled
    val isProgressiveFadeActive = appThemeConfig.progressiveTopFadeEnabled && !headerBlurEnabled
    val collapseArrowRotation = animateFloatAsState(
        targetValue = if (actionDockCollapsed) 0f else 180f,
        animationSpec = com.android.purebilibili.core.ui.motion.softLandingSpring(),
        label = "dynamicActionsChevronRotation",
    )

    // Shared layout request: both materials put the toggle beside the tabs and expand
    // actions downward. Glass controls decoration only, not the header structure.

    BiliPaiImmersiveTopBar(
        backdrop = dockBackdrop,
        enabled = isProgressiveBlurActive,
        headerBlurActive = headerBlurEnabled &&
            hazeState?.let { recoverableBlurEnabled(it) } == true &&
            !isProgressiveBlurActive,
        fadeEnabled = isProgressiveFadeActive,
        iconSurfaceColor = if (!isProgressiveBlurActive && headerBlurEnabled && hazeState != null) {
            AppSurfaceTokens.cardContainer().copy(alpha = AppSurfaceTokens.FrostedScrimAlpha)
        } else Color.Transparent,
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
    val visibleDockBackdrop = com.android.purebilibili.core.ui.blur.rememberSurfaceOverlayBackdrop(
        dockBackdrop, com.android.purebilibili.core.ui.components.LocalAppTopBarSurfaceColor.current,
    )
    Column {
        Spacer(modifier = Modifier.height(statusBarHeight))

        // Dock 保留各自材质；关闭顶部模糊时由外层容器提供连续的不透明背景。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(tabHeight)
                .padding(horizontal = resolveDynamicTopBarHorizontalPadding()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppThemeAdaptiveTabRow(
                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.PRIMARY,
                options = tabs.mapIndexed { index, label -> AppSegmentOption(index, label) },
                selectedValue = selectedTab,
                onSelectionChange = onTabSelected,
                modifier = Modifier.weight(1f).height(tabHeight),
                // Fixed slots: only the page or indicator changes selection; the rail
                // itself never scrolls. Keep the same interaction across all materials.
                scrollable = false,
                forceEqualWidth = true,
                liquidGlassLabelHorizontalPadding = if (liquidGlassEnabled) 20.dp else null,
                compactMiuixWhenTwoOptions = false,
                height = tabHeight,
                indicatorHeight = tabHeight - 8.dp,
                labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
                indicatorPositionProvider = indicatorPositionProvider,
                isScrollInProgressProvider = isScrollInProgressProvider,
                dragSelectionEnabled = tabs.size > 1,
                tapPressRefractionEnabled = true,
                miuixBackdrop = visibleDockBackdrop.takeIf { liquidGlassEnabled },
            )
            CompositionLocalProvider(
                LocalAppTopBarIconDecorationEnabled provides liquidGlassEnabled,
                LocalAppTopBarIconBackdrop provides dockBackdrop,
            ) {
                AppIconButton(
                    onClick = { onActionDockCollapsedChange(!actionDockCollapsed) },
                    modifier = Modifier.size(tabHeight),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = collapseArrowRotation.value
                        },
                        contentDescription = if (actionDockCollapsed) "展开动态操作" else "收起动态操作",
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !actionDockCollapsed,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = com.android.purebilibili.core.ui.motion.softLandingSpring(),
            ) + fadeIn(),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeOut(),
        ) {
            CompositionLocalProvider(
                LocalAppTopBarIconDecorationEnabled provides liquidGlassEnabled,
                LocalAppTopBarIconBackdrop provides dockBackdrop,
            ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = resolveDynamicTopBarHorizontalPadding()),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onFilterClick != null) {
                            AppIconButton(onClick = onFilterClick) {
                                AppIcon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = "动态筛选",
                                    tint = if (isFilterActive) AppContentColors.accent() else AppContentColors.primaryText(),
                                )
                            }
                        }
                        if (onAuthorSearchClick != null) {
                            AppIconButton(
                                onClick = onAuthorSearchClick,
                                modifier = authorSearchSource?.let {
                                    Modifier.appGlassMenuSource(it)
                                } ?: Modifier,
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "搜索 UP 主动态",
                                )
                            }
                        }
                        // 操作集中靠右排列，避免搜索与发布/更多之间出现大段空白。
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
                                                ?: AppContentColors.primaryText(),
                                            modifier = Modifier.size(AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro)
                                        )
                                    }
                                }
                            }
                        }
                        // 玻璃菜单直接显示位置选项，单层面板沿用更多按钮的锚点；
                        // 关闭玻璃时保留原生菜单原有的分级结构。
                        val positionActions = DynamicDisplayMode.entries.map { mode ->
                            AppWindowAction(
                                label = resolveDynamicDisplayModeLabel(mode),
                                selected = displayMode == mode,
                                onClick = { onDisplayModeChange(mode) },
                            )
                        }
                        val useGlassPositionMenu = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
                            com.android.purebilibili.core.ui.components.appGlassMenuAvailable()
                        AppWindowActionMenu(
                            groups = if (useGlassPositionMenu) listOf(positionActions) else listOf(
                                listOf(AppWindowAction(label = "关注列表位置", children = positionActions)),
                            ),
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.MoreHoriz,
                                contentDescription = "更多动态操作",
                            )
                        }
                    }
            }
        }
        Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
    }
    }
}

