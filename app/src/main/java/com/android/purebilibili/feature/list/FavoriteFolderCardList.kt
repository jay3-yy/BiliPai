package com.android.purebilibili.feature.list

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.rememberAppBookmarkIcon
import com.android.purebilibili.core.ui.rememberAppChevronForwardIcon
import com.android.purebilibili.core.ui.rememberAppFolderIcon
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.FavFolder

/**
 * PiliPlus 式收藏夹卡片列表：视频 Tab 下收藏夹以横卡形式罗列，
 * 每张卡展示封面（16:10）、标题、简介、内容数与公开/私密信息，
 * 点击进入收藏夹详情；列表末尾保留订阅收藏夹入口。
 *
 * 收藏夹列表接口对部分收藏夹不返回封面，需要逐个额外请求补齐。这里随滚动到可见前沿
 * 分批回调 [onCoverPrefetch]，交由上层按需补全，避免一次性请求全部收藏夹。
 */
@Composable
internal fun FavoriteFolderCardList(
    folders: List<FavFolder>,
    subscribedFoldersCount: Int,
    searchQuery: String,
    padding: PaddingValues,
    transitionEnabled: Boolean,
    onFolderClick: (FavFolder) -> Unit,
    onSubscribedClick: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onCoverPrefetch: (List<FavFolder>) -> Unit = {},
    selectionMode: Boolean = false,
    selectedMediaIds: Set<Long> = emptySet(),
    selectionEnabled: Boolean = true,
    onToggleSelection: (FavFolder) -> Unit = {},
    onStartSelection: (FavFolder) -> Unit = {},
) {
    // 只在列表真正可见时跟踪滚动前沿，避免未挂载时反复回调。
    val coverPrefetchFrontier by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
    }
    val latestFolders by rememberUpdatedState(folders)
    LaunchedEffect(coverPrefetchFrontier, folders.size) {
        val current = latestFolders
        if (current.isEmpty()) return@LaunchedEffect
        val end = resolveFavoriteFolderCoverPrefetchEnd(
            folderCount = current.size,
            lastVisibleIndex = coverPrefetchFrontier,
        )
        if (end < 0) return@LaunchedEffect
        onCoverPrefetch(current.take(end + 1))
    }

    if (folders.isEmpty() && subscribedFoldersCount == 0) {
        val message = if (searchQuery.isNotBlank()) "没有找到相关收藏夹" else "暂无收藏夹"
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentAlignment = Alignment.Center,
        ) {
            AppText(text = message, color = AppContentColors.secondaryText())
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .responsiveContentWidth(resolveCommonListSingleColumnMaxWidth())
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacingTokens.Medium,
            end = AppSpacingTokens.Medium,
            top = padding.calculateTopPadding() + AppSpacingTokens.Medium,
            bottom = padding.calculateBottomPadding() + AppSpacingTokens.ExtraLarge,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
    ) {
        items(items = folders, key = { "favorite_folder_${it.id}_${it.fid}" }) { folder ->
            FavoriteFolderCard(
                folder = folder,
                transitionEnabled = transitionEnabled,
                onClick = {
                    if (selectionMode) {
                        if (selectionEnabled) onToggleSelection(folder)
                    } else onFolderClick(folder)
                },
                selectionMode = selectionMode,
                selected = resolveFavoriteFolderMediaId(folder) in selectedMediaIds,
                onLongClick = { if (selectionEnabled) onStartSelection(folder) },
            )
        }
        if (!selectionMode && subscribedFoldersCount > 0 && searchQuery.isBlank()) {
            item(key = "favorite_folder_subscribed_entry") {
                FavoriteSubscribedEntryRow(onClick = onSubscribedClick)
            }
        }
    }
}

/** 单张收藏夹卡片，布局对齐 PiliPlus fav/video/item.dart。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteFolderCard(
    folder: FavFolder,
    transitionEnabled: Boolean,
    onClick: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
) {
    val coverUrl = remember(folder.cover) {
        resolveFavoriteFolderPreviewCover(folder, emptyList())
    }
    val privacyLabel = if (isFavoriteFolderPrivate(folder.attr)) "私密" else "公开"
    val metaLine = buildString {
        append(folder.media_count)
        append("个内容 · ")
        append(privacyLabel)
    }

    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .semantics { if (selectionMode) this.selected = selected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = AppSpacingTokens.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small + AppSpacingTokens.Micro),
    ) {
        if (selectionMode) {
            AppIcon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (selected) "已选择" else "未选择",
                tint = if (selected) AppContentColors.accent() else AppContentColors.secondaryText(),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Box(
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(16f / 10f)
                .clip(AppShapes.mediaCover())
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = FormatUtils.fixImageUrl(coverUrl),
                    contentDescription = folder.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AppIcon(
                    imageVector = rememberAppFolderIcon(),
                    contentDescription = null,
                    tint = AppContentColors.accent(),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = AppSpacingTokens.ExtraSmall),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
        ) {
            AppText(
                text = folder.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (folder.intro.isNotBlank()) {
                AppText(
                    text = folder.intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            AppText(
                text = metaLine,
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
            )
        }
    }
}

/** 订阅收藏夹入口行，保留 BiliPai 追更能力。 */
@Composable
private fun FavoriteSubscribedEntryRow(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)
            .clip(AppShapes.container(ContainerLevel.Card))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
    ) {
        AppIcon(
            imageVector = rememberAppBookmarkIcon(),
            contentDescription = null,
            tint = AppContentColors.accent(),
        )
        AppText(
            text = "订阅收藏夹（追更）",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        AppIcon(
            imageVector = rememberAppChevronForwardIcon(),
            contentDescription = null,
            tint = AppContentColors.secondaryText(),
        )
    }
}
