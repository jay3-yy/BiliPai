package com.android.purebilibili.feature.audio.bgm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.ViewGrpcRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@androidx.compose.runtime.Immutable
internal data class BgmDetailUiState(
    val detail: BgmDetailData? = null,
    val videos: List<BgmRecommendVideo> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val updatingWish: Boolean = false,
    val revision: Int = 0,
)

internal class BgmDetailViewModel : ViewModel() {
    private val _state = MutableStateFlow(BgmDetailUiState())
    val state = _state.asStateFlow()
    private val messagesChannel = Channel<String>(Channel.BUFFERED)
    val messages = messagesChannel.receiveAsFlow()
    private var request: Triple<String, Long, Long>? = null
    private var videosOnly = false
    private var loadJob: Job? = null

    fun initialize(musicId: String, aid: Long, cid: Long, showVideos: Boolean) {
        val next = Triple(musicId, aid, cid)
        if (request == next && videosOnly == showVideos) return
        request = next
        videosOnly = showVideos
        refresh()
    }

    fun refresh() {
        if (_state.value.updatingWish) return
        val (id, aid, cid) = request ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val detail = ViewGrpcRepository.getBgmDetail(id, aid, cid)
            val videos = if (videosOnly) ViewGrpcRepository.getAllBgmRecommendVideos(id) else Result.success(emptyList())
            _state.update { it.copy(detail = detail.getOrNull(), videos = videos.getOrDefault(emptyList()),
                loading = false, revision = it.revision + 1,
                error = detail.exceptionOrNull()?.message ?: videos.exceptionOrNull()?.message) }
        }
    }

    fun toggleWish() {
        val id = request?.first ?: return
        val detail = _state.value.detail ?: return
        if (_state.value.updatingWish || _state.value.loading) return
        val csrf = TokenManager.csrfCache
        if (TokenManager.sessDataCache.isNullOrBlank() || csrf.isNullOrBlank()) {
            messagesChannel.trySend("请先登录")
            return
        }
        _state.update { it.copy(updatingWish = true) }
        viewModelScope.launch {
            try {
                val result = NetworkModule.api.updateBgmWish(id, if (detail.wishListen) 2 else 1, csrf)
                check(result.code == 0) { result.message.ifBlank { "点赞失败 (${result.code})" } }
                _state.update { it.copy(detail = detail.copy(wishListen = !detail.wishListen,
                    wishCount = (detail.wishCount + if (detail.wishListen) -1 else 1).coerceAtLeast(0))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messagesChannel.send(e.message ?: "点赞失败")
            } finally {
                _state.update { it.copy(updatingWish = false) }
            }
        }
    }
}
