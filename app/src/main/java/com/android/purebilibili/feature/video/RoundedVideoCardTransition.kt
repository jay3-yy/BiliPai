package com.android.purebilibili.feature.video

import android.app.Activity
import android.app.ActivityOptions
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.android.purebilibili.core.util.Logger
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** 圆角窗口协议适配。只使用已确认的接口签名，不绕过系统准入或隐藏 API 限制。 */
internal object RoundedVideoCardTransition {
    private const val TAG = "RoundedVideoCardTransition"
    private const val SCALED_THUMBNAIL = 103

    private data class Protocol(val open: Method, val update: Method)

    private val protocol: Protocol? by lazy {
        try {
            val integer = Int::class.javaPrimitiveType!!
            Protocol(
                ActivityOptions::class.java.getDeclaredMethod(
                    "makeScaleUpDown", View::class.java, Bitmap::class.java,
                    integer, integer, integer, integer, Float::class.javaPrimitiveType!!,
                    Handler::class.java, Runnable::class.java, Runnable::class.java,
                    Runnable::class.java, Runnable::class.java, integer,
                ),
                Activity::class.java.getDeclaredMethod("updateScaleUpDownData", Bundle::class.java),
            )
        } catch (_: NoSuchMethodException) {
            null
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统不允许访问圆角窗口协议", error)
            null
        }
    }

    val available: Boolean get() = protocol != null

    fun options(
        activity: Activity, bitmap: Bitmap, bounds: Rect, radius: Float,
        backgroundColor: Int,
        onEvent: (String) -> Unit,
    ): ActivityOptions? {
        val api = protocol ?: return null
        fun callback(event: String) = Runnable { onEvent(event) }
        return try {
            api.open.invoke(
                null, activity.window.decorView, bitmap, bounds.left, bounds.top,
                radius.toInt(), backgroundColor, 1f, Handler(Looper.getMainLooper()),
                callback("open_start"), callback("open_end"),
                callback("return_start"), callback("return_end"), SCALED_THUMBNAIL,
            ) as? ActivityOptions
        } catch (error: ReflectiveOperationException) {
            Logger.w(TAG, "圆角窗口启动配置不可用", (error as? InvocationTargetException)?.targetException ?: error)
            null
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统拒绝圆角窗口配置", error)
            null
        }
    }

    /** 返回关系登记在来源窗口，不能向目标 Activity 写入配置。 */
    fun refreshReturn(source: Activity, bounds: Rect?, enabled: Boolean) {
        val api = protocol ?: return
        val data = Bundle().apply {
            if (!enabled || bounds == null) {
                putBoolean("disableBackAnimation", true)
            } else {
                putInt("xInScreen", bounds.left)
                putInt("yInScreen", bounds.top)
            }
        }
        try {
            api.update.invoke(source, data)
        } catch (error: ReflectiveOperationException) {
            Logger.w(TAG, "无法更新系统返回落点", (error as? InvocationTargetException)?.targetException ?: error)
        } catch (error: SecurityException) {
            Logger.w(TAG, "系统拒绝返回配置", error)
        }
    }
}
