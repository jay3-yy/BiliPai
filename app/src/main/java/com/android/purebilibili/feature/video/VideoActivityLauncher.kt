package com.android.purebilibili.feature.video

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.withResumed
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.util.WeakHashMap

/** 独立窗口入口；官方转场的启动配置与播放参数分别传递。 */
internal object VideoActivityLauncher {
    private val openingJobs = WeakHashMap<ComponentActivity, Job>()

    suspend fun open(
        activity: ComponentActivity,
        request: VideoOpenRequest,
        transitionOptions: android.os.Bundle? = null,
        officialTransitionToken: String? = null,
    ) {
        val job = requireNotNull(currentCoroutineContext()[Job])
        val previous = openingJobs.put(activity, job)
        if (previous !== job) previous?.cancel()
        try {
            activity.lifecycle.withResumed { }
            val intent = Intent(activity, VideoActivity::class.java).putExtras(request.toBundle())
            officialTransitionToken?.let { intent.putExtra(OfficialVideoCardTransition.EXTRA_SESSION, it) }
            val manager = MiniPlayerManager.getInstance(activity)
            manager.isNavigatingToVideo = true
            manager.haltForeignPlaybackForIncomingVideo(request.bvid)
            manager.exitMiniMode(animate = false)
            if (activity is VideoActivity) {
                intent.putExtra("video_parent_is_video", true)
                activity.onOpeningVideoChild()
            }
            try {
                activity.startActivity(intent, transitionOptions)
            } catch (error: SecurityException) {
                if (activity is VideoActivity) activity.onVideoChildLaunchRejected()
                manager.isNavigatingToVideo = false
                throw error
            }
        } finally {
            if (openingJobs[activity] === job) openingJobs.remove(activity)
        }
    }
}
