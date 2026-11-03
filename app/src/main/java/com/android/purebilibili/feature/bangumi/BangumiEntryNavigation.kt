package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.navigation3.BiliPaiNavKey

/** 点击即导航，详情与播放资源由目的页加载，与卡片过渡同时进行。 */
internal fun resolveBangumiPlaybackEntry(key: BiliPaiNavKey): BiliPaiNavKey = when (key) {
    is BiliPaiNavKey.BangumiDetail -> BiliPaiNavKey.BangumiPlayer(
        seasonId = key.seasonId,
        epId = key.epId,
        mediaId = key.mediaId,
    )
    else -> key
}
