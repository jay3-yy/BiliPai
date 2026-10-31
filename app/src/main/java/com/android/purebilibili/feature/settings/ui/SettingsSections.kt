package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.adaptiveSquircleBackground
import com.android.purebilibili.core.ui.AppSemanticAccentRole
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.util.EasterEggs
import com.android.purebilibili.core.ui.components.AppAdaptiveSwitch
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.rememberAdaptivePreferenceIconContentColor
import com.android.purebilibili.core.ui.components.rememberAdaptivePreferenceIconContainerColor
import com.android.purebilibili.core.ui.components.rememberAdaptivePreferenceIconTint
import com.android.purebilibili.core.ui.components.rememberAdaptiveListVisualCapabilities
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.core.store.MAX_HOME_REFRESH_COUNT
import com.android.purebilibili.core.store.MIN_HOME_REFRESH_COUNT
import com.android.purebilibili.core.store.NetworkProxyStore
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.network.policy.AppHttpProxySettings
import com.android.purebilibili.core.network.policy.ProxyRouteMode
import com.android.purebilibili.core.network.policy.formatAppHttpProxyEndpoint
import com.android.purebilibili.core.network.policy.formatAppHttpProxySummary
import com.android.purebilibili.core.network.policy.isAppHttpProxyConfigured
import com.android.purebilibili.core.network.policy.parseProxyDomainRules
import com.android.purebilibili.core.network.policy.sanitizeProxyHostInput
import com.android.purebilibili.core.network.policy.sanitizeProxyPortInput
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.feature.dynamic.allDynamicTabSpecs
import com.android.purebilibili.feature.dynamic.shouldAllowDynamicTabVisibilityToggleOff
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════
//  UI 组件 (Stateless Components)
// ═══════════════════════════════════════════════════

// Delegated to core/ui/components/iOSListComponents.kt
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle as SettingsSectionTitle
import com.android.purebilibili.core.ui.components.AppPreferenceGroup as SettingsGroup
import com.android.purebilibili.core.ui.components.AppSwitchPreference as SettingSwitchItem
import com.android.purebilibili.core.ui.components.AppPreference as SettingClickableItem
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import com.android.purebilibili.core.ui.components.AppPreferenceDivider as SettingsDivider
import com.android.purebilibili.core.ui.components.AppSliderDialogPreference as SettingSliderItem
import com.android.purebilibili.core.ui.components.AppPreferenceGroupPresentation



// ═══════════════════════════════════════════════════
//  业务板块 (Business Sections)
// ═══════════════════════════════════════════════════

@Composable
private fun SettingsAdaptiveDivider() {
    val visualSpec = rememberAdaptiveListVisualCapabilities().componentSpec
    SettingsDivider(startIndent = visualSpec.dividerStartIndentDp.dp)
}

@Composable
internal fun SettingsCardGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsGroup(
        presentation = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            AppPreferenceGroupPresentation.CARD
        } else {
            AppPreferenceGroupPresentation.FLAT
        },
    ) {
        content()
    }
}

internal data class SettingsDetailEntry(
    val target: SettingsSearchTarget,
    val title: String,
    val value: String,
    val openFocus: SettingsSceneDetailFocus? = null,
    val onClick: () -> Unit
)

internal data class SettingsRootCategoryActions(
    val onAppearanceClick: () -> Unit,
    val onHomeClick: () -> Unit,
    val onAnimationClick: () -> Unit,
    val onPlaybackClick: () -> Unit,
    val onBottomBarClick: () -> Unit,
    val onPermissionClick: () -> Unit,
    val onSpacePrivacyClick: () -> Unit,
    val onLoginActivityClick: () -> Unit,
    val onMessageNotificationClick: () -> Unit,
    val onBlockedListClick: () -> Unit,
    val onCommentFraudHistoryClick: () -> Unit,
    val onPluginsClick: () -> Unit,
    val onExportLogsClick: () -> Unit,
    val onSettingsShareClick: () -> Unit,
    val onWebDavBackupClick: () -> Unit,
    val onDownloadPathClick: () -> Unit,
    val onImageSavePathClick: () -> Unit,
    val onClearCacheClick: () -> Unit,
    val onAutoCacheClearIntervalChange: (SettingsManager.AutoCacheClearInterval) -> Unit,
    val onAutoCacheClearThresholdChange: (Int) -> Unit,
    val onGithubClick: () -> Unit,
    val onTelegramClick: () -> Unit,
    val onTelegramGroupClick: () -> Unit = {},
    val onTwitterClick: () -> Unit,
    val onDonateClick: () -> Unit,
    val onDisclaimerClick: () -> Unit,
    val onLicenseClick: () -> Unit,
    val onVerificationClick: () -> Unit,
    val onBuildSourceClick: () -> Unit,
    val onBuildFingerprintClick: () -> Unit,
    val onCheckUpdateClick: () -> Unit,
    val onViewReleaseNotesClick: () -> Unit,
    val onVersionClick: () -> Unit,
    val onReplayOnboardingClick: () -> Unit,
    val onTipsClick: () -> Unit,
    val onOpenLinksClick: () -> Unit,
    val onPrivacyModeChange: (Boolean) -> Unit,
    val onSearchSuggestionsChange: (Boolean) -> Unit,
    val onPrivacyContentAuthenticationChange: (Boolean) -> Unit,
    val onCrashTrackingChange: (Boolean) -> Unit,
    val onAnalyticsChange: (Boolean) -> Unit,
    val onEnhancedDiagnosticLoggingChange: (Boolean) -> Unit,
    val onEasterEggChange: (Boolean) -> Unit,
    val onAutoCheckUpdateChange: (Boolean) -> Unit,
    val onAppUpdateChannelChange: (com.android.purebilibili.core.store.SettingsManager.AppUpdateChannel) -> Unit,
    val onFeedApiTypeChange: (com.android.purebilibili.core.store.SettingsManager.FeedApiType) -> Unit,
    val onIncrementalTimelineRefreshChange: (Boolean) -> Unit,
    val onDynamicImagePreviewTextVisibleChange: (Boolean) -> Unit,
    val onDynamicCommentPreviewVisibleChange: (Boolean) -> Unit,
    val onDynamicDetailImageLayoutChange: (com.android.purebilibili.core.store.SettingsManager.DynamicDetailImageLayout) -> Unit,
    val onDynamicTopBarCollapseOnScrollChange: (Boolean) -> Unit,
    val onDynamicFeedLayoutModeChange: (com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode) -> Unit,
    val onDynamicTabVisibilityChange: (String) -> Unit,
    val onDynamicTabOrderChange: (List<String>) -> Unit,
    val onHomeRefreshCountChange: (Int) -> Unit
)

internal data class SettingsRootCategoryState(
    val privacyModeEnabled: Boolean,
    val searchSuggestionsEnabled: Boolean,
    val privacyContentAuthenticationEnabled: Boolean,
    val crashTrackingEnabled: Boolean,
    val analyticsEnabled: Boolean,
    val enhancedDiagnosticLoggingEnabled: Boolean,
    val pluginCount: Int,
    val customDownloadPath: String?,
    val customImageSavePath: String?,
    val cacheSize: String,
    val autoCacheClearInterval: SettingsManager.AutoCacheClearInterval,
    val autoCacheClearThresholdGb: Int,
    val versionName: String,
    val appIcon: String,
    val easterEggEnabled: Boolean,
    val updateStatusText: String,
    val isCheckingUpdate: Boolean,
    val autoCheckUpdateEnabled: Boolean,
    val appUpdateChannel: com.android.purebilibili.core.store.SettingsManager.AppUpdateChannel,
    val verificationLabel: String,
    val verificationSubtitle: String,
    val buildSourceValue: String,
    val buildSourceSubtitle: String,
    val buildFingerprintValue: String,
    val buildFingerprintCopyValue: String,
    val buildFingerprintSubtitle: String,
    val versionClickCount: Int,
    val versionClickThreshold: Int,
    val feedApiType: com.android.purebilibili.core.store.SettingsManager.FeedApiType,
    val incrementalTimelineRefreshEnabled: Boolean,
    val dynamicImagePreviewTextVisible: Boolean,
    val dynamicCommentPreviewVisible: Boolean,
    val dynamicDetailImageLayout: com.android.purebilibili.core.store.SettingsManager.DynamicDetailImageLayout,
    val dynamicTopBarCollapseOnScroll: Boolean,
    val dynamicFeedLayoutMode: com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode,
    val dynamicVisibleTabIds: Set<String>,
    val dynamicTabOrder: List<String>,
    val homeRefreshCount: Int
)

@Composable
internal fun SettingsRootCategoryListSection(
    categories: List<SettingsRootCategory>,
    onCategoryClick: (SettingsRootCategory) -> Unit,
    onDonateClick: () -> Unit,
) {
    val siblingTints = rememberSettingsSiblingIconTints(categories.size + 1)
    val donateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DONATE)
    SettingsCardGroup {
        categories.forEachIndexed { index, category ->
            val visual = rememberSettingsEntryVisual(category.searchTarget)
            SettingsCategoryContainerOrigin(
                category = category,
                first = index == 0,
                onClick = { onCategoryClick(category) },
            ) { open ->
                SettingsRootCategoryRow(
                    title = stringResource(category.titleResource()),
                    subtitle = stringResource(category.subtitleResource()),
                    iconColorKey = category.title,
                    icon = visual.icon,
                    iconPainter = visual.iconResId?.let { painterResource(id = it) },
                    iconTint = siblingTints[index],
                    iconSizeDp = visual.iconSizeDp,
                    onClick = open,
                )
            }
            SettingsAdaptiveDivider()
        }
        SettingsRootCategoryRow(
            title = settingsDestinationCopy(SettingsSearchTarget.DONATE).title,
            subtitle = settingsDestinationCopy(SettingsSearchTarget.DONATE).summary,
            icon = donateVisual.icon,
            iconPainter = donateVisual.iconResId?.let { painterResource(id = it) },
            iconTint = siblingTints.last(),
            iconSizeDp = donateVisual.iconSizeDp,
            onClick = onDonateClick,
        )
    }
}

