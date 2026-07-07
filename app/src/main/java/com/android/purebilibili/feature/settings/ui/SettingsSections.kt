package com.android.purebilibili.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.util.EasterEggs
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import com.android.purebilibili.core.ui.common.copyOnLongPress
import com.android.purebilibili.core.ui.components.AppAdaptiveSwitch
import com.android.purebilibili.core.ui.components.rememberAdaptiveSemanticIconTint
import com.android.purebilibili.core.ui.components.resolveAdaptiveListComponentVisualSpec
import com.android.purebilibili.core.ui.components.IOSSearchBar
import com.android.purebilibili.core.ui.components.resolveAdaptiveListRowVisualSpec
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.core.ui.IOSAlertDialog
import com.android.purebilibili.core.ui.IOSDialogAction
import com.android.purebilibili.core.store.MAX_HOME_REFRESH_COUNT
import com.android.purebilibili.core.store.MIN_HOME_REFRESH_COUNT
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
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.ui.components.IOSSectionTitle as SettingsSectionTitle
import com.android.purebilibili.core.ui.components.IOSGroup as SettingsGroup
import com.android.purebilibili.core.ui.components.IOSSwitchItem as SettingSwitchItem
import com.android.purebilibili.core.ui.components.IOSClickableItem as SettingClickableItem
import com.android.purebilibili.core.ui.components.IOSDivider as SettingsDivider
import com.android.purebilibili.core.ui.components.IOSSliderPreference as SettingSliderItem



// ═══════════════════════════════════════════════════
//  业务板块 (Business Sections)
// ═══════════════════════════════════════════════════

@Composable
private fun SettingsAdaptiveDivider() {
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val visualSpec = remember(uiPreset, androidNativeVariant) {
        resolveAdaptiveListComponentVisualSpec(uiPreset, androidNativeVariant)
    }
    SettingsDivider(startIndent = visualSpec.dividerStartIndentDp.dp)
}

@Composable
private fun SettingsCardGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = AppSurfaceTokens.groupedListContainer().luminance() < 0.45f
    val darkTintBase = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val baseCardContainer = AppSurfaceTokens.cardContainer()
    val containerColor = if (isDark) {
        darkTintBase.compositeOver(baseCardContainer)
    } else {
        baseCardContainer
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
    }

    SettingsGroup(
        containerColor = containerColor,
        shape = AppShapes.container(ContainerLevel.Dialog),
        border = BorderStroke(0.6.dp, borderColor)
    ) {
        content()
    }
}

@Composable
fun SupportAuthorCompactSection(
    onDonateClick: () -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val donateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DONATE, uiPreset)

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
    val uiPreset = LocalUiPreset.current
    val appearanceVisual = rememberSettingsEntryVisual(SettingsSearchTarget.APPEARANCE, uiPreset)
    val playbackVisual = rememberSettingsEntryVisual(SettingsSearchTarget.PLAYBACK, uiPreset)
    val bottomBarVisual = rememberSettingsEntryVisual(SettingsSearchTarget.BOTTOM_BAR, uiPreset)

    SettingsCardGroup {
        SettingClickableItem(
            icon = appearanceVisual.icon,
            iconPainter = appearanceVisual.iconResId?.let { painterResource(id = it) },
            title = "外观设置",
            value = "主题、图标、模糊效果",
            onClick = onAppearanceClick,
            iconTint = appearanceVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = playbackVisual.icon,
            iconPainter = playbackVisual.iconResId?.let { painterResource(id = it) },
            title = "播放设置",
            value = "解码、手势、后台播放",
            onClick = onPlaybackClick,
            iconTint = playbackVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = bottomBarVisual.icon,
            iconPainter = bottomBarVisual.iconResId?.let { painterResource(id = it) },
            title = "导航设置",
            value = "底栏、顶部标签、平板侧边栏",
            onClick = onBottomBarClick,
            iconTint = bottomBarVisual.iconTint
        )
    }
}

