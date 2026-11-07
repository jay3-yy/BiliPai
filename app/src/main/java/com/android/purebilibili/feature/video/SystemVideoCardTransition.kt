package com.android.purebilibili.feature.video

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
import com.android.purebilibili.core.ui.transition.VideoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.resolveVideoCardSourceClipRadiusPx
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.navigation3.VideoCardTransitionSession
import com.itgsa.animation.sharedview.ViewAnimationConfig
import com.itgsa.animation.sharedview.ViewAnimationExtras
import com.itgsa.animation.sharedview.ViewAnimationListener
import com.itgsa.animation.sharedview.ViewAnimationManager
import com.itgsa.animation.sharedview.ViewAnimationType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
)

/** 详情窗口首帧的同步卡片种子；仅窗口转场打开时的首个播放身份使用。 */
val LocalVideoDetailOpeningSeed = androidx.compose.runtime.staticCompositionLocalOf<Bitmap?> { null }

/** 系统窗口转场调度：标准协议优先，其次圆角窗口协议；自研形变由导航独立承接。
 * 只保存本次来源的弱锚点与卡片像素，不以 bvid 查找替代实例。主线程访问。
 */
internal object SystemVideoCardTransition {
    const val EXTRA_SESSION = "official_video_card_session"

    private class Session(
        val anchor: VideoCardSourceAnchor,
        val thumbnail: Bitmap,
        val windowCoordinates: Boolean,
        val canRefreshBitmap: Boolean,
        val canConfigureBlur: Boolean,
        val initialRealtimeBlurEnabled: Boolean,
        val roundedSource: WeakReference<ComponentActivity>?,
        val openingBounds: Rect,
        val openingRadius: Float,
        val coverCornerDp: Float?,
    ) {
        val requestedAtMs = SystemClock.uptimeMillis()
        @Volatile var openingEvent = "awaiting_start"
    }

    private val sessions = mutableMapOf<String, Session>()

    fun canAttempt(enabled: Boolean, source: VideoCardTransitionSession): Boolean {
        val sourceReady = enabled && source.hasUsableSourceGeometry && source.sourceAnchor != null
        val supported = if (sourceReady) deviceCapability().available else null
        val allowed = sourceReady && supported == true
        Logger.i("SystemVideoCardTransition", "入口判定: route=${source.sourceRoute} enabled=$enabled geometry=${source.hasUsableSourceGeometry} " +
            "anchor=${source.sourceAnchor != null} supported=$supported allowed=$allowed")
        return allowed
    }

    fun deviceCapability(): SystemVideoCardCapability {
        if (ViewAnimationManager.isViewAnimationSupported() <= 0) {
            return SystemVideoCardCapability(
                RoundedVideoCardTransition.available, configurableBlur = false,
                backAnimMetadata = null, roundedWindow = RoundedVideoCardTransition.available,
            )
        }
        val extras = ViewAnimationManager.getViewAnimationExtras()
        val backAnimMetadata = extras?.get(ViewAnimationExtras.EXTRA_BACK_ANIM_TYPE)
        // 协议声明支持即接管打开段；返回动画类型按设备声明的可写集合尽力控制，
        // 未声明的类型不写入（协议默认值即设备默认策略），不把可用的设备挡在门外。
        return SystemVideoCardCapability(
            available = true,
            configurableBlur = extras?.containsKey(ViewAnimationExtras.EXTRA_BLUR) == true,
            backAnimMetadata = backAnimMetadata,
        )
    }

