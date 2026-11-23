package com.android.purebilibili.core.ui.components

import androidx.compose.runtime.compositionLocalOf

/** Only optional row requests consume this permission; covers and gestures stay immediate. */
val LocalListAuxiliaryWorkAllowed = compositionLocalOf { true }

/** 横向 Pager 滑动、底栏切页过渡或非当前页时置为 false，防止 Miuix VerticalScrollBar 随页面横穿屏幕。 */
val LocalScrollBarAllowed = compositionLocalOf { true }
