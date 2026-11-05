package com.android.purebilibili.feature.video

import android.os.Bundle
import com.android.purebilibili.navigation3.BiliPaiNavKey

/** 视频入口数据与系统转场 options 分开传递，避免将播放参数当作 ActivityOptions。 */
internal data class VideoOpenRequest(
    val bvid: String,
    val cid: Long = 0L,
    val coverUrl: String = "",
    val startAudio: Boolean = false,
    val autoPortrait: Boolean = false,
    val fullscreen: Boolean = false,
    val resumePositionMs: Long = 0L,
    val commentRootRpid: Long = 0L,
    val commentTargetRpid: Long = 0L,
    val initialVertical: Boolean = false,
    val directPortraitEntry: Boolean = false,
) {
    fun toNavigationKey() = BiliPaiNavKey.VideoDetail(
        bvid = bvid, cid = cid, coverUrl = coverUrl, startAudio = startAudio,
        autoPortrait = autoPortrait, fullscreen = fullscreen,
        resumePositionMs = resumePositionMs, commentRootRpid = commentRootRpid,
        commentTargetRpid = commentTargetRpid, initialVertical = initialVertical,
        directPortraitEntry = directPortraitEntry,
    )

    fun toBundle() = Bundle().apply {
        putString("bvid", bvid)
        putLong("cid", cid)
        putString("cover", coverUrl)
        putBoolean("startAudio", startAudio)
        putBoolean("autoPortrait", autoPortrait)
        putBoolean("fullscreen", fullscreen)
        putLong("resumePositionMs", resumePositionMs)
        putLong("commentRootRpid", commentRootRpid)
        putLong("commentTargetRpid", commentTargetRpid)
        putBoolean("initialVertical", initialVertical)
        putBoolean("directPortraitEntry", directPortraitEntry)
    }

    companion object {
        fun fromKey(key: BiliPaiNavKey.VideoDetail) = VideoOpenRequest(
            bvid = key.bvid, cid = key.cid, coverUrl = key.coverUrl,
            startAudio = key.startAudio, autoPortrait = key.autoPortrait,
            fullscreen = key.fullscreen, resumePositionMs = key.resumePositionMs,
            commentRootRpid = key.commentRootRpid, commentTargetRpid = key.commentTargetRpid,
            initialVertical = key.initialVertical, directPortraitEntry = key.directPortraitEntry,
        )

        fun fromBundle(bundle: Bundle): VideoOpenRequest? {
            val bvid = bundle.getString("bvid")?.takeIf { it.isNotBlank() } ?: return null
            return VideoOpenRequest(
                bvid = bvid, cid = bundle.getLong("cid"), coverUrl = bundle.getString("cover").orEmpty(),
                startAudio = bundle.getBoolean("startAudio"), autoPortrait = bundle.getBoolean("autoPortrait"),
                fullscreen = bundle.getBoolean("fullscreen"), resumePositionMs = bundle.getLong("resumePositionMs"),
                commentRootRpid = bundle.getLong("commentRootRpid"), commentTargetRpid = bundle.getLong("commentTargetRpid"),
                initialVertical = bundle.getBoolean("initialVertical"), directPortraitEntry = bundle.getBoolean("directPortraitEntry"),
            )
        }
    }
}
