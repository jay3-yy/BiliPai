package com.android.purebilibili.feature.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.motion.iosMorphTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.blur.Backdrop

private const val LINKED_DOCK_MERGE_DURATION_MILLIS = 280
private const val LINKED_DOCK_SEARCH_DURATION_MILLIS = 240

@Composable
internal fun LinkedBottomDock(
    currentItem: BottomNavItem,
    firstItem: BottomNavItem,
    firstLabel: String,
    searchEnabled: Boolean,
    isFeedScrollInProgress: Boolean,
    collapseRequested: Boolean,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    containerColor: Color,
    backdrop: Backdrop?,
    glassEnabled: Boolean,
    liquidGlassTuning: LiquidGlassTuning,
    iconStyle: SharedFloatingBottomBarIconStyle,
    nowPlayingContent: (@Composable (Modifier, Float, Float, Float) -> Unit)?,
    modifier: Modifier = Modifier,
    navigationContent: @Composable () -> Unit,
) {
    val hasAudio = nowPlayingContent != null
    var phase by remember(currentItem, searchEnabled, hasAudio) {
        mutableStateOf(
            if (currentItem == BottomNavItem.HOME) {
                LinkedDockPhase.Expanded
            } else {
                resolveLinkedDockRestingPhase(collapseRequested, hasAudio)
            }
        )
    }
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val scroll = LocalHomeScrollOffset.current
    val scrolling by rememberUpdatedState(isFeedScrollInProgress)
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(currentItem, hasAudio, scroll, threshold) {
        if (currentItem != BottomNavItem.HOME) return@LaunchedEffect
        var previous = scroll.floatValue
        var accumulated = 0f
        snapshotFlow { scroll.floatValue to scrolling }.collect { (offset, active) ->
            val delta = offset - previous
            previous = offset
            if (!active || phase == LinkedDockPhase.Search) {
                accumulated = 0f
            } else {
                accumulated = accumulateDockScroll(accumulated, delta)
                if (offset <= 0f || accumulated <= -threshold) {
                    phase = LinkedDockPhase.Expanded
                    accumulated = 0f
                } else if (hasAudio && accumulated >= threshold) {
                    phase = LinkedDockPhase.Playback
                    accumulated = 0f
                }
            }
        }
    }
    LaunchedEffect(currentItem, collapseRequested, hasAudio) {
        if (currentItem != BottomNavItem.HOME && phase != LinkedDockPhase.Search) {
            phase = resolveLinkedDockRestingPhase(collapseRequested, hasAudio)
        }
    }
    fun expand() {
        focusManager.clearFocus()
        phase = LinkedDockPhase.Expanded
    }
    BackHandler(phase != LinkedDockPhase.Expanded) { expand() }
    val reduceMotion = rememberSystemReduceMotion()
    val transition = updateTransition(targetState = phase, label = "linkedBottomDock")
    val merge = transition.animateFloat(
        transitionSpec = {
            if (reduceMotion) snap() else iosMorphTween(LINKED_DOCK_MERGE_DURATION_MILLIS)
        },
        label = "dockMerge",
    ) { if (it == LinkedDockPhase.Expanded) 0f else 1f }
    val search = transition.animateFloat(
        transitionSpec = {
            if (reduceMotion) snap() else iosMorphTween(LINKED_DOCK_SEARCH_DURATION_MILLIS)
        },
        label = "dockSearch",
    ) { if (it == LinkedDockPhase.Search) 1f else 0f }
    val shape = resolveSharedBottomBarCapsuleShape()
    val contentColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary
    // A single audio child is measured and moved between rows. Playback and artwork stay mounted.
    Layout(
        modifier = modifier.fillMaxWidth().imePadding().navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        content = {
            Box(Modifier.graphicsLayer { alpha = (1f - merge.value * 3f).coerceIn(0f, 1f) }
                .pointerInput(phase) {
                    if (phase != LinkedDockPhase.Expanded) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .then(if (phase != LinkedDockPhase.Expanded) Modifier.clearAndSetSemantics {} else Modifier)) {
                if (merge.value < 0.999f) navigationContent()
            }
            Box(Modifier.graphicsLayer {
                alpha = (merge.value * 2f).coerceIn(0f, 1f)
            }
                .then(if (phase != LinkedDockPhase.Expanded) Modifier.clickable(role = Role.Button) { expand() }
                    else Modifier.clearAndSetSemantics {}), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().biliPaiFloatingDockShell(backdrop, containerColor, 0f, shape = shape,
                    enabled = glassEnabled, liquidGlassTuning = liquidGlassTuning))
                if (merge.value > 0.001f) {
                    AppIcon(
                        imageVector = if (iconStyle == SharedFloatingBottomBarIconStyle.MIUIX) {
                            resolveHomeNavigationBarIcon(firstItem, currentItem == firstItem)
                        } else resolveMaterialBottomBarIcon(firstItem, currentItem == firstItem),
                        contentDescription = "$firstLabel，展开底栏",
                        tint = accentColor,
                    )
                }
            }
            Box {
                nowPlayingContent?.invoke(Modifier.fillMaxSize(), merge.value.coerceIn(0f, 1f),
                    search.value.coerceIn(0f, 1f), 0f)
            }
            Box(contentAlignment = Alignment.Center) {
                if (searchEnabled) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().biliPaiFloatingDockShell(backdrop, containerColor, 0f, shape = shape,
                                enabled = glassEnabled, liquidGlassTuning = liquidGlassTuning))
                        Box(Modifier.fillMaxSize().then(
                            if (phase != LinkedDockPhase.Search) Modifier.clickable(role = Role.Button) {
                                phase = LinkedDockPhase.Search
                            } else Modifier
                        )) {
                            BiliPaiBottomBarSearchVisualContent(
                                expanded = phase == LinkedDockPhase.Search,
                                query = query,
                                onQueryChange = { query = it },
                                onSubmit = {
                                    focusManager.clearFocus()
                                    if (query.isBlank()) onSearchClick() else onSearchKeywordSubmit(query.trim())
                                },
                                contentColor = contentColor,
                                accentColor = accentColor,
                                iconScale = 1f,
                                fieldAlpha = search.value.coerceIn(0f, 1f),
                                interactive = true,
                                iconStyle = iconStyle,
                            )
                        }
                    }
                }
            }
        },
    ) { children, constraints ->
        val width = constraints.maxWidth.coerceAtMost(600.dp.roundToPx())
        val button = 56.dp.roundToPx()
        val barHeight = 64.dp.roundToPx()
        val controlHeight = 56.dp.roundToPx()
        // Keep the compact search surface circular; its width starts at [button].
        val searchHeight = button
        val gap = 8.dp.roundToPx()
        val progress = merge.value.coerceIn(0f, 1f)
        val geometry = resolveLinkedDockGeometry(
            width, button, barHeight, gap, hasAudio, searchEnabled, progress, search.value,
        )
        val top = geometry.top
        val searchWidth = geometry.searchWidth
        val audioWidth = geometry.audioWidth
        val navWidth = (width - (if (searchEnabled) button + gap else 0)).coerceAtLeast(0)
        val nav = children[0].measure(Constraints.fixed(navWidth, barHeight))
        val first = children[1].measure(Constraints.fixed(button, controlHeight))
        val audio = children[2].measure(
            Constraints.fixed(if (hasAudio) audioWidth else 0, if (hasAudio) controlHeight else 0)
        )
        val searchBox = children[3].measure(Constraints.fixed(searchWidth, searchHeight))
        layout(constraints.maxWidth, geometry.height) {
            val left = (constraints.maxWidth - width) / 2
            if (progress < 0.999f) nav.placeRelative(left, top)
            if (progress > 0.001f) first.placeRelative(left, top + (barHeight - controlHeight) / 2)
            if (hasAudio) {
                audio.placeRelative(
                    left + geometry.audioX,
                    geometry.audioY + (barHeight - controlHeight) / 2,
                )
            }
            searchBox.placeRelative(
                left + width - searchWidth,
                top + (barHeight - searchHeight) / 2,
            )
        }
    }
}
