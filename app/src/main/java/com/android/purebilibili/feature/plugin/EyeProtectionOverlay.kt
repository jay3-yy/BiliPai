package com.android.purebilibili.feature.plugin

import com.android.purebilibili.plugin.sdk.EyeCareReminder
import com.android.purebilibili.plugin.sdk.EyeProtectionPluginApi

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.continuityTween
import com.android.purebilibili.core.ui.AppSpacingTokens

@Composable
fun EyeProtectionOverlay(
    playbackActive: Boolean = false
) {
    val plugins by PluginManager.pluginsFlow.collectAsStateWithLifecycle()
    val plugin = remember(plugins) { PluginManager.getPlugin(EyeProtectionPluginApi::class) } ?: return
    val pluginEnabled = PluginManager.isEnabled(plugin.id)
    val settingsPreviewEnabled by plugin.settingsPreviewEnabled.collectAsStateWithLifecycle()
    if (!pluginEnabled && !settingsPreviewEnabled) return

    val isNightModeActive by plugin.isNightModeActive.collectAsStateWithLifecycle()
    val brightnessLevel by plugin.brightnessLevel.collectAsStateWithLifecycle()
    val warmFilterStrength by plugin.warmFilterStrength.collectAsStateWithLifecycle()
    val careReminder by plugin.careReminder.collectAsStateWithLifecycle()
    val weakenDuringPlayback by plugin.weakenDuringPlayback.collectAsStateWithLifecycle()

    val paint = remember(
        brightnessLevel,
        warmFilterStrength,
        playbackActive,
        weakenDuringPlayback,
        isNightModeActive,
        settingsPreviewEnabled
    ) {
        val weaken = playbackActive && weakenDuringPlayback && !settingsPreviewEnabled
        if (!isNightModeActive && !settingsPreviewEnabled) {
            EyeOverlayPaint(dimAlpha = 0f, warmAlpha = 0f)
        } else {
            resolveEyeOverlayPaint(
                brightnessLevel = brightnessLevel,
                warmFilterStrength = warmFilterStrength,
                playbackWeaken = weaken
            )
        }
    }

    val darknessAlpha = animateFloatAsState(
        targetValue = paint.dimAlpha,
        animationSpec = continuityTween(420),
        label = "eye_darkness"
    )
    val warmAlpha = animateFloatAsState(
        targetValue = paint.warmAlpha,
        animationSpec = continuityTween(420),
        label = "eye_warm"
    )
    val warmColor = Color(paint.warmColor)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val dim = darknessAlpha.value
        val warm = warmAlpha.value
        if (dim > 0.004f) {
            drawRect(color = Color.Black.copy(alpha = dim))
        }
        if (warm > 0.004f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to warmColor.copy(alpha = (warm * 1.12f).coerceAtMost(0.38f)),
                    0.45f to warmColor.copy(alpha = warm),
                    1f to warmColor.copy(alpha = warm * 0.72f)
                )
            )
        }
    }

    careReminder?.let { reminder ->
        RestReminderDialog(
            reminder = reminder,
            snoozeMinutes = plugin.getSnoozeMinutes(),
            onDismiss = { plugin.dismissReminder() },
            onSnooze = { plugin.snoozeReminder() },
            onRest = { plugin.confirmRest() }
        )
    }
}

@Composable
private fun RestReminderDialog(
    reminder: EyeCareReminder,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onRest: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenHeightDp) {
        resolveEyeReminderDialogLayoutPolicy(screenHeightDp = configuration.screenHeightDp)
    }
    val accent = AppContentColors.accent()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(layoutPolicy.maxHeightFraction),
        shape = AppShapes.container(ContainerLevel.Dialog),
        containerColor = AppSurfaceTokens.cardContainer(),
        tonalElevation = 6.dp,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = accent.copy(alpha = 0.14f),
                        shape = AppShapes.container(ContainerLevel.Card)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            AppText(
                text = reminder.title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                AppText(
                    text = "已使用 BiliPai 约 ${reminder.usageMinutes} 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppSurfaceTokens.onSurfaceVariantSummary(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                AppText(
                    text = reminder.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.primaryText(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                AppText(
                    text = reminder.suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            AppButton(
                onClick = onRest,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = AppShapes.container(ContainerLevel.Card),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppText("我已休息", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            if (layoutPolicy.useCompactSecondaryActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextButton(
                        onClick = onSnooze,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppText("${snoozeMinutes} 分钟后再说")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    AppTextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppText(
                            "先继续看",
                            color = AppSurfaceTokens.onSurfaceVariantActions()
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppTextButton(
                        onClick = onSnooze,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppText("${snoozeMinutes} 分钟后再说")
                    }
                    AppTextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppText(
                            "先继续看",
                            color = AppSurfaceTokens.onSurfaceVariantActions()
                        )
                    }
                }
            }
        }
    )
}