internal data class SettingsDetailEntry(
    val target: SettingsSearchTarget,
    val title: String,
    val value: String,
    val onClick: () -> Unit
)

internal data class SettingsRootCategoryActions(
    val onAppearanceClick: () -> Unit,
    val onAnimationClick: () -> Unit,
    val onPlaybackClick: () -> Unit,
    val onBottomBarClick: () -> Unit,
    val onPermissionClick: () -> Unit,
    val onBlockedListClick: () -> Unit,
    val onPluginsClick: () -> Unit,
    val onExportLogsClick: () -> Unit,
    val onSettingsShareClick: () -> Unit,
    val onWebDavBackupClick: () -> Unit,
    val onDownloadPathClick: () -> Unit,
    val onImageSavePathClick: () -> Unit,
    val onClearCacheClick: () -> Unit,
    val onGithubClick: () -> Unit,
    val onTelegramClick: () -> Unit,
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
    val onEasterEggChange: (Boolean) -> Unit,
    val onAutoCheckUpdateChange: (Boolean) -> Unit,
    val onFeedApiTypeChange: (com.android.purebilibili.core.store.SettingsManager.FeedApiType) -> Unit,
    val onIncrementalTimelineRefreshChange: (Boolean) -> Unit,
    val onDynamicImagePreviewTextVisibleChange: (Boolean) -> Unit,
    val onDynamicAllTabHorizontalUserListVisibleChange: (Boolean) -> Unit,
    val onDynamicTabVisibilityChange: (String) -> Unit,
    val onHomeRefreshCountChange: (Int) -> Unit
)

