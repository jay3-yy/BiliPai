package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.RelationStatData
import com.android.purebilibili.data.model.response.SpaceTagItem
import com.android.purebilibili.data.model.response.UpStatData

internal data class SpaceHeaderMetricItem(
    val label: String,
    val value: Long?
)

internal fun resolveSpaceHeaderMetricItems(
    relationStat: RelationStatData?,
    upStat: UpStatData?
): List<SpaceHeaderMetricItem> {
    return listOf(
        SpaceHeaderMetricItem("粉丝", relationStat?.follower?.toLong()),
        SpaceHeaderMetricItem("关注", relationStat?.following?.toLong()),
        SpaceHeaderMetricItem("获赞", upStat?.likes)
    )
}

/**
 * Resolves the action button label on the UP space header aligned with PiliPlus:
 * - isOwner: "编辑资料"
 * - relationStatus == 128: "移除黑名单"
 * - !isFollowed: "关注"
 * - isFollowed:
 *   - relationStatus == 1: "悄悄关注"
 *   - relationStatus in setOf(4, 6): "已互关"
 *   - relationStatus == -10: "特别关注"
 *   - otherwise: "已关注"
 */
internal fun resolveSpaceFollowActionLabel(
    isOwner: Boolean,
    relationStatus: Int = 0,
    isFollowed: Boolean = false,
): String {
    if (isOwner) return "编辑资料"
    if (relationStatus == 128) return "移除黑名单"
    if (!isFollowed) return "关注"
    return when (relationStatus) {
        1 -> "悄悄关注"
        4, 6 -> "已互关"
        -10 -> "特别关注"
        else -> "已关注"
    }
}

internal const val SPACE_PINNED_TOP_CHROME_FADE_RANGE_PX = 120

/** 0 at rest over the banner, 1 after the header has scrolled under the pinned chrome. */
internal fun resolveSpacePinnedTopChromeScrim(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    fadeRangePx: Int = SPACE_PINNED_TOP_CHROME_FADE_RANGE_PX,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (fadeRangePx <= 0) return 0f
    return (firstVisibleItemScrollOffset.toFloat() / fadeRangePx).coerceIn(0f, 1f)
}

internal fun resolveSpaceBannerAlignment(dy: Float): androidx.compose.ui.Alignment {
    return androidx.compose.ui.BiasAlignment(0f, dy.coerceIn(-1f, 1f))
}

internal fun resolveSpaceBannerColorFilter(
    isLight: Boolean,
    hasFilter: Boolean = true
): androidx.compose.ui.graphics.ColorFilter? {
    if (!hasFilter) return null
    return if (isLight) {
        androidx.compose.ui.graphics.ColorFilter.tint(
            androidx.compose.ui.graphics.Color(0x5DFFFFFF),
            androidx.compose.ui.graphics.BlendMode.Lighten
        )
    } else {
        androidx.compose.ui.graphics.ColorFilter.tint(
            androidx.compose.ui.graphics.Color(0x8D000000),
            androidx.compose.ui.graphics.BlendMode.Darken
        )
    }
}

/**
 * Resolves the IP location display text on the UP space header, aligned with PiliPlus / Bilibili:
 * - Strips redundant prefixes such as "IP属地：" or "IP 属地：".
 * - Returns clean "IP 属地 · $cleanLocation" or null if empty/blank.
 */
/**
 * Resolves the tags displayed beside the UID using the same contract as PiliPlus:
 * keep the server-provided `location` / `real_name` titles unchanged. The fallback is
 * only used when the Android space aggregate response did not include a location tag.
 */
internal fun resolveSpaceDisplayTags(
    spaceTags: List<SpaceTagItem>,
    ipLocation: String? = null,
    sex: String? = null,
    showSex: Boolean = false,
): List<SpaceTagItem> {
    val result = mutableListOf<SpaceTagItem>()
    val locationTag = spaceTags.firstOrNull { it.type == "location" }
    val otherTags = spaceTags.filter {
        it !== locationTag && it.type == "real_name"
    }

    val resolvedIp = locationTag?.title?.takeIf { it.isNotBlank() }
        ?: ipLocation?.takeIf { it.isNotBlank() }

    if (!resolvedIp.isNullOrBlank()) {
        result.add(
            locationTag
                ?: SpaceTagItem(
                    title = if (resolvedIp.startsWith("IP属地")) resolvedIp else "IP属地：$resolvedIp",
                    type = "location"
                )
        )
    }
    if (showSex) {
        com.android.purebilibili.core.util.resolveUserSexLabel(sex)?.let { sexLabel ->
            result.add(SpaceTagItem(title = sexLabel, type = "sex"))
        }
    }
    result.addAll(otherTags)
    return result
}
