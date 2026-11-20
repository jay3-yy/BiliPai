package com.android.purebilibili.feature.settings.highlights

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.store.AppThemeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.UpdateHighlightsStore
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.settings.DarkThemeStyle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Suppress("DEPRECATION")
private fun isExistingInstallation(context: Context): Boolean {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return info.lastUpdateTime > info.firstInstallTime
}

/** One invitation on an idle homepage; settings may reopen the same offline catalog.
 * Eligibility never changes settings. Presentation is remembered by stable feature IDs. */
@Composable
internal fun UpdateHighlightsHost(
    allowAutomatic: Boolean = false,
    manualOpen: Boolean = false,
    onManualDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Observe lifecycle transitions even below STARTED, so a background pause cancels the invite delay.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val canShowAutomatically = allowAutomatic && lifecycleState == Lifecycle.State.RESUMED
    val settings by remember(context) { SettingsManager.getAppThemeSettings(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    val history by remember(context) { UpdateHighlightsStore.history(context) }
        .collectAsStateWithLifecycle(initialValue = UpdateHighlightHistory())
    var initialized by remember { mutableStateOf(false) }
    var automaticIds by rememberSaveable { mutableStateOf("") }
    var previewSnapshot by remember { mutableStateOf<AppThemeSettings?>(null) }
    var applied by rememberSaveable { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled

    LaunchedEffect(context, manualOpen) {
        // Manual browsing must not establish an upgrade baseline or consume automatic prompts.
        if (!manualOpen) {
            try {
                val existing = withContext(Dispatchers.IO) { isExistingInstallation(context) }
                UpdateHighlightsStore.prepare(context, BuildConfig.VERSION_CODE, existing)
                initialized = true
            } catch (_: IOException) {
                // Leave history untouched; the next entry can retry without repeated dialogs.
            }
        }
    }
    val currentSettings = settings
    val pending = remember(history.pendingIds, currentSettings) {
        if (currentSettings == null) emptyList() else updateHighlightCatalog.filter {
            it.introducedVersionCode <= BuildConfig.VERSION_CODE &&
                it.id in history.pendingIds && canPreviewUpdateHighlight(it, currentSettings) &&
                !isUpdateHighlightAlreadyEnabled(it, currentSettings)
        }.sortedByDescending { it.introducedVersionCode }.take(3)
    }
    LaunchedEffect(initialized, canShowAutomatically, pending, manualOpen) {
        if (!initialized || !canShowAutomatically || manualOpen || automaticIds.isNotEmpty() || pending.isEmpty()) return@LaunchedEffect
        delay(1_500)
        // Mark the whole pending batch once, so a skipped-version update never chains dialogs.
        val batchIds = history.pendingIds
        automaticIds = pending.joinToString(",") { it.id }
        try {
            UpdateHighlightsStore.markPresented(context, batchIds)
        } catch (_: IOException) {
            // Keep the displayed invitation usable; persist again on the next entry.
        }
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
    val features = if (manualOpen) latestReleaseHighlights(BuildConfig.VERSION_CODE) else {
        val ids = automaticIds.split(',').toSet()
        updateHighlightCatalog.filter { it.id in ids }
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
    if ((!manualOpen && (!canShowAutomatically || automaticIds.isEmpty())) || currentSettings == null) return
    val snapshot = previewSnapshot
    fun dismiss() {
        if (applying) return
        automaticIds = ""
        previewSnapshot = null
        applied = false
        message = null
        onManualDismiss()
    }
    AppAlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(if (applied) "已应用新配色" else if (snapshot != null) "预览 CMF 配色" else "本次更新") },
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
                } else {
                    Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    if (features.isEmpty()) Text("当前没有新的体验项目。")
                    features.forEach { feature ->
                        Text(feature.title, style = MaterialTheme.typography.titleMedium)
                        Text(feature.description)
                        if (isUpdateHighlightAlreadyEnabled(feature, currentSettings)) {
                            Text("你已启用这项功能。", style = MaterialTheme.typography.bodySmall)
                        } else if (!canPreviewUpdateHighlight(feature, currentSettings)) {
                            Text("切换到 MD3 主题并选择自定义颜色后，可从这里预览；当前设置会保持不变。",
                                style = MaterialTheme.typography.bodySmall)
                        } else if (feature.action == UpdateHighlightAction.PREVIEW_CMF) {
                            AppDialogAction(onClick = { previewSnapshot = currentSettings; message = null }) { Text("预览新配色") }
                        }
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (snapshot != null && !applied) {
                if (applying) Text("正在应用…") else AppDialogAction(onClick = {
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
            } else AppDialogAction(onClick = ::dismiss) { Text(if (applied) "完成" else "保持当前设置") }
        },
        dismissButton = {
            if (snapshot != null && !applying) AppDialogAction(onClick = { previewSnapshot = null; message = null }) { Text("返回") }
        },
    )
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
