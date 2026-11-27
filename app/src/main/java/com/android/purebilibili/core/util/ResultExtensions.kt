// 文件路径: core/util/ResultExtensions.kt
package com.android.purebilibili.core.util

/**
 * Result 扩展工具类
 * 
 * 提供统一的错误处理和结果转换工具，简化 Repository 层代码
 */

/**
 * 安全执行挂起函数，自动捕获异常并包装为 Result
 */
suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Logger.e("SafeApiCall", " API call failed: ${e.message}")
        Result.failure(e)
    }
}

/**
 * 安全执行挂起函数，失败时返回默认值
 */
suspend inline fun <T> safeApiCallOrDefault(default: T, crossinline block: suspend () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        Logger.e("SafeApiCall", " API call failed, using default: ${e.message}")
        default
    }
}

/**
 * 安全执行挂起函数，失败时返回 null
 */
suspend inline fun <T> safeApiCallOrNull(crossinline block: suspend () -> T): T? {
    return try {
        block()
    } catch (e: Exception) {
        Logger.e("SafeApiCall", " API call failed, returning null: ${e.message}")
        null
    }
}

/**
 * Result 扩展：成功时执行 action
 */
inline fun <T> Result<T>.onSuccessLog(tag: String, message: (T) -> String): Result<T> {
    onSuccess { Logger.d(tag, " ${message(it)}") }
    return this
}

/**
 * Result 扩展：失败时执行 action 并记录日志
 */
inline fun <T> Result<T>.onFailureLog(tag: String, message: (Throwable) -> String = { it.message ?: "Unknown error" }): Result<T> {
    onFailure { Logger.e(tag, " ${message(it)}") }
    return this
}

/**
 * Result 扩展：转换为另一个类型
 */
inline fun <T, R> Result<T>.mapSuccess(transform: (T) -> R): Result<R> {
    return fold(
        onSuccess = { Result.success(transform(it)) },
        onFailure = { Result.failure(it) }
    )
}

/**
 * Result 扩展：获取值或抛出带消息的异常
 */
fun <T> Result<T>.getOrThrow(message: String): T {
    return getOrElse { throw Exception(message, it) }
}

// ==================== 错误码常量 ====================

object ApiErrorCodes {
    const val SUCCESS = 0
    const val NOT_LOGIN = -101
    const val ACCOUNT_BANNED = -102
    const val CSRF_ERROR = -111
    const val NOT_MODIFIED = -304
    const val BAD_REQUEST = -400
    const val NOT_FOUND = -404
    const val TOO_MANY_REQUESTS = -412
    const val RISK_CONTROL = -352
    const val VIDEO_NOT_EXIST = 62002
    const val VIDEO_REVIEWING = 62004
    
    fun isLoginRequired(code: Int): Boolean = code == NOT_LOGIN
    fun isRateLimited(code: Int): Boolean = code == TOO_MANY_REQUESTS
    fun isRiskControlled(code: Int): Boolean = code == RISK_CONTROL
}

/**
 * 根据 API 错误码获取用户友好的错误消息
 */
fun getApiErrorMessage(code: Int, defaultMessage: String? = null): String {
    return when (code) {
        ApiErrorCodes.SUCCESS -> "成功"
        ApiErrorCodes.NOT_LOGIN -> "请先登录"
        ApiErrorCodes.ACCOUNT_BANNED -> "账号已被封禁"
        ApiErrorCodes.CSRF_ERROR -> "CSRF 验证失败，请重新登录"
        ApiErrorCodes.BAD_REQUEST -> "请求参数错误"
        ApiErrorCodes.NOT_FOUND -> "内容不存在"
        ApiErrorCodes.TOO_MANY_REQUESTS -> "请求过于频繁，请稍后重试"
        ApiErrorCodes.RISK_CONTROL -> "触发风控，请稍后重试"
        ApiErrorCodes.VIDEO_NOT_EXIST -> "视频不存在或已删除"
        ApiErrorCodes.VIDEO_REVIEWING -> "视频审核中"
        else -> defaultMessage ?: "未知错误 ($code)"
    }
}

private val CJK_CHAR_REGEX = Regex("[\\u4E00-\\u9FFF]")

/**
 * 将底层异常转为面向用户的中文提示：
 * 保留后端返回的中文业务错误，过滤英文网络/序列化等技术栈原文。
 */
fun Throwable.toUserFriendlyMessage(fallback: String = "操作失败，请稍后重试"): String {
    val raw = message?.trim().orEmpty()
    val lower = raw.lowercase()
    return when {
        this is java.net.UnknownHostException || "unable to resolve host" in lower ->
            "网络未连接，请检查网络设置"
        this is java.net.SocketTimeoutException || "timeout" in lower || "timed out" in lower ->
            "网络请求超时，请稍后重试"
        this is java.net.ConnectException || "failed to connect" in lower || "connection refused" in lower ->
            "无法连接服务器，请稍后重试"
        this is javax.net.ssl.SSLException || "ssl" in lower && "handshake" in lower ->
            "安全连接失败，请检查网络环境"
        this is java.io.EOFException || "connection reset" in lower || "stream was reset" in lower ->
            "网络连接已中断，请重试"
        raw.isNotBlank() && CJK_CHAR_REGEX.containsMatchIn(raw) -> raw
        else -> fallback
    }
}
