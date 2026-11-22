package com.android.purebilibili.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import kotlin.math.roundToInt

/** 三种材质共用内容和保存回调，外观由主题组件提供。 */
@Composable
internal fun LiveDanmakuAdvancedSettings(
    settings: DanmakuSettings,
    onStyleChanged: (LiveDanmakuStyleOption, Float) -> Unit,
    onStylePreview: (LiveDanmakuStyleOption, Float) -> Unit,
    onMassiveModeChanged: (Boolean) -> Unit,
    onStaticToScrollChanged: (Boolean) -> Unit,
    onFixedVelocityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
) {
    val glass = com.android.purebilibili.feature.video.ui.components.LocalPlayerGlassSettingsSurface.current
    Column(
        modifier = modifier.then(if (scrollEnabled) Modifier.verticalScroll(scrollState) else Modifier),
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
    ) {
        if (glass) {
            com.android.purebilibili.feature.video.ui.components.DanmakuSettingsSectionTitle("外观")
        } else {
            AppText("调整弹幕外观、停留时间与密度。", color = AppContentColors.secondaryText())
        }
        LiveDanmakuStyleSliderRow(
            label = "字体粗细", valueText = { it.roundToInt().toString() },
            value = settings.fontWeight.toFloat(), valueRange = 1f..9f, steps = 7,
            defaultValue = DanmakuSettings().fontWeight.toFloat(),
            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.FontWeight, it) },
            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.FontWeight, it) },
        )
        LiveDanmakuStyleSliderRow(
            label = "描边粗细", valueText = { "${(it * 10).roundToInt() / 10f} dp" },
            value = settings.strokeWidth, valueRange = 0f..5f, steps = 9,
            defaultValue = DanmakuSettings().strokeWidth,
            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.StrokeWidth, it) },
            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.StrokeWidth, it) },
        )
        LiveDanmakuStyleSliderRow(
            label = "弹幕行高", valueText = { "${(it * 10).roundToInt() / 10f} 倍" },
            value = settings.lineHeight, valueRange = 1f..3f, steps = 19,
            defaultValue = DanmakuSettings().lineHeight,
            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.LineHeight, it) },
            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.LineHeight, it) },
        )
        if (glass) com.android.purebilibili.feature.video.ui.components.DanmakuSettingsSectionTitle("停留时间")
        LiveDanmakuStyleSliderRow(
            label = "滚动弹幕时长", valueText = { "${it.roundToInt()} 秒" },
            value = settings.scrollDurationSeconds, valueRange = 1f..50f, steps = 48,
            defaultValue = DanmakuSettings().scrollDurationSeconds,
            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.ScrollDuration, it) },
            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.ScrollDuration, it) },
        )
        AppText(
            "实际滚动时长还受上一级滚动速度和固定速度选项影响。",
            color = AppContentColors.secondaryText(),
            style = if (glass) androidx.compose.material3.MaterialTheme.typography.bodySmall
                else androidx.compose.material3.LocalTextStyle.current,
        )
        LiveDanmakuStyleSliderRow(
            label = "静态弹幕时长", valueText = { "${it.roundToInt()} 秒" },
            value = settings.staticDurationSeconds, valueRange = 1f..50f, steps = 48,
            defaultValue = DanmakuSettings().staticDurationSeconds,
            onValuePreview = { onStylePreview(LiveDanmakuStyleOption.StaticDuration, it) },
            onValueChangeFinished = { onStyleChanged(LiveDanmakuStyleOption.StaticDuration, it) },
        )
        if (glass) com.android.purebilibili.feature.video.ui.components.DanmakuSettingsSectionTitle("密度与滚动")
        LiveDanmakuAdvancedSwitch("海量弹幕", settings.massiveMode, onMassiveModeChanged, Modifier.fillMaxWidth())
        LiveDanmakuAdvancedSwitch("固定弹幕转滚动", settings.staticDanmakuToScroll, onStaticToScrollChanged, Modifier.fillMaxWidth())
        LiveDanmakuAdvancedSwitch("滚动弹幕固定速度", settings.scrollFixedVelocity, onFixedVelocityChanged, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LiveDanmakuAdvancedSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (com.android.purebilibili.feature.video.ui.components.LocalPlayerGlassSettingsSurface.current) {
        com.android.purebilibili.feature.video.ui.components.DanmakuSettingsToggle(title, checked, onCheckedChange, modifier)
        return
    }
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
    ) {
        AppText(title, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
