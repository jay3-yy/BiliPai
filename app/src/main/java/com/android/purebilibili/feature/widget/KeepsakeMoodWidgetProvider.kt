package com.android.purebilibili.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.personal.CompletionKeepsakeStore
import com.android.purebilibili.feature.video.player.resolveActivePlaybackLaunchIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 桌面「每日纪念」情绪小组件：
 * 展示按时段问候与本地完结短评（无记录时展示柔和陪伴句），仅响应系统刷新、保存事件与手动切换，不轮询。
 */
class KeepsakeMoodWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        runPending(context) {
            renderAll(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHUFFLE) {
            super.onReceive(context, intent)
            return
        }
        runPending(context) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val nextOffset = (prefs.getInt(KEY_MANUAL_OFFSET, 0) + 1) % 10_000
            prefs.edit().putInt(KEY_MANUAL_OFFSET, nextOffset).apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KeepsakeMoodWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                renderAll(context, manager, ids)
            }
        }
    }

    private fun runPending(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(5_000L) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w(TAG, "Keepsake mood widget update failed", error)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        private const val TAG = "KeepsakeMoodWidget"
        private const val PREFS_NAME = "keepsake_mood_widget"
        private const val KEY_MANUAL_OFFSET = "manual_offset"
        private const val ACTION_SHUFFLE = "com.android.purebilibili.widget.KEEPSAKE_SHUFFLE"

        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, KeepsakeMoodWidgetProvider::class.java))
            if (ids.isEmpty()) return
            appContext.sendBroadcast(
                Intent(appContext, KeepsakeMoodWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private suspend fun renderAll(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val records = CompletionKeepsakeStore.loadRecords(context)
            val offset = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_MANUAL_OFFSET, 0)
            val calendar = Calendar.getInstance()
            val epochDay = TimeUnit.MILLISECONDS.toDays(calendar.timeInMillis)
            val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val display = pickKeepsakeWidgetDisplayModel(
                records = records,
                epochDay = epochDay,
                hourOfDay = hourOfDay,
                manualOffset = offset,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_keepsake_mood)
            views.setTextViewText(R.id.widget_keepsake_greeting, "${display.greeting} · ${display.badge}")
            views.setTextViewText(R.id.widget_keepsake_body, display.bodyText)
            views.setTextViewText(R.id.widget_keepsake_footer, display.footerText)

            val recapIntent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(KEEPSAKE_WIDGET_RECAP_DEEP_LINK))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val recapPendingIntent = PendingIntent.getActivity(
                context,
                203,
                recapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val deepLink = resolveKeepsakeWidgetDeepLink(display)
            val openPendingIntent = if (deepLink != null) {
                recapPendingIntent
            } else {
                val videoIntent = resolveActivePlaybackLaunchIntent(
                    context = context,
                    bvid = display.targetBvid,
                )
                PendingIntent.getActivity(
                    context,
                    201,
                    videoIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_keepsake_root, openPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_keepsake_greeting, recapPendingIntent)

            val shuffleIntent = Intent(context, KeepsakeMoodWidgetProvider::class.java)
                .setAction(ACTION_SHUFFLE)
                .setData(Uri.parse("bilipai-widget://keepsake/shuffle"))
            val shufflePendingIntent = PendingIntent.getBroadcast(
                context,
                202,
                shuffleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_keepsake_shuffle, shufflePendingIntent)

            manager.updateAppWidget(ids, views)
        }
    }
}
