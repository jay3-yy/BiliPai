package com.android.purebilibili.feature.video

import com.android.purebilibili.navigation3.SystemWindowMotionPhase
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
import com.android.purebilibili.core.store.CardTransitionMethod
import com.android.purebilibili.core.ui.transition.VideoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.resolveVideoCardSourceClipRadiusPx
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.navigation3.VideoCardTransitionSession
import com.android.purebilibili.feature.profile.ProfileAvatarEntrance
import com.itgsa.animation.sharedview.ViewAnimationConfig
import com.itgsa.animation.sharedview.ViewAnimationExtras
import com.itgsa.animation.sharedview.ViewAnimationListener
import com.itgsa.animation.sharedview.ViewAnimationManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.coroutines.resume

internal data class SystemVideoCardCapability(
    val method: CardTransitionMethod = CardTransitionMethod.STANDARD,
    val configurableBlur: Boolean = false,
    /** EXTRA_BACK_ANIM_TYPE 原始声明；null = 设备未声明，按协议不假定类型可写。 */
    val backAnimMetadata: String? = null,
    /** 标准 SDK 与原生无缝门面并存，使用该门面的返回更新契约。 */
    val oplusSdkWindow: Boolean = false,
    val systemConditionSummary: String? = null,
) {
    val available: Boolean get() = method != CardTransitionMethod.STANDARD
}