/** 捕获单行自己的绘制；材质、字体和按压反馈仍由原设置行负责。 */
@Composable
private fun SettingsCategoryContainerOrigin(
    category: SettingsRootCategory,
    first: Boolean,
    onClick: () -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    val sources = LocalSettingsContainerSources.current
    val owner = LocalSettingsContainerOwner.current
    if (sources == null || owner == null) {
        content(onClick)
        return
    }
    val target = resolveSettingsCategoryNavKey(category)
    val anchor = remember(category, sources, owner) {
        sources.sources[target]?.takeIf { it.owner == owner }?.anchor ?: SettingsContainerAnchor()
    }
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val roundedGroup = LocalAppUiStyle.current == AppUiStyle.MIUIX
    // 首页 MD3 使用平铺分组；Miuix 沿用卡片在各玻璃状态下的实际底色。
    val color = if (roundedGroup) {
        com.android.purebilibili.core.ui.components.adaptivePreferenceGroupContainerColor()
    } else AppSurfaceTokens.groupedListContainer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val groupRadius = rememberAdaptiveListVisualCapabilities().componentSpec.groupCornerRadiusDp
    val topRadius = if (first && roundedGroup) with(density) { groupRadius.dp.toPx() } else 0f
    SideEffect {
        // 深层子页可能让来源行退出组合；重建后将同一导航来源重新绑定到新图层。
        sources.sources[target]?.takeIf { it.anchor === anchor }?.layer = layer
    }
    DisposableEffect(anchor) {
        onDispose { anchor.detach() }
    }
    Box(Modifier.fillMaxWidth()
        .onGloballyPositioned(anchor::update)
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            anchor.recorded = true
            if (sources.sources[target]?.takeIf { it.anchor === anchor }?.returnInFlight?.invoke() != true) {
                drawLayer(layer)
            }
        }
    ) {
        content {
            sources.capture(target, owner, anchor, layer, color, topRadius, 0f)
            onClick()
        }
    }
}

@Composable
private fun SettingsRootCategoryRow(
    title: String,
    subtitle: String,
    iconColorKey: String = title,
    icon: ImageVector?,
    iconPainter: androidx.compose.ui.graphics.painter.Painter?,
    iconTint: Color,
    iconSizeDp: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visualSpec = resolveSettingsVisualSpec()
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(iconTint)
    val iconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = 16.dp,
                vertical = visualSpec.categoryRowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX || com.android.purebilibili.core.ui.components.useColorfulPreferenceIcons()) {
            SettingsFunctionIcon(title = iconColorKey, imageVector = icon, painter = iconPainter)
        } else Box(
            modifier = Modifier
                .size(visualSpec.categoryIconBubbleSize)
                .clip(AppShapes.container(ContainerLevel.Field))
                .background(effectiveIconTint),
            contentAlignment = Alignment.Center,
        ) {
            when {
                iconPainter != null -> AppIcon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = iconContentColor,
                    modifier = Modifier.size(iconSizeDp.dp),
                )
                icon != null -> AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconContentColor,
                    modifier = Modifier.size(iconSizeDp.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = AppContentColors.primaryText(),
            )
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
        }
        AppIcon(
            imageVector = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_keyboard_arrow_right_24),
            contentDescription = null,
            tint = AppContentColors.secondaryText().copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun SettingsDetailGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        SettingsSectionTitle(title = title)
        content()
    }
}

@Composable
internal fun SettingsDetailEntrySection(
    entries: List<SettingsDetailEntry>
) {
    val siblingTints = rememberSettingsSiblingIconTints(entries.size)
    SettingsCardGroup {
        entries.forEachIndexed { index, entry ->
            val visual = rememberSettingsEntryVisual(entry.target)
            SettingClickableItem(
                icon = visual.icon,
                iconPainter = visual.iconResId?.let { painterResource(id = it) },
                title = entry.title,
                subtitle = entry.value,
                onClick = {
                    entry.openFocus?.let { detailFocus ->
                        SettingsSearchFocusController.submit(detailFocus.target, detailFocus.focusId)
                    } ?: SettingsSearchFocusController.clear()
                    entry.onClick()
                },
                iconTint = siblingTints[index]
            )
            if (index != entries.lastIndex) {
                SettingsAdaptiveDivider()
            }
        }
    }
}

@Composable
internal fun SettingsRootCategoryEntranceSection(
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.entrance()) {
        content()
    }
}

