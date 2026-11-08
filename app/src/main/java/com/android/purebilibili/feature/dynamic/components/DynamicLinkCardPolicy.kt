package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.data.model.response.DynamicAdditional
import com.android.purebilibili.data.model.response.DynamicAdditionalReserve
import com.android.purebilibili.data.model.response.OpusLinkCard
import com.android.purebilibili.data.model.response.resolveDynamicCardButtonText
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * 动态卡片附加内容（相关游戏 / 投稿 / 商品 / 预约 / 投票 / 赛事 / 充电抽奖）的统一映射层。
 *
 * 背景：同一个附加内容在两个入口来自两份不同的接口数据——
 * - 信息流：`module_dynamic.additional`
 * - 详情页：opus 正文段落里的 `link_card`（见 DynamicModulesFlexibleSerializer.parse*LinkCard）
 *
 * 两边各自渲染过一套版式，导致同一个横幅在信息流是「角标 + 单行省略 + 右侧按钮」，
 * 点进详情后变成「标题 + 两行描述」，整块重排、观感上就是闪烁。
 *
 * 这里把信息流的 additional 映射成与详情完全相同的 [OpusLinkCard]，
 * 两个入口再共用 [DynamicLinkCard] 渲染，字段规则只保留一份。
 */
internal fun resolveDynamicLinkCard(additional: DynamicAdditional?): OpusLinkCard? {
    if (additional == null) return null
    //  各分支只做字段映射，不额外加"内容是否够用"的判定：详情侧的 parse*LinkCard
    //  也是靠末尾统一过滤，两边规则必须一致，否则同一个卡片的出现条件都不同。
    return when (additional.type) {
        "ADDITIONAL_TYPE_COMMON" -> additional.common
            ?.let { common ->
                OpusLinkCard(
                    type = LINK_CARD_TYPE_COMMON,
                    oid = common.id_str,
                    title = common.title,
                    description = listOf(common.desc, common.desc1, common.desc2)
                        .filter(String::isNotBlank)
                        .joinToString("\n"),
                    cover = normalizeLinkCardCover(common.cover),
                    jumpUrl = common.jump_url,
                )
            }
        "ADDITIONAL_TYPE_UGC" -> additional.ugc
            ?.let { ugc ->
                OpusLinkCard(
                    type = LINK_CARD_TYPE_UGC,
                    title = ugc.title,
                    description = ugc.desc_second,
                    cover = normalizeLinkCardCover(ugc.cover),
                    jumpUrl = ugc.jump_url,
                )
            }
        "ADDITIONAL_TYPE_GOODS" -> additional.goods
            ?.let { goods -> goods.items.firstOrNull()?.let { item -> goods to item } }
            ?.let { (goods, item) ->
                OpusLinkCard(
                    type = LINK_CARD_TYPE_GOODS,
                    title = item.name.ifBlank { goods.head_text },
                    description = item.price.ifBlank { item.brief },
                    label = goods.head_text,
                    cover = normalizeLinkCardCover(item.cover),
                    jumpUrl = item.jump_url.ifBlank { goods.jump_url },
                    badgeText = item.jump_desc,
                    goodsItem = item.copy(cover = normalizeLinkCardCover(item.cover)),
                )
            }
        "ADDITIONAL_TYPE_RESERVE" -> additional.reserve
            ?.let { reserve ->
                OpusLinkCard(
                    type = LINK_CARD_TYPE_RESERVE,
                    oid = reserve.rid.takeIf { it > 0L }?.toString().orEmpty(),
                    title = reserve.title.ifBlank { "预约" },
                    description = reserveDescription(reserve),
                    jumpUrl = reserve.jump_url.ifBlank { reserve.button?.jump_url.orEmpty() },
                    badgeText = resolveDynamicCardButtonText(reserve.button),
                    reserve = reserve,
                )
            }
        "ADDITIONAL_TYPE_VOTE" -> additional.vote
            ?.let { vote ->
                OpusLinkCard(
                    type = LINK_CARD_TYPE_VOTE,
                    oid = vote.vote_id.takeIf { it > 0L }?.toString().orEmpty(),
                    title = vote.title.ifBlank { vote.desc }.ifBlank { "投票" },
                    description = if (vote.join_num > 0) "${vote.join_num} 人参与" else "",
                )
            }
        "ADDITIONAL_TYPE_MATCH" -> additional.match
            ?.let { match ->
                OpusLinkCard(
                    type = LINK_CARD_TYPE_MATCH,
                    title = match.title.ifBlank { match.matchInfo?.title.orEmpty() },
                    description = match.sub_title.ifBlank { match.matchInfo?.sub_title.orEmpty() },
                    label = "赛事",
                    cover = normalizeLinkCardCover(match.cover),
                    jumpUrl = match.jump_url,
                    match = match,
                )
            }
        "ADDITIONAL_TYPE_UPOWER_LOTTERY" -> additional.upower_lottery
            ?.let { lottery ->
                OpusLinkCard(
                    type = LINK_CARD_TYPE_UPOWER_LOTTERY,
                    oid = lottery.rid.takeIf { it > 0L }?.toString().orEmpty(),
                    title = lottery.title.ifBlank { "充电专属抽奖" },
                    description = listOfNotNull(lottery.desc?.text, lottery.hint?.text)
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    label = "充电专属抽奖",
                    jumpUrl = lottery.jump_url.ifBlank { lottery.button?.jump_url.orEmpty() },
                    badgeText = resolveDynamicCardButtonText(lottery.button),
                )
            }
        else -> null
    }?.takeIf { it.title.isNotBlank() || it.cover.isNotBlank() || it.jumpUrl.isNotBlank() }
}

