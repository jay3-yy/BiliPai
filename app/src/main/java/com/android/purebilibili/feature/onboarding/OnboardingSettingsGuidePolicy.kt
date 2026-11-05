package com.android.purebilibili.feature.onboarding

import android.content.Context
import com.android.purebilibili.core.store.BottomBarSearchAutoExpandMode
import com.android.purebilibili.core.store.HomeTopLayoutOrder
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.applyOnboardingRecommendedUiStyle

enum class OnboardingSettingsProfile(
    val title: String,
    val subtitle: String
) {
    RECOMMENDED(
        title = "推荐默认",
        subtitle = "MD3 跟随系统、原生底栏、五个纯文字顶部标签"
    ),
    PERFORMANCE(
        title = "流畅优先",
        subtitle = "原生材质与底栏，保留核心视频过渡"
    ),
    DATA_SAVER(
        title = "省流量",
        subtitle = "移动网络自动省流量，首页封面更克制"
    )
}

data class OnboardingSettingsGuidePreset(
    val profile: OnboardingSettingsProfile,
    val bottomBarFloating: Boolean,
    val bottomBarLiquidGlassEnabled: Boolean,
    val androidNativeLiquidGlassEnabled: Boolean,
    val bottomBarSearchEnabled: Boolean,
    val topTabLabelMode: Int,
    val topTabOrderIds: List<String>,
    val topTabVisibleIds: Set<String>,
    val homeTopLayoutOrder: HomeTopLayoutOrder,
    val dataSaverMode: SettingsManager.DataSaverMode,
    val lowQualityHomeCoverInDataSaver: Boolean,
    val cardTransitionEnabled: Boolean,
    val summaryLines: List<String>
)

private val DEFAULT_ONBOARDING_TOP_TAB_IDS = listOf(
    "RECOMMEND",
    "FOLLOW",
    "POPULAR",
    "LIVE",
    "GAME"
)

fun resolveOnboardingSettingsGuidePreset(
    profile: OnboardingSettingsProfile
): OnboardingSettingsGuidePreset {
    val sharedSummary = listOf(
        "默认使用 MD3 / Material 3",
        "使用原生材质和底栏，液态玻璃按需开启",
        "首页顶部标签纯文字显示 5 个"
    )
    return when (profile) {
        OnboardingSettingsProfile.RECOMMENDED -> OnboardingSettingsGuidePreset(
            profile = profile,
            bottomBarFloating = false,
            bottomBarLiquidGlassEnabled = false,
            androidNativeLiquidGlassEnabled = false,
            bottomBarSearchEnabled = false,
            topTabLabelMode = SettingsManager.TopTabLabelMode.TEXT_ONLY,
            topTabOrderIds = DEFAULT_ONBOARDING_TOP_TAB_IDS,
            topTabVisibleIds = DEFAULT_ONBOARDING_TOP_TAB_IDS.toSet(),
            homeTopLayoutOrder = HomeTopLayoutOrder.SEARCH_THEN_TABS,
            dataSaverMode = SettingsManager.DataSaverMode.MOBILE_ONLY,
            lowQualityHomeCoverInDataSaver = false,
            cardTransitionEnabled = true,
            summaryLines = sharedSummary
        )

        OnboardingSettingsProfile.PERFORMANCE -> OnboardingSettingsGuidePreset(
            profile = profile,
            bottomBarFloating = false,
            bottomBarLiquidGlassEnabled = false,
            androidNativeLiquidGlassEnabled = false,
            bottomBarSearchEnabled = false,
            topTabLabelMode = SettingsManager.TopTabLabelMode.TEXT_ONLY,
            topTabOrderIds = DEFAULT_ONBOARDING_TOP_TAB_IDS,
            topTabVisibleIds = DEFAULT_ONBOARDING_TOP_TAB_IDS.toSet(),
            homeTopLayoutOrder = HomeTopLayoutOrder.SEARCH_THEN_TABS,
            dataSaverMode = SettingsManager.DataSaverMode.MOBILE_ONLY,
            lowQualityHomeCoverInDataSaver = false,
            cardTransitionEnabled = true,
            summaryLines = sharedSummary + "保留核心视频过渡"
        )

        OnboardingSettingsProfile.DATA_SAVER -> OnboardingSettingsGuidePreset(
            profile = profile,
            bottomBarFloating = false,
            bottomBarLiquidGlassEnabled = false,
            androidNativeLiquidGlassEnabled = false,
            bottomBarSearchEnabled = false,
            topTabLabelMode = SettingsManager.TopTabLabelMode.TEXT_ONLY,
            topTabOrderIds = DEFAULT_ONBOARDING_TOP_TAB_IDS,
            topTabVisibleIds = DEFAULT_ONBOARDING_TOP_TAB_IDS.toSet(),
            homeTopLayoutOrder = HomeTopLayoutOrder.SEARCH_THEN_TABS,
            dataSaverMode = SettingsManager.DataSaverMode.MOBILE_ONLY,
            lowQualityHomeCoverInDataSaver = true,
            cardTransitionEnabled = true,
            summaryLines = sharedSummary + "省流量时首页封面使用低清晰度"
        )
    }
}

suspend fun applyOnboardingSettingsGuidePreset(
    context: Context,
    profile: OnboardingSettingsProfile
) {
    val preset = resolveOnboardingSettingsGuidePreset(profile)
    applyOnboardingRecommendedUiStyle(context)
    SettingsManager.setBottomBarFloating(context, preset.bottomBarFloating)
    SettingsManager.setBottomBarLiquidGlassEnabled(context, preset.bottomBarLiquidGlassEnabled)
    SettingsManager.setAndroidNativeLiquidGlassEnabled(
        context,
        preset.androidNativeLiquidGlassEnabled
    )
    SettingsManager.setBottomBarSearchEnabled(context, preset.bottomBarSearchEnabled)
    SettingsManager.setTopTabLabelMode(context, preset.topTabLabelMode)
    SettingsManager.setTopTabOrder(context, preset.topTabOrderIds)
    SettingsManager.setTopTabVisibleTabs(context, preset.topTabVisibleIds)
    SettingsManager.setHomeTopLayoutOrder(context, preset.homeTopLayoutOrder)
    SettingsManager.setDataSaverMode(context, preset.dataSaverMode)
    SettingsManager.setLowQualityHomeCoverInDataSaver(
        context = context,
        value = preset.lowQualityHomeCoverInDataSaver
    )
    SettingsManager.setCardTransitionEnabled(context, preset.cardTransitionEnabled)
    SettingsManager.setLiveSurfaceCardTransitionEnabled(context, true)
}
