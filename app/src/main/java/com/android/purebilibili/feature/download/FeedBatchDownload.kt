package com.android.purebilibili.feature.download

import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class FeedDownloadVideo(val bvid: String, val title: String)

internal data class FeedDownloadFailure(val title: String, val reason: String)

internal data class FeedBatchDownloadInput(
    val candidates: List<BatchDownloadCandidate>,
    val qualities: List<Pair<Int, String>>,
    val preferredQuality: Int,
    val failures: List<FeedDownloadFailure>,
)

internal data class FeedBatchDownloadReport(
    val result: BatchDownloadQueueResult,
    val failures: List<FeedDownloadFailure>,
)

private data class FeedDownloadDetails(
    val candidates: List<BatchDownloadCandidate>,
    val qualities: List<Pair<Int, String>>,
    val failure: FeedDownloadFailure? = null,
)

internal suspend fun loadFeedBatchDownloadInput(
    videos: List<FeedDownloadVideo>,
): FeedBatchDownloadInput = coroutineScope {
    val limiter = Semaphore(4)
    val results = videos.distinctBy { it.bvid }.map { video ->
        async {
            limiter.withPermit {
                val detail = VideoRepository.getVideoInfoOnly(video.bvid).getOrElse {
                    if (it is CancellationException) throw it
                    return@withPermit FeedDownloadDetails(
                        candidates = emptyList(),
                        qualities = emptyList(),
                        failure = FeedDownloadFailure(video.title, it.message ?: "无法获取视频详情"),
                    )
                }
                val candidates = resolveFeedDownloadParts(detail)
                val firstPart = candidates.firstOrNull()
                    ?: return@withPermit FeedDownloadDetails(
                        candidates = emptyList(),
                        qualities = emptyList(),
                        failure = FeedDownloadFailure(video.title, "未获取到可下载的分 P"),
                    )
                val stream = try {
                    VideoRepository.getPlayUrlDataForDownload(firstPart.bvid, firstPart.cid)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                FeedDownloadDetails(candidates, stream?.downloadQualities().orEmpty())
            }
        }
    }.awaitAll()

    val qualities = results.flatMap { it.qualities }.distinctBy { it.first }.sortedByDescending { it.first }
    val probed = results.filter { it.qualities.isNotEmpty() }
    val preferred = qualities.firstOrNull { (id, _) ->
        probed.all { video -> video.qualities.any { it.first == id } }
    } ?: qualities.firstOrNull()
    FeedBatchDownloadInput(
        candidates = results.flatMap { it.candidates }.distinctBy { it.id },
        qualities = qualities,
        preferredQuality = preferred?.first ?: 0,
        failures = results.mapNotNull { it.failure },
    )
}

private fun resolveFeedDownloadParts(info: ViewInfo): List<BatchDownloadCandidate> {
    val parts = resolveBatchDownloadCandidates(info.copy(ugc_season = null))
    if (parts.isNotEmpty()) return parts.map { it.copy(selected = true, ownerFace = info.owner.face) }
    if (info.cid <= 0L) return emptyList()
    return listOf(
        BatchDownloadCandidate(
            id = "${info.bvid}#${info.cid}",
            bvid = info.bvid,
            cid = info.cid,
            aid = info.aid,
            title = info.title,
            label = info.title,
            cover = info.pic,
            ownerName = info.owner.name,
            ownerFace = info.owner.face,
            isVerticalVideo = info.dimension?.isVertical == true,
            selected = true,
        ),
    )
}

private fun PlayUrlData.downloadQualities(): List<Pair<Int, String>> =
    dash?.video.orEmpty()
        .filter { it.getValidUrl().isNotBlank() }
        .map { it.id }
        .distinct()
        .map { id ->
            id to (acceptDescription.getOrNull(acceptQuality.indexOf(id))
                ?: com.android.purebilibili.data.model.VideoQuality.fromCode(id)?.description
                ?: "${id} 档")
        }

internal suspend fun enqueueFeedBatchDownload(
    quality: Pair<Int, String>,
    options: DownloadOptions,
    candidates: List<BatchDownloadCandidate>,
): FeedBatchDownloadReport {
    val (qualityId, qualityLabel) = quality
    val telemetry = DownloadTelemetry()
    var added = 0
    var existing = 0
    val failures = mutableListOf<FeedDownloadFailure>()
    for (candidate in candidates.filter { it.selected }.distinctBy { it.id }) {
        val title = if (candidate.episodeCount > 1) "${candidate.title} · ${candidate.label}" else candidate.title
        if (DownloadManager.tasks.value.values.any {
                !it.isFailed && !it.isAudioOnly && it.bvid == candidate.bvid && it.cid == candidate.cid
            }
        ) {
            telemetry.record(DownloadStage.QUEUE, DownloadOutcome.DUPLICATE)
            existing++
            continue
        }
        try {
            val task = observeDownloadStreamResolution {
                // 按所选档位取流，其他稿件可用画质不同，不能将降级的流标为用户选中的画质。
                val stream = VideoRepository.getPlayUrlDataForDownload(candidate.bvid, candidate.cid, qualityId)
                    ?: throw DownloadStreamUnavailableException("无法获取下载地址，请检查网络或视频权限")
                val video = stream.dash?.video.orEmpty().firstOrNull { track ->
                    track.id == qualityId && track.getValidUrl().isNotBlank()
                } ?: throw DownloadStreamUnavailableException("此分 P 无法下载 $qualityLabel")
                val audio = stream.dash?.audio.orEmpty().firstOrNull { it.getValidUrl().isNotBlank() }
                    ?: throw DownloadStreamUnavailableException("未取得音频流")
                DownloadTask(
                    aid = candidate.aid,
                    bvid = candidate.bvid,
                    cid = candidate.cid,
                    title = title,
                    episodeLabel = candidate.label.takeIf { candidate.episodeCount > 1 },
                    groupKey = candidate.groupKey.takeIf(String::isNotBlank),
                    groupTitle = candidate.groupTitle.takeIf(String::isNotBlank),
                    episodeSortIndex = candidate.episodeSortIndex,
                    episodeCount = candidate.episodeCount,
                    cover = candidate.cover,
                    ownerName = candidate.ownerName,
                    ownerFace = candidate.ownerFace,
                    duration = candidate.durationSeconds,
                    quality = qualityId,
                    qualityDesc = qualityLabel,
                    videoUrl = video.getValidUrl(),
                    audioUrl = audio.getValidUrl(),
                    videoBackupUrls = video.downloadBackupUrls(),
                    audioBackupUrls = audio.downloadBackupUrls(),
                    isVerticalVideo = candidate.isVerticalVideo,
                    options = options,
                )
            }
            if (DownloadManager.addTask(task)) added++ else existing++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failures += FeedDownloadFailure(title, error.message ?: "无法加入下载队列")
        }
    }
    return FeedBatchDownloadReport(BatchDownloadQueueResult(added, existing, failures.size), failures)
}
