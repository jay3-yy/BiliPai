package com.android.purebilibili.core.plugin.js

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ExternalMediaLaunchRequest(
    val launchId: String,
    val title: String,
    val coverUrl: String? = null,
    val streams: List<BiliPaiJsMediaStream>,
    val selectedStreamIndex: Int = 0,
    /** 提供第三方弹幕的 JS 插件 ID；null 表示本次播放不加载弹幕。 */
    val danmakuPluginId: String? = null
)

object ExternalMediaLaunchStore {
    private const val MAX_CACHED_LAUNCH_REQUESTS = 16
    private val requests = object : LinkedHashMap<String, ExternalMediaLaunchRequest>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ExternalMediaLaunchRequest>?
        ): Boolean = size > MAX_CACHED_LAUNCH_REQUESTS
    }

    @Synchronized
    fun put(
        title: String,
        coverUrl: String?,
        streams: List<BiliPaiJsMediaStream>,
        selectedStreamIndex: Int = 0,
        danmakuPluginId: String? = null
    ): String {
        val launchId = UUID.randomUUID().toString()
        requests[launchId] = ExternalMediaLaunchRequest(
            launchId = launchId,
            title = title,
            coverUrl = coverUrl,
            streams = streams,
            selectedStreamIndex = selectedStreamIndex.coerceIn(0, streams.lastIndex.coerceAtLeast(0)),
            danmakuPluginId = danmakuPluginId
        )
        return launchId
    }

    @Synchronized
    fun get(launchId: String): ExternalMediaLaunchRequest? {
        return requests[launchId]
    }

    @Synchronized
    fun remove(launchId: String) {
        requests.remove(launchId)
    }

    @Synchronized
    fun trimToSize(maxEntries: Int) {
        val safeMax = maxEntries.coerceAtLeast(0)
        val iterator = requests.entries.iterator()
        while (requests.size > safeMax && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
}
