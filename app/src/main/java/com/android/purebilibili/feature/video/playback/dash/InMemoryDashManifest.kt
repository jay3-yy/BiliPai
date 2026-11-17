@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.playback.dash

import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource

// 仅标识清单的解析基址；实际音视频地址由清单中的绝对 BaseURL 提供。
internal const val IN_MEMORY_DASH_MANIFEST_URI = "memory://bilipai/adaptive.mpd"

/** 每个媒体源持有自己的清单字节，每次加载拥有独立的读取位置。 */
internal fun createInMemoryDashManifestDataSourceFactory(manifest: String): DataSource.Factory {
    // 首次 open 随清单加载线程编码，媒体源构造阶段不转换整份清单。
    val bytes by lazy { manifest.toByteArray(Charsets.UTF_8) }
    return DataSource.Factory { ByteArrayDataSource { _ -> bytes } }
}
