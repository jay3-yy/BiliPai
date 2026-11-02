package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.BiliGrpcClient
import com.android.purebilibili.core.network.grpc.ProtoWire
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HtmlEntityUtils
import com.android.purebilibili.data.model.response.ReplyCardLabel
import com.android.purebilibili.data.model.response.ReplyConfig
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyContentUrl
import com.android.purebilibili.data.model.response.ReplyControl
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.CommentMainListContinuation
import com.android.purebilibili.data.model.response.CommentMainListRoute
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyEmote
import com.android.purebilibili.data.model.response.OfficialVerify
import com.android.purebilibili.data.model.response.ReplyFansDetail
import com.android.purebilibili.data.model.response.ReplySailingPendant
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyLevelInfo
import com.android.purebilibili.data.model.response.ReplyPage
import com.android.purebilibili.data.model.response.ReplyPageControl
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.data.model.response.ReplyRichText
import com.android.purebilibili.data.model.response.ReplyRichTextNote
import com.android.purebilibili.data.model.response.ReplyRichTextOpus
import com.android.purebilibili.data.model.response.ReplyTop
import com.android.purebilibili.data.model.response.ReplyUpper
import com.android.purebilibili.data.model.response.ReplyVipInfo
import com.android.purebilibili.data.model.response.ReplyVote
import com.android.purebilibili.data.model.response.ReplyVoteCard
import com.android.purebilibili.data.model.response.ReplyVoteCardOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

// 评论 gRPC 读取为两端共用能力；手机功能层（翻译、排序常量）直接消费。
object CommentGrpcRepository {
    private const val PATH_MAIN_LIST = "/bilibili.main.community.reply.v1.Reply/MainList"
    private const val PATH_DETAIL_LIST = "/bilibili.main.community.reply.v1.Reply/DetailList"
    private const val PATH_DIALOG_LIST = "/bilibili.main.community.reply.v1.Reply/DialogList"
    private const val PATH_TRANSLATE_REPLY = "/bilibili.main.community.reply.v1.Reply/TranslateReply"
    const val MODE_TIME = 2
    const val MODE_HOT = 3

    fun buildMainListRequest(
        oid: Long,
        type: Int,
        mode: Int,
        nextOffset: String?,
    ): ByteArray {
        val fields = mutableListOf(
            ProtoWire.int64(1, oid),
            ProtoWire.int64(2, type.toLong()),
        )
        fields += ProtoWire.int32(9, mode)
        if (!nextOffset.isNullOrBlank()) {
            fields += ProtoWire.bytes(
                10,
                ProtoWire.message(
                    ProtoWire.int32(1, 20),
                    ProtoWire.string(2, nextOffset),
                    ProtoWire.bool(3, false)
                )
            )
        }
        return ProtoWire.message(*fields.toTypedArray())
    }

    fun getMainList(
        oid: Long,
        type: Int,
        mode: Int,
        nextOffset: String?,
    ): Result<ReplyData> {
        if (mode != MODE_TIME && mode != MODE_HOT) {
            return Result.failure(IllegalArgumentException("Unsupported gRPC reply mode: $mode"))
        }
        return runCatching {
            val response = BiliGrpcClient.request(
                path = PATH_MAIN_LIST,
                message = buildMainListRequest(
                    oid = oid,
                    type = type,
                    mode = mode,
                    nextOffset = nextOffset,
                )
            )
            parseMainListReply(response)
        }
    }

    fun buildDetailListRequest(
        oid: Long,
        type: Int,
        root: Long,
        rpid: Long = 0L,
        mode: Int = MODE_TIME,
        nextOffset: String?
    ): ByteArray {
        val fields = mutableListOf(
            ProtoWire.int64(1, oid),
            ProtoWire.int64(2, type.toLong()),
            ProtoWire.int64(3, root),
            ProtoWire.int64(4, rpid),
            ProtoWire.int32(6, 0),
            ProtoWire.int32(7, mode)
        )
        if (!nextOffset.isNullOrBlank()) {
            fields += ProtoWire.bytes(
                8,
                ProtoWire.message(
                    ProtoWire.int32(1, 20),
                    ProtoWire.string(2, nextOffset),
                    ProtoWire.bool(3, false)
                )
            )
        }
        return ProtoWire.message(*fields.toTypedArray())
    }

