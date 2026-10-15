// 文件路径: data/model/response/DynamicModels.kt
package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 *  动态页面数据模型
 * API: x/polymer/web-dynamic/v1/feed/all
 */

// --- 顶层响应 ---
@Serializable
data class DynamicFeedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicFeedData? = null
)

@Serializable
data class DynamicFeedData(
    val items: List<DynamicItem> = emptyList(),
    val offset: String = "", // 分页偏移量
    val has_more: Boolean = false,
    val update_baseline: String = "",
    val update_num: Int = 0
)

/**
 * 动态未读数（红点）轻量接口
 * API: x/polymer/web-dynamic/v1/feed/all/update
 * 只返回本次更新基线以上的新动态条数，避免轮询时拉全量 feed。
 */
@Serializable
data class DynamicUpdateCountResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicUpdateCountData? = null
)

@Serializable
data class DynamicUpdateCountData(
    val update_num: Int = 0
)

@Serializable
data class DynamicDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicDetailData? = null
)

@Serializable
data class DynamicDetailData(
    val item: DynamicItem? = null,
    val fallback: DynamicOpusFallback? = null
)

@Serializable
data class DynamicOpusFallback(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0
)

@Serializable
data class TopicDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TopicDetailData? = null
)

@Serializable
data class TopicDetailData(
    @SerialName("top_details")
    val topDetails: TopicTopDetails? = null
)

@Serializable
data class TopicTopDetails(
    @SerialName("topic_creator")
    val topicCreator: TopicCreator? = null,
    @SerialName("topic_item")
    val topicItem: TopicItem? = null
)

@Serializable
data class TopicCreator(
    val uid: Long = 0,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class TopicItem(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    @SerialName("share_pic")
    val sharePic: String = "",
    val view: Long = 0,
    val discuss: Long = 0,
    val dynamics: Long = 0,
    val fav: Long = 0,
    val like: Long = 0,
    val share: Long = 0,
    @SerialName("share_url")
    val shareUrl: String = "",
    @SerialName("jump_url")
    val jumpUrl: String = ""
)

@Serializable
data class TopicFeedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TopicFeedData? = null
)

@Serializable
data class TopicFeedData(
    @SerialName("topic_card_list")
    val topicCardList: TopicCardList? = null
)

@Serializable
data class TopicCardList(
    @SerialName("has_more")
    val hasMore: Boolean = false,
    val offset: String = "",
    val items: List<TopicDynamicCardItem> = emptyList(),
    @SerialName("topic_sort_by_conf")
    val topicSortByConf: TopicSortByConf? = null,
)

@Serializable
data class TopicSortByConf(
    @SerialName("all_sort_by")
    val allSortBy: List<TopicSortOption> = emptyList(),
    @SerialName("show_sort_by")
    val showSortBy: Int = 0,
)

@Serializable
data class TopicSortOption(
    @SerialName("sort_by")
    val sortBy: Int = 0,
    @SerialName("sort_name")
    val sortName: String = "",
)

@Serializable
data class TopicDynamicCardItem(
    @SerialName("dynamic_card_item")
    val dynamicCardItem: DynamicItem? = null,
    @SerialName("topic_type")
    val topicType: String = ""
)

// --- 动态卡片 ---
@Serializable
data class DynamicItem(
    val id_str: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val type: String = "", // DYNAMIC_TYPE_AV, DYNAMIC_TYPE_DRAW, DYNAMIC_TYPE_WORD, DYNAMIC_TYPE_FORWARD；opus/detail 可能返回数字
    val visible: Boolean = true,
    @Serializable(with = DynamicModulesFlexibleSerializer::class)
    val modules: DynamicModules = DynamicModules(),
    val orig: DynamicItem? = null,  //  转发动态的原始内容
    val basic: DynamicBasic? = null  //  [新增] 评论区参数
)

object DynamicModulesFlexibleSerializer : KSerializer<DynamicModules> {
    override val descriptor: SerialDescriptor = DynamicModules.serializer().descriptor

    /** 解析 opus 段落内交互载荷（预约 / 赛事 / 按钮）时使用的宽松 Json 配置。 */
    private val linkCardPayloadJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override fun deserialize(decoder: Decoder): DynamicModules {
        val jsonDecoder = decoder as? JsonDecoder ?: return DynamicModules.serializer().deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            is JsonObject -> jsonDecoder.json.decodeFromJsonElement(DynamicModules.serializer(), element)
            is JsonArray -> {
                var merged = DynamicModules()
                var opusTitle: String? = null
                val opusContentBlocks = mutableListOf<OpusContentBlock>()
                element.forEach { node ->
                    val obj = node as? JsonObject ?: return@forEach
                    val parsed = jsonDecoder.json.decodeFromJsonElement(DynamicModules.serializer(), obj)
                    merged = merged.copy(
                        module_author = parsed.module_author ?: merged.module_author,
                        module_dynamic = parsed.module_dynamic ?: merged.module_dynamic,
                        module_more = parsed.module_more ?: merged.module_more,
                        module_stat = parsed.module_stat ?: merged.module_stat,
                        module_fold = parsed.module_fold ?: merged.module_fold,
                        module_tag = parsed.module_tag ?: merged.module_tag,
                        module_dispute = parsed.module_dispute ?: merged.module_dispute,
                        module_interaction = parsed.module_interaction ?: merged.module_interaction
                    )

                    val moduleType = obj["module_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val titleModule = obj["module_title"] as? JsonObject
                    if (moduleType == "MODULE_TYPE_TITLE" || titleModule != null) {
                        opusTitle = titleModule?.get("text")?.jsonPrimitive?.contentOrNull
                    }
                    val contentModule = obj["module_content"] as? JsonObject
                    if (moduleType == "MODULE_TYPE_CONTENT" || contentModule != null) {
                        val paragraphs = contentModule?.get("paragraphs") as? JsonArray
                        paragraphs?.forEach { paragraphNode ->
                            val paragraph = paragraphNode as? JsonObject ?: return@forEach
                            opusContentBlocks += extractParagraphBlocks(paragraph)
                        }
                    }
                }
                merged.normalizeWithOpusModules(
                    title = opusTitle,
                    contentBlocks = opusContentBlocks
                )
            }
            else -> DynamicModules()
        }
    }

