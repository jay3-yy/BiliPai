package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.feature.audio.player.SLEEP_TIMER_FADE_DURATION_DEFAULT_SEC
import com.android.purebilibili.feature.audio.player.SLEEP_TIMER_FADE_DURATION_MAX_SEC
import com.android.purebilibili.feature.audio.player.SLEEP_TIMER_FADE_DURATION_MIN_SEC
import com.android.purebilibili.feature.audio.player.SLEEP_TIMER_FADE_DURATION_PRESETS_SEC
import com.android.purebilibili.feature.audio.player.SLEEP_TIMER_QUICK_EXTEND_MINUTES
import com.android.purebilibili.feature.audio.player.SleepTimerFinishedEvent
import com.android.purebilibili.feature.audio.player.parseSleepTimerFadeDurationInput
import kotlinx.coroutines.delay

/**
 * 深夜时段在定时关闭面板顶部展示的轻量一键建议。
 */
@Composable
internal fun LateNightSleepSuggestionCard(
    onEnableAfterCurrent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    AppSurface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onEnableAfterCurrent()
        },
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(
                imageVector = Icons.Outlined.DarkMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = "夜深了，播完这集就休息？",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                AppText(
                    text = "点此一键设为播完当前内容后停播",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

/**
 * 定时关闭结束前渐弱淡出开关行，支持点击「淡出时长」按需弹窗自定义秒数。
 */
@Composable
internal fun SleepTimerFadePreferenceRow(
    fadeEnabled: Boolean,
    onFadeEnabledChange: (Boolean) -> Unit,
    fadeDurationSec: Int = SLEEP_TIMER_FADE_DURATION_DEFAULT_SEC,
    onFadeDurationSecChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showDurationDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = "结束前渐弱淡出",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.primaryText(),
                )
                AppText(
                    text = "最后 $fadeDurationSec 秒平滑降低音量，避免突然中断",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
            }
            AppSwitch(
                checked = fadeEnabled,
                onCheckedChange = onFadeEnabledChange,
            )
        }
        if (fadeEnabled && onFadeDurationSecChange != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = "渐弱淡出时长",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
                AppTextButton(onClick = { showDurationDialog = true }) {
                    AppText("$fadeDurationSec 秒 · 调整")
                }
            }
        }
    }

    if (showDurationDialog && onFadeDurationSecChange != null) {
        var inputText by remember(fadeDurationSec) { mutableStateOf(fadeDurationSec.toString()) }
        val parsedSec = remember(inputText) { parseSleepTimerFadeDurationInput(inputText) }
        val showError = inputText.isNotBlank() && parsedSec == null

        AppAlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { AppText("渐弱淡出时长") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SLEEP_TIMER_FADE_DURATION_PRESETS_SEC.forEach { preset ->
                            AppFilterChip(
                                selected = parsedSec == preset,
                                onClick = { inputText = preset.toString() },
                                label = { AppText("$preset 秒") },
                            )
                        }
                    }
                    AppOutlinedTextField(
                        value = inputText,
                        onValueChange = { value -> inputText = value.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        label = { AppText("自定义秒数") },
                        placeholder = { AppText("$SLEEP_TIMER_FADE_DURATION_MIN_SEC–$SLEEP_TIMER_FADE_DURATION_MAX_SEC") },
                        isError = showError,
                        supportingText = {
                            AppText(
                                if (showError) {
                                    "请输入 $SLEEP_TIMER_FADE_DURATION_MIN_SEC 到 $SLEEP_TIMER_FADE_DURATION_MAX_SEC 之间的整数秒"
                                } else {
                                    "定时结束前 ${parsedSec ?: fadeDurationSec} 秒开始平滑降音"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = parsedSec != null,
                    onClick = {
                        parsedSec?.let(onFadeDurationSecChange)
                        showDurationDialog = false
                    },
                ) { AppText("保存") }
            },
            dismissButton = {
                AppTextButton(onClick = { showDurationDialog = false }) { AppText("取消") }
            },
        )
    }
}

/**
 * 前台定时结束后的温和晚安收尾轻浮条，支持一键续时 15 分钟并自动消退。
 */
@Composable
internal fun SleepTimerGoodnightOverlay(
    event: SleepTimerFinishedEvent?,
    onExtend15Minutes: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    extendLabel: String = "再听 $SLEEP_TIMER_QUICK_EXTEND_MINUTES 分钟",
) {
    val reduceMotion = rememberSystemReduceMotion()
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)

    LaunchedEffect(event?.id) {
        if (event != null) {
            delay(8_000L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = event != null,
        enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically { it / 2 },
        exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val activeEvent = event ?: return@AnimatedVisibility
        AppSurface(
            shape = AppShapes.container(ContainerLevel.Card),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(
                    imageVector = Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = AppContentColors.accent(),
                    modifier = Modifier.size(18.dp),
                )
                AppText(
                    text = activeEvent.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.primaryText(),
                    modifier = Modifier.weight(1f),
                )
                AppTextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExtend15Minutes()
                    },
                ) {
                    AppText(extendLabel)
                }
                AppIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭晚安提示",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
