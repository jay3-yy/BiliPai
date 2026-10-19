package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavFolderSource

internal enum class FavoriteResourceOrder(
    val apiValue: String,
    val label: String
) {
    FAVORITE_TIME("mtime", "收藏时间"),
    PLAY_COUNT("view", "播放量"),
    PUBLISH_TIME("pubtime", "投稿时间")
}


// attr 是属性位集合，仅最低位表示私密；其他位不影响公开状态。
internal fun isFavoriteFolderPrivate(attr: Int): Boolean = (attr and 1) != 0


internal fun canManageFavoriteFolder(folder: FavFolder, accountMid: Long?): Boolean {
    val ownerMid = folder.upper?.mid?.takeIf { it > 0L } ?: folder.mid
    return folder.source == FavFolderSource.OWNED && accountMid != null &&
        accountMid > 0L && ownerMid == accountMid && resolveFavoriteFolderMediaId(folder) > 0L
}

internal data class FavoriteFolderBatchPermissions(
    val canDelete: Boolean,
    val canShare: Boolean,
    val canClean: Boolean,
)

internal fun favoriteFolderBatchPermissions(
    selected: List<FavFolder>,
    defaultMediaId: Long?,
    accountMid: Long?,
): FavoriteFolderBatchPermissions {
    val owned = selected.isNotEmpty() && selected.all { canManageFavoriteFolder(it, accountMid) }
    return FavoriteFolderBatchPermissions(
        canDelete = owned && selected.none { resolveFavoriteFolderMediaId(it) == defaultMediaId },
        canShare = owned && selected.none { isFavoriteFolderPrivate(it.attr) },
        canClean = owned,
    )
}

internal fun favoriteFolderShareText(folders: List<FavFolder>): String =
    folders.joinToString("\n\n") {
        "${it.title}\nhttps://www.bilibili.com/medialist/detail/ml${resolveFavoriteFolderMediaId(it)}"
    }