    override fun serialize(encoder: Encoder, value: DynamicModules) {
        DynamicModules.serializer().serialize(encoder, value)
    }

    private fun extractParagraphBlocks(paragraph: JsonObject): List<OpusContentBlock> {
        val blocks = mutableListOf<OpusContentBlock>()
        val paragraphType = paragraph["para_type"]?.jsonPrimitive?.intOrNull ?: 0
        val alignment = paragraph["align"]?.jsonPrimitive?.intOrNull ?: 0
        extractParagraphHeading(paragraph)?.let {
            blocks += OpusContentBlock.Heading(text = it, alignment = alignment)
        }
        val listBlock = extractParagraphList(paragraph, alignment)
        val codeText = extractParagraphCode(paragraph)
        listBlock?.let { blocks += it }
        codeText?.let { blocks += OpusContentBlock.Code(it) }
        if (paragraphType == 3) {
            blocks += OpusContentBlock.Divider(extractParagraphLinePic(paragraph))
        }
        if (listBlock == null && codeText == null && paragraphType != 3) {
            extractParagraphText(paragraph)?.let { text ->
                if (blocks.none { it.plainText == text }) {
                    blocks += if (paragraphType == 4) {
                        OpusContentBlock.Quote(text = text, alignment = alignment)
                    } else {
                        OpusContentBlock.Text(
                            text = text,
                            alignment = alignment,
                            richTextNodes = extractParagraphRichTextNodes(paragraph),
                        )
                    }
                }
            }
        }
        extractParagraphPics(paragraph, includeLinePic = paragraphType != 3).forEach { pic ->
            blocks += OpusContentBlock.Image(pic)
        }
        extractParagraphLinkCard(paragraph)?.let { blocks += OpusContentBlock.LinkCard(it) }
        return blocks
    }

    private fun extractParagraphHeading(paragraph: JsonObject): String? {
        val nodes = paragraph["heading"]
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.get("nodes")
        return extractParagraphNodesText(nodes).takeIf { it.isNotBlank() }
    }

