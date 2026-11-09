package com.android.purebilibili.feature.video

import android.content.Intent
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.lifecycle.withResumed
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.WeakHashMap
import java.lang.ref.WeakReference
import java.util.UUID
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation

/** 独立窗口入口；官方转场的启动配置与播放参数分别传递。 */
internal object VideoActivityLauncher {
    private val openingJobs = WeakHashMap<ComponentActivity, Job>()
    private const val EXTRA_PARENT_SESSION = "video_parent_window_session"
    private val parentWindows = mutableMapOf<String, WeakReference<ComponentActivity>>()
    private val keepLandscapeBack = WeakHashMap<Activity, Boolean>()

    internal fun keepLandscapeForBack(activity: Activity) {
        keepLandscapeBack[activity] = true
        activity.applyPlayerRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        val parent = activity.intent?.getStringExtra(EXTRA_PARENT_SESSION)?.let { parentWindows[it]?.get() }
        if (parent != null && !parent.isFinishing && !parent.isDestroyed) {
            parent.applyPlayerRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        }
    }

    internal fun consumeLandscapeBack(activity: Activity): Boolean = keepLandscapeBack.remove(activity) == true

    internal fun releaseParent(activity: Activity) {
        activity.intent?.getStringExtra(EXTRA_PARENT_SESSION)?.let { parentWindows.remove(it) }
        keepLandscapeBack.remove(activity)
    }

    suspend fun open(
        activity: ComponentActivity,
        request: VideoOpenRequest,
        transitionOptions: android.os.Bundle? = null,
        officialTransitionToken: String? = null,
        canLaunch: () -> Boolean = { true },
    ): Boolean {
        val job = requireNotNull(currentCoroutineContext()[Job])
        val previous = openingJobs.put(activity, job)
        if (previous !== job) previous?.cancel()
        try {
            activity.lifecycle.withResumed { }
            currentCoroutineContext().ensureActive()
            if (!canLaunch()) return false
            val intent = Intent(activity, VideoActivity::class.java).putExtras(request.toBundle())
            officialTransitionToken?.let { intent.putExtra(SystemVideoCardTransition.EXTRA_SESSION, it) }
            val manager = MiniPlayerManager.getInstance(activity)
            manager.isNavigatingToVideo = true
            manager.haltForeignPlaybackForIncomingVideo(request.bvid)
            manager.exitMiniMode(animate = false)
            if (activity is VideoActivity) {
                intent.putExtra("video_parent_is_video", true)
                activity.onOpeningVideoChild()
            }
            val parentSession = UUID.randomUUID().toString()
            intent.putExtra(EXTRA_PARENT_SESSION, parentSession)
            parentWindows[parentSession] = WeakReference(activity)
            try {
                activity.startActivity(intent, transitionOptions)
            } catch (error: SecurityException) {
                parentWindows.remove(parentSession)
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
