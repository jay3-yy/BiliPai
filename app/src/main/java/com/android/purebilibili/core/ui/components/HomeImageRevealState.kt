package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.decode.DataSource
import com.android.purebilibili.core.ui.motion.continuityTween

/** 每个图片身份仅对首次网络结果淡入；隐藏、失焦或关闭动效后直接完成，不在恢复时重播。 */
internal class HomeImageRevealState {
    private val progress = Animatable(0f)
    private var receivedResult = false
    var pending by mutableStateOf(false)
        private set
    val alpha: Float get() = if (pending) progress.value else 1f

    fun onSuccess(dataSource: DataSource, motionAllowed: Boolean) {
        if (receivedResult) return
        receivedResult = true
        pending = dataSource == DataSource.NETWORK && motionAllowed
    }

    suspend fun finish(motionAllowed: Boolean) {
        if (!pending) return
        if (motionAllowed) progress.animateTo(1f, continuityTween(120))
        pending = false
    }
}
