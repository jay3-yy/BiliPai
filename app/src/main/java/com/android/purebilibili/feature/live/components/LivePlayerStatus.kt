package com.android.purebilibili.feature.live.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** 全屏控制栏的时间、电量只观察系统广播，不参与播放器状态与流切换。 */
@Composable
internal fun LivePlayerStatus(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var battery by remember(context) { mutableStateOf<Int?>(null) }
    var time by remember(context) { mutableStateOf(DateFormat.format("HH:mm", System.currentTimeMillis()).toString()) }
    DisposableEffect(context) {
        fun update(intent: Intent?) {
            time = DateFormat.format("HH:mm", System.currentTimeMillis()).toString()
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) battery = (level * 100 / scale).coerceIn(0, 100)
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = update(intent)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        update(ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(battery?.let { "$it%" } ?: "—%", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 30.dp))
        Text(time, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 35.dp))
    }
}