@Composable
internal fun SettingsRootCategoryContent(
    category: SettingsRootCategory,
    actions: SettingsRootCategoryActions,
    state: SettingsRootCategoryState
) {
    val nearbyHandoff = com.android.purebilibili.feature.nearby.LocalNearbyHandoff.current
    val resolvedCategory = canonicalSettingsRootCategory(category)
    Column {
        when (resolvedCategory) {
            SettingsRootCategory.FULLSCREEN_GESTURE,
            SettingsRootCategory.COMMENTS_CONTENT,
            SettingsRootCategory.MESSAGE_NOTIFICATION,
            SettingsRootCategory.VIDEO_DECODER,
            SettingsRootCategory.GLASS_ADVANCED -> Unit
            SettingsRootCategory.PLAYER_DIAGNOSTICS -> {
                SettingsDetailGroup(title = "应用日志") {
                    DiagnosticsSection(
                        crashTrackingEnabled = state.crashTrackingEnabled,
                        analyticsEnabled = state.analyticsEnabled,
                        enhancedDiagnosticLoggingEnabled = state.enhancedDiagnosticLoggingEnabled,
                        onCrashTrackingChange = actions.onCrashTrackingChange,
                        onAnalyticsChange = actions.onAnalyticsChange,
                        onEnhancedDiagnosticLoggingChange = actions.onEnhancedDiagnosticLoggingChange,
                        onExportLogsClick = actions.onExportLogsClick,
                    )
                }
                SettingsDetailGroup(title = "播放器") { PlaybackDiagnosticsSection() }
            }
            SettingsRootCategory.APPEARANCE_THEME -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "外观与主题") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.INTERFACE_THEME,
                                    title = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.APPEARANCE,
                                        SettingsSearchFocusIds.APPEARANCE_THEME,
                                    ),
                                    onClick = actions.onAppearanceClick,
                                ),
                            ),
                        )
                    }
                }
            }
            SettingsRootCategory.PLAYBACK_QUALITY -> {
                // 分类入口直跳播放器与画质页；此页仅保留搜索叶子结果落地的分组。
                if (com.android.purebilibili.feature.nearby.NEARBY_HANDOFF_ENABLED) {
                    SettingsRootCategoryEntranceSection {
                        SettingsDetailGroup(title = "跨设备播放") {
                            SettingsDetailEntrySection(entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.NEARBY_HANDOFF,
                                    title = settingsDestinationCopy(SettingsSearchTarget.NEARBY_HANDOFF).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.NEARBY_HANDOFF).summary,
                                    onClick = { nearbyHandoff?.openDevices?.invoke() },
                                ),
                            ))
                        }
                    }
                }
            }
            SettingsRootCategory.HOME_RECOMMENDATION -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "首页展示") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.HOME_FEED,
                                    title = settingsDestinationCopy(SettingsSearchTarget.HOME_FEED).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.HOME_FEED).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.HOME_FEED,
                                        SettingsSearchFocusIds.HOME_OVERVIEW,
                                    ),
                                    onClick = actions.onHomeClick,
                                ),
                            ),
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "推荐流与动态") {
                        FeedApiSection(
                            feedApiType = state.feedApiType,
                            onFeedApiTypeChange = actions.onFeedApiTypeChange,
                            incrementalTimelineRefreshEnabled = state.incrementalTimelineRefreshEnabled,
                            onIncrementalTimelineRefreshChange = actions.onIncrementalTimelineRefreshChange,
                            dynamicImagePreviewTextVisible = state.dynamicImagePreviewTextVisible,
                            onDynamicImagePreviewTextVisibleChange = actions.onDynamicImagePreviewTextVisibleChange,
                            dynamicCommentPreviewVisible = state.dynamicCommentPreviewVisible,
                            onDynamicCommentPreviewVisibleChange = actions.onDynamicCommentPreviewVisibleChange,
                            dynamicDetailImageLayout = state.dynamicDetailImageLayout,
                            onDynamicDetailImageLayoutChange = actions.onDynamicDetailImageLayoutChange,
                            dynamicTopBarCollapseOnScroll = state.dynamicTopBarCollapseOnScroll,
                            onDynamicTopBarCollapseOnScrollChange =
                                actions.onDynamicTopBarCollapseOnScrollChange,
                            dynamicFeedLayoutMode = state.dynamicFeedLayoutMode,
                            onDynamicFeedLayoutModeChange = actions.onDynamicFeedLayoutModeChange,
                            dynamicVisibleTabIds = state.dynamicVisibleTabIds,
                            onDynamicTabVisibilityChange = actions.onDynamicTabVisibilityChange,
                            dynamicTabOrder = state.dynamicTabOrder,
                            onDynamicTabOrderChange = actions.onDynamicTabOrderChange,
                            homeRefreshCount = state.homeRefreshCount,
                            onHomeRefreshCountChange = actions.onHomeRefreshCountChange,
                        )
                    }
                }
            }
            SettingsRootCategory.NAVIGATION_INTERACTION -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "导航") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.NAVIGATION,
                                    title = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.BOTTOM_BAR,
                                        SettingsSearchFocusIds.BOTTOM_BAR_START,
                                    ),
                                    onClick = actions.onBottomBarClick,
                                ),
                            ),
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "交互与动效") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.ANIMATION,
                                    title = settingsDestinationCopy(SettingsSearchTarget.ANIMATION).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.ANIMATION).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.ANIMATION,
                                        SettingsSearchFocusIds.ANIMATION_START,
                                    ),
                                    onClick = actions.onAnimationClick,
                                ),
                            ),
                        )
                    }
                }
            }
            SettingsRootCategory.PRIVACY_PERMISSION -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "隐私与权限") {
                        PrivacySection(
                            privacyModeEnabled = state.privacyModeEnabled,
                            searchSuggestionsEnabled = state.searchSuggestionsEnabled,
                            privacyContentAuthenticationEnabled = state.privacyContentAuthenticationEnabled,
                            onPrivacyModeChange = actions.onPrivacyModeChange,
                            onSearchSuggestionsChange = actions.onSearchSuggestionsChange,
                            onPrivacyContentAuthenticationChange = actions.onPrivacyContentAuthenticationChange,
                            onPermissionClick = actions.onPermissionClick,
                            onSpacePrivacyClick = actions.onSpacePrivacyClick,
                            onLoginActivityClick = actions.onLoginActivityClick,
                            onMessageNotificationClick = actions.onMessageNotificationClick,
                            onBlockedListClick = actions.onBlockedListClick,
                            onCommentFraudHistoryClick = actions.onCommentFraudHistoryClick // [New]
                        )
                    }
                }
            }
            SettingsRootCategory.STORAGE_BACKUP -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "存储与备份") {
                        DataStorageSection(
                            customDownloadPath = state.customDownloadPath,
                            customImageSavePath = state.customImageSavePath,
                            cacheSize = state.cacheSize,
                            onSettingsShareClick = actions.onSettingsShareClick,
                            onWebDavBackupClick = actions.onWebDavBackupClick,
                            onDownloadPathClick = actions.onDownloadPathClick,
                            onImageSavePathClick = actions.onImageSavePathClick,
                            onClearCacheClick = actions.onClearCacheClick,
                            autoCacheClearInterval = state.autoCacheClearInterval,
                            autoCacheClearThresholdGb = state.autoCacheClearThresholdGb,
                            onAutoCacheClearIntervalChange = actions.onAutoCacheClearIntervalChange,
                            onAutoCacheClearThresholdChange = actions.onAutoCacheClearThresholdChange,
                        )
                    }
                }
            }
            SettingsRootCategory.PLUGINS_EXTENSIONS -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "插件与扩展") {
                        PluginCenterSection(
                            pluginCount = state.pluginCount,
                            onPluginsClick = actions.onPluginsClick,
                        )
                    }
                }
            }
            SettingsRootCategory.APPEARANCE_INTERACTION -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "显示与交互") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.INTERFACE_THEME,
                                    title = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.APPEARANCE,
                                        SettingsSearchFocusIds.APPEARANCE_THEME,
                                    ),
                                    onClick = actions.onAppearanceClick
                                )
                            )
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "动效") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.ANIMATION,
                                    title = settingsDestinationCopy(SettingsSearchTarget.ANIMATION).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.ANIMATION).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.ANIMATION,
                                        SettingsSearchFocusIds.ANIMATION_START,
                                    ),
                                    onClick = actions.onAnimationClick
                                )
                            )
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "导航") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.NAVIGATION,
                                    title = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.BOTTOM_BAR,
                                        SettingsSearchFocusIds.BOTTOM_BAR_START,
                                    ),
                                    onClick = actions.onBottomBarClick
                                )
                            )
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "全屏与手势") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.FULLSCREEN_GESTURE,
                                    title = settingsDestinationCopy(SettingsSearchTarget.FULLSCREEN_GESTURE).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.FULLSCREEN_GESTURE).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.PLAYBACK,
                                        SettingsSearchFocusIds.PLAYBACK_FULLSCREEN,
                                    ),
                                    onClick = actions.onPlaybackClick
                                )
                            )
                        )
                    }
                }
            }
            SettingsRootCategory.CONTENT_PLAYBACK -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "首页展示") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.HOME_FEED,
                                    title = settingsDestinationCopy(SettingsSearchTarget.HOME_FEED).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.HOME_FEED).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.HOME_FEED,
                                        SettingsSearchFocusIds.HOME_OVERVIEW,
                                    ),
                                    onClick = actions.onHomeClick
                                )
                            )
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "推荐流与动态") {
                        FeedApiSection(
                            feedApiType = state.feedApiType,
                            onFeedApiTypeChange = actions.onFeedApiTypeChange,
                            incrementalTimelineRefreshEnabled = state.incrementalTimelineRefreshEnabled,
                            onIncrementalTimelineRefreshChange = actions.onIncrementalTimelineRefreshChange,
                            dynamicImagePreviewTextVisible = state.dynamicImagePreviewTextVisible,
                            onDynamicImagePreviewTextVisibleChange = actions.onDynamicImagePreviewTextVisibleChange,
                            dynamicCommentPreviewVisible = state.dynamicCommentPreviewVisible,
                            onDynamicCommentPreviewVisibleChange = actions.onDynamicCommentPreviewVisibleChange,
                            dynamicDetailImageLayout = state.dynamicDetailImageLayout,
                            onDynamicDetailImageLayoutChange = actions.onDynamicDetailImageLayoutChange,
                            dynamicTopBarCollapseOnScroll = state.dynamicTopBarCollapseOnScroll,
                            onDynamicTopBarCollapseOnScrollChange =
                                actions.onDynamicTopBarCollapseOnScrollChange,
                            dynamicFeedLayoutMode = state.dynamicFeedLayoutMode,
                            onDynamicFeedLayoutModeChange = actions.onDynamicFeedLayoutModeChange,
                            dynamicVisibleTabIds = state.dynamicVisibleTabIds,
                            onDynamicTabVisibilityChange = actions.onDynamicTabVisibilityChange,
                            dynamicTabOrder = state.dynamicTabOrder,
                            onDynamicTabOrderChange = actions.onDynamicTabOrderChange,
                            homeRefreshCount = state.homeRefreshCount,
                            onHomeRefreshCountChange = actions.onHomeRefreshCountChange
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "画质与播放") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.PLAYBACK_QUALITY,
                                    title = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.PLAYBACK,
                                        SettingsSearchFocusIds.PLAYBACK_NETWORK,
                                    ),
                                    onClick = actions.onPlaybackClick
                                )
                            )
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "互动") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.INTERACTION_COMMENT,
                                    title = settingsDestinationCopy(SettingsSearchTarget.INTERACTION_COMMENT).title,
                                    value = settingsDestinationCopy(SettingsSearchTarget.INTERACTION_COMMENT).summary,
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.PLAYBACK,
                                        SettingsSearchFocusIds.PLAYBACK_COMMENTS,
                                    ),
                                    onClick = actions.onPlaybackClick
                                )
                            )
                        )
                    }
                }
            }
            SettingsRootCategory.PRIVACY_STORAGE -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "同步与存储") {
                        DataStorageSection(
                            customDownloadPath = state.customDownloadPath,
                            customImageSavePath = state.customImageSavePath,
                            cacheSize = state.cacheSize,
                            onSettingsShareClick = actions.onSettingsShareClick,
                            onWebDavBackupClick = actions.onWebDavBackupClick,
                            onDownloadPathClick = actions.onDownloadPathClick,
                            onImageSavePathClick = actions.onImageSavePathClick,
                            onClearCacheClick = actions.onClearCacheClick,
                            autoCacheClearInterval = state.autoCacheClearInterval,
                            autoCacheClearThresholdGb = state.autoCacheClearThresholdGb,
                            onAutoCacheClearIntervalChange = actions.onAutoCacheClearIntervalChange,
                            onAutoCacheClearThresholdChange = actions.onAutoCacheClearThresholdChange,
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "隐私与安全") {
                        PrivacySection(
                            privacyModeEnabled = state.privacyModeEnabled,
                            searchSuggestionsEnabled = state.searchSuggestionsEnabled,
                            privacyContentAuthenticationEnabled = state.privacyContentAuthenticationEnabled,
                            onPrivacyModeChange = actions.onPrivacyModeChange,
                            onSearchSuggestionsChange = actions.onSearchSuggestionsChange,
                            onPrivacyContentAuthenticationChange = actions.onPrivacyContentAuthenticationChange,
                            onPermissionClick = actions.onPermissionClick,
                            onSpacePrivacyClick = actions.onSpacePrivacyClick,
                            onLoginActivityClick = actions.onLoginActivityClick,
                            onMessageNotificationClick = actions.onMessageNotificationClick,
                            onBlockedListClick = actions.onBlockedListClick,
                            onCommentFraudHistoryClick = actions.onCommentFraudHistoryClick // [New]
                        )
                    }
                }
            }
            SettingsRootCategory.SYSTEM_ABOUT -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "问题排查") {
                        DiagnosticsSection(
                            crashTrackingEnabled = state.crashTrackingEnabled,
                            analyticsEnabled = state.analyticsEnabled,
                            enhancedDiagnosticLoggingEnabled = state.enhancedDiagnosticLoggingEnabled,
                            onCrashTrackingChange = actions.onCrashTrackingChange,
                            onAnalyticsChange = actions.onAnalyticsChange,
                            onEnhancedDiagnosticLoggingChange = actions.onEnhancedDiagnosticLoggingChange,
                            onExportLogsClick = actions.onExportLogsClick,
                        )
                        SettingsAdaptiveDivider()
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.DIAGNOSTICS,
                                    title = "播放器诊断",
                                    value = "出现黑屏、卡顿或画质切换失败时用于排查问题",
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.PLAYBACK,
                                        SettingsSearchFocusIds.PLAYBACK_DEBUG,
                                    ),
                                    onClick = actions.onPlaybackClick,
                                ),
                            ),
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "帮助与工具") {
                        SupportToolsSection(
                            onTipsClick = actions.onTipsClick,
                            onOpenLinksClick = actions.onOpenLinksClick,
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "应用信息") {
                        var showUserAgreement by remember { mutableStateOf(false) }
                        SettingClickableItem(
                            icon = rememberMaterialSymbol(R.drawable.ms_gavel_24),
                            title = "用户协议与隐私政策",
                            value = "查看全文",
                            onClick = { showUserAgreement = true },
                        )
                        SettingsAdaptiveDivider()
                        if (showUserAgreement) {
                            com.android.purebilibili.feature.agreement.UserAgreementReviewDialog(
                                onDismiss = { showUserAgreement = false },
                            )
                        }
                        AboutSection(
                            versionName = state.versionName,
                            appIconKey = state.appIcon,
                            easterEggEnabled = state.easterEggEnabled,
                            onLicenseClick = actions.onLicenseClick,
                            onGithubClick = actions.onGithubClick,
                            onVerificationClick = actions.onVerificationClick,
                            onBuildSourceClick = actions.onBuildSourceClick,
                            onBuildFingerprintClick = actions.onBuildFingerprintClick,
                            onCheckUpdateClick = actions.onCheckUpdateClick,
                            onViewReleaseNotesClick = actions.onViewReleaseNotesClick,
                            autoCheckUpdateEnabled = state.autoCheckUpdateEnabled,
                            onAutoCheckUpdateChange = actions.onAutoCheckUpdateChange,
                            appUpdateChannel = state.appUpdateChannel,
                            onAppUpdateChannelChange = actions.onAppUpdateChannelChange,
                            onVersionClick = actions.onVersionClick,
                            onReplayOnboardingClick = actions.onReplayOnboardingClick,
                            onEasterEggChange = actions.onEasterEggChange,
                            updateStatusText = state.updateStatusText,
                            isCheckingUpdate = state.isCheckingUpdate,
                            verificationLabel = state.verificationLabel,
                            verificationSubtitle = state.verificationSubtitle,
                            buildSourceValue = state.buildSourceValue,
                            buildSourceSubtitle = state.buildSourceSubtitle,
                            buildFingerprintValue = state.buildFingerprintValue,
                            buildFingerprintCopyValue = state.buildFingerprintCopyValue,
                            buildFingerprintSubtitle = state.buildFingerprintSubtitle,
                            versionClickCount = state.versionClickCount,
                            versionClickThreshold = state.versionClickThreshold
                        )
                    }
                }
                SettingsRootCategoryEntranceSection {
                    ReleaseChannelPinnedCard(
                        onGithubClick = actions.onGithubClick,
                        onTelegramClick = actions.onTelegramClick,
                        onTelegramGroupClick = actions.onTelegramGroupClick,
                        onDisclaimerClick = actions.onDisclaimerClick
                    )
                }
            }
        }
    }
}

