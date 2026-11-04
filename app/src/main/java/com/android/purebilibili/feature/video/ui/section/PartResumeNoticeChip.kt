package com.android.purebilibili.feature.video.ui.section

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.video.viewmodel.PartResumeNotice
import com.android.purebilibili.feature.video.viewmodel.buildPartResumeNoticeMessage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 左滑移除的触发阈值（px）。 */
private const val PART_RESUME_DISMISS_THRESHOLD_PX = 96f
private const val PART_RESUME_NOTICE_DURATION_MS = 5_000L

/**
 * 「上次看到第 N P，点击跳转」提示条：点击跳转、左滑移除，默认 5 秒后自动隐藏。
 */
@Composable
internal fun PartResumeNoticeChip(
    notice: PartResumeNotice,
    onJump: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_SEEK)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val accessibilityManager = LocalAccessibilityManager.current
    val timeoutMillis = accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = PART_RESUME_NOTICE_DURATION_MS,
        containsText = true,
        containsControls = true,
    ) ?: PART_RESUME_NOTICE_DURATION_MS
    LaunchedEffect(notice, timeoutMillis) {
        delay(timeoutMillis)
        currentOnDismiss()
    }
    var offsetX by remember(notice.requestToken, notice.mediaKey) { mutableFloatStateOf(0f) }
    Text(
        text = buildPartResumeNoticeMessage(notice.partNumber),
        color = Color.White,
        fontSize = 13.sp,
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(notice.requestToken, notice.mediaKey) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX <= -PART_RESUME_DISMISS_THRESHOLD_PX) {
                            onDismiss()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                ) { change, dragAmount ->
                    // 只允许向左拖出；向右拖回时不超过原位。
                    offsetX = (offsetX + dragAmount).coerceAtMost(0f)
                    change.consume()
                }
            }
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable {
                haptic(HapticType.LIGHT)
                onJump()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