/**
 * 统一卡片类型。信息流的 additional 类型会被翻译成详情侧的 link_card 类型，
 * 这样两条路径产出的 [OpusLinkCard] 可以直接逐字段比较（测试里就是这么断言的）。
 */
internal const val LINK_CARD_TYPE_COMMON = "LINK_CARD_TYPE_COMMON"
internal const val LINK_CARD_TYPE_UGC = "LINK_CARD_TYPE_UGC"
internal const val LINK_CARD_TYPE_GOODS = "LINK_CARD_TYPE_GOODS"
internal const val LINK_CARD_TYPE_RESERVE = "LINK_CARD_TYPE_RESERVE"
internal const val LINK_CARD_TYPE_VOTE = "LINK_CARD_TYPE_VOTE"
internal const val LINK_CARD_TYPE_MATCH = "LINK_CARD_TYPE_MATCH"
internal const val LINK_CARD_TYPE_UPOWER_LOTTERY = "LINK_CARD_TYPE_UPOWER_LOTTERY"

/** 预约卡片的描述行，与详情侧 opus link_card 的拼接方式保持一致。 */
internal fun reserveDescription(reserve: DynamicAdditionalReserve): String =
    listOfNotNull(reserve.desc1?.text, reserve.desc2?.text, reserve.desc3?.text)
        .filter(String::isNotBlank)
        .joinToString(" · ")

/** 预约与取消共用 uncheck 的禁用标记；跳转按钮不受预约操作限制。 */
internal fun resolveReserveActionEnabled(card: OpusLinkCard): Boolean {
    val button = card.reserve?.button ?: return false
    return button.jump_url.isNotBlank() || button.uncheck?.disable != 1
}

internal data class DynamicReserveUi(
    val actionJumpUrl: String,
    val descriptionJumpUrl: String,
    val actionEnabled: Boolean,
    val action: DynamicReserveAction,
)

/**
 * 预约交互参数。信息流与详情共用同一份 [OpusLinkCard.reserve]，
 * 因此两侧的按钮文案、可点状态、跳转目标与提醒时间完全一致。
 */
internal fun resolveDynamicReserveUi(card: OpusLinkCard, dynamicId: String): DynamicReserveUi? {
    val reserve = card.reserve ?: return null
    if (reserve.rid <= 0L || reserve.state == -1) return null
    val button = reserve.button
    return DynamicReserveUi(
        actionJumpUrl = button?.jump_url.orEmpty(),
        descriptionJumpUrl = reserve.desc3?.jump_url.orEmpty(),
        actionEnabled = resolveReserveActionEnabled(card),
        action = DynamicReserveAction(
            dynamicId = dynamicId,
            reserveId = reserve.rid,
            currentButtonStatus = button?.status ?: 0,
            reserveTotal = reserve.reserve_total,
            buttonType = button?.type ?: 0,
            title = reserve.title,
            startAtMillis = resolveLiveReserveStartAtMillis(
                listOfNotNull(reserve.desc1?.text, reserve.desc2?.text, reserve.desc3?.text)
                    .joinToString(" "),
            ),
        ),
    )
}

internal data class DynamicLinkCardTeam(
    val name: String,
    val logoUrl: String,
    val score: String,
)

