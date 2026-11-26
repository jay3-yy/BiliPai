package com.android.purebilibili.feature.nearby

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom

internal const val NEARBY_TITLE = "设备接力"
internal const val NEARBY_SERVICE = "_bilipai._tcp."
internal const val NEARBY_PORT = 39327
internal const val NEARBY_PROTOCOL_VERSION = 3

internal const val NEARBY_MEDIA_VIDEO = "video"
internal const val NEARBY_MEDIA_BANGUMI = "bangumi"
internal const val NEARBY_MEDIA_LIVE = "live"

internal const val NEARBY_DEVICE_PHONE = "phone"
internal const val NEARBY_DEVICE_TABLET = "tablet"
internal const val NEARBY_DEVICE_TV = "tv"

internal val nearbyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal enum class NearbyDeviceFormFactor(val wireValue: String, val label: String) {
    PHONE(NEARBY_DEVICE_PHONE, "手机"),
    TABLET(NEARBY_DEVICE_TABLET, "平板"),
    TV(NEARBY_DEVICE_TV, "电视");

    companion object {
        fun fromWire(value: String?): NearbyDeviceFormFactor = when (value?.lowercase()) {
            NEARBY_DEVICE_TABLET -> TABLET
            NEARBY_DEVICE_TV -> TV
            else -> PHONE
        }

        fun resolveLocal(context: Context): NearbyDeviceFormFactor {
            val config = context.resources.configuration
            val uiMode = config.uiMode and Configuration.UI_MODE_TYPE_MASK
            return when {
                uiMode == Configuration.UI_MODE_TYPE_TELEVISION -> TV
                config.smallestScreenWidthDp >= 600 -> TABLET
                else -> PHONE
            }
        }
    }
}

@Serializable
internal data class NearbyPeer(
    val id: String,
    val name: String,
    val pin: String,
    val host: String,
    val port: Int,
    val deviceType: String = NEARBY_DEVICE_PHONE,
) {
    val formFactor: NearbyDeviceFormFactor
        get() = NearbyDeviceFormFactor.fromWire(deviceType)
}

internal const val NEARBY_MAX_QUEUE_ITEMS = 24
internal const val NEARBY_MAX_FRAME_BYTES = 32_768

@Serializable
internal data class NearbyQueueItem(
    val bvid: String = "",
    val cid: Long = 0L,
    val title: String = "",
    val cover: String = "",
    val owner: String = "",
    val duration: Long = 0L,
    val isBangumi: Boolean = false,
    val seasonId: Long = 0L,
    val epId: Long = 0L,
) {
    fun validate() {
        require(title.length <= 120 && owner.length <= 48 && cover.length <= 1024 && duration >= 0L)
        require(cover.isBlank() || URI(cover).let {
            it.scheme == "https" && it.userInfo == null &&
                it.port == -1 && (it.host == "hdslb.com" || it.host?.endsWith(".hdslb.com") == true)
        })
        if (isBangumi) {
            require(seasonId > 0L && epId > 0L)
            require(bvid.isBlank() || Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE).matches(bvid))
            require(cid >= 0L)
        } else {
            require(Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE).matches(bvid))
            require(cid >= 0L)
        }
    }
}

@Serializable
internal data class NearbyQueueContext(
    val items: List<NearbyQueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val playMode: String = "",
    val isExternal: Boolean = false,
    val externalSource: String = "",
) {
    fun validate() {
        require(items.size in 1..NEARBY_MAX_QUEUE_ITEMS)
        require(currentIndex in items.indices)
        require(playMode.length <= 24 && externalSource.length <= 24)
        items.forEach { it.validate() }
    }
}

