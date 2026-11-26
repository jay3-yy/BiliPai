package com.android.purebilibili.feature.download

import com.android.purebilibili.data.repository.DanmakuRepository
import com.android.purebilibili.data.repository.resolveDanmakuSegmentCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class LocalDanmakuManifest(
    val bvid: String,
    val cid: Long,
    val aid: Long,
    val durationMs: Long,
    val segmentPaths: List<String>,
    val standardSegmentCount: Int = 0,
    val savedAt: Long
)

internal data class LocalDanmakuSource(
    val standardSegmentPaths: List<String> = emptyList(),
    val specialSegmentPaths: List<String> = emptyList()
) {
    val totalFileCount: Int
        get() = standardSegmentPaths.size + specialSegmentPaths.size
}

internal data class DownloadDanmakuAssetResult(
    val segmentPaths: List<String>,
    val metadataPath: String?
)

internal object DownloadDanmakuAssetService {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun download(
        task: DownloadTask,
        taskDir: File,
        forceDownload: Boolean = false,
        updateState: (DownloadAssetState) -> Unit
    ): DownloadDanmakuAssetResult = withContext(Dispatchers.IO) {
        if (!forceDownload && !task.options.includeDanmaku) {
            updateState(
                DownloadAssetState(
                    kind = DownloadAssetKind.DANMAKU,
                    status = DownloadAssetStatus.SKIPPED
                )
            )
            return@withContext DownloadDanmakuAssetResult(emptyList(), null)
        }

        updateState(
            DownloadAssetState(
                kind = DownloadAssetKind.DANMAKU,
                status = DownloadAssetStatus.DOWNLOADING
            )
        )

        val durationMs = task.duration.coerceAtLeast(0) * 1000L
        val viewReply = if (task.aid > 0L) {
            DanmakuRepository.getDanmakuView(task.cid, task.aid)
        } else {
            null
        }
        val standardSegments = DanmakuRepository.getDanmakuSegments(
            cid = task.cid,
            durationMs = durationMs,
            metadataSegmentCount = viewReply?.dmSge?.total?.toInt()
        )
        val danmakuDir = File(taskDir, "danmaku").apply { mkdirs() }
        val standardSegmentPaths = standardSegments.mapIndexed { index, bytes ->
            val file = File(danmakuDir, "${task.id}_seg_${index + 1}.pb")
            file.writeBytes(bytes)
            file.absolutePath
        }
        val specialSegmentPaths = viewReply?.specialDms.orEmpty().mapIndexedNotNull { index, url ->
            val file = File(danmakuDir, "${task.id}_special_${index + 1}.pb")
            DanmakuRepository.downloadSpecialDanmaku(url, file)
                ?.takeIf { it > 0L }
                ?.let { file.absolutePath }
        }
        val segmentPaths = standardSegmentPaths + specialSegmentPaths
        if (segmentPaths.isEmpty()) {
            updateState(
                DownloadAssetState(
                    kind = DownloadAssetKind.DANMAKU,
                    status = DownloadAssetStatus.FAILED,
                    errorMessage = "未获取到弹幕"
                )
            )
            return@withContext DownloadDanmakuAssetResult(emptyList(), null)
        }
        val totalBytes = standardSegments.sumOf { it.size.toLong() } +
            specialSegmentPaths.sumOf { File(it).length() }
        val manifestFile = File(danmakuDir, "${task.id}_manifest.json")
        manifestFile.writeText(
            json.encodeToString(
                LocalDanmakuManifest(
                    bvid = task.bvid,
                    cid = task.cid,
                    aid = task.aid,
                    durationMs = durationMs,
                    segmentPaths = segmentPaths,
                    standardSegmentCount = standardSegmentPaths.size,
                    savedAt = System.currentTimeMillis()
                )
            )
        )

        updateState(
            DownloadAssetState(
                kind = DownloadAssetKind.DANMAKU,
                status = DownloadAssetStatus.COMPLETED,
                totalBytes = totalBytes,
                downloadedBytes = totalBytes,
                filePath = manifestFile.absolutePath,
                segmentCount = segmentPaths.size
            )
        )
        DownloadDanmakuAssetResult(
            segmentPaths = segmentPaths,
            metadataPath = manifestFile.absolutePath
        )
    }

    /** Resolve file ownership without loading segment payloads into Compose state. */
    fun readLocalSource(task: DownloadTask): LocalDanmakuSource {
        val manifest = task.localDanmakuMetadataPath
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?.let { file -> runCatching { json.decodeFromString<LocalDanmakuManifest>(file.readText()) }.getOrNull() }
        val paths = task.localDanmakuSegmentPaths
        val namedSpecialStart = paths.indexOfFirst { File(it).name.contains("_special_") }
        val standardCount = when {
            namedSpecialStart >= 0 -> namedSpecialStart
            manifest != null && manifest.standardSegmentCount > 0 -> manifest.standardSegmentCount
            else -> resolveDanmakuSegmentCount(
                durationMs = manifest?.durationMs ?: task.duration.coerceAtLeast(0) * 1_000L,
                metadataSegmentCount = null
            )
        }
            .coerceIn(0, paths.size)
        return LocalDanmakuSource(
            standardSegmentPaths = paths.take(standardCount).filter(::isReadableDanmakuFile),
            specialSegmentPaths = paths.drop(standardCount).filter(::isReadableDanmakuFile)
        )
    }

    private fun isReadableDanmakuFile(path: String): Boolean =
        File(path).let { it.isFile && it.length() > 0L }
}