    fun buildDialogListRequest(
        oid: Long,
        type: Int,
        root: Long,
        dialog: Long,
        nextOffset: String?
    ): ByteArray {
        val fields = mutableListOf(
            ProtoWire.int64(1, oid),
            ProtoWire.int64(2, type.toLong()),
            ProtoWire.int64(3, root),
            ProtoWire.int64(4, dialog)
        )
        if (!nextOffset.isNullOrBlank()) {
            fields += ProtoWire.bytes(
                6,
                ProtoWire.message(
                    ProtoWire.int32(1, 20),
                    ProtoWire.string(2, nextOffset),
                    ProtoWire.bool(3, false)
                )
            )
        }
        return ProtoWire.message(*fields.toTypedArray())
    }

    fun getDetailList(
        oid: Long,
        type: Int,
        root: Long,
        rpid: Long = 0L,
        mode: Int = MODE_TIME,
        nextOffset: String?
    ): Result<ReplyData> {
        return runCatching {
            val response = BiliGrpcClient.request(
                path = PATH_DETAIL_LIST,
                message = buildDetailListRequest(
                    oid = oid,
                    type = type,
                    root = root,
                    rpid = rpid,
                    mode = mode,
                    nextOffset = nextOffset
                )
            )
            parseDetailListReply(response)
        }
    }

    fun getDialogList(
        oid: Long,
        type: Int,
        root: Long,
        dialog: Long,
        nextOffset: String?
    ): Result<ReplyData> {
        return runCatching {
            val response = BiliGrpcClient.request(
                path = PATH_DIALOG_LIST,
                message = buildDialogListRequest(
                    oid = oid,
                    type = type,
                    root = root,
                    dialog = dialog,
                    nextOffset = nextOffset
                )
            )
            parseDialogListReply(response)
        }
    }

