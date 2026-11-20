package com.android.purebilibili.feature.video.ui.components

internal data class PlayerMenuAction(
    val title: String,
    val run: () -> Unit,
    val currentValue: String? = null,
)

internal fun Boolean.playerMenuToggleValue(): String = if (this) "开启" else "关闭"

internal fun formatPlayerMenuTimerValue(minutes: Int?): String =
    minutes?.let { "$it 分钟" } ?: "未设置"
