package com.android.purebilibili.feature.plugin.googlecast

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.purebilibili.core.plugin.CastPluginApi
import com.android.purebilibili.core.plugin.CastDiscoveryRequirement
import com.android.purebilibili.feature.cast.hasRawLocalNetworkAccess
import com.android.purebilibili.core.plugin.CastPluginMediaRequest
import com.android.purebilibili.core.plugin.CastPluginPlaybackState
import com.android.purebilibili.core.plugin.CastPluginRoute
import com.android.purebilibili.core.plugin.PluginCapability
import com.android.purebilibili.core.plugin.PluginCapabilityManifest
import com.android.purebilibili.core.util.Logger
import com.google.android.gms.cast.framework.CastContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import kotlinx.coroutines.flow.StateFlow

const val GOOGLE_CAST_PLUGIN_ID = "google_cast"
private const val TAG = "GoogleCastPlugin"

internal fun resolveGoogleCastDiscoveryRequirement(sdkInt: Int): CastDiscoveryRequirement =
    if (sdkInt >= 37) CastDiscoveryRequirement.RAW_LOCAL_NETWORK else CastDiscoveryRequirement.NONE

class GoogleCastPlugin : CastPluginApi {

    override val id = GOOGLE_CAST_PLUGIN_ID
    override val name = "Google Cast"
    override val description = "将视频投屏到 Chromecast / Google Cast 设备"
    override val version = "0.1.1"
    override val author = "Leko (lekoOwO)"
    // This plugin uses an app-owned route list, not the system Output Switcher.
    override val discoveryRequirement: CastDiscoveryRequirement
        get() = resolveGoogleCastDiscoveryRequirement(Build.VERSION.SDK_INT)
    override val icon: ImageVector = Icons.Rounded.Cast
    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = "com.android.purebilibili.feature.plugin.googlecast.GoogleCastPlugin",
        capabilities = setOf(
            PluginCapability.PLAYER_STATE,
            PluginCapability.PLAYER_CONTROL,
            PluginCapability.NETWORK,
            PluginCapability.PLUGIN_STORAGE
        )
    )

    override val routes: StateFlow<List<CastPluginRoute>> = GoogleCastRouteManager.routes
    override val playbackState: StateFlow<CastPluginPlaybackState> = GoogleCastPlaybackController.playbackState
    override val isDiscovering: StateFlow<Boolean> = GoogleCastRouteManager.isDiscovering

    override fun startRouteDiscovery(context: Context) {
        GoogleCastRouteManager.startDiscovery(context)
    }

    override fun stopRouteDiscovery() {
        GoogleCastRouteManager.stopDiscovery()
    }

    override suspend fun cast(
        context: Context,
        route: CastPluginRoute,
        media: CastPluginMediaRequest
    ): Result<Unit> {
        if (Build.VERSION.SDK_INT >= 37 && !hasRawLocalNetworkAccess(context)) {
            onDiscoveryAccessRevoked()
            return Result.failure(SecurityException("请先允许本地网络权限，再连接 Google Cast 设备"))
        }
        val result = GoogleCastMediaLoader.loadMedia(
            context = context,
            routeId = route.routeId,
            url = media.url,
            title = media.title,
            creator = media.creator,
            contentType = media.contentType,
            startPositionMs = media.startPositionMs,
            autoplay = media.autoplay
        )
        if (result.isSuccess) {
            val session = CastContext.getSharedInstance(context).sessionManager.currentCastSession
            if (session != null) {
                GoogleCastPlaybackController.attach(
                    session = session,
                    title = media.title,
                    deviceLabel = route.name
                )
            }
        }
        return result
    }

    override suspend fun play(): Result<Unit> = GoogleCastPlaybackController.play()

    override suspend fun pause(): Result<Unit> = GoogleCastPlaybackController.pause()

    override suspend fun seek(positionMs: Long): Result<Unit> = GoogleCastPlaybackController.seek(positionMs)

    override suspend fun onEnable() {
        Logger.d(TAG, "Google Cast plugin enabled")
    }

    override suspend fun onDisable() {
        GoogleCastPlaybackController.detach()
        GoogleCastRouteManager.stopDiscovery()
        Logger.d(TAG, "Google Cast plugin disabled")
    }
}
