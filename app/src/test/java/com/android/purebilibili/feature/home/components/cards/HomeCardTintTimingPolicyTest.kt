package com.android.purebilibili.feature.home.components.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [resolveCardTintAnimationMillis] 的语义表。
 *
 * 三档时长各自对应一种"变化场景"，优先级从高到低是：动效闸门 → 首色晚到 → 运行中变色。
 * 断言数字而不是"大于 0"，是因为这三个值本身是体验标定的一部分（140ms 是"晚到"的感知阈值，
 * 350ms 是已有颜色互变的标准时长），改动必须是有意识的。
 */
class HomeCardTintTimingPolicyTest {

    // T1
    @Test
    fun motionDisabledSnapsRegardlessOfWhetherColorIsLate() {
        assertEquals(
            HOME_CARD_TINT_SNAP_MS,
            resolveCardTintAnimationMillis(isFirstColorForCard = false, motionAllowed = false),
            "动效被禁止时必须 0ms 落位，非零时长在滚动中会持续产生动画帧",
        )
    }

    // T2
    @Test
    fun lateFirstColorUsesShortFade() {
        assertEquals(
            HOME_CARD_TINT_LATE_ARRIVAL_MS,
            resolveCardTintAnimationMillis(isFirstColorForCard = true, motionAllowed = true),
            "首色晚到应压缩到 140ms：350ms 会被感知成「卡了一下才上色」",
        )
    }

    // T3
    @Test
    fun liveColorChangeUsesFullFade() {
        assertEquals(
            HOME_CARD_TINT_LIVE_CHANGE_MS,
            resolveCardTintAnimationMillis(isFirstColorForCard = false, motionAllowed = true),
            "已有颜色换成另一个颜色应走完整 350ms，否则会出现突兀的整块换色",
        )
    }

    // T4
    @Test
    fun motionGateWinsOverLateArrival() {
        val millis = resolveCardTintAnimationMillis(isFirstColorForCard = true, motionAllowed = false)

        assertEquals(HOME_CARD_TINT_SNAP_MS, millis, "闸门关闭优先于「首色晚到」，两者同时成立时必须是 0ms")
    }

    // T5
    @Test
    fun timingConstantsKeepTheirOrdering() {
        // 顺序本身是语义：snap < late arrival < live change。任何倒序都说明有人调错了档。
        assertEquals(0, HOME_CARD_TINT_SNAP_MS, "snap 必须是 0，非零会在滚动中持续产生动画帧")
        assertTrue(
            HOME_CARD_TINT_SNAP_MS < HOME_CARD_TINT_LATE_ARRIVAL_MS,
            "晚到时长必须大于 snap",
        )
        assertTrue(
            HOME_CARD_TINT_LATE_ARRIVAL_MS < HOME_CARD_TINT_LIVE_CHANGE_MS,
            "晚到时长必须小于运行中变色时长，否则晚到的首色会被拖慢",
        )
    }
}