@Composable
fun SupportToolsSection(
    onTipsClick: () -> Unit,
    onOpenLinksClick: () -> Unit,
) {
    val tipsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.TIPS)
    val openLinksVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_LINKS)
    val siblingTints = rememberSettingsSiblingIconTints(2, paletteOffset = 1)

    SettingsCardGroup {
        SettingClickableItem(
            icon = tipsVisual.icon,
            iconPainter = tipsVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.TIPS).title,
            subtitle = settingsDestinationCopy(SettingsSearchTarget.TIPS).summary,
            onClick = onTipsClick,
            iconTint = siblingTints[0]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = openLinksVisual.icon,
            iconPainter = openLinksVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.OPEN_LINKS).title,
            value = settingsDestinationCopy(SettingsSearchTarget.OPEN_LINKS).summary,
            onClick = onOpenLinksClick,
            iconTint = siblingTints[1]
        )
    }
}

@Composable
fun ReleaseChannelPinnedCard(
    onGithubClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onTelegramGroupClick: () -> Unit = {},
    onDisclaimerClick: () -> Unit
) {
    val disclaimerTint = rememberAdaptivePreferenceIconTint(AppContentColors.accent())
    val releaseChannelIcon = rememberAppShareIcon()
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppCardShape.Semantic(ContainerLevel.Dialog),
        colors = AppCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsFunctionIcon(
                    title = "官方渠道",
                    imageVector = releaseChannelIcon,
                    contentDescription = null,
                    tint = disclaimerTint,
                    nativeSize = 20.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "官方渠道：GitHub · 频道 · 群组",
                        style = MaterialTheme.typography.titleSmall,
                        color = AppContentColors.primaryText()
                    )
                    AppText(
                        text = "请从官方渠道获取安装包，注意来源安全。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppOutlinedButton(
                    onClick = onGithubClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    AppText(
                        text = "GitHub",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
                AppOutlinedButton(
                    onClick = onTelegramClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    AppText(
                        text = "频道",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppOutlinedButton(
                    onClick = onTelegramGroupClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    AppText(
                        text = "群组",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
                AppOutlinedButton(
                    onClick = onDisclaimerClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    AppText(
                        text = "完整声明",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
fun FeedApiSection(
    feedApiType: com.android.purebilibili.core.store.SettingsManager.FeedApiType,
    onFeedApiTypeChange: (com.android.purebilibili.core.store.SettingsManager.FeedApiType) -> Unit,
    incrementalTimelineRefreshEnabled: Boolean,
    onIncrementalTimelineRefreshChange: (Boolean) -> Unit,
    dynamicImagePreviewTextVisible: Boolean,
    onDynamicImagePreviewTextVisibleChange: (Boolean) -> Unit,
    dynamicCommentPreviewVisible: Boolean,
    onDynamicCommentPreviewVisibleChange: (Boolean) -> Unit,
    dynamicDetailImageLayout: com.android.purebilibili.core.store.SettingsManager.DynamicDetailImageLayout,
    onDynamicDetailImageLayoutChange: (com.android.purebilibili.core.store.SettingsManager.DynamicDetailImageLayout) -> Unit,
    dynamicTopBarCollapseOnScroll: Boolean,
    onDynamicTopBarCollapseOnScrollChange: (Boolean) -> Unit,
    dynamicFeedLayoutMode: com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode,
    onDynamicFeedLayoutModeChange: (com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode) -> Unit,
    dynamicVisibleTabIds: Set<String>,
    onDynamicTabVisibilityChange: (String) -> Unit,
    dynamicTabOrder: List<String>,
    onDynamicTabOrderChange: (List<String>) -> Unit,
    homeRefreshCount: Int,
    onHomeRefreshCountChange: (Int) -> Unit
) {
    ContentDisplayPreferences()
    val siblingTints = rememberSettingsSiblingIconTints(10, paletteOffset = 1)
    val feedIcon = rememberSettingsSemanticIcon(SettingsIconRole.FEED_API)
    val refreshIcon = rememberSettingsSemanticIcon(SettingsIconRole.REFRESH_COUNT)
    val visibilityIcon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_TAB_VISIBILITY)
    val previewTextIcon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_PREVIEW_TEXT)
    val imageLayoutIcon = rememberSettingsSemanticIcon(SettingsIconRole.GRID_COLUMNS)
    val topBarCollapseIcon = rememberSettingsSemanticIcon(SettingsIconRole.NAVIGATION)
    val commentPreviewIcon = rememberSettingsSemanticIcon(SettingsIconRole.INTERACTION_COMMENT)
    SettingsCardGroup {
        SettingsSingleChoicePreference(
            title = "首页推荐来源",
            subtitle = feedApiType.description,
            options = resolveFeedApiSegmentOptions(),
            selectedValue = feedApiType,
            icon = feedIcon,
            iconTint = siblingTints[0],
            onSelectionChange = onFeedApiTypeChange,
        )
        SettingsAdaptiveDivider()
        SettingsItemAnchor("home.incremental_timeline_refresh_enabled") {
            SettingSwitchItem(
                icon = refreshIcon,
                title = settingItemTitle("home.incremental_timeline_refresh_enabled"),
                subtitle = "动态流下拉刷新只把新动态加到顶部，不重新排列已看到的内容",
                checked = incrementalTimelineRefreshEnabled,
                onCheckedChange = onIncrementalTimelineRefreshChange,
                iconTint = siblingTints[1]
            )
        }
        SettingsAdaptiveDivider()
        SettingsItemAnchor("home.dynamic_comment_preview_visible") {
            SettingSwitchItem(
                icon = commentPreviewIcon,
                title = settingItemTitle("home.dynamic_comment_preview_visible"),
                subtitle = "在动态卡片中显示相关评论摘要",
                checked = dynamicCommentPreviewVisible,
                onCheckedChange = onDynamicCommentPreviewVisibleChange,
                iconTint = siblingTints[9]
            )
        }
        SettingsAdaptiveDivider()
        com.android.purebilibili.feature.settings.DynamicVisibilityCheckPreference()
        SettingsAdaptiveDivider()
        SettingsItemAnchor("home.dynamic_image_preview_text_visible") {
            SettingSwitchItem(
                icon = previewTextIcon,
                title = settingItemTitle("home.dynamic_image_preview_text_visible"),
                subtitle = "打开图文动态图片时默认显示下方文字，可用右上角眼睛临时切换",
                checked = dynamicImagePreviewTextVisible,
                onCheckedChange = onDynamicImagePreviewTextVisibleChange,
                iconTint = siblingTints[2]
            )
        }
        SettingsAdaptiveDivider()
        SettingsSingleChoicePreference(
            title = "动态详情图片展示",
            subtitle = "缩略图可更快看到评论；详情页右上角可临时切换",
            options = com.android.purebilibili.core.store.SettingsManager.DynamicDetailImageLayout.entries.map { layout ->
                com.android.purebilibili.core.ui.components.AppSegmentOption(
                    value = layout,
                    label = layout.label
                )
            },
            selectedValue = dynamicDetailImageLayout,
            icon = imageLayoutIcon,
            iconTint = siblingTints[3],
            onSelectionChange = onDynamicDetailImageLayoutChange,
        )
        SettingsAdaptiveDivider()
        SettingsItemAnchor("home.dynamic_top_bar_collapse_on_scroll") {
            SettingSwitchItem(
                icon = topBarCollapseIcon,
                title = settingItemTitle("home.dynamic_top_bar_collapse_on_scroll"),
                subtitle = if (dynamicTopBarCollapseOnScroll) {
                    "浏览下方动态时收起分类栏；UP 主栏独立收起"
                } else {
                    "分类栏固定在顶部；UP 主栏独立收起"
                },
                checked = dynamicTopBarCollapseOnScroll,
                onCheckedChange = onDynamicTopBarCollapseOnScrollChange,
                iconTint = siblingTints[5]
            )
        }
        SettingsAdaptiveDivider()
        SettingsSingleChoicePreference(
            title = "动态页面布局",
            subtitle = "多列随屏幕宽度调整；列表每行一条",
            options = com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode.entries.map { mode ->
                com.android.purebilibili.core.ui.components.AppSegmentOption(
                    value = mode,
                    label = mode.label
                )
            },
            selectedValue = dynamicFeedLayoutMode,
            icon = feedIcon,
            iconTint = siblingTints[6],
            onSelectionChange = onDynamicFeedLayoutModeChange,
        )
        SettingsAdaptiveDivider()
        FeedDynamicTabVisibilityItem(
            icon = visibilityIcon,
            visibleTabIds = dynamicVisibleTabIds,
            onTabVisibilityChange = onDynamicTabVisibilityChange,
            tabOrder = dynamicTabOrder,
            onTabOrderChange = onDynamicTabOrderChange,
            iconTint = siblingTints[7]
        )
        SettingsAdaptiveDivider()
        SettingSliderItem(
            icon = refreshIcon,
            title = "首页刷新数量",
            subtitle = resolveHomeRefreshCountSummary(homeRefreshCount),
            value = homeRefreshCount.toFloat(),
            onValueChange = { value -> onHomeRefreshCountChange(value.roundToInt()) },
            valueRange = resolveHomeRefreshSliderRange(),
            steps = resolveHomeRefreshSliderSteps(),
            valueFormatter = { value -> value.roundToInt().toString() },
            iconTint = siblingTints[8]
        )
    }
}

@Composable
private fun FeedDynamicTabVisibilityItem(
    icon: ImageVector,
    visibleTabIds: Set<String>,
    onTabVisibilityChange: (String) -> Unit,
    tabOrder: List<String>,
    onTabOrderChange: (List<String>) -> Unit,
    iconTint: Color
) {
    val listCapabilities = rememberAdaptiveListVisualCapabilities()
    val visualSpec = listCapabilities.componentSpec
    val rowSpec = listCapabilities.rowSpec
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(iconTint)
    val iconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = rowSpec.insideHorizontalPaddingDp.dp,
                vertical = rowSpec.insideVerticalPaddingDp.dp
            )
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (LocalAppUiStyle.current == AppUiStyle.MIUIX || com.android.purebilibili.core.ui.components.useColorfulPreferenceIcons()) {
                SettingsFunctionIcon(
                    title = "动态分类",
                    imageVector = icon,
                )
            } else Box(
                modifier = Modifier
                    .size(visualSpec.iconContainerSizeDp.dp)
                    .adaptiveSquircleBackground(
                        color = effectiveIconTint,
                        cornerRadius = visualSpec.iconCornerRadiusDp.dp,
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    icon,
                    contentDescription = null,
                    tint = iconContentColor,
                    modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = "动态分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppContentColors.primaryText()
                )
                Spacer(modifier = Modifier.height(2.dp))
                AppText(
                    text = "选择动态分类和顺序，至少保留一个；点关注用户可在“全部”中查看其动态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val orderedTabs = remember(tabOrder) {
            allDynamicTabSpecs.sortedWith(
                compareBy(
                    { spec -> tabOrder.indexOf(spec.id).takeIf { it >= 0 } ?: tabOrder.size },
                    { it.logicalIndex }
                )
            )
        }
        orderedTabs.forEachIndexed { index, tab ->
            val checked = tab.id in visibleTabIds
            val enabled = shouldAllowDynamicTabVisibilityToggleOff(
                currentVisibleTabIds = visibleTabIds,
                targetTabId = tab.id
            )
            SettingSwitchItem(
                title = tab.title,
                checked = checked,
                onCheckedChange = { onTabVisibilityChange(tab.id) },
                enabled = enabled
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AppIconButton(
                    enabled = index > 0,
                    onClick = {
                        val newOrder = orderedTabs.map { it.id }.toMutableList()
                        val target = newOrder[index - 1]
                        newOrder[index - 1] = newOrder[index]
                        newOrder[index] = target
                        onTabOrderChange(newOrder)
                    }
                ) {
                    AppIcon(
                        androidx.compose.material.icons.Icons.Default.KeyboardArrowUp,
                        contentDescription = "上移${tab.title}"
                    )
                }
                AppIconButton(
                    enabled = index < orderedTabs.lastIndex,
                    onClick = {
                        val newOrder = orderedTabs.map { it.id }.toMutableList()
                        val target = newOrder[index + 1]
                        newOrder[index + 1] = newOrder[index]
                        newOrder[index] = target
                        onTabOrderChange(newOrder)
                    }
                ) {
                    AppIcon(
                        androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                        contentDescription = "下移${tab.title}"
                    )
                }
            }
            if (index != orderedTabs.lastIndex) {
                SettingsAdaptiveDivider()
            }
        }
    }
}

internal fun resolveHomeRefreshCountSummary(count: Int): String {
    return "单次最多请求 $count 条推荐内容，实际显示可能更少"
}

internal fun resolveHomeRefreshSliderRange(): ClosedFloatingPointRange<Float> {
    return MIN_HOME_REFRESH_COUNT.toFloat()..MAX_HOME_REFRESH_COUNT.toFloat()
}

internal fun resolveHomeRefreshSliderSteps(): Int {
    return (MAX_HOME_REFRESH_COUNT - MIN_HOME_REFRESH_COUNT - 1).coerceAtLeast(0)
}

@Composable
fun PrivacySection(
    privacyModeEnabled: Boolean,
    searchSuggestionsEnabled: Boolean,
    privacyContentAuthenticationEnabled: Boolean,
    onPrivacyModeChange: (Boolean) -> Unit,
    onSearchSuggestionsChange: (Boolean) -> Unit,
    onPrivacyContentAuthenticationChange: (Boolean) -> Unit,
    onPermissionClick: () -> Unit,
    onSpacePrivacyClick: () -> Unit,
    onLoginActivityClick: () -> Unit,
    onMessageNotificationClick: () -> Unit,
    onBlockedListClick: () -> Unit, // [New]
    onCommentFraudHistoryClick: () -> Unit // [New]
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val searchHintEnabled by remember(context) {
        com.android.purebilibili.core.store.SearchHintSettingsStore.isEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val personalRecapEnabled by remember(context) {
        SettingsManager.getSubscriptionRecapEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    val siblingTints = rememberSettingsSiblingIconTints(4, paletteOffset = 4)
    val permissionVisual = rememberSettingsEntryVisual(SettingsSearchTarget.PERMISSION)
    val spacePrivacyVisual = rememberSettingsEntryVisual(SettingsSearchTarget.SPACE_PRIVACY)
    val loginActivityVisual = rememberSettingsEntryVisual(SettingsSearchTarget.LOGIN_ACTIVITY)
    val messageNotificationVisual =
        rememberSettingsEntryVisual(SettingsSearchTarget.MESSAGE_NOTIFICATION)
    val blockedListVisual = rememberSettingsEntryVisual(SettingsSearchTarget.BLOCKED_LIST)
    val historyIcon = rememberSettingsSemanticIcon(SettingsIconRole.PRIVACY_HISTORY)
    val searchRecommendationIcon = rememberSettingsSemanticIcon(SettingsIconRole.PRIVACY_SEARCH_RECOMMENDATION)
    val personalRecapIcon = rememberSettingsSemanticIcon(SettingsIconRole.PERSONAL_RECAP)
    val contentAuthenticationIcon = rememberSettingsSemanticIcon(
        SettingsIconRole.PRIVACY_CONTENT_AUTHENTICATION,
    )

    val showHotControl by SettingsManager.getSearchLandingSectionVisible(context, 0).collectAsStateWithLifecycle(initialValue = true)
    val showHistoryControl by SettingsManager.getSearchLandingSectionVisible(context, 1).collectAsStateWithLifecycle(initialValue = true)
    val showDiscoverControl by SettingsManager.getSearchLandingSectionVisible(context, 2).collectAsStateWithLifecycle(initialValue = true)

    SettingsCardGroup {
        listOf(
            Triple("privacy.search_hot_control", showHotControl, SettingsIconRole.SEARCH_HOT_SECTION),
            Triple("privacy.search_history_control", showHistoryControl, SettingsIconRole.SEARCH_HISTORY_SECTION),
            Triple("privacy.search_discover_control", showDiscoverControl, SettingsIconRole.SEARCH_DISCOVER_SECTION),
        ).forEachIndexed { index, (id, visible, iconRole) ->
            SettingsItemAnchor(id) {
                SettingSwitchItem(
                    icon = rememberSettingsSemanticIcon(iconRole),
                    title = settingItemTitle(id),
                    subtitle = if (index == 1) "关闭后隐藏搜索历史，记录设置和已有历史不变" else "关闭后隐藏搜索页对应分区",
                    checked = visible,
                    onCheckedChange = { enabled ->
                        scope.launch { SettingsManager.setSearchLandingSectionVisible(context, index, enabled) }
                    },
                )
            }
            SettingsAdaptiveDivider()
        }
        SettingSwitchItem(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.HOME_SEARCH_GLASS),
            title = "搜索框推荐词",
            subtitle = "在搜索框显示推荐词；关闭后显示固定提示",
            checked = searchHintEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    com.android.purebilibili.core.store.SearchHintSettingsStore.setEnabled(context, enabled)
                }
            },
            iconTint = siblingTints[0],
        )
        SettingsAdaptiveDivider()

        SettingsItemAnchor("privacy.search_suggestions_enabled") {
            SettingSwitchItem(
                icon = searchRecommendationIcon,
                title = settingItemTitle("privacy.search_suggestions_enabled"),
                subtitle = "开启时使用官方搜索推荐；关闭后改用公开热搜词",
                checked = searchSuggestionsEnabled,
                onCheckedChange = onSearchSuggestionsChange,
                iconTint = siblingTints[0],
            )
        }
        SettingsAdaptiveDivider()
        SettingsItemAnchor("privacy.privacy_mode_enabled") {
            SettingSwitchItem(
                icon = historyIcon,
                title = settingItemTitle("privacy.privacy_mode_enabled"),
                subtitle = "开启后不再新增播放和搜索历史，已有记录不会被删除",
                checked = privacyModeEnabled,
                onCheckedChange = onPrivacyModeChange,
                iconTint = siblingTints[0]
            )
        }
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = personalRecapIcon,
            title = "我的回顾",
            subtitle = "在历史页显示阅读与观看统计、趋势图和最近爱看的 UP 主",
            checked = personalRecapEnabled,
            onCheckedChange = { enabled ->
                scope.launch { SettingsManager.setSubscriptionRecapEnabled(context, enabled) }
            },
            iconTint = siblingTints[0],
        )
        SettingsAdaptiveDivider()
        SettingsItemAnchor("privacy.privacy_content_authentication_enabled") {
            SettingSwitchItem(
                icon = contentAuthenticationIcon,
                title = settingItemTitle("privacy.privacy_content_authentication_enabled"),
                subtitle = "进入收藏、历史等页面前使用指纹、人脸或锁屏密码确认身份",
                checked = privacyContentAuthenticationEnabled,
                onCheckedChange = onPrivacyContentAuthenticationChange,
                iconTint = siblingTints[1]
            )
        }
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = spacePrivacyVisual.icon,
            iconPainter = spacePrivacyVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.SPACE_PRIVACY).title,
            subtitle = settingsDestinationCopy(SettingsSearchTarget.SPACE_PRIVACY).summary,
            onClick = onSpacePrivacyClick,
            iconTint = spacePrivacyVisual.iconTint,
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = loginActivityVisual.icon,
            iconPainter = loginActivityVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.LOGIN_ACTIVITY).title,
            subtitle = settingsDestinationCopy(SettingsSearchTarget.LOGIN_ACTIVITY).summary,
            onClick = onLoginActivityClick,
            iconTint = loginActivityVisual.iconTint,
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = permissionVisual.icon,
            iconPainter = permissionVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.PERMISSION).title,
            onClick = onPermissionClick,
            iconTint = siblingTints[2]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = blockedListVisual.icon,
            iconPainter = blockedListVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.BLOCKED_LIST).title,
            value = settingsDestinationCopy(SettingsSearchTarget.BLOCKED_LIST).summary,
            onClick = onBlockedListClick,
            iconTint = siblingTints[3]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_shield_24),
            title = "评论检查记录",
            value = "查看已发评论及其显示状态",
            onClick = onCommentFraudHistoryClick,
            iconTint = AppContentColors.secondaryText()
        )
    }
}

