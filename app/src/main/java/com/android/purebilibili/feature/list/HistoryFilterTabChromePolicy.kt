package com.android.purebilibili.feature.list

import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppTopChromePolicy
import com.android.purebilibili.feature.home.components.resolveHomeTopSearchRowHorizontalPadding
import kotlin.math.roundToInt

internal const val HISTORY_FILTER_LIQUID_DOCK_LABEL_FONT_SIZE_SP = 15

internal data class HistoryFilterTabChromeSpec(
    val useLiquidDock: Boolean,
    val heightDp: Int,
    val indicatorHeightDp: Int,
    val itemWidthDp: Int?,
    val horizontalPaddingDp: Int,
    val labelFontSizeSp: Int,
    val dragSelectionEnabled: Boolean
)

internal fun shouldUseHistoryFilterLiquidDock(
    androidNativeLiquidGlassEnabled: Boolean
): Boolean = androidNativeLiquidGlassEnabled

internal fun resolveHistoryFilterTabItemWidthDp(filterCount: Int): Int {
    return when {
        filterCount >= 5 -> 56
        filterCount >= 4 -> 60
        else -> 66
    }
}

internal fun resolveHistoryFilterTabChromeSpec(
    homeSettings: HomeSettings,
    topChromePolicy: AppTopChromePolicy,
    filterCount: Int = HistoryContentFilter.entries.size
): HistoryFilterTabChromeSpec {
    val useLiquidDock = shouldUseHistoryFilterLiquidDock(
        androidNativeLiquidGlassEnabled = homeSettings.androidNativeLiquidGlassEnabled
    )
    val compactChrome = topChromePolicy.compactChromeSpec
    return if (useLiquidDock) {
        // Glass filters share the compact category dock height. Search/header height
        // makes short, content-sized labels produce circular selection indicators.
        // Keep this sizing inside the glass branch; native chip sizing stays unchanged.
        val sharedDockHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp
        HistoryFilterTabChromeSpec(
            useLiquidDock = true,
            heightDp = sharedDockHeightDp,
            indicatorHeightDp = com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp(
                sharedDockHeightDp.toFloat(),
            ),
            // Use the shared measured label width and padding for the compact glass capsule.
            itemWidthDp = null,
            horizontalPaddingDp = resolveHomeTopSearchRowHorizontalPadding(topChromePolicy)
                .value
                .roundToInt(),
            labelFontSizeSp = HISTORY_FILTER_LIQUID_DOCK_LABEL_FONT_SIZE_SP,
            // Drag is allowed in the dock middle; FloatingBottomBar leaves the
            // system-gesture edge bands for predictive back.
            dragSelectionEnabled = true
        )
    } else {
        HistoryFilterTabChromeSpec(
            useLiquidDock = false,
            heightDp = compactChrome.chipHeightDp,
            indicatorHeightDp = 30,
            itemWidthDp = resolveHistoryFilterTabItemWidthDp(filterCount),
            horizontalPaddingDp = 12,
            labelFontSizeSp = HISTORY_FILTER_LIQUID_DOCK_LABEL_FONT_SIZE_SP,
            dragSelectionEnabled = false
        )
    }
}
