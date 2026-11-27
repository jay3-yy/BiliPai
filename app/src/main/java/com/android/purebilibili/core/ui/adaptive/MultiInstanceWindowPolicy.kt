package com.android.purebilibili.core.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.android.purebilibili.feature.video.VideoActivity
import com.android.purebilibili.feature.video.VideoOpenRequest

/**
 * 平板、折叠屏展开态或多窗口模式下允许以相邻多实例方式打开独立视频窗口。
 */
internal fun canLaunchAdjacentMultiInstance(
    isLargeScreen: Boolean,
    isInMultiWindowMode: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = sdkInt >= Build.VERSION_CODES.N && (isLargeScreen || isInMultiWindowMode)

internal fun buildAdjacentVideoWindowIntent(
    context: Context,
    bvid: String,
    cid: Long = 0L,
    coverUrl: String = "",
): Intent? {
    val cleanBvid = bvid.trim()
    if (cleanBvid.isEmpty()) return null
    val request = VideoOpenRequest(
        bvid = cleanBvid,
        cid = cid.coerceAtLeast(0L),
        coverUrl = coverUrl,
    )
    return Intent(context, VideoActivity::class.java)
        .putExtras(request.toBundle())
        .addFlags(
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )
}

internal fun launchVideoInAdjacentWindow(
    context: Context,
    bvid: String,
    cid: Long = 0L,
    coverUrl: String = "",
): Boolean {
    val intent = buildAdjacentVideoWindowIntent(
        context = context,
        bvid = bvid,
        cid = cid,
        coverUrl = coverUrl,
    ) ?: return false
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

internal fun isActivityMultiWindowCompat(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode
}
