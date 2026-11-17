package top.yukonga.miuix.kmp.nav.core

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import top.yukonga.miuix.kmp.nav.runtime.NavPresentation
import top.yukonga.miuix.kmp.nav.runtime.isVisibleAt
import top.yukonga.miuix.kmp.nav.runtime.relativeDepth
import top.yukonga.miuix.kmp.nav.transition.NavTransition

/** 每帧只计算可见性布尔值；成员、顺序或转场元数据变化时才重建结果列表。 */
internal fun navVisibleEntriesState(
    presentation: NavPresentation,
    transition: NavTransition,
    indexByContentKey: Map<Any, Int>,
): State<List<NavEntry<*>>> {
    val candidates = derivedStateOf {
        val presented = presentation.presented
        var windowDepth = transition.opaqueDepth
        for (entry in presented) {
            val entryTransition = entry.metadata[NAV_TRANSITION_METADATA_KEY] as? NavTransition ?: transition
            windowDepth = maxOf(windowDepth, entryTransition.opaqueDepth)
        }
        val depth = windowDepth
        presented.mapNotNull { entry ->
            val index = indexByContentKey[entry.contentKey] ?: return@mapNotNull null
            val entryTransition = entry.metadata[NAV_TRANSITION_METADATA_KEY] as? NavTransition ?: transition
            Triple(index, entry, derivedStateOf(structuralEqualityPolicy()) {
                val d = relativeDepth(presentation.animatedTop.value, index)
                val settlingSpring = entry.presentation.isRemoving &&
                    entryTransition.retainsSpringOvershoot && presentation.animatedTop.isRunning
                val preparingEnter = !entry.presentation.isRemoving && d == -1f &&
                    entryTransition.preparesEnterFrame
                isVisibleAt(d, depth) || settlingSpring || preparingEnter
            })
        }.sortedBy { it.first }
    }
    return derivedStateOf {
        val entries = candidates.value
        buildList {
            for ((_, entry, visible) in entries) {
                if (visible.value) add(entry)
            }
        }
    }
}
