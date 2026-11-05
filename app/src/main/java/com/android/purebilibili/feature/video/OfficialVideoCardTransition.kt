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
    /** EXTRA_BACK_ANIM_TYPE 原始声明；null = 设备未声明，按协议不假定类型可写。 */
    val backAnimMetadata: String?,
)

/** 详情窗口首帧的同步卡片种子；仅窗口转场打开时的首个播放身份使用。 */
val LocalVideoDetailOpeningSeed = androidx.compose.runtime.staticCompositionLocalOf<Bitmap?> { null }

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
            return OfficialVideoCardCapability(false, configurableBlur = false, backAnimMetadata = null)
        }
        val extras = ViewAnimationManager.getViewAnimationExtras()
        val backAnimMetadata = extras?.get(ViewAnimationExtras.EXTRA_BACK_ANIM_TYPE)
        // 协议声明支持即接管打开段；返回动画类型按设备声明的可写集合尽力控制，
        // 未声明的类型不写入（协议默认值即设备默认策略），不把可用的设备挡在门外。
        return OfficialVideoCardCapability(
            available = true,
            configurableBlur = extras?.containsKey(ViewAnimationExtras.EXTRA_BLUR) == true,
            backAnimMetadata = backAnimMetadata,
        )
    }

    /** 按声明顺序取第一个设备允许写入的返回类型常量；全部未声明时返回 null（不写入）。 */
    private fun declaredBackType(
        capability: OfficialVideoCardCapability,
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
        val capability = deviceCapability()
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
                    .setRadius(requireNotNull(session.anchor.radiusPx()))
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
        ViewAnimationManager.overrideViewAnimationTransition(activity, bundle)
    }

    fun release(token: String?) {
        if (token != null) sessions.remove(token)
    }

    /** 打开时捕获的卡片像素，供详情首帧在封面就绪前同步垫底。 */
    fun peekThumbnail(token: String?): Bitmap? = token?.let { sessions[it]?.thumbnail }

    fun initialRealtimeBlurEnabled(token: String?): Boolean? = token?.let { sessions[it]?.initialRealtimeBlurEnabled }
}
