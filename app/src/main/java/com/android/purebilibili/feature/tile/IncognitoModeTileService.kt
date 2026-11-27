package com.android.purebilibili.feature.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import com.android.purebilibili.R
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.audio.player.SystemMediaPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 下拉控制中心「无痕模式」快捷磁贴。
 * 一键切换不记录播放与搜索历史状态，并同步至 :playback 独立音频进程镜像。
 */
class IncognitoModeTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var toggleJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        applyTile(SettingsManager.isPrivacyModeEnabledSync(applicationContext))
    }

    override fun onClick() {
        super.onClick()
        val action = Runnable { togglePrivacyMode() }
        if (isLocked) {
            unlockAndRun(action)
        } else {
            action.run()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun togglePrivacyMode() {
        toggleJob?.cancel()
        toggleJob = serviceScope.launch {
            val appContext = applicationContext
            val nextEnabled = !SettingsManager.isPrivacyModeEnabledSync(appContext)
            try {
                SettingsManager.setPrivacyModeEnabled(appContext, nextEnabled)
                SystemMediaPreferences.updatePrivateHistory(appContext, nextEnabled)
                applyTile(nextEnabled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w(TAG, "Failed to toggle privacy mode tile", error)
                applyTile(SettingsManager.isPrivacyModeEnabledSync(appContext))
            }
        }
    }

    private fun applyTile(enabled: Boolean) {
        val tile = qsTile ?: return
        val label = getString(R.string.qs_tile_incognito_label)
        val presentation = resolveIncognitoTilePresentation(
            enabled = enabled,
            tileLabel = label,
            onSubtitle = getString(R.string.qs_tile_state_on),
            offSubtitle = getString(R.string.qs_tile_state_off),
        )
        tile.label = label
        tile.state = presentation.state
        tile.icon = Icon.createWithResource(this, R.drawable.ms_visibility_off_24)
        tile.contentDescription = presentation.contentDescription
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = presentation.subtitle
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "IncognitoModeTile"

        fun requestListeningState(context: Context) {
            val appContext = context.applicationContext ?: context
            TileService.requestListeningState(
                appContext,
                ComponentName(appContext, IncognitoModeTileService::class.java),
            )
        }
    }
}
