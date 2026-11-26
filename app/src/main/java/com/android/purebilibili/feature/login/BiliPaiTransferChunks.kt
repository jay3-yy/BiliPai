package com.android.purebilibili.feature.login

import android.util.Base64

data class BiliPaiTransferChunk(val transferId: String, val index: Int, val total: Int, val data: String)

object BiliPaiTransferChunks {
    private const val PREFIX = "bilipai://transfer/chunk?"
    private const val PREFERRED_CHUNK_DATA = 760
    private const val MAX_CHUNK_DATA = 1_500
    private const val MAX_CHUNKS = 128

    fun split(transferId: String, envelope: String): List<String> {
        val encoded = Base64.encodeToString(envelope.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        // 默认使用较低密度的 760 字符分块，大幅提升手机暗光/近距扫码识别率；
        // 仅在极端大体积不可压缩载荷时自动提升分块密度，保留 128 块上限容量。
        val chunkSize = if ((encoded.length + PREFERRED_CHUNK_DATA - 1) / PREFERRED_CHUNK_DATA <= MAX_CHUNKS) {
            PREFERRED_CHUNK_DATA
        } else {
            ((encoded.length + MAX_CHUNKS - 1) / MAX_CHUNKS).coerceAtLeast(PREFERRED_CHUNK_DATA)
        }
        require(chunkSize <= MAX_CHUNK_DATA) { "传输内容过大，请减少所选设置或插件后重试" }
        val total = (encoded.length + chunkSize - 1) / chunkSize
        require(total in 1..MAX_CHUNKS) { "传输内容过大，请减少所选设置或插件后重试" }
        return (0 until total).map { index ->
            val start = index * chunkSize
            val end = minOf(start + chunkSize, encoded.length)
            "$PREFIX" + "id=$transferId&i=$index&n=$total&d=${encoded.substring(start, end)}"
        }
    }

    fun parse(raw: String): BiliPaiTransferChunk? {
        if (!raw.startsWith(PREFIX) || raw.length > 2_400) return null
        val values = raw.substringAfter('?').split('&').mapNotNull {
            val p = it.split('=', limit = 2); if (p.size == 2) p[0] to p[1] else null
        }.toMap()
        val index = values["i"]?.toIntOrNull() ?: return null
        val total = values["n"]?.toIntOrNull() ?: return null
        if (values["id"].isNullOrBlank() || total !in 1..128 || index !in 0 until total ||
            values["d"].isNullOrEmpty() || values.getValue("d").length > MAX_CHUNK_DATA) return null
        return BiliPaiTransferChunk(values.getValue("id"), index, total, values["d"].orEmpty())
    }

    fun join(chunks: Collection<BiliPaiTransferChunk>): String? {
        if (chunks.isEmpty()) return null
        val first = chunks.first()
        if (chunks.size != first.total || chunks.any { it.transferId != first.transferId || it.total != first.total }) return null
        val encoded = (0 until first.total).map { i -> chunks.firstOrNull { it.index == i }?.data ?: return null }.joinToString("")
        return runCatching { String(Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE)) }.getOrNull()
    }
}