    /**
     * 翻译评论 (gRPC TranslateReply)。
     * 返回翻译后的消息文本，失败返回 null。
     */
    suspend fun translateReply(
        type: Long,
        oid: Long,
        rpid: Long
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val request = ProtoWire.message(
                ProtoWire.int64(1, type),
                ProtoWire.int64(2, oid),
                ProtoWire.packedInt64(3, listOf(rpid))
            )
            val response = BiliGrpcClient.request(
                path = PATH_TRANSLATE_REPLY,
                message = request
            )
            Result.success(parseTranslateReplyResp(response, rpid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解析 TranslateReplyResp，提取指定 rpid 的翻译文本。
     * Resp 字段 1 是 map<int64, ReplyInfo>；
     * map entry: 字段 1 = key(rpid), 字段 2 = value(ReplyInfo)；
     * ReplyInfo 字段 17 = translatedContent (Content)，Content 字段 1 = message。
     */
    private fun parseTranslateReplyResp(bytes: ByteArray, targetRpid: Long): String? {
        ProtoWire.parseFields(bytes).forEach { field ->
            if (field.number == 1) {
                var key = 0L
                var translatedMessage: String? = null
                ProtoWire.parseFields(field.bytes).forEach { entryField ->
                    when (entryField.number) {
                        1 -> key = entryField.varint
                        2 -> {
                            // ReplyInfo: 只需字段 17 (translatedContent)
                            ProtoWire.parseFields(entryField.bytes).forEach { replyInfoField ->
                                if (replyInfoField.number == 17) {
                                    // Content: 字段 1 = message
                                    ProtoWire.parseFields(replyInfoField.bytes).forEach { contentField ->
                                        if (contentField.number == 1) {
                                            translatedMessage = HtmlEntityUtils.unescape(
                                                ProtoWire.stringValue(contentField)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (key == targetRpid) return translatedMessage
            }
        }
        return null
    }

    internal fun parseMainListReply(bytes: ByteArray): ReplyData {
        var cursor = ReplyCursor()
        var cursorNext = 0L
        var subject = GrpcSubjectControl()
        var upTop: ReplyItem? = null
        var adminTop: ReplyItem? = null
        var voteTop: ReplyItem? = null
        var voteCard: ReplyVoteCard? = null
        val topReplies = mutableListOf<ReplyItem>()
        val replies = mutableListOf<ReplyItem>()
        var paginationNextOffset = ""

        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> {
                    cursor = parseCursorReply(field.bytes)
                    cursorNext = ProtoWire.parseFields(field.bytes)
                        .firstOrNull { it.number == 1 }?.varint ?: 0L
                }
                2 -> replies += parseReplyInfo(field.bytes)
                3 -> subject = parseSubjectControl(field.bytes)
                4 -> upTop = parseReplyInfo(field.bytes)
                5 -> adminTop = parseReplyInfo(field.bytes)
                6 -> voteTop = parseReplyInfo(field.bytes)
                14 -> topReplies += parseReplyInfo(field.bytes)
                20 -> paginationNextOffset = parseFeedPaginationReplyNextOffset(field.bytes)
                23 -> voteCard = parseVoteCard(field.bytes)
            }
        }

        return ReplyData(
            cursor = cursor.copy(allCount = subject.count.toInt()),
            config = ReplyConfig(showUpFlag = subject.showUpAction),
            replies = replies,
            topReplies = topReplies,
            top = ReplyTop(
                admin = adminTop,
                upper = upTop,
                vote = voteTop
            ),
            upper = ReplyUpper(
                mid = subject.upMid,
                top = upTop
            ),
            control = ReplyPageControl(
                title = subject.title,
                backgroundText = subject.backgroundText,
                emptyText = subject.emptyText,
                emptyHighlightText = subject.emptyHighlightText,
                inputDisable = subject.inputDisable,
                rootInputText = subject.rootText,
                childInputText = subject.childText,
                uploadPictureIconState = subject.uploadPictureIconState
            ),
            voteCard = voteCard,
            grpcNextOffset = paginationNextOffset,
            mainListContinuation = if (cursor.isEnd) null else CommentMainListContinuation(
                route = CommentMainListRoute.GRPC,
                next = cursorNext,
                offset = paginationNextOffset.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun parseVoteCard(bytes: ByteArray): ReplyVoteCard? {
        var voteId = 0L
        var title = ""
        var count = 0L
        var myVoteOption: Long? = null
        val options = mutableListOf<ReplyVoteCardOption>()
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> voteId = field.varint
                2 -> title = ProtoWire.stringValue(field)
                3 -> count = field.varint
                4 -> {
                    var index = 0L
                    var description = ""
                    var optionCount = 0L
                    ProtoWire.parseFields(field.bytes).forEach { optionField ->
                        when (optionField.number) {
                            1 -> index = optionField.varint
                            2 -> description = ProtoWire.stringValue(optionField)
                            3 -> optionCount = optionField.varint
                        }
                    }
                    options += ReplyVoteCardOption(index, description, optionCount)
                }
                5 -> myVoteOption = field.varint
            }
        }
        return voteId.takeIf { it > 0L }?.let {
            ReplyVoteCard(it, title, count, options, myVoteOption)
        }
    }

    internal fun parseDetailListReply(bytes: ByteArray): ReplyData {
        var cursor = ReplyCursor()
        var subject = GrpcSubjectControl()
        var root = ReplyItem()
        var paginationNextOffset = ""

        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> cursor = parseCursorReply(field.bytes)
                2 -> subject = parseSubjectControl(field.bytes)
                3 -> root = parseReplyInfo(field.bytes)
                8 -> paginationNextOffset = parseFeedPaginationReplyNextOffset(field.bytes)
            }
        }

        val detailCount = root.rcount
            .takeIf { it > 0 }
            ?: root.count.takeIf { it > 0 }
            ?: subject.count.toInt()

        return ReplyData(
            cursor = cursor.copy(allCount = detailCount),
            page = ReplyPage(count = detailCount),
            config = ReplyConfig(showUpFlag = subject.showUpAction),
            replies = root.replies.orEmpty(),
            root = root,
            control = ReplyPageControl(
                title = subject.title,
                backgroundText = subject.backgroundText,
                emptyText = subject.emptyText,
                emptyHighlightText = subject.emptyHighlightText,
                inputDisable = subject.inputDisable,
                rootInputText = subject.rootText,
                childInputText = subject.childText,
                uploadPictureIconState = subject.uploadPictureIconState
            ),
            grpcNextOffset = paginationNextOffset
        )
    }

    internal fun parseDialogListReply(bytes: ByteArray): ReplyData {
        var cursor = ReplyCursor()
        var subject = GrpcSubjectControl()
        val replies = mutableListOf<ReplyItem>()
        var paginationNextOffset = ""

        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> cursor = parseCursorReply(field.bytes)
                2 -> subject = parseSubjectControl(field.bytes)
                3 -> replies += parseReplyInfo(field.bytes)
                5 -> paginationNextOffset = parseFeedPaginationReplyNextOffset(field.bytes)
            }
        }

        return ReplyData(
            cursor = cursor.copy(allCount = subject.count.toInt()),
            config = ReplyConfig(showUpFlag = subject.showUpAction),
            replies = replies,
            control = ReplyPageControl(
                title = subject.title,
                backgroundText = subject.backgroundText,
                emptyText = subject.emptyText,
                emptyHighlightText = subject.emptyHighlightText,
                inputDisable = subject.inputDisable,
                rootInputText = subject.rootText,
                childInputText = subject.childText,
                uploadPictureIconState = subject.uploadPictureIconState
            ),
            grpcNextOffset = paginationNextOffset
        )
    }

    private fun parseCursorReply(bytes: ByteArray): ReplyCursor {
        var next = 0
        var isEnd = false
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> next = field.varint.toInt()
                4 -> isEnd = field.varint != 0L
            }
        }
        return ReplyCursor(next = next, isEnd = isEnd)
    }

    private fun parseFeedPaginationReplyNextOffset(bytes: ByteArray): String {
        var nextOffset = ""
        ProtoWire.parseFields(bytes).forEach { field ->
            if (field.number == 1) nextOffset = ProtoWire.stringValue(field)
        }
        return nextOffset
    }

    private fun parseSubjectControl(bytes: ByteArray): GrpcSubjectControl {
        var control = GrpcSubjectControl()
        ProtoWire.parseFields(bytes).forEach { field ->
            control = when (field.number) {
                1 -> control.copy(upMid = field.varint)
                7 -> control.copy(backgroundText = ProtoWire.stringValue(field))
                17 -> control.copy(title = ProtoWire.stringValue(field))
                21 -> control.copy(emptyText = ProtoWire.stringValue(field))
                22 -> control.copy(emptyHighlightText = ProtoWire.stringValue(field))
                11 -> control.copy(showUpAction = field.varint != 0L)
                13 -> control.copy(inputDisable = field.varint != 0L)
                14 -> control.copy(rootText = ProtoWire.stringValue(field))
                15 -> control.copy(childText = ProtoWire.stringValue(field))
                16 -> control.copy(count = field.varint)
                26 -> control.copy(uploadPictureIconState = field.varint.toInt())
                else -> control
            }
        }
        return control
    }

    private fun parseReplyInfo(bytes: ByteArray): ReplyItem {
        val replies = mutableListOf<ReplyItem>()
        var id = 0L
        var oid = 0L
        var type = 0L
        var mid = 0L
        var root = 0L
        var parent = 0L
        var dialog = 0L
        var like = 0
        var ctime = 0L
        var count = 0
        var content = ReplyContent()
        var member = com.android.purebilibili.data.model.response.ReplyMember()
        var control = GrpcReplyControl()

        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> replies += parseReplyInfo(field.bytes)
                2 -> id = field.varint
                3 -> oid = field.varint
                4 -> type = field.varint
                5 -> mid = field.varint
                6 -> root = field.varint
                7 -> parent = field.varint
                8 -> dialog = field.varint
                9 -> like = field.varint.toInt()
                10 -> ctime = field.varint
                11 -> count = field.varint.toInt()
                12 -> content = parseContent(field.bytes)
                13 -> member = parseMember(field.bytes)
                14 -> control = parseReplyControl(field.bytes)
            }
        }

        return ReplyItem(
            rpid = id,
            root = root,
            oid = oid,
            mid = mid,
            count = count,
            rcount = count,
            like = like,
            ctime = ctime,
            action = control.action.toInt(),
            member = member,
            content = content,
            replies = replies,
            cardLabels = control.cardLabels,
            replyControl = control.replyControl,
            parent = parent,
            dialog = dialog,
            replyType = if (type != 0L) type.toInt() else 1
        )
    }

    private fun parseMember(bytes: ByteArray): com.android.purebilibili.data.model.response.ReplyMember {
        var mid = 0L
        var name = ""
        var face = ""
        var level = 0
        var vipType = 0
        var vipStatus = 0
        var officialVerifyType: Int? = null
        var garbPendantImage = ""
        var garbCardImage = ""
        var garbCardImageWithFocus = ""
        var garbCardNumber = ""
        var garbCardFanColor = ""
        var garbCardIsFan = 0
        var fansMedalName = ""
        var fansMedalLevel = 0
        var fansMedalColor = 0L
        var fansMedalColorName = 0L
        var isSeniorMember = 0

        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> mid = field.varint
                2 -> name = ProtoWire.stringValue(field)
                4 -> face = ProtoWire.stringValue(field)
                5 -> level = field.varint.toInt()
                6 -> officialVerifyType = field.varint.toInt()
                7 -> vipType = field.varint.toInt()
                8 -> vipStatus = field.varint.toInt()
                11 -> garbPendantImage = ProtoWire.stringValue(field)
                12 -> garbCardImage = ProtoWire.stringValue(field)
                13 -> garbCardImageWithFocus = ProtoWire.stringValue(field)
                15 -> garbCardNumber = ProtoWire.stringValue(field)
                16 -> garbCardFanColor = ProtoWire.stringValue(field)
                17 -> garbCardIsFan = if (field.varint != 0L) 1 else 0
                18 -> fansMedalName = ProtoWire.stringValue(field)
                19 -> fansMedalLevel = field.varint.toInt()
                20 -> fansMedalColor = field.varint
                27 -> fansMedalColorName = field.varint
                32 -> isSeniorMember = field.varint.toInt()
            }
        }

        return com.android.purebilibili.data.model.response.ReplyMember(
            mid = mid.toString(),
            uname = name.ifBlank { "未知用户" },
            avatar = face,
            isSeniorMember = isSeniorMember,
            levelInfo = ReplyLevelInfo(currentLevel = level),
            vip = ReplyVipInfo(vipType = vipType, vipStatus = vipStatus),
            officialVerify = OfficialVerify(type = officialVerifyType ?: -1),
            pendant = garbPendantImage.takeIf { it.isNotBlank() }?.let {
                ReplySailingPendant(image = it)
            },
            fansDetail = if (fansMedalName.isNotBlank() && fansMedalLevel > 0) {
                ReplyFansDetail(
                    uid = mid,
                    medalName = fansMedalName,
                    level = fansMedalLevel,
                    medalColor = fansMedalColor,
                    medalColorName = fansMedalColorName,
                )
            } else {
                null
            },
            garbCardImage = garbCardImage,
            garbCardImageWithFocus = garbCardImageWithFocus,
            garbCardNumber = garbCardNumber,
            garbCardFanColor = garbCardFanColor,
            garbCardIsFan = garbCardIsFan
        )
    }

