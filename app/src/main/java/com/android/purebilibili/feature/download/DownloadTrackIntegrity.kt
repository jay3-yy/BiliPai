package com.android.purebilibili.feature.download

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal class DownloadTrackIntegrityException(
    kind: DownloadAssetKind,
    reason: String,
    cause: Throwable? = null,
) : IOException("${if (kind == DownloadAssetKind.VIDEO) "视频" else "音频"}轨校验失败：$reason", cause)

internal data class VerifiedDownloadTrack(
    val totalBytes: Long,
    val segmentCount: Int,
    val sha256: String,
)

internal fun verifyDownloadTrackDigest(
    file: File,
    kind: DownloadAssetKind,
    expectedBytes: Long,
    expectedSha256: String?,
    ensureActive: () -> Unit,
): String {
    if (expectedBytes <= 0L || file.length() != expectedBytes) {
        throw DownloadTrackIntegrityException(kind, "文件大小不符")
    }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
    if (expectedSha256 != null && sha256 != expectedSha256) {
        throw DownloadTrackIntegrityException(kind, "已下载文件内容发生变化")
    }
    return sha256
}

internal fun requireReadableDownloadTrack(file: File, kind: DownloadAssetKind) {
    val prefix = if (kind == DownloadAssetKind.VIDEO) "video/" else "audio/"
    val extractor = MediaExtractor()
    try {
        file.inputStream().use { input ->
            try {
                extractor.setDataSource(input.fd)
            } catch (error: IOException) {
                throw DownloadTrackIntegrityException(kind, "无法解析媒体文件", error)
            }
        }
        val index = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
        } ?: throw DownloadTrackIntegrityException(kind, "缺少所需媒体轨")
        extractor.selectTrack(index)
        if (extractor.sampleTrackIndex != index) {
            throw DownloadTrackIntegrityException(kind, "媒体轨没有可读取的采样")
        }
    } finally {
        extractor.release()
    }
}
