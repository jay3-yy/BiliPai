package com.android.purebilibili.core.ui.popup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.purebilibili.core.ui.AppDialogExitState
import com.android.purebilibili.core.ui.AppGlassPopupMotionSpec
import com.android.purebilibili.core.ui.AppRetainedDialogPresenter
import com.android.purebilibili.core.ui.LocalAppRetainedDialogPresenter
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion

internal class RetainedDialogEntry(
    val owner: Any,
    val parent: Any?,
    val content: @Composable (AppDialogExitState) -> Unit,
) {
    var closing by mutableStateOf(false)
        internal set
}

/** 同一请求不重开；父窗口关闭时一起关闭其子窗口，保留各自的退出画面。 */
internal class RetainedDialogRegistry : AppRetainedDialogPresenter {
    val entries = mutableStateListOf<RetainedDialogEntry>()

    override fun show(owner: Any, parent: Any?, content: @Composable (AppDialogExitState) -> Unit) {
        if (entries.any { it.owner === owner }) return
        if (parent != null && entries.any { it.owner === parent && it.closing }) return
        entries += RetainedDialogEntry(owner, parent, content)
    }

    override fun dismiss(owner: Any) {
        val owners = mutableSetOf(owner)
        var changed: Boolean
        do {
            changed = false
            entries.forEach { entry ->
                if (entry.owner in owners || entry.parent in owners) {
                    entry.closing = true
                    if (owners.add(entry.owner)) changed = true
                }
            }
        } while (changed)
    }

    fun finish(entry: RetainedDialogEntry) {
        if (entry.closing) entries.remove(entry)
    }
}

@Composable
internal fun AppRetainedDialogHost(content: @Composable () -> Unit) {
    // 设置宿主、模态窗口宿主复用同一个长期存活的注册表，避免父窗口卸载时截断子窗口。
    if (LocalAppRetainedDialogPresenter.current != null) {
        content()
        return
    }
    val registry = remember { RetainedDialogRegistry() }
    CompositionLocalProvider(LocalAppRetainedDialogPresenter provides registry) {
        content()
        registry.entries.toList().forEach { entry ->
            key(entry.owner) {
                val progress = remember { Animatable(0f) }
                val reduced = LocalSystemReduceMotion.current || !LocalComponentMotionEnabled.current
                LaunchedEffect(entry.closing, reduced) {
                    if (entry.closing) {
                        if (reduced) progress.snapTo(1f)
                        else progress.animateTo(1f, tween(AppGlassPopupMotionSpec.UnanchoredExitMillis,
                            easing = com.android.purebilibili.core.ui.motion.AppMotionEasing.StandardEaseOut))
                        registry.finish(entry)
                    }
                }
                val exit = remember(entry, progress) { AppDialogExitState(progress.asState()) { entry.closing } }
                entry.content(exit)
            }
        }
    }
}
