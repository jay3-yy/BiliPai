package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.data.model.response.DynamicAdditional
import com.android.purebilibili.data.model.response.DynamicAdditionalCommon
import com.android.purebilibili.data.model.response.DynamicAdditionalGoods
import com.android.purebilibili.data.model.response.DynamicAdditionalGoodsItem
import com.android.purebilibili.data.model.response.DynamicAdditionalReserve
import com.android.purebilibili.data.model.response.DynamicAdditionalText
import com.android.purebilibili.data.model.response.DynamicAdditionalUgc
import com.android.purebilibili.data.model.response.DynamicAdditionalVote
import com.android.purebilibili.data.model.response.DynamicCardButton
import com.android.purebilibili.data.model.response.DynamicCardButtonStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DynamicAdditionalCardPolicyTest {

    @Test
    fun ugcAdditionalMapsToVideoCard() {
        val card = resolveDynamicAdditionalCard(
            DynamicAdditional(
                type = "ADDITIONAL_TYPE_UGC",
                ugc = DynamicAdditionalUgc(
                    title = "投稿标题",
                    cover = "https://i0.hdslb.com/a.jpg",
                    desc_second = "1.2万播放",
                    jump_url = "https://b23.tv/av1"
                )
            )
        )
        assertEquals("投稿标题", card?.title)
        assertEquals("投稿", card?.kindLabel)
        assertEquals("https://b23.tv/av1", card?.jumpUrl)
    }

    @Test
    fun voteAndGoodsAdditionalMapToReadableCards() {
        val vote = resolveDynamicAdditionalCard(
            DynamicAdditional(
                type = "ADDITIONAL_TYPE_VOTE",
                vote = DynamicAdditionalVote(desc = "今晚吃什么", join_num = 12, vote_id = 88L)
            )
        )
        val goods = resolveDynamicAdditionalCard(
            DynamicAdditional(
                type = "ADDITIONAL_TYPE_GOODS",
                goods = DynamicAdditionalGoods(
                    items = listOf(
                        DynamicAdditionalGoodsItem(name = "周边", brief = "现货", cover = "c", jump_url = "u")
                    )
                )
            )
        )
        val reserve = resolveDynamicAdditionalCard(
            DynamicAdditional(
                type = "ADDITIONAL_TYPE_RESERVE",
                reserve = DynamicAdditionalReserve(
                    title = "直播预约",
                    desc1 = DynamicAdditionalText("今晚 8 点")
                )
            )
        )
        assertEquals("投票", vote?.kindLabel)
        assertEquals("12 人参与", vote?.subtitle)
        assertEquals(88L, vote?.voteId)
        assertEquals("商品", goods?.kindLabel)
        assertEquals("预约", reserve?.kindLabel)
        assertNull(resolveDynamicAdditionalCard(null))
    }

    @Test
    fun commonAdditionalUsesServerButtonAndFallbackUrl() {
        val card = resolveDynamicAdditionalCard(
            DynamicAdditional(
                type = "ADDITIONAL_TYPE_COMMON",
                common = DynamicAdditionalCommon(
                    title = "游戏中心",
                    desc1 = "新活动",
                    head_text = "游戏",
                    button = DynamicCardButton(
                        jump_url = "https://www.bilibili.com/blackboard/game",
                        jump_style = DynamicCardButtonStyle(text = "进入"),
                    ),
                ),
            ),
        )

        assertEquals("游戏", card?.kindLabel)
        assertEquals("进入", card?.actionLabel)
        assertEquals("https://www.bilibili.com/blackboard/game", card?.jumpUrl)
    }

    @Test
    fun reserveCardUsesServerStatusLabelsAndActionParameters() {
        val card = resolveDynamicAdditionalCard(
            DynamicAdditional(
                type = "ADDITIONAL_TYPE_RESERVE",
                reserve = DynamicAdditionalReserve(
                    title = "首播预约",
                    rid = 42L,
                    reserve_total = 99L,
                    desc1 = DynamicAdditionalText("明晚 8 点"),
                    desc2 = DynamicAdditionalText("99 人预约"),
                    button = DynamicCardButton(
                        status = 1,
                        type = 1,
                        check = DynamicCardButtonStyle(text = "已预约"),
                        uncheck = DynamicCardButtonStyle(text = "预约", disable = 0),
                    ),
                ),
            ),
        )

        assertEquals(42L, card?.reserveId)
        assertEquals(99L, card?.reserveTotal)
        assertEquals("已预约", card?.actionLabel)
        assertEquals("明晚 8 点", card?.reserveDescriptionPrefix)
        assertEquals(false, card?.reserveButtonDisabled)
    }

    @Test
    fun reserveJumpButtonTakesPriorityOverToggleAndKeepsGiftLink() {
        val card = resolveDynamicAdditionalCard(
            DynamicAdditional(
                type = "ADDITIONAL_TYPE_RESERVE",
                reserve = DynamicAdditionalReserve(
                    title = "预约抽奖",
                    rid = 42L,
                    desc3 = DynamicAdditionalText(
                        text = "预约后参与抽奖",
                        jump_url = "https://www.bilibili.com/blackboard/gift",
                    ),
                    button = DynamicCardButton(
                        jump_url = "https://www.bilibili.com/blackboard/reserve",
                        jump_style = DynamicCardButtonStyle(text = "查看详情"),
                        uncheck = DynamicCardButtonStyle(text = "预约", disable = 1),
                    ),
                ),
            ),
        )

        assertEquals("查看详情", card?.actionLabel)
        assertEquals("https://www.bilibili.com/blackboard/reserve", card?.reserveActionJumpUrl)
        assertEquals("https://www.bilibili.com/blackboard/gift", card?.reserveDescriptionJumpUrl)
        assertEquals(false, card?.reserveButtonDisabled)
        assertEquals(true, card?.subtitle?.contains("预约后参与抽奖"))
    }
}
