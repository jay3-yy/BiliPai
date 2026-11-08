package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.VideoRepository

enum class HomeVideoClickSource {
    GRID,
    TODAY_WATCH,
    PREVIEW
}

data class HomeVideoClickRequest(
    val bvid: String,
    val dynamicId: String = "",
    val cid: Long = 0L,
    val coverUrl: String = "",
    val isVerticalVideo: Boolean = false,
    val source: HomeVideoClickSource = HomeVideoClickSource.GRID,
    val sourceRoute: String? = null,
    val episodeId: Long = 0L,
    val title: String = "",
)

fun resolveHomeCategoryVideoSourceRoute(category: HomeCategory): String {
    return "home?category=${category.name}"
}

/**
 * 点击进详情时使用的横竖屏判定。
 *
 * 列表加载时 [VideoRepository] 已经批量预取过判定结果，这里优先读缓存；
 * 未命中（例如预取还在路上）时回退到列表接口自带字段，行为与改动前一致。
 */
fun VideoItem.resolveIsVerticalForNavigation(): Boolean =
    VideoRepository.getCachedVerticalFlag(bvid) ?: isVertical
