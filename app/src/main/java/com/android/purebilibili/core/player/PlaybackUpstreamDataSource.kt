package com.android.purebilibili.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.player.ripper.ThreadRipper

/**
 * 播放链路的上游 HTTP 数据源：OkHttpDataSource，外面视设置套一层线程撕裂者并发引擎。
 *
 * 所有创建 ExoPlayer 媒体源的地方都应经由这里，这样多线程下载只需在一处开关。
 * 返回值仍然要交给 [PlaybackMediaCache.buildCachedDataSourceFactory] 包一层磁盘缓存。
 */
@UnstableApi
internal fun buildPlaybackUpstreamDataSourceFactory(
    context: Context?,
    headers: Map<String, String>
): DataSource.Factory {
    val http = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
        .setDefaultRequestProperties(headers)
    val appContext = context?.applicationContext ?: NetworkModule.appContext ?: return http
    return ThreadRipper.wrap(appContext, http, headers)
}
