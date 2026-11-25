package com.android.purebilibili.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.AccountCookieStore
import com.android.purebilibili.core.network.AccountCookieSession
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiKeyManager
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@androidx.compose.runtime.Immutable
internal data class LiveMatchGradeState(
    val contestId: Long = 0,
    val game: Int = 1,
    val account: AccountSessionIdentity = AccountSessionIdentity(),
    val data: LiveMatchGrades? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val availableGames: Int = 1,
    val submitting: Boolean = false,
    val feedback: String? = null,
    val feedbackPlayerId: Long = 0,
    val requiresLogin: Boolean = false,
    val submissionVersion: Int = 0,
)

@androidx.compose.runtime.Immutable
internal data class LiveMatchGradeComments(
    val gradeId: String = "",
    val mode: Int = 3,
    val items: List<ReplyItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val offset: String = "",
    val end: Boolean = false,
)

internal class LiveMatchGradeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(LiveMatchGradeState())
    val state = mutableState.asStateFlow()
    private val mutableComments = MutableStateFlow(LiveMatchGradeComments())
    val comments = mutableComments.asStateFlow()
    private var request: Job? = null
    private var mutation: Job? = null
    private var commentRequest: Job? = null

    private fun isCurrent(context: LiveMatchGradeState): Boolean =
        state.value.contestId == context.contestId && state.value.game == context.game &&
            state.value.account == context.account && TokenManager.accountIdentity.value == context.account

    private fun cookie(session: AccountCookieSession): String = AccountCookieStore.read(NetworkModule.appContext, session).entries.joinToString("; ") { "${it.key}=${it.value}" }

    fun load(id: Long, game: Int) {
        stopRequests()
        val knownGames = if (state.value.contestId == id) state.value.availableGames else 1
        val context = LiveMatchGradeState(contestId = id, game = game, availableGames = knownGames,
            account = TokenManager.accountIdentity.value)
        mutableState.value = context
        mutableComments.value = LiveMatchGradeComments()
        request = viewModelScope.launch {
            try {
                val session = AccountCookieSession.main()
                if (!isCurrent(context)) return@launch
                val response = NetworkModule.api.getLiveMatchGrades(id, game, cookie(session))
                ensureActive()
                if (!isCurrent(context)) return@launch
                check(response.code == 0) { response.message.ifBlank { "选手评分加载失败" } }
                val data = checkNotNull(response.data) { "选手评分数据为空" }
                val resolvedGames = maxOf(knownGames, data.currentGame ?: 1).coerceIn(1, 99)
                mutableState.update { current ->
                    if (current.contestId == context.contestId && current.game == context.game &&
                        current.account == context.account && TokenManager.accountIdentity.value == context.account
                    ) {
                        current.copy(data = data, loading = false, availableGames = maxOf(current.availableGames, resolvedGames))
                    } else current
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                val errorMsg = error.message ?: "选手评分加载失败"
                mutableState.update { current ->
                    if (current.contestId == context.contestId && current.game == context.game &&
                        current.account == context.account && TokenManager.accountIdentity.value == context.account
                    ) {
                        current.copy(loading = false, error = errorMsg)
                    } else current
                }
            }
        }
    }

    fun showComments(player: LiveMatchPlayerGrade, mode: Int = 3, more: Boolean = false) {
        val context = state.value
        val aid = context.data?.aid ?: return
        if (aid <= 0 || player.gradeId.toLongOrNull()?.let { it > 0 } != true || !isCurrent(context)) return
        val previous = comments.value
        if (more && (previous.gradeId != player.gradeId || previous.loading || previous.end)) return
        commentRequest?.cancel()
        var requestOffset = ""
        var requestMode = mode
        mutableComments.update { current ->
            val base = if (more && current.gradeId == player.gradeId) current
                else LiveMatchGradeComments(gradeId = player.gradeId, mode = mode)
            requestOffset = base.offset
            requestMode = base.mode
            base.copy(loading = true, error = null)
        }
        commentRequest = viewModelScope.launch {
            try {
                val session = AccountCookieSession.main()
                if (!isCurrent(context)) return@launch
                val params = liveMatchGradeCommentQuery(aid, player.gradeId, requestOffset, requestMode)
                val (img, sub) = WbiKeyManager.getWbiKeys().getOrThrow()
                ensureActive()
                if (!isCurrent(context)) return@launch
                val response = NetworkModule.api.getReplyList(WbiUtils.sign(params, img, sub), cookie(session))
                ensureActive()
                if (!isCurrent(context)) return@launch
                check(response.code == 0) { response.message.ifBlank { "评分评论加载失败（${response.code}）" } }
                val data = checkNotNull(response.data) { "评分评论数据为空" }
                val incoming = data.replies.orEmpty()
                val next = data.cursor.paginationReply?.nextOffset.orEmpty()
                val reachedEnd = data.cursor.isEnd || next.isBlank() || next == requestOffset
                mutableComments.update { current ->
                    if (current.gradeId != player.gradeId || current.mode != requestMode) current
                    else current.copy(
                        items = (current.items + incoming).distinctBy { it.rpid },
                        loading = false,
                        offset = next,
                        end = reachedEnd
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (isCurrent(context)) {
                    val errorMsg = error.message ?: "评分评论加载失败"
                    mutableComments.update { current ->
                        if (current.gradeId != player.gradeId || current.mode != requestMode) current
                        else current.copy(loading = false, error = errorMsg)
                    }
                }
            }
        }
    }

    fun clearFeedback() { mutableState.update { it.copy(feedback = null, feedbackPlayerId = 0) } }

    fun clearComments() { commentRequest?.cancel(); mutableComments.value = LiveMatchGradeComments() }

    fun submit(player: LiveMatchPlayerGrade, score: Int, message: String = "") {
        val context = state.value
        if (context.submitting || context.loading || !isCurrent(context)) return
        val players = context.data?.let { it.teams.ifEmpty { listOfNotNull(it.home, it.away) } }?.flatMap { it.players }.orEmpty()
        if (players.none { it.playerId == player.playerId && it.gradeId == player.gradeId }) return
        val session = AccountCookieSession.main()
        val csrf = session.seed["bili_jct"].orEmpty()
        if (!isCurrent(context)) return
        if (session.sessData.isBlank() || csrf.isBlank() || context.account.mid == null) {
            mutableState.update { current ->
                if (current.contestId == context.contestId && current.game == context.game && current.account == context.account) {
                    current.copy(feedback = "请先登录后参与评分和评论", requiresLogin = true, feedbackPlayerId = player.playerId)
                } else current
            }
            return
        }
        if (score !in liveMatchRatingScores || message.length > 1000) return
        mutableState.update { current ->
            if (current.contestId == context.contestId && current.game == context.game && current.account == context.account) {
                current.copy(submitting = true, feedback = null, feedbackPlayerId = player.playerId)
            } else current
        }
        mutation = viewModelScope.launch {
            var accepted = false
            try {
                fun ensureSuccess(code: Int, message: String) {
                    if (code == -101 && isCurrent(context)) {
                        mutableState.update { it.copy(requiresLogin = true) }
                    }
                    check(code == 0) {
                        when (code) {
                            -101 -> "登录已过期，请重新登录"
                            84088 -> "比赛尚未开始，暂不能评分"
                            else -> message.ifBlank { "提交失败（$code）" }
                        }
                    }
                }
                if (message.isBlank()) {
                    val response = NetworkModule.api.submitLiveMatchGrade(
                        liveMatchGradeFields(context.contestId, context.game, player.playerId, score, csrf), cookie(session))
                    ensureActive()
                    ensureSuccess(response.code, response.message)
                } else {
                    val response = NetworkModule.api.addLiveMatchGradeComment(
                        liveMatchGradeCommentFields(context.data?.aid ?: 0, player.gradeId, score, message, csrf), cookie(session))
                    ensureActive()
                    ensureSuccess(response.code, response.message)
                }
                accepted = true
                if (!isCurrent(context)) return@launch
                // 已确认的个人评分立即回显，均分与人数仍以刷新结果为准。
                val fallbackOld = checkNotNull(context.data)
                mutableState.update { current ->
                    val baseData = current.data ?: fallbackOld
                    current.copy(
                        data = baseData.withConfirmedRating(player.playerId, player.gradeId, score),
                        feedback = if (message.isBlank()) "评分已提交" else "评分评论已发布",
                        feedbackPlayerId = player.playerId,
                        submissionVersion = current.submissionVersion + if (message.isNotBlank()) 1 else 0
                    )
                }
                if (message.isNotBlank() && comments.value.gradeId == player.gradeId) showComments(player, comments.value.mode)
                val refreshed = NetworkModule.api.getLiveMatchGrades(context.contestId, context.game, cookie(session))
                ensureActive()
                if (isCurrent(context)) {
                    val refreshedData = refreshed.data
                    if (refreshed.code == 0 && refreshedData != null) {
                        val confirmed = refreshedData.withConfirmedRating(player.playerId, player.gradeId, score)
                        mutableState.update { it.copy(data = confirmed) }
                    } else {
                        mutableState.update { it.copy(feedback = "提交成功，最新统计暂未刷新", feedbackPlayerId = player.playerId) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (isCurrent(context)) {
                    val feedbackText = if (accepted) "提交成功，最新统计暂未刷新"
                        else error.message ?: "提交结果未确认，请刷新评分后再决定是否重试"
                    mutableState.update { it.copy(feedbackPlayerId = player.playerId, feedback = feedbackText) }
                }
            } finally {
                if (isCurrent(context)) {
                    mutableState.update { it.copy(submitting = false) }
                }
            }
        }
    }

    private fun stopRequests() { request?.cancel(); mutation?.cancel(); commentRequest?.cancel() }
    fun stop() { stopRequests(); mutableState.value = LiveMatchGradeState(); mutableComments.value = LiveMatchGradeComments() }
}
