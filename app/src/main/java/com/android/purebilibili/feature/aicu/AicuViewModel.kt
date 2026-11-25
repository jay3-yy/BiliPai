package com.android.purebilibili.feature.aicu

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.AicuConsentStore
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.AicuDataSource
import com.android.purebilibili.data.repository.AicuRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException

internal enum class AicuConsentState { CHECKING, REQUIRED, ACCEPTED, ERROR }
internal enum class AicuLoadState { IDLE, QUEUING, LOADING, CONTENT, EMPTY, ERROR }

@androidx.compose.runtime.Immutable
internal data class AicuUiState(
    val uid: String = "",
    val category: AicuCategory = AicuCategory.COMMENT,
    val consent: AicuConsentState = AicuConsentState.CHECKING,
    val consentSeconds: Int = 5,
    val savingConsent: Boolean = false,
    val consentError: String? = null,
    val filters: Map<AicuCategory, AicuFilter> = emptyMap(),
    val pages: Map<AicuCategory, AicuPage> = emptyMap(),
    val loadState: AicuLoadState = AicuLoadState.IDLE,
    val queueAhead: Int? = null,
    val error: String? = null,
    val retrySeconds: Int = 0,
    val trending: List<AicuTrendingEntry> = emptyList(),
    val trendingLoading: Boolean = false,
    val trendingError: String? = null,
) {
    val filter: AicuFilter get() = filters[category] ?: AicuFilter()
    val page: AicuPage? get() = pages[category]
    val busy: Boolean get() = loadState == AicuLoadState.QUEUING || loadState == AicuLoadState.LOADING
}

