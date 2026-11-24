package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppGlassMenu
import com.android.purebilibili.core.ui.components.LocalAppGlassPanelDismissAction
import com.android.purebilibili.core.ui.components.AppGlassMenuSource
import com.android.purebilibili.core.ui.components.AppGlassMenuPlacement
import com.android.purebilibili.core.ui.components.appGlassMenuAvailable
import androidx.compose.ui.platform.LocalConfiguration
import com.android.purebilibili.data.model.response.RecommendationFeedbackReason
import com.android.purebilibili.data.model.response.RelatedVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedVideoActionSheet(
    video: RelatedVideo,
    onWatchLater: () -> Unit,
    onReasonSelected: (RecommendationFeedbackReason) -> Unit,
    onDismissRequest: () -> Unit,
    source: AppGlassMenuSource? = null,
) {
    val model = remember(video.bvid, video.owner.mid, video.owner.name) {
        resolveRelatedVideoActionSheetModel(video)
    }
    val glass = appGlassMenuAvailable()
    val configuration = LocalConfiguration.current
    val glassMaxHeight = (configuration.screenHeightDp * 0.85f).dp
    val scrollState = rememberScrollState()
    val body: @Composable () -> Unit = {
        val dispatchAfterDismiss = LocalAppGlassPanelDismissAction.current
        fun dismissWithAction(action: (() -> Unit)? = null) {
            if (glass && dispatchAfterDismiss != null) {
                dispatchAfterDismiss { action?.invoke() }
            } else {
                action?.invoke()
                onDismissRequest()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (glass) Modifier.heightIn(max = glassMaxHeight).verticalScroll(scrollState) else Modifier)
                // 同窗玻璃宿主已避让系统栏；原生弹层保留原有 inset。
                .then(if (glass) Modifier else Modifier.navigationBarsPadding())
                .padding(bottom = 8.dp)
        ) {
            val sheetContext = androidx.compose.ui.platform.LocalContext.current
            SheetActionRow(
                text = "添加至稍后再看",
                onClick = { dismissWithAction(onWatchLater) }
            )
            if (video.bvid.isNotBlank()) {
                AppHorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                SheetActionRow(
                    text = "下一集播放",
                    onClick = {
                        dismissWithAction {
                            val added = com.android.purebilibili.feature.video.player.PlaylistManager.enqueuePlayNext(
                                com.android.purebilibili.feature.video.player.PlaylistItem(
                                    bvid = video.bvid,
                                    cid = video.cid,
                                    title = video.title,
                                    cover = video.pic,
                                    owner = video.owner.name,
                                    ownerFace = video.owner.face,
                                    duration = video.duration.toLong(),
                                )
                            )
                            android.widget.Toast.makeText(
                                sheetContext,
                                if (added) "已设为下一集播放" else "当前正在播放该视频",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                )
                AppHorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                SheetActionRow(
                    text = "加入待播队列",
                    onClick = {
                        dismissWithAction {
                            val added = com.android.purebilibili.feature.video.player.PlaylistManager.enqueueToEnd(
                                com.android.purebilibili.feature.video.player.PlaylistItem(
                                    bvid = video.bvid,
                                    cid = video.cid,
                                    title = video.title,
                                    cover = video.pic,
                                    owner = video.owner.name,
                                    ownerFace = video.owner.face,
                                    duration = video.duration.toLong(),
                                )
                            )
                            android.widget.Toast.makeText(
                                sheetContext,
                                if (added) "已加入待播队列" else "已在待播队列中",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                )
            }
            AppHorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            SheetSectionHeader(
                title = "反馈",
                subtitle = "（选择后将优化此类推荐）"
            )
            ReasonChipGrid(
                reasons = model.feedbackReasons,
                onReasonSelected = { reason -> dismissWithAction { onReasonSelected(reason) } }
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppHorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            SheetSectionHeader(
                title = "我不想看",
                subtitle = "（选择后将减少相似推荐）"
            )
            ReasonChipGrid(
                reasons = model.dislikeReasons,
                onReasonSelected = { reason -> dismissWithAction { onReasonSelected(reason) } }
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppHorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
            SheetActionRow(
                text = "取消",
                onClick = { dismissWithAction() },
                emphasize = false
            )
        }
    }
    // 复用同窗玻璃弹窗的背景采样、锚点形变与退场保留；原生组件参数不变。
    if (AppGlassMenu(
            expanded = true,
            onDismissRequest = onDismissRequest,
            title = "视频操作",
            placement = if (source?.isAvailable == true) AppGlassMenuPlacement.ANCHOR_END else AppGlassMenuPlacement.CENTER,
            source = source,
            minWidth = 0.dp,
            maxWidth = 420.dp,
            maxHeight = glassMaxHeight,
            scrollState = scrollState,
            interactiveContent = true,
            showScrollbar = false,
            useAvailableHeight = true,
            content = body,
        )) return
    AppModalBottomSheet(onDismissRequest = onDismissRequest) { body() }
}

@Composable
private fun SheetSectionHeader(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AppContentColors.primaryText()
        )
        AppText(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SheetActionRow(
    text: String,
    onClick: () -> Unit,
    emphasize: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AppText(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (emphasize) FontWeight.Medium else FontWeight.Normal
            ),
            color = AppContentColors.primaryText()
        )
    }
}

@Composable
private fun ReasonChipGrid(
    reasons: List<RecommendationFeedbackReason>,
    onReasonSelected: (RecommendationFeedbackReason) -> Unit
) {
    if (reasons.isEmpty()) return
    val rows = reasons.chunked(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { reason ->
                    ReasonChip(
                        text = reason.name,
                        modifier = Modifier.weight(1f),
                        onClick = { onReasonSelected(reason) }
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReasonChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = AppShapes.container(ContainerLevel.Field)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.primaryText(),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
