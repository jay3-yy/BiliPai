// File: feature/video/ui/components/DanmakuPoolSheet.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.appSearchElasticPress
import com.android.purebilibili.core.ui.components.AppTextButton

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.ProvideAppHapticScene
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AnimatedCountText
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.danmaku.engine.DanmakuItem

internal val DANMAKU_POOL_ROW_HORIZONTAL_PADDING = 12.dp
internal val DANMAKU_POOL_ROW_VERTICAL_PADDING = 8.dp
internal val DANMAKU_POOL_ROW_INNER_HEIGHT = 24.dp
internal val DANMAKU_POOL_ROW_MIN_HEIGHT =
    DANMAKU_POOL_ROW_INNER_HEIGHT + DANMAKU_POOL_ROW_VERTICAL_PADDING * 2
internal val DANMAKU_POOL_SEARCH_BORDER_WIDTH = 0.5.dp

private enum class DanmakuPoolSortMode(val label: String) {
    TIME("时间"),
    HOT("热度"),
}

/**
 * 弹幕池面板（底部抽屉）
 * 允许用户浏览当前视频加载的全部弹幕、实时关键词搜索、点击跳转时间轴、复制文本、点赞及撤回自己的弹幕。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanmakuPoolSheet(
    danmakuList: List<DanmakuItem>,
    currentPositionMs: Long = 0L,
    videoCid: Long = 0L,
    onSeekTo: (Long) -> Unit,
    likedDanmakuIds: Set<Long> = emptySet(),
    onLikeDanmaku: (dmid: Long, like: Boolean) -> Unit = { _, _ -> },
    onRecallDanmaku: (Long) -> Unit = {},
    onReportDanmaku: (dmid: Long, reason: Int) -> Unit = { _, _ -> },
    onBlockSender: (userHash: String) -> Unit = {},
    onSendSame: (String) -> Unit = {},
    isSending: Boolean = false,
    upOwnerUserHash: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshSnapshot: (() -> List<DanmakuItem>)? = null,
    onReportDanmakuWithDetails: ((dmid: Long, reason: Int, explanation: String, blockUser: Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(DanmakuPoolSortMode.TIME) }
    var selectedItemForAction by remember(videoCid) { mutableStateOf<DanmakuItem?>(null) }
    var showReportReasons by remember { mutableStateOf(false) }
    var pendingSendText by remember(videoCid) { mutableStateOf<String?>(null) }
    var snapshot by remember(videoCid) { mutableStateOf(danmakuList.toList()) }

    val filteredList by remember(snapshot, searchQuery, sortMode) {
        derivedStateOf {
            val query = searchQuery.trim()
            val base = if (query.isEmpty()) {
                snapshot
            } else {
                snapshot.filter { (it.text ?: "").contains(query, ignoreCase = true) }
            }
            when (sortMode) {
                DanmakuPoolSortMode.TIME -> base.sortedBy { it.showAtTime }
                DanmakuPoolSortMode.HOT -> base.sortedByDescending { it.likeCount }
            }
        }
    }

    val listState = rememberLazyListState()

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 16.dp),
        ) {
            // 顶栏：标题 + 弹幕总数 + 关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = "弹幕列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppContentColors.primaryText(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = "共 ${snapshot.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(onClick = { snapshot = (onRefreshSnapshot?.invoke() ?: danmakuList).toList() }) {
                        AppText("刷新")
                    }
                    // 排序切换按钮
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppSurfaceTokens.surfaceContainerHigh())
                            .padding(2.dp),
                    ) {
                        DanmakuPoolSortMode.entries.forEach { mode ->
                            val isSelected = mode == sortMode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) AppContentColors.accent()
                                        else Color.Transparent
                                    )
                                    .clickable { sortMode = mode }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AppContentColors.onAccent()
                                    else AppContentColors.secondaryText(),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AppIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 搜索框（紧凑单行，行高与下方弹幕行一致）
            DanmakuPoolSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 列表内容
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = if (searchQuery.isNotEmpty()) "未找到包含「$searchQuery」的弹幕" else "暂无弹幕数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.secondaryText(),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(filteredList, key = { it.danmakuId.takeIf { id -> id != 0L } ?: it.hashCode().toLong() }) { item ->
                        val isLiked = likedDanmakuIds.contains(item.danmakuId)
                        val isNearCurrentTime = kotlin.math.abs(item.showAtTime - currentPositionMs) < 3000L

                        DanmakuPoolItemRow(
                            item = item,
                            isLiked = isLiked,
                            isHighlighted = isNearCurrentTime,
                            isUpOwner = item.isUpOwner ||
                                (upOwnerUserHash != null && item.userHash == upOwnerUserHash),
                            onItemClick = {
                                onSeekTo(item.showAtTime)
                                Toast.makeText(
                                    context,
                                    "已跳转至 ${FormatUtils.formatDuration(item.showAtTime)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onLongClick = {
                                selectedItemForAction = item
                            },
                            onLikeClick = {
                                onLikeDanmaku(item.danmakuId, !isLiked)
                            },
                        )
                    }
                }
            }
        }
    }

    pendingSendText?.let { text ->
        DanmakuSameSendConfirmation(
            text = text,
            isSending = isSending,
            onDismiss = { pendingSendText = null },
            onConfirm = { pendingSendText = null; onSendSame(text) },
        )
    }

    // 弹幕长按操作菜单 Dialog
    selectedItemForAction?.let { item ->
        AppAlertDialog(
            onDismissRequest = {
                showReportReasons = false
                selectedItemForAction = null
            },
            title = {
                AppText(
                    text = if (showReportReasons) "举报原因" else "弹幕操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (showReportReasons) {
                    DanmakuReportForm(
                        canBlockUser = item.userHash.isNotBlank() && !item.isSelf,
                        onSubmit = { reason, explanation, block ->
                            if (onReportDanmakuWithDetails != null) {
                                onReportDanmakuWithDetails(item.danmakuId, reason, explanation, block)
                            } else {
                                onReportDanmaku(item.danmakuId, reason)
                                if (block) onBlockSender(item.userHash)
                            }
                            showReportReasons = false
                            selectedItemForAction = null
                        },
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppText(
                            text = item.text.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppContentColors.primaryText(),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // 跳转播放
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSeekTo(item.showAtTime)
                                    selectedItemForAction = null
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            AppText(
                                text = "跳转到该时间 (${FormatUtils.formatDuration(item.showAtTime)})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        if (!item.text.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !isSending) {
                                    pendingSendText = item.text
                                    selectedItemForAction = null
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PiliPlusDanmakuSendIcon(size = 20.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                AppText("发送同款", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // 复制文本
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("danmaku", item.text.orEmpty())
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "已复制弹幕内容", Toast.LENGTH_SHORT).show()
                                    selectedItemForAction = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            AppText(text = "复制弹幕内容", style = MaterialTheme.typography.bodyMedium)
                        }

                        // 撤回弹幕（仅自己发送的弹幕）
                        if (item.isSelf) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onRecallDanmaku(item.danmakuId)
                                        selectedItemForAction = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = AppContentColors.error(),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                AppText(
                                    text = "撤回该弹幕",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppContentColors.error(),
                                )
                            }
                        }

                        // 屏蔽发送者
                        if (item.userHash.isNotBlank() && !item.isSelf) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onBlockSender(item.userHash)
                                        selectedItemForAction = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(
                                    Icons.Filled.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                AppText(text = "屏蔽发送者", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // 举报弹幕
                        if (item.danmakuId > 0L) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showReportReasons = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(
                                    Icons.Filled.Report,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = AppContentColors.error(),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                AppText(
                                    text = "举报弹幕",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppContentColors.error(),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(onClick = {
                    if (showReportReasons) showReportReasons = false else selectedItemForAction = null
                }) {
                    AppText(if (showReportReasons) "返回" else "关闭")
                }
            },
        )
    }
}

@Composable
private fun DanmakuPoolSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderShape = AppShapes.borderedContainer(ContainerLevel.Card)
    val borderColor = if (isFocused) {
        AppContentColors.accent().copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val textColor = AppContentColors.primaryText()
    val secondaryColor = AppContentColors.secondaryText()

    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DANMAKU_POOL_ROW_MIN_HEIGHT)
            .appSearchElasticPress(highlightShape = borderShape),
        color = AppSurfaceTokens.searchContainer(),
        shape = borderShape,
        border = BorderStroke(DANMAKU_POOL_SEARCH_BORDER_WIDTH, borderColor),
    ) {
        ProvideAppHapticScene(AppHapticScene.TEXT) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(AppContentColors.accent()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = DANMAKU_POOL_ROW_MIN_HEIGHT)
                            .padding(
                                horizontal = DANMAKU_POOL_ROW_HORIZONTAL_PADDING,
                                vertical = DANMAKU_POOL_ROW_VERTICAL_PADDING,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            modifier = Modifier.size(16.dp),
                            tint = secondaryColor,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = DANMAKU_POOL_ROW_INNER_HEIGHT),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                AppText(
                                    text = "搜索当前已加载的弹幕...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                        if (query.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(DANMAKU_POOL_ROW_INNER_HEIGHT)
                                    .clip(CircleShape)
                                    .clickable { onQueryChange("") },
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(14.dp),
                                    tint = secondaryColor,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun DanmakuPoolItemRow(
    item: DanmakuItem,
    isLiked: Boolean,
    isHighlighted: Boolean,
    isUpOwner: Boolean,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        AppSurfaceTokens.surfaceContainer()
    }

    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .clickable(onClick = onItemClick),
        color = backgroundColor,
        shape = AppShapes.container(ContainerLevel.Card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DANMAKU_POOL_ROW_MIN_HEIGHT)
                .padding(
                    horizontal = DANMAKU_POOL_ROW_HORIZONTAL_PADDING,
                    vertical = DANMAKU_POOL_ROW_VERTICAL_PADDING,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 时间胶囊
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppSurfaceTokens.surfaceContainerHigh())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = FormatUtils.formatDuration(item.showAtTime),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.secondaryText(),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // UP 主弹幕：粉色 UP 标签（与播放器内徽章同色系）
            if (isUpOwner) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    AppText(
                        text = "UP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // 如果是自己的弹幕，显示“我的”标签
            if (item.isSelf) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(AppContentColors.accent().copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    AppText(
                        text = "我的",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.accent(),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // 颜色指示圆点（如果有自定义颜色）
            val customColor = item.textColor?.let { Color(it or -0x1000000) }
            if (customColor != null && customColor != Color.White) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(customColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // 弹幕文本
            AppText(
                text = item.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.primaryText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 点赞按钮
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onLikeClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "赞同",
                    modifier = Modifier.size(16.dp),
                    tint = if (isLiked) AppContentColors.accent() else AppContentColors.secondaryText(),
                )
                val totalLikes = item.likeCount.coerceAtLeast(0L) +
                    if (isLiked && item.likeCount < Long.MAX_VALUE) 1L else 0L
                Spacer(modifier = Modifier.width(4.dp))
                AnimatedCountText(
                    count = totalLikes,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLiked) AppContentColors.accent() else AppContentColors.secondaryText(),
                )
            }
        }
    }
}