internal data class DynamicMatchUi(
    val centerLabel: String,
    val teams: List<DynamicLinkCardTeam>,
)

/** 赛事比分行数据。信息流与详情都从同一份 [OpusLinkCard.match] 取值。 */
internal fun resolveDynamicMatchUi(card: OpusLinkCard): DynamicMatchUi? {
    val match = card.match ?: return null
    val info = match.matchInfo
    val teams = listOfNotNull(info?.leftTeam, info?.rightTeam)
        .filter { it.name.isNotBlank() || it.pic.isNotBlank() }
        .map { team -> DynamicLinkCardTeam(name = team.name, logoUrl = team.pic, score = team.score) }
    val centerLabel = listOfNotNull(
        info?.centerTop?.filter(String::isNotBlank)?.joinToString(" "),
        info?.centerBottom.takeIf { bottom -> !bottom.isNullOrBlank() },
    ).filter(String::isNotBlank).joinToString(" · ")
    if (teams.size != 2 && centerLabel.isBlank()) return null
    return DynamicMatchUi(centerLabel = centerLabel, teams = teams)
}

/** 投票卡片用 oid 承载 vote_id，点整卡打开投票弹窗。 */
internal fun resolveDynamicLinkCardVoteId(card: OpusLinkCard): Long? =
    card.oid.toLongOrNull()?.takeIf { it > 0L && card.type == LINK_CARD_TYPE_VOTE }

/**
 * 卡片身份，用于把「预约成功后回写的状态」精确绑定到当前这张卡，
 * 同时作为信息流 additional 与详情 opus link_card 的去重依据。
 *
 * 只用两条路径必然逐字一致的字段（type / title / cover / description）：
 * oid 与 jumpUrl 在两侧取值规则不同（例如 COMMON 的 oid 信息流取 common.id_str、
 * 详情取 card.oid；VOTE 的 jumpUrl 信息流根本没有该字段），拿它们当身份会导致
 * 同一张卡在信息流与详情算出两个 key，去重失效、卡片重复渲染。
 */
internal fun OpusLinkCard.linkCardIdentity(): String =
    listOf(type, title, cover, description).joinToString("|") { it.trim() }

private fun normalizeLinkCardCover(rawUrl: String): String = when {
    rawUrl.startsWith("//") -> "https:$rawUrl"
    rawUrl.startsWith("http://") -> rawUrl.replaceFirst("http://", "https://")
    else -> rawUrl
}

data class DynamicReserveAction(
    val dynamicId: String,
    val reserveId: Long,
    val currentButtonStatus: Int,
    val reserveTotal: Long,
    val buttonType: Int = 0,
    val title: String = "",
    val startAtMillis: Long? = null,
)

data class DynamicReserveResult(
    val description: String,
    val reserveTotal: Long,
    val buttonStatus: Int,
)

private val liveReserveMonthDayTimePattern = Pattern.compile(
    "(?<!\\d)(\\d{1,2})[-/月](\\d{1,2})日?\\s+(\\d{1,2}):(\\d{2})(?!\\d)",
)

/** Parses the month/day and time shown in Bilibili's reserve-card description. */
internal fun resolveLiveReserveStartAtMillis(
    text: String,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long? {
    val matcher = liveReserveMonthDayTimePattern.matcher(text)
    if (!matcher.find()) return null
    val month = matcher.group(1)?.toIntOrNull() ?: return null
    val day = matcher.group(2)?.toIntOrNull() ?: return null
    val hour = matcher.group(3)?.toIntOrNull() ?: return null
    val minute = matcher.group(4)?.toIntOrNull() ?: return null
    val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zoneId)
    return runCatching {
        var candidate = LocalDateTime.of(now.year, month, day, hour, minute)
        if (!candidate.isAfter(now)) candidate = candidate.plusYears(1)
        candidate.atZone(zoneId).toInstant().toEpochMilli()
    }.getOrNull()
}

internal fun resolveLiveReserveNotificationTime(
    startAtMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    .format(java.time.Instant.ofEpochMilli(startAtMillis).atZone(zoneId))

/** 商品介绍与价格分行显示；其他卡片继续沿用原描述。 */
internal fun resolveDynamicLinkCardDescriptions(card: OpusLinkCard): List<String> {
    val goods = card.goodsItem
    return if (goods != null) {
        listOf(goods.brief, goods.price, if (card.jumpUrl.isBlank()) "暂无商品链接" else "")
            .filter(String::isNotBlank).distinct()
    } else listOf(card.description).filter(String::isNotBlank)
}
