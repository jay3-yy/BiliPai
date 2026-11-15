package com.android.purebilibili.feature.message

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.IdUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class MessageVideoCardPreview(
    val title: String,
    val bvid: String,
    val cover: String,
    val duration: Long,
    val targetUrl: String = "",
)

enum class MessageCardKind(val label: String) {
    Video("视频"),
    Article("专栏"),
    Bangumi("番剧"),
    Live("直播"),
    Dynamic("动态"),
    Image("图片"),
    Share("分享")
}

data class MessageCardPreview(
    val kind: MessageCardKind,
    val title: String,
    val subtitle: String = "",
    val cover: String = "",
    val targetUrl: String = "",
    val bvid: String = "",
    val duration: Long = 0
)

object MessagePreviewParser {

    fun parseSessionPreview(content: String?, msgType: Int): String {
        if (content.isNullOrEmpty()) return ""

        return when (msgType) {
            1 -> parseTextContent(content)
            2 -> "[图片]"
            5 -> "[消息已撤回]"
            6 -> "[表情]"
            7,
            12,
            13,
            14 -> parseMessageCard(content, msgType)?.let { "${it.kind.label}：${it.title}" }
                ?: parseSharePreview(content)
            10 -> "[通知]"
            11 -> parseVideoCard(content)?.let { "视频：${it.title}" } ?: "[视频]"
            else -> "[消息]"
        }
    }

    fun parseMessageCard(content: String, msgType: Int): MessageCardPreview? {
        val json = parseJsonObject(content) ?: return null
        return when (msgType) {
            7 -> parseShareCard(json)
            11 -> parseVideoCard(content)?.let {
                MessageCardPreview(
                    kind = MessageCardKind.Video,
                    title = it.title,
                    cover = it.cover,
                    bvid = it.bvid,
                    duration = it.duration,
                    targetUrl = it.targetUrl,
                )
            }
            12 -> parseArticleCard(json)
            13 -> MessageCardPreview(
                kind = MessageCardKind.Image,
                title = json.firstString("title", "text").ifBlank { "图片卡片" },
                cover = json.firstString("pic_url", "cover", "image"),
                targetUrl = json.firstString("jump_url", "jump_uri", "url")
            )
            14 -> MessageCardPreview(
                kind = resolveShareKind(json.int("source")),
                title = json.firstString("title", "headline").ifBlank { "分享内容" },
                subtitle = json.firstString("desc", "author"),
                cover = json.firstString("cover", "thumb"),
                targetUrl = json.videoTargetUrl(),
                bvid = if (resolveShareKind(json.int("source")) == MessageCardKind.Video) json.videoBvid() else "",
            )
            else -> null
        }
    }

    fun parseVideoCard(content: String): MessageVideoCardPreview? {
        val json = parseJsonObject(content) ?: return null
        val bvid = json.videoBvid()
        val cover = json.string("cover")
        val title = json.string("title").ifBlank {
            if (json.long("times") == 0L) "内容已失效" else "视频"
        }
        val duration = json.long("times")

        if (title.isBlank() && bvid.isBlank() && cover.isBlank()) {
            return null
        }

        return MessageVideoCardPreview(
            title = title,
            bvid = bvid,
            cover = cover,
            duration = duration,
            targetUrl = json.videoTargetUrl(),
        )
    }

    private fun parseSharePreview(content: String): String {
        val json = parseJsonObject(content) ?: return "[分享]"
        return parseShareCard(json)?.let { "${it.kind.label}：${it.title}" } ?: "[分享]"
    }

    private fun parseShareCard(json: JsonObject): MessageCardPreview? {
        val kind = resolveShareKind(json.int("source"))
        val title = json.firstString("title", "headline").ifBlank { kind.label }
        return MessageCardPreview(
            kind = kind,
            title = title,
            subtitle = json.firstString("desc", "author"),
            cover = json.firstString("thumb", "cover", "pic"),
            targetUrl = json.firstString("url", "jump_url", "jump_uri", "all_uri"),
            bvid = if (kind == MessageCardKind.Video) json.videoBvid() else ""
        )
    }

    private fun parseArticleCard(json: JsonObject): MessageCardPreview {
        val cover = json.firstString("cover").ifBlank {
            (json["image_urls"] as? JsonArray)
                ?.firstOrNull()
                ?.let { it as? JsonPrimitive }
                ?.contentOrNull
                .orEmpty()
        }
        return MessageCardPreview(
            kind = MessageCardKind.Article,
            title = json.string("title").ifBlank { "专栏" },
            subtitle = json.firstString("summary", "author"),
            cover = cover,
            targetUrl = json.firstString("url", "jump_url", "jump_uri")
        )
    }

    private fun JsonObject.videoTargetUrl(): String =
        firstString("url", "jump_url", "jump_uri", "all_uri")

    // 视频消息可只携带数字 id；统一转成 BV，保证点击与转场使用同一内容身份。
    private fun JsonObject.videoBvid(): String {
        string("bvid").takeIf { it.isNotBlank() }?.let { return it }
        val aid = long("aid").takeIf { it > 0L } ?: long("id").takeIf { it > 0L }
        if (aid != null) return IdUtils.av2bv(aid)
        val target = BilibiliNavigationTargetParser.parse(videoTargetUrl()) as? BilibiliNavigationTarget.Video
            ?: return ""
        return if (target.videoId.startsWith("BV")) target.videoId else {
            target.videoId.removePrefix("av").removePrefix("AV").toLongOrNull()
                ?.let(IdUtils::av2bv).orEmpty()
        }
    }

    private fun resolveShareKind(source: Int): MessageCardKind {
        return when (source) {
            4 -> MessageCardKind.Live
            5 -> MessageCardKind.Video
            6 -> MessageCardKind.Article
            7, 16, 17 -> MessageCardKind.Bangumi
            11 -> MessageCardKind.Dynamic
            else -> MessageCardKind.Share
        }
    }

    private fun parseTextContent(content: String): String {
        if (!content.trim().startsWith("{")) {
            return content
        }

        return parseJsonObject(content)?.string("content").orEmpty().ifBlank { content }
    }

    private fun parseJsonObject(content: String): JsonObject? {
        return runCatching {
            Json.parseToJsonElement(content) as? JsonObject
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String {
        return (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    private fun JsonObject.firstString(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            string(key).takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun JsonObject.long(key: String): Long {
        return (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
    }

    private fun JsonObject.int(key: String): Int {
        return (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
    }
}
