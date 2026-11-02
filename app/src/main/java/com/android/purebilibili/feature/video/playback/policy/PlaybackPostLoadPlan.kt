package com.android.purebilibili.feature.video.playback.policy

internal enum class PlaybackPostLoadTask {
    PLAYER_INFO,
    VIDEO_SHOT,
    REFRESH_DEFERRED_SIGNALS,
    LOAD_FOLLOWING_MIDS,
    OWNER_STATS,
    VIDEO_TAGS,
    AI_SUMMARY,
    ONLINE_COUNT,
    HEARTBEAT,
    PLUGIN_ON_VIDEO_LOAD,
    START_PLUGIN_CHECK
}

internal data class PlaybackPostLoadTaskSpec(
    val task: PlaybackPostLoadTask,
    val delayMs: Long
)

internal fun buildPlaybackPostLoadPlan(
    isLoggedIn: Boolean
): List<PlaybackPostLoadTaskSpec> {
    val plan = mutableListOf(
        PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.PLAYER_INFO,
            delayMs = 150L
        ),
        PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.VIDEO_SHOT,
            delayMs = 150L
        ),
        PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.OWNER_STATS,
            delayMs = 450L
        ),
        // 标签行会插在简介与操作栏之间，晚到必然把下方内容整体下推。
        // 提前到首批发起，让插入发生在进入详情页的共享元素转场窗口内被掩盖，
        // 而不是转场结束后单独跳一下。（标签请求很小，不与播放首帧抢占带宽）
        PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.VIDEO_TAGS,
            delayMs = 0L
        ),
        PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.HEARTBEAT,
            delayMs = 900L
        ),
        PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.PLUGIN_ON_VIDEO_LOAD,
            delayMs = 900L
        ),
        PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.START_PLUGIN_CHECK,
            delayMs = 1_200L
        ),
        PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.AI_SUMMARY,
            delayMs = 1_500L
        )
    )

    if (isLoggedIn) {
        // 交互状态(点赞/关注/收藏/投币)直接决定详情页按钮的最终外观，
        // 是唯一"晚到即被用户看见"的信号，因此不再排队等待，加载完成后立即发起。
        plan += PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.REFRESH_DEFERRED_SIGNALS,
            delayMs = 0L
        )
        plan += PlaybackPostLoadTaskSpec(
            task = PlaybackPostLoadTask.LOAD_FOLLOWING_MIDS,
            delayMs = 450L
        )
    }

    // 这里只绑定人数观察的内容身份，实际网络请求由实时设置和可见消费者控制。
    plan += PlaybackPostLoadTaskSpec(
        task = PlaybackPostLoadTask.ONLINE_COUNT,
        delayMs = 1_500L,
    )

    return plan.sortedWith(
        compareBy<PlaybackPostLoadTaskSpec> { spec -> spec.delayMs }
            .thenBy { spec -> spec.task.ordinal }
    )
}