internal data class SettingsRootCategoryState(
    val privacyModeEnabled: Boolean,
    val privacyContentAuthenticationEnabled: Boolean,
    val crashTrackingEnabled: Boolean,
    val analyticsEnabled: Boolean,
    val pluginCount: Int,
    val customDownloadPath: String?,
    val customImageSavePath: String?,
    val cacheSize: String,
    val versionName: String,
    val easterEggEnabled: Boolean,
    val updateStatusText: String,
    val isCheckingUpdate: Boolean,
    val autoCheckUpdateEnabled: Boolean,
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
    val uiPreset = LocalUiPreset.current
    val visual = rememberSettingsEntryVisual(category.searchTarget, uiPreset)
    val effectiveIconTint = rememberAdaptiveSemanticIconTint(visual.iconTint, uiPreset)
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
                        .background(effectiveIconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        visual.icon != null -> Icon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = effectiveIconTint,
                            modifier = Modifier.size(22.dp)
                        )
                        visual.iconResId != null -> Icon(
                            painter = painterResource(id = visual.iconResId),
                            contentDescription = null,
                            tint = effectiveIconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = CupertinoIcons.Default.ChevronForward,
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
    val uiPreset = LocalUiPreset.current
    val visualSpec = resolveSettingsVisualSpec()

    SettingsDetailGroup(title = "分类") {
        SettingsCardGroup {
            categories.forEachIndexed { index, category ->
                val visual = rememberSettingsEntryVisual(category.searchTarget, uiPreset)
                SettingsRootCategoryRow(
                    title = category.title,
                    subtitle = category.subtitle,
                    icon = visual.icon,
                    iconPainter = visual.iconResId?.let { painterResource(id = it) },
                    iconTint = visual.iconTint,
                    onClick = { onCategoryClick(category) },
                )
                if (index != categories.lastIndex) {
                    SettingsAdaptiveDivider()
                }
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
    onClick: () -> Unit,
) {
    val visualSpec = resolveSettingsVisualSpec()
    Row(
        modifier = Modifier
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
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                iconPainter != null -> Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(visualSpec.categoryIconSize),
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(visualSpec.categoryIconSize),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = CupertinoIcons.Default.ChevronForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun SettingsHomeSearchEntry(
    onClick: () -> Unit,
) {
    val visualSpec = resolveSettingsVisualSpec()
    Box(
        modifier = Modifier
            .padding(
                horizontal = visualSpec.screenHorizontalPadding,
                vertical = visualSpec.searchBarVerticalPadding,
            )
            .clickable(onClick = onClick),
    ) {
        IOSSearchBar(
            query = "",
            onQueryChange = {},
            placeholder = stringResource(R.string.settings_search_placeholder),
        )
    }
}

@Composable
internal fun SettingsAboutHomeSection(
    onGithubClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onDonateClick: () -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val githubVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_SOURCE_HOME, uiPreset)
    val telegramVisual = rememberSettingsEntryVisual(SettingsSearchTarget.TELEGRAM, uiPreset)
    val updateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.CHECK_UPDATE, uiPreset)
    val donateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DONATE, uiPreset)

    SettingsDetailGroup(title = "关于") {
        SettingsCardGroup {
            SettingClickableItem(
                icon = telegramVisual.icon,
                iconPainter = telegramVisual.iconResId?.let { painterResource(id = it) },
                title = "Telegram 频道",
                value = "官方发布与反馈",
                onClick = onTelegramClick,
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
    val uiPreset = LocalUiPreset.current
    val shareVisual = rememberSettingsEntryVisual(SettingsSearchTarget.SETTINGS_SHARE, uiPreset)
    val webDavVisual = rememberSettingsEntryVisual(SettingsSearchTarget.WEBDAV_BACKUP, uiPreset)
    val cacheVisual = rememberSettingsEntryVisual(SettingsSearchTarget.CLEAR_CACHE, uiPreset)

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
    val uiPreset = LocalUiPreset.current
    SettingsCardGroup {
        entries.forEachIndexed { index, entry ->
            val visual = rememberSettingsEntryVisual(entry.target, uiPreset)
            SettingClickableItem(
                icon = visual.icon,
                iconPainter = visual.iconResId?.let { painterResource(id = it) },
                title = entry.title,
                subtitle = entry.value,
                onClick = {
                    resolveSettingsSceneDetailFocus(entry.target)?.let { detailFocus ->
                        SettingsSearchFocusController.submit(detailFocus.target, detailFocus.focusId)
                    }
                    entry.onClick()
                },
                iconTint = visual.iconTint
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
    Column {
        when (category) {
            SettingsRootCategory.APPEARANCE_INTERACTION -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "显示与交互") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.INTERFACE_THEME,
                                    title = "外观设置",
                                    value = "UI 预设、主题、字体、DPI、动态图标与开屏",
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
                                    value = "过渡动画、触感反馈、动态图标与底栏搜索入口",
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
                                    value = "底栏、顶部标签、平板侧边栏与底栏项目顺序",
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
                                    value = "在播放设置内 · 全屏方向与手势控制",
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
                                    value = "卡片样式、壁纸效果与推荐流宽度",
                                    onClick = actions.onAppearanceClick
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
                                    value = "在播放设置内 · 解码、画质与倍速",
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
                                    value = "在播放设置内 · 评论、点赞与简介",
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
                            onClearCacheClick = actions.onClearCacheClick
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
                            onBlockedListClick = actions.onBlockedListClick
                        )
                    }
                }
            }
            SettingsRootCategory.SYSTEM_ABOUT -> {
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "诊断与插件") {
                        SettingsDetailEntrySection(
                            entries = listOf(
                                SettingsDetailEntry(
                                    target = SettingsSearchTarget.DIAGNOSTICS,
                                    title = "播放器诊断",
                                    value = "在播放设置内 · 诊断日志与统计",
                                    onClick = actions.onPlaybackClick
                                )
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DeveloperSection(
                            crashTrackingEnabled = state.crashTrackingEnabled,
                            analyticsEnabled = state.analyticsEnabled,
                            pluginCount = state.pluginCount,
                            onCrashTrackingChange = actions.onCrashTrackingChange,
                            onAnalyticsChange = actions.onAnalyticsChange,
                            onPluginsClick = actions.onPluginsClick,
                            onExportLogsClick = actions.onExportLogsClick
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SettingsDetailGroup(title = "关于与发布") {
                        AboutSection(
                            versionName = state.versionName,
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
                        onDisclaimerClick = actions.onDisclaimerClick
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRootCategoryEntranceSection {
                    SupportToolsSection(
                        onTipsClick = actions.onTipsClick,
                        onOpenLinksClick = actions.onOpenLinksClick
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
    val uiPreset = LocalUiPreset.current
    val tipsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.TIPS, uiPreset)
    val openLinksVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_LINKS, uiPreset)

    SettingsCardGroup {
        SettingClickableItem(
            icon = tipsVisual.icon,
            iconPainter = tipsVisual.iconResId?.let { painterResource(id = it) },
            title = "小贴士 & 隐藏操作",
            value = "探索更多功能",
            onClick = onTipsClick,
            iconTint = tipsVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = openLinksVisual.icon,
            iconPainter = openLinksVisual.iconResId?.let { painterResource(id = it) },
            title = "默认打开链接",
            value = "设置应用链接支持",
            onClick = onOpenLinksClick,
            iconTint = openLinksVisual.iconTint
        )
    }
}

@Composable
fun ReleaseChannelPinnedCard(
    onGithubClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onDisclaimerClick: () -> Unit
) {
    val disclaimerTint = rememberAdaptiveSemanticIconTint(iOSBlue)
    val releaseChannelIcon = rememberAppShareIcon()
    Card(
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
                Icon(
                    imageVector = releaseChannelIcon,
                    contentDescription = null,
                    tint = disclaimerTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "官方发布渠道仅限 GitHub / Telegram",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "不存在其他官方发布渠道，请注意安装来源安全。",
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
                OutlinedButton(
                    onClick = onGithubClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(
                        text = "GitHub",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
                OutlinedButton(
                    onClick = onTelegramClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(
                        text = "Telegram",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
                TextButton(
                    onClick = onDisclaimerClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(
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
    val uiPreset = LocalUiPreset.current
    val storageTint = rememberSettingsEntryTint(SettingsEntryTintRole.SECONDARY, iOSBlue, uiPreset)
    val privacyTint = rememberSettingsEntryTint(SettingsEntryTintRole.TERTIARY, iOSPurple, uiPreset)
    val developerTint = rememberSettingsEntryTint(SettingsEntryTintRole.SECONDARY, iOSTeal, uiPreset)
    val aboutTint = rememberSettingsEntryTint(SettingsEntryTintRole.TERTIARY, iOSOrange, uiPreset)
    val contentAndStorageIcon = rememberSettingsSemanticIcon(SettingsIconRole.DATA_BACKUP, uiPreset)
    val privacyIcon = rememberSettingsSemanticIcon(SettingsIconRole.PRIVACY_PERMISSION, uiPreset)
    val developerVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DIAGNOSTICS, uiPreset)
    val aboutIcon = rememberSettingsSemanticIcon(SettingsIconRole.ABOUT_SUPPORT, uiPreset)
    SettingsCardGroup {
        SettingClickableItem(
            icon = contentAndStorageIcon,
            title = "内容与存储",
            value = "推荐流、下载与缓存",
            onClick = onContentAndStorageClick,
            iconTint = storageTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = privacyIcon,
            title = "隐私与安全",
            value = "无痕模式、权限与黑名单",
            onClick = onPrivacyAndSecurityClick,
            iconTint = privacyTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = developerVisual.icon,
            iconPainter = developerVisual.iconResId?.let { painterResource(id = it) },
            title = "扩展与调试",
            value = "插件、日志与数据采集",
            onClick = onExtensionsAndDebugClick,
            iconTint = developerTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = aboutIcon,
            title = "关于与支持",
            value = "版本、开源、帮助与作者",
            onClick = onAboutAndSupportClick,
            iconTint = aboutTint
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
    dynamicVisibleTabIds: Set<String>,
    onDynamicTabVisibilityChange: (String) -> Unit,
    homeRefreshCount: Int,
    onHomeRefreshCountChange: (Int) -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val feedTint = rememberSettingsEntryTint(SettingsEntryTintRole.TERTIARY, iOSOrange, uiPreset)
    val incrementalRefreshTint = rememberSettingsEntryTint(SettingsEntryTintRole.SECONDARY, iOSGreen, uiPreset)
    val feedIcon = rememberSettingsSemanticIcon(SettingsIconRole.FEED_API, uiPreset)
    val refreshIcon = rememberSettingsSemanticIcon(SettingsIconRole.REFRESH_COUNT, uiPreset)
    val visibilityIcon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_TAB_VISIBILITY, uiPreset)
    val previewTextIcon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_PREVIEW_TEXT, uiPreset)
    SettingsCardGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = feedIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = feedTint
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "推荐流类型",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = feedApiType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            IOSSlidingSegmentedControl(
                options = resolveFeedApiSegmentOptions(),
                selectedValue = feedApiType,
                onSelectionChange = onFeedApiTypeChange
            )
        }
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = refreshIcon,
            title = "动态增量刷新",
            subtitle = "下拉刷新时不重置列表，仅在顶部插入新内容",
            checked = incrementalTimelineRefreshEnabled,
            onCheckedChange = onIncrementalTimelineRefreshChange,
            iconTint = incrementalRefreshTint
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = previewTextIcon,
            title = "动态图片默认显示文字",
            subtitle = "打开图文动态图片时默认显示下方文字，可用右上角眼睛临时切换",
            checked = dynamicImagePreviewTextVisible,
            onCheckedChange = onDynamicImagePreviewTextVisibleChange,
            iconTint = feedTint
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = visibilityIcon,
            title = "全部动态显示 UP 主栏",
            subtitle = "关闭后，“全部”tab 顶部不再弹出 UP 主横向栏，UP tab 仍可选择关注用户",
            checked = dynamicAllTabHorizontalUserListVisible,
            onCheckedChange = onDynamicAllTabHorizontalUserListVisibleChange,
            iconTint = feedTint
        )
        SettingsAdaptiveDivider()
        FeedDynamicTabVisibilityItem(
            icon = visibilityIcon,
            visibleTabIds = dynamicVisibleTabIds,
            onTabVisibilityChange = onDynamicTabVisibilityChange,
            iconTint = feedTint
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
            valueLabel = homeRefreshCount.toString(),
            iconTint = incrementalRefreshTint
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
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val visualSpec = remember(uiPreset, androidNativeVariant) {
        resolveAdaptiveListComponentVisualSpec(uiPreset, androidNativeVariant)
    }
    val rowSpec = remember(uiPreset, androidNativeVariant) {
        resolveAdaptiveListRowVisualSpec(uiPreset, androidNativeVariant)
    }
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
                    .clip(RoundedCornerShape(visualSpec.iconCornerRadiusDp.dp))
                    .background(iconTint.copy(alpha = visualSpec.iconBackgroundAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(visualSpec.iconGlyphSizeDp.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "动态栏位显示",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
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
    onBlockedListClick: () -> Unit // [New]
) {
    val uiPreset = LocalUiPreset.current
    val privacyModeTint = rememberSettingsEntryTint(SettingsEntryTintRole.TERTIARY, iOSPurple, uiPreset)
    val permissionVisual = rememberSettingsEntryVisual(SettingsSearchTarget.PERMISSION, uiPreset)
    val blockedListVisual = rememberSettingsEntryVisual(SettingsSearchTarget.BLOCKED_LIST, uiPreset)
    val visibilityOffIcon = rememberSettingsSemanticIcon(SettingsIconRole.PRIVACY_PERMISSION, uiPreset)
    val contentAuthenticationIcon = rememberSettingsSemanticIcon(
        SettingsIconRole.PRIVACY_CONTENT_AUTHENTICATION,
        uiPreset
    )

    SettingsCardGroup {
        SettingSwitchItem(
            icon = visibilityOffIcon,
            title = "不记录历史",
            subtitle = "启用后不记录播放历史和搜索历史",
            checked = privacyModeEnabled,
            onCheckedChange = onPrivacyModeChange,
            iconTint = privacyModeTint
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = contentAuthenticationIcon,
            title = "进入隐私内容时验证",
            subtitle = "进入收藏、历史等页面时使用指纹、人脸或锁屏密码",
            checked = privacyContentAuthenticationEnabled,
            onCheckedChange = onPrivacyContentAuthenticationChange,
            iconTint = permissionVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = permissionVisual.icon,
            iconPainter = permissionVisual.iconResId?.let { painterResource(id = it) },
            title = "权限管理",
            value = "查看应用权限",
            onClick = onPermissionClick,
            iconTint = permissionVisual.iconTint
        )
         SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = blockedListVisual.icon,
            iconPainter = blockedListVisual.iconResId?.let { painterResource(id = it) },
            title = "黑名单管理",
            value = "管理已屏蔽的 UP 主",
            onClick = onBlockedListClick,
            iconTint = blockedListVisual.iconTint
        )
    }
}

@Composable
fun DataStorageSection(
    customDownloadPath: String?,
    customImageSavePath: String?,
    cacheSize: String,
    onSettingsShareClick: () -> Unit,
    onWebDavBackupClick: () -> Unit,
    onDownloadPathClick: () -> Unit,
    onImageSavePathClick: () -> Unit,
    onClearCacheClick: () -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val settingsShareVisual = rememberSettingsEntryVisual(SettingsSearchTarget.SETTINGS_SHARE, uiPreset)
    val webDavVisual = rememberSettingsEntryVisual(SettingsSearchTarget.WEBDAV_BACKUP, uiPreset)
    val downloadPathVisual = rememberSettingsEntryVisual(SettingsSearchTarget.DOWNLOAD_PATH, uiPreset)
    val imageSavePathVisual = rememberSettingsEntryVisual(SettingsSearchTarget.IMAGE_SAVE_PATH, uiPreset)
    val clearCacheVisual = rememberSettingsEntryVisual(SettingsSearchTarget.CLEAR_CACHE, uiPreset)

    SettingsCardGroup {
        SettingClickableItem(
            icon = settingsShareVisual.icon,
            iconPainter = settingsShareVisual.iconResId?.let { painterResource(id = it) },
            title = "设置分享",
            value = "导出并导入可分享设置",
            onClick = onSettingsShareClick,
            iconTint = settingsShareVisual.iconTint
        )
        SettingsAdaptiveDivider()
        // WebDAV 是“备份副本”场景，使用双文档图标比链路图标更贴合语义。
        SettingClickableItem(
            icon = webDavVisual.icon,
            iconPainter = webDavVisual.iconResId?.let { painterResource(id = it) },
            title = "WebDAV 云备份",
            value = "备份与恢复设置/插件",
            onClick = onWebDavBackupClick,
            iconTint = webDavVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = downloadPathVisual.icon,
            iconPainter = downloadPathVisual.iconResId?.let { painterResource(id = it) },
            title = "下载位置",
            value = if (customDownloadPath != null) "自定义" else "默认",
            onClick = onDownloadPathClick,
            iconTint = downloadPathVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = imageSavePathVisual.icon,
            iconPainter = imageSavePathVisual.iconResId?.let { painterResource(id = it) },
            title = "图片保存位置",
            value = if (customImageSavePath != null) "已选择目录" else "默认",
            onClick = onImageSavePathClick,
            iconTint = imageSavePathVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = clearCacheVisual.icon,
            iconPainter = clearCacheVisual.iconResId?.let { painterResource(id = it) },
            title = "清除缓存",
            value = cacheSize,
            onClick = onClearCacheClick,
            iconTint = clearCacheVisual.iconTint
        )
    }
}

@Composable
fun DeveloperSection(
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
    pluginCount: Int,
    onCrashTrackingChange: (Boolean) -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onPluginsClick: () -> Unit,
    onExportLogsClick: () -> Unit
) {
    val uiPreset = LocalUiPreset.current
    val crashTrackingTint = rememberSettingsEntryTint(SettingsEntryTintRole.SECONDARY, iOSTeal, uiPreset)
    val analyticsTint = rememberSettingsEntryTint(SettingsEntryTintRole.PRIMARY, iOSBlue, uiPreset)
    val pluginsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.PLUGINS, uiPreset)
    val exportLogsVisual = rememberSettingsEntryVisual(SettingsSearchTarget.EXPORT_LOGS, uiPreset)
    val crashTrackingIcon = rememberSettingsSemanticIcon(SettingsIconRole.CRASH_TRACKING, uiPreset)
    val analyticsIcon = rememberSettingsSemanticIcon(SettingsIconRole.ANALYTICS, uiPreset)

    SettingsCardGroup {
        SettingSwitchItem(
            icon = crashTrackingIcon,
            title = "崩溃追踪",
            subtitle = "默认开启，仅用于定位崩溃与严重故障",
            checked = crashTrackingEnabled,
            onCheckedChange = onCrashTrackingChange,
            iconTint = crashTrackingTint
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = analyticsIcon,
            title = "使用情况统计",
            subtitle = "默认开启，开启后用于匿名统计每日活跃与基础使用情况",
            checked = analyticsEnabled,
            onCheckedChange = onAnalyticsChange,
            iconTint = analyticsTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = pluginsVisual.icon,
            iconPainter = pluginsVisual.iconResId?.let { painterResource(id = it) },
            title = "插件中心",
            value = "$pluginCount 个已启用",
            onClick = onPluginsClick,
            iconTint = pluginsVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = exportLogsVisual.icon,
            iconPainter = exportLogsVisual.iconResId?.let { painterResource(id = it) },
            title = "导出日志",
            value = "播放器诊断与问题反馈",
            onClick = onExportLogsClick,
            iconTint = exportLogsVisual.iconTint
        )
    }
}

@Composable
fun AboutSection(
    versionName: String,
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
    var detailDialogContent by remember { mutableStateOf<AppBuildInfoDialogContent?>(null) }
    val uiPreset = LocalUiPreset.current
    val autoCheckTint = rememberSettingsEntryTint(SettingsEntryTintRole.PRIMARY, iOSBlue, uiPreset)
    val easterEggTint = rememberSettingsEntryTint(SettingsEntryTintRole.TERTIARY, iOSYellow, uiPreset)
    val licensesVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_SOURCE_LICENSES, uiPreset)
    val openSourceHomeVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_SOURCE_HOME, uiPreset)
    val checkUpdateVisual = rememberSettingsEntryVisual(SettingsSearchTarget.CHECK_UPDATE, uiPreset)
    val releaseNotesVisual = rememberSettingsEntryVisual(SettingsSearchTarget.VIEW_RELEASE_NOTES, uiPreset)
    val replayOnboardingVisual = rememberSettingsEntryVisual(SettingsSearchTarget.REPLAY_ONBOARDING, uiPreset)
    val notificationIcon = rememberSettingsSemanticIcon(SettingsIconRole.AUTO_CHECK_UPDATE, uiPreset)
    val infoIcon = rememberSettingsSemanticIcon(SettingsIconRole.ABOUT_SUPPORT, uiPreset)
    val sparklesIcon = rememberSettingsSemanticIcon(SettingsIconRole.EASTER_EGG, uiPreset)
    val verificationIcon = rememberSettingsSemanticIcon(SettingsIconRole.BUILD_VERIFICATION, uiPreset)
    val buildSourceIcon = rememberSettingsSemanticIcon(SettingsIconRole.BUILD_SOURCE, uiPreset)
    val buildFingerprintIcon = rememberSettingsSemanticIcon(SettingsIconRole.BUILD_FINGERPRINT, uiPreset)

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
        IOSAlertDialog(
            onDismissRequest = { detailDialogContent = null },
            title = { Text(dialogContent.title) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(dialogScrollState)
                ) {
                    Text(
                        text = dialogContent.value,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dialogContent.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                IOSDialogAction(
                    onClick = {
                        when (dialogContent.action) {
                            AppBuildInfoDialogAction.VIEW_VERIFICATION -> onVerificationClick()
                            AppBuildInfoDialogAction.VIEW_BUILD_SOURCE -> onBuildSourceClick()
                            AppBuildInfoDialogAction.VIEW_BUILD_FINGERPRINT -> onBuildFingerprintClick()
                        }
                        detailDialogContent = null
                    }
                ) {
                    Text(dialogContent.actionLabel)
                }
            },
            dismissButton = {
                IOSDialogAction(onClick = { detailDialogContent = null }) {
                    Text("关闭")
                }
            }
        )
    }

    AboutProjectOverviewCard(versionName = versionName)
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
            enableCopy = true
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
            enableCopy = true
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
            enableCopy = true
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
            iconTint = licensesVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = checkUpdateVisual.icon,
            iconPainter = checkUpdateVisual.iconResId?.let { painterResource(id = it) },
            title = "检查更新",
            value = if (isCheckingUpdate) "检查中..." else updateStatusText,
            onClick = onCheckUpdateClick,
            iconTint = checkUpdateVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = releaseNotesVisual.icon,
            iconPainter = releaseNotesVisual.iconResId?.let { painterResource(id = it) },
            title = "查看更新日志",
            value = "最新版本说明",
            onClick = onViewReleaseNotesClick,
            iconTint = releaseNotesVisual.iconTint
        )
        SettingsAdaptiveDivider()
        SettingSwitchItem(
            icon = notificationIcon,
            title = "自动检查更新",
            subtitle = resolveAutoCheckUpdateSubtitle(autoCheckEnabled = autoCheckUpdateEnabled),
            checked = autoCheckUpdateEnabled,
            onCheckedChange = onAutoCheckUpdateChange,
            iconTint = autoCheckTint
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
            enableCopy = true
        )
        SettingsAdaptiveDivider()
        SettingClickableItem(
            icon = replayOnboardingVisual.icon,
            iconPainter = replayOnboardingVisual.iconResId?.let { painterResource(id = it) },
            title = "重播新手引导",
            value = "了解应用功能",
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
    val avatarResId: Int
) {
    val profileUrl: String get() = "https://github.com/$githubLogin"
}

// ponytail: 静态列表避免关于页每次打开都请求 GitHub；需要实时同步时再接 contributors API。
// 头像已预置为本地图片资源，无需网络请求。
internal val AboutContributors = listOf(
    AboutContributor("jay3-yy", "jay3-yy", R.drawable.avatar_jay3_yy),
    AboutContributor("Chenx Dust", "chenx-dust", R.drawable.avatar_chenx_dust),
    AboutContributor("usontong", "usontong", R.drawable.avatar_usontong),
    AboutContributor("Leko", "lekoOwO", R.drawable.avatar_lekoowo),
    AboutContributor("TanakaLun", "TanakaLun", R.drawable.avatar_tanakalun),
    AboutContributor("Matt Van Horn", "mvanhorn", R.drawable.avatar_mvanhorn),
    AboutContributor("qyo123oyq", "qyo123oyq", R.drawable.avatar_qyo123oyq)
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
    contributors: List<AboutContributor> = AboutContributors
) {
    val slogan = remember { AboutSlogans.random() }
    Card(
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
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_bilipai_foreground),
                    contentDescription = "BiliPai 图标",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BiliPai",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
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
                Text(
                    text = slogan,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
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
            Image(
                painter = painterResource(id = contributor.avatarResId),
                contentDescription = "${contributor.name} 头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = contributor.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
