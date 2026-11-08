package com.android.purebilibili.core.subtitle

import com.android.purebilibili.data.model.response.SubtitleItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val content: String,
    /** AI 逐字字幕提供的词级时间戳；普通 CC 字幕为空。 */
    val words: List<SubtitleWordSpan> = emptyList()
)

data class SubtitleWordSpan(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class SubtitleLoadResult(
    val track: SubtitleTrackMeta,
    val cues: List<SubtitleCue>
)

data class SubtitleTrackMeta(
    val id: Long = 0L,
    val idStr: String = "",
    val lan: String,
    val lanDoc: String,
    val subtitleUrl: String,
    val aiStatus: Int = 0,
    val aiType: Int = 0,
    val type: Int = 0
) {
    val trackKey: String
        get() = buildSubtitleTrackKey(
            subtitleId = id,
            subtitleIdStr = idStr,
            languageCode = lan,
            subtitleUrl = subtitleUrl
        )
}

data class SubtitleTrackOption(
    val trackKey: String,
    val languageCode: String,
    val label: String,
    val selected: Boolean,
    val likelyAi: Boolean
)

data class SubtitleLanguageSelection(
    val primaryLanguage: String?,
    val secondaryLanguage: String?
)

enum class SubtitleDisplayMode {
    OFF,
    PRIMARY_ONLY,
    SECONDARY_ONLY,
    BILINGUAL
}

data class SubtitleDisplayOption(
    val mode: SubtitleDisplayMode,
    val label: String,
    val enabled: Boolean
)

data class SubtitlePreferenceSession(
    val key: String,
    val initialMode: SubtitleDisplayMode
)

data class SubtitleControlAvailability(
    val trackAvailable: Boolean,
    val primarySelectable: Boolean,
    val secondarySelectable: Boolean
)

data class SubtitleTextSizeSpec(
    val primarySp: Int,
    val secondarySp: Int
)

enum class SubtitleAutoPreference {
    OFF,
    ON,
    WITHOUT_AI,
    AUTO,
    /** 只自动启用中文字幕（含 AI 中文），没有中文字幕时不开启。 */
    CHINESE_ONLY
}

private val SUBTITLE_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun normalizeBilibiliSubtitleUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> ""
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
        else -> trimmed
    }
}

fun isTrustedBilibiliSubtitleUrl(raw: String): Boolean {
    val normalized = normalizeBilibiliSubtitleUrl(raw)
    if (normalized.isBlank()) return false
    val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank()) return false

    val trustedHost = host == "hdslb.com" ||
        host.endsWith(".hdslb.com") ||
        host == "bilibili.com" ||
        host.endsWith(".bilibili.com")
    if (!trustedHost) return false

    val path = uri.path?.lowercase().orEmpty()
    return path.contains("subtitle")
}

fun isLikelyAiSubtitleTrack(track: SubtitleTrackMeta): Boolean {
    if (track.aiStatus > 0 || track.aiType > 0) return true
    val label = track.lanDoc.lowercase()
    return label.contains("ai") ||
        label.contains("自动") ||
        label.contains("机翻") ||
        label.contains("机器")
}

fun buildSubtitleTrackKey(
    subtitleId: Long,
    subtitleIdStr: String,
    languageCode: String?,
    subtitleUrl: String
): String {
    return listOf(
        subtitleId.coerceAtLeast(0L).toString(),
        subtitleIdStr.trim(),
        languageCode?.trim().orEmpty(),
        normalizeBilibiliSubtitleUrl(subtitleUrl)
    ).joinToString("|")
}

fun mapPlayerInfoSubtitleTracks(subtitles: List<SubtitleItem>): List<SubtitleTrackMeta> {
    return orderSubtitleTracksByPreference(
        subtitles.mapNotNull { item ->
            val normalizedUrl = normalizeBilibiliSubtitleUrl(item.subtitleUrl)
            if (!isTrustedBilibiliSubtitleUrl(normalizedUrl)) return@mapNotNull null
            SubtitleTrackMeta(
                id = item.id,
                idStr = item.idStr,
                lan = item.lan,
                lanDoc = item.lanDoc,
                subtitleUrl = normalizedUrl,
                aiStatus = item.aiStatus,
                aiType = item.aiType,
                type = item.type
            )
        }.distinctBy { meta -> meta.trackKey }
    )
}

