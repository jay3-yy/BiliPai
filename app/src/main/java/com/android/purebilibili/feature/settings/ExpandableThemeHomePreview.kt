package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalPredictiveBackGestureEnabled
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.rememberExpandablePreviewMotion
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 只展开当前分组的配色小样；宿主继续决定玻璃、MD3、Miuix 的材质与圆角。 */
@Composable
internal fun ExpandableThemeHomePreview(modifier: Modifier = Modifier) {
    val motion = rememberExpandablePreviewMotion()
    var selectedNavigationIndex by rememberSaveable { mutableIntStateOf(0) }
    val previewScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    val predictive = LocalPredictiveBackGestureEnabled.current && motion.predictive
    val latestMotion by rememberUpdatedState(motion)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = motion.expanded,
        onBackCancelled = motion.onBackCancelled,
        onBackCompleted = motion.close,
    )
    LaunchedEffect(backState, predictive) {
        var tracking = false
        snapshotFlow { backState.transitionState }.collect { transition ->
            if (predictive && latestMotion.expanded && transition is NavigationEventTransitionState.InProgress) {
                if (!tracking) {
                    latestMotion.onBackStarted()
                    tracking = true
                }
                latestMotion.onBackProgress(transition.latestEvent.progress)
            } else {
                tracking = false
            }
        }
    }
    val previewMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !motion.expanded, role = Role.Button, onClickLabel = "展开首页配色预览") {
                        scope.launch {
                            previewScroll.scrollTo(0)
                            motion.open()
                        }
                    }
                    .focusProperties { canFocus = !motion.expanded }
                    .graphicsLayer { alpha = (1f - latestMotion.progress.value * 2f).coerceIn(0f, 1f) }
                    .then(if (motion.expanded) Modifier.clearAndSetSemantics { } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeHomePreview(selectedNavigationIndex = selectedNavigationIndex)
                AppText("点击展开配色预览", style = MaterialTheme.typography.labelMedium, color = AppContentColors.secondaryText())
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = previewMaxHeight)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .focusProperties { canFocus = motion.expanded }
                    .graphicsLayer { alpha = ((latestMotion.progress.value - 0.3f) / 0.7f).coerceIn(0f, 1f) }
                    .then(if (!motion.expanded) Modifier.clearAndSetSemantics { } else Modifier)
                    .verticalScroll(previewScroll, enabled = motion.expanded)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText("首页配色预览", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    AppTextButton(onClick = motion.close, enabled = motion.expanded) { AppText("收起") }
                }
                ThemeHomePreview(
                    expanded = true,
                    selectedNavigationIndex = selectedNavigationIndex,
                    onNavigationSelected = if (motion.expanded) ({ index: Int -> selectedNavigationIndex = index }) else null,
                )
                ThemePreviewRoleSwatches()
                AppText(
                    "可点击底栏查看选中配色，仅在此处演示，不改变设置或打开页面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
            }
        },
    ) { measurables, constraints ->
        // 两端各按最终宽度排版，逐帧只插值外壳高度，避免字号和卡片被拉伸。
        val compact = measurables[0].measure(constraints.copy(minHeight = 0))
        // 横屏或窄分屏空间不足时保持至少与小样等高，扩展内容改为滚动。
        val expanded = measurables[1].measure(constraints.copy(minHeight = compact.height))
        val progress = latestMotion.progress.value.coerceIn(0f, 1f)
        val height = constraints.constrainHeight((compact.height + (expanded.height - compact.height) * progress).roundToInt())
        layout(constraints.maxWidth, height) {
            if (motion.expanded) {
                compact.placeRelative(0, 0)
                expanded.placeRelative(0, 0)
            } else {
                expanded.placeRelative(0, 0)
                compact.placeRelative(0, 0)
            }
        }
    }
}

@Composable
private fun ThemePreviewRoleSwatches(modifier: Modifier = Modifier) {
    val roles = listOf(
        "主色" to AppSurfaceTokens.primary(),
        "背景" to AppSurfaceTokens.groupedListContainer(),
        "容器" to AppSurfaceTokens.cardContainer(),
        "文字" to AppContentColors.primaryText(),
    )
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        roles.forEach { (label, color) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(32.dp).clip(AppShapes.container(ContainerLevel.Field)).background(color))
                AppText(label, style = MaterialTheme.typography.labelSmall, color = AppContentColors.secondaryText())
            }
        }
    }
}
