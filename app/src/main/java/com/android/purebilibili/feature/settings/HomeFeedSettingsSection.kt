package com.android.purebilibili.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.home.DynamicSettingsStore
import com.android.purebilibili.feature.dynamic.defaultDynamicTabVisibleIds
import com.android.purebilibili.feature.dynamic.resolveDynamicVisibleTabIdsAfterToggle
import kotlinx.coroutines.launch

@Composable
internal fun HomeFeedSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feedApiType by SettingsManager.getFeedApiType(context).collectAsStateWithLifecycle(initialValue = SettingsManager.FeedApiType.WEB
    )
    val incrementalTimelineRefreshEnabled by SettingsManager.getIncrementalTimelineRefresh(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val dynamicVisibleTabIds by SettingsManager.getDynamicTabVisibleTabs(context)
        .collectAsStateWithLifecycle(initialValue = defaultDynamicTabVisibleIds)
    val dynamicTabOrder by SettingsManager.getDynamicTabOrder(context)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val dynamicImagePreviewTextVisible by SettingsManager.getDynamicImagePreviewTextVisible(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val dynamicCommentPreviewVisible by DynamicSettingsStore.observeCommentPreviewVisible(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val dynamicDetailImageLayout by SettingsManager.getDynamicDetailImageLayout(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.peekDynamicDetailImageLayout(context))
    val dynamicTopBarCollapseOnScroll by SettingsManager
        .getDynamicTopBarCollapseOnScroll(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val dynamicFeedLayoutMode by SettingsManager
        .getDynamicFeedLayoutMode(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode.WATERFALL)
    
    FeedApiSection(
        feedApiType = feedApiType,
        incrementalTimelineRefreshEnabled = incrementalTimelineRefreshEnabled,
        dynamicImagePreviewTextVisible = dynamicImagePreviewTextVisible,
        dynamicCommentPreviewVisible = dynamicCommentPreviewVisible,
        dynamicDetailImageLayout = dynamicDetailImageLayout,
        dynamicTopBarCollapseOnScroll = dynamicTopBarCollapseOnScroll,
        dynamicFeedLayoutMode = dynamicFeedLayoutMode,
        dynamicVisibleTabIds = dynamicVisibleTabIds,
        dynamicTabOrder = dynamicTabOrder,
        onFeedApiTypeChange = { value -> scope.launch { SettingsManager.setFeedApiType(context, value); android.widget.Toast.makeText(context, "已切换为${value.label}，下拉刷新后生效", android.widget.Toast.LENGTH_SHORT).show() } },
        onIncrementalTimelineRefreshChange = { value -> scope.launch { SettingsManager.setIncrementalTimelineRefresh(context, value) } },
        onDynamicImagePreviewTextVisibleChange = { value -> scope.launch { SettingsManager.setDynamicImagePreviewTextVisible(context, value) } },
        onDynamicCommentPreviewVisibleChange = { value -> scope.launch { DynamicSettingsStore.setCommentPreviewVisible(context, value) } },
        onDynamicDetailImageLayoutChange = { value -> scope.launch { SettingsManager.setDynamicDetailImageLayout(context, value) } },
        onDynamicTopBarCollapseOnScrollChange = { value -> scope.launch { SettingsManager.setDynamicTopBarCollapseOnScroll(context, value) } },
        onDynamicFeedLayoutModeChange = { value -> scope.launch { SettingsManager.setDynamicFeedLayoutMode(context, value) } },
        onDynamicTabOrderChange = { value -> scope.launch { SettingsManager.setDynamicTabOrder(context, value) } },
        onDynamicTabVisibilityChange = { tabId ->
            scope.launch { SettingsManager.setDynamicTabVisibleTabs(context, resolveDynamicVisibleTabIdsAfterToggle(dynamicVisibleTabIds, tabId)) }
        },
    )
}