fun resolveSubtitleTrackDisplayLabel(track: SubtitleTrackMeta): String {
    val baseLabel = track.lanDoc.trim().ifBlank { track.lan.trim() }
    val aiSuffix = if (isLikelyAiSubtitleTrack(track) &&
        !baseLabel.contains("AI", ignoreCase = true) &&
        !baseLabel.contains("自动") &&
        !baseLabel.contains("机翻")
    ) {
        " · AI"
    } else {
        ""
    }
    return (baseLabel + aiSuffix).ifBlank { "未知字幕" }
}

fun buildSubtitleTrackOptions(
    tracks: List<SubtitleTrackMeta>,
    selectedTrackKey: String?
): List<SubtitleTrackOption> {
    return tracks.map { track ->
        SubtitleTrackOption(
            trackKey = track.trackKey,
            languageCode = track.lan,
            label = resolveSubtitleTrackDisplayLabel(track),
            selected = track.trackKey == selectedTrackKey,
            likelyAi = isLikelyAiSubtitleTrack(track)
        )
    }
}

fun normalizeSubtitleVerticalOffsetFraction(value: Float): Float {
    return value.coerceIn(-0.30f, 0.30f)
}

fun resolveSubtitleTextSizeSpec(
    playerWidthDp: Int,
    largeTextEnabled: Boolean
): SubtitleTextSizeSpec {
    return when {
        playerWidthDp >= 840 -> SubtitleTextSizeSpec(
            primarySp = if (largeTextEnabled) 26 else 22,
            secondarySp = if (largeTextEnabled) 23 else 20
        )
        playerWidthDp >= 600 -> SubtitleTextSizeSpec(
            primarySp = if (largeTextEnabled) 24 else 20,
            secondarySp = if (largeTextEnabled) 21 else 18
        )
        else -> SubtitleTextSizeSpec(
            primarySp = if (largeTextEnabled) 18 else 16,
            secondarySp = if (largeTextEnabled) 16 else 14
        )
    }
}

private fun subtitleTrackPreferenceScore(track: SubtitleTrackMeta): Int {
    var score = 0
    if (!isLikelyAiSubtitleTrack(track)) score += 100
    if (isTrustedBilibiliSubtitleUrl(track.subtitleUrl)) score += 20
    if (track.lanDoc.contains("官方")) score += 8
    if (track.id > 0L) score += 1
    return score
}

fun orderSubtitleTracksByPreference(tracks: List<SubtitleTrackMeta>): List<SubtitleTrackMeta> {
    if (tracks.size <= 1) return tracks
    return tracks.sortedWith(
        compareByDescending<SubtitleTrackMeta> { subtitleTrackPreferenceScore(it) }
            .thenByDescending { it.id }
            .thenBy { it.lanDoc }
    )
}

