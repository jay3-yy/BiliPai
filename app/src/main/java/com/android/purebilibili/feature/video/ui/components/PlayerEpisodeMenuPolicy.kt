package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.UgcSeason

internal sealed interface PlayerEpisodeMenuTarget {
    data class Part(val pageIndex: Int) : PlayerEpisodeMenuTarget
    data class Episode(val episode: UgcEpisode) : PlayerEpisodeMenuTarget
}

internal data class PlayerEpisodeMenuEntry(
    val indexLabel: String,
    val title: String,
    val durationSeconds: Long,
    val target: PlayerEpisodeMenuTarget,
    val cover: String = "",
    /** 合集剧集的发布时间（秒）；分P没有，0 表示未知。 */
    val pubdateSeconds: Long = 0,
    /** 播放/弹幕计数；null 表示未知（分P），0 是真实的零。 */
    val viewCount: Long? = null,
    val danmakuCount: Long? = null,
)

internal data class PlayerEpisodeMenuSection(
    val title: String,
    val entries: List<PlayerEpisodeMenuEntry>,
)

internal data class PlayerEpisodeMenuContent(
    val sections: List<PlayerEpisodeMenuSection>,
    val currentSectionIndex: Int = -1,
    val currentEntryIndex: Int = -1,
)

/** Shared projection for detail, audio-mode and player episode menus. */
internal fun resolvePlayerEpisodeMenuContent(
    pages: List<Page>,
    currentPageIndex: Int,
    ugcSeason: UgcSeason?,
    currentBvid: String,
    currentCid: Long,
): PlayerEpisodeMenuContent {
    if (pages.size > 1) {
        return PlayerEpisodeMenuContent(
            sections = listOf(PlayerEpisodeMenuSection("选集", pages.mapIndexed { index, page ->
                PlayerEpisodeMenuEntry(
                    indexLabel = "P${page.page.takeIf { it > 0 } ?: (index + 1)}",
                    title = page.part.ifBlank { "第${index + 1}P" },
                    durationSeconds = page.duration,
                    target = PlayerEpisodeMenuTarget.Part(index),
                    cover = page.firstFrame,
                )
            })),
            currentSectionIndex = if (currentPageIndex in pages.indices) 0 else -1,
            currentEntryIndex = currentPageIndex.takeIf { it in pages.indices } ?: -1,
        )
    }

    val sections = ugcSeason?.sections.orEmpty().mapIndexedNotNull { sectionIndex, section ->
        if (section.episodes.isEmpty()) return@mapIndexedNotNull null
        PlayerEpisodeMenuSection(
            title = section.title.ifBlank { "分区 ${sectionIndex + 1}" },
            entries = buildList {
                section.episodes.forEachIndexed { episodeIndex, episode ->
                    val title = episode.title.ifBlank { episode.arc?.title.orEmpty() }
                        .ifBlank { "第${episodeIndex + 1}集" }
                    if (episode.pages.isEmpty()) {
                        add(PlayerEpisodeMenuEntry(
                            indexLabel = "${episodeIndex + 1}",
                            title = title,
                            durationSeconds = episode.arc?.duration?.toLong() ?: 0L,
                            cover = episode.arc?.pic.orEmpty().ifBlank { ugcSeason?.cover.orEmpty() },
                            pubdateSeconds = episode.arc?.pubdate ?: 0L,
                            viewCount = episode.arc?.stat?.view?.toLong(),
                            danmakuCount = episode.arc?.stat?.danmaku?.toLong(),
                            target = PlayerEpisodeMenuTarget.Episode(episode),
                        ))
                    } else {
                        episode.pages.forEachIndexed { partIndex, page ->
                            val partNumber = page.page.takeIf { it > 0 } ?: (partIndex + 1)
                            add(PlayerEpisodeMenuEntry(
                                indexLabel = if (episode.pages.size > 1) {
                                    "${episodeIndex + 1} · P$partNumber"
                                } else {
                                    "${episodeIndex + 1}"
                                },
                                title = if (episode.pages.size > 1) {
                                    "$title · ${page.part.ifBlank { "第${partNumber}P" }}"
                                } else {
                                    title
                                },
                                durationSeconds = page.duration,
                                cover = page.firstFrame.ifBlank { episode.arc?.pic.orEmpty().ifBlank { ugcSeason?.cover.orEmpty() } },
                                // 嵌套分P沿用所属剧集的发布时间与统计（与 PiliPlus 剧集面板一致）。
                                pubdateSeconds = episode.arc?.pubdate ?: 0L,
                                viewCount = episode.arc?.stat?.view?.toLong(),
                                danmakuCount = episode.arc?.stat?.danmaku?.toLong(),
                                target = PlayerEpisodeMenuTarget.Episode(
                                    if (page.cid > 0L && page.cid != episode.cid) {
                                        episode.copy(cid = page.cid)
                                    } else {
                                        episode
                                    },
                                ),
                            ))
                        }
                    }
                }
            },
        )
    }
    // Find an exact part across every section before falling back to the video's first row.
    // This avoids selecting P1 (or a duplicate bvid in an earlier section) while P2 is playing.
    fun findCurrent(exactPart: Boolean): PlayerEpisodeMenuContent? {
        if (currentBvid.isBlank()) return null
        sections.forEachIndexed { sectionIndex, section ->
            val entryIndex = section.entries.indexOfFirst { entry ->
                val episode = (entry.target as PlayerEpisodeMenuTarget.Episode).episode
                episode.bvid == currentBvid && (!exactPart || episode.cid == currentCid)
            }
            if (entryIndex >= 0) return PlayerEpisodeMenuContent(sections, sectionIndex, entryIndex)
        }
        return null
    }
    return (if (currentCid > 0L) findCurrent(exactPart = true) else null)
        ?: findCurrent(exactPart = false)
        ?: PlayerEpisodeMenuContent(sections)
}

