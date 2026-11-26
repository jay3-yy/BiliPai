package com.android.purebilibili.feature.plugin

import com.android.purebilibili.plugin.sdk.Plugin
import com.android.purebilibili.plugin.sdk.RecommendationStrategy
import kotlinx.coroutines.flow.StateFlow

/** Host-only settings controls, available even when recommendation execution is disabled. */
interface TodayWatchControls : Plugin {
    val configState: StateFlow<TodayWatchPluginConfig>
    fun setCurrentMode(mode: TodayWatchPluginMode)
    fun setCollapsed(collapsed: Boolean)
    fun setRecommendationStrategy(strategy: RecommendationStrategy) {}
}
