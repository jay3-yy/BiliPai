package com.android.purebilibili.feature.video.playback.loader

import android.content.Context
import android.net.NetworkCapabilities
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 仅在当前加载请求内复用；后续请求重新读取网络与偏好。 */
internal data class PlaybackLoadPreferences(
    val storedQuality: Int,
    val autoHighestQuality: Boolean,
    val defaultAudioQuality: Int,
    val lastSelectedAudioQuality: Int,
    val videoCodec: String,
    val secondVideoCodec: String,
    val clickToPlay: Boolean,
    val isOnMobileNetwork: Boolean,
    val dataSaverMode: SettingsManager.DataSaverMode,
    val preferredPlaybackSpeed: Float,
)

internal suspend fun readPlaybackLoadPreferences(context: Context): PlaybackLoadPreferences =
    withContext(Dispatchers.IO) {
        val network = NetworkUtils.getActiveNetworkCapabilities(context)
        PlaybackLoadPreferences(
            storedQuality = NetworkUtils.getDefaultQualityId(
                context,
                isOnWifi = network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            ),
            autoHighestQuality = SettingsManager.getAutoHighestQualitySync(context),
            defaultAudioQuality = PlayerSettingsStore.getCachedDefaultAudioQuality(context),
            lastSelectedAudioQuality = PlayerSettingsStore.getCachedLastSelectedAudioQuality(context),
            videoCodec = SettingsManager.getVideoCodecSync(context),
            secondVideoCodec = SettingsManager.getVideoSecondCodecSync(context),
            clickToPlay = SettingsManager.getClickToPlaySync(context),
            isOnMobileNetwork = network?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            dataSaverMode = SettingsManager.getDataSaverModeSync(context),
            preferredPlaybackSpeed = SettingsManager.getPreferredPlaybackSpeedSync(context),
        )
    }