    private fun extractParagraphList(
        paragraph: JsonObject,
        alignment: Int,
    ): OpusContentBlock.ListBlock? {
        val listObject = paragraph["list"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
        val ordered = listObject["style"]?.jsonPrimitive?.intOrNull == 1
        val items = listObject["items"]
            ?.let { runCatching { it.jsonArray }.getOrNull() }
            .orEmpty()
            .mapNotNull { item ->
                val itemObject = item as? JsonObject ?: return@mapNotNull null
                extractParagraphNodesText(itemObject["nodes"]).takeIf { it.isNotBlank() }
            }
        if (items.isEmpty()) return null
        return OpusContentBlock.ListBlock(
            items = items,
            ordered = ordered,
            alignment = alignment,
        )
    }

    private fun extractParagraphCode(paragraph: JsonObject): String? {
        val codeObject = paragraph["code"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
        return codeObject["content"]?.jsonPrimitive?.contentOrNull
            ?.replace("&quot;", "\"")
            ?.replace("&amp;", "&")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractParagraphNodesText(nodesElement: kotlinx.serialization.json.JsonElement?): String {
        val nodes = nodesElement as? JsonArray ?: return ""
        return buildString {
            nodes.forEach { node ->
                val nodeObject = node as? JsonObject ?: return@forEach
                val words = (nodeObject["word"] as? JsonObject)
                    ?.get("words")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: (nodeObject["rich"] as? JsonObject)
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                    ?: (nodeObject["rich"] as? JsonObject)
                        ?.get("orig_text")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                    ?: ((nodeObject["rich"] as? JsonObject)?.get("emoji") as? JsonObject)
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.contentOrNull
                    ?: (nodeObject["formula"] as? JsonObject)
                        ?.get("latex_content")
                        ?.jsonPrimitive
                        ?.contentOrNull
                if (!words.isNullOrBlank()) append(words)
            }
        }.trim()
    }

    private fun extractParagraphText(paragraph: JsonObject): String? {
        val nodes = paragraph["text"]
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.get("nodes")
        return extractParagraphNodesText(nodes).takeIf { it.isNotBlank() }
    }

    private fun extractParagraphRichTextNodes(paragraph: JsonObject): List<RichTextNode> {
        val nodes = (paragraph["text"] as? JsonObject)?.get("nodes") as? JsonArray
            ?: return emptyList()
        val parsedNodes = nodes.mapNotNull { nodeElement ->
            val node = nodeElement as? JsonObject ?: return@mapNotNull null
            (node["rich"] as? JsonObject)?.let { rich ->
                val emoji = parseParagraphEmojiInfo(rich["emoji"] as? JsonObject)
                val emojiText = emoji?.text.orEmpty()
                val rawText = rich["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val rawOrigText = rich["orig_text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val text = rawText.ifBlank { rawOrigText.ifBlank { emojiText } }
                val origText = rawOrigText.ifBlank { rawText.ifBlank { emojiText } }
                val richNode = RichTextNode(
                    type = rich["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    text = text,
                    orig_text = origText,
                    emoji = emoji,
                    jump_url = rich["jump_url"]?.jsonPrimitive?.contentOrNull,
                    rid = rich["rid"]?.jsonPrimitive?.contentOrNull,
                )
                richNode
            } ?: (node["word"] as? JsonObject)
                ?.get("words")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
                ?.let { words -> RichTextNode(type = "RICH_TEXT_NODE_TYPE_TEXT", text = words) }
                ?: (node["formula"] as? JsonObject)
                    ?.get("latex_content")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { formula ->
                        // Keep formula nodes in the stream so an otherwise complete
                        // paragraph is not downgraded to plain text (which would also
                        // lose AT metadata next to the formula).
                        RichTextNode(type = "RICH_TEXT_NODE_TYPE_TEXT", text = formula)
                    }
        }
        return parsedNodes.filter { it.text.isNotBlank() || it.orig_text.isNotBlank() || it.emoji != null }
    }

    private fun parseParagraphEmojiInfo(emojiObject: JsonObject?): EmojiInfo? {
        if (emojiObject == null) return null
        val iconUrl = emojiObject["icon_url"]?.jsonPrimitive?.contentOrNull
            ?: emojiObject["url"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val webpUrl = emojiObject["webp_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val gifUrl = emojiObject["gif_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val size = emojiObject["size"]?.jsonPrimitive?.intOrNull ?: 1
        val text = emojiObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (iconUrl.isBlank() && webpUrl.isBlank() && gifUrl.isBlank() && text.isBlank()) return null
        return EmojiInfo(
            icon_url = iconUrl,
            webp_url = webpUrl,
            gif_url = gifUrl,
            size = size,
            text = text,
        )
    }

    private fun extractParagraphPics(
        paragraph: JsonObject,
        includeLinePic: Boolean,
    ): List<OpusPic> {
        val results = mutableListOf<OpusPic>()
        val picObject = paragraph["pic"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val pics = picObject?.get("pics") as? JsonArray
        pics?.mapNotNullTo(results) { picNode ->
            val pic = picNode as? JsonObject ?: return@mapNotNullTo null
            parseOpusPic(pic)
        }
        if (results.isEmpty()) {
            parseOpusPic(picObject)?.let(results::add)
        }
        if (results.isEmpty()) {
            val picsAtRoot = paragraph["pics"] as? JsonArray
            picsAtRoot?.mapNotNullTo(results) { picNode ->
                val pic = picNode as? JsonObject ?: return@mapNotNullTo null
                parseOpusPic(pic)
            }
        }
        if (includeLinePic) {
            extractParagraphLinePic(paragraph)?.let(results::add)
        }
        return results
    }

    private fun extractParagraphLinePic(paragraph: JsonObject): OpusPic? = paragraph["line"]
        ?.let { runCatching { it.jsonObject }.getOrNull() }
        ?.get("pic")
        ?.let { runCatching { it.jsonObject }.getOrNull() }
        ?.let(::parseOpusPic)

    private fun parseOpusPic(pic: JsonObject?): OpusPic? {
        if (pic == null) return null
        val url = pic["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (url.isEmpty()) return null
        return OpusPic(
            url = normalizeOpusImageUrl(url),
            width = pic["width"]?.jsonPrimitive?.intOrNull ?: 0,
            height = pic["height"]?.jsonPrimitive?.intOrNull ?: 0,
            size = pic["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            live_url = pic["live_url"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun extractParagraphLinkCard(paragraph: JsonObject): OpusLinkCard? {
        val card = paragraph["link_card"]
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.get("card")
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?: return null
        val type = card.stringValue("type")
        if (type.isBlank()) return null
        return when (type) {
            "LINK_CARD_TYPE_UGC" -> parseUgcLinkCard(card, type)
            "LINK_CARD_TYPE_COMMON" -> parseCommonLinkCard(card, type)
            "LINK_CARD_TYPE_LIVE" -> parseLiveLinkCard(card, type)
            "LINK_CARD_TYPE_OPUS" -> parseOpusLinkCard(card, type)
            "LINK_CARD_TYPE_MUSIC" -> parseMusicLinkCard(card, type)
            "LINK_CARD_TYPE_GOODS" -> parseGoodsLinkCard(card, type)
            "LINK_CARD_TYPE_VOTE" -> parseVoteLinkCard(card, type)
            "LINK_CARD_TYPE_RESERVE" -> parseReserveLinkCard(card, type)
            "LINK_CARD_TYPE_MATCH" -> parseMatchLinkCard(card, type)
            "LINK_CARD_TYPE_UPOWER_LOTTERY" -> parseUpowerLotteryLinkCard(card, type)
            "LINK_CARD_TYPE_ITEM_NULL" -> parseItemNullLinkCard(card, type)
            else -> parseGenericLinkCard(card, type)
        }.takeIf { it.title.isNotBlank() || it.cover.isNotBlank() || it.jumpUrl.isNotBlank() }
    }

    private fun parseUgcLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val ugc = card.objectValue("ugc")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { ugc.stringValue("id_str") },
            title = ugc.stringValue("title"),
            description = ugc.stringValue("desc_second"),
            // 不设 label：信息流的 additional.ugc 根本没有 head_text 字段，用它会让
            // 同一个投稿卡在信息流没有角标、进详情却多出一行，版式整体下移。
            cover = normalizeOptionalOpusImageUrl(ugc.stringValue("cover")),
            jumpUrl = ugc.stringValue("jump_url")
        )
    }

    private fun parseCommonLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val common = card.objectValue("common")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { common.stringValue("id_str") },
            title = common.stringValue("title"),
            description = listOf(
                common.stringValue("desc"),
                common.stringValue("desc1"),
                common.stringValue("desc2")
            ).filter { it.isNotBlank() }.joinToString("\n"),
            // 不设 label：实测同一个「相关游戏」横幅，信息流的 head_text 是「相关游戏」
            // 而详情侧为空，用它会让卡片进详情后少一行、内容整体上移。统一不展示角标后
            // 两个入口的版式与高度完全一致。
            cover = normalizeOptionalOpusImageUrl(common.stringValue("cover")),
            jumpUrl = common.stringValue("jump_url")
        )
    }

    private fun parseLiveLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val live = card.objectValue("live")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { live.stringValue("id") },
            title = live.stringValue("title"),
            description = listOf(
                live.stringValue("desc_first"),
                live.stringValue("desc_second")
            ).filter { it.isNotBlank() }.joinToString("\n"),
            label = live.stringValue("badge_text"),
            cover = normalizeOptionalOpusImageUrl(live.stringValue("cover")),
            jumpUrl = live.stringValue("jump_url")
        )
    }

    private fun parseOpusLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val opus = card.objectValue("opus")
        val authorName = opus.objectValue("author").stringValue("name")
        val statView = opus.objectValue("stat").stringValue("view")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid"),
            title = opus.stringValue("title"),
            description = listOf(
                authorName,
                statView.takeIf { it.isNotBlank() }?.let { "${it}阅读" }.orEmpty()
            ).filter { it.isNotBlank() }.joinToString(" · "),
            cover = normalizeOptionalOpusImageUrl(opus.stringValue("cover")),
            jumpUrl = opus.stringValue("jump_url")
        )
    }

    private fun parseMusicLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val music = card.objectValue("music")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { music.stringValue("id") },
            title = music.stringValue("title"),
            description = music.stringValue("label"),
            cover = normalizeOptionalOpusImageUrl(music.stringValue("cover")),
            jumpUrl = music.stringValue("jump_url")
        )
    }

    private fun parseGoodsLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val goods = card.objectValue("goods")
        val firstItem = goods?.get("items")
            ?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.firstOrNull()
            ?.let { runCatching { it.jsonObject }.getOrNull() }
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { firstItem.stringValue("id") },
            title = firstItem.stringValue("name").ifBlank { goods.stringValue("head_text") },
            description = firstItem.stringValue("price").ifBlank { firstItem.stringValue("brief") },
            label = goods.stringValue("head_text"),
            badgeText = firstItem.stringValue("jump_desc"),
            cover = normalizeOptionalOpusImageUrl(firstItem.stringValue("cover")),
            jumpUrl = firstItem.stringValue("jump_url").ifBlank { goods.stringValue("jump_url") }
        )
    }

    private fun parseVoteLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val vote = card.objectValue("vote")
        val joinNum = vote.stringValue("join_num").toIntOrNull() ?: 0
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { vote.stringValue("vote_id") },
            title = vote.stringValue("title").ifBlank { vote.stringValue("desc") }.ifBlank { "投票" },
            // 与信息流对齐：描述行只放参与人数，不再把投票文案重复一遍。
            description = if (joinNum > 0) "$joinNum 人参与" else "",
            jumpUrl = vote.stringValue("jump_url")
        )
    }

    private fun parseReserveLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val reserve = card.objectValue("reserve")
        val button = reserve.objectValue("button")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { reserve.stringValue("rid") },
            title = reserve.stringValue("title").ifBlank { "预约" },
            description = listOf(
                reserve.objectValue("desc1").stringValue("text"),
                reserve.objectValue("desc2").stringValue("text"),
                reserve.objectValue("desc3").stringValue("text"),
            ).filter(String::isNotBlank).joinToString(" · "),
            jumpUrl = reserve.stringValue("jump_url").ifBlank { button.stringValue("jump_url") },
            // 按钮文案走与信息流完全相同的取值规则（resolveDynamicCardButtonText），
            // 避免同一个预约按钮在列表里显示「预约」、进详情变成别的文字。
            badgeText = resolveDynamicCardButtonText(decodeButtonPayload(button)),
            // 详情侧也要能直接预约，而不是只能点开跳转链接。
            reserve = decodeReservePayload(reserve),
        )
    }

    private fun parseMatchLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val match = card.objectValue("match")
        val matchInfo = match.objectValue("match_info")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { match.stringValue("id") },
            title = match.stringValue("title").ifBlank { matchInfo.stringValue("title") },
            description = match.stringValue("sub_title").ifBlank { matchInfo.stringValue("sub_title") },
            label = "赛事",
            cover = normalizeOptionalOpusImageUrl(match.stringValue("cover")),
            jumpUrl = match.stringValue("jump_url"),
            // 补解析 match_info：此前详情侧把比分与左右队伍整段丢掉了，
            // 同一个赛事卡在信息流有比分行、进详情就消失。
            match = decodeMatchPayload(match),
        )
    }

    private fun parseUpowerLotteryLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val lottery = card.objectValue("upower_lottery")
        val button = lottery.objectValue("button")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid").ifBlank { lottery.stringValue("rid") },
            title = lottery.stringValue("title").ifBlank { "充电专属抽奖" },
            description = listOf(
                lottery.objectValue("desc").stringValue("text"),
                lottery.objectValue("hint").stringValue("text"),
            ).filter(String::isNotBlank).joinToString(" · "),
            label = "充电专属抽奖",
            jumpUrl = lottery.stringValue("jump_url").ifBlank { button.stringValue("jump_url") },
            badgeText = resolveDynamicCardButtonText(decodeButtonPayload(button)),
        )
    }

    private fun parseItemNullLinkCard(card: JsonObject, type: String): OpusLinkCard {
        val itemNull = card.objectValue("item_null")
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid"),
            title = itemNull.stringValue("text").ifBlank { "内容已失效" },
            cover = normalizeOptionalOpusImageUrl(itemNull.stringValue("icon"))
        )
    }

    private fun parseGenericLinkCard(card: JsonObject, type: String): OpusLinkCard {
        return OpusLinkCard(
            type = type,
            oid = card.stringValue("oid"),
            title = card.stringValue("title"),
            description = card.stringValue("desc"),
            cover = normalizeOptionalOpusImageUrl(card.stringValue("cover")),
            jumpUrl = card.stringValue("jump_url")
        )
    }

    /**
     * opus 段落里的交互载荷（预约、赛事）用同一套数据模型承载，
     * 这样信息流 additional 与详情 opus link_card 可以共用渲染与交互逻辑。
     * 解析失败时退化为 null，只影响交互，不影响卡片本身的展示。
     */
    private fun decodeReservePayload(reserve: JsonObject?): DynamicAdditionalReserve? =
        reserve?.let { payload ->
            runCatching {
                linkCardPayloadJson.decodeFromJsonElement(DynamicAdditionalReserve.serializer(), payload)
            }.getOrNull()
        }

    private fun decodeMatchPayload(match: JsonObject?): DynamicAdditionalMatch? =
        match?.let { payload ->
            runCatching {
                linkCardPayloadJson.decodeFromJsonElement(DynamicAdditionalMatch.serializer(), payload)
            }.getOrNull()
        }

    private fun decodeButtonPayload(button: JsonObject?): DynamicCardButton? =
        button?.let { payload ->
            runCatching {
                linkCardPayloadJson.decodeFromJsonElement(DynamicCardButton.serializer(), payload)
            }.getOrNull()
        }

    private fun JsonObject?.stringValue(key: String): String {
        if (this == null) return ""
        return get(key)?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    }

    private fun JsonObject?.objectValue(key: String): JsonObject? {
        if (this == null) return null
        return get(key)?.let { runCatching { it.jsonObject }.getOrNull() }
    }

    private fun normalizeOptionalOpusImageUrl(rawUrl: String): String {
        return rawUrl.takeIf { it.isNotBlank() }?.let(::normalizeOpusImageUrl).orEmpty()
    }

    private fun normalizeOpusImageUrl(rawUrl: String): String {
        return when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("http://") -> rawUrl.replaceFirst("http://", "https://")
            else -> rawUrl
        }
    }

    private fun DynamicModules.normalizeWithOpusModules(
        title: String?,
        contentBlocks: List<OpusContentBlock>
    ): DynamicModules {
        val existing = module_dynamic
        val paragraphTexts = contentBlocks.mapNotNull { block ->
            block.plainText.takeIf(String::isNotBlank)
        }
        val pics = contentBlocks.mapNotNull { block ->
            when (block) {
                is OpusContentBlock.Image -> block.pic
                is OpusContentBlock.Divider -> block.pic
                else -> null
            }
        }
        val descText = paragraphTexts.joinToString(separator = "\n").trim()
        val cleanTitle = title?.trim().takeUnless { it.isNullOrBlank() }
        val hasDerivedContent = contentBlocks.isNotEmpty() || descText.isNotBlank() || pics.isNotEmpty() || cleanTitle != null
        if (!hasDerivedContent) return this

        val existingDesc = existing?.desc
        val existingDescText = existingDesc?.text.orEmpty()
        val mergedDescText = when {
            descText.isBlank() -> existingDescText
            existingDescText.isBlank() -> descText
            descText.length > existingDescText.length -> descText
            else -> existingDescText
        }
        val allBlockRichTextNodes = contentBlocks.flatMap { block ->
            when (block) {
                is OpusContentBlock.Text -> block.richTextNodes
                else -> emptyList()
            }
        }
        val mergedRichTextNodes = if (allBlockRichTextNodes.isNotEmpty()) {
            allBlockRichTextNodes
        } else {
            existingDesc?.rich_text_nodes.orEmpty()
        }
        val mergedDesc = if (mergedDescText.isNotBlank()) {
            DynamicDesc(
                text = mergedDescText,
                rich_text_nodes = if (mergedDescText == existingDescText && existingDesc != null && existingDesc.rich_text_nodes.isNotEmpty()) {
                    existingDesc.rich_text_nodes
                } else {
                    mergedRichTextNodes
                }
            )
        } else {
            null
        }

        val existingMajor = existing?.major
        val existingOpus = existingMajor?.opus
        val mergedOpus = OpusMajor(
            jump_url = existingOpus?.jump_url.orEmpty(),
            title = cleanTitle ?: existingOpus?.title,
            summary = when {
                mergedDescText.isNotBlank() -> OpusSummary(
                    text = mergedDescText,
                    rich_text_nodes = mergedRichTextNodes
                )
                existingOpus?.summary != null -> existingOpus.summary
                else -> null
            },
            pics = if (pics.isNotEmpty()) pics else existingOpus?.pics.orEmpty(),
            contentBlocks = if (contentBlocks.isNotEmpty()) contentBlocks else existingOpus?.contentBlocks.orEmpty()
        )
        val mergedMajor = existingMajor?.copy(
            type = "MAJOR_TYPE_OPUS",
            opus = mergedOpus,
        ) ?: DynamicMajor(type = "MAJOR_TYPE_OPUS", opus = mergedOpus)
        return copy(
            module_dynamic = DynamicContentModule(
                desc = mergedDesc ?: existingDesc,
                major = if (existingMajor == null || existingMajor.type.isBlank() || existingMajor.type == "MAJOR_TYPE_OPUS") {
                    mergedMajor
                } else {
                    existingMajor
                },
                additional = existing?.additional,
                topic = existing?.topic,
            )
        )
    }

}

