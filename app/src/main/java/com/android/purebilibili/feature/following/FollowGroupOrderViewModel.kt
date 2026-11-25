package com.android.purebilibili.feature.following

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.RelationTagItem
import com.android.purebilibili.data.repository.FollowGroupOrderRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class FollowGroupOrderState(
    val identity: AccountSessionIdentity = TokenManager.accountIdentity.value,
    val original: ImmutableList<RelationTagItem>? = null,
    val draft: ImmutableList<RelationTagItem> = persistentListOf(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val dirty: Boolean get() = original != null && original.map { it.tagid } != draft.map { it.tagid }
}

internal class FollowGroupOrderViewModel : ViewModel() {
    private val requests = AccountScopedRequests(viewModelScope)
    private val mutableState = MutableStateFlow(FollowGroupOrderState())
    val state = mutableState.asStateFlow()

    init { requests.observe { mutableState.value = FollowGroupOrderState(identity = requests.identity) } }

    fun load() {
        val current = mutableState.value
        if (!requests.isCurrent(current.identity) || current.loading || current.saving) return
        mutableState.update { it.copy(loading = true, error = null, saved = false) }
        requests.launch {
            val result = FollowGroupOrderRepository.load(current.identity)
            if (!requests.isCurrent(current.identity)) return@launch
            result.fold(
                onSuccess = { groups ->
                    val custom = groups.filter { isSortableFollowGroup(it.tagid) }.toImmutableList()
                    mutableState.update { it.copy(original = custom, draft = custom, loading = false) }
                },
                onFailure = { error -> mutableState.update { it.copy(loading = false, error = error.message ?: "加载分组失败") } },
            )
        }
    }

    fun move(fromId: Long, toId: Long) {
        mutableState.update { current ->
            if (!requests.isCurrent(current.identity) || current.loading || current.saving) current
            else current.copy(draft = moveFollowGroup(current.draft, fromId, toId).toImmutableList(), error = null)
        }
    }

    fun save() {
        val current = mutableState.value
        if (!requests.isCurrent(current.identity) || current.loading || current.saving || !current.dirty) return
        mutableState.update { it.copy(saving = true, error = null) }
        requests.launch {
            val result = FollowGroupOrderRepository.save(current.identity, current.original.orEmpty().map { it.tagid }, current.draft.map { it.tagid })
            if (!requests.isCurrent(current.identity)) return@launch
            result.fold(
                onSuccess = { mutableState.update { it.copy(original = current.draft, saving = false, saved = true) } },
                onFailure = { error -> mutableState.update { it.copy(saving = false, error = error.message ?: "保存失败，排序已保留") } },
            )
        }
    }

    fun consumeSaved() { mutableState.update { it.copy(saved = false) } }
}
