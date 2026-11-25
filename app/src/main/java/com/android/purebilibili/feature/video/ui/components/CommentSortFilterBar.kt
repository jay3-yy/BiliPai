package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import kotlin.math.ceil

internal data class CommentSortSegmentedControlSpec(
    val itemWidthDp: Int,
    val heightDp: Int,
    val indicatorHeightDp: Int
)

internal fun resolveCommentSortSegmentedControlSpec(
    itemCount: Int,
    liquidGlassEnabled: Boolean = false,
): CommentSortSegmentedControlSpec {
    return CommentSortSegmentedControlSpec(
        // The outer header and inner dock must shrink together. Native defaults stay intact.
        itemWidthDp = if (liquidGlassEnabled || itemCount >= 4) 56 else 66,
        heightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp,
        indicatorHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp,
    )
}

internal fun hasCommentSortIndicatorScaleClearance(
    containerHeightDp: Int,
    indicatorHeightDp: Int
): Boolean {
    val geometry = com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorGeometry(
        dockHeightDp = containerHeightDp.toFloat(),
        indicatorHeightDp = indicatorHeightDp.toFloat(),
    )
    return geometry.pressedHeightDp > containerHeightDp
}

internal fun resolveCommentSortDockViewportOverflowDp(
    containerHeightDp: Int,
    indicatorHeightDp: Int,
): Int {
    val geometry = com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorGeometry(
        dockHeightDp = containerHeightDp.toFloat(),
        indicatorHeightDp = indicatorHeightDp.toFloat(),
    )
    return ceil(
        ((geometry.pressedHeightDp - containerHeightDp) / 2f).coerceAtLeast(0f)
    ).toInt()
}

/**
 * 评论列表标题。
 */
@Composable
fun CommentListHeader(
    count: Int,
    title: String = "评论",
    modifier: Modifier = Modifier,
    liquidGlassEffectsEnabled: Boolean = true,
    compactTypography: Boolean = false,
) {
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
        liquidGlassEffectsEnabled
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommentListTitle(
            title = title,
            count = count,
            liquidGlassEnabled = liquidGlassEnabled,
            compactTypography = compactTypography,
        )
    }
}

