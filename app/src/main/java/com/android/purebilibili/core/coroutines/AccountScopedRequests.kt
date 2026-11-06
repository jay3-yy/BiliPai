package com.android.purebilibili.core.coroutines

import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** Main-thread owner for requests whose cached results belong to the current app account. */
internal class AccountScopedRequests(
    private val parentScope: CoroutineScope,
    private val identities: StateFlow<AccountSessionIdentity> = TokenManager.accountIdentity,
) {
    var identity: AccountSessionIdentity = identities.value
        private set
    private val observedIdentity = MutableStateFlow(identity)

    private var requests = newScope()

    private fun newScope() = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )

    fun isCurrent(requestedIdentity: AccountSessionIdentity = identity): Boolean =
        requestedIdentity == identities.value

    /** 等待旧请求与账号重置完成，再允许恢复本地数据。 */
    suspend fun awaitCurrentIdentity(): AccountSessionIdentity =
        combine(observedIdentity, identities) { observed, current -> observed to current }
            .first { (observed, current) -> observed == current }.first

    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        val requestedIdentity = identity
        return requests.launch(context) {
            if (!isCurrent(requestedIdentity)) return@launch
            block()
        }
    }

    fun observe(onAccountChanged: () -> Unit): Job = parentScope.launch {
        // Base ViewModels can install this observer before subclass state is initialized.
        yield()
        identities.collect { next ->
            if (next == identity) return@collect
            requests.cancel()
            // Old finally/rollback blocks must finish before the new account state is reset.
            requests.coroutineContext[Job]?.join()
            if (next != identities.value) return@collect
            identity = next
            requests = newScope()
            onAccountChanged()
            observedIdentity.value = identity
        }
    }
}
