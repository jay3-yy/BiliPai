package com.android.purebilibili.feature.video.screen

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.android.purebilibili.core.lifecycle.PendingResource
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.feature.video.state.prepareVideoDetailPlayer
import com.android.purebilibili.feature.video.ui.overlay.rememberVideoDetailOpeningCoverImage
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first

/** 详情接管前由入场协程持有新实例；返回或切走当前路由时释放尚未交接的实例。 */
@Composable
internal fun rememberVideoDetailEntryPlayer(
    context: Context,
    bvid: String,
    cid: Long,
    preparedPlayer: PendingResource<ExoPlayer>?,
    reuseAtEntry: Boolean,
    canPrepare: Boolean,
): PendingResource<ExoPlayer>? {
    val entryPlayer = remember(context, bvid, cid) {
        mutableStateOf(preparedPlayer?.takeIf { it.isAvailable })
    }
    val prepareAllowed = rememberUpdatedState(canPrepare)
    LaunchedEffect(entryPlayer, reuseAtEntry) {
        if (reuseAtEntry || entryPlayer.value != null) return@LaunchedEffect
        snapshotFlow { prepareAllowed.value }.first { it }
        val pending = prepareVideoDetailPlayer(context)
        try {
            // 构造期间页面可能已被覆盖，等会话重新可用再创建完整详情。
            if (!prepareAllowed.value) snapshotFlow { prepareAllowed.value }.first { it }
            entryPlayer.value = pending
            awaitCancellation()
        } finally {
            pending.dispose()
        }
    }
    return entryPlayer.value
}

@Composable
internal fun rememberVideoDetailHorizontalAdaptation(context: Context): State<Boolean> {
    val windowSizeClass = LocalWindowSizeClass.current
    val display = LocalAppWindowAdaptiveInfo.current.displayContext
    return remember(context) {
        SettingsManager.getHorizontalAdaptationEnabled(context)
    }.collectAsStateWithLifecycle(
        initialValue = windowSizeClass.isTabletDevice || display.isKnownFoldableDevice,
    )
}

/** 播放器准备期间只绘制来源封面，玻璃与原生详情共用入场占位。 */
@Composable
internal fun VideoDetailOpeningPreview(
    bvid: String,
    coverUrl: String,
    isVertical: Boolean,
    fullscreen: Boolean,
    horizontalAdaptationEnabled: Boolean,
) {
    val openingCover = rememberVideoDetailOpeningCoverImage(bvid)
    val display = LocalAppWindowAdaptiveInfo.current.displayContext
    BoxWithConstraints(Modifier.fillMaxSize().background(AppSurfaceTokens.background())) {
        val width = maxWidth
        val height = maxHeight
        val useLargeLayout = !fullscreen && shouldUseLargeScreenVideoLayout(
            windowWidthDp = width.value,
            windowHeightDp = height.value,
            horizontalAdaptationEnabled = horizontalAdaptationEnabled,
            isFoldableCoverWindow = display.isFoldableCoverWindow,
        )
        val metrics = if (useLargeLayout) {
            resolveLargeScreenVideoMetrics(width.value, height.value, isVertical, enableVerticalExpand = true)
        } else null
        val insets = if (fullscreen || metrics != null) 0.dp else maxOf(
            WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding(),
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
        )
        val coverWidth = metrics?.playerWidthDp?.dp ?: width
        val coverHeight = when {
            fullscreen -> height
            metrics != null -> metrics.playerHeightDp.dp
            else -> resolvePhoneInlineVideoViewportHeightDp(
                width.value, height.value, display.isFoldableCoverWindow,
            ).dp
        }
        val alignment = if (metrics?.mode == LargeScreenVideoLayoutMode.VerticalThreePane) {
            Alignment.TopCenter
        } else Alignment.TopStart
        Box(
            Modifier.align(alignment).width(coverWidth).background(Color.Black).padding(top = insets)
                .height(coverHeight),
        ) {
            if (openingCover != null) {
                Image(openingCover.painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = FormatUtils.fixImageUrl(coverUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
