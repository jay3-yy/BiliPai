package com.android.purebilibili.feature.plugin.googlecast

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.android.purebilibili.core.plugin.CastPluginRoute
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.cast.hasRawLocalNetworkAccess
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object GoogleCastRouteManager {
    private const val TAG = "GoogleCastRouteManager"
    private const val ACTIVE_SCAN_WINDOW_MS = 8_000L

    private val _routes = MutableStateFlow<List<CastPluginRoute>>(emptyList())
    val routes: StateFlow<List<CastPluginRoute>> = _routes.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var mediaRouter: MediaRouter? = null
    private var selector: MediaRouteSelector? = null
    private var callback: MediaRouter.Callback? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val endDiscoveryRunnable = Runnable {
        _isDiscovering.value = false
    }

    private val castControlCategory: String by lazy {
        CastMediaControlIntent.categoryForCast(
            CastReceiverPolicy.resolveReceiverApplicationId()
        )
    }

    private val routeCache = mutableMapOf<String, MediaRouter.RouteInfo>()

    fun startDiscovery(context: Context) {
        if (Build.VERSION.SDK_INT >= 37 && !hasRawLocalNetworkAccess(context)) {
            stopDiscovery()
            return
        }
        ensureCastContext(context)
        if (mediaRouter == null) {
            val router = MediaRouter.getInstance(context.applicationContext)
            val sel = MediaRouteSelector.Builder()
                .addControlCategory(castControlCategory)
                .build()
            val cb = object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    updateRoutes(router)
                }

                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    updateRoutes(router)
                }

                override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    updateRoutes(router)
                }
            }
            // Active scan is important when the user explicitly opens the cast sheet.
            router.addCallback(
                sel,
                cb,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
                    MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            mediaRouter = router
            selector = sel
            callback = cb
            updateRoutes(router)
        } else {
            // Refresh with another active-scan window.
            mediaRouter?.let { updateRoutes(it) }
        }
        markDiscovering()
    }

    fun stopDiscovery() {
        mainHandler.removeCallbacks(endDiscoveryRunnable)
        val router = mediaRouter
        val cb = callback
        if (router != null && cb != null) router.removeCallback(cb)
        mediaRouter = null
        selector = null
        callback = null
        _isDiscovering.value = false
        _routes.value = emptyList()
        routeCache.clear()
    }

    fun getCachedRoute(routeId: String): MediaRouter.RouteInfo? {
        return routeCache[routeId]
    }

    private fun markDiscovering() {
        _isDiscovering.value = true
        mainHandler.removeCallbacks(endDiscoveryRunnable)
        mainHandler.postDelayed(endDiscoveryRunnable, ACTIVE_SCAN_WINDOW_MS)
    }

    private fun ensureCastContext(context: Context) {
        runCatching {
            CastContext.getSharedInstance(context.applicationContext)
        }.onFailure { error ->
            Logger.w(TAG, "CastContext init failed (GMS missing or unavailable): ${error.message}")
        }
    }

    private fun updateRoutes(router: MediaRouter) {
        val castRoutes = router.routes.mapNotNull { route ->
            routeCache[route.id] = route
            toCastPluginRoute(
                routeId = route.id,
                name = route.name,
                description = route.description,
                deviceType = route.deviceType,
                isDefaultOrBluetooth = route.isDefault || route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP,
                supportsCastCategory = route.supportsControlCategory(castControlCategory)
            )
        }.distinctBy { it.routeId }
        _routes.value = castRoutes
        Logger.d(TAG, "Cast routes updated: ${castRoutes.size}")
    }
}
