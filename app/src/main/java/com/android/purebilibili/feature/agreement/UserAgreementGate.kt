package com.android.purebilibili.feature.agreement

import com.android.purebilibili.core.ui.AppContentColors

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.app.PureApplication
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CrashReporter
import kotlinx.coroutines.launch

/**
 * First-launch agreement gate: the app renders only after the user agrees to the
 * current agreement version; declining exits the app immediately.
 * The screen follows the active app theme and branches on dark/light.
 */
@Composable
fun UserAgreementGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accepted by remember { mutableStateOf(UserAgreementStore.isAccepted(context)) }
    val initialChoice = remember { readInitialUserAgreementTelemetryChoice(context) }
    var crashTrackingEnabled by remember { mutableStateOf(initialChoice.crashTrackingEnabled) }
    var analyticsEnabled by remember { mutableStateOf(initialChoice.analyticsEnabled) }
    var isSaving by remember { mutableStateOf(false) }

    if (accepted) {
        content()
    } else {
        UserAgreementGateScreen(
            crashTrackingEnabled = crashTrackingEnabled,
            onCrashTrackingChange = { crashTrackingEnabled = it },
            analyticsEnabled = analyticsEnabled,
            onAnalyticsChange = { analyticsEnabled = it },
            isSaving = isSaving,
            onAgree = {
                if (isSaving) return@UserAgreementGateScreen
                isSaving = true
                scope.launch {
                    try {
                        UserAgreementStore.accept(context)
                        SettingsManager.setCrashTrackingEnabled(context, crashTrackingEnabled)
                        SettingsManager.setAnalyticsEnabled(context, analyticsEnabled)
                        SettingsManager.setCrashTrackingConsentShown(context, true)
                        val app = context.applicationContext as? PureApplication
                        if (app != null) {
                            app.onUserAgreementAccepted(
                                crashTrackingEnabled = crashTrackingEnabled,
                                analyticsEnabled = analyticsEnabled,
                            )
                        } else {
                            CrashReporter.setEnabled(crashTrackingEnabled)
                            AnalyticsHelper.setEnabled(analyticsEnabled)
                        }
                        accepted = true
                    } finally {
                        isSaving = false
                    }
                }
            },
        )
    }
}

@Composable
private fun UserAgreementGateScreen(
    crashTrackingEnabled: Boolean,
    onCrashTrackingChange: (Boolean) -> Unit,
    analyticsEnabled: Boolean,
    onAnalyticsChange: (Boolean) -> Unit,
    isSaving: Boolean,
    onAgree: () -> Unit,
) {
    val activity = LocalActivity.current
    val darkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val titleColor = if (darkTheme) MaterialTheme.colorScheme.onBackground
    else AppContentColors.primaryText()
    // The agree button unlocks only after the user has scrolled the full text to the bottom
    // (sticky: it stays enabled afterwards even if they scroll back up).
    // must observe layoutInfo through derivedStateOf: canScrollForward is not snapshot state
    // and would otherwise never recompose the button on scroll.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrolledToEnd by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 1
        }
    }
    var reachedBottom by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(scrolledToEnd) {
        if (scrolledToEnd) reachedBottom = true
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            // Avoid the status bar / navigation bar so titles are never drawn underneath them.
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText(
                stringResource(UserAgreementText.TITLE),
                style = MaterialTheme.typography.headlineSmall,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            AppCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "agreement_body") { UserAgreementBody() }
                    item(key = "telemetry_preferences") {
                        UserAgreementTelemetryPreferences(
                            crashTrackingEnabled = crashTrackingEnabled,
                            onCrashTrackingChange = onCrashTrackingChange,
                            analyticsEnabled = analyticsEnabled,
                            onAnalyticsChange = onAnalyticsChange,
                            enabled = !isSaving,
                        )
                    }
                    item(key = "agreement_end") {
                        AppText(
                            stringResource(com.android.purebilibili.R.string.user_agreement_read_to_end),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText(),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            AppButton(
                onClick = onAgree,
                enabled = reachedBottom && !isSaving,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            ) {
                AppText(
                    stringResource(
                        if (reachedBottom) com.android.purebilibili.R.string.user_agreement_agree_continue
                        else com.android.purebilibili.R.string.user_agreement_read_to_end_action
                    )
                )
            }
            AppTextButton(
                onClick = { activity?.finishAffinity() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            ) { AppText(stringResource(com.android.purebilibili.R.string.user_agreement_decline_exit)) }
        }
    }
}

@Composable
private fun UserAgreementTelemetryPreferences(
    crashTrackingEnabled: Boolean,
    onCrashTrackingChange: (Boolean) -> Unit,
    analyticsEnabled: Boolean,
    onAnalyticsChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppHorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
        AppText(
            text = stringResource(R.string.user_agreement_telemetry_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = AppContentColors.accent(),
        )
        AppText(
            text = stringResource(R.string.user_agreement_telemetry_section_hint),
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )
        UserAgreementTelemetryToggleRow(
            title = stringResource(R.string.user_agreement_crash_tracking_title),
            subtitle = stringResource(R.string.user_agreement_crash_tracking_subtitle),
            checked = crashTrackingEnabled,
            enabled = enabled,
            onCheckedChange = onCrashTrackingChange,
        )
        UserAgreementTelemetryToggleRow(
            title = stringResource(R.string.user_agreement_analytics_title),
            subtitle = stringResource(R.string.user_agreement_analytics_subtitle),
            checked = analyticsEnabled,
            enabled = enabled,
            onCheckedChange = onAnalyticsChange,
        )
    }
}

@Composable
private fun UserAgreementTelemetryToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Field))
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppText(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AppContentColors.primaryText(),
            )
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** Full agreement body, also used by the review dialog reachable from the login footer. */
@Composable
fun UserAgreementBody(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UserAgreementText.SECTIONS.forEach { section ->
            AppText(stringResource(section.title), style = MaterialTheme.typography.titleSmall,
                color = AppContentColors.accent())
            section.bodyLines.forEach { line ->
                LinkifiedText(stringResource(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val URL_REGEX = Regex("""https?://[A-Za-z0-9./_%?=&:#~+-]+""")

/** Renders text with highlighted, clickable URLs (e.g. the open-source repo link). */
@Composable
private fun LinkifiedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val linkColor = AppContentColors.accent()
    val linkStyle = androidx.compose.ui.text.SpanStyle(
        color = linkColor,
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
    )
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        URL_REGEX.findAll(text).forEach { match ->
            val range = match.range
            if (range.first > cursor) append(text.substring(cursor, range.first))
            val url = match.value
            withLink(androidx.compose.ui.text.LinkAnnotation.Url(
                url,
                androidx.compose.ui.text.TextLinkStyles(style = linkStyle),
            ) { uriHandler.openUri(url) }) { append(url) }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    AppText(annotated, style = style)
}

/** Read-only review dialog, e.g. from the login screen footer. Follows the active theme. */
@Composable
fun UserAgreementReviewDialog(onDismiss: () -> Unit) {
    com.android.purebilibili.core.ui.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(UserAgreementText.TITLE)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                UserAgreementBody()
            }
        },
        confirmButton = {
            AppDialogAction(onClick = onDismiss) {
                AppText(stringResource(com.android.purebilibili.R.string.common_close))
            }
        },
    )
}
