package com.android.purebilibili.feature.settings

import android.os.Build
import android.widget.Toast
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.feature.audio.player.SLEEP_TIMER_FADE_DURATION_DEFAULT_SEC
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch

private fun AppearanceSceneRecipe.semanticIcon(): ImageVector = when (this) {
    AppearanceSceneRecipe.NATIVE_MD3 -> Icons.Outlined.Palette
    AppearanceSceneRecipe.FLUID_MIUIX -> Icons.Outlined.Tune
    AppearanceSceneRecipe.LIQUID_GLASS -> Icons.Outlined.AutoAwesome
    AppearanceSceneRecipe.MIDNIGHT_AMOLED -> Icons.Outlined.DarkMode
}

private fun PlaybackSceneRecipe.semanticIcon(): ImageVector = when (this) {
    PlaybackSceneRecipe.BINGE_IMMERSIVE -> Icons.Outlined.Movie
    PlaybackSceneRecipe.LATE_NIGHT_PODCAST -> Icons.Outlined.Headphones
    PlaybackSceneRecipe.SPEED_RUNNER -> Icons.Outlined.Bolt
}

/**
 * 外观设置顶部场景化一键配方栏（受「配方与玩法引导」开关控制，默认开启）。
 */
@Composable
internal fun AppearanceSceneRecipesSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val guidesEnabled by remember(context) {
        SettingsManager.getSettingsRecipeGuidesEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    if (!guidesEnabled) return

    val scope = rememberCoroutineScope()
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    val recipes = remember { filterAppearanceRecipesForApi() }
    val uiStyle = LocalAppUiStyle.current
    val themeConfig = LocalAppThemeConfig.current
    val themeMode by remember(context) { SettingsManager.getThemeMode(context) }
        .collectAsStateWithLifecycle(initialValue = AppThemeMode.FOLLOW_SYSTEM)
    val darkThemeStyle by remember(context) { SettingsManager.getDarkThemeStyle(context) }
        .collectAsStateWithLifecycle(initialValue = DarkThemeStyle.DEFAULT)
    val dynamicColorEnabled by remember(context) { SettingsManager.getDynamicColor(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val customizedItems = remember(uiStyle, themeMode, darkThemeStyle, themeConfig.liquidGlassEnabled, dynamicColorEnabled, themeConfig.focusViewingHideStatsEnabled) {
        collectCustomizedAppearanceSettings(
            uiStyleLabel = if (uiStyle == AppUiStyle.MIUIX) "Miuix" else "Material 3",
            isDefaultUiStyle = uiStyle == AppUiStyle.MATERIAL3,
            themeModeLabel = themeMode.label,
            isFollowSystemTheme = themeMode == AppThemeMode.FOLLOW_SYSTEM,
            amoledDarkEnabled = darkThemeStyle == DarkThemeStyle.AMOLED,
            liquidGlassEnabled = themeConfig.liquidGlassEnabled,
            dynamicColorEnabled = dynamicColorEnabled,
            focusViewingHideStatsEnabled = themeConfig.focusViewingHideStatsEnabled,
        )
    }
    var showCustomizedDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        RecipeSectionHeader(
            title = "快速风格配方",
            customizedCount = customizedItems.size,
            onShowCustomized = { showCustomizedDialog = true },
            onHide = {
                scope.launch {
                    SettingsManager.setSettingsRecipeGuidesEnabled(context, false)
                    Toast.makeText(context, "已隐藏配方引导，可在使用技巧中重新开启", Toast.LENGTH_SHORT).show()
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            recipes.forEach { recipe ->
                SceneRecipeCard(
                    icon = recipe.semanticIcon(),
                    title = recipe.title,
                    subtitle = recipe.subtitle,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            when (recipe) {
                                AppearanceSceneRecipe.NATIVE_MD3 -> {
                                    SettingsManager.setUiStyle(context, AppUiStyle.MATERIAL3)
                                    SettingsManager.setAndroidNativeLiquidGlassEnabled(context, false)
                                    SettingsManager.setDynamicColor(context, true)
                                }
                                AppearanceSceneRecipe.FLUID_MIUIX -> {
                                    SettingsManager.setUiStyle(context, AppUiStyle.MIUIX)
                                    SettingsManager.setAndroidNativeLiquidGlassEnabled(context, false)
                                }
                                AppearanceSceneRecipe.LIQUID_GLASS -> {
                                    SettingsManager.setAndroidNativeLiquidGlassEnabled(context, true)
                                    SettingsManager.setDynamicColor(context, true)
                                }
                                AppearanceSceneRecipe.MIDNIGHT_AMOLED -> {
                                    SettingsManager.setThemeMode(context, AppThemeMode.DARK)
                                    SettingsManager.setDarkThemeStyle(context, DarkThemeStyle.AMOLED)
                                }
                            }
                            com.android.purebilibili.core.util.AnalyticsHelper.logRecipeApplied(
                                category = "appearance",
                                recipeId = recipe.name.lowercase(),
                            )
                            Toast.makeText(context, recipe.summaryToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            SceneRecipeCard(
                icon = Icons.Outlined.Restore,
                title = "恢复默认风格",
                subtitle = "Material 3 · 跟随系统",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        SettingsManager.setUiStyle(context, AppUiStyle.MATERIAL3)
                        SettingsManager.setThemeMode(context, AppThemeMode.FOLLOW_SYSTEM)
                        SettingsManager.setDarkThemeStyle(context, DarkThemeStyle.DEFAULT)
                        SettingsManager.setAndroidNativeLiquidGlassEnabled(
                            context,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                        )
                        SettingsManager.setDynamicColor(context, true)
                        Toast.makeText(context, "已恢复默认外观风格", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    if (showCustomizedDialog) {
        CustomizedSettingsSummaryDialog(
            title = "已自定义的外观项",
            items = customizedItems,
            onDismiss = { showCustomizedDialog = false },
        )
    }
}

/**
 * 播放设置顶部高频场景配方栏（受「配方与玩法引导」开关控制，默认开启）。
 */
@Composable
internal fun PlaybackSceneRecipesSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val guidesEnabled by remember(context) {
        SettingsManager.getSettingsRecipeGuidesEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    if (!guidesEnabled) return

    val scope = rememberCoroutineScope()
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    val autoPlayEnabled by remember(context) { SettingsManager.getAutoPlay(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getAutoPlaySync(context))
    val bgPlayEnabled by remember(context) { SettingsManager.getBackgroundPlaybackEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getBackgroundPlaybackEnabledSync(context))
    val loudnessEnabled by remember(context) { SettingsManager.getLoudnessNormalizationEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getLoudnessNormalizationEnabledSync(context))
    val sleepFadeEnabled by remember(context) { SettingsManager.getSleepTimerFadeOutEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getSleepTimerFadeOutEnabledSync(context))
    val sleepFadeDurationSec by remember(context) { SettingsManager.getSleepTimerFadeDurationSec(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getSleepTimerFadeDurationSecSync(context))
    val longPressSpeedLockEnabled by remember(context) { SettingsManager.getLongPressSpeedLockEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getLongPressSpeedLockEnabledSync(context))
    val twoFingerVerticalSpeedEnabled by remember(context) { SettingsManager.getTwoFingerVerticalSpeedEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    val customizedItems = remember(
        autoPlayEnabled,
        bgPlayEnabled,
        loudnessEnabled,
        sleepFadeEnabled,
        sleepFadeDurationSec,
        longPressSpeedLockEnabled,
        twoFingerVerticalSpeedEnabled,
    ) {
        collectCustomizedPlaybackSettings(
            autoPlayEnabled = autoPlayEnabled,
            bgPlayEnabled = bgPlayEnabled,
            loudnessEnabled = loudnessEnabled,
            sleepFadeEnabled = sleepFadeEnabled,
            sleepFadeDurationSec = sleepFadeDurationSec,
            longPressSpeedLockEnabled = longPressSpeedLockEnabled,
            twoFingerVerticalSpeedEnabled = twoFingerVerticalSpeedEnabled,
        )
    }
    var showCustomizedDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        RecipeSectionHeader(
            title = "快速播放配方",
            customizedCount = customizedItems.size,
            onShowCustomized = { showCustomizedDialog = true },
            onHide = {
                scope.launch {
                    SettingsManager.setSettingsRecipeGuidesEnabled(context, false)
                    Toast.makeText(context, "已隐藏配方引导，可在使用技巧中重新开启", Toast.LENGTH_SHORT).show()
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlaybackSceneRecipe.entries.forEach { recipe ->
                SceneRecipeCard(
                    icon = recipe.semanticIcon(),
                    title = recipe.title,
                    subtitle = recipe.subtitle,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            when (recipe) {
                                PlaybackSceneRecipe.BINGE_IMMERSIVE -> {
                                    SettingsManager.setAutoPlay(context, true)
                                    SettingsManager.setVideoAmbientEnabled(context, true)
                                }
                                PlaybackSceneRecipe.LATE_NIGHT_PODCAST -> {
                                    SettingsManager.setStopPlaybackOnExit(context, false)
                                    SettingsManager.setBackgroundPlaybackEnabled(context, true)
                                    SettingsManager.setLoudnessNormalizationEnabled(context, true)
                                    SettingsManager.setSleepTimerFadeOutEnabled(context, true)
                                    SettingsManager.setSleepTimerFadeDurationSec(context, 20)
                                }
                                PlaybackSceneRecipe.SPEED_RUNNER -> {
                                    SettingsManager.setAutoPlay(context, true)
                                    SettingsManager.setLongPressSpeedLockEnabled(context, true)
                                    SettingsManager.setTwoFingerVerticalSpeedEnabled(context, true)
                                }
                            }
                            com.android.purebilibili.core.util.AnalyticsHelper.logRecipeApplied(
                                category = "playback",
                                recipeId = recipe.name.lowercase(),
                            )
                            Toast.makeText(context, recipe.summaryToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            SceneRecipeCard(
                icon = Icons.Outlined.Restore,
                title = "恢复默认播放",
                subtitle = "还原环境光与手势默认值",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        SettingsManager.setVideoAmbientEnabled(context, false)
                        SettingsManager.setLoudnessNormalizationEnabled(context, false)
                        SettingsManager.setLongPressSpeedLockEnabled(context, false)
                        SettingsManager.setTwoFingerVerticalSpeedEnabled(context, false)
                        SettingsManager.setSleepTimerFadeOutEnabled(context, true)
                        SettingsManager.setSleepTimerFadeDurationSec(context, SLEEP_TIMER_FADE_DURATION_DEFAULT_SEC)
                        Toast.makeText(context, "已恢复默认播放配方项", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    if (showCustomizedDialog) {
        CustomizedSettingsSummaryDialog(
            title = "已自定义的播放项",
            items = customizedItems,
            onDismiss = { showCustomizedDialog = false },
        )
    }
}

@Composable
private fun RecipeSectionHeader(
    title: String,
    customizedCount: Int,
    onShowCustomized: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppPreferenceSectionTitle(title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (customizedCount > 0) {
                AppTextButton(onClick = onShowCustomized) {
                    AppText(
                        text = "已改 $customizedCount 项",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.accent(),
                    )
                }
            }
            AppTextButton(onClick = onHide) {
                AppText(
                    text = "隐藏",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.secondaryText(),
                )
            }
        }
    }
}

@Composable
private fun CustomizedSettingsSummaryDialog(
    title: String,
    items: List<CustomizedSettingSummary>,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (items.isEmpty()) {
                    AppText(
                        text = "当前常用设置均为默认值。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.secondaryText(),
                    )
                } else {
                    items.forEachIndexed { index, entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AppText(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = AppContentColors.primaryText(),
                            )
                            AppText(
                                text = "当前：${entry.currentValue}（默认：${entry.defaultValue}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                            )
                        }
                        if (index < items.lastIndex) AppPreferenceDivider(startIndent = 0.dp)
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) { AppText("知道了") }
        },
    )
}

@Composable
private fun SceneRecipeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    AppSurface(
        onClick = onClick,
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
        modifier = Modifier
            .width(172.dp)
            .heightIn(min = 82.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppContentColors.accent(),
                    modifier = Modifier.size(16.dp),
                )
                AppText(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = AppContentColors.primaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 设置搜索空态的「隐藏手感精选」轮换卡片（同样受「配方与玩法引导」开关控制）。
 */
@Composable
internal fun SettingsHiddenGemsSpotlightCard(
    onExploreGem: (SettingsHiddenGem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val guidesEnabled by remember(context) {
        SettingsManager.getSettingsRecipeGuidesEnabled(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    if (!guidesEnabled) return

    val gems = SETTINGS_HIDDEN_GEMS
    if (gems.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    val current = gems[index.coerceIn(0, gems.lastIndex)]
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)

    AppSurface(
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = AppContentColors.accent(),
                        modifier = Modifier.size(16.dp),
                    )
                    AppText(
                        text = "隐藏手感精选",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.accent(),
                    )
                }
                AppIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        index = resolveNextHiddenGemIndex(index, gems.size)
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "换一条推荐",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            AppText(
                text = current.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText(),
            )
            AppText(
                text = current.description,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppTextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExploreGem(current)
                    },
                ) {
                    AppText("去看看")
                }
            }
        }
    }
}