@Composable
fun DataStorageSection(
    customDownloadPath: String?,
    customImageSavePath: String?,
    cacheSize: String,
    autoCacheClearInterval: SettingsManager.AutoCacheClearInterval,
    autoCacheClearThresholdGb: Int,
    onSettingsShareClick: () -> Unit,
    onWebDavBackupClick: () -> Unit,
    onDownloadPathClick: () -> Unit,
    onImageSavePathClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onAutoCacheClearIntervalChange: (SettingsManager.AutoCacheClearInterval) -> Unit,
    onAutoCacheClearThresholdChange: (Int) -> Unit
) {
    val settingsShareVisual = rememberSettingsEntryVisual(SettingsSearchTarget.SETTINGS_SHARE)
    val webDavVisual = rememberSettingsEntryVisual(SettingsSearchTarget.WEBDAV_BACKUP)
    val downloadPathVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DOWNLOAD_PATH)
    val imageSavePathVisual = rememberSettingsEntryVisual(SettingsSearchTarget.IMAGE_SAVE_PATH)
    val clearCacheVisual = rememberSettingsEntryVisual(SettingsSearchTarget.CLEAR_CACHE)
    val autoClearCacheIcon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_CLEAR_CACHE)
    val cacheCapacityIcon = rememberSettingsSemanticIcon(SettingsIconRole.CACHE_CAPACITY_LIMIT)
    val siblingTints = rememberSettingsSiblingIconTints(7, paletteOffset = 2)
    val showExplicitActionChevron =
        rememberAdaptiveListVisualCapabilities().showExplicitActionChevron

    SettingsCardGroup {
        SettingClickableItem(
            icon = settingsShareVisual.icon,
            iconPainter = settingsShareVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.SETTINGS_SHARE).title,
            value = settingsDestinationCopy(SettingsSearchTarget.SETTINGS_SHARE).summary,
            onClick = onSettingsShareClick,
            iconTint = siblingTints[0]
        )
        SettingsAdaptiveDivider()
        // WebDAV 是“备份副本”场景，使用双文档图标比链路图标更贴合语义。
        SettingClickableItem(
            icon = webDavVisual.icon,
            iconPainter = webDavVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.WEBDAV_BACKUP).title,
            value = settingsDestinationCopy(SettingsSearchTarget.WEBDAV_BACKUP).summary,
            onClick = onWebDavBackupClick,
            iconTint = siblingTints[1]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = downloadPathVisual.icon,
            iconPainter = downloadPathVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.DOWNLOAD_PATH).title,
            value = if (customDownloadPath != null) "自定义" else "默认",
            onClick = onDownloadPathClick,
            iconTint = siblingTints[2],
            showChevron = showExplicitActionChevron
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = imageSavePathVisual.icon,
            iconPainter = imageSavePathVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.IMAGE_SAVE_PATH).title,
            value = if (customImageSavePath != null) "已选择目录" else "默认",
            onClick = onImageSavePathClick,
            iconTint = siblingTints[3],
            showChevron = showExplicitActionChevron
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = clearCacheVisual.icon,
            iconPainter = clearCacheVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.CLEAR_CACHE).title,
            value = cacheSize,
            onClick = onClearCacheClick,
            iconTint = siblingTints[4],
            showChevron = showExplicitActionChevron
        )
        SettingsAdaptiveDivider()
        SettingsSingleChoicePreference(
            title = "自动清理缓存",
            subtitle = "到达设定周期后，在应用启动时删除可重新下载的缓存",
            options = SettingsManager.AutoCacheClearInterval.entries.map { interval ->
                com.android.purebilibili.core.ui.components.AppSegmentOption(
                    value = interval,
                    label = interval.label
                )
            },
            selectedValue = autoCacheClearInterval,
            icon = autoClearCacheIcon,
            iconTint = siblingTints[5],
            onSelectionChange = onAutoCacheClearIntervalChange
        )
        SettingsAdaptiveDivider()
        SettingSliderItem(
            icon = cacheCapacityIcon,
            title = "缓存容量上限",
            subtitle = "缓存超过设定容量后，在应用启动时自动清理；默认 5 GB",
            value = autoCacheClearThresholdGb.toFloat(),
            onValueChange = { value -> onAutoCacheClearThresholdChange(value.roundToInt()) },
            valueRange = 1f..20f,
            steps = 18,
            valueFormatter = { value -> "${value.roundToInt()} GB" },
            iconTint = siblingTints[6]
        )
    }
}

