package com.android.purebilibili.core.plugin.bpx

import android.util.Base64
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.Inflater

private const val MAX_BPX_SOURCE_BYTES = 128 * 1024

object BpxOfflineCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val allowedSlots = setOf(
        "feed.pipe",
        "detail.companion",
        "player.track",
        "home.lens",
        "community.craft",
        "theme.skin",
    )
    private val idRegex = Regex("^[a-z0-9]+(\\.[a-z0-9_]+){1,4}$")

    fun isOfflinePreviewUri(raw: String): Boolean {
        val normalized = raw.trim().lowercase()
        return normalized.startsWith("bilipai://ext/preview")
    }

    fun decodeOfflinePreviewUri(rawUri: String): Result<BpxPreviewPackage> = runCatching {
        val uri = URI(rawUri.trim())
        require(uri.scheme.equals("bilipai", ignoreCase = true) && uri.host.equals("ext", ignoreCase = true)) {
            "不是有效的 bilipai://ext/preview 链接"
        }
        val query = uri.rawQuery
            ?.split("&")
            ?.mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val kv = part.split("=", limit = 2)
                URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()) to
                    URLDecoder.decode(kv.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
            }
            ?.toMap()
            .orEmpty()

        val payload = query["payload"]?.trim().orEmpty()
        require(payload.isNotEmpty()) { "离线扩展二维码缺少 payload 数据" }
        val codec = query["c"]?.lowercase() ?: "deflate"
        val rawBytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val sourceBytes = when (codec) {
            "deflate" -> inflateRawBounded(rawBytes, MAX_BPX_SOURCE_BYTES)
            "raw" -> {
                require(rawBytes.size <= MAX_BPX_SOURCE_BYTES) { "扩展源码超过大小限制" }
                rawBytes
            }
            else -> throw IllegalArgumentException("不支持的离线编码方式: $codec")
        }
        parseBpxSource(sourceBytes.toString(StandardCharsets.UTF_8)).getOrThrow()
    }

    fun parseBpxSource(rawSource: String): Result<BpxPreviewPackage> = runCatching {
        val trimmed = rawSource.trim()
        require(trimmed.isNotEmpty()) { "扩展源码为空" }
        require(trimmed.toByteArray(StandardCharsets.UTF_8).size <= MAX_BPX_SOURCE_BYTES) {
            "单文件 .bpx 源码超过 128 KiB 上限"
        }

        val isJson = trimmed.startsWith("{")
        val format = if (isJson) "json" else "js"
        val manifest = if (isJson) {
            json.decodeFromString(BpxManifest.serializer(), trimmed)
        } else {
            val manifestJson = extractManifestJsonFromJs(trimmed)
            json.decodeFromString(BpxManifest.serializer(), manifestJson)
        }
        validateManifest(manifest, trimmed, format)
        BpxPreviewPackage(
            manifest = manifest,
            format = format,
            rawSource = trimmed,
            sha256 = sha256Hex(trimmed.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    private fun validateManifest(manifest: BpxManifest, source: String, format: String) {
        require(manifest.bpxVersion == 1) { "不支持的 bpxVersion: ${manifest.bpxVersion}" }
        require(idRegex.matches(manifest.id) && manifest.id.length <= 64) {
            "扩展 ID 格式无效: ${manifest.id}"
        }
        require(manifest.name.isNotBlank()) { "扩展名称不能为空" }
        require(manifest.author.name.isNotBlank()) { "缺少创作者署名 author.name" }
        require(manifest.mounts.isNotEmpty()) { "扩展未声明任何宿主挂载插槽 (mounts)" }
        manifest.mounts.forEach { mount ->
            require(mount.slot in allowedSlots) { "未知挂载插槽: ${mount.slot}" }
        }
        manifest.permissions.networkDomains.forEach { domain ->
            require(!domain.contains("*") && !domain.contains("/")) {
                "联网白名单必须为精确域名（禁止通配符）: $domain"
            }
        }
        if (format == "js") {
            require(!Regex("\\b(eval|XMLHttpRequest|localStorage)\\b").containsMatchIn(source)) {
                "脚本包含沙盒禁用的全局接口"
            }
        }
    }

    private fun inflateRawBounded(compressed: ByteArray, maxBytes: Int): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                total += count
                if (total > maxBytes) {
                    throw IllegalArgumentException("解压后的扩展内容超过安全体积上限")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun extractManifestJsonFromJs(source: String): String {
        val match = Regex("manifest\\s*:\\s*\\{").find(source)
            ?: Regex("export\\s+const\\s+manifest\\s*=\\s*\\{").find(source)
            ?: throw IllegalArgumentException("未找到 globalThis.BPX = { manifest: { ... } } 声明")
        val braceStart = source.indexOf('{', match.range.first)
        val rawObj = extractBalancedBlock(source, braceStart)
            ?: throw IllegalArgumentException("manifest 对象花括号未闭合")
        return jsObjectLiteralToJson(rawObj)
    }

    private fun extractBalancedBlock(source: String, startIdx: Int): String? {
        var depth = 0
        var inStr: Char? = null
        var escaped = false
        for (i in startIdx until source.length) {
            val ch = source[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (inStr != null) {
                if (ch == inStr) inStr = null
                continue
            }
            if (ch == '"' || ch == '\'' || ch == '`') {
                inStr = ch
                continue
            }
            if (ch == '{') depth++
            else if (ch == '}') {
                depth--
                if (depth == 0) return source.substring(startIdx, i + 1)
            }
        }
        return null
    }

    private fun jsObjectLiteralToJson(rawObj: String): String {
        val out = StringBuilder(rawObj.length + 32)
        var i = 0
        val n = rawObj.length
        while (i < n) {
            val ch = rawObj[i]
            val next = if (i + 1 < n) rawObj[i + 1] else '\u0000'
            if (ch == '/' && next == '/') {
                while (i < n && rawObj[i] != '\n') i++
                continue
            }
            if (ch == '/' && next == '*') {
                i += 2
                while (i < n && !(rawObj[i] == '*' && i + 1 < n && rawObj[i + 1] == '/')) i++
                i += 2
                continue
            }
            if (ch == '"' || ch == '\'') {
                val quote = ch
                out.append('"')
                i++
                while (i < n && rawObj[i] != quote) {
                    if (rawObj[i] == '\\' && i + 1 < n) {
                        val esc = rawObj[i + 1]
                        if (esc == '\'') out.append('\'') else out.append('\\').append(esc)
                        i += 2
                        continue
                    }
                    if (rawObj[i] == '"') out.append("\\\"") else out.append(rawObj[i])
                    i++
                }
                out.append('"')
                i++
                continue
            }
            if (ch == ',') {
                var j = i + 1
                while (j < n && rawObj[j].isWhitespace()) j++
                if (j < n && (rawObj[j] == '}' || rawObj[j] == ']')) {
                    i++
                    continue
                }
                out.append(',')
                i++
                continue
            }
            if (ch.isLetter() || ch == '_' || ch == '$') {
                var j = i + 1
                while (j < n && (rawObj[j].isLetterOrDigit() || rawObj[j] == '_' || rawObj[j] == '$')) j++
                val ident = rawObj.substring(i, j)
                var k = j
                while (k < n && rawObj[k].isWhitespace()) k++
                if (k < n && rawObj[k] == ':') {
                    out.append('"').append(ident).append("\":")
                    i = k + 1
                    continue
                }
                out.append(ident)
                i = j
                continue
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
