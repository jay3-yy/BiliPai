package top.yukonga.miuix.kmp.nav.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.NAV_TRANSITION_METADATA_KEY
import top.yukonga.miuix.kmp.nav.core.NavEntry
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.entryProvider
import top.yukonga.miuix.kmp.nav.transition.NavTransition

class NavPresentationTest {
    private data class Route(val id: String, val title: String = id) : NavKey

    private fun entry(id: String, title: String = id): NavEntry<Route> =
        NavEntry(key = Route(id, title), contentKey = id, metadata = emptyMap(), content = {})

    @Test
    fun pushPreservesEntryIdentityAndAdoptsUpdatedArguments() {
        val presentation = NavPresentation(0f)
        val root = entry("root")
        presentation.reconcile(listOf(root), NavChange.None)
        val beforePush = presentation.presented

        presentation.reconcile(listOf(entry("root", "updated"), entry("child")), NavChange.Push)

        assertEquals(1, beforePush.size)
        assertEquals(2, presentation.presented.size)
        assertSame(root, presentation.presented.first())
        assertEquals("updated", root.key.title)
    }

    @Test
    fun sameTypeEntriesKeepTheirOwnTransitionThroughPushAndPop() {
        val first = Route("videoA")
        val related = Route("videoB")
        val sharedTransition = NavTransition { this }
        val pageTransition = NavTransition { this }
        val sharedTransitions = mapOf(first to sharedTransition)
        val provider = entryProvider {
            entry<Route>(transition = pageTransition, transitionForKey = sharedTransitions::get) {}
        }
        val presentation = NavPresentation(0f)
        presentation.reconcile(listOf(provider(first)), NavChange.None)

        presentation.reconcile(listOf(provider(first), provider(related)), NavChange.Push)
        assertSame(sharedTransition, presentation.presented.first().metadata[NAV_TRANSITION_METADATA_KEY])
        assertSame(pageTransition, presentation.presented.last().metadata[NAV_TRANSITION_METADATA_KEY])

        presentation.reconcile(listOf(provider(first)), NavChange.Pop)
        val leaving = presentation.presented.last()
        assertTrue(leaving.presentation.isRemoving)
        assertSame(pageTransition, leaving.metadata[NAV_TRANSITION_METADATA_KEY])
        assertSame(sharedTransition, presentation.presented.first().metadata[NAV_TRANSITION_METADATA_KEY])
    }

    @Test
    fun leavingEntryCanReviveAndUnloadWithoutMutatingCapturedMembership() {
        val presentation = NavPresentation(1f)
        val root = entry("root")
        val child = entry("child")
        presentation.reconcile(listOf(root, child), NavChange.None)
        val beforePop = presentation.presented

        presentation.reconcile(listOf(entry("root")), NavChange.Pop)
        assertTrue(child.presentation.isRemoving)
        assertSame(child, presentation.presented.last())

        presentation.reconcile(listOf(entry("root"), entry("child", "revived")), NavChange.Push)
        assertFalse(child.presentation.isRemoving)
        assertEquals("revived", child.key.title)
        assertSame(child, presentation.presented.last())

        presentation.reconcile(listOf(entry("root")), NavChange.Pop)
        presentation.unload(child)
        assertEquals(1, presentation.presented.size)
        assertEquals(2, beforePop.size)
    }
}
