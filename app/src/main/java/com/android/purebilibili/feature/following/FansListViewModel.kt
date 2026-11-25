package com.android.purebilibili.feature.following

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FollowingUser
import com.android.purebilibili.data.repository.FanManagementRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class FansListState(
    val identity: AccountSessionIdentity = TokenManager.accountIdentity.value,
    val users: ImmutableList<FollowingUser> = persistentListOf(),
    val total: Int? = null,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val canManage: Boolean = false,
    val removingMid: Long? = null,
)

internal fun mergeFansPage(
    existing: List<FollowingUser>,
    incoming: List<FollowingUser>,
    removed: Set<Long>,
): ImmutableList<FollowingUser> =
    (existing + incoming).distinctBy { it.mid }.filterNot { it.mid in removed }.toImmutableList()

internal fun shouldLoadMoreFans(
    page: Int,
    furthestLoadedPage: Int,
    incomingCount: Int,
    previousSize: Int,
    mergedSize: Int,
    total: Int,
): Boolean = incomingCount > 0 && mergedSize < total &&
    (page == 1 || page < furthestLoadedPage || mergedSize > previousSize)

internal data class FansPageRequest(val page: Int, val refresh: Boolean)

internal fun resolveFansPageRequest(nextPage: Int, refresh: Boolean, failed: FansPageRequest?): FansPageRequest =
    if (refresh) FansPageRequest(1, true) else failed ?: FansPageRequest(nextPage, false)

internal class FansListViewModel(private val mid: Long) : ViewModel() {
    private val requests = AccountScopedRequests(viewModelScope)
    private fun initial() = FansListState(identity = requests.identity, canManage = mid > 0L && mid == requests.identity.mid)
    private val mutableState = MutableStateFlow(initial())
    val state = mutableState.asStateFlow()
    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()
    private var page = 1
    private var failedRequest: FansPageRequest? = null
    private var removalVersion = 0L
    private var furthestLoadedPage = 0
    private val removed = mutableSetOf<Long>()

    init {
        requests.observe {
            page = 1
            failedRequest = null
            removalVersion = 0L
            furthestLoadedPage = 0
            removed.clear()
            mutableState.value = initial()
            load()
        }
        viewModelScope.launch {
            FanManagementRepository.removed.collect { event ->
                if (event.identity == requests.identity && requests.isCurrent(event.identity) && mid == event.identity.mid && removed.add(event.mid)) {
                    removalVersion++
                    // 移除会改变服务端页偏移；保留已加载条目，后续从首段补齐可能前移的用户。
                    page = 1
                    mutableState.update { state -> state.copy(
                        users = state.users.filterNot { it.mid == event.mid }.toImmutableList(),
                        total = state.total?.let { (it - 1).coerceAtLeast(0) },
                    ) }
                }
            }
        }
    }

    fun load(refresh: Boolean = false) {
        val current = mutableState.value
        val identity = current.identity
        if (!requests.isCurrent(identity) || current.loading || (!refresh && !current.hasMore && current.error == null)) return
        if (mid <= 0L) {
            mutableState.update { it.copy(error = "无效的用户 UID", hasMore = false) }
            return
        }
        val request = resolveFansPageRequest(page, refresh, failedRequest)
        val requestPage = request.page
        if (request.refresh) {
            removed.clear()
            furthestLoadedPage = 0
        }
        val version = removalVersion
        val cookie = synchronized(TokenManager) {
            if (identity != TokenManager.accountIdentity.value || identity.mid != TokenManager.midCache) return
            if ((identity.mid ?: 0L) > 0L) "SESSDATA=${TokenManager.sessDataCache.orEmpty()}; DedeUserID=${identity.mid}" else ""
        }
        failedRequest = null
        mutableState.update { it.copy(loading = true, error = null) }
        requests.launch {
            try {
                val response = NetworkModule.api.getFollowers(vmid = mid, pn = requestPage, cookie = cookie)
                check(response.code == 0) { response.message.ifBlank { "加载粉丝失败（${response.code}）" } }
                val data = requireNotNull(response.data) { "粉丝列表数据为空" }
                val incoming = requireNotNull(data.list) { "粉丝列表数据不完整" }
                if (!requests.isCurrent(identity)) return@launch
                mutableState.update { state ->
                    val users = mergeFansPage(if (request.refresh) emptyList() else state.users, incoming, removed)
                    val total = if (removed.isEmpty()) data.total else minOf(data.total, state.total ?: data.total)
                    state.copy(
                        users = users, total = total.coerceAtLeast(users.size), loading = false,
                        hasMore = shouldLoadMoreFans(requestPage, furthestLoadedPage, incoming.size, state.users.size, users.size, total),
                    )
                }
                furthestLoadedPage = maxOf(furthestLoadedPage, requestPage)
                page = if (version != removalVersion) 1 else requestPage + 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requests.isCurrent(identity)) {
                    failedRequest = request
                    mutableState.update { it.copy(loading = false, error = failure.message ?: "加载粉丝失败，请重试") }
                }
            }
        }
    }

    fun removeFan(targetMid: Long) {
        val current = mutableState.value
        if (!requests.isCurrent(current.identity) || !current.canManage || current.removingMid != null || current.users.none { it.mid == targetMid }) return
        mutableState.update { it.copy(removingMid = targetMid) }
        requests.launch {
            val result = FanManagementRepository.remove(current.identity, targetMid)
            if (!requests.isCurrent(current.identity)) return@launch
            mutableState.update { it.copy(removingMid = null) }
            _events.trySend(result.exceptionOrNull()?.message ?: "已移除粉丝")
        }
    }
}
