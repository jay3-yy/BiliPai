package com.android.purebilibili.navigation3

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavBackCompletionPolicy
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.NavMotionLayout
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

private val LocalStackSceneContent = compositionLocalOf<@Composable (() -> Unit) -> Unit> {
    error("The main stack scene requires a motion layout")
}

private class BiliPaiStackScene(
    override val entries: List<NavEntry<BiliPaiNavKey>>,
    val onBack: () -> Unit,
) : Scene<BiliPaiNavKey> {
    override val key: Any get() = BiliPaiStackScene::class
    override val previousEntries: List<NavEntry<BiliPaiNavKey>>
        get() = entries.subList(0, entries.lastIndex)
    override val content: @Composable () -> Unit = { LocalStackSceneContent.current(onBack) }

    override fun equals(other: Any?): Boolean = other is BiliPaiStackScene && entries == other.entries

    override fun hashCode(): Int = entries.hashCode()
}

@Composable
internal fun BiliPaiAndroidXNavDisplay(
    backStack: NavBackStack<BiliPaiNavKey>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    transition: NavTransition = NavTransitions.MiuixDefault,
    effects: NavDisplayEffects = NavDisplayEffects(),
    backCompletionPolicy: NavBackCompletionPolicy? = null,
    onBackStarted: () -> Unit = {},
    onBackProgressStarted: ((() -> Float) -> Unit)? = null,
    onBackCancelled: () -> Unit = {},
    motion: NavEntryBuilder.() -> Unit,
    content: @Composable (BiliPaiNavKey) -> Unit,
) {
    val latestContent = rememberUpdatedState(content)
    val entryContent: @Composable (BiliPaiNavKey) -> Unit = remember {
        { route -> latestContent.value(route) }
    }
    val routes = backStack.toList()
    val entries = remember(routes, entryContent) {
        routes.map { route ->
            NavEntry(route, contentKey = route.toString(), content = entryContent)
        }
    }

    val windowLifecycleOwner = LocalLifecycleOwner.current
    val sceneStrategies = remember {
        listOf(SceneStrategy<BiliPaiNavKey> { sceneEntries ->
            BiliPaiStackScene(sceneEntries, this.onBack)
        })
    }
    val sceneState = rememberSceneState(entries, sceneStrategies, onBack = onBack)
    val eventState = rememberNavigationEventState(sceneState)
    val sceneEntries = sceneState.currentScene.entries
    val pageContent = remember(routes, sceneEntries) {
        val contentByRoute = buildMap<Any, @Composable (Any) -> Unit> {
            sceneEntries.forEachIndexed { index, entry -> put(routes[index]) { entry.Content() } }
        }
        val findContent: (Any) -> (@Composable (Any) -> Unit) = { contentByRoute.getValue(it) }
        findContent
    }
    // A fixed scene identity keeps the layout alive during stack changes. The live entries and
    // motion metadata are supplied together so a departing page keeps its previous motion.
    val sceneContent: @Composable (() -> Unit) -> Unit = { sceneOnBack ->
        // Each page is capped by exposure and gesture state. Use the window lifecycle as the
        // parent so a logical stack update within this scene cannot pause the whole scene.
        CompositionLocalProvider(LocalLifecycleOwner provides windowLifecycleOwner) {
            NavMotionLayout(
                routes = routes,
                liveBackStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = sceneOnBack,
                onBackStarted = onBackStarted,
                onBackProgressStarted = onBackProgressStarted,
                onBackCancelled = onBackCancelled,
                transition = transition,
                effects = effects,
                backCompletionPolicy = backCompletionPolicy,
                motion = motion,
                entryContent = pageContent,
            )
        }
    }
    // The scene registers one predictive-back handler and shares its driver with pointer swipes.
    CompositionLocalProvider(LocalStackSceneContent provides sceneContent) {
        NavDisplay(
            sceneState = sceneState,
            navigationEventState = eventState,
            modifier = modifier.fillMaxSize(),
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        )
    }
}