//  [新增] 动态基础信息 - 包含评论区参数
@Serializable
data class DynamicBasic(
    val comment_id_str: String = "",   // 评论区 oid
    val comment_type: Int = 0,         // 评论区 type (1=视频, 11=图片, 17=动态)
    val rid_str: String = ""           // 资源 id
)

// --- 动态模块集合 ---
@Serializable
data class DynamicModules(
    val module_author: DynamicAuthorModule? = null,
    val module_dynamic: DynamicContentModule? = null,
    val module_more: DynamicMoreModule? = null,
    val module_stat: DynamicStatModule? = null,
    // 相关动态折叠条（"展开x条相关动态"）
    val module_fold: DynamicFoldModule? = null,
    // 置顶标记（text == "置顶" 时置顶）
    val module_tag: DynamicTagModule? = null,
    // 违规/风险提示条
    val module_dispute: DynamicDisputeModule? = null,
    // 互动条（UP主点赞/相关评论提示）
    val module_interaction: DynamicInteractionModule? = null
)

@Serializable
data class DynamicInteractionModule(
    val items: List<DynamicInteractionItem> = emptyList()
)

@Serializable
data class DynamicInteractionItem(
    /** 1 = 相关评论（评论图标），其他值 = UP主点赞等（点赞图标）。 */
    val type: Int = 0,
    val desc: DynamicDesc? = null
)

