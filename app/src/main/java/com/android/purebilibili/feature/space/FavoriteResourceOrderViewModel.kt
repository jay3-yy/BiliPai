package com.android.purebilibili.feature.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FavoriteData
import com.android.purebilibili.data.repository.FavoriteRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class FavoriteResourceOrderState(
    val identity: AccountSessionIdentity = TokenManager.accountIdentity.value,
    val folderId: Long = 0L,
    val ownerMid: Long = 0L,
    val original: List<FavoriteData>? = null,
    val draft: List<FavoriteData> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val total: Int? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val dirty: Boolean get() = original != null && original.map { it.orderKey } != draft.map { it.orderKey }
}

internal class FavoriteResourceOrderViewModel : ViewModel() {
    private val requests = AccountScopedRequests(viewModelScope)
    private val mutableState = MutableStateFlow(FavoriteResourceOrderState())
    val state = mutableState.asStateFlow()

    init { requests.observe { mutableState.value = FavoriteResourceOrderState(identity = requests.identity) } }

    private var loadingJob: Job? = null

    fun load(folderId: Long, ownerMid: Long) {
        val current = mutableState.value
        if (!requests.isCurrent() || current.saving || requests.identity.mid != ownerMid) return
        loadingJob?.cancel()
        mutableState.value = FavoriteResourceOrderState(identity = requests.identity, folderId = folderId, ownerMid = ownerMid)
        loadNext()
    }

    fun loadNext() {
        val current = mutableState.value
        if (!requests.isCurrent(current.identity) || current.loading || current.saving || current.folderId <= 0L ||
            current.identity.mid != current.ownerMid || (current.page > 0 && !current.hasMore)) return
        mutableState.update { it.copy(loading = true, error = null) }
        loadingJob = requests.launch {
            try {
                val data = FavoriteRepository.getFavoriteList(current.folderId, current.page + 1, order = "mtime").getOrThrow()
                currentCoroutineContext().ensureActive()
                if (!requests.isCurrent(current.identity)) return@launch
                val info = requireNotNull(data.info) { "无法确认收藏夹归属，请重新读取" }
                val owner = info.upper?.mid?.takeIf { it > 0L } ?: info.mid
                check(owner == current.ownerMid) { "只能排序自己的收藏夹" }
                check(current.total == null || current.total == info.media_count) { "收藏夹内容已变化，请重新读取" }
                val incoming = data.medias.orEmpty()
                check(incoming.isNotEmpty() || info.media_count == 0) { "分页内容为空，请重试" }
                val original = current.original.orEmpty() + incoming
                check(original.map { it.orderKey }.distinct().size == original.size) { "收藏夹内容已变化，请重新读取" }
                check(incoming.all { it.id > 0 && it.type > 0 }) { "收藏内容身份无效，请重新读取" }
                mutableState.update { it.copy(original = original, draft = current.draft + incoming,
                    page = current.page + 1, hasMore = data.has_more, total = info.media_count, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requests.isCurrent(current.identity)) mutableState.update { it.copy(loading = false, error = e.message ?: "读取失败，请重试") }
            }
        }
    }

    fun move(from: String, to: String) {
        mutableState.update {
            if (!requests.isCurrent(it.identity) || it.loading || it.saving) it
            else it.copy(draft = moveFavoriteResource(it.draft, from, to), error = null)
        }
    }

    fun save() {
        val current = mutableState.value
        if (!requests.isCurrent(current.identity) || current.loading || current.saving || !current.dirty) return
        mutableState.update { it.copy(saving = true, error = null) }
        requests.launch {
            try {
                val payload = favoriteResourceOrderPayload(current.original.orEmpty(), current.draft)
                FavoriteRepository.sortResources(current.folderId, current.ownerMid, payload, current.identity).getOrThrow()
                if (requests.isCurrent(current.identity)) mutableState.update {
                    it.copy(original = current.draft, saving = false, saved = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requests.isCurrent(current.identity)) mutableState.update { it.copy(saving = false, error = e.message ?: "保存失败，调整已保留") }
            }
        }
    }

    fun consumeSaved() { mutableState.update { it.copy(saved = false) } }
}
