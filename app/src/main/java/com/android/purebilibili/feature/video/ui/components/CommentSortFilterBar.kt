package com.android.purebilibili.feature.video.ui.components
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

internal fun resolveCommentSortSegmentedControlSpec(itemCount: Int): CommentSortSegmentedControlSpec {
    return CommentSortSegmentedControlSpec(
        itemWidthDp = if (itemCount >= 4) 56 else 66,
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommentListTitle(title = title, count = count)
    }
}

@Composable
private fun CommentListTitle(title: String, count: Int) {
    val appearance = rememberVideoCommentAppearance()
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppText(
            text = title,
            fontSize = 20.sp, // iOS Large Title style scale
            fontWeight = FontWeight.Bold,
            color = appearance.primaryTextColor,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(modifier = Modifier.width(6.dp))
        AppText(
            text = FormatUtils.formatStat(count.toLong()),
            fontSize = 16.sp,
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
) {
    val uiStyle = LocalAppUiStyle.current
    val sortModes = remember { listOf(CommentSortMode.HOT, CommentSortMode.NEWEST) }
    val spec = remember(sortModes.size) {
        resolveCommentSortSegmentedControlSpec(itemCount = sortModes.size)
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
        )
        if (uiStyle == AppUiStyle.MIUIX) {
            Box(
                modifier = Modifier.width((spec.itemWidthDp * sortModes.size).dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                AppThemeAdaptiveTabRow(
                    options = sortModes.map { AppSegmentOption(it, it.label) },
                    selectedValue = sortMode,
                    onSelectionChange = onSortModeChange,
                    modifier = Modifier.fillMaxWidth(),
                    height = spec.heightDp.dp,
                    indicatorHeight = spec.indicatorHeightDp.dp,
                    labelFontSize = 13.sp,
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
                AppText(text = sortMode.label, fontSize = 14.sp)
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
) {
    val sortModes = remember { listOf(CommentSortMode.HOT, CommentSortMode.NEWEST) }
    CommentSegmentedControl(
        items = sortModes.map { it.label },
        selectedIndex = sortModes.indexOf(sortMode).coerceAtLeast(0),
        onScaleChange = { index ->
            sortModes.getOrNull(index)?.let(onSortModeChange)
        },
        modifier = modifier,
        miuixBackdrop = miuixBackdrop,
        liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
    )
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
    val spec = remember(items.size) {
        resolveCommentSortSegmentedControlSpec(itemCount = items.size)
    }
    BottomBarLiquidSegmentedControl(
        items = items,
        selectedIndex = selectedIndex,
        onSelected = onScaleChange,
        itemWidth = spec.itemWidthDp.dp,
        height = spec.heightDp.dp,
        indicatorHeight = spec.indicatorHeightDp.dp,
        labelFontSize = 13.sp,
        modifier = modifier,
        miuixBackdrop = miuixBackdrop,
        liquidGlassEffectsEnabled = liquidGlassEffectsEnabled,
        dragSelectionEnabled = items.size > 1,
        tapPressRefractionEnabled = true,
        // The detail header owns a fixed 66dp-per-item width. Keep native Miuix items
        // evenly divided when its non-glass outer track is transparent.
        forceEqualWidth = true,
    )
}