@Serializable
internal data class NearbyVideo(
    val bvid: String = "",
    val cid: Long = 0L,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val title: String = "",
    val cover: String = "",
    val mediaType: String = NEARBY_MEDIA_VIDEO,
    val seasonId: Long = 0L,
    val epId: Long = 0L,
    val roomId: Long = 0L,
    val uname: String = "",
    val playing: Boolean = true,
    val updatedAtMs: Long = 0L,
    val queue: NearbyQueueContext? = null,
    val qualityId: Int = 0,
    val qualityLabel: String = "",
    val danmakuEnabled: Boolean? = null,
    val audioQualityId: Int = 0,
    val audioQualityLabel: String = "",
    val audioLang: String = "",
    val audioLangLabel: String = "",
    val audioOnly: Boolean = false,
) {
    val isLive: Boolean get() = mediaType == NEARBY_MEDIA_LIVE
    val isBangumi: Boolean get() = mediaType == NEARBY_MEDIA_BANGUMI
    val isVideo: Boolean get() = mediaType == NEARBY_MEDIA_VIDEO

    val effectiveQualityLabel: String
        get() = resolveNearbyQualityLabel(qualityId = qualityId, rawLabel = qualityLabel, isLive = isLive)

    val effectiveAudioQualityLabel: String
        get() = resolveNearbyAudioQualityLabel(audioQualityId = audioQualityId, rawLabel = audioQualityLabel)

    val effectiveAudioLangLabel: String
        get() = audioLangLabel.trim().take(24).ifBlank {
            audioLang.trim().take(16)
        }

    val contentKey: String
        get() = when (mediaType) {
            NEARBY_MEDIA_BANGUMI -> "bangumi:$seasonId:$epId"
            NEARBY_MEDIA_LIVE -> "live:$roomId"
            else -> "video:$bvid:$cid"
        }

    val mediaTypeLabel: String
        get() = when (mediaType) {
            NEARBY_MEDIA_BANGUMI -> "番剧"
            NEARBY_MEDIA_LIVE -> "直播"
            else -> if (audioOnly) "听视频" else "视频"
        }

    fun sameContentAs(other: NearbyVideo): Boolean = contentKey == other.contentKey

    fun validate() {
        require(title.length <= 200 && cover.length <= 1024 && uname.length <= 64)
        require(qualityId in 0..100_000 && qualityLabel.length <= 24)
        require(audioQualityId in 0..100_000 && audioQualityLabel.length <= 24)
        require(audioLang.length <= 16 && audioLangLabel.length <= 24)
        require(cover.isBlank() || URI(cover).let {
            it.scheme == "https" && it.userInfo == null &&
                it.port == -1 && (it.host == "hdslb.com" || it.host?.endsWith(".hdslb.com") == true)
        })
        when (mediaType) {
            NEARBY_MEDIA_VIDEO -> {
                require(Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE).matches(bvid))
                require(cid > 0 && positionMs in 0..604_800_000L)
                require(speed.isFinite() && speed in 0.25f..4f)
            }
            NEARBY_MEDIA_BANGUMI -> {
                require(seasonId > 0L && epId > 0L && positionMs in 0..604_800_000L)
                require(speed.isFinite() && speed in 0.25f..4f)
                require(bvid.isBlank() || Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE).matches(bvid))
                require(cid >= 0L)
            }
            NEARBY_MEDIA_LIVE -> {
                require(roomId > 0L)
                require(positionMs in 0..604_800_000L)
                require(speed.isFinite() && speed in 0.25f..4f)
            }
            else -> throw IllegalArgumentException("不支持的接力内容类型")
        }
        queue?.validate()
    }
}

/** 将 B 站视频/番剧/直播清晰度编号归一化为紧凑可读标签。 */
internal fun resolveNearbyQualityLabel(
    qualityId: Int,
    rawLabel: String = "",
    isLive: Boolean = false,
): String {
    val cleaned = rawLabel.trim().take(24)
    if (cleaned.isNotBlank() && cleaned != "自动") return cleaned
    if (qualityId <= 0) return ""
    return if (isLive) {
        when (qualityId) {
            30000 -> "杜比"
            25000 -> "4K"
            20000 -> "4K"
            10000 -> "原画"
            400 -> "蓝光"
            250 -> "超清"
            150 -> "高清"
            80 -> "流畅"
            else -> ""
        }
    } else {
        when (qualityId) {
            129 -> "HDR Vivid"
            127 -> "8K"
            126 -> "杜比视界"
            125 -> "HDR"
            120 -> "4K"
            116 -> "1080P60"
            112 -> "1080P+"
            100 -> "智能修复"
            80 -> "1080P"
            74 -> "720P60"
            64 -> "720P"
            32 -> "480P"
            16 -> "360P"
            else -> ""
        }
    }
}

/** 将 B 站音轨音质编号归一化为紧凑可读标签（杜比全景声 / Hi-Res / 码率）。 */
internal fun resolveNearbyAudioQualityLabel(
    audioQualityId: Int,
    rawLabel: String = "",
): String {
    val cleaned = rawLabel.trim().take(24)
    if (cleaned.isNotBlank() && cleaned != "自动") return cleaned
    return when (audioQualityId) {
        30250 -> "杜比全景声"
        30251 -> "Hi-Res"
        30280 -> "192K"
        30232 -> "132K"
        30216 -> "64K"
        else -> ""
    }
}

internal fun formatNearbySpeed(speed: Float): String {
    val rounded = kotlin.math.round(speed * 100f) / 100f
    return if (rounded == rounded.toInt().toFloat()) {
        "${rounded.toInt()}x"
    } else {
        val text = "%.2f".format(java.util.Locale.US, rounded).trimEnd('0').trimEnd('.')
        "${text}x"
    }
}

