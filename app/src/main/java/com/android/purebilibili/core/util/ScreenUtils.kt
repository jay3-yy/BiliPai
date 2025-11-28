package com.android.purebilibili.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object ScreenUtils {
    fun setFullScreen(context: Context, isFull: Boolean) {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isFull) {
            // 切横屏
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // 隐藏状态栏和导航栏
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // 切竖屏
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // 显示状态栏
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}