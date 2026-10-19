package com.android.purebilibili.feature.list

import androidx.compose.runtime.Composable
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.data.model.response.FavFolder

@Composable
internal fun FavoriteFolderOverviewMenu(
    busy: Boolean,
    canSelect: Boolean,
    onCreate: () -> Unit,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    AppWindowActionMenu(
        enabled = !busy,
        groups = listOf(listOf(
            AppWindowAction(label = "新建收藏夹", onClick = onCreate),
            AppWindowAction(label = "多选/批量管理", enabled = canSelect, onClick = onSelect),
        )),
        content = content,
    )
}

@Composable
internal fun FavoriteFolderBatchActions(
    folders: List<FavFolder>,
    selectedIds: Set<Long>,
    permissions: FavoriteFolderBatchPermissions,
    busy: Boolean,
    onSelectionChange: (Set<Long>) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onClean: () -> Unit,
) {
    val visibleIds = folders.mapTo(linkedSetOf(), ::resolveFavoriteFolderMediaId)
    val allSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
    AppTextButton(enabled = !busy && visibleIds.isNotEmpty(), onClick = {
        onSelectionChange(if (allSelected) selectedIds - visibleIds else selectedIds + visibleIds)
    }) { AppText(if (allSelected) "取消全选" else "全选") }
    // Management actions only appear after an explicit folder selection.
    if (selectedIds.isNotEmpty()) {
        AppTextButton(enabled = !busy && permissions.canDelete, onClick = onDelete) {
            AppText("删除收藏夹")
        }
        AppTextButton(enabled = !busy && permissions.canShare, onClick = onShare) {
            AppText("分享收藏夹")
        }
        AppTextButton(enabled = !busy && permissions.canClean, onClick = onClean) {
            AppText("清理失效内容")
        }
    }
}

internal fun favoriteDetailManagementActions(
    canCopy: Boolean,
    canManage: Boolean,
    canShare: Boolean,
    hasItems: Boolean,
    busy: Boolean,
    onShare: () -> Unit,
    onSelect: () -> Unit,
): List<AppWindowAction> = buildList {
    add(AppWindowAction(
        label = "分享至动态",
        enabled = canShare && !busy,
        onClick = onShare,
    ))
    if (canCopy) {
        add(AppWindowAction(
            label = if (canManage) "批量取消收藏/移除收藏" else "多选/批量复制",
            enabled = hasItems && !busy,
            onClick = onSelect,
        ))
    }
}
