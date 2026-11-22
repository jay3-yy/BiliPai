package com.android.purebilibili.core.lifecycle

/** 导航暂存资源：接收方取走后接管释放；取消导航只释放尚未取走的资源。 */
internal class PendingResource<T : Any>(value: T, private val release: (T) -> Unit) {
    private var pending: T? = value

    val isAvailable: Boolean get() = pending != null

    fun take(): T? = pending.also { pending = null }

    fun dispose() {
        take()?.let(release)
    }
}
