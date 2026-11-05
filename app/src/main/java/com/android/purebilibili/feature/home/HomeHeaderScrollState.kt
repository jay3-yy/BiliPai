package com.android.purebilibili.feature.home

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderSettleTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 顶栏偏移、回弹和回顶锁共用一个所有者；协程由调用页面的生命周期管理。 */
internal class HomeHeaderScrollState(
    private val savedOffset: MutableFloatState,
    private val savedTabsCollapsed: MutableState<Boolean>,
    private val globalScrollOffset: MutableFloatState,
) {
    var offsetPx by savedOffset
        private set
    var tabsCollapsed by savedTabsCollapsed
    private var headerSettleAnimationJob: Job? by mutableStateOf(null)
    private var headerSettleTargetPx: Float? = null
    var revealLocked by mutableStateOf(false)
        private set
    val offsetProvider: () -> Float = { offsetPx }
    val isSettling: Boolean get() = headerSettleAnimationJob != null
    val scrollToTopInProgress: Boolean get() = homeScrollToTopMutex.isLocked

    fun setOffsetImmediately(value: Float) {
        cancelSettle()
        offsetPx = value
    }

    private fun cancelSettle() {
        val previousJob = headerSettleAnimationJob
        headerSettleAnimationJob = null
        headerSettleTargetPx = null
        previousJob?.cancel()
    }

    fun reveal() {
        tabsCollapsed = false
        setOffsetImmediately(0f)
        globalScrollOffset.floatValue = 0f
    }

    fun animateTo(
        targetValue: Float,
        coroutineScope: CoroutineScope,
        headerSettleMotionSpec: AnimationSpec<Float>,
    ) {
        // 同方向连续拖动复用当前目标，避免即时模式每次回调都重启动画。
        if (headerSettleAnimationJob?.isActive == true && headerSettleTargetPx == targetValue) return
        val transition = resolveHomeHeaderSettleTransition(
            currentHeaderOffsetPx = offsetPx,
            targetHeaderOffsetPx = targetValue
        )
        if (!transition.shouldAnimate) {
            setOffsetImmediately(transition.targetOffsetPx)
            return
        }
        cancelSettle()
        headerSettleTargetPx = transition.targetOffsetPx
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            animate(
                initialValue = offsetPx,
                targetValue = transition.targetOffsetPx,
                animationSpec = headerSettleMotionSpec
            ) { value, _ ->
                offsetPx = value
            }
        }
        headerSettleAnimationJob = job
        job.invokeOnCompletion {
            if (headerSettleAnimationJob === job) {
                headerSettleAnimationJob = null
                headerSettleTargetPx = null
                // 只有当前回弹任务可更新落位状态，取消的旧任务不能覆盖新手势。
                if (!job.isCancelled && targetValue >= -0.5f) {
                    tabsCollapsed = false
                }
            }
        }
        job.start()
    }

    private val homeScrollToTopMutex = Mutex()
    suspend fun withScrollToTopLock(
        headerSettleMotionSpec: AnimationSpec<Float>,
        requestIsCurrent: () -> Boolean,
        block: suspend () -> Unit,
    ) {
        homeScrollToTopMutex.withLock {
            if (!requestIsCurrent()) return@withLock
            kotlinx.coroutines.coroutineScope {
                revealLocked = true
                cancelSettle()
                tabsCollapsed = false
                globalScrollOffset.floatValue = 0f
                val headerAnimJob = launch {
                    if (offsetPx < -0.5f) {
                        animate(
                            initialValue = offsetPx,
                            targetValue = 0f,
                            animationSpec = headerSettleMotionSpec
                        ) { value, _ -> offsetPx = value }
                    } else {
                        offsetPx = 0f
                    }
                }
                headerSettleAnimationJob = headerAnimJob
                try {
                    block()
                    headerAnimJob.join()
                } finally {
                    headerAnimJob.cancel()
                    if (requestIsCurrent()) {
                        reveal()
                    } else {
                        // 旧请求只释放自己持有的锁，不把新页面顶栏强行展开。
                        if (headerSettleAnimationJob === headerAnimJob) {
                            setOffsetImmediately(offsetProvider())
                        }
                    }
                    revealLocked = false
                }
            }
        }
    }
}

@Composable
internal fun rememberHomeHeaderScrollState(globalScrollOffset: MutableFloatState): HomeHeaderScrollState {
    val offset = rememberSaveable { mutableFloatStateOf(0f) }
    val tabsCollapsed = rememberSaveable { mutableStateOf(false) }
    return remember(offset, tabsCollapsed, globalScrollOffset) {
        HomeHeaderScrollState(offset, tabsCollapsed, globalScrollOffset)
    }
}
