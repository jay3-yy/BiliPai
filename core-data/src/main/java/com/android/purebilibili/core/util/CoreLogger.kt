package com.android.purebilibili.core.util

/**
 * 跨模块日志出口。
 *
 * `core-data` 这类下层模块不能反向依赖 app 模块的 `Logger`（它带文件落盘），
 * 但排查问题时又需要这些模块的日志出现在同一份日志文件里。
 * app 启动时把 `Logger.d` 注入进来，下层模块通过 [d] 打日志。
 *
 * 未注入时退回 `android.util.Log`，只进 logcat。
 */
object CoreLogger {
    /** (tag, message) -> Unit，由 app 模块注入。 */
    var sink: ((String, String) -> Unit)? = null

    fun d(tag: String, message: String) {
        val target = sink
        if (target != null) {
            target(tag, message)
        } else {
            android.util.Log.d(tag, message)
        }
    }
}
