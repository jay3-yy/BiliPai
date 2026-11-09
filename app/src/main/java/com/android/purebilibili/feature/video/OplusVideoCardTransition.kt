package com.android.purebilibili.feature.video

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import com.android.purebilibili.core.util.Logger
import com.itgsa.animation.sharedview.ViewAnimationListener
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.function.BiConsumer

/** 旧无缝窗口协议。系统保留转场与返回关系，应用只提供卡片像素和屏幕几何。 */
internal object OplusVideoCardTransition {
    private const val TAG = "OplusVideoCardTransition"
    private const val OPEN = "view_seamless_open"

    private data class Protocol(
        val open: Method,
        val skipBack: Method,
        val cancel: Method,
        val fromBundle: Method,
        val remoteTransition: Method,
        val callback: Class<*>,
    )

    private val protocol: Protocol? by lazy {
        try {
            val facade = Class.forName("com.oplus.animation.OplusViewSeamless", false, null)
            val callback = Class.forName("com.oplus.animation.OplusViewSeamless\$AnimationCallback", false, null)
            Protocol(
                facade.getMethod("setSeamlessView", View::class.java, Context::class.java,
                    Bundle::class.java, callback),
                facade.getMethod("skipBackAnim", Activity::class.java),
                facade.getMethod("finishCurrentAnimation"),
                ActivityOptions::class.java.getMethod("fromBundle", Bundle::class.java),
                ActivityOptions::class.java.getMethod("getRemoteTransition"),
                callback,
            )
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: NoSuchMethodException) {
            null
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统不允许访问旧无缝窗口协议", error)
            null
        } catch (error: LinkageError) {
            Logger.w(TAG, "旧无缝窗口协议依赖不可用", error)
            null
        }
    }

    val available: Boolean get() = protocol != null

    internal data class Conditions(
        val featureEnabled: Boolean?,
        val configurationEnabled: Boolean?,
        val animationLevel: Int?,
        val windowAllowed: Boolean?,
        val windowState: Int?,
        val compatMode: Boolean?,
        val queryErrors: List<String>,
    ) {
        val blocked: Boolean get() = featureEnabled == false || configurationEnabled == false ||
            (animationLevel != null && animationLevel > 3) || windowAllowed == false
    }

    /** 读取执行器实际使用的缓存值，并由系统自身判断窗口；不可读与关闭分开记录。 */
    fun conditions(activity: Activity?): Conditions {
        val errors = mutableListOf<String>()
        val manager = try {
            Class.forName("android.app.viewseamless.OplusViewSeamlessRemoteManager", false, null)
        } catch (error: ClassNotFoundException) {
            return Conditions(null, null, null, null, null, null, listOf(error.toString()))
        }
        fun <T> read(name: String, block: () -> T): T? = try {
            block()
        } catch (error: ReflectiveOperationException) {
            errors += "$name: ${cause(error)}"
            null
        } catch (error: SecurityException) {
            errors += "$name: $error"
            null
        }
        fun field(name: String): Any? = read(name) {
            manager.getDeclaredField(name).apply { isAccessible = true }.get(null)
        }
        val feature = field("FEATURE_ENABLE") as? Boolean
        val configuration = field("DPP") as? Boolean
        val level = field("ANIM_LEVEL") as? Int
        val allowed = if (activity == null) null else read("isFeatureEnable") {
            val instance = manager.getMethod("getInstance").invoke(null)
            manager.getDeclaredMethod("isFeatureEnable", Context::class.java)
                .apply { isAccessible = true }.invoke(instance, activity) as Boolean
        }
        val windowState = if (activity == null) null else read("windowState") {
            val flexible = Class.forName("com.oplus.flexiblewindow.FlexibleWindowManager", false, null)
            flexible.getMethod("getFlexibleWindowState", Activity::class.java)
                .invoke(flexible.getMethod("getInstance").invoke(null), activity) as Int
        }
        val compatMode = if (activity == null) null else read("compatMode") {
            manager.getMethod("inOplusCompatMode", android.content.res.Configuration::class.java)
                .invoke(null, activity.resources.configuration) as Boolean
        }
        return Conditions(feature, configuration, level, allowed, windowState, compatMode, errors)
    }

    internal class Opening(
        val options: Bundle,
        private val callback: Any,
        private val offset: Method,
        val supportsPositionOffset: Boolean,
    ) {
        fun updatePosition(bounds: Rect, initialBounds: Rect) {
            offset.invoke(callback, PointF((bounds.left - initialBounds.left).toFloat(),
                (bounds.top - initialBounds.top).toFloat()))
        }
    }

