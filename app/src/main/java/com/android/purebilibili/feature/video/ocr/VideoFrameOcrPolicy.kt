package com.android.purebilibili.feature.video.ocr

internal data class VideoFrameOcrBlock(
    val id: Int,
    val text: String,
)

internal sealed interface VideoFrameOcrResult {
    data class Success(val blocks: List<VideoFrameOcrBlock>) : VideoFrameOcrResult
    data class Failure(val message: String) : VideoFrameOcrResult
}

internal fun normalizeOcrBlocks(rawBlocks: List<String>): List<VideoFrameOcrBlock> {
    var nextId = 0
    return rawBlocks.mapNotNull { raw ->
        val cleaned = raw
            .replace("\r\n", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        if (cleaned.isBlank()) null else VideoFrameOcrBlock(id = nextId++, text = cleaned)
    }
}

internal fun joinSelectedOcrText(
    blocks: List<VideoFrameOcrBlock>,
    selectedIds: Set<Int>,
): String = blocks
    .filter { it.id in selectedIds }
    .joinToString(separator = "\n\n") { it.text }

internal fun resolveOcrFailureMessage(rawError: String?): String {
    val lowered = rawError.orEmpty().lowercase()
    return when {
        "waiting for" in lowered || "download" in lowered || "module" in lowered ->
            "正在首次准备端侧识字模型，请保持联网稍后重试"
        "gms" in lowered || "play services" in lowered || "unavailable" in lowered ->
            "当前设备缺少 Google Play 服务，暂不支持端侧文字识别"
        else -> "未能识别当前画面文字，请换一帧重试"
    }
}