    private fun parseContent(bytes: ByteArray): ReplyContent {
        var message = ""
        val emotes = mutableMapOf<String, ReplyEmote>()
        val topics = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        val urls = mutableMapOf<String, ReplyContentUrl>()
        val atNameToMid = mutableMapOf<String, Long>()
        var vote: ReplyVote? = null
        var richText = ReplyRichText()
        val pictures = mutableListOf<ReplyPicture>()

        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> message = HtmlEntityUtils.unescape(ProtoWire.stringValue(field))
                3 -> parseMapEntry(field.bytes) { key, value ->
                    parseEmote(value, key)?.let { emotes[key.ifBlank { it.text }] = it }
                }
                4 -> parseMapEntry(field.bytes) { key, _ ->
                    if (key.isNotBlank()) topics[key] = JsonObject(emptyMap())
                }
                5 -> parseMapEntry(field.bytes) { key, value ->
                    urls[key] = parseUrl(value, key)
                }
                6 -> vote = parseVote(field.bytes)
                7 -> parseLongMapEntry(field.bytes) { key, value ->
                    if (key.isNotBlank() && value > 0L) atNameToMid[key] = value
                }
                8 -> richText = parseRichText(field.bytes)
                9 -> pictures += parsePicture(field.bytes)
            }
        }

        return ReplyContent(
            message = message,
            emote = emotes.takeIf { it.isNotEmpty() },
            vote = vote,
            richText = richText,
            urls = urls,
            topics = topics,
            atNameToMid = atNameToMid,
            pictures = pictures.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseEmote(bytes: ByteArray, fallbackText: String): ReplyEmote? {
        var id = 0L
        var url = ""
        var gifUrl = ""
        var text = fallbackText
        var webpUrl = ""
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                2 -> url = ProtoWire.stringValue(field)
                5 -> id = field.varint
                7 -> gifUrl = ProtoWire.stringValue(field)
                8 -> text = ProtoWire.stringValue(field)
                9 -> webpUrl = ProtoWire.stringValue(field)
            }
        }
        val resolvedUrl = listOf(webpUrl, gifUrl, url).firstOrNull { it.isNotBlank() }.orEmpty()
        return if (resolvedUrl.isBlank() && text.isBlank()) {
            null
        } else {
            ReplyEmote(id = id, text = text, url = resolvedUrl)
        }
    }

    private fun parseUrl(bytes: ByteArray, token: String): ReplyContentUrl {
        var title = ""
        var prefixIcon = ""
        var appUrlSchema = ""
        var pcUrl = ""
        var isGoods = false
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> title = ProtoWire.stringValue(field)
                3 -> prefixIcon = ProtoWire.stringValue(field)
                4 -> appUrlSchema = ProtoWire.stringValue(field)
                10 -> isGoods = ProtoWire.parseFields(field.bytes).any { extra ->
                    extra.number == 1 || extra.number == 2 || (extra.number == 5 && extra.varint == 1L)
                }
                13 -> pcUrl = ProtoWire.stringValue(field)
            }
        }
        return ReplyContentUrl(
            title = title,
            url = pcUrl.ifBlank { token },
            appUrlSchema = appUrlSchema,
            prefixIcon = prefixIcon,
            isGoods = isGoods
        )
    }

    private fun parseVote(bytes: ByteArray): ReplyVote {
        var id = 0L
        var title = ""
        var count = 0L
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> id = field.varint
                2 -> title = ProtoWire.stringValue(field)
                3 -> count = field.varint
            }
        }
        return ReplyVote(id = id, title = title, count = count)
    }

    private fun parseRichText(bytes: ByteArray): ReplyRichText {
        var note: ReplyRichTextNote? = null
        var opus: ReplyRichTextOpus? = null
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> note = parseRichTextNote(field.bytes)
                2 -> opus = parseRichTextOpus(field.bytes)
            }
        }
        return ReplyRichText(note = note, opus = opus)
    }

    private fun parseRichTextNote(bytes: ByteArray): ReplyRichTextNote {
        var summary = ""
        var clickUrl = ""
        var lastMtimeText = ""
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> summary = ProtoWire.stringValue(field)
                3 -> clickUrl = ProtoWire.stringValue(field)
                4 -> lastMtimeText = ProtoWire.stringValue(field)
            }
        }
        return ReplyRichTextNote(summary = summary, clickUrl = clickUrl, lastMtimeText = lastMtimeText)
    }

    private fun parseRichTextOpus(bytes: ByteArray): ReplyRichTextOpus {
        var opusId = 0L
        var oid = 0L
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> opusId = field.varint
                3 -> oid = field.varint
            }
        }
        return ReplyRichTextOpus(opusId = opusId, oid = oid)
    }

    private fun parsePicture(bytes: ByteArray): ReplyPicture {
        var src = ""
        var width = 0
        var height = 0
        var size = 0f
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> src = ProtoWire.stringValue(field)
                2 -> width = ProtoWire.doubleValue(field).toInt()
                3 -> height = ProtoWire.doubleValue(field).toInt()
                4 -> size = ProtoWire.doubleValue(field).toFloat()
            }
        }
        return ReplyPicture(imgSrc = src, imgWidth = width, imgHeight = height, imgSize = size)
    }

    private fun parseReplyControl(bytes: ByteArray): GrpcReplyControl {
        var action = 0L
        var upReply = false
        var isUpTop = false
        var location = ""
        var translationSwitch = 0
        var chargedDesc = ""
        val labels = mutableListOf<ReplyCardLabel>()
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> action = field.varint
                3 -> upReply = field.varint != 0L
                12 -> isUpTop = field.varint != 0L
                19 -> labels += parseCardLabel(field.bytes)
                25 -> location = ProtoWire.stringValue(field)
                31 -> chargedDesc = ProtoWire.stringValue(field)
                37 -> translationSwitch = field.varint.toInt()
            }
        }
        return GrpcReplyControl(
            action = action,
            replyControl = ReplyControl(
                location = location,
                isUpTop = isUpTop,
                upReply = upReply,
                translationSwitch = translationSwitch,
                chargedDesc = chargedDesc
            ),
            cardLabels = labels.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseCardLabel(bytes: ByteArray): ReplyCardLabel {
        var text = ""
        var color = ""
        var jumpUrl = ""
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> text = ProtoWire.stringValue(field)
                4 -> color = ProtoWire.stringValue(field)
                11 -> jumpUrl = ProtoWire.stringValue(field)
            }
        }
        return ReplyCardLabel(textContent = text, labelColor = color, jumpUrl = jumpUrl)
    }

    private fun parseMapEntry(bytes: ByteArray, onEntry: (String, ByteArray) -> Unit) {
        var key = ""
        var value = ByteArray(0)
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> key = ProtoWire.stringValue(field)
                2 -> value = field.bytes
            }
        }
        if (key.isNotBlank() || value.isNotEmpty()) onEntry(key, value)
    }

    private fun parseLongMapEntry(bytes: ByteArray, onEntry: (String, Long) -> Unit) {
        var key = ""
        var value = 0L
        ProtoWire.parseFields(bytes).forEach { field ->
            when (field.number) {
                1 -> key = ProtoWire.stringValue(field)
                2 -> value = field.varint
            }
        }
        if (key.isNotBlank()) onEntry(key, value)
    }

    private data class GrpcReplyControl(
        val action: Long = 0L,
        val replyControl: ReplyControl = ReplyControl(),
        val cardLabels: List<ReplyCardLabel>? = null
    )

    private data class GrpcSubjectControl(
        val title: String = "",
        val backgroundText: String = "",
        val emptyText: String = "",
        val emptyHighlightText: String = "",
        val upMid: Long = 0L,
        val showUpAction: Boolean = false,
        val inputDisable: Boolean = false,
        val rootText: String = "",
        val childText: String = "",
        val count: Long = 0L,
        val uploadPictureIconState: Int = 0
    )
}
