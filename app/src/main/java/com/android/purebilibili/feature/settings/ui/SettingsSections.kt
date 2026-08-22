package com.android.purebilibili.feature.settings
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.rememberAppCollectionIcon
import com.android.purebilibili.core.ui.rememberAppDynamicIcon
import com.android.purebilibili.core.ui.rememberAppInfoIcon
import com.android.purebilibili.core.ui.rememberAppLockIcon
import com.android.purebilibili.core.ui.rememberAppNotificationIcon
import com.android.purebilibili.core.ui.rememberAppRefreshIcon
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.ui.rememberAppSparklesIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOffIcon
import com.android.purebilibili.core.ui.rememberAppWarningIcon
import com.android.purebilibili.core.ui.rememberAppAnalyticsIcon
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.adaptiveSquircleBackground
import com.android.purebilibili.core.ui.AppSemanticAccentRole
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.util.EasterEggs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import com.android.purebilibili.core.ui.common.copyOnLongPress
import com.android.purebilibili.core.ui.components.AppAdaptiveSwitch
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.rememberAdaptivePreferenceIconContentColor
import com.android.purebilibili.core.ui.components.rememberAdaptivePreferenceIconContainerColor
import com.android.purebilibili.core.ui.components.rememberAdaptivePreferenceIconTint
import com.android.purebilibili.core.ui.components.rememberAdaptiveListVisualCapabilities
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.core.store.MAX_HOME_REFRESH_COUNT
import com.android.purebilibili.core.store.MIN_HOME_REFRESH_COUNT
import com.android.purebilibili.core.store.NetworkProxyStore
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.network.policy.AppHttpProxySettings
import com.android.purebilibili.core.network.policy.formatAppHttpProxyEndpoint
import com.android.purebilibili.core.network.policy.formatAppHttpProxySummary
import com.android.purebilibili.core.network.policy.isAppHttpProxyConfigured
import com.android.purebilibili.core.network.policy.sanitizeProxyHostInput
import com.android.purebilibili.core.network.policy.sanitizeProxyPortInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.feature.dynamic.allDynamicTabSpecs
import com.android.purebilibili.feature.dynamic.shouldAllowDynamicTabVisibilityToggleOff
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════
//  UI 组件 (Stateless Components)
// ═══════════════════════════════════════════════════

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
private fun SettingsCardGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsGroup(
        presentation = AppPreferenceGroupPresentation.FLAT,
    ) {
        content()
    }
}

@Composable
fun SupportAuthorCompactSection(
    onDonateClick: () -> Unit
) {
    val donateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DONATE)

    SettingsCardGroup {
        SettingClickableItem(
            icon = donateVisual.icon,
            iconPainter = donateVisual.iconResId?.let { painterResource(id = it) },
            title = "打赏作者",
            value = "支持开发",
            onClick = onDonateClick,
            iconTint = donateVisual.iconTint,
            enableCopy = false
        )
    }
}

@Composable
fun GeneralSection(
    onAppearanceClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onBottomBarClick: () -> Unit
) {
    val appearanceVisual = rememberSettingsEntryVisual(SettingsSearchTarget.APPEARANCE)
    val playbackVisual = rememberSettingsEntryVisual(SettingsSearchTarget.PLAYBACK)
    val bottomBarVisual = rememberSettingsEntryVisual(SettingsSearchTarget.BOTTOM_BAR)
    val siblingTints = remember { resolveSettingsSiblingIconTints(3) }

    SettingsCardGroup {
        SettingClickableItem(
            icon = appearanceVisual.icon,
            iconPainter = appearanceVisual.iconResId?.let { painterResource(id = it) },
            title = "外观设置",
            value = "界面风格、颜色、字体和显示大小",
            onClick = onAppearanceClick,
            iconTint = siblingTints[0]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = playbackVisual.icon,
            iconPainter = playbackVisual.iconResId?.let { painterResource(id = it) },
            title = "播放设置",
            value = "清晰度、倍速、小窗、后台播放和全屏操作",
            onClick = onPlaybackClick,
            iconTint = siblingTints[1]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = bottomBarVisual.icon,
            iconPainter = bottomBarVisual.iconResId?.let { painterResource(id = it) },
            title = "导航设置",
            value = "底栏、顶部入口、图标文字和排列顺序",
            onClick = onBottomBarClick,
            iconTint = siblingTints[2]
        )
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
    val onDynamicAllTabHorizontalUserListVisibleChange: (Boolean) -> Unit,
    val onDynamicTopBarCollapseOnScrollChange: (Boolean) -> Unit,
    val onDynamicFeedLayoutModeChange: (com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode) -> Unit,
    val onDynamicTabVisibilityChange: (String) -> Unit,
    val onHomeRefreshCountChange: (Int) -> Unit
)

internal data class SettingsRootCategoryState(
    val privacyModeEnabled: Boolean,
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
    val dynamicAllTabHorizontalUserListVisible: Boolean,
    val dynamicTopBarCollapseOnScroll: Boolean,
    val dynamicFeedLayoutMode: com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode,
    val dynamicVisibleTabIds: Set<String>,
    val homeRefreshCount: Int
)

@Composable
internal fun SettingsRootCategoryNavigationSection(
    category: SettingsRootCategory,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    actions: SettingsRootCategoryActions,
    state: SettingsRootCategoryState
) {
    val visual = rememberSettingsEntryVisual(category.searchTarget)
    val effectiveIconTint = rememberAdaptivePreferenceIconContainerColor(visual.iconTint)
    val iconContentColor = rememberAdaptivePreferenceIconContentColor(effectiveIconTint)
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    Column {
        // Header row
        SettingsCardGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(AppShapes.container(ContainerLevel.Chip))
                        .background(effectiveIconTint),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        visual.icon != null -> AppIcon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(visual.iconSizeDp.dp)
                        )
                        visual.iconResId != null -> AppIcon(
                            painter = painterResource(id = visual.iconResId),
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = category.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AppIcon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "收起${category.title}" else "展开${category.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }

        // Expandable content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(clip = false) + fadeIn(),
            exit = shrinkVertically(clip = false) + fadeOut()
        ) {
            Box(modifier = Modifier.padding(top = 12.dp)) {
                SettingsRootCategoryContent(
                    category = category,
                    actions = actions,
                    state = state
                )
            }
        }
    }
}