internal fun formatNearbyPreferenceSummary(
    media: NearbyVideo,
    includeSpeed: Boolean = false,
): String {
    val parts = buildList {
        val quality = media.effectiveQualityLabel
        if (quality.isNotBlank()) add(quality)
        val audio = media.effectiveAudioQualityLabel
        if (media.audioQualityId == 30250 || media.audioQualityId == 30251) {
            if (audio.isNotBlank()) add(audio)
        }
        if (media.audioLang.isNotBlank()) {
            val lang = media.effectiveAudioLangLabel
            if (lang.isNotBlank()) add(lang)
        }
        if (includeSpeed && !media.isLive && kotlin.math.abs(media.speed - 1f) >= 0.01f) {
            add(formatNearbySpeed(media.speed))
        }
        media.danmakuEnabled?.let { enabled ->
            add(if (enabled) "弹幕开" else "弹幕关")
        }
    }
    return parts.joinToString(" · ")
}

@Serializable
internal data class NearbyMessage(
    val version: Int = NEARBY_PROTOCOL_VERSION,
    val requestId: String = "",
    val replyTo: String = "",
    val transferId: String = "",
    val kind: String,
    val id: String,
    val name: String = "",
    val deviceType: String = NEARBY_DEVICE_PHONE,
    val token: String = "",
    val video: NearbyVideo? = null,
    val peer: NearbyPeer? = null,
    val accepted: Boolean = false,
)

internal data class NearbyPairCode(val peer: NearbyPeer, val token: String) {
    fun encode(): String = Uri.Builder().scheme("bilipai-pair").authority("v1")
        .appendQueryParameter("host", peer.host).appendQueryParameter("port", peer.port.toString())
        .appendQueryParameter("id", peer.id).appendQueryParameter("name", peer.name)
        .appendQueryParameter("type", peer.formFactor.wireValue)
        .appendQueryParameter("pin", peer.pin).appendQueryParameter("token", token).build().toString()

    companion object {
        fun decode(text: String): NearbyPairCode {
            require(text.length in 1..2048) { "配对码格式不正确" }
            val uri = Uri.parse(text.trim())
            require(uri.scheme == "bilipai-pair" && uri.host == "v1") { "不是 BiliPai 配对码" }
            val host = uri.getQueryParameter("host").orEmpty()
            requireLocalAddress(host)
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
            require(port in 1024..65535) { "连接端口不正确" }
            val pin = uri.getQueryParameter("pin").orEmpty()
            val token = uri.getQueryParameter("token").orEmpty()
            val id = uri.getQueryParameter("id").orEmpty()
            val type = NearbyDeviceFormFactor.fromWire(uri.getQueryParameter("type")).wireValue
            require(Regex("[a-f0-9]{64}").matches(pin) && Regex("[a-f0-9]{32}").matches(token))
            require(id == pin.take(32))
            return NearbyPairCode(
                NearbyPeer(
                    id = id,
                    name = uri.getQueryParameter("name").orEmpty().take(40),
                    pin = pin,
                    host = host,
                    port = port,
                    deviceType = type,
                ),
                token,
            )
        }
    }
}

/** 仅接受 IP 字面量，禁止由收到的配对码触发任意域名解析或公网连接。 */
internal fun requireLocalAddress(host: String) {
    require(host.length in 3..64 && host.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it in ".:" })
    require(host.contains(':') || (host.split('.').size == 4 &&
        host.split('.').all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }))
    val address = InetAddress.getByName(host)
    require(!address.isAnyLocalAddress && !address.isMulticastAddress &&
        (address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)))
}

internal fun ByteArray.nearbyHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun nearbyToken(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.nearbyHex()
internal fun nearbyPin(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).nearbyHex()

internal fun normalizeNearbyCover(cover: String): String = runCatching {
    val normalized = when {
        cover.startsWith("//") -> "https:$cover"
        cover.startsWith("http://") -> "https://" + cover.removePrefix("http://")
        else -> cover
    }
    val uri = URI(normalized)
    normalized.takeIf { it.length <= 1024 && uri.scheme == "https" && uri.userInfo == null &&
        uri.port == -1 && (uri.host == "hdslb.com" || uri.host?.endsWith(".hdslb.com") == true) }.orEmpty()
}.getOrDefault("")

internal fun DataInputStream.readNearby(): NearbyMessage {
    val size = readInt()
    require(size in 1..NEARBY_MAX_FRAME_BYTES) { "接力消息过大" }
    val bytes = ByteArray(size).also(::readFully)
    return nearbyJson.decodeFromString<NearbyMessage>(bytes.toString(Charsets.UTF_8)).also {
        if (it.version != NEARBY_PROTOCOL_VERSION) {
            throw NearbyConnectionFailure("两端设备接力协议不兼容，请使用相同版本")
        }
        require(it.requestId.length <= 32 && it.replyTo.length <= 32 &&
            it.transferId.length <= 32 && it.id.length in 1..64 && it.name.length <= 40 &&
            it.deviceType.length <= 16 && it.token.length <= 64)
    }
}

internal fun DataOutputStream.writeNearby(message: NearbyMessage) {
    val bytes = nearbyJson.encodeToString(NearbyMessage.serializer(), message).toByteArray(Charsets.UTF_8)
    require(bytes.size <= NEARBY_MAX_FRAME_BYTES)
    writeInt(bytes.size)
    write(bytes)
    flush()
}