@Serializable
data class DynamicFoldModule(
    val ids: List<String> = emptyList(),
    val statement: String = "",
    val users: List<DynamicFoldUser> = emptyList()
)

@Serializable
data class DynamicFoldUser(
    val mid: Long = 0,
    val face: String = ""
)

@Serializable
data class DynamicTagModule(
    val text: String = ""
)

@Serializable
data class DynamicDisputeModule(
    val title: String = "",
    val desc: String = "",
    val jump_url: String = ""
)

// --- 评论互动设置（评论精选 / 评论开关） ---
// API: x/v2/reply/subject/interaction-status
@Serializable
data class ReplyInteractionResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ReplyInteractionData? = null
)

@Serializable
data class ReplyInteractionData(
    val up_reply_selection: ReplyInteractionStatus? = null,
    val up_reply: ReplyInteractionStatus? = null
)

@Serializable
data class ReplyInteractionStatus(
    val status: Int = 0, // 1 = 开启中
    val can_modify: Boolean = false
)

// Read-only recent-author flags. Missing flags remain unknown, unlike the legacy integer uplist.
@Serializable
data class DynamicAuthorUpdatesPortalResponse(
    val code: Int? = null,
    val data: DynamicAuthorUpdatesPortalData? = null
)

@Serializable
data class DynamicAuthorUpdatesPortalData(
    @SerialName("up_list") val upList: DynamicAuthorUpdatesPage? = null
)

@Serializable
data class DynamicAuthorUpdatesPageResponse(
    val code: Int? = null,
    val data: DynamicAuthorUpdatesPage? = null
)

@Serializable
data class DynamicAuthorUpdatesPage(
    @SerialName("has_more") val hasMore: Boolean? = null,
    val items: List<DynamicAuthorUpdateItem>? = null,
    val offset: String? = null
)

