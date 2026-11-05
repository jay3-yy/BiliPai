package com.android.purebilibili.feature.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeStateProjectionTest {
    private data class Content(val account: String, val count: Int?, val loading: Boolean = false)

    @Test
    fun idleProjectionDoesNoBackgroundWorkAndSynchronousReadsUseTheCurrentAccount() = runTest {
        val source = MutableStateFlow(Content("first", 5))
        var selections = 0
        val projected = HomeStateProjection(source) { selections++; it.account to it.count }
        source.value = Content("second", 0)
        runCurrent()

        assertEquals(0, source.subscriptionCount.value)
        assertEquals(0, selections)
        assertEquals("second" to 0, projected.value)
        assertEquals(listOf("second" to 0), projected.replayCache)

        val received = mutableListOf<Pair<String, Int?>>()
        val job = launch { projected.collect { received += it } }
        runCurrent()
        assertEquals(listOf("second" to 0), received)
        job.cancel()
        runCurrent()
        assertEquals(0, source.subscriptionCount.value)
    }

    @Test
    fun unknownRealZeroAndUnchangedFieldsKeepTheirDistinctMeanings() = runTest {
        val source = MutableStateFlow(Content("account", null))
        val projected = HomeStateProjection(source) { it.count }
        val received = mutableListOf<Int?>()
        val job = launch { projected.collect { received += it } }
        runCurrent()
        assertNull(projected.value)
        source.value = Content("account", 0)
        runCurrent()
        source.value = Content("account", 0, loading = true)
        runCurrent()
        source.value = Content("account", 8)
        runCurrent()
        assertEquals(listOf(null, 0, 8), received)
        job.cancel()
    }

    @Test
    fun cancellingOneConsumerKeepsTheOtherLiveAndResubscribeStartsAtLatest() = runTest {
        val source = MutableStateFlow(Content("account", 1))
        val projected = HomeStateProjection(source) { it.count }
        val first = mutableListOf<Int?>()
        val second = mutableListOf<Int?>()
        val firstJob = launch { projected.collect { first += it } }
        val secondJob = launch { projected.collect { second += it } }
        runCurrent()
        firstJob.cancel()
        runCurrent()
        source.value = Content("account", 2)
        runCurrent()
        assertEquals(listOf(1), first)
        assertEquals(listOf(1, 2), second)
        assertEquals(1, source.subscriptionCount.value)
        secondJob.cancel()
        runCurrent()
        source.value = Content("other account", 9)
        runCurrent()
        val resumed = mutableListOf<Int?>()
        val resumedJob = launch { projected.collect { resumed += it } }
        runCurrent()
        assertEquals(listOf(9), resumed)
        resumedJob.cancel()
    }
}
