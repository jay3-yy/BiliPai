package com.android.purebilibili.feature.video

import android.app.Activity
import android.app.ActivityOptions
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.android.purebilibili.core.util.Logger
import com.itgsa.animation.sharedview.ViewAnimationListener
import com.itgsa.animation.sharedview.ViewAnimationManager
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** OneShot 窗口协议；打开和返回使用窗口坐标，回调仅记录系统实际通知。 */
internal object VivoVideoCardTransition {
    private const val TAG = "VivoVideoCardTransition"

    private data class Protocol(
        val open: Method,
        val overrideOptions: Method,
        val started: Class<*>,
        val finished: Class<*>,
        val blur: Method?,
        val cancelReturn: Method?,
    )

    private val protocol: Protocol? by lazy {
        try {
            val started = Class.forName("android.app.ActivityOptions\$OnAnimationStartedListener", false, null)
            val finished = Class.forName("android.app.ActivityOptions\$OnAnimationFinishedListener", false, null)
            require(started.isInterface && finished.isInterface)
            started.getMethod("onAnimationStarted", Long::class.javaPrimitiveType)
            finished.getMethod("onAnimationFinished", Long::class.javaPrimitiveType)
            Protocol(
                ActivityOptions::class.java.getMethod("makeOneShotThumbnailAnimation",
                    Bitmap::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    Handler::class.java, started, finished),
                Activity::class.java.getMethod("overrideTransitionOptions", Bundle::class.java),
                started, finished,
                optional(ActivityOptions::class.java, "setOneShotEnableBlur", Boolean::class.javaPrimitiveType!!),
                optional(WindowManager::class.java, "onViewPositionChanged",
                    Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            )
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: NoSuchMethodException) {
            null
        } catch (error: IllegalArgumentException) {
            Logger.w(TAG, "OneShot 回调契约不匹配", error)
            null
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统不允许访问 OneShot 窗口协议", error)
            null
        } catch (error: LinkageError) {
            Logger.w(TAG, "OneShot 窗口协议依赖不可用", error)
            null
        }
    }

    val available: Boolean get() = protocol != null
    val configurableBlur: Boolean get() = protocol?.blur != null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var runningReturn = WeakReference<Opening>(null)

    internal class CallbackPhase(val open: Boolean) {
        var started = false
        var finished = false
    }

    internal class Opening internal constructor(
        source: Activity,
        private val listener: ViewAnimationListener,
    ) {
        private val source = WeakReference(source)
        lateinit var options: Bundle
            internal set
        private var pendingReturn: Bundle? = null
        private var suppressReturn = false
        private var returnRunning = false
        private var cancellationRequested = false
        private var abandoned = false
        private var returnPhase: CallbackPhase? = null

        internal fun beginPhase(open: Boolean): CallbackPhase = CallbackPhase(open).also {
            if (!open) {
                returnPhase = it
                returnRunning = false
                cancellationRequested = false
                if (runningReturn.get() === this) runningReturn.clear()
            }
        }

        internal fun record(phase: CallbackPhase, started: Boolean) {
            if (abandoned || phase.finished || (!phase.open && returnPhase !== phase)) return
            if (started && phase.started) return
            if (started) phase.started = true else phase.finished = true
            val mode = if (phase.open) ViewAnimationManager.ANIM_MODE_OPEN else ViewAnimationManager.ANIM_MODE_BACK
            if (!phase.open) {
                returnRunning = started
                if (started) runningReturn = WeakReference(this)
                else if (runningReturn.get() === this) runningReturn.clear()
            }
            if (started) listener.onAnimationStart(mode)
            else {
                if (!phase.open && cancellationRequested) {
                    Logger.i(TAG, "OneShot 收回取消请求后收到结束回调；系统未单独声明取消原因")
                }
                listener.onAnimationEnd(mode)
            }
        }

        fun prepareReturn(bitmap: Bitmap, bounds: Rect?, radius: Float,
                          enabled: Boolean, blurEnabled: Boolean) {
            returnPhase = null
            returnRunning = false
            cancellationRequested = false
            if (runningReturn.get() === this) runningReturn.clear()
            suppressReturn = !enabled
            pendingReturn = if (enabled && bounds != null) {
                makeOptions(this, bitmap, bounds, radius, open = false, blurEnabled = blurEnabled)?.toBundle()
            } else Bundle()
            if (pendingReturn == null) {
                pendingReturn = Bundle()
                Logger.w(TAG, "OneShot 返回参数不可用，交还普通窗口返回")
            }
        }

        /** finishActivity 已提交后才覆盖正在收集的返回转场。 */
        fun afterFinish(target: Activity) {
            if (!target.isFinishing) return
            val bundle = pendingReturn ?: return
            pendingReturn = null
            try {
                protocol!!.overrideOptions.invoke(target, bundle)
                if (suppressReturn) {
                    @Suppress("DEPRECATION")
                    target.overridePendingTransition(0, 0)
                }
                Logger.i(TAG, "OneShot 返回请求已提交: keys=${bundle.keySet()} suppress=$suppressReturn")
            } catch (error: ReflectiveOperationException) {
                Logger.w(TAG, "OneShot 返回提交失败", cause(error))
            } catch (error: SecurityException) {
                Logger.w(TAG, "系统拒绝 OneShot 返回提交", error)
            }
        }

        /** 仅在来源窗口的收回阶段显式请求；系统命令无会话 token，不能用于再次打开前的清场。 */
        internal fun cancelReturn(requester: Activity): Boolean {
            if (abandoned || !returnRunning || cancellationRequested || source.get() !== requester ||
                !requester.window.decorView.hasWindowFocus()) return false
            val cancel = protocol?.cancelReturn ?: return false
            return try {
                cancel.invoke(requester.windowManager, 0, 0)
                cancellationRequested = true
                Logger.i(TAG, "已请求结束正在运行的 OneShot 收回动画；等待系统结束回调")
                true
            } catch (error: ReflectiveOperationException) {
                Logger.w(TAG, "OneShot 收回取消请求失败", cause(error))
                false
            } catch (error: SecurityException) {
                Logger.w(TAG, "系统拒绝 OneShot 收回取消请求", error)
                false
            }
        }

        fun abandon() {
            abandoned = true
            pendingReturn = null
            if (runningReturn.get() === this) runningReturn.clear()
        }
    }

    fun open(source: Activity, bitmap: Bitmap, bounds: Rect, radius: Float,
             blurEnabled: Boolean, listener: ViewAnimationListener): Opening? {
        if (protocol == null) return null
        val opening = Opening(source, listener)
        val options = makeOptions(opening, bitmap, bounds, radius, open = true, blurEnabled = blurEnabled)
            ?: return null
        opening.options = options.toBundle()
        return opening
    }

    fun cancelRunningReturn(source: Activity): Boolean = runningReturn.get()?.cancelReturn(source) == true

    private fun makeOptions(opening: Opening, bitmap: Bitmap, bounds: Rect, radius: Float,
                            open: Boolean, blurEnabled: Boolean): ActivityOptions? {
        val api = protocol ?: return null
        return try {
            val phase = opening.beginPhase(open)
            val started = callback(api.started, "onAnimationStarted") { opening.record(phase, started = true) }
            val finished = callback(api.finished, "onAnimationFinished") { opening.record(phase, started = false) }
            val options = api.open.invoke(null, bitmap, bounds.left, bounds.top, radius,
                open, mainHandler, started, finished) as ActivityOptions
            api.blur?.invoke(options, blurEnabled)
            options
        } catch (error: ReflectiveOperationException) {
            Logger.w(TAG, "OneShot 参数构造失败", cause(error))
            null
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统拒绝 OneShot 参数访问", error)
            null
        } catch (error: IllegalArgumentException) {
            Logger.w(TAG, "OneShot 回调参数不匹配", error)
            null
        }
    }

    private fun callback(type: Class<*>, event: String, action: () -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                event -> { mainHandler.post { action() }; null }
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "OneShotCallback($event)"
                else -> null
            }
        }

    private fun optional(type: Class<*>, name: String, vararg parameters: Class<*>): Method? = try {
        type.getMethod(name, *parameters)
    } catch (_: NoSuchMethodException) {
        null
    } catch (error: SecurityException) {
        Logger.w(TAG, "系统不允许访问可选 OneShot 方法 $name", error)
        null
    }

    private fun cause(error: ReflectiveOperationException): Throwable =
        (error as? InvocationTargetException)?.targetException ?: error
}