@Serializable
data class DynamicAuthorUpdateItem(
    val mid: Long? = null,
    @SerialName("has_update") val hasUpdate: Boolean? = null
)

// --- 关注 UP 列表（含未读标记，供 UP 列表红点） ---
// API: dynamic_svr/v1/dynamic_svr/w_dyn_uplist
@Serializable
data class UplistResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UplistData? = null
)

@Serializable
data class UplistData(
    val items: List<UplistItem> = emptyList()
)

@Serializable
data class UplistItem(
    val user_profile: UplistUserProfile? = null,
    @SerialName("has_update") val has_update: Int = 0
)

@Serializable
data class UplistUserProfile(
    val info: UplistUserInfo? = null
)

@Serializable
data class UplistUserInfo(
    val uid: Long = 0,
    val uname: String = "",
    val face: String = ""
)

// --- 发布纯文本动态响应（防 shadow-ban 校验用） ---
// API: dynamic_svr/v1/dynamic_svr/create
@Serializable
data class DynamicCreateResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicCreateData? = null
)

@Serializable
data class DynamicCreateData(
    @SerialName("dynamic_id_str") val dynamic_id_str: String = ""
)

@Serializable
data class DynamicMoreModule(
    val three_point_items: List<DynamicThreePointItem> = emptyList()
)

@Serializable
data class DynamicThreePointItem(
    val label: String = "",
    val modal: DynamicThreePointModal? = null,
    val params: DynamicThreePointParams? = null,
    val type: String = ""
)

@Serializable
data class DynamicThreePointModal(
    val cancel: String = "",
    val confirm: String = "",
    val content: String = "",
    val title: String = ""
)

@Serializable
data class DynamicThreePointParams(
    val dyn_id_str: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val dyn_type: Int = 0,
    val rid_str: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val dynamic_id: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val status: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0
)

// --- 作者模块 ---
@Serializable
data class DynamicAuthorModule(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val pub_time: String = "", // "昨天 18:00"
    @Serializable(with = FlexibleLongSerializer::class)
    val pub_ts: Long = 0, // 时间戳
    // 动作描述："投稿了视频" / "发布了图文" / "直播中" 等
    @SerialName("pub_action")
    val pub_action: String = "",
    @Serializable(with = FlexibleBooleanSerializer::class)
    val following: Boolean? = null,
    val official_verify: DynamicOfficialVerify? = null,
    val vip: DynamicVipInfo? = null,
    val decorate: DecorateInfo? = null
)

@Serializable
data class DynamicOfficialVerify(
    val type: Int = -1, // 0: 个人认证, 1: 机构认证, -1: 无
    val desc: String = ""
)

@Serializable
data class DynamicVipInfo(
    val type: Int = 0, // 0: 无, 1: 月度, 2: 年度
    val status: Int = 0,
    val nickname_color: String = "" // "#FB7299"
)

@Serializable
data class DecorateInfo(
    val card_url: String = "", // 装扮卡片 URL
    val name: String = ""
)

// --- 内容模块 ---
@Serializable
data class DynamicContentModule(
    val desc: DynamicDesc? = null,
    val major: DynamicMajor? = null,
    val additional: DynamicAdditional? = null,
    val topic: DynamicTopic? = null,
)

@Serializable
data class DynamicTopic(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val name: String = "",
)

@Serializable
data class DynamicAdditional(
    val type: String = "",
    val common: DynamicAdditionalCommon? = null,
    val ugc: DynamicAdditionalUgc? = null,
    val reserve: DynamicAdditionalReserve? = null,
    val goods: DynamicAdditionalGoods? = null,
    val vote: DynamicAdditionalVote? = null,
    val match: DynamicAdditionalMatch? = null,
    val upower_lottery: DynamicAdditionalUpowerLottery? = null
)

@Serializable
data class DynamicAdditionalCommon(
    val button: DynamicCardButton? = null,
    val cover: String = "",
    /** 详情侧 opus link_card 会带 desc，信息流此前漏解析，补齐后两侧描述才能逐字一致。 */
    val desc: String = "",
    val desc1: String = "",
    val desc2: String = "",
    val head_text: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val id_str: String = "",
    val jump_url: String = "",
    val style: Int = 0,
    val sub_type: String = "",
    val title: String = ""
)

@Serializable
data class DynamicAdditionalUgc(
    val title: String = "",
    val cover: String = "",
    val desc_second: String = "",
    val jump_url: String = "",
    /** 角标文案来源，与详情侧 LINK_CARD_TYPE_UGC 的 ugc.head_text 对齐。 */
    val head_text: String = ""
)

@Serializable
data class DynamicAdditionalReserve(
    val title: String = "",
    val state: Int = 0,
    val desc1: DynamicAdditionalText? = null,
    val desc2: DynamicAdditionalText? = null,
    val desc3: DynamicAdditionalText? = null,
    val button: DynamicCardButton? = null,
    val jump_url: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val reserve_total: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val rid: Long = 0,
    val stype: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val up_mid: Long = 0
)

@Serializable
data class DynamicReserveClickResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicReserveClickData? = null,
)

@Serializable
data class DynamicReserveClickData(
    val desc_update: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val reserve_update: Long = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val final_btn_status: Int = 0,
)

@Serializable
data class DynamicAdditionalGoods(
    val head_text: String = "",
    val jump_url: String = "",
    val items: List<DynamicAdditionalGoodsItem> = emptyList()
)

@Serializable
data class DynamicAdditionalGoodsItem(
    val name: String = "",
    val brief: String = "",
    val price: String = "",
    val cover: String = "",
    val jump_url: String = "",
    /** 右侧角标文案，与详情侧 items[].jump_desc 对齐。 */
    val jump_desc: String = ""
)

@Serializable
data class DynamicAdditionalVote(
    val title: String = "",
    val desc: String = "",
    val join_num: Int = 0,
    val vote_id: Long = 0
)

@Serializable
data class DynamicAdditionalMatch(
    val title: String = "",
    val sub_title: String = "",
    val cover: String = "",
    val jump_url: String = "",
    @SerialName("match_info")
    val matchInfo: DynamicMatchInfo? = null
)

