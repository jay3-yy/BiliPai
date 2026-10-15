package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.data.model.response.ViewInfo
import java.util.Locale
import java.util.TimeZone

private val CURRENT_AFFAIRS_PARTITION_KEYWORDS = listOf(
    "资讯",
    "热点",
    "环球",
    "社会",
    "新闻",
    "时政"
)

private val CURRENT_AFFAIRS_TITLE_KEYWORDS = listOf(
    "时政",
    "新闻",
    "快讯",
    "发布会",
    "记者会",
    "回应",
    "通报",
    "局势",
    "突发",
    "声明",
    "公告",
    "联合国",
    "白宫",
    "国务院",
    "外交部",
    "国防部",
    "两会",
    "俄乌",
    "巴以",
    "选举"
)

internal fun shouldShowAiSummaryEntry(
    aiSummary: AiSummaryData?,
    isAiSummaryEntryEnabled: Boolean
): Boolean {
    return isAiSummaryEntryEnabled && hasAiSummaryContent(aiSummary)
}

internal fun hasAiSummaryContent(aiSummary: AiSummaryData?): Boolean {
    val modelResult = aiSummary?.modelResult ?: return false
    if (aiSummary.code != 0) return false
    return modelResult.summary.isNotBlank() || modelResult.outline.isNotEmpty()
}

internal fun shouldShowVideoNoteEntry(
    isVideoLoaded: Boolean,
    aid: Long
): Boolean {
    return isVideoLoaded && aid > 0L
}

internal fun shouldShowInlineOwnerIdentity(showOwnerAvatar: Boolean): Boolean {
    return !showOwnerAvatar
}

internal const val UP_INFO_COMPACT_WIDTH_THRESHOLD_DP = 320

internal fun shouldUseCompactUpInfoLayout(widthDp: Int): Boolean {
    return widthDp in 1 until UP_INFO_COMPACT_WIDTH_THRESHOLD_DP
}

/** 详情页统计数量在前；未知值隐藏，真实的零值保留。 */
internal fun resolveVideoDetailOwnerStatsLabels(
    followerCount: Int?,
    videoCount: Int?
): List<String> {
    return listOfNotNull(
        followerCount?.takeIf { it >= 0 }?.let { "${FormatUtils.formatStat(it.toLong())}粉丝" },
        videoCount?.takeIf { it >= 0 }?.let { "${FormatUtils.formatStat(it.toLong())}视频" }
    )
}

internal fun resolveVideoDetailOnlineCountText(
    showOnlineCount: Boolean,
    onlineCount: String
): String {
    return if (showOnlineCount) onlineCount.trim() else ""
}

internal fun resolveVideoDetailBadges(info: ViewInfo): List<String> {
    val badges = mutableListOf<String>()
    if (info.isUpowerExclusive) {
        badges += if (info.isUpowerPreview) "充电专属 · 可试看" else "充电专属"
    }
    if (info.isCooperation) {
        badges += "联合投稿"
    }
    return badges
}

internal fun shouldShowCreatorTeamSection(info: ViewInfo): Boolean {
    return info.staff.isNotEmpty()
}

/** 恰饭徽标超过该字数时改为标题上方独立一行，避免挤压标题。 */
private const val SPONSOR_LABEL_INLINE_MAX_LENGTH = 10

internal fun shouldStackSponsorLabelAboveTitle(label: String): Boolean {
    return label.length > SPONSOR_LABEL_INLINE_MAX_LENGTH
}

internal fun shouldEmphasizePrecisePublishTime(
    partitionName: String,
    title: String
): Boolean {
    return CURRENT_AFFAIRS_PARTITION_KEYWORDS.any { keyword ->
        partitionName.contains(keyword, ignoreCase = true)
    } || CURRENT_AFFAIRS_TITLE_KEYWORDS.any { keyword ->
        title.contains(keyword, ignoreCase = true)
    }
}

internal fun resolvePublishTimeRowText(
    pubdate: Long,
    partitionName: String,
    title: String,
    nowMs: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (pubdate <= 0L) return ""

    val relativeText = FormatUtils.formatPublishTime(
        timestampSeconds = pubdate,
        nowMs = nowMs
    )
    if (relativeText.isBlank()) return ""

    return if (shouldEmphasizePrecisePublishTime(partitionName = partitionName, title = title)) {
        val preciseText = FormatUtils.formatPrecisePublishTime(
            timestampSeconds = pubdate,
            locale = locale,
            timeZone = timeZone
        )
        "发布时间 $relativeText  ·  $preciseText"
    } else {
        // PiliPlus 直接展示格式化时间，不加“发布于”前缀
        relativeText
    }
}

internal fun resolveCompactPublishTimeRowText(
    pubdate: Long,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (pubdate <= 0L) return ""
    val relativeText = FormatUtils.formatPublishTime(
        timestampSeconds = pubdate,
        nowMs = nowMs
    )
    return relativeText.takeIf { it.isNotBlank() }?.let { "发布于 $it" }.orEmpty()
}

internal fun resolveDynamicPublishTimeRowText(
    publishTs: Long,
    title: String,
    nowMs: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (publishTs <= 0L) return ""

    val relativeText = FormatUtils.formatPublishTime(
        timestampSeconds = publishTs,
        nowMs = nowMs
    )
    if (relativeText.isBlank()) return ""

    return if (shouldEmphasizePrecisePublishTime(partitionName = "", title = title)) {
        val preciseText = FormatUtils.formatPrecisePublishTime(
            timestampSeconds = publishTs,
            locale = locale,
            timeZone = timeZone
        )
        "动态发布 $relativeText  ·  $preciseText"
    } else {
        "动态发布 $relativeText"
    }
}

/**
 * 视频荣誉徽标文案(入站必刷/每周必看/全站排行榜/热门)。
 * 优先使用接口下发的 honor_name;缺省时按 type 拼 B 站官方文案。
 */
internal fun resolveVideoHonorChipText(
    type: Int,
    honorName: String,
    descContent: String?,
    weeklyRecommendNum: Int
): String? {
    honorName.takeIf { it.isNotBlank() }?.let { return it }
    return when (type) {
        1 -> "入站必刷收录"
        2 -> weeklyRecommendNum.takeIf { it > 0 }
            ?.let { "第$it 期每周必看" }
            ?: "每周必看"
        3 -> descContent?.toIntOrNull()
            ?.let { "全站排行榜最高第$it 名" }
            ?: "全站排行榜上榜作品"
        4 -> "热门"
        else -> null
    }
}

/**
 * 荣誉徽标跳转链接:一律走 bilibili://popular 内部 scheme,
 * 每周必看携带期号进入原生选期页,其余映射到首页热门区对应子分类。
 */
internal fun resolveVideoHonorJumpUrl(
    type: Int,
    honorUrl: String,
    weeklyRecommendNum: Int,
    honorText: String = ""
): String? {
    return when (type) {
        1 -> "bilibili://popular/all"
        2 -> {
            val number = weeklyRecommendNum.takeIf { it > 0 }
                ?: (com.android.purebilibili.core.util.BilibiliNavigationTargetParser.parse(honorUrl)
                    as? com.android.purebilibili.core.util.BilibiliNavigationTarget.PopularFeed)?.weeklyNumber
                ?: Regex("第\\s*(\\d+)\\s*期").find(honorText)
                    ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
            "bilibili://popular/weekly" + (number?.let { "?number=$it" } ?: "")
        }
        3 -> "bilibili://popular/rank"
        4 -> "bilibili://popular/comprehensive"
        else -> honorUrl.takeIf { it.isNotBlank() }
    }
}
