package com.android.purebilibili.navigation3.predictiveback

import top.yukonga.miuix.kmp.nav.core.NavBackCompletionPolicy

// A completed gesture can land the floating card while it is still visibly detached.
internal object VideoCardBackCompletionPolicy : NavBackCompletionPolicy {
    private const val FLOATING_CARD_PROGRESS = 0.65f
    private const val RESTORE_DETAIL_PROGRESS = 0.35f

    override fun shouldCommit(progress: Float, peakProgress: Float, velocity: Float): Boolean {
        if (!progress.isFinite() || !peakProgress.isFinite() || !velocity.isFinite()) return false
        if (peakProgress >= FLOATING_CARD_PROGRESS) return progress > RESTORE_DETAIL_PROGRESS
        return velocity > -1f
    }
}
