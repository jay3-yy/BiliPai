package com.android.purebilibili.feature.video

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.Lifecycle
import com.android.purebilibili.core.ui.transition.VideoCardSourceAnchor
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.navigation3.VideoCardTransitionSession
import com.itgsa.animation.sharedview.ViewAnimationConfig
import com.itgsa.animation.sharedview.ViewAnimationExtras
import com.itgsa.animation.sharedview.ViewAnimationManager
import com.itgsa.animation.sharedview.ViewAnimationType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

internal data class OfficialVideoCardCapability(
    val available: Boolean,
    val configurableBlur: Boolean,
)

/** 只保存本次来源的弱锚点与卡片像素，不以 bvid 查找替代实例。主线程访问。 */
internal object OfficialVideoCardTransition {
    const val EXTRA_SESSION = "official_video_card_session"

    private class Session(
        val anchor: VideoCardSourceAnchor,
        val thumbnail: Bitmap,
        val windowCoordinates: Boolean,
        val canRefreshBitmap: Boolean,
        val canConfigureBlur: Boolean,
        val initialRealtimeBlurEnabled: Boolean,
    )

    private val sessions = mutableMapOf<String, Session>()

    fun canAttempt(enabled: Boolean, source: VideoCardTransitionSession): Boolean {
        if (!enabled || !source.hasUsableSourceGeometry || source.sourceAnchor == null) return false
        return deviceCapability().available
    }

    fun deviceCapability(): OfficialVideoCardCapability {
        if (ViewAnimationManager.isViewAnimationSupported() <= 0) {
            return OfficialVideoCardCapability(available = false, configurableBlur = false)
        }
        val extras = ViewAnimationManager.getViewAnimationExtras()
        val backTypes = extras?.get(ViewAnimationExtras.EXTRA_BACK_ANIM_TYPE)
        // 必须能在来源消失或减少动态效果时停止返回形变，才接管整次往返。
        val available = supportsOfficialBackType(backTypes, "VIEW_ANIM_DEFAULT") &&
            supportsOfficialBackType(backTypes, "VIEW_ANIM_FOLLOW_SYSTEM") &&
            supportsOfficialBackType(backTypes, "VIEW_ANIM_NONE")
        return OfficialVideoCardCapability(
            available = available,
            configurableBlur = extras?.containsKey(ViewAnimationExtras.EXTRA_BLUR) == true,
        )
    }

    /** false 表示未启动窗口，调用方继续原有应用内导航；失效请求不得继续导航。 */
    suspend fun tryOpen(
        activity: ComponentActivity,
        request: VideoOpenRequest,
        source: VideoCardTransitionSession,
        realtimeBlurEnabled: Boolean,
        isCurrent: () -> Boolean,
    ): Boolean {
        val anchor = source.sourceAnchor ?: return false
        val owner = source.sourcePixelOwner ?: return false
        if (!CardPositionManager.isActiveVideoCardPixelOwner(owner)) return false
        val layer = CardPositionManager.nativeVideoCardLayerFor(source.sourceChromeSnapshot) ?: return false
        if (layer.isReleased || layer.size.width <= 1 || layer.size.height <= 1) return false
        val thumbnail = layer.toImageBitmap().asAndroidBitmap()
        currentCoroutineContext().ensureActive()
        if (!isCurrent() || !CardPositionManager.isActiveVideoCardPixelOwner(owner)) return false
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !activity.window.decorView.hasWindowFocus()) return false
        val extras = ViewAnimationManager.getViewAnimationExtras()
        val windowCoordinates = officialBoundsUseWindowCoordinates(extras?.get(ViewAnimationExtras.EXTRA_BOUNDS))
        val bounds = anchor.bounds(windowCoordinates) ?: return false
        if (kotlin.math.abs(thumbnail.width - bounds.width()) > 1 ||
            kotlin.math.abs(thumbnail.height - bounds.height()) > 1) return false
        val radius = anchor.radiusPx() ?: return false
        val canConfigureBlur = extras?.containsKey(ViewAnimationExtras.EXTRA_BLUR) == true
        val configuration = Bundle().apply {
            if (canConfigureBlur) putBoolean(ViewAnimationExtras.EXTRA_BLUR, realtimeBlurEnabled)
        }
        val options = ViewAnimationManager.makeViewAnimationOptions(
            thumbnail, bounds, radius, configuration, null,
        ) ?: return false
        val token = UUID.randomUUID().toString()
        sessions[token] = Session(
            anchor, thumbnail, windowCoordinates,
            canRefreshBitmap = extras?.containsKey(ViewAnimationExtras.EXTRA_BITMAP) == true,
            canConfigureBlur = canConfigureBlur,
            initialRealtimeBlurEnabled = realtimeBlurEnabled,
        )
        var launched = false
        try {
            VideoActivityLauncher.open(activity, request, options.toBundle(), token)
            launched = true
            Logger.d("OfficialVideoCardTransition", "官方卡片转场已请求: ${request.bvid}")
            return true
        } catch (error: SecurityException) {
            // 系统服务拒绝本应用的转场准入时，窗口未启动，仍可回到应用内导航。
            Logger.w("OfficialVideoCardTransition", "系统拒绝官方转场，改用应用内动画", error)
            ViewAnimationManager.cancelCurrentViewAnimation()
            return false
        } finally {
            if (!launched) sessions.remove(token)
        }
    }

    fun refreshReturn(activity: VideoActivity, motionEnabled: Boolean, realtimeBlurEnabled: Boolean) {
        val token = activity.intent.getStringExtra(EXTRA_SESSION) ?: return
        val session = sessions[token]
        val bounds = session?.anchor?.bounds(session.windowCoordinates)
        val config = ViewAnimationConfig()
        when {
            !motionEnabled -> config.setBackAnimType(ViewAnimationType.VIEW_ANIM_NONE)
            session == null || bounds == null -> config.setBackAnimType(ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM)
            else -> {
                config.setBounds(bounds)
                    .setRadius(requireNotNull(session.anchor.radiusPx()))
                    .setBackAnimType(ViewAnimationType.VIEW_ANIM_DEFAULT)
            }
        }
        val bundle = config.toBundle()
        if (session != null && bounds != null && session.canRefreshBitmap) {
            bundle.putParcelable(ViewAnimationExtras.EXTRA_BITMAP, session.thumbnail)
        }
        if (session?.canConfigureBlur == true) {
            bundle.putBoolean(ViewAnimationExtras.EXTRA_BLUR, realtimeBlurEnabled)
        }
        ViewAnimationManager.overrideViewAnimationTransition(activity, bundle)
    }

    fun release(token: String?) {
        if (token != null) sessions.remove(token)
    }

    fun initialRealtimeBlurEnabled(token: String?): Boolean? = token?.let { sessions[it]?.initialRealtimeBlurEnabled }
}
