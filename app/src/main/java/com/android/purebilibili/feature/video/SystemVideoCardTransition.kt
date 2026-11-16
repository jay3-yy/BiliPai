package com.android.purebilibili.feature.video

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.Lifecycle
import androidx.window.layout.WindowMetricsCalculator
import com.android.purebilibili.core.ui.transition.resolveVideoCardSourceClipRadiusPx
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.navigation3.VideoCardTransitionSession
import com.itgsa.animation.sharedview.ViewAnimationConfig
import com.itgsa.animation.sharedview.ViewAnimationExtras
import com.itgsa.animation.sharedview.ViewAnimationListener
import com.itgsa.animation.sharedview.ViewAnimationManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.coroutines.resume

internal data class SystemVideoCardCapability(
    val available: Boolean,
    val configurableBlur: Boolean,
    /** EXTRA_BACK_ANIM_TYPE 原始声明；null = 设备未声明，按协议不假定类型可写。 */
    val backAnimMetadata: String?,
    val roundedWindow: Boolean = false,
    val oplusWindow: Boolean = false,
    val vivoWindow: Boolean = false,
    /** 标准 SDK 与原生无缝门面并存，使用该门面的返回更新契约。 */
    val oplusSdkWindow: Boolean = false,
    val systemConditionSummary: String? = null,
)

/** 系统窗口转场调度：标准协议优先，再尝试旧无缝、OneShot 与圆角窗口协议；不可用时回退普通页面转场。
 * 启动时校验来源卡片，返回沿用本次窗口已提交的卡片像素与落点。主线程访问。
 */
internal object SystemVideoCardTransition {
    const val EXTRA_SESSION = "official_video_card_session"

    private data class WindowGeometry(
        val bounds: Rect,
        val displayId: Int,
        val rotation: Int,
        val densityDpi: Int,
    )

    private fun windowGeometry(activity: Activity): WindowGeometry {
        // 后台来源窗口不依赖 DecorView 是否仍有布局或附着到显示设备。
        @Suppress("DEPRECATION")
        val display = activity.windowManager.defaultDisplay
        // 校验真实窗口边界；播放时系统栏显隐不等于窗口缩放或分屏变化。
        val windowBounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        return WindowGeometry(
            Rect(windowBounds),
            display.displayId, display.rotation, activity.resources.configuration.densityDpi,
        )
    }

    private class Session(
        val sourceRoute: String?,
        val sourceActivity: WeakReference<ComponentActivity>,
        val sourceWindow: WindowGeometry,
        val thumbnail: Bitmap,
        val canRefreshBitmap: Boolean,
        val canConfigureBlur: Boolean,
        val canConfigureBackgroundScale: Boolean,
        val initialRealtimeBlurEnabled: Boolean,
        val roundedSource: WeakReference<ComponentActivity>?,
        val oplusWindow: Boolean,
        val vivoWindow: Boolean,
        val oplusSdkWindow: Boolean,
        val openingBounds: Rect,
        val openingRadius: Float,
        val openingSeed: VideoDetailOpeningSeed?,
    ) {
        val requestedAtMs = SystemClock.uptimeMillis()
        @Volatile var openingEvent = "awaiting_start"
        var oplusOpening: OplusVideoCardTransition.Opening? = null
        var vivoOpening: VivoVideoCardTransition.Opening? = null
    }

    private val sessions = mutableMapOf<String, Session>()
    private var latestWindowRequestToken: String? = null

    fun canAttempt(enabled: Boolean, source: VideoCardTransitionSession, activity: Activity? = null): Boolean {
        val sourceReady = enabled && source.hasUsableSourceGeometry && source.sourceAnchor != null
        val capability = if (sourceReady) deviceCapability(activity) else null
        val supported = capability?.available
        val allowed = sourceReady && supported == true
        Logger.i("SystemVideoCardTransition", "入口判定: route=${source.sourceRoute} enabled=$enabled geometry=${source.hasUsableSourceGeometry} " +
            "anchor=${source.sourceAnchor != null} supported=$supported allowed=$allowed " +
            "conditions=${capability?.systemConditionSummary}")
        return allowed
    }

