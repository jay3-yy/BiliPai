package com.android.purebilibili.feature.settings

data class SettingsTabletLayoutPolicy(
    val primaryRatio: Float,
    val masterPanePaddingDp: Int,
    val detailPanePaddingDp: Int,
    val detailMaxWidthDp: Int,
    val rootPanelMaxWidthDp: Int
)

fun shouldUseSettingsSplitLayout(
    widthDp: Int,
    hasVerticalSeparatingHinge: Boolean = false,
): Boolean = widthDp >= 840 || (hasVerticalSeparatingHinge && widthDp >= 600)

fun shouldRenderSettingsSinglePaneContent(
    widthDp: Int,
    forceSinglePaneContent: Boolean,
    hasVerticalSeparatingHinge: Boolean = false,
): Boolean = forceSinglePaneContent || !shouldUseSettingsSplitLayout(widthDp, hasVerticalSeparatingHinge)

fun resolveSettingsTabletLayoutPolicy(
    widthDp: Int
): SettingsTabletLayoutPolicy {
    return when {
        widthDp >= 1600 -> SettingsTabletLayoutPolicy(
            primaryRatio = 0.30f,
            masterPanePaddingDp = 20,
            detailPanePaddingDp = 28,
            detailMaxWidthDp = 920,
            rootPanelMaxWidthDp = 700
        )
        widthDp >= 840 -> SettingsTabletLayoutPolicy(
            primaryRatio = 0.35f,
            masterPanePaddingDp = 16,
            detailPanePaddingDp = 24,
            detailMaxWidthDp = 800,
            rootPanelMaxWidthDp = 600
        )
        else -> SettingsTabletLayoutPolicy(
            primaryRatio = 0.44f,
            masterPanePaddingDp = 12,
            detailPanePaddingDp = 16,
            detailMaxWidthDp = 640,
            rootPanelMaxWidthDp = 540
        )
    }
}
