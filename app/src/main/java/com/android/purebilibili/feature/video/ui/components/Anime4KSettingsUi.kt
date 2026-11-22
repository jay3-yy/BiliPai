package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.anime4k.Anime4KBypassReason
import com.android.purebilibili.plugin.sdk.Anime4KPreset
import com.android.purebilibili.feature.anime4k.FSR_SHARPNESS_SLIDER_STEPS
import com.android.purebilibili.plugin.sdk.VideoEnhancementAlgorithm
import com.android.purebilibili.feature.anime4k.resolveAnime4KPresetLabel
import kotlin.math.roundToInt

@Composable
internal fun VideoEnhancementAlgorithmOptions(
    algorithm: VideoEnhancementAlgorithm,
    onAlgorithmChange: (VideoEnhancementAlgorithm) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoEnhancementAlgorithm.entries.forEach { value ->
            AppFilterChip(
                selected = algorithm == value,
                onClick = { onAlgorithmChange(value) },
                label = {
                    AppText(
                        when (value) {
                            VideoEnhancementAlgorithm.ANIME4K -> "Anime4K（动漫）"
                            VideoEnhancementAlgorithm.FSR_1_0 -> "FSR 1.0（通用）"
                        }
                    )
                }
            )
        }
    }
}

@Composable
internal fun Anime4KPresetOptions(
    preset: Anime4KPreset,
    onPresetChange: (Anime4KPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Anime4KPreset.FAST,
        Anime4KPreset.QUALITY
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { value ->
            AppFilterChip(
                selected = preset == value,
                onClick = { onPresetChange(value) },
                label = { AppText(resolveAnime4KPresetLabel(value)) }
            )
        }
    }
}

@Composable
internal fun FsrSharpnessOptions(
    sharpness: Float,
    onSharpnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeSharpness = sharpness.coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = "FSR 锐化",
                style = MaterialTheme.typography.labelLarge,
                color = AppContentColors.secondaryText()
            )
            AppText(
                text = "${(safeSharpness * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText()
            )
        }
        AppSlider(
            value = safeSharpness,
            onValueChange = onSharpnessChange,
            valueRange = 0f..1f,
            steps = FSR_SHARPNESS_SLIDER_STEPS,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun resolveAnime4KSettingsSubtitle(
    enabled: Boolean,
    available: Boolean,
    bypassReason: Anime4KBypassReason
): String {
    if (!available) return "当前设备不支持 OpenGL ES 3.0"
    if (!enabled) return "关闭"
    return when (bypassReason) {
        Anime4KBypassReason.NONE -> "已启用"
        Anime4KBypassReason.HDR_OR_DOLBY_VISION -> "HDR / 杜比视界使用原始输出"
        Anime4KBypassReason.PICTURE_IN_PICTURE -> "小窗模式使用原始输出"
        Anime4KBypassReason.AUDIO_ONLY -> "音频模式使用原始输出"
        Anime4KBypassReason.HOST_NOT_STARTED -> "后台时使用原始输出"
        Anime4KBypassReason.GL_UNAVAILABLE -> "渲染管线不可用"
        Anime4KBypassReason.DISABLED -> "关闭"
    }
}