    fun deviceCapability(activity: Activity? = null): SystemVideoCardCapability {
        // 标准 SDK 的能力探测只检查静态开关；原生无缝实现还会按当前窗口拒绝请求。
        val oplusSystemManager = OplusVideoCardTransition.hasSystemManager
        val conditions = if (oplusSystemManager) OplusVideoCardTransition.conditions(activity) else null
        if (conditions?.blocked == true) {
            return SystemVideoCardCapability(false, false, null, systemConditionSummary = conditions.toString())
        }
        if (ViewAnimationManager.isViewAnimationSupported() > 0) {
            val extras = ViewAnimationManager.getViewAnimationExtras()
            return SystemVideoCardCapability(
                available = true,
                configurableBlur = extras?.containsKey(ViewAnimationExtras.EXTRA_BLUR) == true,
                backAnimMetadata = extras?.get(ViewAnimationExtras.EXTRA_BACK_ANIM_TYPE),
                oplusSdkWindow = oplusSystemManager,
                systemConditionSummary = conditions?.toString(),
            )
        }
        if (OplusVideoCardTransition.available) {
            return SystemVideoCardCapability(
                available = true, configurableBlur = false,
                backAnimMetadata = null, oplusWindow = true, systemConditionSummary = conditions.toString(),
            )
        }
        if (VivoVideoCardTransition.available) {
            return SystemVideoCardCapability(
                available = true, configurableBlur = VivoVideoCardTransition.configurableBlur,
                backAnimMetadata = null, vivoWindow = true,
            )
        }
        return SystemVideoCardCapability(
            RoundedVideoCardTransition.available, configurableBlur = false,
            backAnimMetadata = null, roundedWindow = RoundedVideoCardTransition.available,
        )
    }

