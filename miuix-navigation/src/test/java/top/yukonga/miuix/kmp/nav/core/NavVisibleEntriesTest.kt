package top.yukonga.miuix.kmp.nav.core

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.runtime.NavPresentation
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

class NavVisibleEntriesTest {
    private data class Route(val id: String) : NavKey
    private fun entry(id: String, transition: NavTransition? = null) = NavEntry(
        key = Route(id), contentKey = id,
        metadata = if (transition == null) emptyMap() else mapOf(NAV_TRANSITION_METADATA_KEY to transition),
        content = {},
    )
    private val transition = object : NavTransition {
        override fun Modifier.transformEntry(scope: NavTransitionScope) = this
    }

    @Test
    fun movingWithinTheSameWindowReusesMembershipAndCrossingTheEndpointUpdatesIt() = runTest {
        val root = entry("root")
        val child = entry("child")
        val presentation = NavPresentation(0.2f)
        presentation.reconcile(listOf(root, child), NavChange.None)
        val visible = navVisibleEntriesState(presentation, transition, mapOf("root" to 0, "child" to 1))
        val initial = visible.value
        assertEquals(listOf(root, child), initial)
        for (top in listOf(0.4f, 0.8f, 1f)) {
            presentation.animatedTop.snapTo(top)
            assertSame(initial, visible.value)
        }
        presentation.animatedTop.snapTo(1.01f)
        assertEquals(listOf(child), visible.value)
        presentation.animatedTop.snapTo(0.8f)
        assertEquals(listOf(root, child), visible.value)
    }

    @Test
    fun reorderingAndRemovingEntriesUpdatesDrawOrderWithoutMutatingAnEarlierResult() = runTest {
        val root = entry("root")
        val child = entry("child")
        val presentation = NavPresentation(0.5f)
        presentation.reconcile(listOf(root, child), NavChange.None)
        val indices = mutableStateMapOf<Any, Int>("root" to 0, "child" to 1)
        val visible = navVisibleEntriesState(presentation, transition, indices)
        val initial = visible.value
        indices["root"] = 1
        indices["child"] = 0
        assertEquals(listOf(child, root), visible.value)
        assertEquals(listOf(root, child), initial)
        indices.remove("child")
        assertEquals(listOf(root), visible.value)
    }

    @Test
    fun modalMetadataAndPreparationFramesRetainTheRequiredEntries() = runTest {
        val modal = object : NavTransition {
            override val opaqueDepth = 2f
            override fun Modifier.transformEntry(scope: NavTransitionScope) = this
        }
        val prepare = object : NavTransition {
            override val preparesEnterFrame = true
            override fun Modifier.transformEntry(scope: NavTransitionScope) = this
        }
        val root = entry("root")
        val middle = entry("middle")
        val top = entry("top", modal)
        val presentation = NavPresentation(2f)
        presentation.reconcile(listOf(root, middle, top), NavChange.None)
        val visible = navVisibleEntriesState(presentation, transition, mapOf("root" to 0, "middle" to 1, "top" to 2))
        assertEquals(listOf(root, middle, top), visible.value)
        top.adoptFrom(entry("top"))
        assertEquals(listOf(middle, top), visible.value)
        presentation.animatedTop.snapTo(1f)
        assertEquals(listOf(root, middle), visible.value)
        top.adoptFrom(entry("top", prepare))
        assertEquals(listOf(root, middle, top), visible.value)
    }
}
