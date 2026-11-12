package com.android.purebilibili.feature.list

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalRecapUpsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = PersonalVideoRecapStats(videoCount = 3)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun entrySnapshotAndLoadedWindowsAreReused() = runTest(dispatcher) {
        val requested = mutableListOf<PersonalRecapWindow>()
        val week = PersonalVideoRecapStats(videoCount = 12)
        val model = PersonalRecapUpsViewModel(PersonalRecapWindow.TODAY, today) { window ->
            requested += window
            week
        }

        assertEquals(today, model.uiState.value.stats)
        model.selectWindow(PersonalRecapWindow.TODAY)
        model.selectWindow(PersonalRecapWindow.LAST_SEVEN_DAYS)
        assertNull(model.uiState.value.stats)
        assertNull(model.uiState.value.error)
        advanceUntilIdle()
        assertEquals(week, model.uiState.value.stats)

        model.selectWindow(PersonalRecapWindow.TODAY)
        assertEquals(today, model.uiState.value.stats)
        model.selectWindow(PersonalRecapWindow.LAST_SEVEN_DAYS)
        assertEquals(week, model.uiState.value.stats)
        assertEquals(listOf(PersonalRecapWindow.LAST_SEVEN_DAYS), requested)
    }

    @Test
    fun failureCanBeRetriedAndRealZeroIsLoadedData() = runTest(dispatcher) {
        var attempts = 0
        val model = PersonalRecapUpsViewModel(PersonalRecapWindow.TODAY, today) {
            attempts++
            if (attempts == 1) throw IOException("网络不可用")
            PersonalVideoRecapStats()
        }

        model.selectSortOrder(PersonalRecapUpSortOrder.ESTIMATED_DURATION)
        model.selectWindow(PersonalRecapWindow.LAST_MONTH)
        advanceUntilIdle()
        assertEquals(PersonalRecapWindow.LAST_MONTH, model.uiState.value.window)
        assertNull(model.uiState.value.stats)
        assertEquals("网络不可用", model.uiState.value.error)
        assertEquals(PersonalRecapUpSortOrder.ESTIMATED_DURATION, model.uiState.value.sortOrder)

        model.retry()
        assertNull(model.uiState.value.error)
        assertNull(model.uiState.value.stats)
        advanceUntilIdle()
        assertEquals(PersonalVideoRecapStats(), model.uiState.value.stats)
        assertEquals(PersonalRecapUpSortOrder.ESTIMATED_DURATION, model.uiState.value.sortOrder)
        assertNull(model.uiState.value.error)
        model.selectWindow(PersonalRecapWindow.TODAY)
        model.selectWindow(PersonalRecapWindow.LAST_MONTH)
        assertEquals(2, attempts)
        assertEquals(0, model.uiState.value.stats?.videoCount)
    }

    @Test
    fun sortingIsLocalAndSurvivesWindowLoadsAndCacheChanges() = runTest(dispatcher) {
        var requests = 0
        val pending = CompletableDeferred<PersonalVideoRecapStats>()
        val model = PersonalRecapUpsViewModel(PersonalRecapWindow.TODAY, today) {
            requests++
            pending.await()
        }

        model.selectSortOrder(PersonalRecapUpSortOrder.ESTIMATED_DURATION)
        assertEquals(0, requests)
        assertEquals(today, model.uiState.value.stats)
        model.selectWindow(PersonalRecapWindow.LAST_SEVEN_DAYS)
        runCurrent()
        model.selectSortOrder(PersonalRecapUpSortOrder.VIDEO_COUNT)
        model.selectSortOrder(PersonalRecapUpSortOrder.ESTIMATED_DURATION)
        pending.complete(PersonalVideoRecapStats(videoCount = 12))
        advanceUntilIdle()

        assertEquals(PersonalRecapUpSortOrder.ESTIMATED_DURATION, model.uiState.value.sortOrder)
        assertEquals(12, model.uiState.value.stats?.videoCount)
        model.selectWindow(PersonalRecapWindow.TODAY)
        assertEquals(PersonalRecapUpSortOrder.ESTIMATED_DURATION, model.uiState.value.sortOrder)
        assertEquals(today, model.uiState.value.stats)
        assertEquals(1, requests)
    }

    @Test
    fun leavingLoadingWindowCancelsItsRequestWithoutOverwritingSelection() = runTest(dispatcher) {
        var cancelled = false
        val model = PersonalRecapUpsViewModel(PersonalRecapWindow.TODAY, today) {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }

        model.selectWindow(PersonalRecapWindow.LAST_SEVEN_DAYS)
        runCurrent()
        model.selectWindow(PersonalRecapWindow.TODAY)
        advanceUntilIdle()

        assertTrue(cancelled)
        assertEquals(PersonalRecapWindow.TODAY, model.uiState.value.window)
        assertEquals(today, model.uiState.value.stats)
        assertNull(model.uiState.value.error)
    }
}