    /** false 表示未启动窗口，调用方继续原有应用内导航；失效请求不得继续导航。 */
    suspend fun tryOpen(
        activity: ComponentActivity,
        request: VideoOpenRequest,
        source: VideoCardTransitionSession,
        realtimeBlurEnabled: Boolean,
        sourceBackgroundColor: Int,
        isCurrent: () -> Boolean,
    ): Boolean {
        fun rejected(reason: String): Boolean {
            Logger.i("SystemVideoCardTransition", "未请求系统展开: route=${source.sourceRoute} reason=$reason")
            return false
        }
        val anchor = source.sourceAnchor ?: return rejected("source_anchor_missing")
        if (!anchor.belongsToWindow(activity.window.decorView)) return rejected("source_window_mismatch")
        val sourceWindow = windowGeometry(activity)
        val owner = source.sourcePixelOwner ?: return rejected("pixel_owner_missing")
        if (!CardPositionManager.isActiveVideoCardPixelOwner(owner)) return rejected("pixel_owner_expired")
        val capability = deviceCapability(activity)
        Logger.i("SystemVideoCardTransition", "系统条件: ${capability.systemConditionSummary ?: "由标准接口判定"}")
        if (!capability.available) return rejected("system_protocol_unavailable ${capability.systemConditionSummary.orEmpty()}")
        val layer = CardPositionManager.nativeCardLayerForOwner(owner)
            ?.takeIf { !it.isReleased && it.size.width > 1 && it.size.height > 1 }
        currentCoroutineContext().ensureActive()
        if (!isCurrent() || !CardPositionManager.isActiveVideoCardPixelOwner(owner)) return rejected("request_expired_after_capture")
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !activity.window.decorView.hasWindowFocus()) return rejected("source_window_inactive")
        val extras = if (capability.roundedWindow || capability.oplusWindow || capability.vivoWindow) null
            else ViewAnimationManager.getViewAnimationExtras()
        val windowCoordinates = capability.vivoWindow ||
            officialBoundsUseWindowCoordinates(extras?.get(ViewAnimationExtras.EXTRA_BOUNDS))
        val bounds = anchor.bounds(windowCoordinates, allowPartial = layer != null) ?: return rejected("source_bounds_unavailable")
        val thumbnail = if (layer != null) {
            // 透明行卡的位图背景透明，先垫来源页面底色，避免引擎放大位图时透出双层内容。
            compositeOverBackgroundColor(
                layer.toImageBitmap().asAndroidBitmap(), sourceBackgroundColor,
            ) ?: return rejected("source_pixels_unavailable")
        } else {
            // 截图始终使用窗口坐标，独立于系统转场声明的屏幕 / 窗口坐标系。
            val captureBounds = anchor.bounds(windowCoordinates = true) ?: return rejected("source_bounds_unavailable")
            captureWindowRegion(activity, captureBounds) ?: return rejected("source_pixels_unavailable")
        }
        if (kotlin.math.abs(thumbnail.width - bounds.width()) > 1 ||
            kotlin.math.abs(thumbnail.height - bounds.height()) > 1) return rejected(
                "source_size_mismatch bitmap=${thumbnail.width}x${thumbnail.height} bounds=$bounds")
        val anchorRadius = anchor.radiusPx() ?: return rejected("source_radius_unavailable")
        val radius = resolveVideoCardSourceClipRadiusPx(
            anchorRadiusPx = anchorRadius,
            coverCornerDp = source.sourceChromeSnapshot?.coverPresentation?.cornerRadiusDp,
            density = activity.resources.displayMetrics.density,
        )
        val canConfigureBlur = extras?.containsKey(ViewAnimationExtras.EXTRA_BLUR) == true
        val canConfigureBackgroundScale = extras?.containsKey(ViewAnimationExtras.EXTRA_BACKGROUND_SCALE) == true
        val configuration = Bundle().apply {
            if (canConfigureBlur) putBoolean(ViewAnimationExtras.EXTRA_BLUR, realtimeBlurEnabled)
            if (canConfigureBackgroundScale) putFloat(ViewAnimationExtras.EXTRA_BACKGROUND_SCALE, 1f)
            if (capability.oplusSdkWindow && extras?.containsKey(ViewAnimationExtras.EXTRA_COLOR) == true) {
                putInt(ViewAnimationExtras.EXTRA_COLOR, sourceBackgroundColor)
            }
        }
        val prefix = when {
            capability.oplusWindow -> "oplus:"
            capability.vivoWindow -> "vivo:"
            capability.roundedWindow -> "rounded:"
            else -> "sdk:"
        }
        val token = prefix + UUID.randomUUID()
        val session = Session(
            sourceRoute = source.sourceRoute,
            sourceActivity = WeakReference(activity),
            sourceWindow = sourceWindow,
            thumbnail = thumbnail,
            canRefreshBitmap = extras?.containsKey(ViewAnimationExtras.EXTRA_BITMAP) == true,
            canConfigureBlur = canConfigureBlur,
            canConfigureBackgroundScale = canConfigureBackgroundScale,
            initialRealtimeBlurEnabled = realtimeBlurEnabled,
            roundedSource = if (capability.roundedWindow) WeakReference(activity) else null,
            oplusWindow = capability.oplusWindow,
            vivoWindow = capability.vivoWindow,
            oplusSdkWindow = capability.oplusSdkWindow,
            openingBounds = Rect(bounds),
            openingRadius = radius,
            openingSeed = videoDetailOpeningSeed(thumbnail, source),
        )
        // 启动请求成功不等于系统实际播放；分别记录打开、返回的启动、取消与结束。
        val listener = object : ViewAnimationListener {
            private fun record(event: String, animMode: Int) {
                if (animMode == ViewAnimationManager.ANIM_MODE_OPEN) session.openingEvent = event
                val mode = when (animMode) {
                    ViewAnimationManager.ANIM_MODE_OPEN -> "OPEN"
                    ViewAnimationManager.ANIM_MODE_BACK -> "BACK"
                    else -> animMode.toString()
                }
                Logger.d("SystemVideoCardTransition", "session=$token mode=$mode event=$event " +
                    "elapsedMs=${SystemClock.uptimeMillis() - session.requestedAtMs}")
            }
            override fun onAnimationStart(animMode: Int) = record("start", animMode)
            override fun onAnimationCancel(animMode: Int) = record("cancel", animMode)
            override fun onAnimationEnd(animMode: Int) = record("end", animMode)
        }
        Logger.d("SystemVideoCardTransition", "session=$token 请求展开: bounds=$bounds radiusPx=$radius " +
            "sourceRoute=${source.sourceRoute} sourceWindow=${activity.javaClass.simpleName} " +
            "taskId=${activity.taskId} taskRoot=${activity.isTaskRoot} " +
            "bitmap=${thumbnail.width}x${thumbnail.height} windowCoordinates=$windowCoordinates " +
            "blurRequested=$realtimeBlurEnabled blurSupported=$canConfigureBlur " +
            "backgroundScaleSupported=$canConfigureBackgroundScale extras=$extras")
        latestWindowRequestToken = token
        val optionsBundle = when {
            capability.vivoWindow -> {
                VivoVideoCardTransition.open(activity, thumbnail, bounds, radius, realtimeBlurEnabled, listener)
                    ?.also { session.vivoOpening = it }?.options
            }
            capability.oplusWindow -> OplusVideoCardTransition.open(
                activity, thumbnail, bounds, radius, sourceBackgroundColor, listener,
            )?.also { opening ->
                session.oplusOpening = opening
            }?.options
            capability.roundedWindow -> RoundedVideoCardTransition.options(
                activity, thumbnail, bounds, radius, sourceBackgroundColor,
            ) { event ->
                if (event.startsWith("open_")) session.openingEvent = event
                Logger.i("SystemVideoCardTransition", "session=$token protocol=rounded event=$event")
            }?.toBundle()
            else -> ViewAnimationManager.makeViewAnimationOptions(
                thumbnail, bounds, radius, configuration, listener,
            )?.toBundle()
        } ?: return rejected("system_options_null protocol=$prefix")
        sessions[token] = session
        var launched = false
        try {
            val directPortraitEntryEnabled = runCatching {
                com.android.purebilibili.core.store.SettingsManager
                    .getAutoPortraitFullscreen(activity)
                    .first()
            }.getOrDefault(false)
            val windowRequest = request.forSystemCardTransition(
                isVerticalVideo = request.initialVertical ||
                    com.android.purebilibili.data.repository.VideoRepository.getCachedVerticalFlag(request.bvid) == true,
                directPortraitEntryEnabled = directPortraitEntryEnabled,
            )
            launched = VideoActivityLauncher.open(activity, windowRequest, optionsBundle, token) {
                // 等待窗口恢复后再次核对请求和几何，不用旧截图打开已切换的卡片。
                    isCurrent() && activity.window.decorView.hasWindowFocus() &&
                        windowGeometry(activity) == sourceWindow && anchor.belongsToWindow(activity.window.decorView) &&
                        CardPositionManager.isActiveVideoCardPixelOwner(owner) &&
                        anchor.bounds(windowCoordinates, allowPartial = layer != null) == bounds && anchor.radiusPx() == anchorRadius
            }
            if (!launched) return rejected("request_expired_before_launch")
            Logger.i("SystemVideoCardTransition", "session=$token 系统卡片转场已请求: ${request.bvid}")
            return true
        } catch (error: SecurityException) {
            // 系统服务拒绝本应用的转场准入时，窗口未启动，仍可回到应用内导航。
            Logger.w("SystemVideoCardTransition", "系统拒绝窗口转场，改用普通页面转场", error)
            return false
        } finally {
            if (!launched) {
                sessions.remove(token)
                session.vivoOpening?.abandon()
                // 旧请求可能在新窗口请求提交后才响应协程取消，不能用全局接口结束新转场。
                if (latestWindowRequestToken == token) {
                    if (session.oplusWindow) OplusVideoCardTransition.cancel()
                    else if (!session.vivoWindow && !capability.roundedWindow) ViewAnimationManager.cancelCurrentViewAnimation()
                    latestWindowRequestToken = null
                }
            }
        }
    }

    fun refreshReturn(
        activity: VideoActivity,
        motionEnabled: Boolean,
        realtimeBlurEnabled: Boolean,
        reason: String,
        returnToSource: Boolean,
        preparingToFinish: Boolean = false,
        preferSystemPredictiveBack: Boolean = false,
    ) {
        val token = activity.intent.getStringExtra(EXTRA_SESSION) ?: return
        // 打开配置由系统保留到关闭；播放和窗口恢复不重复覆盖返回参数。
        if (!preparingToFinish) return
        val session = sessions[token]
        val source = session?.sourceActivity?.get()
        // 回卡属于本次窗口会话，不属于详情当前标签、滚动位置或正在播放的视频。
        // 系统已持有打开时的落点；后台来源卡片是否仍参与 Compose 测量不影响往返。
        val sameWindow = session != null && source != null && !source.isFinishing && !source.isDestroyed &&
            windowGeometry(source) == session.sourceWindow && windowGeometry(activity) == session.sourceWindow
        val sourceAvailable = returnToSource && sameWindow
        val bounds = session?.openingBounds?.takeIf { sourceAvailable }
        if (!sourceAvailable) {
            Logger.w("SystemVideoCardTransition", "返回窗口来源不可用: reason=$reason returnToSource=$returnToSource sameWindow=$sameWindow")
        }
        if (token.startsWith("vivo:")) {
            session?.vivoOpening?.prepareReturn(
                session.thumbnail, bounds, session.openingRadius, motionEnabled, realtimeBlurEnabled,
            )
            return
        }
        if (token.startsWith("oplus:")) {
            OplusVideoCardTransition.prepareReturn(
                activity, session?.oplusOpening, session?.openingBounds, bounds, motionEnabled && sourceAvailable,
            )
            return
        }
        if (token.startsWith("rounded:")) {
            session?.roundedSource?.get()?.let { source ->
                RoundedVideoCardTransition.refreshReturn(source, bounds, motionEnabled && sourceAvailable)
            }
            return
        }
        val capability = deviceCapability(activity)
        if (!capability.available || capability.roundedWindow || capability.oplusWindow || capability.vivoWindow) return
        val currentRadius = session?.openingRadius?.takeIf { sourceAvailable }
        val oplusSdkWindow = session?.oplusSdkWindow ?: capability.oplusSdkWindow
        val plan = resolveOfficialVideoCardReturnPlan(
            metadata = capability.backAnimMetadata,
            oplusSdkWindow = oplusSdkWindow,
            preparingToFinish = preparingToFinish,
            motionEnabled = motionEnabled,
            sourceAvailable = sourceAvailable,
            preferSystemPredictiveBack = preferSystemPredictiveBack,
        ) ?: return
        val config = ViewAnimationConfig()
        plan.backAnimType?.let { config.setBackAnimType(it) }
        if (plan.useSourceBounds) config.setBounds(requireNotNull(bounds))
        if (plan.updateRadius) config.setRadius(requireNotNull(currentRadius))
        val bundle = config.toBundle()
        if (session != null && bounds != null && session.canRefreshBitmap &&
            (!oplusSdkWindow || plan.useSourceBounds)) {
            bundle.putParcelable(ViewAnimationExtras.EXTRA_BITMAP, session.thumbnail)
        }
        if (plan.updateEffects && session?.canConfigureBlur == true) {
            bundle.putBoolean(ViewAnimationExtras.EXTRA_BLUR, realtimeBlurEnabled)
        }
        if (plan.updateEffects && session?.canConfigureBackgroundScale == true) {
            bundle.putFloat(ViewAnimationExtras.EXTRA_BACKGROUND_SCALE, 1f)
        }
        Logger.d("SystemVideoCardTransition", "session=$token 更新返回配置: reason=$reason openingEvent=${session?.openingEvent} " +
            "lifecycle=${activity.lifecycle.currentState} finishing=${activity.isFinishing} " +
            "motion=$motionEnabled returnToSource=$returnToSource sameWindow=$sameWindow bounds=$bounds " +
            "oplusSdk=$oplusSdkWindow plan=$plan keys=${bundle.keySet()}")
        com.android.purebilibili.core.ui.transition.VideoCardTransitionDiagnostics.measureWork(
            "official_return_override", detail = { "reason=$reason session=$token" },
        ) { ViewAnimationManager.overrideViewAnimationTransition(activity, bundle) }
    }

    fun release(token: String?) {
        if (token != null) sessions.remove(token)
        if (latestWindowRequestToken == token) latestWindowRequestToken = null
    }

    /** 首页来源保持同一窗口和卡片，不通过重新启动首页破坏系统返回会话。 */
    fun returnsDirectlyToHome(activity: VideoActivity): Boolean {
        val token = activity.intent.getStringExtra(EXTRA_SESSION) ?: return false
        val session = sessions[token] ?: return false
        return session.sourceRoute == com.android.purebilibili.navigation.ScreenRoutes.Home.route &&
            session.sourceActivity.get() is com.android.purebilibili.MainActivity
    }

    fun afterFinish(activity: VideoActivity) {
        val token = activity.intent.getStringExtra(EXTRA_SESSION) ?: return
        sessions[token]?.vivoOpening?.afterFinish(activity)
    }

    /** 卡片位图垫上来源页面底色后交给引擎；位图尺寸不变，仅消除透明背景。 */
    private fun compositeOverBackgroundColor(source: Bitmap, backgroundColor: Int): Bitmap? {
        // 图层位图为硬件位图：先落为软件位图，软件画布才能继续合成底色。
        val softwareSource = try {
            if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else {
                source
            }
        } catch (error: IllegalArgumentException) {
            Logger.w("SystemVideoCardTransition", "卡片位图软件化失败", error)
            return null
        }
        val backed = Bitmap.createBitmap(softwareSource.width, softwareSource.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(backed)
        canvas.drawColor(backgroundColor)
        canvas.drawBitmap(softwareSource, 0f, 0f, null)
        return backed
    }

    /** 无快照层时按窗口坐标复制来源卡片矩形；调用方在主线程等待结果。 */
    private suspend fun captureWindowRegion(activity: ComponentActivity, windowBounds: Rect): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val bitmap = Bitmap.createBitmap(windowBounds.width(), windowBounds.height(), Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(
                    activity.window, windowBounds, bitmap, { result ->
                        continuation.resume(bitmap.takeIf { result == PixelCopy.SUCCESS })
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (error: IllegalArgumentException) {
                Logger.w("SystemVideoCardTransition", "卡片矩形像素复制失败", error)
                continuation.resume(null)
            }
        }

    /** 播放器只读取来源封面，系统开合及返回仍使用完整 thumbnail。 */
    fun peekOpeningSeed(token: String?): VideoDetailOpeningSeed? = token?.let { sessions[it]?.openingSeed }

    fun initialRealtimeBlurEnabled(token: String?): Boolean? = token?.let { sessions[it]?.initialRealtimeBlurEnabled }
}
