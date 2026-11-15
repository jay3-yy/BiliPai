package com.android.purebilibili.feature.home.components.cards

/** 动效闸门关闭时的时长：颜色直接落位，不做过渡。 */
internal const val HOME_CARD_TINT_SNAP_MS = 0

/**
 * "颜色晚到"的过渡时长。
 *
 * 卡片已经以无色的默认外观显示了一段时间，此刻颜色才被算出来。用满 [HOME_CARD_TINT_LIVE_CHANGE_MS]
 * 的 350ms 会让用户明确感到「卡了一下才上色」，所以压缩到 140ms：既不是硬切（硬切会在静态
 * 画面上产生一次突兀的整块换色），又短到接近"立刻就好"的感知。
 */
internal const val HOME_CARD_TINT_LATE_ARRIVAL_MS = 140

/** 卡片本来就有色，运行中换成另一个色（例如换了一路封面源）：用完整的过渡时长。 */
internal const val HOME_CARD_TINT_LIVE_CHANGE_MS = 350

/**
 * 决定首页卡片跟随取色的渐入时长（毫秒）。
 *
 * ## 只处理两种"变化"
 *
 * **首帧就有色（缓存命中）这条路径不需要这个函数**：`Animatable(targetTint)` 的初值就是缓存色，
 * 调用方的 `LaunchedEffect` 会因为 `value == target` 直接 return，天然是 0ms。也就是说本函数
 * 只负责：
 * 1. **颜色晚到**：`isFirstColorForCard == true`（[Animatable] 值仍是透明），走
 *    [HOME_CARD_TINT_LATE_ARRIVAL_MS]；
 * 2. **运行中真实变色**：已有颜色被换成另一个颜色，走 [HOME_CARD_TINT_LIVE_CHANGE_MS]。
 *
 * ## 为什么动效闸门优先级最高
 *
 * 闸门关闭（滚动中、卡片不可见、系统降低动效、组件动效被关）时，任何插值都是纯浪费：
 * 这些帧用户看不到，或者本来就不该有动效。此时必须直接 `snapTo`，而不是用一个"很短但非零"
 * 的时长糊过去——非零时长在滚动中会持续产生动画帧与重组。
 *
 * 本函数刻意不依赖任何 Compose 图形类型（不引 `Color`），只吃布尔量，便于纯 JVM 单测。
 *
 * @param isFirstColorForCard 该卡当前还没有任何颜色（[Animatable] 值仍是透明），即"晚到"。
 * @param motionAllowed 动效闸门（已含非滚动、可见、视觉工作活跃、未降低动效）。
 * @return 过渡时长；`<= 0` 表示调用方应改用 `snapTo`。
 */
internal fun resolveCardTintAnimationMillis(
    isFirstColorForCard: Boolean,
    motionAllowed: Boolean,
): Int {
    if (!motionAllowed) return HOME_CARD_TINT_SNAP_MS
    if (isFirstColorForCard) return HOME_CARD_TINT_LATE_ARRIVAL_MS
    return HOME_CARD_TINT_LIVE_CHANGE_MS
}