@Serializable
data class DynamicMatchInfo(
    val title: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val sub_title: String = "",
    @SerialName("center_top")
    val centerTop: List<String> = emptyList(),
    @SerialName("center_bottom")
    val centerBottom: String = "",
    @SerialName("left_team")
    val leftTeam: DynamicMatchTeam? = null,
    @SerialName("right_team")
    val rightTeam: DynamicMatchTeam? = null
)

@Serializable
data class DynamicMatchTeam(
    val name: String = "",
    val pic: String = "",
    val score: String = "",
    val id: Long = 0
)

@Serializable
data class DynamicAdditionalUpowerLottery(
    val button: DynamicCardButton? = null,
    val desc: DynamicAdditionalText? = null,
    val hint: DynamicAdditionalText? = null,
    val jump_url: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val rid: Long = 0,
    val state: Int = 0,
    val title: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val up_mid: Long = 0,
    val upower_action_state: Int = 0,
    val upower_level: Int = 0
)

@Serializable
data class DynamicCardButton(
    val jump_style: DynamicCardButtonStyle? = null,
    val jump_url: String = "",
    val type: Int = 0,
    val status: Int = 0,
    val check: DynamicCardButtonStyle? = null,
    val uncheck: DynamicCardButtonStyle? = null
)

@Serializable
data class DynamicCardButtonStyle(
    val disable: Int = 0,
    val icon_url: String = "",
    val text: String = "",
    val toast: String = ""
)

/**
 * 动态卡片按钮文案。信息流 additional 与详情 opus link_card 共用同一套取值规则：
 * 已/未选中文案优先，其次回落到跳转文案，避免同一个按钮在两个入口显示两种文字。
 */
fun resolveDynamicCardButtonText(button: DynamicCardButton?): String {
    if (button == null) return ""
    val stateText = if (button.status == button.type) button.check?.text else button.uncheck?.text
    return stateText.orEmpty().ifBlank { button.jump_style?.text.orEmpty() }
}

@Serializable
data class DynamicAdditionalText(
    val text: String = "",
    val jump_url: String = "",
)

@Serializable
data class DynamicDesc(
    val text: String = "", // 动态文字内容
    val rich_text_nodes: List<RichTextNode> = emptyList()
)

@Serializable
data class RichTextNode(
    val type: String = "", // TEXT, EMOJI, AT, TOPIC / RICH_TEXT_NODE_TYPE_*
    val text: String = "",
    /** 部分接口只填 orig_text；渲染时与 text 互为兜底 */
    val orig_text: String = "",
    val emoji: EmojiInfo? = null,
    val jump_url: String? = null,
    /** AT 节点对应用户 mid；API 可能给 number，用 flexible string 避免解析失败 */
    @Serializable(with = FlexibleStringSerializer::class)
    val rid: String? = null
)

@Serializable
data class EmojiInfo(
    /** 主图；部分接口用 url 字段（评论/表情面板风格） */
    @JsonNames("url")
    val icon_url: String = "",
    val webp_url: String = "",
    val gif_url: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val size: Int = 1,
    val text: String = ""
)

// --- 主要内容 (视频/图片/直播/图文) ---
@Serializable
data class DynamicMajor(
    val type: String = "", // MAJOR_TYPE_ARCHIVE, MAJOR_TYPE_DRAW, MAJOR_TYPE_LIVE_RCMD, MAJOR_TYPE_OPUS, MAJOR_TYPE_NONE
    val archive: ArchiveMajor? = null, // 视频
    val pgc: ArchiveMajor? = null, // 番剧/影视
    val article: ArticleMajor? = null, // 专栏
    val draw: DrawMajor? = null, // 图片
    val live_rcmd: LiveRcmdMajor? = null, //  直播推荐
    val live: LiveMajor? = null, // DYNAMIC_TYPE_LIVE
    val opus: OpusMajor? = null, //  [新增] 图文动态 (新版格式)
    val ugc_season: UgcSeasonMajor? = null, // [新增] 合集
    val medialist: MedialistMajor? = null,
    val courses: CoursesMajor? = null,
    val subscription_new: SubscriptionNewMajor? = null,
    val common: CommonMajor? = null,
    val music: MusicMajor? = null,
    val none: NoneMajor? = null,
    val upower_common: UpowerCommonMajor? = null
)

//  [新增] 图文动态 (MAJOR_TYPE_OPUS) - B站新版图文格式
@Serializable
data class OpusMajor(
    val jump_url: String = "",
    val pics: List<OpusPic> = emptyList(), // 图片列表
    val summary: OpusSummary? = null, // 文字摘要
    val title: String? = null, // 标题 (可选)
    @Transient
    val contentBlocks: List<OpusContentBlock> = emptyList()
)

sealed interface OpusContentBlock {
    data class Text(
        val text: String,
        val alignment: Int = 0,
        val richTextNodes: List<RichTextNode> = emptyList(),
    ) : OpusContentBlock
    data class Heading(val text: String, val level: Int = 2, val alignment: Int = 0) : OpusContentBlock
    data class Quote(val text: String, val alignment: Int = 0) : OpusContentBlock
    data class ListBlock(
        val items: List<String>,
        val ordered: Boolean,
        val alignment: Int = 0,
    ) : OpusContentBlock
    data class Code(val text: String, val language: String = "") : OpusContentBlock
    data class Divider(val pic: OpusPic? = null) : OpusContentBlock
    data class Image(val pic: OpusPic) : OpusContentBlock
    data class LinkCard(val card: OpusLinkCard) : OpusContentBlock
}

val OpusContentBlock.plainText: String
    get() = when (this) {
        is OpusContentBlock.Text -> text
        is OpusContentBlock.Heading -> text
        is OpusContentBlock.Quote -> text
        is OpusContentBlock.ListBlock -> items.mapIndexed { index, item ->
            if (ordered) "${index + 1}. $item" else "• $item"
        }.joinToString("\n")
        is OpusContentBlock.Code -> text
        is OpusContentBlock.Divider,
        is OpusContentBlock.Image,
        is OpusContentBlock.LinkCard -> ""
    }