@Composable
private fun PluginCenterSection(
    pluginCount: Int,
    onPluginsClick: () -> Unit,
) {
    val pluginsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.PLUGINS)
    SettingsCardGroup {
        SettingClickableItem(
            icon = pluginsVisual.icon,
            iconPainter = pluginsVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.PLUGINS).title,
            value = "$pluginCount 个已启用",
            onClick = onPluginsClick,
            iconTint = pluginsVisual.iconTint,
        )
    }
}

@Composable
private fun DiagnosticsSection(
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
    enhancedDiagnosticLoggingEnabled: Boolean,
    onCrashTrackingChange: (Boolean) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onEnhancedDiagnosticLoggingChange: (Boolean) -> Unit,
    onExportLogsClick: () -> Unit,
) {
    val context = LocalContext.current
    val siblingTints = rememberSettingsSiblingIconTints(6, paletteOffset = 5)
    val exportLogsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.EXPORT_LOGS)
    val useMd3ExportLogsDescription = LocalAppUiStyle.current == AppUiStyle.MATERIAL3
    val exportLogsDescription = "默认导出崩溃摘要，详细错误记录需另选"
    val proxySettings by NetworkProxyStore.settings.collectAsStateWithLifecycle(
        initialValue = NetworkProxyStore.getSync(context)
    )
    var showProxyDialog by remember { mutableStateOf(false) }
    var showEnhancedDiagnosticConsent by remember { mutableStateOf(false) }

    SettingsCardGroup {
        SettingsItemAnchor("diagnostics.crash_tracking_enabled") {
            SettingSwitchItem(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.CRASH_TRACKING),
                title = settingItemTitle("diagnostics.crash_tracking_enabled"),
                subtitle = "记录崩溃和严重故障信息，帮助定位问题；默认开启",
                checked = crashTrackingEnabled,
                onCheckedChange = onCrashTrackingChange,
                iconTint = siblingTints[0],
            )
        }
        SettingsAdaptiveDivider()
        SettingsItemAnchor("diagnostics.analytics_enabled") {
            SettingSwitchItem(
                icon = rememberSettingsSemanticIcon(SettingsIconRole.ANALYTICS),
                title = settingItemTitle("diagnostics.analytics_enabled"),
                subtitle = "匿名统计每日活跃和基础功能使用情况，不包含账号内容；默认开启",
                checked = analyticsEnabled,
                onCheckedChange = onAnalyticsChange,
                iconTint = siblingTints[1],
            )
        }
        SettingsAdaptiveDivider()
        SettingsItemAnchor("diagnostics.enhanced_diagnostic_logging_enabled") {
            SettingSwitchItem(
                icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_pest_control_24),
                title = settingItemTitle("diagnostics.enhanced_diagnostic_logging_enabled"),
                subtitle = if (enhancedDiagnosticLoggingEnabled) {
                    "记录详细运行信息；关闭后清除，基础错误日志保留"
                } else {
                    "记录更多排查信息，隐藏账号凭据与观看内容"
                },
                checked = enhancedDiagnosticLoggingEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) showEnhancedDiagnosticConsent = true
                    else onEnhancedDiagnosticLoggingChange(false)
                },
                iconTint = siblingTints[2],
            )
        }
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_lan_24),
            title = "网络代理",
            subtitle = formatAppHttpProxySummary(proxySettings) +
                "（作用于应用内网络请求；视频播放始终直连）",
            checked = proxySettings.enabled,
            onCheckedChange = { enabled ->
                if (enabled && !isAppHttpProxyConfigured(proxySettings)) {
                    showProxyDialog = true
                } else {
                    NetworkProxyStore.save(context, proxySettings.copy(enabled = enabled))
                }
            },
            iconTint = siblingTints[3],
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_hub_24),
            title = "代理地址",
            value = formatAppHttpProxyEndpoint(proxySettings),
            subtitle = "填写“主机:端口”，例如 127.0.0.1:7890",
            onClick = { showProxyDialog = true },
            iconTint = siblingTints[4],
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = exportLogsVisual.icon,
            iconPainter = exportLogsVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.EXPORT_LOGS).title,
            subtitle = exportLogsDescription.takeIf { useMd3ExportLogsDescription },
            value = exportLogsDescription.takeUnless { useMd3ExportLogsDescription },
            onClick = onExportLogsClick,
            iconTint = siblingTints[5],
        )
    }

    if (showProxyDialog) {
        NetworkProxyEditDialog(
            initial = proxySettings,
            onDismiss = { showProxyDialog = false },
            onConfirm = { next ->
                NetworkProxyStore.save(context, next)
                showProxyDialog = false
            },
        )
    }

    if (showEnhancedDiagnosticConsent) {
        AppAlertDialog(
            onDismissRequest = { showEnhancedDiagnosticConsent = false },
            title = { AppText("开启详细运行日志？", fontWeight = FontWeight.Bold) },
            text = {
                AppText(
                    text = "将记录应用版本、设备与系统环境、请求结果、播放器状态和错误堆栈。" +
                        "账号凭据、用户标识、视频标识、搜索词和用户输入会在写入前脱敏。" +
                        "详细日志仅保存在应用私有目录（最多 256KB），不会自动上传；关闭后会清除详细日志。" +
                        "基础错误与启动诊断另行保留（最多 64KB），崩溃快照单独保存，可在诊断设置中清理。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),
                )
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        showEnhancedDiagnosticConsent = false
                        onEnhancedDiagnosticLoggingChange(true)
                    }
                ) { AppText("同意并开启") }
            },
            dismissButton = {
                AppDialogAction(onClick = { showEnhancedDiagnosticConsent = false }) {
                    AppText("取消")
                }
            },
        )
    }
}

