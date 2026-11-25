package com.android.purebilibili.core.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.WindowRegionLayout
import com.android.purebilibili.core.util.AppHingeSafePane
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.layoutHinges
import com.android.purebilibili.core.util.resolveHingeSafeContentRegions
import com.android.purebilibili.core.util.resolveHingeSafeRegion

/** A single scroll viewport: full-line headers and any column count stay clear of every hinge. */
@Composable
internal fun AppHingeSafeContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val adaptiveInfo = LocalAppWindowAdaptiveInfo.current
    val savedState = rememberSaveableStateHolder()
    val savedContent: @Composable BoxWithConstraintsScope.() -> Unit = {
        savedState.SaveableStateProvider("content") { content() }
    }
    if (!adaptiveInfo.shouldAvoidHinge) {
        BoxWithConstraints(modifier = modifier, content = savedContent)
        return
    }
    val hinges = adaptiveInfo.foldingFeature.layoutHinges()
    // 非遮挡铰链一律允许内容整体跨越：折叠机半开时 Jetpack 会把 FOLD 上报为
    // isSeparating=true（视为两个逻辑屏），若按 separating 避让，Tabletop/半折
    // 姿态下信息流会被整体钳进单侧 pane，另一侧整块留白。国内折叠机全是软折痕
    // （FOLD、无物理缝隙），跨折痕滚动可接受；仅 HINGE 类遮挡铰链仍走分窗避让。
    if (hinges.all { hinge -> !hinge.isOccluding }) {
        BoxWithConstraints(modifier = modifier, content = savedContent)
        return
    }
    val clearancePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    WindowRegionLayout(
        modifier = modifier,
        regionProvider = { size, origin ->
            val regions = resolveHingeSafeContentRegions(size.width, size.height, hinges, origin, clearancePx)
            listOfNotNull(resolveHingeSafeRegion(regions, AppHingeSafePane.Largest))
        },
        primaryContent = { BoxWithConstraints(Modifier.fillMaxSize(), content = savedContent) },
    )
}

internal fun resolveHingeSafeFeedColumns(
    requestedColumns: Int,
    availableWidthDp: Float,
    minCardWidthDp: Int = 180,
): Int = requestedColumns.coerceIn(
    1,
    (availableWidthDp / minCardWidthDp.coerceAtLeast(1)).toInt().coerceAtLeast(1),
)
