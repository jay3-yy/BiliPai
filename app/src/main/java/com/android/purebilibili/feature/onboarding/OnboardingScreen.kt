package com.android.purebilibili.feature.onboarding

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.resolveAdaptivePrimaryAccentColors
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.R
import com.android.purebilibili.feature.settings.OFFICIAL_GITHUB_URL
import com.android.purebilibili.feature.settings.OFFICIAL_TELEGRAM_CHANNEL_URL
import com.android.purebilibili.feature.settings.OFFICIAL_TELEGRAM_GROUP_URL

/**
 * Usage notice opened from Settings.
 *
 * - Default action: 「我不同意」→ exit app
 * - 「我已知晓」only after every clause is checked
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onDisagree: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clauses = remember { userAgreementClauseList() }
    val checked = remember {
        mutableStateMapOf<UserAgreementClause, Boolean>().apply {
            clauses.forEach { put(it, false) }
        }
    }
    val channels = remember {
        userAgreementChannelLinks(
            telegramChannelUrl = OFFICIAL_TELEGRAM_CHANNEL_URL,
            telegramGroupUrl = OFFICIAL_TELEGRAM_GROUP_URL,
            githubUrl = OFFICIAL_GITHUB_URL,
        )
    }
    val canAcknowledge = canAcknowledgeUserAgreement(checked)

    // Back defaults to disagree (exit), not enter the app.
    LocalNavigationBackHandler(enabled = true, onBackCompleted = onDisagree)

    AppSurface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_root")
    ) {
        AppScaffold(
            bottomBar = {
                val actionColors = resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)
                val acknowledgeButtonShape = if (isMiuixNonGlassEnabled()) {
                    RoundedCornerShape(AppChromeSizeTokens.MiuixNativeCompactCornerRadiusDp.dp)
                } else {
                    ButtonDefaults.outlinedShape
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp, bottom = 16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Default / primary: 我不同意 → exit app
                    AppButton(
                        onClick = onDisagree,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_disagree_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = actionColors.backgroundColor,
                            contentColor = actionColors.contentColor,
                        ),
                    ) {
                        AppText(
                            text = stringResource(R.string.onboarding_disagree),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    // Secondary: only after all clauses checked
                    AppOutlinedButton(
                        onClick = onFinish,
                        enabled = canAcknowledge,
                        shape = acknowledgeButtonShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_action_button"),
                    ) {
                        AppText(
                            text = stringResource(R.string.onboarding_acknowledge),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        imageVector = Icons.Outlined.Policy,
                        contentDescription = null,
                        tint = AppContentColors.accent(),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    AppText(
                        text = stringResource(R.string.onboarding_notice_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppContentColors.primaryText(),
                    )
                }
                AppText(
                    text = stringResource(R.string.onboarding_notice_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppContentColors.secondaryText(),
                )

                AppHorizontalDivider()

                clauses.forEach { clause ->
                    val isChecked = checked[clause] == true
                    AppListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Checkbox,
                                onClick = { checked[clause] = !isChecked },
                            )
                            .testTag("user_agreement_clause_${clause.name}"),
                        leadingContent = {
                            AppCheckbox(
                                checked = isChecked,
                                onCheckedChange = { checked[clause] = it },
                            )
                        },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(
                                    imageVector = clause.icon(),
                                    contentDescription = null,
                                    tint = AppContentColors.accent(),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AppText(
                                    text = stringResource(clause.titleResource()),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppContentColors.primaryText(),
                                )
                            }
                        },
                        supportingContent = {
                            AppText(
                                text = stringResource(clause.bodyResource()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppContentColors.secondaryText(),
                            )
                        },
                    )
                }

                AppHorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = AppContentColors.accent(),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = stringResource(R.string.onboarding_official_channels),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                    )
                }
                AppText(
                    text = stringResource(R.string.onboarding_channels_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),
                )

                channels.forEachIndexed { index, channel ->
                    val channelLabel = stringResource(
                        when (index) {
                            0 -> R.string.onboarding_telegram_channel
                            1 -> R.string.onboarding_telegram_group
                            else -> R.string.onboarding_open_source
                        }
                    )
                    AppListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(channel.url) }
                            .testTag("user_agreement_channel_${channel.label}"),
                        leadingContent = {
                            AppIcon(
                                imageVector = channel.icon(),
                                contentDescription = null,
                                tint = AppContentColors.accent(),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        headlineContent = {
                            AppText(
                                text = channelLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AppContentColors.accent(),
                            )
                        },
                        trailingContent = {
                            AppTextButton(onClick = { uriHandler.openUri(channel.url) }) {
                                AppText(stringResource(R.string.common_open))
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun UserAgreementClause.titleResource(): Int = when (this) {
    UserAgreementClause.OPEN_SOURCE_FREE -> R.string.onboarding_clause_open_source_title
    UserAgreementClause.NO_DOMESTIC_PROMO -> R.string.onboarding_clause_promotion_title
    UserAgreementClause.FEEDBACK_WITH_LOGS -> R.string.onboarding_clause_feedback_title
}

private fun UserAgreementClause.bodyResource(): Int = when (this) {
    UserAgreementClause.OPEN_SOURCE_FREE -> R.string.onboarding_clause_open_source_body
    UserAgreementClause.NO_DOMESTIC_PROMO -> R.string.onboarding_clause_promotion_body
    UserAgreementClause.FEEDBACK_WITH_LOGS -> R.string.onboarding_clause_feedback_body
}

private fun UserAgreementClause.icon(): ImageVector = when (this) {
    UserAgreementClause.OPEN_SOURCE_FREE -> Icons.Outlined.Code
    UserAgreementClause.NO_DOMESTIC_PROMO -> Icons.Outlined.Campaign
    UserAgreementClause.FEEDBACK_WITH_LOGS -> Icons.Outlined.BugReport
}

private fun UserAgreementChannelLink.icon(): ImageVector = when (url) {
    OFFICIAL_TELEGRAM_CHANNEL_URL -> Icons.Outlined.Campaign
    OFFICIAL_TELEGRAM_GROUP_URL -> Icons.Outlined.Groups
    OFFICIAL_GITHUB_URL -> Icons.Outlined.Code
    else -> Icons.Outlined.OpenInNew
}