internal class AicuViewModel(
    private val source: AicuDataSource,
    private val consentStore: AicuConsentStore,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
    private val trendingLoader: (suspend () -> List<AicuTrendingEntry>)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AicuUiState())
    val state = mutableState.asStateFlow()
    private val consentTimer = AicuConsentTimer(nowMs)
    private var initialized = false
    private var initJob: Job? = null
    private var timerJob: Job? = null
    private var saveJob: Job? = null
    private var queryJob: Job? = null
    private var cooldownJob: Job? = null
    private var generation = 0L
    private var retryAtMs = 0L
    private var lastQuery: AicuQuery? = null
    private var submittedUid: Long? = null
    private var autoQueryUid: Long? = null
    private var autoQueryPending = false
    private var foreground = true
    private var disclaimerVisible = false
    private var exited = false

    fun loadTrending() {
        if (exited || state.value.consent != AicuConsentState.ACCEPTED ||
            state.value.trendingLoading || trendingLoader == null
        ) return
        mutableState.update { it.copy(trendingLoading = true, trendingError = null) }
        viewModelScope.launch {
            try {
                val values = trendingLoader.invoke()
                mutableState.update { it.copy(trending = values, trendingLoading = false) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutableState.update { it.copy(trendingLoading = false, trendingError = error.message ?: "热搜加载失败") }
            }
        }
    }

    fun initialize(uid: Long?, category: AicuCategory) {
        if (initialized || initJob?.isActive == true || exited) return
        autoQueryUid = uid?.takeIf { it > 0 }
        autoQueryPending = autoQueryUid != null
        mutableState.update { it.copy(uid = autoQueryUid?.toString().orEmpty(), category = category, consent = AicuConsentState.CHECKING) }
        initJob = viewModelScope.launch {
            try {
                val accepted = consentStore.acceptedVersion() >= AICU_DISCLAIMER_VERSION
                initialized = true
                mutableState.update { it.copy(consent = if (accepted) AicuConsentState.ACCEPTED else AicuConsentState.REQUIRED, consentError = null) }
                if (accepted && autoQueryPending && !exited) submit()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableState.update { it.copy(consent = AicuConsentState.ERROR, consentError = "无法读取确认状态，请重试。") }
            }
        }
    }

    fun setForeground(visible: Boolean) {
        foreground = visible
        if (!visible) {
            setDisclaimerVisible(false)
            cancelQuery()
        } else if (autoQueryPending && state.value.consent == AicuConsentState.ACCEPTED) {
            submit()
        }
    }

    fun setDisclaimerVisible(visible: Boolean) {
        disclaimerVisible = visible && foreground && state.value.consent == AicuConsentState.REQUIRED && !exited
        consentTimer.setVisible(disclaimerVisible)
        timerJob?.cancel()
        mutableState.update { it.copy(consentSeconds = consentTimer.remainingSeconds()) }
        if (disclaimerVisible) timerJob = viewModelScope.launch {
            while (consentTimer.remainingMs() > 0) {
                delay(100)
                mutableState.update { it.copy(consentSeconds = consentTimer.remainingSeconds()) }
            }
        }
    }

    fun acceptDisclaimer() {
        if (exited || !disclaimerVisible || state.value.consent != AicuConsentState.REQUIRED ||
            consentTimer.remainingMs() > 0 || saveJob?.isActive == true) return
        mutableState.update { it.copy(savingConsent = true, consentError = null) }
        saveJob = viewModelScope.launch {
            try {
                consentStore.accept(AICU_DISCLAIMER_VERSION)
                if (!exited) {
                    mutableState.update { it.copy(consent = AicuConsentState.ACCEPTED, savingConsent = false) }
                    setDisclaimerVisible(false)
                    if (autoQueryPending) submit()
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableState.update { it.copy(savingConsent = false, consentError = "确认状态保存失败，请重试。") }
            }
        }
    }

    fun editUid(value: String) {
        if (state.value.uid == value) return
        cancelQuery()
        submittedUid = null
        autoQueryPending = false
        lastQuery = null
        mutableState.update { it.copy(uid = value, pages = emptyMap(), error = null, loadState = AicuLoadState.IDLE) }
    }

    fun editFilter(filter: AicuFilter) {
        mutableState.update { it.copy(filters = it.filters + (it.category to filter)) }
    }

    fun resetFilter() {
        editFilter(AicuFilter())
        submit()
    }

    fun selectCategory(category: AicuCategory) {
        if (category == state.value.category) return
        cancelQuery()
        lastQuery = null
        mutableState.update {
            it.copy(category = category, error = null, loadState = pageLoadState(it.pages[category]))
        }
        if (submittedUid != null && state.value.page == null) submit()
    }

    fun submit() {
        if (!canRequest()) return
        val uid = parseAicuUid(state.value.uid)
        if (uid == null) {
            mutableState.update { it.copy(error = "请输入有效的正整数 UID。") }
            return
        }
        val current = state.value
        try {
            current.filter.timestamps()
        } catch (error: IllegalArgumentException) {
            mutableState.update { it.copy(error = error.message) }
            return
        }
        submittedUid = uid
        autoQueryPending = false
        startQuery(AicuQuery(uid, current.category, filter = current.filter))
    }

    fun changePage(delta: Int) {
        if (!canRequest() || state.value.busy) return
        val page = state.value.page ?: return
        if (delta > 0 && page.isEnd) return
        val target = page.query.page + delta
        if (target > 0) startQuery(page.query.copy(page = target))
    }

    fun retry() {
        if (!canRequest()) return
        lastQuery?.let(::startQuery) ?: submit()
    }

    private fun canRequest() = !exited && foreground && state.value.consent == AicuConsentState.ACCEPTED && nowMs() >= retryAtMs

    private fun startQuery(query: AicuQuery) {
        if (!canRequest()) return
        cancelQuery()
        lastQuery = query
        val requestGeneration = generation
        mutableState.update { current ->
            val previous = current.pages[query.category]
            val sameResults = previous?.query?.let { it.uid == query.uid && it.filter == query.filter } == true
            current.copy(loadState = AicuLoadState.QUEUING, queueAhead = null, error = null,
                pages = if (sameResults) current.pages else current.pages - query.category)
        }
        queryJob = viewModelScope.launch {
            try {
                val result = source.query(query) { position ->
                    // OkHttp delivers queue updates off the main thread; return to the owning scope.
                    viewModelScope.launch {
                        if (generation == requestGeneration && !exited) mutableState.update {
                            it.copy(queueAhead = position, loadState = if (position == null) AicuLoadState.LOADING else AicuLoadState.QUEUING)
                        }
                    }
                }
                if (generation == requestGeneration && !exited) {
                    generation++
                    mutableState.update {
                        it.copy(pages = it.pages + (query.category to result), loadState = pageLoadState(result), queueAhead = null, error = null)
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (generation == requestGeneration && !exited) {
                    generation++
                    val seconds = (error as? AicuRequestException)?.retryAfterSeconds ?: 0
                    if (seconds > 0) startCooldown(seconds)
                    mutableState.update { it.copy(loadState = AicuLoadState.ERROR, queueAhead = null, error = when (error) {
                        is AicuRequestException -> error.message
                        is SocketTimeoutException -> "连接超时，请重新查询。"
                        else -> "查询连接失败，请检查网络后重试。"
                    }) }
                }
            }
        }
    }

    fun cancelQuery() {
        generation++
        queryJob?.cancel()
        queryJob = null
        mutableState.update { it.copy(queueAhead = null, loadState = pageLoadState(it.page)) }
    }

    fun leave() {
        exited = true
        setDisclaimerVisible(false)
        initJob?.cancel()
        saveJob?.cancel()
        cooldownJob?.cancel()
        cancelQuery()
    }

    private fun startCooldown(seconds: Long) {
        retryAtMs = nowMs() + seconds.coerceAtMost(86400) * 1000
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            do {
                val remaining = ((retryAtMs - nowMs()).coerceAtLeast(0) + 999) / 1000
                mutableState.update { it.copy(retrySeconds = remaining.toInt()) }
                if (remaining > 0) delay(250)
            } while (remaining > 0)
        }
    }

    private fun pageLoadState(page: AicuPage?) = when {
        page == null -> AicuLoadState.IDLE
        page.records.isEmpty() -> AicuLoadState.EMPTY
        else -> AicuLoadState.CONTENT
    }
}
