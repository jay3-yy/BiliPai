package com.android.purebilibili.feature.message

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.android.purebilibili.R
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.WindowHeightSizeClass
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
private data object MessageInbox : NavKey

@Serializable
private data class MessageConversation(
    val talkerId: Long,
    val sessionType: Int,
    val userName: String,
) : NavKey

private val messageNavigationState = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(MessageInbox::class, MessageInbox.serializer())
            subclass(MessageConversation::class, MessageConversation.serializer())
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun MessageCenterScreen(
    onBack: () -> Unit,
    onConversationVisibilityChanged: (Boolean) -> Unit,
    onTopItemClick: (MessageCenterDestination) -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenBilibiliLink: (String) -> Unit,
    onOpenConversationWindow: (BiliPaiNavKey.Chat, () -> Unit) -> Boolean,
) {
    val accountIdentity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val latestConversationVisibilityChanged by rememberUpdatedState(onConversationVisibilityChanged)
    DisposableEffect(Unit) {
        onDispose { latestConversationVisibilityChanged(false) }
    }
    val windowDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val compactHeight = LocalWindowSizeClass.current.heightSizeClass == WindowHeightSizeClass.Compact
    val directive = windowDirective.copy(
        maxHorizontalPartitions = if (compactHeight) 1 else windowDirective.maxHorizontalPartitions,
        maxVerticalPartitions = 1,
    )
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        shouldHandleSinglePaneLayout = true,
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = directive,
    )
    // 单栏可以从会话行进独立窗口，双栏继续用同一个详情栏切换会话。
    val singlePane = directive.maxHorizontalPartitions == 1

    key(accountIdentity) {
        // 手机、折叠屏和大屏共用同一条会话导航，窗口尺寸变化只更新各栏可见性。
        val backStack = rememberNavBackStack(messageNavigationState, MessageInbox)
        val hasConversation = backStack.last() is MessageConversation
        SideEffect { latestConversationVisibilityChanged(hasConversation) }
        val closeConversation: () -> Unit = {
            if (backStack.last() is MessageConversation) backStack.removeAt(backStack.lastIndex)
        }
        NavDisplay(
            backStack = backStack,
            onBack = closeConversation,
            modifier = Modifier.fillMaxSize(),
            sceneStrategies = listOf(listDetailStrategy),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<MessageInbox>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AppText(
                                    text = stringResource(R.string.message_select_conversation),
                                    tapToCopyEnabled = false,
                                )
                            }
                        },
                    ),
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize().focusGroup()) {
                        InboxScreen(
                            onBack = onBack,
                            onTopItemClick = onTopItemClick,
                            onOpenLink = onOpenBilibiliLink,
                            paneWidth = maxWidth,
                            conversationWindowEnabled = singlePane,
                            onSessionClick = { talkerId, sessionType, userName ->
                                val openInPane: () -> Unit = {
                                    val current = backStack.last()
                                    val conversation = MessageConversation(talkerId, sessionType, userName)
                                    if (current is MessageConversation) {
                                        if (current.talkerId != talkerId || current.sessionType != sessionType) {
                                            backStack[backStack.lastIndex] = conversation
                                        }
                                    } else {
                                        backStack.add(conversation)
                                    }
                                }
                                if (!singlePane || !onOpenConversationWindow(
                                        BiliPaiNavKey.Chat(talkerId, sessionType, userName), openInPane,
                                    )
                                ) openInPane()
                            },
                        )
                    }
                }
                entry<MessageConversation>(metadata = ListDetailSceneStrategy.detailPane()) { conversation ->
                    val layout = LocalListDetailSceneScope.current?.scaffoldTransitionScope
                        ?.scaffoldStateTransition?.targetState
                    Box(Modifier.fillMaxSize().focusGroup()) {
                        ChatScreen(
                            talkerId = conversation.talkerId,
                            sessionType = conversation.sessionType,
                            userName = conversation.userName.ifBlank { "用户${conversation.talkerId}" },
                            onBack = closeConversation,
                            onNavigateToVideo = onNavigateToVideo,
                            onOpenBilibiliLink = onOpenBilibiliLink,
                            showCloseButton = layout?.get(ListDetailPaneScaffoldRole.List) == PaneAdaptedValue.Expanded,
                        )
                    }
                }
            },
        )
    }
}
