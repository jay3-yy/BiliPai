package com.android.purebilibili.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceGroup
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.feature.agreement.UserAgreementReviewDialog
import com.android.purebilibili.feature.settings.OFFICIAL_GITHUB_URL
import com.android.purebilibili.feature.settings.OFFICIAL_TELEGRAM_CHANNEL_URL
import com.android.purebilibili.feature.settings.OFFICIAL_TELEGRAM_GROUP_URL
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold

/**
 * Read-only usage notice & official channels reference opened from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clauses = remember { userAgreementClauseList() }
    val channels = remember {
        userAgreementChannelLinks(
            telegramChannelUrl = OFFICIAL_TELEGRAM_CHANNEL_URL,
            telegramGroupUrl = OFFICIAL_TELEGRAM_GROUP_URL,
            githubUrl = OFFICIAL_GITHUB_URL,
        )
    }
    var showUserAgreementDialog by remember { mutableStateOf(false) }
    var showFirstLaunchSetupReplay by remember { mutableStateOf(false) }
    val contentBottomPadding = resolveBottomSafeAreaPadding(
        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        extraBottomPadding = 32.dp,
    )

    SettingsPageScaffold(
        title = stringResource(R.string.onboarding_notice_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.common_back),
        bottomContentPadding = contentBottomPadding,
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_root"),
        lazyListContent = {
            item(key = "intro") {
                AppText(
                    text = stringResource(R.string.onboarding_notice_review_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            item(key = "guidelines_title") {
                AppPreferenceSectionTitle(stringResource(R.string.onboarding_section_guidelines))
            }
            item(key = "guidelines_group") {
                AppPreferenceGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    clauses.forEachIndexed { index, clause ->
                        AppPreference(
                            icon = clause.icon(),
                            title = stringResource(clause.titleResource()),
                            subtitle = stringResource(clause.bodyResource()),
                            onClick = null,
                            showChevron = false,
                        )
                        if (index < clauses.lastIndex) {
                            AppPreferenceDivider()
                        }
                    }
                }
            }

            item(key = "channels_title") {
                Spacer(modifier = Modifier.height(8.dp))
                AppPreferenceSectionTitle(stringResource(R.string.onboarding_official_channels))
            }
            item(key = "channels_description") {
                AppText(
                    text = stringResource(R.string.onboarding_channels_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item(key = "channels_group") {
                AppPreferenceGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    channels.forEachIndexed { index, channel ->
                        val channelLabel = stringResource(
                            when (index) {
                                0 -> R.string.onboarding_telegram_channel
                                1 -> R.string.onboarding_telegram_group
                                else -> R.string.onboarding_open_source
                            }
                        )
                        AppPreference(
                            icon = channel.icon(),
                            title = channelLabel,
                            onClick = { uriHandler.openUri(channel.url) },
                            showChevron = true,
                        )
                        if (index < channels.lastIndex) {
                            AppPreferenceDivider()
                        }
                    }
                }
            }

            item(key = "agreement_title") {
                Spacer(modifier = Modifier.height(8.dp))
                AppPreferenceSectionTitle(stringResource(R.string.onboarding_section_agreement))
            }
            item(key = "agreement_group") {
                AppPreferenceGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AppPreference(
                        icon = ImageVector.vectorResource(R.drawable.ms_palette_24),
                        title = "重新体验新手引导",
                        subtitle = "直观体验外观风格、深浅配色、液态玻璃与首页布局",
                        onClick = { showFirstLaunchSetupReplay = true },
                        showChevron = true,
                    )
                    AppPreferenceDivider()
                    AppPreference(
                        icon = Icons.Outlined.Policy,
                        title = stringResource(R.string.onboarding_view_user_agreement),
                        subtitle = stringResource(R.string.onboarding_view_user_agreement_summary),
                        onClick = { showUserAgreementDialog = true },
                        showChevron = true,
                    )
                }
            }
        },
    )

    if (showFirstLaunchSetupReplay) {
        val replayReduceMotion = LocalSystemReduceMotion.current
        val replayVisibleState = remember {
            MutableTransitionState(replayReduceMotion).apply { targetState = true }
        }
        LaunchedEffect(replayVisibleState.isIdle, replayVisibleState.currentState) {
            if (replayVisibleState.isIdle && !replayVisibleState.currentState) {
                showFirstLaunchSetupReplay = false
            }
        }
        val dismissReplay = {
            if (replayReduceMotion) {
                showFirstLaunchSetupReplay = false
            } else {
                replayVisibleState.targetState = false
            }
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = dismissReplay,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val enterSpec = AppMotionTokens.emphasizedSpec<Float>()
            val exitSpec = AppMotionTokens.expressiveSpec<Float>()
            AnimatedVisibility(
                visibleState = replayVisibleState,
                enter = if (replayReduceMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(enterSpec) + scaleIn(enterSpec, initialScale = 0.96f)
                },
                exit = if (replayReduceMotion) {
                    ExitTransition.None
                } else {
                    fadeOut(exitSpec) + scaleOut(exitSpec, targetScale = 0.96f)
                },
            ) {
                FirstLaunchSetupScreen(
                    onFinished = dismissReplay,
                    isReplay = true,
                )
            }
        }
    }

    if (showUserAgreementDialog) {
        UserAgreementReviewDialog(
            onDismiss = { showUserAgreementDialog = false },
        )
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
    else -> Icons.Outlined.Public
}
