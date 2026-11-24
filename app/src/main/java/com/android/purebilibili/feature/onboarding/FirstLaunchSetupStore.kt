package com.android.purebilibili.feature.onboarding

import android.content.Context

internal const val FIRST_LAUNCH_SETUP_VERSION = 1

/** Persistence for the post-agreement first-launch theme & material setup flow. */
object FirstLaunchSetupStore {
    private const val PREFS_NAME = "first_launch_setup"
    private const val KEY_COMPLETED_VERSION = "completed_version"
    private const val KEY_FULLSCREEN_GESTURE_HINT_SHOWN = "fullscreen_gesture_hint_shown"

    fun isCompleted(context: Context): Boolean = completedVersion(context) >= FIRST_LAUNCH_SETUP_VERSION

    fun completedVersion(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_COMPLETED_VERSION, 0)

    fun markCompleted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COMPLETED_VERSION, FIRST_LAUNCH_SETUP_VERSION)
            .apply()
    }

    fun isFullscreenGestureHintShown(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FULLSCREEN_GESTURE_HINT_SHOWN, false)

    fun markFullscreenGestureHintShown(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FULLSCREEN_GESTURE_HINT_SHOWN, true)
            .apply()
    }

    fun resetForReplay(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_COMPLETED_VERSION)
            .apply()
    }
}
