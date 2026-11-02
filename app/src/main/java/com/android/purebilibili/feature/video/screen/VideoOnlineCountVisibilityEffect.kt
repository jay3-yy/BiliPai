package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.feature.video.playback.policy.OnlineCountMediaKey
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel

/** 详情与竖屏页分别持有可见消费者，保留页面的退出不能撤销当前页面的需求。 */
@Composable
internal fun VideoOnlineCountVisibilityEffect(
    viewModel: VideoPlaybackViewModel,
    bvid: String,
    cid: Long,
    pageActive: Boolean,
) {
    val owner = remember(viewModel) { Any() }
    val visualWorkActive = rememberVisualWorkActive()
    val active = pageActive && visualWorkActive
    DisposableEffect(viewModel, owner, bvid, cid, active) {
        if (active && bvid.isNotBlank() && cid > 0L) {
            viewModel.setOnlineCountConsumer(owner, OnlineCountMediaKey(bvid, cid))
        }
        onDispose { viewModel.setOnlineCountConsumer(owner, null) }
    }
}
