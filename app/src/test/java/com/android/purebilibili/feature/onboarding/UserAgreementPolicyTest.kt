package com.android.purebilibili.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgreementPolicyTest {

    @Test
    fun cannotAcknowledgeUntilAllClausesChecked() {
        assertFalse(
            canAcknowledgeUserAgreement(
                openSourceFreeChecked = false,
                noDomesticPromoChecked = false,
                feedbackWithLogsChecked = false,
            )
        )
        assertFalse(
            canAcknowledgeUserAgreement(
                openSourceFreeChecked = true,
                noDomesticPromoChecked = true,
                feedbackWithLogsChecked = false,
            )
        )
        assertTrue(
            canAcknowledgeUserAgreement(
                openSourceFreeChecked = true,
                noDomesticPromoChecked = true,
                feedbackWithLogsChecked = true,
            )
        )
    }

    @Test
    fun mapFormRequiresEveryClauseTrue() {
        val partial = UserAgreementClause.entries.associateWith { it != UserAgreementClause.FEEDBACK_WITH_LOGS }
        assertFalse(canAcknowledgeUserAgreement(partial))

        val full = UserAgreementClause.entries.associateWith { true }
        assertTrue(canAcknowledgeUserAgreement(full))
    }

    @Test
    fun clauseCopyCoversRequiredTopics() {
        val titles = userAgreementClauseList().map { it.title }
        assertTrue(titles.any { it.contains("开源") })
        assertTrue(titles.any { it.contains("宣传") })
        assertTrue(titles.any { it.contains("日志") || it.contains("截图") })
        assertTrue(userAgreementIntroText().isNotBlank())
    }

    @Test
    fun channelLinksIncludeTelegramChannelGroupAndGithub_withoutDisplayingUrls() {
        val links = userAgreementChannelLinks(
            telegramChannelUrl = "https://t.me/bilipai666",
            telegramGroupUrl = "https://t.me/bilipai888/1",
            githubUrl = "https://github.com/jay3-yy/BiliPai/",
        )
        assertEquals(3, links.size)
        assertEquals(
            listOf("Telegram 频道", "Telegram 交流群", "开源地址"),
            links.map { it.label },
        )
        assertTrue(links[0].url.contains("bilipai666"))
        assertTrue(links[1].url.contains("bilipai888"))
        assertTrue(links[2].url.contains("github.com"))
    }
}