@Composable
internal fun SettingsRootCategoryListSection(
    categories: List<SettingsRootCategory>,
    onCategoryClick: (SettingsRootCategory) -> Unit
) {
    val siblingTints = remember(categories.size) {
        resolveSettingsSiblingIconTints(categories.size)
    }
    SettingsCardGroup {
        categories.forEachIndexed { index, category ->
            val visual = rememberSettingsEntryVisual(category.searchTarget)
            SettingsRootCategoryRow(
                title = category.title,
                subtitle = category.subtitle,
                icon = visual.icon,
                iconPainter = visual.iconResId?.let { painterResource(id = it) },
                iconTint = siblingTints[index],
                iconSizeDp = visual.iconSizeDp,
                onClick = { onCategoryClick(category) },
            )
            if (index != categories.lastIndex) {
                SettingsAdaptiveDivider()
            }
        }
    }
}

@Composable
private fun SettingsRootCategoryRow(
    title: String,
    subtitle: String,
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
        Box(
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppIcon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun SettingsAboutHomeSection(
    onGithubClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onTelegramGroupClick: () -> Unit = {},
    onCheckUpdateClick: () -> Unit,
    onDonateClick: () -> Unit
) {
    val githubVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_SOURCE_HOME)
    val telegramVisual = rememberSettingsEntryVisual(SettingsSearchTarget.TELEGRAM)
    val updateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.CHECK_UPDATE)
    val donateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DONATE)

    SettingsDetailGroup(title = "关于") {
        SettingsCardGroup {
            SettingClickableItem(
                icon = telegramVisual.icon,
                iconPainter = telegramVisual.iconResId?.let { painterResource(id = it) },
                title = "Telegram 频道",
                value = "@bilipai666",
                onClick = onTelegramClick,
                iconTint = telegramVisual.iconTint
            )
            SettingsAdaptiveDivider()
            SettingClickableItem(
                icon = telegramVisual.icon,
                iconPainter = telegramVisual.iconResId?.let { painterResource(id = it) },
                title = "Telegram 交流群",
                value = "@bilipai888",
                onClick = onTelegramGroupClick,
                iconTint = telegramVisual.iconTint
            )
            SettingsAdaptiveDivider()
            SettingClickableItem(
                icon = githubVisual.icon,
                iconPainter = githubVisual.iconResId?.let { painterResource(id = it) },
                title = "开源主页",
                value = "GitHub",
                onClick = onGithubClick,
                iconTint = githubVisual.iconTint
            )
            SettingsAdaptiveDivider()
            SettingClickableItem(
                icon = updateVisual.icon,
                iconPainter = updateVisual.iconResId?.let { painterResource(id = it) },
                title = "检查更新",
                value = "查看最新版本",
                onClick = onCheckUpdateClick,
                iconTint = updateVisual.iconTint
            )
            SettingsAdaptiveDivider()
            SettingClickableItem(
                icon = donateVisual.icon,
                iconPainter = donateVisual.iconResId?.let { painterResource(id = it) },
                title = "打赏作者",
                value = "支持开发",
                onClick = onDonateClick,
                iconTint = donateVisual.iconTint,
                enableCopy = false
            )
        }
    }
}