@Composable
internal fun CommentListTitle(
    title: String,
    count: Int,
    liquidGlassEnabled: Boolean,
    compactTypography: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 紧凑字阶用于玻璃评论栏与详情页共享浮动评论栏；关闭玻璃的原生列表栏默认保持
    // MD3/Miuix 原字阶，只有明确传入 compactTypography 的调用点才跟随紧凑字阶。
    val compact = liquidGlassEnabled || compactTypography
    val titleStyle = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    val countStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
    val titleWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold
    val appearance = rememberVideoCommentAppearance()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AppText(
            text = title,
            style = titleStyle,
            fontWeight = titleWeight,
            color = appearance.primaryTextColor,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(modifier = Modifier.width(6.dp))
        AppText(
            text = FormatUtils.formatStat(count.toLong()),
            style = countStyle,
            fontWeight = FontWeight.Normal,
            color = appearance.secondaryTextColor,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * 同时展示评论标题和排序控件的通用列表栏，用于不使用详情页顶栏的评论界面。
 */
@Composable
fun CommentSortHeader(
    count: Int,
    sortMode: CommentSortMode,
    onSortModeChange: (CommentSortMode) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: (() -> Unit)? = null,
    compactTypography: Boolean = false,
) {
    val uiStyle = LocalAppUiStyle.current
    val sortModes = remember { listOf(CommentSortMode.HOT, CommentSortMode.NEWEST) }
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    val spec = remember(sortModes.size, liquidGlassEnabled) {
        resolveCommentSortSegmentedControlSpec(sortModes.size, liquidGlassEnabled)
    }
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CommentListTitle(
            title = if (uiStyle == AppUiStyle.MIUIX) "评论" else "${sortMode.label}评论",
            count = count,
            liquidGlassEnabled = liquidGlassEnabled,
            compactTypography = compactTypography,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onSearchClick != null) {
                AppIconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(spec.heightDp.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索评论",
                        modifier = Modifier.size(18.dp),
                        tint = AppContentColors.secondaryText(),
                    )
                }
            }
            if (uiStyle == AppUiStyle.MIUIX) {
                val sortOptions = remember(sortModes) {
                    sortModes.map { AppSegmentOption(it, it.label) }
                }
                Box(
                    modifier = Modifier.width((spec.itemWidthDp * sortModes.size).dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AppThemeAdaptiveTabRow(
                        // Glass indicator fills the measured slot; native geometry keeps its defaults.
                        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                        role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
                        options = sortOptions,
                        selectedValue = sortMode,
                        onSelectionChange = onSortModeChange,
                        modifier = Modifier.fillMaxWidth(),
                        height = spec.heightDp.dp,
                        indicatorHeight = spec.indicatorHeightDp.dp,
                        labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
                        compactMiuixWhenTwoOptions = true,
                        dragSelectionEnabled = true,
                        tapPressRefractionEnabled = false,
                    )
                }
            } else {
                AppTextButton(
                    onClick = {
                        onSortModeChange(
                            if (sortMode == CommentSortMode.HOT) CommentSortMode.NEWEST else CommentSortMode.HOT
                        )
                    },
                ) {
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "切换评论排序",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText(text = sortMode.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * 评论排序分段控件，放置在详情页顶栏的“评论”标签右侧。
 */
@Composable
fun CommentSortFilterBar(
    sortMode: CommentSortMode,
    onSortModeChange: (CommentSortMode) -> Unit,
    modifier: Modifier = Modifier,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidGlassEffectsEnabled: Boolean = true,
    onSearchClick: (() -> Unit)? = null,
) {
    val sortModes = remember { listOf(CommentSortMode.HOT, CommentSortMode.NEWEST) }
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
        liquidGlassEffectsEnabled
    val spec = remember(sortModes.size, liquidGlassEnabled) {
        resolveCommentSortSegmentedControlSpec(sortModes.size, liquidGlassEnabled)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onSearchClick != null) {
            AppIconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(spec.heightDp.dp),
            ) {
                AppIcon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "搜索评论",
                    modifier = Modifier.size(18.dp),
                    tint = AppContentColors.secondaryText(),
                )
            }
        }
        val sortLabels = remember(sortModes) { sortModes.map { it.label } }
        Box(
            modifier = Modifier.requiredWidth((spec.itemWidthDp * sortModes.size).dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            CommentSegmentedControl(
                items = sortLabels,
                selectedIndex = sortModes.indexOf(sortMode).coerceAtLeast(0),
                onScaleChange = { index ->
                    sortModes.getOrNull(index)?.let(onSortModeChange)
                },
                modifier = Modifier.fillMaxWidth(),
                miuixBackdrop = miuixBackdrop,
                liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
            )
        }
    }
}

/**
 * Bottom-bar matched segmented control.
 */
@Composable
fun CommentSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onScaleChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidGlassEffectsEnabled: Boolean = true,
) {
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled &&
        liquidGlassEffectsEnabled
    val spec = remember(items.size, liquidGlassEnabled) {
        resolveCommentSortSegmentedControlSpec(items.size, liquidGlassEnabled)
    }
    BottomBarLiquidSegmentedControl(
        // Glass indicator fills the measured slot; native geometry keeps its defaults.
        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
        items = items,
        selectedIndex = selectedIndex,
        onSelected = onScaleChange,
        itemWidth = spec.itemWidthDp.dp,
        height = spec.heightDp.dp,
        indicatorHeight = spec.indicatorHeightDp.dp,
        labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
        modifier = modifier,
        miuixBackdrop = miuixBackdrop,
        liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
        dragSelectionEnabled = items.size > 1,
        tapPressRefractionEnabled = true,
        // The header and child share this material-specific slot width. Keep native
        // Miuix evenly divided in its original 66dp slots when the outer track is transparent.
        forceEqualWidth = true,
    )
}
