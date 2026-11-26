package com.android.purebilibili.feature.audio.ambient

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import kotlin.math.roundToInt

/**
 * 听视频助眠环境音混音控件，内嵌于听视频定时面板中。
 */
@Composable
internal fun AmbientNoiseMixerSection(
    modifier: Modifier = Modifier,
) {
    val state by AmbientNoiseMixer.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                text = "助眠环境音混音",
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.primaryText(),
            )
            AppText(
                text = if (state.isPlaying) "${state.preset.label} · ${(state.volume * 100).roundToInt()}%" else "本地合成 · 定时结束自动停",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AmbientNoisePreset.entries.forEach { preset ->
                val selected = (preset == AmbientNoisePreset.NONE && !state.isPlaying) ||
                    (state.isPlaying && state.preset == preset)
                AppFilterChip(
                    selected = selected,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        AmbientNoiseMixer.selectPreset(preset)
                    },
                    label = { AppText(preset.label) },
                )
            }
        }

        if (state.isPlaying && state.preset != AmbientNoisePreset.NONE) {
            AppSlider(
                value = state.volume,
                onValueChange = { AmbientNoiseMixer.setVolume(it) },
                valueRange = AMBIENT_NOISE_MIN_VOLUME..AMBIENT_NOISE_MAX_VOLUME,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