/** 系统窗口转场调度：标准协议优先，再尝试旧无缝和 OneShot；不可用时回退普通页面转场。
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
        val method: CardTransitionMethod,
        val oplusSdkWindow: Boolean,
        val backAnimMetadata: String?,
        val openingBounds: Rect,
        val openingAnchorRadius: Float,
        val windowCoordinates: Boolean,
        val allowPartialSource: Boolean,
        val openingRadius: Float,
        val openingSeed: VideoDetailOpeningSeed?,
        val profileAvatarEntrance: ProfileAvatarEntrance?,
        val sourceAnchor: VideoCardSourceAnchor,
        val onReturn: ((SystemWindowMotionPhase) -> Unit)?,
        val neighborMotion: ((SystemWindowMotionPhase) -> Unit)?,
    ) {
        val requestedAtMs = SystemClock.uptimeMillis()
        @Volatile var openingEvent = "awaiting_start"
        var oplusOpening: OplusVideoCardTransition.Opening? = null
        var vivoOpening: VivoVideoCardTransition.Opening? = null
        var returnMotionEnabled = false
        var motionPhase: SystemWindowMotionPhase? = null

        fun dispatchMotion(phase: SystemWindowMotionPhase) {
            // SDK 回调可能来自 Binder；所有 Compose 状态变更统一回到主线程。
            val deliver = Runnable {
                if (phase.returning && !returnMotionEnabled) return@Runnable
                if (motionPhase == phase || motionPhase == SystemWindowMotionPhase.RETURNED) return@Runnable
                if (motionPhase?.returning == true && !phase.returning) return@Runnable
                if (motionPhase == SystemWindowMotionPhase.RETURN_CANCELLED &&
                    phase == SystemWindowMotionPhase.RETURNED
                ) return@Runnable
                motionPhase = phase
                neighborMotion?.invoke(phase)
                if (phase == SystemWindowMotionPhase.RETURNED) sourceAnchor.requestReturnLanding()
                if (phase.returning) onReturn?.invoke(phase)
            }
            if (Looper.myLooper() == Looper.getMainLooper()) deliver.run()
            else Handler(Looper.getMainLooper()).post(deliver)
        }
    }

    private val sessions = mutableMapOf<String, Session>()
    private var latestWindowRequestToken: String? = null

    // 只记录播放入口，查看设置的过程不会覆盖上一次测试结果。
    private val lastOpenedMethodState = MutableStateFlow<CardTransitionMethod?>(null)
    val lastOpenedMethod = lastOpenedMethodState.asStateFlow()

    fun recordStandardOpen() {
        lastOpenedMethodState.value = CardTransitionMethod.STANDARD
    }

    fun profileAvatarEntrance(token: String?): ProfileAvatarEntrance? = sessions[token]?.profileAvatarEntrance

    fun hasSession(token: String?): Boolean = token != null && token in sessions

    fun canAttempt(
        enabled: Boolean,
        source: VideoCardTransitionSession,
        activity: Activity,
        method: CardTransitionMethod,
    ): Boolean {
        val sourceReady = enabled && source.hasUsableSourceGeometry && source.sourceAnchor != null &&
            source.sourcePixelOwner != null
        val capability = if (sourceReady) deviceCapability(activity, method) else null
        val supported = capability?.available
        val allowed = sourceReady && supported == true
        Logger.i("SystemVideoCardTransition", "入口判定: route=${source.sourceRoute} enabled=$enabled geometry=${source.hasUsableSourceGeometry} " +
            "anchor=${source.sourceAnchor != null} requested=$method resolved=${capability?.method} allowed=$allowed " +
            "conditions=${capability?.systemConditionSummary}")
        return allowed
    }

    fun deviceCapability(activity: Activity?, method: CardTransitionMethod): SystemVideoCardCapability {
        if (method == CardTransitionMethod.STANDARD) return SystemVideoCardCapability()
        val automatic = method == CardTransitionMethod.AUTO
        val checksOfficial = automatic || method == CardTransitionMethod.OFFICIAL
        val checksOplus = automatic || method == CardTransitionMethod.OPLUS_LEGACY
        val oplusSystemManager = (checksOfficial || checksOplus) && OplusVideoCardTransition.hasSystemManager
        val conditions = if (oplusSystemManager) OplusVideoCardTransition.conditions(activity) else null
        val summary = conditions?.toString()
        if (conditions?.blocked == true) {
            return SystemVideoCardCapability(systemConditionSummary = summary)
        }
        if (checksOfficial && ViewAnimationManager.isViewAnimationSupported() > 0) {
            val extras = ViewAnimationManager.getViewAnimationExtras()
            return SystemVideoCardCapability(
                method = CardTransitionMethod.OFFICIAL,
                configurableBlur = extras?.containsKey(ViewAnimationExtras.EXTRA_BLUR) == true,
                backAnimMetadata = extras?.get(ViewAnimationExtras.EXTRA_BACK_ANIM_TYPE),
                oplusSdkWindow = oplusSystemManager,
                systemConditionSummary = summary,
            )
        }
        if (checksOplus && OplusVideoCardTransition.available) {
            return SystemVideoCardCapability(
                method = CardTransitionMethod.OPLUS_LEGACY,
                systemConditionSummary = summary,
            )
        }
        if ((automatic || method == CardTransitionMethod.VIVO_ONESHOT) && VivoVideoCardTransition.available) {
            return SystemVideoCardCapability(
                method = CardTransitionMethod.VIVO_ONESHOT,
                configurableBlur = VivoVideoCardTransition.configurableBlur,
            )
        }
        // 保留可用的系统卡片展开。旧圆角协议的背景效果由系统控制。
        if ((automatic || method == CardTransitionMethod.XIAOMI_ROUNDED) && RoundedVideoCardTransition.available) {
            return SystemVideoCardCapability(method = CardTransitionMethod.XIAOMI_ROUNDED)
        }
        return SystemVideoCardCapability(systemConditionSummary = summary)
    }

    /** false 表示未启动窗口，调用方继续原有应用内导航；失效请求不得继续导航。 */
    suspend fun tryOpen(
        activity: ComponentActivity,
        request: VideoOpenRequest,
        source: VideoCardTransitionSession,
        realtimeBlurEnabled: Boolean,
        sourceBackgroundColor: Int,
        method: CardTransitionMethod,
        isCurrent: () -> Boolean,
        reportMethod: Boolean = true,
        onReturn: ((SystemWindowMotionPhase) -> Unit)? = null,
        profileAvatarEntrance: ProfileAvatarEntrance? = null,
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
        val capability = deviceCapability(activity, method)
        Logger.i("SystemVideoCardTransition", "系统条件: ${capability.systemConditionSummary ?: "由标准接口判定"}")
        if (!capability.available) return rejected("system_protocol_unavailable ${capability.systemConditionSummary.orEmpty()}")
        val layer = CardPositionManager.nativeCardLayerForOwner(owner)
            ?.takeIf { !it.isReleased && it.size.width > 1 && it.size.height > 1 }
        currentCoroutineContext().ensureActive()
        if (!isCurrent() || !CardPositionManager.isActiveVideoCardPixelOwner(owner)) return rejected("request_expired_after_capture")
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !activity.window.decorView.hasWindowFocus()) return rejected("source_window_inactive")
        val extras = if (capability.method == CardTransitionMethod.OFFICIAL) {
            ViewAnimationManager.getViewAnimationExtras()
        } else null
        val windowCoordinates = capability.method == CardTransitionMethod.VIVO_ONESHOT ||
            officialBoundsUseWindowCoordinates(extras?.get(ViewAnimationExtras.EXTRA_BOUNDS))
        val capturedBounds = anchor.bounds(windowCoordinates, allowPartial = layer != null)
            ?: return rejected("source_bounds_unavailable")
        val isNowPlayingBar = source.sourceChromeSnapshot?.isNowPlayingBar == true
        val thumbnail = if (layer != null) {
            // 透明行卡的位图背景透明，先垫来源页面底色，避免引擎放大位图时透出双层内容。
            val frozenBar = source.sourceChromeSnapshot?.takeIf { it.isNowPlayingBar }
                ?.let(CardPositionManager::nativeVideoCardBitmapFor)
            val pixels = (frozenBar ?: layer.toImageBitmap()).asAndroidBitmap()
            withContext(Dispatchers.Default) {
                compositeOverBackgroundColor(pixels, sourceBackgroundColor)
            } ?: return rejected("source_pixels_unavailable")
        } else {
            // 截图始终使用窗口坐标，独立于系统转场声明的屏幕 / 窗口坐标系。
            val captureBounds = anchor.bounds(windowCoordinates = true) ?: return rejected("source_bounds_unavailable")
            captureWindowRegion(activity, captureBounds) ?: return rejected("source_pixels_unavailable")
        }
        val windowRequest = if (request.initialRoute.isNotBlank()) {
            request
        } else {
            val directPortraitEntryEnabled = runCatching {
                com.android.purebilibili.core.store.SettingsManager
                    .getAutoPortraitFullscreen(activity)
                    .first()
            }.getOrDefault(false)
            request.forSystemCardTransition(
                isVerticalVideo = request.initialVertical ||
                    com.android.purebilibili.data.repository.VideoRepository.getCachedVerticalFlag(request.bvid) == true,
                directPortraitEntryEnabled = directPortraitEntryEnabled,
            )
        }
        // 像素准备期间可能已换卡或离开窗口，不为过期请求创建系统转场配置。
        if (!isCurrent() || !CardPositionManager.isActiveVideoCardPixelOwner(owner)) return rejected("request_expired_after_pixels")
        // 胶囊图层只录制横条自身。合成像素期间若整条仅移动，提交时使用最新位置；
        // 尺寸仍由下方校验，窗口截图也仍绑定原始截图矩形。
        val bounds = if (isNowPlayingBar && layer != null) {
            anchor.bounds(windowCoordinates, allowPartial = true) ?: return rejected("source_bounds_unavailable")
        } else capturedBounds
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
        val prefix = when (capability.method) {
            CardTransitionMethod.VIVO_ONESHOT -> "vivo"
            CardTransitionMethod.OPLUS_LEGACY -> "oplus"
            CardTransitionMethod.XIAOMI_ROUNDED -> "rounded"
            else -> "sdk"
        }
        val token = "$prefix:${UUID.randomUUID()}"
        val session = Session(
            sourceRoute = source.sourceRoute,
            sourceActivity = WeakReference(activity),
            sourceWindow = sourceWindow,
            thumbnail = thumbnail,
            canRefreshBitmap = extras?.containsKey(ViewAnimationExtras.EXTRA_BITMAP) == true,
            canConfigureBlur = canConfigureBlur,
            canConfigureBackgroundScale = canConfigureBackgroundScale,
            initialRealtimeBlurEnabled = realtimeBlurEnabled,
            method = capability.method,
            oplusSdkWindow = capability.oplusSdkWindow,
            backAnimMetadata = capability.backAnimMetadata,
            openingBounds = Rect(bounds),
            openingAnchorRadius = anchorRadius,
            windowCoordinates = windowCoordinates,
            allowPartialSource = layer != null,
            openingRadius = radius,
            openingSeed = videoDetailOpeningSeed(thumbnail, source),
            profileAvatarEntrance = profileAvatarEntrance,
            sourceAnchor = anchor,
            onReturn = onReturn,
            neighborMotion = com.android.purebilibili.navigation3.SystemWindowControlNeighbors.capture(anchor),
        )
        // 启动请求成功不等于系统实际播放；分别记录打开、返回的启动、取消与结束。
        val listener = object : ViewAnimationListener {
            private fun record(event: String, animMode: Int) {
                if (animMode == ViewAnimationManager.ANIM_MODE_OPEN) session.openingEvent = event
                val returning = animMode == ViewAnimationManager.ANIM_MODE_BACK
                if (returning || animMode == ViewAnimationManager.ANIM_MODE_OPEN) {
                    session.dispatchMotion(when (event) {
                        "start" -> if (returning) SystemWindowMotionPhase.RETURNING else SystemWindowMotionPhase.OPENING
                        "cancel" -> if (returning) SystemWindowMotionPhase.RETURN_CANCELLED else SystemWindowMotionPhase.OPEN_CANCELLED
                        else -> if (returning) SystemWindowMotionPhase.RETURNED else SystemWindowMotionPhase.OPENED
                    })
                }
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
        val optionsBundle = when (capability.method) {
            CardTransitionMethod.VIVO_ONESHOT -> {
                VivoVideoCardTransition.open(activity, thumbnail, bounds, radius, realtimeBlurEnabled, listener)
                    ?.also { session.vivoOpening = it }?.options
            }
            CardTransitionMethod.OPLUS_LEGACY -> OplusVideoCardTransition.open(
                activity, thumbnail, bounds, radius, sourceBackgroundColor, listener,
            )?.also { opening ->
                session.oplusOpening = opening
            }?.options
            CardTransitionMethod.XIAOMI_ROUNDED -> RoundedVideoCardTransition.options(
                activity, thumbnail, bounds, radius, sourceBackgroundColor,
            ) { event ->
                when (event) {
                    "open_start" -> listener.onAnimationStart(ViewAnimationManager.ANIM_MODE_OPEN)
                    "open_end" -> listener.onAnimationEnd(ViewAnimationManager.ANIM_MODE_OPEN)
                    "return_start" -> listener.onAnimationStart(ViewAnimationManager.ANIM_MODE_BACK)
                    "return_end" -> listener.onAnimationEnd(ViewAnimationManager.ANIM_MODE_BACK)
                }
            }?.toBundle()
            CardTransitionMethod.OFFICIAL -> ViewAnimationManager.makeViewAnimationOptions(
                thumbnail, bounds, radius, configuration, listener,
            )?.toBundle()
            CardTransitionMethod.AUTO, CardTransitionMethod.STANDARD -> error("Expected a system card transition")
        } ?: return rejected("system_options_null protocol=${capability.method}")
        sessions[token] = session
        var launched = false
        try {
            launched = VideoActivityLauncher.open(
                activity, windowRequest, optionsBundle, token,
            ) {
                // 等待窗口恢复后再次核对请求和几何，不用旧截图打开已切换的卡片。
                isCurrent() && activity.window.decorView.hasWindowFocus() &&
                    windowGeometry(activity) == sourceWindow && CardPositionManager.isActiveVideoCardPixelOwner(owner) &&
                    anchor.matchesWindowGeometry(
                        activity.window.decorView, bounds, anchorRadius, windowCoordinates, allowPartial = layer != null,
                    )
            }
            if (!launched) return rejected("request_expired_before_launch")
            if (reportMethod) lastOpenedMethodState.value = session.method
            Logger.i("SystemVideoCardTransition", "session=$token 系统卡片转场已请求: ${request.bvid}")
            return true
        } catch (error: SecurityException) {
            // 系统服务拒绝本应用的转场准入时，窗口未启动，仍可回到应用内导航。
            Logger.w("SystemVideoCardTransition", "系统拒绝窗口转场，改用普通页面转场", error)
            return false
        } finally {
            if (!launched) {
                sessions.remove(token)
                session.dispatchMotion(SystemWindowMotionPhase.OPEN_CANCELLED)
                session.vivoOpening?.abandon()
                // 旧请求可能在新窗口请求提交后才响应协程取消，不能用全局接口结束新转场。
                if (latestWindowRequestToken == token) {
                    when (session.method) {
                        CardTransitionMethod.OPLUS_LEGACY -> OplusVideoCardTransition.cancel()
                        CardTransitionMethod.OFFICIAL -> ViewAnimationManager.cancelCurrentViewAnimation()
                        else -> Unit
                    }
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
    ) {
        val token = activity.intent.getStringExtra(EXTRA_SESSION) ?: return
        // 打开配置由系统保留到关闭；播放和窗口恢复不重复覆盖返回参数。
        if (!preparingToFinish) return
        val session = sessions[token]
        val source = session?.sourceActivity?.get()
        // 回卡只允许落到本次打开的原窗口和原控件；仍在同一窗口的列表也可能已经重排。
        val childWindow = windowGeometry(activity)
        val sameWindow = session != null && source != null && !source.isFinishing && !source.isDestroyed &&
            windowGeometry(source) == session.sourceWindow &&
            childWindow.displayId == session.sourceWindow.displayId &&
            childWindow.rotation == session.sourceWindow.rotation
        val bounds = if (returnToSource && sameWindow && session != null && source != null) {
            session.openingBounds.takeIf {
                session.sourceAnchor.matchesWindowGeometry(
                    source.window.decorView, it, session.openingAnchorRadius,
                    session.windowCoordinates, session.allowPartialSource,
                )
            }
        } else null
        val sourceAvailable = bounds != null
        // 准备参数不等于系统已经开始返回，联动等待协议的真实回调。
        session?.returnMotionEnabled = sourceAvailable && motionEnabled
        if (!sourceAvailable) {
            Logger.w("SystemVideoCardTransition", "返回落点失效: reason=$reason returnToSource=$returnToSource sameWindow=$sameWindow")
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
            source?.let {
                // 局部回弹由系统返回结束回调触发，保留返回到来源控件的圆角动画。
                RoundedVideoCardTransition.refreshReturn(it, bounds, motionEnabled && sourceAvailable)
            }
            return
        }
        // 已打开的窗口沿用提交时的协议。详情窗口当前的入场资格与此次回卡无关。
        val currentRadius = session?.openingRadius?.takeIf { sourceAvailable }
        val oplusSdkWindow = session?.oplusSdkWindow == true
        val plan = resolveOfficialVideoCardReturnPlan(
            metadata = session?.backAnimMetadata,
            oplusSdkWindow = oplusSdkWindow,
            preparingToFinish = preparingToFinish,
            motionEnabled = motionEnabled,
            sourceAvailable = sourceAvailable,
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
        try {
            val backed = Bitmap.createBitmap(softwareSource.width, softwareSource.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(backed)
            canvas.drawColor(backgroundColor)
            canvas.drawBitmap(softwareSource, 0f, 0f, null)
            return backed
        } finally {
            // 只回收本次软件化副本；来源图层仍持有传入的位图。
            if (softwareSource !== source) softwareSource.recycle()
        }
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