/**
 * Adaptive dialog for app HTTP proxy host/port (API traffic only).
 * Uses design-system adaptive entry points so Material 3 style renders MD3
 * primitives and Miuix style renders Miuix native components.
 */
@Composable
private fun NetworkProxyEditDialog(
    initial: AppHttpProxySettings,
    onDismiss: () -> Unit,
    onConfirm: (AppHttpProxySettings) -> Unit,
) {
    var host by remember(initial) { mutableStateOf(initial.host) }
    var portText by remember(initial) { mutableStateOf(initial.portText) }
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var splitByDomain by remember(initial) { mutableStateOf(initial.routeMode == ProxyRouteMode.SPLIT) }
    var domainsText by remember(initial) {
        mutableStateOf(initial.proxiedDomains.joinToString(separator = "\n"))
    }
    val draft = AppHttpProxySettings(
        enabled = enabled,
        host = sanitizeProxyHostInput(host),
        portText = sanitizeProxyPortInput(portText),
        routeMode = if (splitByDomain) ProxyRouteMode.SPLIT else ProxyRouteMode.GLOBAL,
        proxiedDomains = parseProxyDomainRules(domainsText),
    )
    val canSave = !enabled || isAppHttpProxyConfigured(draft)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(
                text = "设置网络代理",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(
                    text = if (enabled && splitByDomain) {
                        "只有下方域名走代理，其余请求（包括视频播放）都直连。"
                    } else {
                        "作用于应用内的网络请求（接口、登录、RSS、图片等）。视频播放始终直连，避免本地代理端口不可用导致卡顿。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
                AppTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = "主机",
                    placeholder = "127.0.0.1",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = portText,
                    onValueChange = { portText = sanitizeProxyPortInput(it) },
                    label = "端口",
                    placeholder = "7890",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppText(
                        text = "启用代理",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    AppAdaptiveSwitch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else 0.38f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = "仅指定域名走代理",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        AppText(
                            text = "开启后只在访问下方域名时使用代理，其余请求直连",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                    AppAdaptiveSwitch(
                        checked = splitByDomain,
                        onCheckedChange = { splitByDomain = it },
                        enabled = enabled,
                    )
                }
                if (enabled && splitByDomain) {
                    AppTextField(
                        value = domainsText,
                        onValueChange = { domainsText = it },
                        label = "代理域名（每行一个）",
                        placeholder = "aicu.cc",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            AppText(
                                text = "填写主域名即可，自动匹配其子域；例如 aicu.cc 会同时覆盖 www.aicu.cc",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                            )
                        },
                    )
                }
                if (enabled && !isAppHttpProxyConfigured(draft)) {
                    AppText(
                        text = "请填写有效的主机与端口（1–65535）",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.error(),
                    )
                }
            }
        },
        confirmButton = {
            AppDialogAction(
                onClick = {
                    if (canSave) {
                        onConfirm(draft)
                    }
                },
            ) {
                AppText(
                    text = "保存",
                    color = if (canSave) {
                        AppContentColors.accent()
                    } else {
                        AppContentColors.primaryText().copy(alpha = 0.38f)
                    },
                )
            }
        },
        dismissButton = {
            AppDialogAction(onClick = onDismiss) {
                AppText("取消")
            }
        },
    )
}

