package com.android.purebilibili.core.coroutines

import com.android.purebilibili.core.store.AccountSessionIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountScopedRequestsTest {
    @Test
    fun `local recovery waits for the new account reset to finish`() = runTest {
        val identities = MutableStateFlow(AccountSessionIdentity(101L, 1L))
        val requests = AccountScopedRequests(backgroundScope, identities)
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        requests.observe { events += "account reset" }
        requests.launch {
            try { awaitCancellation() } finally {
                withContext(NonCancellable) { cleanup.await() }
            }
        }
        runCurrent()
        val next = AccountSessionIdentity(202L, 2L)
        identities.value = next
        val recovery = async {
            val identity = requests.awaitCurrentIdentity()
            events += "local data restored"
            identity
        }
        runCurrent()
        assertFalse(recovery.isCompleted)
        cleanup.complete(Unit)
        assertEquals(next, recovery.await())
        assertEquals(listOf("account reset", "local data restored"), events)
    }

    @Test
    fun `switch clears old data after pending request rollback finishes`() = runTest {
        val identities = MutableStateFlow(AccountSessionIdentity(101L, 1L))
        val requests = AccountScopedRequests(backgroundScope, identities)
        val cleanup = CompletableDeferred<Unit>()
        var displayed = "account A"
        requests.observe { displayed = "account B" }
        requests.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    cleanup.await()
                    displayed = "old request rollback"
                }
            }
        }
        runCurrent()

        identities.value = AccountSessionIdentity(202L, 2L)
        runCurrent()
        cleanup.complete(Unit)
        runCurrent()

        assertEquals("account B", displayed)
        var currentResult = ""
        requests.launch { currentResult = "fresh B data" }
        runCurrent()
        assertEquals("fresh B data", currentResult)
    }

    @Test
    fun `queued old request cannot start after identity changes`() = runTest {
        val identities = MutableStateFlow(AccountSessionIdentity(101L, 1L))
        val requests = AccountScopedRequests(backgroundScope, identities)
        requests.observe { }
        runCurrent()
        var mutatedAccount = false
        requests.launch { mutatedAccount = true }
        identities.value = AccountSessionIdentity(202L, 2L)
        runCurrent()
        assertFalse(mutatedAccount)
    }

    @Test
    fun `rapid switching skips superseded reset and never revives earlier account requests`() = runTest {
        val first = AccountSessionIdentity(101L, 1L)
        val identities = MutableStateFlow(first)
        val requests = AccountScopedRequests(backgroundScope, identities)
        val cleanup = CompletableDeferred<Unit>()
        val reloadedAccounts = mutableListOf<AccountSessionIdentity>()
        requests.observe { reloadedAccounts += requests.identity }
        requests.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanup.await() }
            }
        }
        runCurrent()
        identities.value = AccountSessionIdentity(202L, 2L)
        runCurrent()
        val returned = AccountSessionIdentity(101L, 3L)
        identities.value = returned
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf(returned), reloadedAccounts)
        assertFalse(requests.isCurrent(first))
        assertTrue(requests.isCurrent(returned))

        identities.value = AccountSessionIdentity(null, 4L)
        runCurrent()
        assertFalse(requests.isCurrent(returned))
        assertEquals(null, reloadedAccounts.last().mid)
    }
}
