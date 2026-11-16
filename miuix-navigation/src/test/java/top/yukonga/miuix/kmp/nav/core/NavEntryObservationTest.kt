package top.yukonga.miuix.kmp.nav.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.assertEquals
import org.junit.Test

class NavEntryObservationTest {
    private data class Route(val title: String) : NavKey

    @Test
    fun metadataUpdateNotifiesMetadataReadersWithoutInvalidatingRouteReaders() {
        val content: @Composable (Route) -> Unit = {}
        val entry = NavEntry(Route("home"), "home", mapOf("motion" to "slide"), content)
        val observer = SnapshotStateObserver { it() }
        var routeInvalidations = 0
        var metadataInvalidations = 0
        val routeReader = Any()
        val metadataReader = Any()
        val onRouteChanged: (Any) -> Unit = { routeInvalidations++ }
        val onMetadataChanged: (Any) -> Unit = { metadataInvalidations++ }
        Snapshot.sendApplyNotifications()
        observer.start()
        try {
            observer.observeReads(routeReader, onRouteChanged) { entry.key }
            observer.observeReads(metadataReader, onMetadataChanged) { entry.metadata }
            entry.adoptFrom(NavEntry(Route("home"), "home", mapOf("motion" to "none"), content))
            Snapshot.sendApplyNotifications()

            assertEquals(mapOf("motion" to "none"), entry.metadata)
            assertEquals(0, routeInvalidations)
            assertEquals(1, metadataInvalidations)

            entry.adoptFrom(NavEntry(Route("updated"), "home", entry.metadata, content))
            Snapshot.sendApplyNotifications()
            assertEquals("updated", entry.key.title)
            assertEquals(1, routeInvalidations)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test
    fun contentReplacementStillInvalidatesTheRegistrationEvenWhenRouteIsUnchanged() {
        val initial: @Composable (Route) -> Unit = {}
        val replacement: @Composable (Route) -> Unit = { androidx.compose.runtime.SideEffect {} }
        val entry = NavEntry(Route("home"), "home", emptyMap(), initial)
        val observer = SnapshotStateObserver { it() }
        var invalidations = 0
        val onChanged: (Any) -> Unit = { invalidations++ }
        Snapshot.sendApplyNotifications()
        observer.start()
        try {
            observer.observeReads(Any(), onChanged) { entry.key }
            entry.adoptFrom(NavEntry(Route("home"), "home", emptyMap(), replacement))
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
            observer.clear()
        }
    }
}
