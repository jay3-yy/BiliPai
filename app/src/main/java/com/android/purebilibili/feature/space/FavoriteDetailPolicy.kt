package com.android.purebilibili.feature.space

internal enum class FavoriteDetailSortOrder(val label: String, val serverOrder: String = "mtime") {
    NEWEST_FIRST("最近收藏"),
    OLDEST_FIRST("最早收藏"),
    MOST_PLAYED("最多播放", "view"),
    LATEST_PUBLISHED("最近投稿", "pubtime"),
}

internal fun favoriteLastServerPage(resourceCount: Int): Int =
    ((resourceCount.coerceAtLeast(0).toLong() + 19L) / 20L).coerceAtLeast(1L).toInt()
