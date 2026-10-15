package com.android.purebilibili.core.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.purebilibili.feature.settings.DarkThemeSchedule
import com.android.purebilibili.feature.settings.isScheduledDarkTheme
import java.util.Calendar

private fun currentLocalMinute(): Int = Calendar.getInstance().let {
    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
}

/** Minute broadcasts cover visible boundary changes; resume/clock/timezone changes recheck immediately.
 * No alarm, background worker or exact-alarm permission is needed for an app appearance preference.
 */
@Composable
internal fun rememberScheduledDarkTheme(schedule: DarkThemeSchedule): Boolean {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var minute by remember { mutableIntStateOf(currentLocalMinute()) }
    DisposableEffect(context, lifecycle, schedule.enabled) {
        if (!schedule.enabled) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                minute = currentLocalMinute()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                minute = currentLocalMinute()
            }
        }
        lifecycle.addObserver(observer)
        minute = currentLocalMinute()
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycle.removeObserver(observer)
        }
    }
    return isScheduledDarkTheme(schedule, minute)
}