@Composable
fun AboutSection(
    versionName: String,
    appIconKey: String,
    easterEggEnabled: Boolean,
    onLicenseClick: () -> Unit,
    onGithubClick: () -> Unit,
    onVerificationClick: () -> Unit,
    onBuildSourceClick: () -> Unit,
    onBuildFingerprintClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onViewReleaseNotesClick: () -> Unit,
    autoCheckUpdateEnabled: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    appUpdateChannel: SettingsManager.AppUpdateChannel,
    onAppUpdateChannelChange: (SettingsManager.AppUpdateChannel) -> Unit,
    onVersionClick: () -> Unit,
    onReplayOnboardingClick: () -> Unit,
    onEasterEggChange: (Boolean) -> Unit,
    updateStatusText: String = "点击检查",
    isCheckingUpdate: Boolean = false,
    verificationLabel: String = "未验证",
    verificationSubtitle: String = "尚未获取官方发布信息",
    buildSourceValue: String = "本地构建",
    buildSourceSubtitle: String = "未关联 GitHub 官方发布",
    buildFingerprintValue: String = "未读取",
    buildFingerprintCopyValue: String = "未读取",
    buildFingerprintSubtitle: String = "尚未读取安装包 SHA-256",
    versionClickCount: Int = 0,
    versionClickThreshold: Int = EasterEggs.VERSION_EASTER_EGG_THRESHOLD
) {
    val context = LocalContext.current
    val appIconAppearance by SettingsManager.getAppIconAppearance(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getAppIconAppearanceSync(context)
        )
    val appIconRes = remember(appIconKey, appIconAppearance) {
        resolveIconOptionPreviewRes(appIconKey, appIconAppearance)
    }
    var showUpdateHighlights by rememberSaveable { mutableStateOf(false) }
    if (showUpdateHighlights) {
        com.android.purebilibili.feature.settings.highlights.UpdateHighlightsHost(
            manualOpen = true,
            onManualDismiss = { showUpdateHighlights = false },
        )
    }
    var detailDialogContent by remember { mutableStateOf<AppBuildInfoDialogContent?>(null) }
    val easterEggTint = rememberSettingsEntryTint(AppSemanticAccentRole.TERTIARY, AppContentColors.warning())
    val updateSiblingTints = rememberSettingsSiblingIconTints(5, paletteOffset = 3)
    val licensesVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_SOURCE_LICENSES)
    val openSourceHomeVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_SOURCE_HOME)
    val checkUpdateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.CHECK_UPDATE)
    val releaseNotesVisual = rememberSettingsEntryVisual(SettingsSearchTarget.VIEW_RELEASE_NOTES)
    val replayOnboardingVisual = rememberSettingsEntryVisual(SettingsSearchTarget.REPLAY_ONBOARDING)
    val notificationIcon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_CHECK_UPDATE)
    val infoIcon = rememberSettingsSemanticIcon(SettingsIconRole.APP_VERSION)
    val sparklesIcon = rememberSettingsSemanticIcon(SettingsIconRole.EASTER_EGG)
    val verificationIcon = rememberSettingsSemanticIcon(SettingsIconRole.BUILD_VERIFICATION)
    val buildSourceIcon = rememberSettingsSemanticIcon(SettingsIconRole.BUILD_SOURCE)
    val buildFingerprintIcon = rememberSettingsSemanticIcon(SettingsIconRole.BUILD_FINGERPRINT)

    val safeThreshold = versionClickThreshold.coerceAtLeast(1)
    val normalizedClickCount = versionClickCount.coerceAtLeast(0)
    val versionProgress = normalizedClickCount.coerceAtMost(safeThreshold).toFloat() / safeThreshold
    val versionIconTint = animateColorAsState(
        targetValue = when {
            normalizedClickCount >= safeThreshold -> AppContentColors.success()
            versionProgress >= 0.85f -> AppContentColors.warning()
            versionProgress >= 0.5f -> AppContentColors.warning()
            normalizedClickCount > 0 -> AppContentColors.accent()
            else -> AppContentColors.accent()
        },
        label = "versionIconTint"
    ).value
    val versionHint = when {
        normalizedClickCount <= 0 -> null
        normalizedClickCount >= safeThreshold -> "彩蛋已解锁"
        else -> "还差 ${safeThreshold - normalizedClickCount} 次"
    }
    val versionValue = buildString {
        append("v$versionName")
        versionHint?.let {
            append(" · ")
            append(it)
        }
    }

    detailDialogContent?.let { dialogContent ->
        val dialogScrollState = rememberScrollState()
        AppAlertDialog(
            onDismissRequest = { detailDialogContent = null },
            title = { AppText(dialogContent.title) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(dialogScrollState)
                ) {
                    AppText(
                        text = dialogContent.value,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppContentColors.primaryText()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        text = dialogContent.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.secondaryText()
                    )
                }
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        when (dialogContent.action) {
                            AppBuildInfoDialogAction.VIEW_VERIFICATION -> onVerificationClick()
                            AppBuildInfoDialogAction.VIEW_BUILD_SOURCE -> onBuildSourceClick()
                            AppBuildInfoDialogAction.VIEW_BUILD_FINGERPRINT -> onBuildFingerprintClick()
                        }
                        detailDialogContent = null
                    }
                ) {
                    AppText(dialogContent.actionLabel)
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { detailDialogContent = null }) {
                    AppText("关闭")
                }
            }
        )
    }

    AboutProjectOverviewCard(
        versionName = versionName,
        appIconRes = appIconRes,
    )
    Spacer(modifier = Modifier.height(12.dp))

    SettingsSectionTitle(title = "来源与验证")
    SettingsCardGroup {
        SettingClickableItem(
            icon = openSourceHomeVisual.icon,
            iconPainter = openSourceHomeVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.OPEN_SOURCE_HOME).title,
            value = settingsDestinationCopy(SettingsSearchTarget.OPEN_SOURCE_HOME).summary,
            onClick = onGithubClick,
            iconTint = openSourceHomeVisual.iconTint,
            enableCopy = true,
            onCopyRequest = rememberClipboardCopyHandler(),
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = licensesVisual.icon,
            iconPainter = licensesVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.OPEN_SOURCE_LICENSES).title,
            onClick = onLicenseClick,
            iconTint = licensesVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = verificationIcon,
            title = "安装包来源验证",
            subtitle = verificationSubtitle,
            value = verificationLabel,
            onClick = {
                detailDialogContent = resolveVerificationDialogContent(
                    label = verificationLabel,
                    summary = verificationSubtitle
                )
            },
            iconTint = when (verificationLabel) {
                "已验证" -> AppContentColors.success()
                "基本可验证" -> AppContentColors.accent()
                else -> AppContentColors.warning()
            }
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = buildSourceIcon,
            title = "版本来源",
            subtitle = buildSourceSubtitle,
            value = buildSourceValue,
            onClick = {
                detailDialogContent = resolveBuildSourceDialogContent(
                    value = buildSourceValue,
                    subtitle = buildSourceSubtitle
                )
            },
            iconTint = AppContentColors.secondaryText(),
            enableCopy = true,
            onCopyRequest = rememberClipboardCopyHandler(),
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = buildFingerprintIcon,
            title = "SHA-256",
            subtitle = buildFingerprintSubtitle,
            value = buildFingerprintValue,
            copyValue = buildFingerprintCopyValue,
            onClick = {
                detailDialogContent = resolveBuildFingerprintDialogContent(
                    value = buildFingerprintValue,
                    fullValue = buildFingerprintCopyValue,
                    subtitle = buildFingerprintSubtitle
                )
            },
            iconTint = AppContentColors.secondaryText(),
            enableCopy = true,
            onCopyRequest = rememberClipboardCopyHandler(),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    SettingsSectionTitle(title = "更新")
    SettingsCardGroup {
        SettingClickableItem(
            icon = sparklesIcon,
            title = "本次更新",
            subtitle = "了解新功能，先预览再决定是否体验",
            onClick = { showUpdateHighlights = true },
            iconTint = AppContentColors.accent(),
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = checkUpdateVisual.icon,
            iconPainter = checkUpdateVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.CHECK_UPDATE).title,
            value = if (isCheckingUpdate) "检查中…" else updateStatusText,
            onClick = onCheckUpdateClick,
            iconTint = updateSiblingTints[1]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = releaseNotesVisual.icon,
            iconPainter = releaseNotesVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.VIEW_RELEASE_NOTES).title,
            onClick = onViewReleaseNotesClick,
            iconTint = updateSiblingTints[2]
        )
        SettingsAdaptiveDivider()
        SettingsItemAnchor("help.auto_check_update_enabled") {
            SettingSwitchItem(
                icon = notificationIcon,
                title = settingItemTitle("help.auto_check_update_enabled"),
                subtitle = resolveAutoCheckUpdateSubtitle(autoCheckEnabled = autoCheckUpdateEnabled),
                checked = autoCheckUpdateEnabled,
                onCheckedChange = onAutoCheckUpdateChange,
                iconTint = updateSiblingTints[3]
            )
        }
        SettingsAdaptiveDivider()
        SettingsSingleChoicePreference(
            title = "更新渠道",
            subtitle = appUpdateChannel.description,
            options = SettingsManager.AppUpdateChannel.entries.map { channel ->
                com.android.purebilibili.core.ui.components.AppSegmentOption(
                    value = channel,
                    label = channel.label
                )
            },
            selectedValue = appUpdateChannel,
            icon = checkUpdateVisual.icon,
            iconTint = updateSiblingTints[4],
            onSelectionChange = onAppUpdateChannelChange
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    SettingsSectionTitle(title = "使用与反馈")
    SettingsCardGroup {
        SettingClickableItem(
            icon = infoIcon,
            title = "版本",
            value = versionValue,
            onClick = onVersionClick,
            iconTint = versionIconTint,
            enableCopy = true,
            onCopyRequest = rememberClipboardCopyHandler(),
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = replayOnboardingVisual.icon,
            iconPainter = replayOnboardingVisual.iconResId?.let { painterResource(id = it) },
            title = settingsDestinationCopy(SettingsSearchTarget.REPLAY_ONBOARDING).title,
            value = settingsDestinationCopy(SettingsSearchTarget.REPLAY_ONBOARDING).summary,
            onClick = onReplayOnboardingClick,
            iconTint = replayOnboardingVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingsItemAnchor("help.easter_egg_enabled") {
            SettingSwitchItem(
                icon = sparklesIcon,
                title = settingItemTitle("help.easter_egg_enabled"),
                subtitle = "刷新、点赞、投币、搜索时显示趣味提示",
                checked = easterEggEnabled,
                onCheckedChange = onEasterEggChange,
                iconTint = easterEggTint
            )
        }
    }
}

internal data class AboutDeveloper(
    val name: String,
    val githubLogin: String,
    val avatarResId: Int? = null,
    val avatarUrl: String? = null
) {
    val profileUrl: String get() = "https://github.com/$githubLogin"
}

// 名单依据仓库的 GitHub 贡献记录维护，合并同一开发者的别名，不收录自动化账号。
// 已有本地头像继续复用；新增开发者使用公开头像链接，不在关于页请求贡献者 API。
internal val AboutDevelopers = listOf(
    AboutDeveloper("jay3-yy", "jay3-yy", R.drawable.avatar_jay3_yy),
    AboutDeveloper(
        name = "Piracola",
        githubLogin = "Piracola",
        avatarUrl = "https://avatars.githubusercontent.com/u/116626041?v=4"
    ),
    AboutDeveloper(
        name = "CoderTCY",
        githubLogin = "CoderTCY",
        avatarUrl = "https://avatars.githubusercontent.com/u/73740771?v=4"
    ),
    AboutDeveloper(
        name = "1290000",
        githubLogin = "1290000",
        avatarUrl = "https://avatars.githubusercontent.com/u/223883074?v=4"
    ),
    AboutDeveloper("Chenx Dust", "chenx-dust", R.drawable.avatar_chenx_dust),
    AboutDeveloper("usontong", "usontong", R.drawable.avatar_usontong),
    AboutDeveloper("Leko", "lekoOwO", R.drawable.avatar_lekoowo),
    AboutDeveloper("TanakaLun", "TanakaLun", R.drawable.avatar_tanakalun),
    AboutDeveloper("Matt Van Horn", "mvanhorn", R.drawable.avatar_mvanhorn),
    AboutDeveloper("qyo123oyq", "qyo123oyq", R.drawable.avatar_qyo123oyq),
    AboutDeveloper(
        name = "maxzrb",
        githubLogin = "maxzrb",
        avatarUrl = "https://avatars.githubusercontent.com/u/114979598?v=4"
    ),
    AboutDeveloper(
        name = "xiaoniao427",
        githubLogin = "xiaoniao427",
        avatarUrl = "https://avatars.githubusercontent.com/u/115906803?v=4"
    ),
    AboutDeveloper(
        name = "zensu357",
        githubLogin = "zensu357",
        avatarUrl = "https://avatars.githubusercontent.com/u/109052061?v=4"
    ),
    AboutDeveloper(
        name = "Kurarion",
        githubLogin = "Kurarion",
        avatarUrl = "https://avatars.githubusercontent.com/u/29721634?v=4"
    ),
    AboutDeveloper(
        name = "MuonChaser",
        githubLogin = "MuonChaser",
        avatarUrl = "https://avatars.githubusercontent.com/u/140365375?v=4"
    ),
    AboutDeveloper(
        name = "tom613951",
        githubLogin = "tom613951",
        avatarUrl = "https://avatars.githubusercontent.com/u/111942871?v=4"
    ),
    AboutDeveloper(
        name = "Cornfy",
        githubLogin = "Cornfy",
        avatarUrl = "https://avatars.githubusercontent.com/u/97228122?v=4"
    ),
    AboutDeveloper(
        name = "cunminF",
        githubLogin = "cunminF",
        avatarUrl = "https://avatars.githubusercontent.com/u/326400924?v=4"
    ),
    AboutDeveloper(
        name = "converk",
        githubLogin = "converk",
        avatarUrl = "https://avatars.githubusercontent.com/u/34793421?v=4"
    ),
    AboutDeveloper(
        name = "Hikari0618",
        githubLogin = "Hikari0618",
        avatarUrl = "https://avatars.githubusercontent.com/u/90288898?v=4"
    ),
    AboutDeveloper(
        name = "ajax-prog",
        githubLogin = "ajax-prog",
        avatarUrl = "https://avatars.githubusercontent.com/u/175576147?v=4"
    ),
    AboutDeveloper(
        name = "bugrsix",
        githubLogin = "bugrsix",
        avatarUrl = "https://avatars.githubusercontent.com/u/104134191?v=4"
    )
)

private val AboutSlogans = listOf(
    """
    删繁留简见初心,
    精雕细磨显匠心。
    弹幕浓淡随手调,
    原生丝滑不染尘。
    """.trimIndent(),
    """
    广告退场方显净,
    不扰清欢伴此心。
    隐私自留不上传,
    一屏干净任你行。
    """.trimIndent(),
    """
    弱水三千凡君取,
    繁简去留只凭心。
    插件添之随所愿,
    本体常净似初晨。
    """.trimIndent()
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AboutProjectOverviewCard(
    versionName: String,
    appIconRes: Int,
    developers: List<AboutDeveloper> = AboutDevelopers
) {
    val slogan = remember { AboutSlogans.random() }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppCardShape.Semantic(ContainerLevel.Dialog),
        colors = AppCardDefaults.colors(
            containerColor = AppSurfaceTokens.cardContainer()
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = appIconRes,
                    contentDescription = "BiliPai 图标",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "BiliPai",
                        style = MaterialTheme.typography.headlineSmall,
                        color = AppContentColors.primaryText()
                    )
                    AppText(
                        text = "v$versionName",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppContentColors.secondaryText()
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .heightIn(min = 76.dp)
                        .clip(CircleShape)
                        .background(AppContentColors.accent().copy(alpha = 0.28f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                AppText(
                    text = slogan,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),

                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            AppText(
                text = "开发者",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.secondaryText()
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                developers.forEach { developer ->
                    AboutDeveloperItem(developer = developer)
                }
            }
        }
    }
}

@Composable
private fun AboutDeveloperItem(
    developer: AboutDeveloper
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(AppShapes.container(ContainerLevel.Chip))
            .clickable { uriHandler.openUri(developer.profileUrl) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            developer.avatarUrl?.let { avatarUrl ->
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "${developer.name} 头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } ?: developer.avatarResId?.let { avatarResId ->
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = "${developer.name} 头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = developer.name,
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.primaryText(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
