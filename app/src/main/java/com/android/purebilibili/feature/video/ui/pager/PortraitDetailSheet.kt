package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.core.ui.AppSurfaceTokens

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.core.util.FormatUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.rememberAppBottomSheetMotion
import com.android.purebilibili.feature.video.ui.section.resolvePublishTimeRowText
import com.android.purebilibili.feature.video.ui.section.shouldEmphasizePrecisePublishTime
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * 竖屏视频详情页 (简介)
 * 使用自定义 Box 叠加实现，避免 ModalBottomSheet 的 WindowInsets 问题
 */
@Composable
fun PortraitDetailSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    info: ViewInfo?,
    modifier: Modifier = Modifier,
    currentCid: Long = 0L,
    recommendationTitle: String = "推荐视频",
    recommendations: List<RelatedVideo> = emptyList(),
    onRecommendationClick: (String) -> Unit = {},
    /** Select multi-P by cid (same bvid) or season episode by bvid+cid. */
    onCollectionItemClick: (bvid: String, cid: Long) -> Unit = { _, _ -> },
    onAuthorClick: (Long) -> Unit = {},
    danmakuEnabled: Boolean = true,
    onDanmakuToggle: () -> Unit = {},
    onActiveChange: (Boolean) -> Unit = {},
) {
    if (!visible && info == null) return

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetMotion = rememberAppBottomSheetMotion()
    val contentVisibility = remember { MutableTransitionState(false) }
    val scrimVisibility = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) {
        contentVisibility.targetState = visible
        scrimVisibility.targetState = visible
    }
    // 退出动画尚未结束时仍由半屏接管触摸，避免视频列表提前恢复翻页。
    val active = visible || contentVisibility.currentState || scrimVisibility.currentState ||
        !contentVisibility.isIdle || !scrimVisibility.isIdle
    val latestOnActiveChange by rememberUpdatedState(onActiveChange)
    LaunchedEffect(active) { latestOnActiveChange(active) }
    DisposableEffect(Unit) { onDispose { latestOnActiveChange(false) } }

    // 拦截返回键
    com.android.purebilibili.core.ui.LocalNavigationBackHandler(enabled = visible) {
        onDismiss()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. 遮罩层 (Scrim)
        AnimatedVisibility(
            visibleState = scrimVisibility,
            enter = sheetMotion.scrimEnter,
            exit = sheetMotion.scrimExit
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        // 2. 内容层 (Sheet Content)
        AnimatedVisibility(
            visibleState = contentVisibility,
            enter = sheetMotion.contentEnter,
            exit = sheetMotion.contentExit
        ) {
            AppSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.75f) // max height 75%
                    .clickable(enabled = false) {}, // 拦截点击防止穿透
                shape = AppShapes.container(ContainerLevel.Sheet),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            text = "简介",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = AppContentColors.primaryText()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppTextButton(onClick = onDanmakuToggle) {
                                AppText(
                                    text = if (danmakuEnabled) "弹幕开" else "弹幕关",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            AppIconButton(onClick = onDismiss) {
                                AppIcon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = AppContentColors.secondaryText()
                                )
                            }
                        }
                    }

                    AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Content
                    if (info == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AdaptiveLoadingIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // 标题
                            val context = LocalContext.current
                            val blockedUpRepository = remember { com.android.purebilibili.data.repository.BlockedUpRepository(context) }
                            val isBlocked by blockedUpRepository.isBlocked(info.owner.mid).collectAsStateWithLifecycle(initialValue = false
        )
                            val scope = rememberCoroutineScope()
                            var showBlockConfirmDialog by remember { mutableStateOf(false) }

                            if (showBlockConfirmDialog) {
                                com.android.purebilibili.core.ui.AppAlertDialog(
                                    onDismissRequest = { showBlockConfirmDialog = false },
                                    title = { AppText(if (isBlocked) "解除屏蔽" else "屏蔽 UP 主") },
                                    text = { AppText(if (isBlocked) "确定要解除对 ${info.owner.name} 的屏蔽吗？" else "屏蔽后，将不再推荐该 UP 主的视频。\n确定要屏蔽 ${info.owner.name} 吗？") },
                                    confirmButton = {
                                        com.android.purebilibili.core.ui.AppDialogAction(
                                            onClick = {
                                                scope.launch {
                                                    if (isBlocked) {
                                                        val result = blockedUpRepository.unblockUpWithBilibiliSync(info.owner.mid)
                                                        android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val result = blockedUpRepository.blockUpWithBilibiliSync(info.owner.mid, info.owner.name, info.owner.face)
                                                        android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    showBlockConfirmDialog = false
                                                }
                                            }
                                        ) {
                                            AppText(
                                                text = if (isBlocked) "解除屏蔽" else "屏蔽",
                                                color = if (!isBlocked) AppContentColors.error() else AppContentColors.neutralAction()
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        com.android.purebilibili.core.ui.AppDialogAction(onClick = { showBlockConfirmDialog = false }) { AppText("取消") }
                                    }
                                )
                            }

                            AppText(
                                text = info.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = AppContentColors.primaryText(),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            val publishTimeRowText = remember(info.pubdate, info.tname, info.title) {
                                resolvePublishTimeRowText(
                                    pubdate = info.pubdate,
                                    partitionName = info.tname,
                                    title = info.title
                                )
                            }
                            val emphasizePublishTime = remember(info.tname, info.title) {
                                shouldEmphasizePrecisePublishTime(
                                    partitionName = info.tname,
                                    title = info.title
                                )
                            }

                            if (publishTimeRowText.isNotBlank()) {
                                if (emphasizePublishTime) {
                                    AppSurface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        shape = AppShapes.container(ContainerLevel.Field),
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    ) {
                                        AppText(
                                            text = publishTimeRowText,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                            color = AppContentColors.secondaryText(),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                } else {
                                    AppText(
                                        text = publishTimeRowText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppContentColors.secondaryText(),
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                }
                            }

                            // 基础信息 (UP主 / 时间 / 播放量)
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.android.purebilibili.feature.video.ui.section.OwnerDecoratedAvatar(
                                    faceUrl = info.owner.face,
                                    ownerMid = info.owner.mid,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable {
                                            if (info.owner.mid > 0L) {
                                                onAuthorClick(info.owner.mid)
                                            }
                                        },
                                    badgeSize = 12.dp,
                                    contentDescription = "${info.owner.name} 头像",
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    AppText(
                                        text = info.owner.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                        color = if (isBlocked) AppContentColors.neutralAction() else AppContentColors.error(),
                                        modifier = Modifier.clickable { showBlockConfirmDialog = true }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    AppText(
                                        text = "${FormatUtils.formatStat(info.stat.view.toLong())}观看 · ${FormatUtils.formatStat(info.stat.danmaku.toLong())}弹幕",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppContentColors.secondaryText()
                                    )
                                }
                            }

                            // VID Info
                            AppText(
                                text = info.bvid,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // 简介正文
                            AppText(
                                text = info.desc.ifEmpty { "暂无简介" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            val activeCid = currentCid.takeIf { it > 0L } ?: info.cid
                            val multiPages = info.pages.filter { it.cid > 0L }
                            if (multiPages.size > 1) {
                                PortraitCollectionSection(
                                    title = "分P（${multiPages.size}）",
                                    items = multiPages.map { page ->
                                        PortraitCollectionChip(
                                            key = "p-${page.cid}",
                                            label = page.part.ifBlank { "P${page.page.coerceAtLeast(1)}" },
                                            selected = page.cid == activeCid,
                                            onClick = {
                                                onCollectionItemClick(info.bvid, page.cid)
                                                onDismiss()
                                            }
                                        )
                                    }
                                )
                            }

                            val season = info.ugc_season
                            val seasonEpisodes = season?.sections
                                ?.flatMap { it.episodes }
                                ?.filter { ep -> ep.cid > 0L || ep.bvid.isNotBlank() }
                                .orEmpty()
                            if (season != null && seasonEpisodes.size > 1) {
                                PortraitCollectionSection(
                                    title = "合集 · ${season.title.ifBlank { "选集" }}（${seasonEpisodes.size}）",
                                    items = seasonEpisodes.mapIndexed { index, episode ->
                                        val epBvid = episode.bvid.trim().ifBlank {
                                            if (episode.aid > 0L) "av${episode.aid}" else info.bvid
                                        }
                                        val epCid = episode.cid
                                        val selected = when {
                                            epCid > 0L && activeCid > 0L -> epCid == activeCid
                                            epBvid.isNotEmpty() -> epBvid == info.bvid.trim()
                                            else -> false
                                        }
                                        PortraitCollectionChip(
                                            key = "ep-${episode.id.coerceAtLeast(0L)}-$epCid-$epBvid",
                                            label = episode.title.ifBlank {
                                                episode.arc?.title?.takeIf { it.isNotBlank() }
                                                    ?: "第${index + 1}集"
                                            },
                                            selected = selected,
                                            onClick = {
                                                onCollectionItemClick(epBvid, epCid)
                                                onDismiss()
                                            }
                                        )
                                    }
                                )
                            }

                            if (recommendations.isNotEmpty()) {
                                AppText(
                                    text = recommendationTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = AppContentColors.primaryText(),
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                recommendations.take(12).forEach { video ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(AppShapes.container(ContainerLevel.Field))
                                            .clickable { onRecommendationClick(video.bvid) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = FormatUtils.fixImageUrl(video.pic),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(width = 96.dp, height = 54.dp)
                                                .clip(AppShapes.container(ContainerLevel.Chip))
                                                .background(AppSurfaceTokens.searchContainer()),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            AppText(
                                                text = video.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                color = AppContentColors.primaryText()
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            AppText(
                                                text = "${video.owner.name} · ${FormatUtils.formatStat(video.stat.view.toLong())}播放",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppContentColors.secondaryText()
                                            )
                                        }
                                    }
                                }
                            }

                            // 标签 (Flow Layout usually, simplified here for now)
                            // TODO: If tags available in ViewInfo, display them.
                            // Currently ViewInfo usually has minimal info, might need separate tags fetch or check ViewInfo structure.
                        }
                    }
                }
            }
        }
    }
}

private data class PortraitCollectionChip(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun PortraitCollectionSection(
    title: String,
    items: List<PortraitCollectionChip>
) {
    if (items.isEmpty()) return
    AppText(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = AppContentColors.primaryText(),
        modifier = Modifier.padding(bottom = 10.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            key(item.key) {
                AppSurface(
                    onClick = item.onClick,
                    shape = AppShapes.container(ContainerLevel.Field),
                    color = if (item.selected) {
                        AppContentColors.accent().copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    border = if (item.selected) {
                        BorderStroke(
                            1.dp,
                            AppContentColors.accent().copy(alpha = 0.55f)
                        )
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppText(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (item.selected) {
                            AppContentColors.accent()
                        } else {
                            AppContentColors.primaryText()
                        },
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
