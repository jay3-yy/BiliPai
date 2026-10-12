package com.android.purebilibili.feature.aicu

import com.android.purebilibili.core.store.AicuConsentStore
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.AicuDataSource
import com.android.purebilibili.data.repository.AicuRequestException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AicuViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 0L
    private class Consent(var version: Int = 0, var failSave: Boolean = false) : AicuConsentStore {
        override suspend fun acceptedVersion() = version
        override suspend fun accept(version: Int) {
            if (failSave) error("storage unavailable")
            this.version = version
        }
    }
    private class Source : AicuDataSource {
        val queries = mutableListOf<AicuQuery>()
        var fail = false
        override suspend fun query(query: AicuQuery, onQueuePosition: (Int?) -> Unit): AicuPage {
            queries += query
            onQueuePosition(2)
            onQueuePosition(null)
            if (fail) throw AicuRequestException("limited", 2)
            return AicuPage(query, listOf(AicuRecord("1", "record", 1L)), 200, query.page >= 2)
        }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `all query actions are blocked until visible five seconds and explicit acceptance`() = runTest(dispatcher) {
        val source = Source()
        val consent = Consent()
        val vm = AicuViewModel(source, consent, nowMs = { now })
        vm.initialize(2, AicuCategory.COMMENT)
        runCurrent()
        vm.setDisclaimerVisible(true)
        now = 4999
        vm.acceptDisclaimer()
        vm.submit()
        vm.retry()
        vm.selectCategory(AicuCategory.VIDEO_DANMAKU)
        runCurrent()
        assertTrue(source.queries.isEmpty())
        now = 5000
        runCurrent()
        assertTrue(source.queries.isEmpty()) // Time alone is not consent.
        vm.acceptDisclaimer()
        runCurrent()
        assertEquals(AICU_DISCLAIMER_VERSION, consent.version)
        assertEquals(1, source.queries.size)
        assertEquals(AicuLoadState.CONTENT, vm.state.value.loadState) // Late queue callbacks cannot overwrite results.
        vm.leave()
    }

    @Test fun `background time and recomposition cannot shorten reading time`() {
        val timer = AicuConsentTimer { now }
        timer.setVisible(true)
        now = 2000
        timer.setVisible(true)
        timer.setVisible(false)
        now = 200000
        assertEquals(3, timer.remainingSeconds())
        timer.setVisible(true)
        now += 2999
        assertEquals(1, timer.remainingSeconds())
        now++
        assertEquals(0L, timer.remainingMs())
        assertEquals(5, AicuConsentTimer { now }.remainingSeconds()) // New process/view model.
    }

    @Test fun `failed persistence never unlocks query and can be retried`() = runTest(dispatcher) {
        val source = Source()
        val consent = Consent(failSave = true)
        val vm = AicuViewModel(source, consent, nowMs = { now })
        vm.initialize(2, AicuCategory.COMMENT)
        runCurrent()
        vm.setDisclaimerVisible(true)
        now = 5000
        vm.acceptDisclaimer()
        runCurrent()
        assertEquals(AicuConsentState.REQUIRED, vm.state.value.consent)
        assertTrue(source.queries.isEmpty())
        assertNotNull(vm.state.value.consentError)
        consent.failSave = false
        vm.acceptDisclaimer()
        runCurrent()
        assertEquals(1, source.queries.size)
        vm.leave()
    }

    @Test fun `accepted standalone entry waits for submission and filters are explicit`() = runTest(dispatcher) {
        val source = Source()
        val vm = AicuViewModel(source, Consent(AICU_DISCLAIMER_VERSION), nowMs = { now })
        vm.initialize(null, AicuCategory.COMMENT)
        runCurrent()
        assertTrue(source.queries.isEmpty())
        vm.editUid("2")
        vm.editFilter(AicuFilter(keyword = "first"))
        assertTrue(source.queries.isEmpty())
        vm.submit()
        runCurrent()
        vm.editFilter(AicuFilter(keyword = "draft"))
        vm.changePage(1)
        runCurrent()
        assertEquals("first", source.queries.last().filter.keyword)
        assertEquals(2, source.queries.last().page)
        vm.submit()
        runCurrent()
        assertEquals("draft", source.queries.last().filter.keyword)
        assertEquals(1, source.queries.last().page)
        vm.leave()
    }

    @Test fun `pagination failures retain content and rate limits gate every request`() = runTest(dispatcher) {
        val source = Source()
        val vm = AicuViewModel(source, Consent(AICU_DISCLAIMER_VERSION), nowMs = { now })
        vm.initialize(2, AicuCategory.COMMENT)
        runCurrent()
        source.fail = true
        vm.changePage(1)
        runCurrent()
        assertEquals(1, vm.state.value.page?.query?.page)
        assertEquals(AicuLoadState.ERROR, vm.state.value.loadState)
        vm.retry()
        vm.submit()
        runCurrent()
        assertEquals(2, source.queries.size)
        now = 2000
        source.fail = false
        vm.retry()
        runCurrent()
        assertEquals(2, vm.state.value.page?.query?.page)
        vm.leave()
    }

    @Test fun `cancelled outdated completion cannot replace current results`() = runTest(dispatcher) {
        val pending = CompletableDeferred<Unit>()
        val source = object : AicuDataSource {
            override suspend fun query(query: AicuQuery, onQueuePosition: (Int?) -> Unit): AicuPage {
                if (query.uid == 2L) withContext(NonCancellable) { pending.await() }
                return AicuPage(query, emptyList(), 0, true)
            }
        }
        val vm = AicuViewModel(source, Consent(AICU_DISCLAIMER_VERSION), nowMs = { now })
        vm.initialize(2, AicuCategory.COMMENT)
        runCurrent()
        vm.editUid("3")
        vm.submit()
        runCurrent()
        pending.complete(Unit)
        runCurrent()
        assertEquals(3L, vm.state.value.page?.query?.uid)
        vm.leave()
    }

    @Test fun `cancel before acknowledgment cannot be bypassed by another event`() = runTest(dispatcher) {
        val source = Source()
        val vm = AicuViewModel(source, Consent(), nowMs = { now })
        vm.initialize(2, AicuCategory.COMMENT)
        runCurrent()
        vm.setDisclaimerVisible(true)
        now = 5000
        vm.leave()
        vm.acceptDisclaimer()
        vm.submit()
        runCurrent()
        assertTrue(source.queries.isEmpty())
    }

    @Test fun `old consent version requires a new acknowledgment`() = runTest(dispatcher) {
        val vm = AicuViewModel(Source(), Consent(AICU_DISCLAIMER_VERSION - 1), nowMs = { now })
        vm.initialize(2, AicuCategory.COMMENT)
        runCurrent()
        assertEquals(AicuConsentState.REQUIRED, vm.state.value.consent)
        vm.leave()
    }

    @Test fun `consent loading in background defers the first contextual request until resume`() = runTest(dispatcher) {
        val source = Source()
        val vm = AicuViewModel(source, Consent(AICU_DISCLAIMER_VERSION), nowMs = { now })
        vm.setForeground(false)
        vm.initialize(2, AicuCategory.COMMENT)
        runCurrent()
        assertTrue(source.queries.isEmpty())
        vm.setForeground(true)
        runCurrent()
        assertEquals(1, source.queries.size)
        vm.setForeground(false)
        vm.setForeground(true)
        runCurrent()
        assertEquals(1, source.queries.size)
        vm.leave()
    }

    @Test fun `glass is limited to readable supported small chrome`() {
        assertTrue(shouldUseAicuLiquidTabs(true, 33, 360f, 1f, true))
        assertFalse(shouldUseAicuLiquidTabs(false, 33, 360f, 1f, true))
        assertFalse(shouldUseAicuLiquidTabs(true, 32, 360f, 1f, true))
        assertFalse(shouldUseAicuLiquidTabs(true, 33, 280f, 1f, true))
        assertFalse(shouldUseAicuLiquidTabs(true, 33, 600f, 1.5f, true))
        assertFalse(shouldUseAicuLiquidTabs(true, 33, 360f, 1f, false))
    }
}
