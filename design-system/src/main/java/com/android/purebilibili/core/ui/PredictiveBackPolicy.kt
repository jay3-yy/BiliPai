package com.android.purebilibili.core.ui

import androidx.compose.runtime.compositionLocalOf

/** 全局控制返回手势的跟手预览；关闭时仍在手势提交后完成正常返回。 */
val LocalPredictiveBackGestureEnabled = compositionLocalOf { true }
