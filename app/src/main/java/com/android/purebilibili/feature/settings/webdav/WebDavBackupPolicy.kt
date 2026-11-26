package com.android.purebilibili.feature.settings.webdav

import java.net.URLDecoder
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val RESPONSE_BLOCK_REGEX = Regex(
    pattern = "<[^>]*:?response[^>]*>(.*?)</[^>]*:?response>",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
private val RFC_1123_FORMATTER: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
private val FILE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

internal const val DEFAULT_WEBDAV_REMOTE_DIR = "/BiliPai/backups"
internal const val DEFAULT_WEBDAV_MAX_REMOTE_BACKUPS = 7
internal val WEBDAV_RETENTION_PRESETS = listOf(5, 7, 15, 30, 0)

internal fun selectWebDavBackupsToPrune(
    entries: List<WebDavBackupEntry>,
    maxRetained: Int,
): List<WebDavBackupEntry> {
    if (maxRetained <= 0 || entries.size <= maxRetained) return emptyList()
    return entries
        .sortedWith(
            compareByDescending<WebDavBackupEntry> { it.lastModifiedEpochMs }
                .thenByDescending { it.fileName }
        )
        .drop(maxRetained)
}

data class WebDavBackupEntry(
    val fileName: String,
    val href: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long
)

internal fun ensureWebDavCollectionUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return trimmed
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

/**
 * 目录创建决策:多数 WebDAV 服务器对「已存在目录」的 MKCOL 返回 405,
 * 但部分实现(409/412/403 等)会让无条件 MKCOL 直接失败。因此创建前先
 * PROPFIND(Depth: 0)探测:已存在则跳过,404 才创建,探测异常时回退
 * 幂等 MKCOL 并再次确认。
 */
internal enum class WebDavDirectoryAction {
    /** 探测确认已存在,无需创建。 */
    EXISTS,

    /** 探测为 404,需要 MKCOL 创建。 */
    CREATE,

    /** 探测异常(非 404 非成功),回退幂等 MKCOL 并再次确认。 */
    CREATE_OR_VERIFY,
}

internal fun resolveWebDavDirectoryAction(
    probeStatus: Int?,
): WebDavDirectoryAction = when (probeStatus) {
    in setOf(200, 207) -> WebDavDirectoryAction.EXISTS
    404 -> WebDavDirectoryAction.CREATE
    else -> WebDavDirectoryAction.CREATE_OR_VERIFY
}

internal fun resolveWebDavDirectoryProbeError(
    probeStatus: Int,
    url: String,
): String = when (probeStatus) {
    401 -> "WebDAV 认证失败(401),请检查用户名/密码: $url"
    403 -> "WebDAV 无权限访问该目录(403): $url"
    else -> "无法访问 WebDAV 目录: HTTP $probeStatus ($url)"
}

internal fun buildWebDavPropfindBody(): String {
    return """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:">
          <d:prop>
            <d:getlastmodified/>
            <d:getcontentlength/>
          </d:prop>
        </d:propfind>
    """.trimIndent()
}

internal fun normalizeWebDavBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.removeSuffix("/")
}

internal fun normalizeWebDavRemoteDir(raw: String): String {
    val parts = raw
        .trim()
        .replace('\\', '/')
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (parts.isEmpty()) return DEFAULT_WEBDAV_REMOTE_DIR
    return "/" + parts.joinToString("/")
}

internal fun buildWebDavBackupFileName(epochMs: Long): String {
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)
    return "bilipai-backup-${time.format(FILE_TIME_FORMATTER)}.zip"
}

internal fun parseWebDavBackupEntries(xml: String): List<WebDavBackupEntry> {
    return RESPONSE_BLOCK_REGEX
        .findAll(xml)
        .mapNotNull { match ->
            val block = match.groupValues.getOrNull(1) ?: return@mapNotNull null
            val href = extractXmlTagValue(block, "href") ?: return@mapNotNull null
            val decodedHref = decodeXml(href)
            val fileName = extractFileNameFromHref(decodedHref) ?: return@mapNotNull null
            if (!fileName.endsWith(".zip", ignoreCase = true)) return@mapNotNull null

            val size = extractXmlTagValue(block, "getcontentlength")
                ?.trim()
                ?.toLongOrNull()
                ?: 0L
            val lastModifiedMs = parseLastModified(
                raw = extractXmlTagValue(block, "getlastmodified"),
                fileName = fileName
            )

            WebDavBackupEntry(
                fileName = fileName,
                href = decodedHref,
                sizeBytes = size,
                lastModifiedEpochMs = lastModifiedMs
            )
        }
        .sortedWith(compareByDescending<WebDavBackupEntry> { it.lastModifiedEpochMs }
            .thenByDescending { it.fileName })
        .toList()
}

internal fun selectLatestWebDavBackup(entries: List<WebDavBackupEntry>): WebDavBackupEntry? {
    return entries
        .maxWithOrNull(compareBy<WebDavBackupEntry> { it.lastModifiedEpochMs }.thenBy { it.fileName })
}

internal fun resolveWebDavDownloadUrl(baseUrl: String, hrefOrPath: String): String {
    val candidate = hrefOrPath.trim()
    if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
        return candidate
    }

    val normalizedBase = normalizeWebDavBaseUrl(baseUrl)
    if (candidate.startsWith("/")) {
        val baseUri = URI(normalizedBase)
        val origin = "${baseUri.scheme}://${baseUri.authority}"
        return origin + candidate
    }
    return "$normalizedBase/$candidate"
}

private fun extractXmlTagValue(block: String, localTagName: String): String? {
    val regex = Regex(
        pattern = "<[^>]*:?$localTagName[^>]*>(.*?)</[^>]*:?$localTagName>",
        options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    val raw = regex.find(block)?.groupValues?.getOrNull(1)?.trim() ?: return null
    return raw.takeIf { it.isNotEmpty() }
}

private fun extractFileNameFromHref(href: String): String? {
    val normalized = href.trim()
    if (normalized.endsWith("/")) return null
    val segment = normalized.substringAfterLast('/', missingDelimiterValue = "")
    if (segment.isBlank()) return null
    return runCatching {
        URLDecoder.decode(segment, StandardCharsets.UTF_8.name())
    }.getOrDefault(segment)
}

private fun parseLastModified(raw: String?, fileName: String): Long {
    if (!raw.isNullOrBlank()) {
        try {
            return ZonedDateTime.parse(raw.trim(), RFC_1123_FORMATTER).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // fall through to filename parsing
        }
    }

    val fileTimePart = fileName
        .removePrefix("bilipai-backup-")
        .removeSuffix(".zip")
    return runCatching {
        LocalDateTime.parse(fileTimePart, FILE_TIME_FORMATTER)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrDefault(0L)
}

private fun decodeXml(raw: String): String {
    return raw
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
