package com.android.purebilibili.feature.onboarding

enum class UserAgreementClause(
    val title: String,
    val body: String,
) {
    OPEN_SOURCE_FREE(
        title = "开源免费",
        body = "本应用为开源免费项目，仅供学习与交流，不提供商业服务，也与哔哩哔哩官方无关。",
    ),
    NO_DOMESTIC_PROMO(
        title = "禁止国内平台宣传",
        body = "请勿在国内平台（如微信、微博、B 站、小红书、应用商店评论等）宣传、引流或二次分发本应用。",
    ),
    FEEDBACK_WITH_LOGS(
        title = "反馈须附日志与截图",
        body = "反馈问题请附带日志与相关截图，否则维护者可能无法复现与处理。",
    ),
}

data class UserAgreementChannelLink(
    val label: String,
    val url: String,
)

fun userAgreementIntroText(): String =
    "进入应用前，请阅读并勾选以下使用约定。"

fun userAgreementClauseList(): List<UserAgreementClause> = UserAgreementClause.entries

fun userAgreementChannelLinks(
    telegramChannelUrl: String,
    telegramGroupUrl: String,
    githubUrl: String,
): List<UserAgreementChannelLink> = listOf(
    UserAgreementChannelLink(
        label = "Telegram 频道",
        url = telegramChannelUrl,
    ),
    UserAgreementChannelLink(
        label = "Telegram 交流群",
        url = telegramGroupUrl,
    ),
    UserAgreementChannelLink(
        label = "开源地址",
        url = githubUrl,
    ),
)

/** All required checkboxes must be checked before 「我已知晓」 can proceed. */
fun canAcknowledgeUserAgreement(checkedClauses: Map<UserAgreementClause, Boolean>): Boolean {
    return UserAgreementClause.entries.all { clause -> checkedClauses[clause] == true }
}

fun canAcknowledgeUserAgreement(
    openSourceFreeChecked: Boolean,
    noDomesticPromoChecked: Boolean,
    feedbackWithLogsChecked: Boolean,
): Boolean = openSourceFreeChecked && noDomesticPromoChecked && feedbackWithLogsChecked