/** Keep a season video's parts in its nested 45dp part strip rather than duplicating covers. */
internal fun groupPlayerEpisodeEntries(entries: List<PlayerEpisodeMenuEntry>): List<List<PlayerEpisodeMenuEntry>> {
    val groups = mutableListOf<MutableList<PlayerEpisodeMenuEntry>>()
    entries.forEach { entry ->
        val episode = (entry.target as? PlayerEpisodeMenuTarget.Episode)?.episode
        val previous = groups.lastOrNull()?.lastOrNull()?.target as? PlayerEpisodeMenuTarget.Episode
        if (episode != null && previous != null && episode.copy(cid = 0) == previous.episode.copy(cid = 0)) {
            groups.last().add(entry)
        } else {
            groups.add(mutableListOf(entry))
        }
    }
    return groups
}

private val EPISODE_PUBDATE_DATE_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 剧集行元数据：发布日期 · 播放 · 弹幕；无任何已知信息时返回 null。 */
internal fun buildPlayerEpisodeMetadataText(
    pubdateSeconds: Long,
    viewCount: Long?,
    danmakuCount: Long?,
): String? {
    val parts = buildList {
        if (pubdateSeconds > 0L) {
            add(java.time.Instant.ofEpochSecond(pubdateSeconds)
                .atZone(java.time.ZoneId.systemDefault())
                .format(EPISODE_PUBDATE_DATE_FORMATTER))
        }
        if (viewCount != null && viewCount >= 0L) {
            add(com.android.purebilibili.core.util.FormatUtils.formatStat(viewCount) + "播放")
        }
        if (danmakuCount != null && danmakuCount >= 0L) {
            add(com.android.purebilibili.core.util.FormatUtils.formatStat(danmakuCount) + "弹幕")
        }
    }
    return parts.joinToString(" · ").ifBlank { null }
}

/** 弹窗按实际可用窗口与测量尺寸定位，不能假定它与播放器宿主窗口一样大。 */
internal fun resolvePlayerPanelPopupPosition(
    bounds: androidx.compose.ui.unit.IntRect,
    windowSize: androidx.compose.ui.unit.IntSize,
    popupContentSize: androidx.compose.ui.unit.IntSize,
    bottomAligned: Boolean = false,
): androidx.compose.ui.unit.IntOffset = androidx.compose.ui.unit.IntOffset(
    x = (bounds.right - popupContentSize.width).coerceIn(
        0, (windowSize.width - popupContentSize.width).coerceAtLeast(0),
    ),
    // 竖屏按实际测量高度贴底；横屏侧栏保留顶部锚点。
    y = (if (bottomAligned) bounds.bottom - popupContentSize.height else bounds.top)
        .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
)

/** Window coordinates. Inline panels occupy detail space, never the playing video. */
internal fun resolvePlayerPanelBounds(
    safe: androidx.compose.ui.unit.IntRect,
    player: androidx.compose.ui.unit.IntRect?,
    isFullscreen: Boolean,
    landscape: Boolean,
    maxSideWidth: Int,
    maxBottomHeight: Int? = null,
): androidx.compose.ui.unit.IntRect {
    if (!isFullscreen && player != null) {
        if (landscape && player.right < safe.right) {
            val left = player.right.coerceIn(safe.left, safe.right)
            if (left < safe.right) return androidx.compose.ui.unit.IntRect(left, safe.top, safe.right, safe.bottom)
        }
        if (!landscape && player.bottom < safe.bottom) {
            val heightLimit = maxBottomHeight?.coerceIn(0, safe.height) ?: safe.height
            val top = maxOf(player.bottom, safe.bottom - heightLimit).coerceIn(safe.top, safe.bottom)
            if (top < safe.bottom) return androidx.compose.ui.unit.IntRect(safe.left, top, safe.right, safe.bottom)
        }
    }
    return if (landscape) {
        val width = minOf(safe.width / 2, maxSideWidth.coerceAtLeast(0))
        androidx.compose.ui.unit.IntRect(safe.right - width, safe.top, safe.right, safe.bottom)
    } else {
        val height = minOf((safe.height * 0.7f).toInt(), maxBottomHeight?.coerceIn(0, safe.height) ?: safe.height)
        androidx.compose.ui.unit.IntRect(safe.left, safe.bottom - height, safe.right, safe.bottom)
    }
}
