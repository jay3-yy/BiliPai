package com.android.purebilibili.feature.settings.highlights

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.UpdateHighlightsStore
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.feature.settings.DarkThemeStyle
import com.android.purebilibili.feature.settings.DonateDialog
import com.android.purebilibili.feature.settings.DonationDialogOrigin
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal val LocalUpdateHighlightNavigator = staticCompositionLocalOf<(UpdateHighlightAction) -> Unit> { {} }

@Suppress("DEPRECATION")
private fun isExistingInstallation(context: Context): Boolean {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return info.lastUpdateTime > info.firstInstallTime
}

/**
 * Post-update congratulations & release highlights bottom sheet.
 *
 * Automatically appears once on an idle homepage after a version upgrade, congratulating the user
 * and previewing the latest release highlights with one-tap jump to each feature area.
 * Settings -> 关于 -> 本次更新 may reopen the same latest-release sheet at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateHighlightsHost(
    allowAutomatic: Boolean = false,
    manualOpen: Boolean = false,
    onManualDismiss: () -> Unit = {},
    onNavigateToHighlight: ((UpdateHighlightAction) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultNavigator = LocalUpdateHighlightNavigator.current
    val navigateToHighlight = onNavigateToHighlight ?: defaultNavigator
    // Observe lifecycle transitions even below STARTED, so a background pause cancels the invite delay.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val canShowAutomatically = allowAutomatic && lifecycleState == Lifecycle.State.RESUMED
    val settings by remember(context) { SettingsManager.getAppThemeSettings(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    val history by remember(context) { UpdateHighlightsStore.history(context) }
        .collectAsStateWithLifecycle(initialValue = UpdateHighlightHistory())
    var initialized by remember { mutableStateOf(false) }
    var automaticIds by rememberSaveable { mutableStateOf("") }
    var pendingBatchIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var previewSnapshot by remember { mutableStateOf<AppThemeSettings?>(null) }
    var applied by rememberSaveable { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showDonateDialog by rememberSaveable { mutableStateOf(false) }
    val donationOrigin = remember { DonationDialogOrigin() }
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled

    LaunchedEffect(context, manualOpen) {
        // Manual browsing must not establish an upgrade baseline or consume automatic prompts.
        if (!manualOpen) {
            try {
                val existing = withContext(Dispatchers.IO) { isExistingInstallation(context) }
                UpdateHighlightsStore.prepare(context, BuildConfig.VERSION_CODE, existing)
                initialized = true
            } catch (_: IOException) {
                // Leave history untouched; the next entry can retry without repeated sheets.
            }
        }
    }
    val currentSettings = settings
    val pending = remember(history.pendingIds, currentSettings) {
        if (currentSettings == null) {
            emptyList()
        } else {
            resolveAutomaticReleaseHighlights(
                versionCode = BuildConfig.VERSION_CODE,
                pendingIds = history.pendingIds,
                settings = currentSettings,
            )
        }
    }
    LaunchedEffect(initialized, canShowAutomatically, pending, manualOpen) {
        if (!initialized || !canShowAutomatically || manualOpen || automaticIds.isNotEmpty() || pending.isEmpty()) {
            return@LaunchedEffect
        }
        delay(1_200)
        // Only record the pending batch locally until the user actually dismisses or acts on the sheet,
        // so a transient pause/route jitter right after reveal cannot permanently swallow the highlights.
        pendingBatchIds = history.pendingIds
        automaticIds = pending.joinToString(",") { it.id }
    }
    // Already enabled features need no invitation if the user later switches away again.
    LaunchedEffect(currentSettings, history.pendingIds) {
        if (currentSettings != null) {
            val activeIds = updateHighlightCatalog.filter {
                it.id in history.pendingIds && isUpdateHighlightAlreadyEnabled(it, currentSettings)
            }.map { it.id }.toSet()
            if (activeIds.isNotEmpty()) {
                try {
                    UpdateHighlightsStore.markPresented(context, activeIds)
                } catch (_: IOException) {
                    // Recheck on a later entry; already-enabled features are never invited.
                }
            }
        }
    }
    val features = if (manualOpen) {
        latestReleaseHighlights(BuildConfig.VERSION_CODE)
    } else {
        val ids = automaticIds.split(',').filter { it.isNotBlank() }.toSet()
        latestReleaseHighlights(BuildConfig.VERSION_CODE).filter { it.id in ids }
    }
    LaunchedEffect(manualOpen, features.map { it.id }) {
        if (manualOpen && features.isNotEmpty()) {
            try {
                UpdateHighlightsStore.markPresented(context, features.map { it.id }.toSet())
            } catch (_: IOException) {
                // Manual access remains available even if invitation history cannot be saved.
            }
        }
    }
    DonateDialog(
        visible = showDonateDialog,
        origin = donationOrigin,
        onDismiss = {
            showDonateDialog = false
            onManualDismiss()
        },
    )
    if ((!manualOpen && (!canShowAutomatically || automaticIds.isEmpty())) || currentSettings == null || showDonateDialog) return

    val snapshot = previewSnapshot
    fun markAutomaticBatchConsumed() {
        val batchIds = pendingBatchIds.ifEmpty {
            automaticIds.split(',').filter { it.isNotBlank() }.toSet()
        }
        pendingBatchIds = emptySet()
        if (!manualOpen && batchIds.isNotEmpty()) {
            scope.launch {
                try {
                    UpdateHighlightsStore.markPresented(context, batchIds)
                } catch (_: IOException) {
                    // Retry on next entry if persistence fails.
                }
            }
        }
    }
    fun dismiss() {
        if (applying) return
        markAutomaticBatchConsumed()
        automaticIds = ""
        previewSnapshot = null
        applied = false
        message = null
        onManualDismiss()
    }

    if (snapshot != null || applied) {
        AppAlertDialog(
            onDismissRequest = ::dismiss,
            title = { Text(if (applied) "已应用新配色" else "预览 CMF 配色") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.62f).dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (applied) {
                        Text("已启用 Standard · Cmf · SPEC_2026。可随时在外观设置中选择其他配色。")
                    } else if (snapshot != null) {
                        Text("应用后将切换为 Standard · Cmf · SPEC_2026，保留颜色来源、种子色、对比度及高级覆盖色。")
                        CmfComparison(snapshot, glassEnabled)
                        Text("仅预览不会保存设置。", style = MaterialTheme.typography.bodySmall)
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                if (snapshot != null && !applied) {
                    if (applying) {
                        Text("正在应用…")
                    } else {
                        AppDialogAction(onClick = {
                            if (applying) return@AppDialogAction
                            applying = true
                            scope.launch {
                                try {
                                    applied = SettingsManager.applyCmfUpdateTrial(context, snapshot)
                                    if (applied) {
                                        previewSnapshot = null
                                    } else {
                                        previewSnapshot = null
                                        message = "设置已发生变化，请重新预览后再应用。"
                                    }
                                } catch (_: IOException) {
                                    message = "保存失败，请稍后重试。"
                                } finally {
                                    applying = false
                                }
                            }
                        }) { Text("应用新配色") }
                    }
                } else {
                    AppDialogAction(onClick = ::dismiss) { Text("完成") }
                }
            },
            dismissButton = {
                if (snapshot != null && !applying) {
                    AppDialogAction(onClick = { previewSnapshot = null; message = null }) { Text("返回") }
                }
            },
        )
        return
    }

    val accentColor = AppContentColors.accent()
    val displayVersionName = remember { formatDisplayVersionName(BuildConfig.VERSION_NAME) }
    val maxScrollHeight = (LocalConfiguration.current.screenHeightDp * 0.56f).dp

    AppModalBottomSheet(onDismissRequest = ::dismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 顶部恭喜横幅与版本徽标
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp),
                        )
                        AppText(
                            text = if (manualOpen) {
                                "v$displayVersionName 更新要点"
                            } else {
                                "新版本已就绪 · v$displayVersionName"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                        )
                    }
                }
                AppText(
                    text = if (manualOpen) {
                        "本次更新内容"
                    } else {
                        "已更新至 v$displayVersionName"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppContentColors.primaryText(),
                )
                AppText(
                    text = if (manualOpen) {
                        "轻点下方任意卡片，即可直达对应功能体验。"
                    } else {
                        "本次带来以下重点升级，轻点任意卡片即可直达体验。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),
                )
            }

            // 更新内容要点卡片列表
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxScrollHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (features.isEmpty()) {
                    AppText(
                        text = "当前版本暂无可预览的更新要点。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.secondaryText(),
                    )
                } else {
                    features.forEach { feature ->
                        val isCmfPreview = feature.action == UpdateHighlightAction.PREVIEW_CMF
                        val enabled = canPreviewUpdateHighlight(feature, currentSettings) &&
                            !isUpdateHighlightAlreadyEnabled(feature, currentSettings)
                        val clickable = feature.action != null && (!isCmfPreview || enabled)
                        UpdateHighlightPreviewCard(
                            feature = feature,
                            clickable = clickable,
                            statusHint = when {
                                isUpdateHighlightAlreadyEnabled(feature, currentSettings) -> "你已启用这项功能"
                                isCmfPreview && !canPreviewUpdateHighlight(feature, currentSettings) ->
                                    "切换到 MD3 主题并选择自定义颜色后可从这里预览"
                                else -> null
                            },
                            onClick = {
                                val action = feature.action ?: return@UpdateHighlightPreviewCard
                                when (action) {
                                    UpdateHighlightAction.PREVIEW_CMF -> {
                                        previewSnapshot = currentSettings
                                        message = null
                                    }
                                    UpdateHighlightAction.OPEN_DONATE -> {
                                        markAutomaticBatchConsumed()
                                        automaticIds = ""
                                        previewSnapshot = null
                                        applied = false
                                        message = null
                                        showDonateDialog = true
                                    }
                                    else -> {
                                        dismiss()
                                        navigateToHighlight(action)
                                    }
                                }
                            },
                        )
                    }
                }
                message?.let {
                    AppText(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (!manualOpen) {
                AppText(
                    text = "随时可在「设置 → 关于 → 本次更新」再次查看",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                )
            }

            // 底部确认按钮
            Button(
                onClick = ::dismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = AppShapes.container(ContainerLevel.Card),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                AppText(
                    text = if (manualOpen) "关闭" else "开始体验",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun UpdateHighlightPreviewCard(
    feature: UpdateHighlight,
    clickable: Boolean,
    statusHint: String?,
    onClick: () -> Unit,
) {
    val accentColor = AppContentColors.accent()
    val cardShape = AppShapes.container(ContainerLevel.Card)
    val iconShape = AppShapes.container(ContainerLevel.Field)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, AppSurfaceTokens.divider().copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(iconShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        imageVector = resolveUpdateHighlightIcon(feature),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }

                feature.badge?.takeIf { it.isNotBlank() }?.let { badgeText ->
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.10f),
                    ) {
                        AppText(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (clickable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        feature.actionLabel?.takeIf { it.isNotBlank() }?.let { label ->
                            AppText(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = accentColor,
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppText(
                    text = feature.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppContentColors.primaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                statusHint?.let { hint ->
                    AppText(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                    )
                }
            }
        }
    }
}

private fun resolveUpdateHighlightIcon(feature: UpdateHighlight): ImageVector = when (feature.icon) {
    UpdateHighlightIcon.SPEED -> Icons.Outlined.Speed
    UpdateHighlightIcon.CONTENT_CUT -> Icons.Outlined.EditNote
    UpdateHighlightIcon.CAST -> Icons.Outlined.Cast
    UpdateHighlightIcon.WIDGETS -> Icons.Outlined.GridView
    UpdateHighlightIcon.AUTO_FIX_HIGH -> Icons.Outlined.HighQuality
    UpdateHighlightIcon.CLEANING_SERVICES -> Icons.Outlined.DeleteSweep
    UpdateHighlightIcon.PALETTE -> Icons.Outlined.Palette
    UpdateHighlightIcon.FAVORITE -> Icons.Outlined.FavoriteBorder
    UpdateHighlightIcon.DOWNLOAD -> Icons.Outlined.Download
    UpdateHighlightIcon.HEADPHONES -> Icons.Outlined.Headphones
    UpdateHighlightIcon.HISTORY -> Icons.Outlined.History
    UpdateHighlightIcon.WATCH_LATER -> Icons.Outlined.WatchLater
    UpdateHighlightIcon.NOTIFICATIONS -> Icons.Outlined.Notifications
    UpdateHighlightIcon.PLAY_CIRCLE -> Icons.Outlined.PlayCircle
    UpdateHighlightIcon.HOME -> Icons.Outlined.Home
    UpdateHighlightIcon.TUNE -> Icons.Outlined.Tune
    UpdateHighlightIcon.AUTO_AWESOME -> Icons.Outlined.AutoAwesome
    null -> when (feature.action) {
        UpdateHighlightAction.OPEN_DONATE -> Icons.Outlined.FavoriteBorder
        UpdateHighlightAction.OPEN_PLUGINS_SETTINGS -> Icons.Outlined.Speed
        UpdateHighlightAction.OPEN_DOWNLOAD_LIST -> Icons.Outlined.Download
        UpdateHighlightAction.OPEN_ICON_SETTINGS,
        UpdateHighlightAction.OPEN_APPEARANCE_SETTINGS,
        UpdateHighlightAction.OPEN_IMAGE_DISPLAY_SETTINGS,
        UpdateHighlightAction.PREVIEW_CMF -> Icons.Outlined.Palette
        UpdateHighlightAction.OPEN_LISTEN_VIDEO -> Icons.Outlined.Headphones
        UpdateHighlightAction.OPEN_PERSONAL_RECAP -> Icons.Outlined.History
        UpdateHighlightAction.OPEN_WATCH_LATER -> Icons.Outlined.WatchLater
        UpdateHighlightAction.OPEN_CUSTOM_PUSH_SETTINGS -> Icons.Outlined.Notifications
        UpdateHighlightAction.OPEN_PLAYBACK_SETTINGS,
        UpdateHighlightAction.OPEN_DECODER_SETTINGS -> Icons.Outlined.PlayCircle
        UpdateHighlightAction.OPEN_HOME_SETTINGS -> Icons.Outlined.Home
        UpdateHighlightAction.OPEN_STORAGE_SETTINGS -> Icons.Outlined.DeleteSweep
        UpdateHighlightAction.OPEN_SETTINGS -> Icons.Outlined.Tune
        null -> Icons.Outlined.AutoAwesome
    }
}

@Composable
private fun CmfComparison(settings: AppThemeSettings, glassEnabled: Boolean, modifier: Modifier = Modifier) {
    val palettes = remember(settings, glassEnabled) {
        val seed = parseMd3CustomColorHex(settings.md3CustomColorHex)
        val configuration = ThemePreviewConfiguration(
            uiStyle = settings.uiStyle, source = settings.md3ColorSource,
            liquidGlassEnabled = glassEnabled, amoledDarkTheme = settings.darkThemeStyle == DarkThemeStyle.AMOLED,
            roleOverrides = settings.themeRoleOverrides, wallpaperOverrides = settings.wallpaperPaletteOverrides,
        )
        listOf(false, true).map { dark ->
            val current = resolveThemePreviewPalette(seed, dark, settings.colorStyle, settings.colorSpec,
                settings.colorContrast, settings.seedPaletteStyle, configuration).material
            val candidate = resolveThemePreviewPalette(seed, dark, PaletteStyle.Cmf(), ColorSpec.SpecVersion.SPEC_2026,
                settings.colorContrast, SeedPaletteStyle.Standard, configuration).material
            Triple(dark, current, candidate)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        palettes.forEach { (dark, current, candidate) ->
            Text(if (dark) "深色模式" else "浅色模式", style = MaterialTheme.typography.titleSmall)
            listOf("当前" to current, "CMF" to candidate).forEach { (label, scheme) ->
                Column(
                    Modifier.fillMaxWidth().background(scheme.surface).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("$label · 主要文字", color = scheme.onSurface)
                    Text("说明文字", color = scheme.onSurfaceVariant)
                    Text("选中状态", color = scheme.onSecondaryContainer,
                        modifier = Modifier.background(scheme.secondaryContainer).padding(8.dp))
                }
            }
        }
    }
}
