package com.android.purebilibili.feature.settings.screen

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val WHEEL_CYCLES = 201

internal fun closestTimeWheelIndex(state: LazyListState): Int? {
    val layout = state.layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    return layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
}

/** Gesture velocity drives the native spline decay, followed by center snapping.
 * No fixed one-step fling or per-frame composition reads; a new touch interrupts inertia.
 */
@Composable
internal fun ThemeTimeWheelPicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onHourScrolling: (Boolean) -> Unit,
    onMinuteScrolling: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rowHeight = 44.dp * density.fontScale.coerceAtLeast(1f)
    val visibleRows = if (density.fontScale > 1.5f) 3 else 5
    val wheelHeight = rowHeight * visibleRows
    val surface = AppSurfaceTokens.surface()
    Box(modifier = modifier.height(wheelHeight), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(AppSurfaceTokens.selectionContainer()),
        )
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeTimeWheelColumn(
                value = hour, count = 24, label = "小时", unit = "时",
                rowHeight = rowHeight, visibleRows = visibleRows,
                onValueChange = onHourChange, onScrolling = onHourScrolling,
                modifier = Modifier.weight(1f),
            )
            ThemeTimeWheelColumn(
                value = minute, count = 60, label = "分钟", unit = "分",
                rowHeight = rowHeight, visibleRows = visibleRows,
                onValueChange = onMinuteChange, onScrolling = onMinuteScrolling,
                modifier = Modifier.weight(1f),
            )
        }
        // Paint-only edge fade keeps the highlight and center digits clear, without an offscreen mask.
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to surface, 0.25f to surface.copy(alpha = 0.65f),
            0.4f to surface.copy(alpha = 0f), 0.6f to surface.copy(alpha = 0f),
            0.75f to surface.copy(alpha = 0.65f), 1f to surface,
        )))
    }
}

@Composable
private fun ThemeTimeWheelColumn(
    value: Int,
    count: Int,
    label: String,
    unit: String,
    rowHeight: androidx.compose.ui.unit.Dp,
    visibleRows: Int,
    onValueChange: (Int) -> Unit,
    onScrolling: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Repeated clock values allow 23 → 00 and 59 → 00 without an abrupt edge stop.
    val initialIndex = remember { (WHEEL_CYCLES / 2) * count + value }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state, snapPosition = SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberSystemReduceMotion()
    val latestChange by rememberUpdatedState(onValueChange)
    val latestScrolling by rememberUpdatedState(onScrolling)
    val haptic = LocalHapticFeedback.current
    val hapticEnabled by rememberUpdatedState(LocalAppThemeConfig.current.hapticFeedbackEnabled)
    val accent = AppContentColors.selectionAccent(LocalAppThemeConfig.current.liquidGlassEnabled)
    val neutral = AppContentColors.secondaryText()
    LaunchedEffect(state, count, haptic) {
        var previousIndex = initialIndex
        var lastHapticTime = 0L
        snapshotFlow { closestTimeWheelIndex(state) }.distinctUntilChanged().collect { index ->
            if (index != null) {
                latestChange(index % count)
                val now = SystemClock.uptimeMillis()
                if (index != previousIndex && state.isScrollInProgress && hapticEnabled && now - lastHapticTime >= 40L) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticTime = now
                }
                previousIndex = index
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { latestScrolling(it) }
    }
    fun select(index: Int) {
        scope.launch {
            val target = index.coerceIn(0, count * WHEEL_CYCLES - 1)
            if (reduceMotion) state.scrollToItem(target) else state.animateScrollToItem(target)
        }
    }
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = rowHeight * (visibleRows / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(rowHeight * visibleRows)
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = label
                stateDescription = "%02d".format(value)
                customActions = listOf(
                    CustomAccessibilityAction("增加$label") {
                        select((closestTimeWheelIndex(state) ?: initialIndex) + 1); true
                    },
                    CustomAccessibilityAction("减少$label") {
                        select((closestTimeWheelIndex(state) ?: initialIndex) - 1); true
                    },
                )
            },
    ) {
        items(count * WHEEL_CYCLES, key = { it }) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .clickable { select(index) }
                    .clearAndSetSemantics { }
                    .graphicsLayer {
                        val layout = state.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                        val distance = item?.let { (it.offset + it.size / 2f - center) / it.size.coerceAtLeast(1) } ?: 3f
                        val weight = (1f - abs(distance) / 3f).coerceIn(0f, 1f)
                        alpha = 0.25f + 0.75f * weight
                        scaleX = if (reduceMotion) 1f else 0.82f + 0.18f * weight
                        scaleY = scaleX
                        rotationX = if (reduceMotion) 0f else (distance * -16f).coerceIn(-65f, 65f)
                        cameraDistance = 12f * density
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AppText(
                    text = "%02d".format(index % count),
                    color = if (index % count == value) accent else neutral,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    tapToCopyEnabled = false,
                )
                AppText(
                    text = unit,
                    color = accent,
                    fontSize = 16.sp,
                    maxLines = 1,
                    tapToCopyEnabled = false,
                    modifier = Modifier.width(24.dp).graphicsLayer {
                        alpha = if (closestTimeWheelIndex(state) == index) 1f else 0f
                    },
                )
            }
        }
    }
}
