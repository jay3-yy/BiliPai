package com.android.purebilibili.data.repository

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SponsorRequestCancellationTest {
    @Test
    fun leavingCancelsTheHttpCall() = runTest {
        val call = mockk<Call>()
        every { call.enqueue(any()) } just Runs
        every { call.cancel() } just Runs
        val job = launch { call.awaitSponsorResponse() }
        runCurrent()
        job.cancelAndJoin()
        verify(exactly = 1) { call.cancel() }
    }

    @Test
    fun cancellationBeforeDispatchClosesTheQueuedResponse() = runTest {
        val call = mockk<Call>()
        val callback = slot<Callback>()
        val response = mockk<Response>()
        every { call.enqueue(capture(callback)) } just Runs
        every { call.cancel() } just Runs
        every { response.close() } just Runs
        val job = launch { call.awaitSponsorResponse().close() }
        runCurrent()
        callback.captured.onResponse(call, response)
        job.cancelAndJoin()
        verify(exactly = 1) { response.close() }
    }
}
