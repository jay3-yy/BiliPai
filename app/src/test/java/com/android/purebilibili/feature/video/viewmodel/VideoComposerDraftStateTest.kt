package com.android.purebilibili.feature.video.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.repository.CommentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VideoComposerDraftStateTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `switching reply targets preserves each draft and rejects the previous callback`() {
        var state = CommentComposerDraftState().open()
        val sessionA = state.sessionId
        val draftA = CommentComposerDraft(text = "reply A")
        state = state.update(sessionA, 11L, draftA)

        state = state.open()
        val sessionB = state.sessionId
        val draftB = CommentComposerDraft(text = "reply B", syncToDynamic = true)
        state = state.update(sessionB, 22L, draftB)
        val beforeLateCallback = state
        state = state.update(sessionA, 11L, CommentComposerDraft(text = "late A"))

        assertSame(beforeLateCallback, state)
        assertSame(draftA, state.drafts[11L])
        assertSame(draftB, state.drafts[22L])

        state = state.open()
        assertSame(draftA, state.drafts[11L])
        assertTrue(state.sessionId > sessionB)
    }

    @Test
    fun `root draft survives replying and cancelling then reopening`() {
        val image = mockk<Uri>()
        val rootDraft = CommentComposerDraft("root", listOf(image), true)
        var state = CommentComposerDraftState().open()
        state = state.update(state.sessionId, null, rootDraft)
        state = state.open()
        val replyDraft = CommentComposerDraft(text = "reply")
        state = state.update(state.sessionId, 11L, replyDraft)

        val cancelledSession = state.sessionId
        state = state.open()

        assertTrue(state.sessionId > cancelledSession)
        assertSame(rootDraft, state.drafts[commentComposerDraftKey(null)])
        assertSame(replyDraft, state.drafts[11L])
        assertEquals(listOf(image), state.drafts[0L]?.imageUris)
        assertEquals(true, state.drafts[0L]?.syncToDynamic)
    }

    @Test
    fun `success removes only the captured reply and preserves a newer composer session`() {
        var state = CommentComposerDraftState().open()
        val sentSession = state.sessionId
        val sentDraft = CommentComposerDraft(text = "sent")
        state = state.update(sentSession, 11L, sentDraft)
        state = state.open()
        val activeSession = state.sessionId
        val activeDraft = CommentComposerDraft(text = "still editing")
        state = state.update(activeSession, 22L, activeDraft)

        state = state.complete(sentSession, 11L, sentDraft)

        assertFalse(state.drafts.containsKey(11L))
        assertSame(activeDraft, state.drafts[22L])
        assertEquals(activeSession, state.sessionId)
        assertEquals(sentSession, state.completedSessionId)
        val editedDraft = CommentComposerDraft(text = "newer B")
        state = state.update(activeSession, 22L, editedDraft)
        assertSame(editedDraft, state.drafts[22L])
    }

    @Test
    fun `root send success clears its captured bucket and late callbacks cannot resurrect it`() {
        var state = CommentComposerDraftState().open()
        val sentSession = state.sessionId
        val sentDraft = CommentComposerDraft(text = "root send", syncToDynamic = true)
        state = state.update(sentSession, null, sentDraft)
        state = state.complete(sentSession, null, sentDraft)
        val completed = state

        state = state.update(sentSession, null, sentDraft)

        assertSame(completed, state)
        assertTrue(state.drafts.isEmpty())
        assertEquals(sentSession, state.completedSessionId)
        state = state.open()
        val reopenedDraft = CommentComposerDraft(text = "new root")
        state = state.update(state.sessionId, null, reopenedDraft)
        assertSame(reopenedDraft, state.drafts[0L])
    }

    @Test
    fun `newer equal text draft instance survives completion through StateFlow`() {
        val store = MutableStateFlow(CommentComposerDraftState().open())
        val session = store.value.sessionId
        val sentDraft = CommentComposerDraft(text = "same text", syncToDynamic = true)
        store.update { it.update(session, 11L, sentDraft) }
        val editedDraft = sentDraft.copy()
        assertNotSame(sentDraft, editedDraft)
        assertEquals(sentDraft, editedDraft)
        store.update { it.update(session, 11L, editedDraft) }

        store.update { it.complete(session, 11L, sentDraft) }

        assertSame(editedDraft, store.value.drafts[11L])
        val completed = store.value
        store.update { it.update(session, 11L, sentDraft) }
        assertSame(completed, store.value)
    }

    @Test
    fun `old completion cannot erase equal text edited in a reopened session`() {
        var state = CommentComposerDraftState().open()
        val sentSession = state.sessionId
        val sentDraft = CommentComposerDraft(text = "same text")
        state = state.update(sentSession, 11L, sentDraft)
        state = state.open()
        val reopenedSession = state.sessionId
        val editedDraft = sentDraft.copy()
        state = state.update(reopenedSession, 11L, editedDraft)
        state = state.complete(sentSession, 11L, sentDraft)

        assertSame(editedDraft, state.drafts[11L])
        assertEquals(reopenedSession, state.sessionId)
        val afterSuccessEdit = CommentComposerDraft(text = "reopened edit")
        state = state.update(reopenedSession, 11L, afterSuccessEdit)
        assertSame(afterSuccessEdit, state.drafts[11L])
    }

    @Test
    fun `reset advances the session and an older completion cannot revive old callbacks`() {
        var state = CommentComposerDraftState().open()
        val oldSession = state.sessionId
        val oldDraft = CommentComposerDraft(text = "old")
        state = state.update(oldSession, 11L, oldDraft)
        state = state.reset()
        val resetSession = state.sessionId
        assertTrue(resetSession > oldSession)
        assertTrue(state.drafts.isEmpty())
        val currentDraft = CommentComposerDraft(text = "current")
        state = state.update(resetSession, 11L, currentDraft)
        state = state.complete(resetSession, 11L, currentDraft)
        state = state.complete(oldSession, 11L, oldDraft)
        val completed = state
        state = state.update(resetSession, 11L, currentDraft)
        state = state.update(oldSession, 11L, oldDraft)

        assertSame(completed, state)
        assertEquals(resetSession, state.completedSessionId)
        assertTrue(state.drafts.isEmpty())
    }

    @Test
    fun `transient blank video keeps drafts while a different video resets with a newer session`() {
        var video = VideoComposerDraftState().forVideo("BV_A")
        var comments = video.comments.open()
        val oldSession = comments.sessionId
        val oldDraft = CommentComposerDraft(text = "video A")
        comments = comments.update(oldSession, null, oldDraft)
        video = video.copy(comments = comments)

        assertSame(video, video.forVideo(""))
        assertSame(video, video.forVideo("BV_A"))
        val nextVideo = video.forVideo("BV_B")

        assertEquals("BV_B", nextVideo.videoId)
        assertTrue(nextVideo.comments.sessionId > oldSession)
        assertTrue(nextVideo.comments.drafts.isEmpty())
        assertSame(nextVideo.comments, nextVideo.comments.update(oldSession, null, oldDraft))
    }

    @Test
    fun `default empty edit removes only its own bucket but sync selection remains a draft`() {
        var state = CommentComposerDraftState().open()
        val session = state.sessionId
        val rootDraft = CommentComposerDraft(text = "root")
        state = state.update(session, null, rootDraft)
        state = state.update(session, 11L, CommentComposerDraft(text = "reply"))
        state = state.update(session, 11L, CommentComposerDraft())

        assertFalse(state.drafts.containsKey(11L))
        assertSame(rootDraft, state.drafts[0L])
        val syncDraft = CommentComposerDraft(syncToDynamic = true)
        state = state.update(session, 11L, syncDraft)
        assertSame(syncDraft, state.drafts[11L])
    }

    @Test
    fun `upload failure preserves the actual video draft body images and sync selection`() = runTest(dispatcher) {
        withVideoViewModel { viewModel ->
            val image = mockk<Uri>()
            val session = viewModel.composerDrafts.value.comments.sessionId
            viewModel.updateCommentDraft("BV_A", session, null, " keep spacing ", listOf(image), true)
            val draft = viewModel.composerDrafts.value.comments.drafts[0L]

            // No playback context has been attached, so the real upload path fails before IO.
            viewModel.sendComment(" keep spacing ", listOf(image), true)
            assertTrue(viewModel.isSendingComment.value)
            runCurrent()

            assertSame(draft, viewModel.composerDrafts.value.comments.drafts[0L])
            assertEquals(" keep spacing ", draft?.text)
            assertEquals(listOf(image), draft?.imageUris)
            assertEquals(true, draft?.syncToDynamic)
            assertEquals(-1L, viewModel.composerDrafts.value.comments.completedSessionId)
            assertTrue(viewModel.showCommentDialog.value)
            assertFalse(viewModel.isSendingComment.value)
            coVerify(exactly = 0) {
                CommentRepository.addComment(any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `send failure keeps the draft and synchronous send flag blocks immediate duplicate clicks`() = runTest(dispatcher) {
        withVideoViewModel { viewModel ->
            coEvery {
                CommentRepository.addComment(any(), any(), any(), any(), any(), any())
            } returns Result.failure(IllegalStateException("send failed"))
            val session = viewModel.composerDrafts.value.comments.sessionId
            viewModel.updateCommentDraft("BV_A", session, null, "retry body", emptyList(), true)
            val draft = viewModel.composerDrafts.value.comments.drafts[0L]

            viewModel.sendComment("retry body", syncToDynamic = true)
            assertTrue(viewModel.isSendingComment.value)
            viewModel.sendComment("duplicate body", syncToDynamic = false)
            runCurrent()

            assertSame(draft, viewModel.composerDrafts.value.comments.drafts[0L])
            assertEquals("retry body", draft?.text)
            assertEquals(true, draft?.syncToDynamic)
            assertEquals(-1L, viewModel.composerDrafts.value.comments.completedSessionId)
            assertTrue(viewModel.showCommentDialog.value)
            assertFalse(viewModel.isSendingComment.value)
            coVerify(exactly = 1) {
                CommentRepository.addComment(101L, "retry body", 0L, 0L, emptyList(), true)
            }
        }
    }

    @Test
    fun `video send success clears the captured raw draft and rejects the dismissal callback`() = runTest(dispatcher) {
        withVideoViewModel { viewModel ->
            coEvery {
                CommentRepository.addComment(any(), any(), any(), any(), any(), any())
            } returns Result.success(null)
            val session = viewModel.composerDrafts.value.comments.sessionId
            viewModel.updateCommentDraft("BV_A", session, null, " raw body ", emptyList(), true)
            viewModel.sendComment(" raw body ", syncToDynamic = true)
            runCurrent()

            assertTrue(viewModel.composerDrafts.value.comments.drafts.isEmpty())
            assertEquals(session, viewModel.composerDrafts.value.comments.completedSessionId)
            assertFalse(viewModel.showCommentDialog.value)
            viewModel.updateCommentDraft("BV_A", session, null, " raw body ", emptyList(), true)
            assertTrue(viewModel.composerDrafts.value.comments.drafts.isEmpty())
            viewModel.showCommentInputDialog()
            assertTrue(viewModel.composerDrafts.value.comments.sessionId > session)
            assertTrue(viewModel.composerDrafts.value.comments.drafts.isEmpty())
        }
    }

    @Test
    fun `video reply A completion leaves the later B composer open with its target and draft`() = runTest(dispatcher) {
        withVideoViewModel { viewModel ->
            val pending = CompletableDeferred<Result<ReplyItem?>>()
            coEvery {
                CommentRepository.addComment(any(), any(), any(), any(), any(), any())
            } coAnswers { pending.await() }
            val replyA = ReplyItem(rpid = 11L)
            viewModel.setReplyingTo(replyA)
            viewModel.showCommentInputDialog()
            val sessionA = viewModel.composerDrafts.value.comments.sessionId
            viewModel.updateCommentDraft("BV_A", sessionA, 11L, "reply A", emptyList(), false)
            viewModel.sendComment("reply A")
            runCurrent()

            viewModel.hideCommentInputDialog()
            val replyB = ReplyItem(rpid = 22L)
            viewModel.setReplyingTo(replyB)
            viewModel.showCommentInputDialog()
            val sessionB = viewModel.composerDrafts.value.comments.sessionId
            viewModel.updateCommentDraft("BV_A", sessionB, 22L, "reply B", emptyList(), true)
            val draftB = viewModel.composerDrafts.value.comments.drafts[22L]
            pending.complete(Result.success(ReplyItem(rpid = 33L)))
            runCurrent()

            assertFalse(viewModel.composerDrafts.value.comments.drafts.containsKey(11L))
            assertSame(draftB, viewModel.composerDrafts.value.comments.drafts[22L])
            assertEquals(sessionB, viewModel.composerDrafts.value.comments.sessionId)
            assertSame(replyB, viewModel.replyingToComment.value)
            assertTrue(viewModel.showCommentDialog.value)
            coVerify(exactly = 1) {
                CommentRepository.addComment(101L, "reply A", 11L, 11L, emptyList(), false)
            }
        }
    }

    @Test
    fun `cancelled video send retains the draft and releases the sending flag`() = runTest(dispatcher) {
        withVideoViewModel { viewModel ->
            val pending = CompletableDeferred<Result<ReplyItem?>>()
            coEvery {
                CommentRepository.addComment(any(), any(), any(), any(), any(), any())
            } coAnswers { pending.await() }
            val session = viewModel.composerDrafts.value.comments.sessionId
            viewModel.updateCommentDraft("BV_A", session, null, "not sent", emptyList(), true)
            val draft = viewModel.composerDrafts.value.comments.drafts[0L]
            viewModel.sendComment("not sent", syncToDynamic = true)
            runCurrent()
            pending.cancel()
            runCurrent()

            assertSame(draft, viewModel.composerDrafts.value.comments.drafts[0L])
            assertEquals(-1L, viewModel.composerDrafts.value.comments.completedSessionId)
            assertTrue(viewModel.showCommentDialog.value)
            assertFalse(viewModel.isSendingComment.value)
        }
    }

    private suspend fun withVideoViewModel(block: suspend (VideoPlaybackViewModel) -> Unit) {
        mockkObject(SettingsManager, CommentRepository)
        val application = mockk<Application>(relaxed = true)
        every { application.applicationContext } returns application
        every { SettingsManager.getPlaybackCdnPreference(application) } returns flowOf("base_url")
        coEvery { CommentRepository.getEmotePackages() } returns Result.success(emptyList())
        var viewModel: VideoPlaybackViewModel? = null
        try {
            val owner = VideoPlaybackViewModel(application)
            viewModel = owner
            val stateField = VideoPlaybackViewModel::class.java.getDeclaredField("_uiState")
            stateField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val state = stateField.get(owner) as MutableStateFlow<VideoPlaybackUiState>
            state.value = VideoPlaybackUiState.Success(
                info = ViewInfo(bvid = "BV_A", aid = 101L),
                playUrl = ""
            )
            owner.showCommentInputDialog()
            block(owner)
        } finally {
            viewModel?.viewModelScope?.cancel()
            unmockkObject(SettingsManager, CommentRepository)
        }
    }
}