    /** 按声明顺序取第一个设备允许写入的返回类型常量；全部未声明时返回 null（不写入）。 */
    private fun declaredBackType(
        capability: SystemVideoCardCapability,
        vararg names: String,
    ): Int? = names.firstOrNull { supportsOfficialBackType(capability.backAnimMetadata, it) }?.let { name ->
        when (name) {
            "VIEW_ANIM_NONE" -> ViewAnimationType.VIEW_ANIM_NONE
            "VIEW_ANIM_FOLLOW_SYSTEM" -> ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM
            "VIEW_ANIM_DEFAULT" -> ViewAnimationType.VIEW_ANIM_DEFAULT
            else -> null
        }
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
        val owner = source.sourcePixelOwner ?: return rejected("pixel_owner_missing")
        if (!CardPositionManager.isActiveVideoCardPixelOwner(owner)) return rejected("pixel_owner_expired")
        val layer = CardPositionManager.nativeVideoCardLayerFor(source.sourceChromeSnapshot)
            ?.takeIf { !it.isReleased && it.size.width > 1 && it.size.height > 1 }
        currentCoroutineContext().ensureActive()
        if (!isCurrent() || !CardPositionManager.isActiveVideoCardPixelOwner(owner)) return rejected("request_expired_after_capture")
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !activity.window.decorView.hasWindowFocus()) return rejected("source_window_inactive")
        val capability = deviceCapability()
        if (!capability.available) return rejected("system_protocol_unavailable")
        val extras = if (capability.roundedWindow) null else ViewAnimationManager.getViewAnimationExtras()
        val windowCoordinates = officialBoundsUseWindowCoordinates(extras?.get(ViewAnimationExtras.EXTRA_BOUNDS))
        val bounds = anchor.bounds(windowCoordinates) ?: return rejected("source_bounds_unavailable")
        val thumbnail = if (layer != null) {
            // 透明行卡的位图背景透明，先垫来源页面底色，避免引擎放大位图时透出双层内容。
            compositeOverBackgroundColor(
                layer.toImageBitmap().asAndroidBitmap(), sourceBackgroundColor,
            ) ?: return rejected("source_pixels_unavailable")
        } else {
            // 直播、番剧等导航容器卡片没有原生快照层，按来源窗口像素复制卡片矩形。
            captureWindowRegion(activity, bounds) ?: return rejected("source_pixels_unavailable")
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
        val configuration = Bundle().apply {
            if (canConfigureBlur) putBoolean(ViewAnimationExtras.EXTRA_BLUR, realtimeBlurEnabled)
        }
        val token = (if (capability.roundedWindow) "rounded:" else "sdk:") + UUID.randomUUID()
        val session = Session(
            anchor, thumbnail, windowCoordinates,
            canRefreshBitmap = extras?.containsKey(ViewAnimationExtras.EXTRA_BITMAP) == true,
            canConfigureBlur = canConfigureBlur,
            initialRealtimeBlurEnabled = realtimeBlurEnabled,
            roundedSource = if (capability.roundedWindow) WeakReference(activity) else null,
            openingBounds = Rect(bounds),
            openingRadius = radius,
            coverCornerDp = source.sourceChromeSnapshot?.coverPresentation?.cornerRadiusDp,
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
            "bitmap=${thumbnail.width}x${thumbnail.height} windowCoordinates=$windowCoordinates " +
            "blurRequested=$realtimeBlurEnabled blurSupported=$canConfigureBlur extras=$extras")
        val options = if (capability.roundedWindow) {
            RoundedVideoCardTransition.options(activity, thumbnail, bounds, radius, sourceBackgroundColor) { event ->
                if (event.startsWith("open_")) session.openingEvent = event
                Logger.i("SystemVideoCardTransition", "session=$token protocol=rounded event=$event")
            }
        } else {
            ViewAnimationManager.makeViewAnimationOptions(thumbnail, bounds, radius, configuration, listener)
        } ?: return rejected("system_options_null")
        sessions[token] = session
        var launched = false
        try {
            launched = VideoActivityLauncher.open(activity, request, options.toBundle(), token) {
                // 等待窗口恢复后再次核对请求和几何，不用旧截图打开已切换的卡片。
                    isCurrent() && activity.window.decorView.hasWindowFocus() &&
                        CardPositionManager.isActiveVideoCardPixelOwner(owner) &&
                        anchor.bounds(windowCoordinates) == bounds && anchor.radiusPx() == anchorRadius
            }
            if (!launched) return rejected("request_expired_before_launch")
            Logger.i("SystemVideoCardTransition", "session=$token 系统卡片转场已请求: ${request.bvid}")
            return true
        } catch (error: SecurityException) {
            // 系统服务拒绝本应用的转场准入时，窗口未启动，仍可回到应用内导航。
            Logger.w("SystemVideoCardTransition", "系统拒绝窗口转场，改用应用内动画", error)
            return false
        } finally {
            if (!launched) {
                sessions.remove(token)
                if (!capability.roundedWindow) ViewAnimationManager.cancelCurrentViewAnimation()
            }
        }
    }