@Serializable
data class OpusLinkCard(
    val type: String = "",
    val oid: String = "",
    val title: String = "",
    val description: String = "",
    val label: String = "",
    val cover: String = "",
    val jumpUrl: String = "",
    val badgeText: String = "",
    /**
     * 预约交互载荷。信息流的 `additional.reserve` 与详情 opus 段落里的
     * `link_card.card.reserve` 结构一致，统一承载在这里，让两个入口共用同一套
     * 预约按钮渲染与交互逻辑。
     */
    val reserve: DynamicAdditionalReserve? = null,
    /**
     * 赛事比分行载荷。详情侧此前完全没有解析 match_info，导致同一个赛事卡
     * 在信息流有比分行、进详情后消失。
     */
    val match: DynamicAdditionalMatch? = null,
)

@Serializable
data class OpusPic(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val size: Double = 0.0,
    val live_url: String? = null,
)

@Serializable
data class OpusSummary(
    val text: String = "",
    val rich_text_nodes: List<RichTextNode> = emptyList()
)

@Serializable
data class LiveMajor(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val title: String = "",
    val cover: String = "",
    val jump_url: String = "",
    val desc_first: String = "",
    val desc_second: String = "",
    val live_state: Int = 0,
    val reserve_type: Int = 0,
    val badge: DynamicMajorBadge? = null
)

@Serializable
data class MedialistMajor(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val title: String = "",
    val cover: String = "",
    val jump_url: String = "",
    val sub_title: String = "",
    val badge: DynamicMajorBadge? = null,
)

@Serializable
data class CoursesMajor(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val title: String = "",
    val cover: String = "",
    val jump_url: String = "",
    val desc: String = "",
    val sub_title: String = "",
    val badge: DynamicMajorBadge? = null
)

@Serializable
data class CommonMajor(
    val badge: DynamicMajorBadge? = null,
    val biz_type: Int = 0,
    val cover: String = "",
    val desc: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val jump_url: String = "",
    val label: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val sketch_id: String = "",
    val style: Int = 0,
    val title: String = ""
)

@Serializable
data class MusicMajor(
    val cover: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val jump_url: String = "",
    val label: String = "",
    val title: String = ""
)

@Serializable
data class NoneMajor(
    val tips: String = ""
)

@Serializable
data class UpowerCommonMajor(
    val background: DynamicThemeImage? = null,
    val button: DynamicCardButton? = null,
    val icon: DynamicThemeImage? = null,
    val jump_url: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val rid: Long = 0,
    val title: String = "",
    val title_prefix: String = "",
    val type: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val up_mid: Long = 0,
    val upower_action_state: Int = 0,
    val upower_level: Int = 0
)

@Serializable
data class DynamicThemeImage(
    val dark_src: String = "",
    val light_src: String = ""
)

@Serializable
data class SubscriptionNewMajor(
    val live_rcmd: LiveRcmdMajor? = null
)

//  直播推荐
@Serializable
data class LiveRcmdMajor(
    val content: String = "" // JSON string，需要解析
)

//  [新增] 合集/剧集 (MAJOR_TYPE_UGC_SEASON)
@Serializable
data class UgcSeasonMajor(
    @Serializable(with = FlexibleLongSerializer::class)
    val aid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val duration_text: String = "",
    val jump_url: String = "",
    val intro: String = "",
    val id: Long = 0, // season_id
    val sign_state: Int = 0,
    val type: Int = 0, // 1=合集
    val stat: UgcSeasonStat = UgcSeasonStat(),
    val archive: ArchiveMajor? = null, // 播放第一集或最新一集
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0 // [新增] UP主真实mid
)

@Serializable
data class UgcSeasonStat(
    val play: String = "0",
    val danmaku: String = "0"
)

@Serializable
data class ArchiveMajor(
    val aid: String = "",
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val duration_text: String = "", // "10:24"
    val stat: ArchiveStat = ArchiveStat(),
    val jump_url: String = "",
    val badge: DynamicMajorBadge? = null,
    @SerialName("is_charging_arc")
    val isChargingArc: Boolean = false,
    @SerialName("elec_arc_type")
    val elecArcType: Int = 0,
    @SerialName("is_ugcpay")
    val isUgcpay: Boolean = false,
    @SerialName("ugc_pay")
    val ugcPay: Int = 0,
    @SerialName("ugc_pay_preview")
    val ugcPayPreview: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val epid: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val season_id: Long = 0
)

@Serializable
data class DynamicMajorBadge(
    val text: String = "",
    val color: String = "",
    @SerialName("bg_color")
    val bgColor: String = ""
)

@Serializable
data class ArchiveStat(
    val play: String = "0", // "123.4万"
    val danmaku: String = "0"
)

@Serializable
data class DrawMajor(
    val id: Long = 0,
    val items: List<DrawItem> = emptyList()
)

@Serializable
data class DrawItem(
    val src: String = "", // 图片 URL
    val width: Int = 0,
    val height: Int = 0,
    val live_url: String? = null,
)

@Serializable
data class ArticleMajor(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val title: String = "",
    val desc: String = "",
    val covers: List<String> = emptyList(),
    val jump_url: String = "",
    val label: String = ""
)

// --- 统计模块 ---
@Serializable
data class DynamicStatModule(
    val comment: StatItem = StatItem(),
    val forward: StatItem = StatItem(),
    val like: StatItem = StatItem()
)

@Serializable
data class StatItem(
    val count: Int = 0,
    val forbidden: Boolean = false,
    /** Server-side interaction state. BiliBili may return 0/1 or a boolean. */
    @Serializable(with = FlexibleBooleanSerializer::class)
    val status: Boolean = false,
)

// --- 动态类型枚举 ---
enum class DynamicType(val apiValue: String) {
    VIDEO("DYNAMIC_TYPE_AV"),
    PGC("DYNAMIC_TYPE_PGC"),
    DRAW("DYNAMIC_TYPE_DRAW"),
    WORD("DYNAMIC_TYPE_WORD"),
    FORWARD("DYNAMIC_TYPE_FORWARD"),
    LIVE("DYNAMIC_TYPE_LIVE_RCMD"),
    OPUS("DYNAMIC_TYPE_DRAW"),  //  [新增] 图文动态 (使用 DRAW 类型，但 major 为 opus)
    UGC_SEASON("DYNAMIC_TYPE_UGC_SEASON"), // [新增] 合集/剧集
    UNKNOWN("UNKNOWN");
    
    companion object {
        fun fromApiValue(value: String): DynamicType {
            return entries.find { it.apiValue == value } ?: UNKNOWN
        }
    }
}
