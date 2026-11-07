package com.android.purebilibili.feature.video

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import com.android.purebilibili.core.ui.transition.VideoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.resolveVideoCardSourceClipRadiusPx
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.navigation3.VideoCardTransitionSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID
import java.lang.ref.WeakReference

/** 自研窗口仅在跨窗口模糊可用时建立；不支持时继续使用同窗转场。 */
internal object AppVideoCardWindow {
    const val EXTRA_SESSION = "app_video_card_window"
    data class Session(
        val anchor: VideoCardSourceAnchor, val pixels: Bitmap, val bounds: Rect, val radius: Float,
        val sourceContent: WeakReference<android.view.View>,
        val backgroundColor: Int,
        val clipRadius: Float,
    )
    private val sessions = mutableMapOf<String, Session>()

    fun session(token: String?) = sessions[token]
    fun release(token: String?) { sessions.remove(token) }

    suspend fun tryOpen(
        activity: ComponentActivity,
        request: VideoOpenRequest,
        source: VideoCardTransitionSession,
        backgroundColor: Int,
        isCurrent: () -> Boolean,
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 31 ||
            !activity.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled) return false
        val anchor = source.sourceAnchor ?: return false
        val owner = source.sourcePixelOwner ?: return false
        if (!CardPositionManager.isActiveVideoCardPixelOwner(owner)) return false
        val bounds = anchor.bounds(false)?.takeIf { it.width() > 1 && it.height() > 1 } ?: return false
        val radius = anchor.radiusPx() ?: return false
        val layer = CardPositionManager.nativeVideoCardLayerFor(source.sourceChromeSnapshot)
            ?.takeIf { !it.isReleased && it.size.width > 1 && it.size.height > 1 } ?: return false
        // 同一次来源已完成像素捕获时直接复用，避免点击后再等待一次 GPU 读回。
        val pixels = (CardPositionManager.nativeVideoCardBitmapFor(source.sourceChromeSnapshot)
            ?: layer.toImageBitmap()).asAndroidBitmap()
        currentCoroutineContext().ensureActive()
        if (kotlin.math.abs(pixels.width - bounds.width()) > 1 ||
            kotlin.math.abs(pixels.height - bounds.height()) > 1) return false
        val token = UUID.randomUUID().toString()
        sessions[token] = Session(anchor, pixels, Rect(bounds), radius,
            WeakReference(activity.findViewById(android.R.id.content)), backgroundColor,
            // 与系统窗口共用裁剪规则；透明行卡使用封面圆角，原始外框仍用于返回身份校验。
            resolveVideoCardSourceClipRadiusPx(
                radius, source.sourceChromeSnapshot?.coverPresentation?.cornerRadiusDp,
                activity.resources.displayMetrics.density,
            ),
        )
        var opened = false
        try {
            opened = VideoActivityLauncher.open(
                activity, request, appTransitionToken = token,
                canLaunch = {
                    isCurrent() && activity.window.decorView.hasWindowFocus() &&
                        CardPositionManager.isActiveVideoCardPixelOwner(owner) && anchor.bounds(false) == bounds
                },
            )
            return opened
        } finally {
            if (!opened) release(token)
        }
    }
}
