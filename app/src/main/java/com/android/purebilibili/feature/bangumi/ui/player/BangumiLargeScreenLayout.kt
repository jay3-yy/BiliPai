package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.video.screen.LargeScreenVideoLayoutMode
import com.android.purebilibili.feature.video.screen.resolveLargeScreenVideoMetrics

/** 复用视频详情的播放器比例与侧栏宽度，选集保留在番剧简介中。 */
@Composable
internal fun BangumiLargeScreenLayout(
    modifier: Modifier = Modifier,
    playerContent: @Composable () -> Unit,
    supportingContent: @Composable (BangumiPlayerContentMode) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(maxWidth, maxHeight) {
            resolveLargeScreenVideoMetrics(
                windowWidthDp = maxWidth.value,
                windowHeightDp = maxHeight.value,
                isVerticalVideo = false,
            )
        }
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        when (metrics.mode) {
            LargeScreenVideoLayoutMode.Landscape, LargeScreenVideoLayoutMode.Split -> {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(metrics.playerWidthDp.dp).fillMaxHeight()) {
                        Box(Modifier.fillMaxWidth().height(metrics.playerHeightDp.dp)) {
                            playerContent()
                        }
                        if (metrics.introBelowPlayer) {
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                supportingContent(BangumiPlayerContentMode.Introduction)
                            }
                        }
                    }
                    VerticalDivider(color = dividerColor)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        supportingContent(
                            if (metrics.introBelowPlayer) BangumiPlayerContentMode.Comments
                            else BangumiPlayerContentMode.Tabs,
                        )
                    }
                }
            }
            LargeScreenVideoLayoutMode.AlmostSquare -> {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(metrics.playerHeightDp.dp)) {
                        playerContent()
                    }
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            supportingContent(BangumiPlayerContentMode.Introduction)
                        }
                        VerticalDivider(color = dividerColor)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            supportingContent(BangumiPlayerContentMode.Comments)
                        }
                    }
                }
            }
            LargeScreenVideoLayoutMode.Phone, LargeScreenVideoLayoutMode.VerticalThreePane -> {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(metrics.playerHeightDp.dp)) {
                        playerContent()
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        supportingContent(BangumiPlayerContentMode.Tabs)
                    }
                }
            }
        }
    }
}