@Composable
internal fun SettingsBackupHomeSection(
    onSettingsShareClick: () -> Unit,
    onWebDavBackupClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    cacheSize: String
) {
    val shareVisual = rememberSettingsEntryVisual(SettingsSearchTarget.SETTINGS_SHARE)
    val webDavVisual = rememberSettingsEntryVisual(SettingsSearchTarget.WEBDAV_BACKUP)
    val cacheVisual = rememberSettingsEntryVisual(SettingsSearchTarget.CLEAR_CACHE)

    SettingsDetailGroup(title = "设置") {
        SettingsCardGroup {
            SettingClickableItem(
                icon = shareVisual.icon,
                iconPainter = shareVisual.iconResId?.let { painterResource(id = it) },
                title = "设置分享",
                value = "导入、导出与迁移",
                onClick = onSettingsShareClick,
                iconTint = shareVisual.iconTint
            )
            SettingsAdaptiveDivider()
            SettingClickableItem(
                icon = webDavVisual.icon,
                iconPainter = webDavVisual.iconResId?.let { painterResource(id = it) },
                title = "WebDAV 备份",
                value = "云端同步",
                onClick = onWebDavBackupClick,
                iconTint = webDavVisual.iconTint
            )
            SettingsAdaptiveDivider()
            SettingClickableItem(
                icon = cacheVisual.icon,
                iconPainter = cacheVisual.iconResId?.let { painterResource(id = it) },
                title = "清理缓存",
                value = cacheSize,
                onClick = onClearCacheClick,
                iconTint = cacheVisual.iconTint
            )
        }
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
    val siblingTints = remember(entries.size) {
        resolveSettingsSiblingIconTints(entries.size)
    }
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
    val resolvedCategory = canonicalSettingsRootCategory(category)
    Column {
        when (resolvedCategory) {
            SettingsRootCategory.APPEARANCE_THEME -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "外观与主题") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.INTERFACE_THEME,
                                    title = "外观设置",
                                    value = "选择界面风格、颜色、字体、显示大小和启动画面",
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
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "播放与画质") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.PLAYBACK_QUALITY,
                                    title = "播放器设置",
                                    value = "调整解码、清晰度、倍速、小窗和全屏操作",
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.PLAYBACK,
                                        SettingsSearchFocusIds.PLAYBACK_DECODER,
                                    ),
                                    onClick = actions.onPlaybackClick,
                                ),
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "互动与评论") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.INTERACTION_COMMENT,
                                    title = "互动、评论与内容预览",
                                    value = "调整评论显示、点赞操作、视频简介和内容入口",
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.PLAYBACK,
                                        SettingsSearchFocusIds.PLAYBACK_INTERACTION,
                                    ),
                                    onClick = actions.onPlaybackClick,
                                ),
                            ),
                        )
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
                                    title = "首页样式与推荐卡片",
                                    value = "调整卡片布局、壁纸、UP 信息和视频时长",
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "推荐流与动态") {
                        FeedApiSection(
                            feedApiType = state.feedApiType,
                            onFeedApiTypeChange = actions.onFeedApiTypeChange,
                            incrementalTimelineRefreshEnabled = state.incrementalTimelineRefreshEnabled,
                            onIncrementalTimelineRefreshChange = actions.onIncrementalTimelineRefreshChange,
                            dynamicImagePreviewTextVisible = state.dynamicImagePreviewTextVisible,
                            onDynamicImagePreviewTextVisibleChange = actions.onDynamicImagePreviewTextVisibleChange,
                            dynamicAllTabHorizontalUserListVisible = state.dynamicAllTabHorizontalUserListVisible,
                            onDynamicAllTabHorizontalUserListVisibleChange =
                                actions.onDynamicAllTabHorizontalUserListVisibleChange,
                            dynamicTopBarCollapseOnScroll = state.dynamicTopBarCollapseOnScroll,
                            onDynamicTopBarCollapseOnScrollChange =
                                actions.onDynamicTopBarCollapseOnScrollChange,
                            dynamicFeedLayoutMode = state.dynamicFeedLayoutMode,
                            onDynamicFeedLayoutModeChange = actions.onDynamicFeedLayoutModeChange,
                            dynamicVisibleTabIds = state.dynamicVisibleTabIds,
                            onDynamicTabVisibilityChange = actions.onDynamicTabVisibilityChange,
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
                                    title = "导航与标签",
                                    value = "选择底栏和顶部入口，并调整图标、文字和顺序",
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "交互与动效") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.ANIMATION,
                                    title = "动效与触感",
                                    value = "控制页面动画、视频转场、振动反馈和玻璃效果",
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
                            privacyContentAuthenticationEnabled = state.privacyContentAuthenticationEnabled,
                            onPrivacyModeChange = actions.onPrivacyModeChange,
                            onPrivacyContentAuthenticationChange = actions.onPrivacyContentAuthenticationChange,
                            onPermissionClick = actions.onPermissionClick,
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
                                    title = "外观设置",
                                    value = "选择界面风格、颜色、字体、显示大小和启动画面",
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "动效") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.ANIMATION,
                                    title = "动效与图标",
                                    value = "控制页面动画、视频转场、振动反馈和玻璃效果",
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "导航") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.NAVIGATION,
                                    title = "导航与标签",
                                    value = "选择底栏和顶部入口，并调整图标、文字和顺序",
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "全屏与手势") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.FULLSCREEN_GESTURE,
                                    title = "全屏与手势",
                                    value = "设置自动横屏、亮度音量手势和全屏返回方式",
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
                                    title = "首页样式与壁纸",
                                    value = "调整卡片布局、壁纸、UP 信息和视频时长",
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "推荐流与动态") {
                        FeedApiSection(
                            feedApiType = state.feedApiType,
                            onFeedApiTypeChange = actions.onFeedApiTypeChange,
                            incrementalTimelineRefreshEnabled = state.incrementalTimelineRefreshEnabled,
                            onIncrementalTimelineRefreshChange = actions.onIncrementalTimelineRefreshChange,
                            dynamicImagePreviewTextVisible = state.dynamicImagePreviewTextVisible,
                            onDynamicImagePreviewTextVisibleChange = actions.onDynamicImagePreviewTextVisibleChange,
                            dynamicAllTabHorizontalUserListVisible = state.dynamicAllTabHorizontalUserListVisible,
                            onDynamicAllTabHorizontalUserListVisibleChange =
                                actions.onDynamicAllTabHorizontalUserListVisibleChange,
                            dynamicTopBarCollapseOnScroll = state.dynamicTopBarCollapseOnScroll,
                            onDynamicTopBarCollapseOnScrollChange =
                                actions.onDynamicTopBarCollapseOnScrollChange,
                            dynamicFeedLayoutMode = state.dynamicFeedLayoutMode,
                            onDynamicFeedLayoutModeChange = actions.onDynamicFeedLayoutModeChange,
                            dynamicVisibleTabIds = state.dynamicVisibleTabIds,
                            onDynamicTabVisibilityChange = actions.onDynamicTabVisibilityChange,
                            homeRefreshCount = state.homeRefreshCount,
                            onHomeRefreshCountChange = actions.onHomeRefreshCountChange
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "画质与播放") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.PLAYBACK_QUALITY,
                                    title = "播放与画质",
                                    value = "选择解码方式、默认清晰度、音质和播放速度",
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "互动") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.INTERACTION_COMMENT,
                                    title = "互动与评论",
                                    value = "调整评论显示、点赞操作、视频简介和内容入口",
                                    openFocus = SettingsSceneDetailFocus(
                                        SettingsSearchTarget.PLAYBACK,
                                        SettingsSearchFocusIds.PLAYBACK_INTERACTION,
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
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "隐私与安全") {
                        PrivacySection(
                            privacyModeEnabled = state.privacyModeEnabled,
                            privacyContentAuthenticationEnabled = state.privacyContentAuthenticationEnabled,
                            onPrivacyModeChange = actions.onPrivacyModeChange,
                            onPrivacyContentAuthenticationChange = actions.onPrivacyContentAuthenticationChange,
                            onPermissionClick = actions.onPermissionClick,
                            onBlockedListClick = actions.onBlockedListClick,
                            onCommentFraudHistoryClick = actions.onCommentFraudHistoryClick // [New]
                        )
                    }
                }
            }
            SettingsRootCategory.SYSTEM_ABOUT -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "系统诊断") {
                        DiagnosticsSection(
                            crashTrackingEnabled = state.crashTrackingEnabled,
                            analyticsEnabled = state.analyticsEnabled,
                            enhancedDiagnosticLoggingEnabled = state.enhancedDiagnosticLoggingEnabled,
                            onCrashTrackingChange = actions.onCrashTrackingChange,
                            onAnalyticsChange = actions.onAnalyticsChange,
                            onEnhancedDiagnosticLoggingChange =
                                actions.onEnhancedDiagnosticLoggingChange,
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
                                    onClick = actions.onPlaybackClick
                                )
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "帮助与系统") {
                        SupportToolsSection(
                            onTipsClick = actions.onTipsClick,
                            onOpenLinksClick = actions.onOpenLinksClick,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "关于与发布") {
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
                Spacer(modifier = Modifier.height(12.dp))
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
    onOpenLinksClick: () -> Unit
) {
    val tipsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.TIPS)
    val openLinksVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_LINKS)
    val siblingTints = remember { resolveSettingsSiblingIconTints(2, paletteOffset = 1) }

    SettingsCardGroup {
        SettingClickableItem(
            icon = tipsVisual.icon,
            iconPainter = tipsVisual.iconResId?.let { painterResource(id = it) },
            title = "小贴士 & 隐藏操作",
            value = "探索更多功能",
            onClick = onTipsClick,
            iconTint = siblingTints[0]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = openLinksVisual.icon,
            iconPainter = openLinksVisual.iconResId?.let { painterResource(id = it) },
            title = "默认打开链接",
            value = "设置应用链接支持",
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
    val disclaimerTint = rememberAdaptivePreferenceIconTint(iOSBlue)
    val releaseChannelIcon = rememberAppShareIcon()
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Dialog),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    imageVector = releaseChannelIcon,
                    contentDescription = null,
                    tint = disclaimerTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "官方渠道：GitHub · 频道 · 群组",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppText(
                        text = "请从官方渠道获取安装包，注意来源安全。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                AppTextButton(
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
fun SettingsSubpageEntrySection(
    onContentAndStorageClick: () -> Unit,
    onPrivacyAndSecurityClick: () -> Unit,
    onExtensionsAndDebugClick: () -> Unit,
    onAboutAndSupportClick: () -> Unit
) {
    val siblingTints = remember { resolveSettingsSiblingIconTints(4, paletteOffset = 2) }
    val contentAndStorageIcon = rememberSettingsSemanticIcon(SettingsIconRole.DATA_BACKUP)
    val privacyIcon = rememberSettingsSemanticIcon(SettingsIconRole.PRIVACY_PERMISSION)
    val developerVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DIAGNOSTICS)
    val aboutIcon = rememberSettingsSemanticIcon(SettingsIconRole.ABOUT_SUPPORT)
    SettingsCardGroup {
        SettingClickableItem(
            icon = contentAndStorageIcon,
            title = "内容与存储",
            value = "推荐流、下载与缓存",
            onClick = onContentAndStorageClick,
            iconTint = siblingTints[0]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = privacyIcon,
            title = "隐私与安全",
            value = "无痕模式、权限与黑名单",
            onClick = onPrivacyAndSecurityClick,
            iconTint = siblingTints[1]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = developerVisual.icon,
            iconPainter = developerVisual.iconResId?.let { painterResource(id = it) },
            title = "扩展与调试",
            value = "插件、日志与数据采集",
            onClick = onExtensionsAndDebugClick,
            iconTint = siblingTints[2]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = aboutIcon,
            title = "关于与支持",
            value = "版本、开源、帮助与作者",
            onClick = onAboutAndSupportClick,
            iconTint = siblingTints[3]
        )
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
    dynamicAllTabHorizontalUserListVisible: Boolean,
    onDynamicAllTabHorizontalUserListVisibleChange: (Boolean) -> Unit,
    dynamicTopBarCollapseOnScroll: Boolean,
    onDynamicTopBarCollapseOnScrollChange: (Boolean) -> Unit,
    dynamicFeedLayoutMode: com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode,
    onDynamicFeedLayoutModeChange: (com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode) -> Unit,
    dynamicVisibleTabIds: Set<String>,
    onDynamicTabVisibilityChange: (String) -> Unit,
    homeRefreshCount: Int,
    onHomeRefreshCountChange: (Int) -> Unit
) {
    val siblingTints = remember { resolveSettingsSiblingIconTints(8, paletteOffset = 1) }
    val feedIcon = rememberSettingsSemanticIcon(SettingsIconRole.FEED_API)
    val refreshIcon = rememberSettingsSemanticIcon(SettingsIconRole.REFRESH_COUNT)
    val visibilityIcon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_TAB_VISIBILITY)
    val previewTextIcon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_PREVIEW_TEXT)
    val topBarCollapseIcon = rememberSettingsSemanticIcon(SettingsIconRole.NAVIGATION)
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
        SettingSwitchItem(
            icon = refreshIcon,
            title = "刷新时保留当前列表",
            subtitle = "下拉刷新只把新动态加到顶部，不重新排列已看到的内容",
            checked = incrementalTimelineRefreshEnabled,
            onCheckedChange = onIncrementalTimelineRefreshChange,
            iconTint = siblingTints[1]
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = previewTextIcon,
            title = "动态图片默认显示文字",
            subtitle = "打开图文动态图片时默认显示下方文字，可用右上角眼睛临时切换",
            checked = dynamicImagePreviewTextVisible,
            onCheckedChange = onDynamicImagePreviewTextVisibleChange,
            iconTint = siblingTints[2]
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = visibilityIcon,
            title = "“全部”页显示关注用户栏",
            subtitle = "关闭后隐藏顶部横向用户列表，“UP主”页仍可选择关注用户",
            checked = dynamicAllTabHorizontalUserListVisible,
            onCheckedChange = onDynamicAllTabHorizontalUserListVisibleChange,
            iconTint = siblingTints[3]
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = topBarCollapseIcon,
            title = "浏览动态时收起顶部栏",
            subtitle = if (dynamicTopBarCollapseOnScroll) {
                "列表下滑时折叠 Tab 顶栏；横向 UP 主栏仍会单独收起"
            } else {
                "Tab 顶栏固定在顶部；横向 UP 主栏仍会单独收起"
            },
            checked = dynamicTopBarCollapseOnScroll,
            onCheckedChange = onDynamicTopBarCollapseOnScrollChange,
            iconTint = siblingTints[4]
        )
        SettingsAdaptiveDivider()
        SettingsSingleChoicePreference(
            title = "动态页面布局",
            subtitle = "瀑布流会按屏幕宽度显示多列；列表模式固定为单列",
            options = com.android.purebilibili.core.store.SettingsManager.DynamicFeedLayoutMode.entries.map { mode ->
                com.android.purebilibili.core.ui.components.AppSegmentOption(
                    value = mode,
                    label = mode.label
                )
            },
            selectedValue = dynamicFeedLayoutMode,
            icon = feedIcon,
            iconTint = siblingTints[5],
            onSelectionChange = onDynamicFeedLayoutModeChange,
        )
        SettingsAdaptiveDivider()
        FeedDynamicTabVisibilityItem(
            icon = visibilityIcon,
            visibleTabIds = dynamicVisibleTabIds,
            onTabVisibilityChange = onDynamicTabVisibilityChange,
            iconTint = siblingTints[6]
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
            iconTint = siblingTints[7]
        )
    }
}