    fun open(
        activity: Activity,
        bitmap: Bitmap,
        bounds: Rect,
        radius: Float,
        backgroundColor: Int,
        listener: ViewAnimationListener,
    ): Opening? {
        val api = protocol ?: return null
        val config = Bundle().apply {
            putParcelable("view_seamless_bitmap", bitmap)
            putParcelable("view_seamless_rect", Rect(bounds))
            putFloat("view_seamless_radius", radius)
            putInt("view_seamless_color", backgroundColor)
            // 来源是窗口 View，不是独立卡片 View；不能让系统隐藏整页 Compose 内容。
            putBoolean("view_seamless_view_visible", true)
        }
        return try {
            val events = BiConsumer<String, Boolean> { event, isOpen ->
                val mode = if (isOpen) 0 else 1
                if (event == "start") listener.onAnimationStart(mode)
                else listener.onAnimationEnd(mode)
            }
            // 先确认系统协议，再加载继承系统回调的桥，避免普通设备解析缺失的父类。
            val callback = Class.forName(
                "com.android.purebilibili.feature.video.OplusAnimationCallbackBridge",
            ).getMethod("create", BiConsumer::class.java).invoke(null, events)
            val offset = api.callback.getMethod("setPositionOffset", PointF::class.java)
            if (api.open.invoke(null, activity.window.decorView, activity, config, callback) != true) {
                Logger.i(TAG, "系统拒绝旧无缝展开，沿用普通页面转场")
                return null
            }
            val options = api.fromBundle.invoke(null, config) as? ActivityOptions
            if (options == null || api.remoteTransition.invoke(options) == null) {
                cancel()
                Logger.w(TAG, "旧无缝协议未返回远程转场")
                null
            } else {
                // 只发送系统生成的 ActivityOptions，截图已由进程内执行器持有。
                // 此标记让框架把远程转场登记到目标 Intent，随后由系统承接 finish 返回。
                Opening(options.toBundle().apply { putBoolean(OPEN, true) }, callback, offset,
                    springPositionOffsetSupported())
            }
        } catch (error: ReflectiveOperationException) {
            Logger.w(TAG, "旧无缝展开配置失败", cause(error))
            cancel()
            null
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统拒绝旧无缝展开配置", error)
            cancel()
            null
        } catch (error: LinkageError) {
            Logger.w(TAG, "系统回调契约不可用", error)
            cancel()
            null
        }
    }

    /** 只有已确认的弹簧执行分支消费位置偏移；其他分支保留原位收回或跳过。 */
    fun prepareReturn(target: Activity, opening: Opening?, initialBounds: Rect?, bounds: Rect?, enabled: Boolean) {
        val api = protocol ?: return
        if (enabled && opening != null && initialBounds != null && bounds != null &&
            (bounds == initialBounds || opening.supportsPositionOffset)) {
            try {
                opening.updatePosition(bounds, initialBounds)
                return
            } catch (error: ReflectiveOperationException) {
                Logger.w(TAG, "返回位置偏移更新失败，跳过卡片收回", cause(error))
            } catch (error: SecurityException) {
                Logger.w(TAG, "系统拒绝返回位置偏移，跳过卡片收回", error)
            }
        }
        try {
            api.skipBack.invoke(null, target)
        } catch (error: ReflectiveOperationException) {
            Logger.w(TAG, "无法取消旧无缝返回落点", cause(error))
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统拒绝取消旧无缝返回落点", error)
        }
    }

    private fun springPositionOffsetSupported(): Boolean = try {
        Class.forName("android.app.viewseamless.ViewSeamlessRemoteTransition", false, null)
            .getDeclaredField("SPRING").apply { isAccessible = true }.getBoolean(null)
    } catch (error: ReflectiveOperationException) {
        Logger.w(TAG, "无法确认系统位置偏移支持，保留原位返回", cause(error))
        false
    } catch (error: SecurityException) {
        Logger.w(TAG, "系统不允许查询位置偏移支持，保留原位返回", error)
        false
    }

    fun cancel() {
        val api = protocol ?: return
        try {
            val accepted = api.cancel.invoke(null)
            Logger.i(TAG, "结束旧无缝动画请求: accepted=$accepted")
        } catch (error: ReflectiveOperationException) {
            Logger.w(TAG, "无法结束旧无缝动画", cause(error))
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统拒绝结束旧无缝动画", error)
        }
    }

    private fun cause(error: ReflectiveOperationException): Throwable =
        (error as? InvocationTargetException)?.targetException ?: error
}
