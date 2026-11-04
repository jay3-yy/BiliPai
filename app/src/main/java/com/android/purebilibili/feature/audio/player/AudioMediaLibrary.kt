package com.android.purebilibili.feature.audio.player

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.android.purebilibili.R
import com.android.purebilibili.core.store.MultiProcessPreferences
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.repository.WatchLaterRepository
import com.android.purebilibili.feature.video.player.PlaylistItem
import kotlinx.coroutines.flow.first

/** 系统媒体目录只暴露内容身份；播放地址由音频服务按当前账号重新获取。 */
internal class AudioMediaLibrary(private val context: Context) {
    suspend fun saved(): BackgroundAudioSnapshot? {
        if (SystemMediaPreferences.libraryProtected(context)) return null
        TokenManager.refreshServiceSession(context)
        val prefs = MultiProcessPreferences.get(context, "background_audio").data.first()
        val raw = prefs[stringPreferencesKey("library_snapshot")] ?: prefs[stringPreferencesKey("snapshot")]
            ?: return null
        return backgroundAudioJson.decodeFromString(BackgroundAudioSnapshot.serializer(), raw)
            .takeIf { it.accountMid == TokenManager.midCache }
    }

    fun folder(id: String): MediaItem {
        require(id in listOf(ROOT, QUEUE, LATER) || id.startsWith("$LATER:") && id.substringAfter(':').toInt() > 0)
        val title = when {
            id == ROOT -> R.string.audio_library_title
            id == QUEUE -> R.string.audio_library_queue
            id == LATER -> R.string.shortcut_watch_later_short
            else -> R.string.audio_library_more
        }
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(context.getString(title))
                .setIsBrowsable(true).setIsPlayable(false).build()
        ).build()
    }

    internal data class Page(val entries: List<PlaylistItem>, val nextParent: String? = null)

    suspend fun entries(parent: String, current: BackgroundAudioSnapshot?): Page {
        check(!SystemMediaPreferences.libraryProtected(context)) { context.getString(R.string.audio_library_locked) }
        TokenManager.refreshServiceSession(context)
        return if (parent == QUEUE) {
            Page((current?.takeIf { it.accountMid == TokenManager.midCache } ?: saved())?.queue.orEmpty())
        } else {
            require(parent == LATER || parent.startsWith("$LATER:"))
            check(!TokenManager.sessDataCache.isNullOrEmpty()) { context.getString(R.string.audio_library_login) }
            val account = TokenManager.midCache
            val page = if (parent == LATER) 1 else parent.substringAfter(':').toInt().also { require(it > 0) }
            val result = WatchLaterRepository.getPage(page, 0, "", false).getOrThrow()
            TokenManager.refreshServiceSession(context)
            check(account == TokenManager.midCache) { "Account changed" }
            val entries = result.items.filter { it.bvid.isNotBlank() }.map {
                PlaylistItem(it.bvid, it.cid, it.title, it.pic, it.owner.name, duration = it.duration.toLong())
            }
            Page(entries, if (result.hasMore) "$LATER:${page + 1}" else null)
        }
    }

    fun item(parent: String, entry: PlaylistItem): MediaItem = MediaItem.Builder()
        .setMediaId("$parent/${entry.bvid}:${entry.cid}")
        .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).setArtist(entry.owner)
            .setArtworkUri(entry.cover.takeIf { it.isNotBlank() }?.let { Uri.parse(if (it.startsWith("//")) "https:$it" else it) })
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
        .build()

    companion object {
        const val ROOT = "audio"
        const val QUEUE = "queue"
        const val LATER = "later"
    }
}
