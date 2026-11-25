package com.android.purebilibili.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.LiveMatchContest
import com.android.purebilibili.data.model.response.LiveMatchSeason
import com.android.purebilibili.data.model.response.LiveMatchGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@androidx.compose.runtime.Immutable
internal data class LiveMatchUiState(
    val date: LocalDate = LocalDate.now(),
    val gameId: Int = 0,
    val games: List<LiveMatchGame> = emptyList(),
    val gamesError: String? = null,
    val seasons: List<LiveMatchSeason> = emptyList(),
    val seasonsLoading: Boolean = false,
    val seasonsError: String? = null,
    val contests: List<LiveMatchContest> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

internal fun liveMatchPlaybackBvid(url: String): String? =
    Regex("(?:^|/)BV[0-9A-Za-z]{10}(?=$|[/?#])").find(url)?.value?.substringAfterLast('/')

internal fun liveMatchImageUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("/") -> "https://i0.hdslb.com$url"
    url.startsWith("http://") -> "https://" + url.removePrefix("http://")
    else -> url
}

internal class LiveMatchViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(LiveMatchUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var gamesRequest: Job? = null
    private var seasonsRequest: Job? = null
    private var started = false
    private var page = 0

    fun start() {
        if (started) return
        started = true
        refresh()
    }

    fun selectGame(id: Int) {
        if (id == state.value.gameId) return
        mutableState.update { it.copy(gameId = id) }
        loadSeasons()
        load(reset = true)
    }

    fun selectDate(date: LocalDate) {
        if (date == state.value.date) return
        mutableState.update { it.copy(date = date) }
        load(reset = true)
    }

    fun refresh() {
        loadGames()
        loadSeasons()
        load(reset = true)
    }

    fun loadMore() {
        if (state.value.hasMore && !state.value.loading && !state.value.loadingMore) load(reset = false)
    }

    fun loadSeasons() {
        seasonsRequest?.cancel()
        val gameId = state.value.gameId
        mutableState.update { it.copy(seasons = emptyList(), seasonsLoading = gameId > 0, seasonsError = null) }
        if (gameId <= 0) return
        seasonsRequest = viewModelScope.launch {
            try {
                val response = NetworkModule.api.getLiveMatchSeasons(gameId)
                ensureActive()
                check(response.code == 0) { response.message.ifBlank { "赛季列表加载失败" } }
                val seasons = checkNotNull(response.data) { "赛季列表为空" }
                mutableState.update { it.copy(
                    seasons = seasons.filter { season -> season.id > 0 }.distinctBy { season -> season.id },
                    seasonsLoading = false,
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(seasonsLoading = false, seasonsError = error.message ?: "赛季列表加载失败") }
            }
        }
    }

    private fun loadGames() {
        gamesRequest?.cancel()
        gamesRequest = viewModelScope.launch {
            try {
                val response = NetworkModule.api.getLiveMatchGames()
                ensureActive()
                check(response.code == 0) { response.message.ifBlank { "分类加载失败" } }
                val games = checkNotNull(response.data) { "分类数据为空" }.distinctBy { it.id }
                mutableState.update { it.copy(games = games, gamesError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(gamesError = error.message ?: "分类加载失败") }
            }
        }
    }

    private fun load(reset: Boolean) {
        request?.cancel()
        val selected = state.value
        val nextPage = if (reset) 1 else page + 1
        mutableState.update {
            it.copy(
                contests = if (reset) emptyList() else it.contests,
                loading = reset,
                loadingMore = !reset,
                error = null,
                hasMore = if (reset) false else it.hasMore,
            )
        }
        request = viewModelScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val response = NetworkModule.api.getLiveMatchSchedule(
                    gameId = selected.gameId,
                    startTime = selected.date.atStartOfDay(zone).toEpochSecond(),
                    endTime = selected.date.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1,
                    page = nextPage,
                )
                ensureActive()
                check(response.code == 0) { response.message.ifBlank { "赛程加载失败" } }
                val data = checkNotNull(response.data) { "赛程数据为空" }
                page = nextPage
                mutableState.update {
                    it.copy(
                        contests = (if (reset) data.contests else it.contests + data.contests).distinctBy { match -> match.id },
                        loading = false,
                        loadingMore = false,
                        hasMore = data.page.total?.let { total -> nextPage.toLong() * data.page.size < total }
                            ?: (data.contests.size >= 30),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, loadingMore = false, error = error.message ?: "赛程加载失败") }
            }
        }
    }
}