@Composable
private fun FeedDynamicTabVisibilityItem(
    icon: ImageVector,
    visibleTabIds: Set<String>,
    onTabVisibilityChange: (String) -> Unit,
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
            Box(
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
                    text = "动态栏位显示",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                AppText(
                    text = "选择动态页显示哪些栏位，至少保留 1 个。隐藏 UP 后，点侧栏用户会直接打开主页。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        allDynamicTabSpecs.forEachIndexed { index, tab ->
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
            if (index != allDynamicTabSpecs.lastIndex) {
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
    privacyContentAuthenticationEnabled: Boolean,
    onPrivacyModeChange: (Boolean) -> Unit,
    onPrivacyContentAuthenticationChange: (Boolean) -> Unit,
    onPermissionClick: () -> Unit,
    onBlockedListClick: () -> Unit, // [New]
    onCommentFraudHistoryClick: () -> Unit // [New]
) {
    val siblingTints = remember { resolveSettingsSiblingIconTints(4, paletteOffset = 4) }
    val permissionVisual = rememberSettingsEntryVisual(SettingsSearchTarget.PERMISSION)
    val blockedListVisual = rememberSettingsEntryVisual(SettingsSearchTarget.BLOCKED_LIST)
    val visibilityOffIcon = rememberSettingsSemanticIcon(SettingsIconRole.PRIVACY_HISTORY)
    val contentAuthenticationIcon = rememberSettingsSemanticIcon(
        SettingsIconRole.PRIVACY_CONTENT_AUTHENTICATION,
    )

    SettingsCardGroup {
        SettingSwitchItem(
            icon = visibilityOffIcon,
            title = "不记录历史",
            subtitle = "启用后不记录播放历史和搜索历史",
            checked = privacyModeEnabled,
            onCheckedChange = onPrivacyModeChange,
            iconTint = siblingTints[0]
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = contentAuthenticationIcon,
            title = "进入隐私内容时验证",
            subtitle = "进入收藏、历史等页面时使用指纹、人脸或锁屏密码",
            checked = privacyContentAuthenticationEnabled,
            onCheckedChange = onPrivacyContentAuthenticationChange,
            iconTint = siblingTints[1]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = permissionVisual.icon,
            iconPainter = permissionVisual.iconResId?.let { painterResource(id = it) },
            title = "权限管理",
            value = "查看应用权限",
            onClick = onPermissionClick,
            iconTint = siblingTints[2]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = blockedListVisual.icon,
            iconPainter = blockedListVisual.iconResId?.let { painterResource(id = it) },
            title = "黑名单管理",
            value = "管理已屏蔽的 UP 主",
            onClick = onBlockedListClick,
            iconTint = siblingTints[3]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = Icons.Outlined.Shield,
            title = "发评反诈历史",
            value = "查看历史发评与风控状态",
            onClick = onCommentFraudHistoryClick,
            iconTint = com.android.purebilibili.core.theme.iOSPink
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
    val siblingTints = remember { resolveSettingsSiblingIconTints(7, paletteOffset = 2) }
    val showExplicitActionChevron =
        rememberAdaptiveListVisualCapabilities().showExplicitActionChevron

    SettingsCardGroup {
        SettingClickableItem(
            icon = settingsShareVisual.icon,
            iconPainter = settingsShareVisual.iconResId?.let { painterResource(id = it) },
            title = "设置分享",
            value = "导出并导入可分享设置",
            onClick = onSettingsShareClick,
            iconTint = siblingTints[0]
        )
        SettingsAdaptiveDivider()
        // WebDAV 是“备份副本”场景，使用双文档图标比链路图标更贴合语义。
        SettingClickableItem(
            icon = webDavVisual.icon,
            iconPainter = webDavVisual.iconResId?.let { painterResource(id = it) },
            title = "WebDAV 云备份",
            value = "备份与恢复设置/插件",
            onClick = onWebDavBackupClick,
            iconTint = siblingTints[1]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = downloadPathVisual.icon,
            iconPainter = downloadPathVisual.iconResId?.let { painterResource(id = it) },
            title = "下载位置",
            value = if (customDownloadPath != null) "自定义" else "默认",
            onClick = onDownloadPathClick,
            iconTint = siblingTints[2],
            showChevron = showExplicitActionChevron
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = imageSavePathVisual.icon,
            iconPainter = imageSavePathVisual.iconResId?.let { painterResource(id = it) },
            title = "图片保存位置",
            value = if (customImageSavePath != null) "已选择目录" else "默认",
            onClick = onImageSavePathClick,
            iconTint = siblingTints[3],
            showChevron = showExplicitActionChevron
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = clearCacheVisual.icon,
            iconPainter = clearCacheVisual.iconResId?.let { painterResource(id = it) },
            title = "清除缓存",
            value = cacheSize,
            onClick = onClearCacheClick,
            iconTint = siblingTints[4],
            showChevron = showExplicitActionChevron
        )
        SettingsAdaptiveDivider()
        SettingsSingleChoicePreference(
            title = "自动清理缓存",
            subtitle = "应用启动时按周期清理可重建缓存",
            options = SettingsManager.AutoCacheClearInterval.entries.map { interval ->
                com.android.purebilibili.core.ui.components.AppSegmentOption(
                    value = interval,
                    label = interval.label
                )
            },
            selectedValue = autoCacheClearInterval,
            icon = clearCacheVisual.icon,
            iconTint = siblingTints[5],
            onSelectionChange = onAutoCacheClearIntervalChange
        )
        SettingsAdaptiveDivider()
        SettingSliderItem(
            icon = clearCacheVisual.icon,
            title = "缓存容量上限",
            subtitle = "应用启动时达到上限即自动清理；默认 5 GB",
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
            title = "插件中心",
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
    val siblingTints = remember { resolveSettingsSiblingIconTints(6, paletteOffset = 5) }
    val exportLogsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.EXPORT_LOGS)
    val proxySettings by NetworkProxyStore.settings.collectAsStateWithLifecycle(
        initialValue = NetworkProxyStore.getSync(context)
    )
    var showProxyDialog by remember { mutableStateOf(false) }
    var showEnhancedDiagnosticConsent by remember { mutableStateOf(false) }

    SettingsCardGroup {
        SettingSwitchItem(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.CRASH_TRACKING),
            title = "崩溃追踪",
            subtitle = "默认开启，仅用于定位崩溃与严重故障",
            checked = crashTrackingEnabled,
            onCheckedChange = onCrashTrackingChange,
            iconTint = siblingTints[0],
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.ANALYTICS),
            title = "使用情况统计",
            subtitle = "默认开启，用于匿名统计每日活跃与基础使用情况",
            checked = analyticsEnabled,
            onCheckedChange = onAnalyticsChange,
            iconTint = siblingTints[1],
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = Icons.Outlined.BugReport,
            title = "增强诊断日志",
            subtitle = if (enhancedDiagnosticLoggingEnabled) {
                "正在采集脱敏后的详细运行信息；关闭会立即清除"
            } else {
                "默认关闭；主动开启后记录更多排障信息，不含凭据与观看内容"
            },
            checked = enhancedDiagnosticLoggingEnabled,
            onCheckedChange = { enabled ->
                if (enabled) showEnhancedDiagnosticConsent = true
                else onEnhancedDiagnosticLoggingChange(false)
            },
            iconTint = siblingTints[2],
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = Icons.Outlined.Lan,
            title = "HTTP 代理",
            subtitle = formatAppHttpProxySummary(proxySettings) +
                "（API/登录；播放流仍直连）",
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
            icon = Icons.Outlined.Dns,
            title = "代理地址",
            value = formatAppHttpProxyEndpoint(proxySettings),
            subtitle = "host:port，如 127.0.0.1:7890",
            onClick = { showProxyDialog = true },
            iconTint = siblingTints[4],
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = exportLogsVisual.icon,
            iconPainter = exportLogsVisual.iconResId?.let { painterResource(id = it) },
            title = "导出日志",
            value = "导出前统一脱敏，仅由你主动分享",
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
            title = { AppText("开启增强诊断日志？", fontWeight = FontWeight.Bold) },
            text = {
                AppText(
                    text = "将记录应用版本、设备与系统环境、请求结果、播放器状态和错误堆栈。" +
                        "账号凭据、用户标识、视频标识、搜索词和用户输入会在写入前脱敏。" +
                        "日志仅保存在应用私有目录（最多 256KB），不会自动上传；关闭后会立即清除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val draft = AppHttpProxySettings(
        enabled = enabled,
        host = sanitizeProxyHostInput(host),
        portText = sanitizeProxyPortInput(portText),
    )
    val canSave = !enabled || isAppHttpProxyConfigured(draft)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(
                text = "设置 HTTP 代理",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(
                    text = "仅作用于 API / 登录请求。播放媒体仍直连，避免本地代理端口不可用导致卡顿。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                if (enabled && !isAppHttpProxyConfigured(draft)) {
                    AppText(
                        text = "请填写有效的主机与端口（1–65535）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
fun DeveloperSection(
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
    enhancedDiagnosticLoggingEnabled: Boolean,
    pluginCount: Int,
    onCrashTrackingChange: (Boolean) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onEnhancedDiagnosticLoggingChange: (Boolean) -> Unit,
    onPluginsClick: () -> Unit,
    onExportLogsClick: () -> Unit
) {
    // Keep public API for older call sites; diagnostics + proxy live in DiagnosticsSection.
    val pluginsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.PLUGINS)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DiagnosticsSection(
            crashTrackingEnabled = crashTrackingEnabled,
            analyticsEnabled = analyticsEnabled,
            enhancedDiagnosticLoggingEnabled = enhancedDiagnosticLoggingEnabled,
            onCrashTrackingChange = onCrashTrackingChange,
            onAnalyticsChange = onAnalyticsChange,
            onEnhancedDiagnosticLoggingChange = onEnhancedDiagnosticLoggingChange,
            onExportLogsClick = onExportLogsClick,
        )
        SettingsCardGroup {
            SettingClickableItem(
                icon = pluginsVisual.icon,
                iconPainter = pluginsVisual.iconResId?.let { painterResource(id = it) },
                title = "插件中心",
                value = "$pluginCount 个已启用",
                onClick = onPluginsClick,
                iconTint = pluginsVisual.iconTint
            )
        }
    }
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
    verificationSubtitle: String = "暂未获取到可核对的 release 证据",
    buildSourceValue: String = "本地构建",
    buildSourceSubtitle: String = "未绑定 GitHub Release",
    buildFingerprintValue: String = "未读取",
    buildFingerprintCopyValue: String = "未读取",
    buildFingerprintSubtitle: String = "暂未读取到当前安装包 SHA-256",
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
    var detailDialogContent by remember { mutableStateOf<AppBuildInfoDialogContent?>(null) }
    val easterEggTint = rememberSettingsEntryTint(AppSemanticAccentRole.TERTIARY, iOSYellow)
    val updateSiblingTints = remember { resolveSettingsSiblingIconTints(5, paletteOffset = 3) }
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
            normalizedClickCount >= safeThreshold -> iOSGreen
            versionProgress >= 0.85f -> iOSOrange
            versionProgress >= 0.5f -> iOSYellow
            normalizedClickCount > 0 -> iOSBlue
            else -> iOSTeal
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        text = dialogContent.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

    SettingsSectionTitle(title = "源码与验证")
    SettingsCardGroup {
        SettingClickableItem(
            icon = openSourceHomeVisual.icon,
            iconPainter = openSourceHomeVisual.iconResId?.let { painterResource(id = it) },
            title = "开源主页",
            value = "GitHub",
            onClick = onGithubClick,
            iconTint = openSourceHomeVisual.iconTint,
            enableCopy = true,
            onCopyRequest = rememberClipboardCopyHandler(),
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = verificationIcon,
            title = "源码一致性",
            subtitle = verificationSubtitle,
            value = verificationLabel,
            onClick = {
                detailDialogContent = resolveVerificationDialogContent(
                    label = verificationLabel,
                    summary = verificationSubtitle
                )
            },
            iconTint = when (verificationLabel) {
                "已验证" -> iOSGreen
                "基本可验证" -> iOSBlue
                else -> iOSOrange
            }
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = buildSourceIcon,
            title = "构建来源",
            subtitle = buildSourceSubtitle,
            value = buildSourceValue,
            onClick = {
                detailDialogContent = resolveBuildSourceDialogContent(
                    value = buildSourceValue,
                    subtitle = buildSourceSubtitle
                )
            },
            iconTint = iOSOrange,
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
            iconTint = iOSPurple,
            enableCopy = true,
            onCopyRequest = rememberClipboardCopyHandler(),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    SettingsSectionTitle(title = "更新")
    SettingsCardGroup {
        SettingClickableItem(
            icon = licensesVisual.icon,
            iconPainter = licensesVisual.iconResId?.let { painterResource(id = it) },
            title = "开源许可证",
            value = "License",
            onClick = onLicenseClick,
            iconTint = updateSiblingTints[0]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = checkUpdateVisual.icon,
            iconPainter = checkUpdateVisual.iconResId?.let { painterResource(id = it) },
            title = "检查更新",
            value = if (isCheckingUpdate) "检查中..." else updateStatusText,
            onClick = onCheckUpdateClick,
            iconTint = updateSiblingTints[1]
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = releaseNotesVisual.icon,
            iconPainter = releaseNotesVisual.iconResId?.let { painterResource(id = it) },
            title = "查看更新日志",
            value = "最新版本说明",
            onClick = onViewReleaseNotesClick,
            iconTint = updateSiblingTints[2]
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = notificationIcon,
            title = "自动检查更新",
            subtitle = resolveAutoCheckUpdateSubtitle(autoCheckEnabled = autoCheckUpdateEnabled),
            checked = autoCheckUpdateEnabled,
            onCheckedChange = onAutoCheckUpdateChange,
            iconTint = updateSiblingTints[3]
        )
        SettingsAdaptiveDivider()
        SettingsSingleChoicePreference(
            title = "检测渠道",
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

    SettingsSectionTitle(title = "辅助")
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
            title = "重看使用须知",
            value = "开源约定与官方渠道",
            onClick = onReplayOnboardingClick,
            iconTint = replayOnboardingVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = sparklesIcon,
            title = "趣味彩蛋",
            subtitle = "刷新、点赞、投币、搜索时显示趣味提示",
            checked = easterEggEnabled,
            onCheckedChange = onEasterEggChange,
            iconTint = easterEggTint
        )
    }
}

internal data class AboutContributor(
    val name: String,
    val githubLogin: String,
    val avatarResId: Int? = null,
    val avatarUrl: String? = null
) {
    val profileUrl: String get() = "https://github.com/$githubLogin"
}

// 默认使用本地头像以避免进入关于页时请求 GitHub；个别新贡献者可使用其公开头像链接。
internal val AboutContributors = listOf(
    AboutContributor("jay3-yy", "jay3-yy", R.drawable.avatar_jay3_yy),
    AboutContributor(
        name = "Piracola",
        githubLogin = "Piracola",
        avatarUrl = "https://avatars.githubusercontent.com/u/116626041?v=4"
    ),
    AboutContributor("Chenx Dust", "chenx-dust", R.drawable.avatar_chenx_dust),
    AboutContributor("usontong", "usontong", R.drawable.avatar_usontong),
    AboutContributor("Leko", "lekoOwO", R.drawable.avatar_lekoowo),
    AboutContributor("TanakaLun", "TanakaLun", R.drawable.avatar_tanakalun),
    AboutContributor("Matt Van Horn", "mvanhorn", R.drawable.avatar_mvanhorn),
    AboutContributor("qyo123oyq", "qyo123oyq", R.drawable.avatar_qyo123oyq),
    AboutContributor(
        name = "maxzrb",
        githubLogin = "maxzrb",
        avatarUrl = "https://avatars.githubusercontent.com/u/114979598?v=4"
    ),
    AboutContributor(
        name = "xiaoniao427",
        githubLogin = "xiaoniao427",
        avatarUrl = "https://avatars.githubusercontent.com/u/115906803?v=4"
    ),
    AboutContributor(
        name = "zensu357",
        githubLogin = "zensu357",
        avatarUrl = "https://avatars.githubusercontent.com/u/109052061?v=4"
    ),
    AboutContributor(
        name = "Kurarion",
        githubLogin = "Kurarion",
        avatarUrl = "https://avatars.githubusercontent.com/u/29721634?v=4"
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
    contributors: List<AboutContributor> = AboutContributors
) {
    val slogan = remember { AboutSlogans.random() }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Dialog),
        colors = CardDefaults.cardColors(
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppText(
                        text = "v$versionName",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                AppText(
                    text = slogan,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            AppText(
                text = "贡献者",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                contributors.forEach { contributor ->
                    AboutContributorItem(contributor = contributor)
                }
            }
        }
    }
}

@Composable
private fun AboutContributorItem(
    contributor: AboutContributor
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(AppShapes.container(ContainerLevel.Chip))
            .clickable { uriHandler.openUri(contributor.profileUrl) }
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
            contributor.avatarUrl?.let { avatarUrl ->
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "${contributor.name} 头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } ?: contributor.avatarResId?.let { avatarResId ->
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = "${contributor.name} 头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        AppText(
            text = contributor.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