fun parseBiliSubtitleBody(rawJson: String): List<SubtitleCue> {
    if (rawJson.isBlank()) return emptyList()
    return try {
        val root = SUBTITLE_JSON.parseToJsonElement(rawJson).jsonObject
        val body = root["body"]?.asJsonArrayOrNull().orEmpty()
        body.mapNotNull { item ->
            val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
            val fromSeconds = obj["from"].asDoubleOrNull() ?: return@mapNotNull null
            val toSeconds = obj["to"].asDoubleOrNull() ?: return@mapNotNull null
            val content = (obj["content"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (content.isBlank() || !fromSeconds.isFinite() || !toSeconds.isFinite() || toSeconds <= fromSeconds) {
                return@mapNotNull null
            }
            val startMs = (fromSeconds * 1000.0).toLong().coerceAtLeast(0L)
            val endMs = (toSeconds * 1000.0).toLong().coerceAtLeast(startMs)
            SubtitleCue(
                startMs = startMs,
                endMs = endMs,
                content = content,
                words = obj["words"].asJsonArrayOrNull().orEmpty().mapNotNull { wordElement ->
                    val wordObject = wordElement.asJsonObjectOrNull() ?: return@mapNotNull null
                    val wordText = listOf("word", "text", "content")
                        .firstNotNullOfOrNull { key ->
                            (wordObject[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
                        }
                        ?: return@mapNotNull null
                    val startSeconds = wordObject["start"].asDoubleOrNull()
                        ?: wordObject["start_time"].asDoubleOrNull()
                        ?: return@mapNotNull null
                    val endSeconds = wordObject["end"].asDoubleOrNull()
                        ?: wordObject["end_time"].asDoubleOrNull()
                        ?: return@mapNotNull null
                    val timing = resolveSubtitleWordTiming(startSeconds, endSeconds, startMs, endMs)
                        ?: return@mapNotNull null
                    SubtitleWordSpan(
                        text = wordText,
                        startMs = timing.first,
                        endMs = timing.second,
                    )
                }
            )
        }.sortedBy { cue -> cue.startMs }
    } catch (_: Throwable) {
        emptyList()
    }
}

/** 秒与毫秒候选必须落在所属句内；异常词时间不参与高亮，轻微越界截到句边界。 */
private fun resolveSubtitleWordTiming(
    start: Double,
    end: Double,
    cueStartMs: Long,
    cueEndMs: Long,
): Pair<Long, Long>? {
    if (!start.isFinite() || !end.isFinite() || end <= start || cueEndMs <= cueStartMs) return null
    val candidates = listOf(
        (start * 1000.0).toLong() to (end * 1000.0).toLong(),
        start.toLong() to end.toLong(),
    ).filter { (from, to) -> to > from }
    val timing = candidates.firstOrNull { (from, to) -> from >= cueStartMs && to <= cueEndMs }
        ?: candidates.firstOrNull { (from, to) -> from < cueEndMs && to > cueStartMs }
        ?: return null
    return maxOf(timing.first, cueStartMs) to minOf(timing.second, cueEndMs)
}

/**
 * B 站 AI 字幕的 lan 形如 ai-zh / ai-en，真实语言码在 ai- 之后。
 * 归一化后才能与普通轨道按同一语言家族比较。
 */
private fun stripSubtitleAiLanguagePrefix(languageCode: String?): String {
    val normalized = languageCode?.trim()?.lowercase().orEmpty()
    if (normalized.isEmpty()) return ""
    return normalized.removePrefix("ai-").ifBlank { normalized }
}

private fun isChineseSubtitleLanguage(languageCode: String?, languageLabel: String? = null): Boolean {
    if (stripSubtitleAiLanguagePrefix(languageCode).startsWith("zh")) return true
    // 少数接口只给 lan_doc，没有可用的语言码
    val label = languageLabel.orEmpty()
    return label.contains("中文") || label.contains("汉语") || label.contains("简体")
}

private fun resolveLanguageFamily(languageCode: String?): String? {
    val normalized = stripSubtitleAiLanguagePrefix(languageCode).takeIf { it.isNotBlank() } ?: return null
    return normalized.substringBefore('-').lowercase()
}

private fun findTrackByPreferredLanguage(
    tracks: List<SubtitleTrackMeta>,
    preferredLanguage: String?
): SubtitleTrackMeta? {
    val preferred = preferredLanguage?.trim()?.takeIf { it.isNotBlank() } ?: return null
    tracks.firstOrNull { track ->
        track.lan.equals(preferred, ignoreCase = true)
    }?.let { return it }

    val preferredFamily = resolveLanguageFamily(preferred) ?: return null
    return tracks.firstOrNull { track ->
        resolveLanguageFamily(track.lan) == preferredFamily
    }
}

fun resolveDefaultSubtitleLanguages(
    tracks: List<SubtitleTrackMeta>,
    preferredPrimaryLanguage: String? = null
): SubtitleLanguageSelection {
    if (tracks.isEmpty()) {
        return SubtitleLanguageSelection(
            primaryLanguage = null,
            secondaryLanguage = null
        )
    }

    // 中文字幕（含 AI 中文）优先于其它语言；中文内部官方优先于 AI。
    val primary = tracks.firstOrNull { track ->
        isChineseSubtitleLanguage(track.lan, track.lanDoc) && !isLikelyAiSubtitleTrack(track)
    } ?: tracks.firstOrNull { track ->
        isChineseSubtitleLanguage(track.lan, track.lanDoc)
    } ?: findTrackByPreferredLanguage(
        tracks = tracks,
        preferredLanguage = preferredPrimaryLanguage
    ) ?: tracks.first()

    val englishSecondary = tracks.firstOrNull { track ->
        track.lan.equals("en-US", ignoreCase = true)
    } ?: tracks.firstOrNull { track ->
        track.lan.equals("en-GB", ignoreCase = true)
    } ?: tracks.firstOrNull { track ->
        track.lan.startsWith("en", ignoreCase = true)
    }
    val secondary = englishSecondary?.takeIf { it.lan != primary.lan } ?: tracks.firstOrNull { track ->
        track.lan != primary.lan
    }

    return SubtitleLanguageSelection(
        primaryLanguage = primary.lan,
        secondaryLanguage = secondary?.lan?.takeIf { it != primary.lan }
    )
}

fun resolveDefaultSubtitleDisplayMode(
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): SubtitleDisplayMode = when {
    hasPrimaryTrack && hasSecondaryTrack -> SubtitleDisplayMode.BILINGUAL
    hasPrimaryTrack -> SubtitleDisplayMode.PRIMARY_ONLY
    hasSecondaryTrack -> SubtitleDisplayMode.SECONDARY_ONLY
    else -> SubtitleDisplayMode.OFF
}

fun normalizeSubtitleDisplayMode(
    preferredMode: SubtitleDisplayMode,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): SubtitleDisplayMode {
    if (!hasPrimaryTrack && !hasSecondaryTrack) {
        return SubtitleDisplayMode.OFF
    }
    return when (preferredMode) {
        SubtitleDisplayMode.OFF -> SubtitleDisplayMode.OFF
        SubtitleDisplayMode.PRIMARY_ONLY -> {
            if (hasPrimaryTrack) SubtitleDisplayMode.PRIMARY_ONLY else SubtitleDisplayMode.SECONDARY_ONLY
        }
        SubtitleDisplayMode.SECONDARY_ONLY -> {
            if (hasSecondaryTrack) SubtitleDisplayMode.SECONDARY_ONLY else SubtitleDisplayMode.PRIMARY_ONLY
        }
        SubtitleDisplayMode.BILINGUAL -> {
            resolveDefaultSubtitleDisplayMode(
                hasPrimaryTrack = hasPrimaryTrack,
                hasSecondaryTrack = hasSecondaryTrack
            )
        }
    }
}

fun resolveSubtitleDisplayModeByAutoPreference(
    preference: SubtitleAutoPreference,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean,
    primaryTrackLikelyAi: Boolean,
    secondaryTrackLikelyAi: Boolean,
    isMuted: Boolean,
    primaryTrackLanguage: String?,
    secondaryTrackLanguage: String?
): SubtitleDisplayMode {
    val defaultMode = resolveDefaultSubtitleDisplayMode(
        hasPrimaryTrack = hasPrimaryTrack,
        hasSecondaryTrack = hasSecondaryTrack
    )
    if (defaultMode == SubtitleDisplayMode.OFF) return SubtitleDisplayMode.OFF

    val defaultModeLikelyAi = when (defaultMode) {
        SubtitleDisplayMode.OFF -> false
        SubtitleDisplayMode.PRIMARY_ONLY -> primaryTrackLikelyAi
        SubtitleDisplayMode.SECONDARY_ONLY -> secondaryTrackLikelyAi
        SubtitleDisplayMode.BILINGUAL -> primaryTrackLikelyAi && secondaryTrackLikelyAi
    }

    return when (preference) {
        SubtitleAutoPreference.OFF -> SubtitleDisplayMode.OFF
        SubtitleAutoPreference.ON -> defaultMode
        SubtitleAutoPreference.WITHOUT_AI -> {
            if (defaultModeLikelyAi) SubtitleDisplayMode.OFF else defaultMode
        }
        SubtitleAutoPreference.AUTO -> {
            if (!defaultModeLikelyAi || isMuted) defaultMode else SubtitleDisplayMode.OFF
        }
        // 只开中文字幕：主轨是中文就只开主轨（不叠加英文副轨），否则退回副轨，都没有就关闭。
        SubtitleAutoPreference.CHINESE_ONLY -> when {
            hasPrimaryTrack && isChineseSubtitleLanguage(primaryTrackLanguage) ->
                SubtitleDisplayMode.PRIMARY_ONLY
            hasSecondaryTrack && isChineseSubtitleLanguage(secondaryTrackLanguage) ->
                SubtitleDisplayMode.SECONDARY_ONLY
            else -> SubtitleDisplayMode.OFF
        }
    }
}

fun resolveSubtitlePreferenceSession(
    bvid: String,
    cid: Long,
    primaryLanguage: String?,
    secondaryLanguage: String?,
    primaryTrackLikelyAi: Boolean,
    secondaryTrackLikelyAi: Boolean,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean,
    preference: SubtitleAutoPreference,
    isMuted: Boolean = false
): SubtitlePreferenceSession {
    val sessionKey = "${bvid.trim()}_${cid}_${primaryLanguage}_${secondaryLanguage}_${primaryTrackLikelyAi}_${secondaryTrackLikelyAi}_${preference.name}"
    val initialMode = resolveSubtitleDisplayModeByAutoPreference(
        preference = preference,
        hasPrimaryTrack = hasPrimaryTrack,
        hasSecondaryTrack = hasSecondaryTrack,
        primaryTrackLikelyAi = primaryTrackLikelyAi,
        secondaryTrackLikelyAi = secondaryTrackLikelyAi,
        isMuted = isMuted,
        primaryTrackLanguage = primaryLanguage,
        secondaryTrackLanguage = secondaryLanguage
    )
    return SubtitlePreferenceSession(
        key = sessionKey,
        initialMode = initialMode
    )
}

fun resolveSubtitleDisplayModePreference(
    previousSessionKey: String?,
    nextSessionKey: String,
    previousMode: SubtitleDisplayMode,
    nextInitialMode: SubtitleDisplayMode
): SubtitleDisplayMode {
    return if (previousSessionKey == nextSessionKey) previousMode else nextInitialMode
}

fun resolveSubtitleControlAvailability(
    primaryTrackBound: Boolean,
    secondaryTrackBound: Boolean,
    primaryCueAvailable: Boolean,
    secondaryCueAvailable: Boolean
): SubtitleControlAvailability {
    val primarySelectable = primaryTrackBound || primaryCueAvailable
    val secondarySelectable = secondaryTrackBound || secondaryCueAvailable
    return SubtitleControlAvailability(
        trackAvailable = primarySelectable || secondarySelectable,
        primarySelectable = primarySelectable,
        secondarySelectable = secondarySelectable
    )
}

fun resolveSubtitleDisplayOptions(
    primaryLabel: String,
    secondaryLabel: String,
    hasPrimaryTrack: Boolean,
    hasSecondaryTrack: Boolean
): List<SubtitleDisplayOption> {
    val options = mutableListOf(
        SubtitleDisplayOption(
            mode = SubtitleDisplayMode.OFF,
            label = "关闭",
            enabled = true
        )
    )
    if (hasPrimaryTrack) {
        options += SubtitleDisplayOption(
            mode = SubtitleDisplayMode.PRIMARY_ONLY,
            label = primaryLabel,
            enabled = true
        )
    }
    if (hasSecondaryTrack) {
        options += SubtitleDisplayOption(
            mode = SubtitleDisplayMode.SECONDARY_ONLY,
            label = secondaryLabel,
            enabled = true
        )
    }
    if (hasPrimaryTrack && hasSecondaryTrack) {
        options += SubtitleDisplayOption(
            mode = SubtitleDisplayMode.BILINGUAL,
            label = "双语",
            enabled = true
        )
    }
    return options
}

fun shouldRenderPrimarySubtitle(mode: SubtitleDisplayMode): Boolean {
    return mode == SubtitleDisplayMode.PRIMARY_ONLY || mode == SubtitleDisplayMode.BILINGUAL
}

fun shouldRenderSecondarySubtitle(mode: SubtitleDisplayMode): Boolean {
    return mode == SubtitleDisplayMode.SECONDARY_ONLY || mode == SubtitleDisplayMode.BILINGUAL
}

fun resolveSubtitleTextAt(cues: List<SubtitleCue>, positionMs: Long): String? =
    findSubtitleText(cues, positionMs, endHoldMs = 0L)

const val SUBTITLE_GAP_HOLD_MS = 280L

/** A short gap may retain its preceding cue, never text remembered from another playback position. */
fun resolveSubtitleDisplayTextAt(cues: List<SubtitleCue>, positionMs: Long): String? =
    findSubtitleText(cues, positionMs, endHoldMs = SUBTITLE_GAP_HOLD_MS)?.takeIf { it.isNotBlank() }

private fun findSubtitleText(cues: List<SubtitleCue>, positionMs: Long, endHoldMs: Long): String? {
    if (cues.isEmpty()) return null
    if (positionMs < 0L) return null

    var low = 0
    var high = cues.lastIndex
    var precedingCue: SubtitleCue? = null
    while (low <= high) {
        val mid = (low + high) ushr 1
        val cue = cues[mid]
        when {
            positionMs < cue.startMs -> high = mid - 1
            positionMs > cue.endMs -> {
                precedingCue = cue
                low = mid + 1
            }
            else -> return cue.content
        }
    }
    val preceding = precedingCue ?: return null
    return preceding.content.takeIf { positionMs - preceding.endMs <= endHoldMs }
}

/**
 * 字幕进度轮询 identity：只绑定 bvid/cid，禁止把整份 [uiState] 当 key。
 * Success 对象频繁替换会让 produceState 以 initialValue=0 重启 → 字幕整页乱跳闪烁。
 */
fun resolveSubtitlePositionPollingIdentity(
    bvid: String?,
    cid: Long,
): String {
    val safeBvid = bvid?.trim().orEmpty()
    val safeCid = cid.coerceAtLeast(0L)
    return if (safeBvid.isEmpty() && safeCid <= 0L) {
        "no-video"
    } else {
        "${safeBvid}_$safeCid"
    }
}

/**
 * 字幕叠层是否保持挂载（容器常驻，只换文案）。
 * 不要用「当前有无 cue 文本」控制整层 if，否则句间空窗会反复进出 composition → 闪。
 */
fun shouldKeepSubtitleOverlayMounted(
    overlayEnabled: Boolean,
    isInPipMode: Boolean,
    isAudioOnly: Boolean,
    suppressOverlay: Boolean,
): Boolean {
    return overlayEnabled && !isInPipMode && !isAudioOnly && !suppressOverlay
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
    return (this as? JsonObject) ?: runCatching { this?.jsonObject }.getOrNull()
}

private fun JsonElement?.asJsonArrayOrNull(): JsonArray? {
    return (this as? JsonArray) ?: runCatching { this?.jsonArray }.getOrNull()
}

private fun JsonElement?.asDoubleOrNull(): Double? {
    return (this as? JsonPrimitive)?.doubleOrNull
}
