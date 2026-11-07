package com.android.purebilibili.feature.video

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.withResumed
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.WeakHashMap

/** 独立窗口入口；官方转场的启动配置与播放参数分别传递。 */
internal object VideoActivityLauncher {
    private val openingJobs = WeakHashMap<ComponentActivity, Job>()

    suspend fun open(
        activity: ComponentActivity,
        request: VideoOpenRequest,
        transitionOptions: android.os.Bundle? = null,
        officialTransitionToken: String? = null,
        appTransitionToken: String? = null,
        canLaunch: () -> Boolean = { true },
    ): Boolean {
        val job = requireNotNull(currentCoroutineContext()[Job])
        val previous = openingJobs.put(activity, job)
        if (previous !== job) previous?.cancel()
        try {
            activity.lifecycle.withResumed { }
            currentCoroutineContext().ensureActive()
            if (!canLaunch()) return false
            val target = if (appTransitionToken != null) AppVideoActivity::class.java else VideoActivity::class.java
            val intent = Intent(activity, target).putExtras(request.toBundle())
            appTransitionToken?.let { intent.putExtra(AppVideoCardWindow.EXTRA_SESSION, it) }
            officialTransitionToken?.let { intent.putExtra(SystemVideoCardTransition.EXTRA_SESSION, it) }
            val manager = MiniPlayerManager.getInstance(activity)
            manager.isNavigatingToVideo = true
            manager.haltForeignPlaybackForIncomingVideo(request.bvid)
            manager.exitMiniMode(animate = false)
            if (activity is VideoActivity) {
                intent.putExtra("video_parent_is_video", true)
                activity.onOpeningVideoChild()
            }
            try {
                val options = if (appTransitionToken != null) {
                    android.app.ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
                } else transitionOptions
                activity.startActivity(intent, options)
            } catch (error: SecurityException) {
                if (activity is VideoActivity) activity.onVideoChildLaunchRejected()
                manager.isNavigatingToVideo = false
                throw error
            }
            return true
        } finally {
            if (openingJobs[activity] === job) openingJobs.remove(activity)
        }
    }
}
