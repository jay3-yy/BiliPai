package com.android.purebilibili.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.store.LiquidGlassAdvancedSettings
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy
import com.android.purebilibili.feature.home.components.isOfficialProgressiveBlurSupported
import com.android.purebilibili.feature.home.components.OFFICIAL_BACKDROP_MIN_FULL_SDK
import com.android.purebilibili.feature.home.components.rememberOfficialProgressiveTopBlur
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur

/** 两个真实渲染器消费相同画面；交互只移动预览背景，不修改已保存设置。 */
@Composable
internal fun ProgressiveBlurComparisonPreviewEntry(
    advancedSettings: LiquidGlassAdvancedSettings,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.preview_blur_compare_title)
    AppIconButton(
        onClick = { open = true },
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = description },
    ) {
        AppIcon(
            painter = painterResource(settingsIconResource(R.drawable.ph_play_circle_24, R.drawable.ms_play_circle_outline_24)),
            contentDescription = null,
            tint = AppContentColors.secondaryText(),
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) {
        ProgressiveBlurComparisonDialog(advancedSettings, onDismiss = { open = false })
    }
}

@Composable
private fun ProgressiveBlurComparisonDialog(
    advancedSettings: LiquidGlassAdvancedSettings,
    onDismiss: () -> Unit,
) {
    val scrollPosition = remember { mutableFloatStateOf(0.35f) }
    val positionProvider = remember { { scrollPosition.floatValue } }
    val tuning = remember(advancedSettings) { resolveLiquidGlassTuning(0.5f, advancedSettings) }
    val gradient = remember(tuning) {
        ProgressiveBlur.Top.copy(
            startFraction = tuning.progressiveBlurStartFraction,
            endFraction = tuning.progressiveBlurEndFraction,
            curve = tuning.progressiveBlurCurve,
        )
    }
    val contentHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.6f).coerceAtLeast(80.dp)
    AppAlertDialog(
        onDismissRequest = onDismiss,
        contentLayout = resolveAppContentDialogLayoutPolicy(maxWidthDp = 640, minWidthDp = 0, horizontalPaddingDp = 16),
        title = { AppText(stringResource(R.string.preview_blur_compare_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = contentHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(stringResource(R.string.preview_blur_compare_summary), color = AppContentColors.secondaryText())
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 480.dp) {
                        val panelWidth = (maxWidth - 12.dp) / 2
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProgressiveBlurComparisonPanel(false, tuning.progressiveBlurRadius, gradient, positionProvider, Modifier.width(panelWidth))
                            ProgressiveBlurComparisonPanel(true, tuning.progressiveBlurRadius, gradient, positionProvider, Modifier.width(panelWidth))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProgressiveBlurComparisonPanel(false, tuning.progressiveBlurRadius, gradient, positionProvider, Modifier.fillMaxWidth())
                            ProgressiveBlurComparisonPanel(true, tuning.progressiveBlurRadius, gradient, positionProvider, Modifier.fillMaxWidth())
                        }
                    }
                }
                AppText(stringResource(R.string.preview_blur_compare_scroll), style = MaterialTheme.typography.labelMedium)
                ComparisonScrollSlider(scrollPosition)
            }
        },
        confirmButton = {
            AppDialogAction(onClick = onDismiss) { AppText(stringResource(R.string.common_close)) }
        },
    )
}

/** 滑动状态仅在控件与绘制阶段读取，两个预览不随拖动重建效果链。 */
@Composable
private fun ComparisonScrollSlider(position: androidx.compose.runtime.MutableFloatState) {
    val description = stringResource(R.string.preview_blur_compare_scroll)
    AppSlider(
        value = position.floatValue,
        onValueChange = { position.floatValue = it },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    )
}

@Composable
private fun ProgressiveBlurComparisonPanel(
    official: Boolean,
    radius: Float,
    gradient: ProgressiveBlur,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val source = if (!official) rememberChromeBackdropSource() else null
    val supported = if (official) isOfficialProgressiveBlurSupported() else Build.VERSION.SDK_INT >= 33
    val platformVersionTooLow = official && (Build.VERSION.SDK_INT < 36 ||
        Build.VERSION.SDK_INT_FULL < OFFICIAL_BACKDROP_MIN_FULL_SDK)
    val effect = when {
        !supported -> null
        radius <= 0.001f -> Modifier
        official -> rememberOfficialProgressiveTopBlur(RectangleShape, radius, gradient)
        else -> remember(source, radius, gradient) {
            Modifier.progressiveTextureBlur(
                backdrop = requireNotNull(source).backdrop,
                shape = RectangleShape,
                blurRadius = radius,
                gradient = gradient,
            )
        }
    }
    val label = if (official) stringResource(R.string.animation_progressive_renderer_android) else "Miuix"
    val accent = AppContentColors.accent()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(label, style = MaterialTheme.typography.titleSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (effect != null) {
                PreviewScrollingCards(
                    progress = progress,
                    accent = accent,
                    modifier = Modifier.fillMaxSize().then(source?.modifier ?: Modifier),
                )
                if (source == null || source.isReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.TopCenter)
                            .then(effect),
                    )
                }
                AppText(
                    text = stringResource(R.string.preview_home_top_title),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                AppText(
                    if (platformVersionTooLow) {
                        val currentApi = if (Build.VERSION.SDK_INT >= 36) {
                            val full = Build.VERSION.SDK_INT_FULL
                            "${full / 100_000}.${full % 100_000}"
                        } else Build.VERSION.SDK_INT.toString()
                        stringResource(R.string.preview_blur_compare_api_requirement, currentApi)
                    } else stringResource(R.string.preview_blur_compare_unavailable, label),
                    modifier = Modifier.padding(16.dp),
                    color = AppContentColors.secondaryText(),
                )
            }
        }
    }
}
