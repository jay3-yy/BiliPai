package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.ui.unit.IntOffset
import com.android.purebilibili.core.ui.motion.continuityTween

// 播放器 overlay 色层动效 token：遮罩/控制栏/锁屏按钮/加载指示共用同一节奏与曲线。
// 原生与液态玻璃两条 overlay 路径共用此节奏，改动需同时目检两条路径；
// 曲线取全局 alpha 主曲线 Continuity（AppMotionTokens 体系），时长保持既有节奏。
internal const val OVERLAY_CHROME_FADE_DURATION_MILLIS = PLAYER_GLASS_VISIBILITY_DURATION_MS
internal const val OVERLAY_CONTROL_FADE_DURATION_MILLIS = 200
internal val OverlayChromeFadeSpec = continuityTween<Float>(OVERLAY_CHROME_FADE_DURATION_MILLIS)
internal val OverlayControlFadeSpec = continuityTween<Float>(OVERLAY_CONTROL_FADE_DURATION_MILLIS)
internal val OverlayControlSlideSpec = continuityTween<IntOffset>(OVERLAY_CONTROL_FADE_DURATION_MILLIS)
