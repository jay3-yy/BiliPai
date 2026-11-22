package com.android.purebilibili.core.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

object AppMotionEasing {
    /** Apple/SwiftUI easeInOut unit curve for reversible on-screen morphs. */
    val IosEaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    /** CSS/Web standard ease (0.25, 0.1, 0.25, 1.0) — shared by tab indicators & popups. */
    val StandardEase: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    /** CSS/Web ease-out (0.0, 0.0, 0.58, 1.0) — shared by dialogs & media previews. */
    val StandardEaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    /** CSS/Web ease-in (0.42, 0.0, 1.0, 1.0). */
    val StandardEaseIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    /** Quintic ease-out (0.23, 1.0, 0.32, 1.0) — smooth deceleration. */
    val EaseOutQuint: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EmphasizedEnter: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EmphasizedExit: Easing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
    val Continuity: Easing = CubicBezierEasing(0.20f, 0.90f, 0.22f, 1.00f)
    val GentleEnter: Easing = CubicBezierEasing(0.18f, 0.80f, 0.20f, 1.00f)
    /** 景深返回清晰：ease-in 向 0，先留住模糊再柔化，避免 Continuity 在 1→0 时过早掐清。 */
    val SoftClear: Easing = CubicBezierEasing(0.40f, 0.00f, 0.55f, 0.30f)

    // ═══ Material 1 时代标准曲线（收编自 Compose 内置缓动，单一来源） ═══
    // 数值与平台内置 FastOutSlowInEasing 完全一致，替换仅收编引用来源，不改变任何现有动画手感。
    // 适用范围：骨架呼吸、计数滚动、环境性过渡等不随主题切换的常驻动画。
    // 主题相关动效仍应使用 Md3* / Continuity；新增代码请先评估主题 token 再落到本组。
    /** M1 标准曲线，与平台 tween 默认缓动同值 */
    val M1Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    /** M1 标准减速（入场侧） */
    val M1Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    // ═══ Material Design 3 官方标准曲线 (Material Motion Spec) ═══
    /** MD3 官方强调入场减速 (Emphasized Decelerate) */
    val Md3EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)
    /** MD3 官方强调离场加速 (Emphasized Accelerate) */
    val Md3EmphasizedAccelerate: Easing = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)
    /** MD3 官方标准曲线 (Standard Easing) */
    val Md3Standard: Easing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)
    /** MD3 官方标准减速 (Standard Decelerate) */
    val Md3StandardDecelerate: Easing = CubicBezierEasing(0.00f, 0.00f, 0.00f, 1.00f)
    /** MD3 官方标准加速 (Standard Accelerate) */
    val Md3StandardAccelerate: Easing = CubicBezierEasing(0.30f, 0.00f, 1.00f, 1.00f)
}
