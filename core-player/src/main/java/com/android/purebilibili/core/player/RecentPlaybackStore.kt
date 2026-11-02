package com.android.purebilibili.core.player

import android.content.Context
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.ViewInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Bounded, account-scoped metadata. CID and completion use the shared progress policy. */
class RecentPlaybackStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tv_recent_playback", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(mid: Long?) = "recent:${mid ?: 0}"
    @Synchronized fun items(mid: Long?): List<VideoItem> = runCatching {
        json.decodeFromString<List<VideoItem>>(prefs.getString(key(mid), "[]") ?: "[]")
    }.getOrDefault(emptyList())
    @Synchronized fun save(mid: Long?, info: ViewInfo, positionMs: Long, durationMs: Long) {
        if (info.bvid.isBlank() || info.cid <= 0 || positionMs < 5_000) return
        val previous = items(mid).filterNot { it.bvid == info.bvid }
        val resume = resolvePlaybackResumePosition(null, positionMs, null, durationMs)
        val next = if (resume > 0) listOf(VideoItem(aid = info.aid, bvid = info.bvid, cid = info.cid,
            title = info.title, pic = info.pic, owner = info.owner, progress = (positionMs / 1000).toInt(),
            duration = (durationMs / 1000).toInt(), view_at = System.currentTimeMillis() / 1000)) + previous else previous
        write(mid, next)
    }
    @Synchronized fun mergeHistory(mid: Long?, history: List<VideoItem>) {
        if (mid == null) return
        val local = items(mid)
        val completed = history.filter { it.cid > 0 && (it.progress == -1 || it.duration > 0 && it.progress > it.duration * .95) }
        val candidates = (local + history.filter { it.cid > 0 && resolvePlaybackResumePosition(null,
            it.progress * 1000L, null, it.duration * 1000L) > 0 })
            .filterNot { item -> completed.any { it.bvid == item.bvid && it.cid == item.cid && it.view_at >= item.view_at } }
            .sortedByDescending { it.view_at }.distinctBy { it.bvid }
        write(mid, candidates)
    }
    @Synchronized fun remove(mid: Long?, bvid: String) = write(mid, items(mid).filterNot { it.bvid == bvid })
    @Synchronized fun clear(mid: Long?) = write(mid, emptyList())
    private fun write(mid: Long?, items: List<VideoItem>) = prefs.edit().putString(key(mid), json.encodeToString(items.take(100))).apply()
}
