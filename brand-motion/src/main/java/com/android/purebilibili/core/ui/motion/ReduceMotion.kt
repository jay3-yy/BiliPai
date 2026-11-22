package com.android.purebilibili.core.ui.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * 系统是否处于「减弱动效」状态。
 *
 * 以开发者选项/无障碍的动画时长缩放为准:`ANIMATOR_DURATION_SCALE == 0` 表示用户
 * 关闭了动画(无障碍「移除动画」也会把它置 0)。通过 ContentObserver 实时跟随变化。
 */
internal fun readSystemReduceMotion(resolver: ContentResolver): Boolean {
    val scale = Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale == 0f
}

private class SharedReduceMotionEntry(
    private val resolver: ContentResolver,
) {
    val state: MutableState<Boolean> = mutableStateOf(readSystemReduceMotion(resolver))
    private var activeListeners = 0
    private var observer: ContentObserver? = null

    fun acquire() {
        if (activeListeners++ == 0) {
            val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    state.value = readSystemReduceMotion(resolver)
                }
            }
            observer = contentObserver
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                contentObserver,
            )
            state.value = readSystemReduceMotion(resolver)
        }
    }

    fun release() {
        if (--activeListeners <= 0) {
            activeListeners = 0
            observer?.let { resolver.unregisterContentObserver(it) }
            observer = null
        }
    }
}

private val sharedReduceMotionLock = Any()
private val sharedReduceMotionEntries = WeakHashMap<ContentResolver, SharedReduceMotionEntry>()

private fun getOrCreateSharedReduceMotionEntry(resolver: ContentResolver): SharedReduceMotionEntry =
    synchronized(sharedReduceMotionLock) {
        sharedReduceMotionEntries.getOrPut(resolver) { SharedReduceMotionEntry(resolver) }
    }

@Composable
fun rememberSystemReduceMotion(): Boolean {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val entry = remember(resolver) { getOrCreateSharedReduceMotionEntry(resolver) }
    DisposableEffect(entry) {
        synchronized(sharedReduceMotionLock) { entry.acquire() }
        onDispose { synchronized(sharedReduceMotionLock) { entry.release() } }
    }
    return entry.state.value
}
