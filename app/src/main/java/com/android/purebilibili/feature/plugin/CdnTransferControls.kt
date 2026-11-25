package com.android.purebilibili.feature.plugin

import com.android.purebilibili.plugin.sdk.Plugin

interface CdnTransferControls : Plugin {
    fun canUseParallelDownload(): Boolean
    suspend fun setParallelDownloadEnabled(enabled: Boolean)

    fun currentParallelConfig(): CdnParallelConfig = CdnParallelConfig()
    suspend fun setParallelMirrorMode(mode: CdnParallelMirrorMode) = Unit
    suspend fun setParallelConcurrencyPreset(preset: CdnConcurrencyPreset) = Unit
    suspend fun setParallelAllowCellular(allow: Boolean) = Unit
}