    fun refreshReturn(
        activity: VideoActivity,
        motionEnabled: Boolean,
        realtimeBlurEnabled: Boolean,
        reason: String,
        preparingToFinish: Boolean = false,
    ) {
        val token = activity.intent.getStringExtra(EXTRA_SESSION) ?: return
        val session = sessions[token]
        if (session == null && token.startsWith("rounded:")) return
        val bounds = session?.anchor?.bounds(session.windowCoordinates)
        if (session?.roundedSource != null) {
            // 返回更新会把系统共享转场数据切换为关闭方向。onResume 早于打开转场
            // ready，不能在恢复、设置变化或配置变化时写入；仅在 finish 前刷新。
            if (!preparingToFinish) return
            // 旧协议只支持更新坐标，不能更新圆角；尺寸或圆角改变时不缩向过期几何。
            val sameShape = bounds != null &&
                bounds.width() == session.openingBounds.width() &&
                bounds.height() == session.openingBounds.height() &&
                resolveVideoCardSourceClipRadiusPx(
                    requireNotNull(session.anchor.radiusPx()),
                    session.coverCornerDp,
                    activity.resources.displayMetrics.density,
                ) == session.openingRadius
            session.roundedSource.get()?.let { source ->
                Logger.i("SystemVideoCardTransition", "session=$token protocol=rounded prepare_return " +
                    "motion=$motionEnabled sameShape=$sameShape bounds=$bounds")
                RoundedVideoCardTransition.refreshReturn(source, bounds, motionEnabled && sameShape)
            }
            return
        }
        val capability = deviceCapability()
        if (!capability.available || capability.roundedWindow) return
        val config = ViewAnimationConfig()
        when {
            !motionEnabled -> {
                // 减少动效：优先无动画返回；设备未声明类型时不写入，由设备默认策略兜底。
                declaredBackType(capability, "VIEW_ANIM_NONE", "VIEW_ANIM_FOLLOW_SYSTEM")
                    ?.let { config.setBackAnimType(it) }
            }
            session == null || bounds == null -> {
                // 来源消失：跟随系统返回，不收缩到过期位置。
                declaredBackType(capability, "VIEW_ANIM_FOLLOW_SYSTEM", "VIEW_ANIM_DEFAULT")
                    ?.let { config.setBackAnimType(it) }
            }
            else -> {
                config.setBounds(bounds)
                    .setRadius(
                        resolveVideoCardSourceClipRadiusPx(
                            requireNotNull(session.anchor.radiusPx()),
                            session.coverCornerDp,
                            activity.resources.displayMetrics.density,
                        )
                    )
                // 一镜到底返回本就是协议默认值，仅在设备声明可写时显式设置。
                if (supportsOfficialBackType(capability.backAnimMetadata, "VIEW_ANIM_DEFAULT")) {
                    config.setBackAnimType(ViewAnimationType.VIEW_ANIM_DEFAULT)
                }
            }
        }
        val bundle = config.toBundle()
        if (session != null && bounds != null && session.canRefreshBitmap) {
            bundle.putParcelable(ViewAnimationExtras.EXTRA_BITMAP, session.thumbnail)
        }
        if (session?.canConfigureBlur == true) {
            bundle.putBoolean(ViewAnimationExtras.EXTRA_BLUR, realtimeBlurEnabled)
        }
        Logger.d("SystemVideoCardTransition", "session=$token 更新返回配置: reason=$reason openingEvent=${session?.openingEvent} " +
            "lifecycle=${activity.lifecycle.currentState} finishing=${activity.isFinishing} " +
            "motion=$motionEnabled bounds=$bounds keys=${bundle.keySet()}")
        com.android.purebilibili.core.ui.transition.VideoCardTransitionDiagnostics.measureWork(
            "official_return_override", detail = { "reason=$reason session=$token" },
        ) { ViewAnimationManager.overrideViewAnimationTransition(activity, bundle) }
    }

    fun release(token: String?) {
        if (token != null) sessions.remove(token)
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
    private suspend fun captureWindowRegion(activity: ComponentActivity, screenBounds: Rect): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val decor = activity.window.decorView
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            val windowBounds = Rect(
                screenBounds.left - location[0], screenBounds.top - location[1],
                screenBounds.right - location[0], screenBounds.bottom - location[1],
            )
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

    /** 打开时捕获的卡片像素，供详情首帧在封面就绪前同步垫底。 */
    fun peekThumbnail(token: String?): Bitmap? = token?.let { sessions[it]?.thumbnail }

    fun initialRealtimeBlurEnabled(token: String?): Boolean? = token?.let { sessions[it]?.initialRealtimeBlurEnabled }
}
