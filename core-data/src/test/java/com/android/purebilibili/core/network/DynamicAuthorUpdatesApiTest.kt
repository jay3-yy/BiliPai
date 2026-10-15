package com.android.purebilibili.core.network

import com.android.purebilibili.data.model.response.DynamicAuthorUpdatesPageResponse
import com.android.purebilibili.data.model.response.DynamicAuthorUpdatesPortalResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DynamicAuthorUpdatesApiTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun laterPageFalseIsIncludedButMissingFlagsAndInvalidAuthorsRemainUnknown() = runBlocking {
        val updates = collectDynamicAuthorUpdates(
            loadPortal = {
                portal("""{"code":0,"data":{"up_list":{"has_more":true,"offset":"first-cursor","items":[{"mid":11,"has_update":true},{"mid":12}]},"live_users":{}}}""")
            },
            loadPage = {
                page("""{"code":0,"data":{"has_more":false,"offset":"","items":[{"mid":21,"has_update":false},{"mid":22,"has_update":null},{"mid":0,"has_update":false},{"mid":-1,"has_update":false},{"has_update":false}]}}""")
            }
        )
        assertEquals(mapOf(11L to true, 21L to false), updates)
    }

    @Test
    fun identicalFlagsCanRepeatWithoutLosingLaterAuthors() = runBlocking {
        val updates = collectDynamicAuthorUpdates(
            loadPortal = {
                portal("""{"code":0,"data":{"up_list":{"has_more":true,"offset":"next","items":[{"mid":11,"has_update":false}]}}}""")
            },
            loadPage = {
                page("""{"code":0,"data":{"has_more":false,"items":[{"mid":11,"has_update":false},{"mid":12,"has_update":true}]}}""")
            }
        )
        assertEquals(mapOf(11L to false, 12L to true), updates)
    }

    @Test
    fun emptyTerminatedRecentListDoesNotInventReadStates() = runBlocking {
        val updates = collectDynamicAuthorUpdates(
            loadPortal = {
                portal("""{"code":0,"data":{"up_list":{"has_more":false,"items":[]}}}""")
            },
            loadPage = { error("A terminated portal must not paginate") }
        )
        assertTrue(updates.isEmpty())
    }

    @Test
    fun missingSuccessCodeOrRequiredPortalContainersCannotProduceASnapshot() = runBlocking {
        listOf(
            """{"data":{"up_list":{"has_more":false,"items":[]}}}""",
            """{"code":-101,"data":{"up_list":{"has_more":false,"items":[]}}}""",
            """{"code":0}""",
            """{"code":0,"data":{}}""",
            """{"code":0,"data":{"up_list":{"has_more":false}}}""",
            """{"code":0,"data":{"up_list":{"items":[{"mid":11,"has_update":false}]}}}"""
        ).forEach { payload ->
            assertNoSnapshot {
                collectDynamicAuthorUpdates(
                    loadPortal = { portal(payload) },
                    loadPage = { error("An invalid portal must not paginate") }
                )
            }
        }
    }

    @Test
    fun secondPageTransportFailureCannotExposeFirstPageFalse() = runBlocking {
        assertNoSnapshot {
            collectDynamicAuthorUpdates(
                loadPortal = { firstPage() },
                loadPage = { throw IOException("Connection lost") }
            )
        }
    }

    @Test
    fun unsuccessfulOrIncompleteSecondPageCannotExposeFirstPageFalse() = runBlocking {
        listOf(
            """{"code":-101}""",
            """{"data":{"has_more":false,"items":[]}}""",
            """{"code":0}""",
            """{"code":0,"data":{"has_more":false}}""",
            """{"code":0,"data":{"items":[{"mid":12,"has_update":false}]}}"""
        ).forEach { payload ->
            assertNoSnapshot {
                collectDynamicAuthorUpdates(
                    loadPortal = { firstPage() },
                    loadPage = { page(payload) }
                )
            }
        }
    }

    @Test
    fun repeatingCursorWithNewAuthorsStillInvalidatesTheEntireSnapshot() = runBlocking {
        var requests = 0
        assertNoSnapshot {
            collectDynamicAuthorUpdates(
                loadPortal = { firstPage() },
                loadPage = {
                    requests++
                    page("""{"code":0,"data":{"has_more":true,"offset":"next","items":[{"mid":12,"has_update":false}]}}""")
                }
            )
        }
        assertEquals(1, requests)
    }

    @Test
    fun cursorCyclesCannotExposeAnOtherwiseValidPrefix() = runBlocking {
        var requests = 0
        assertNoSnapshot {
            collectDynamicAuthorUpdates(
                loadPortal = { firstPage() },
                loadPage = {
                    requests++
                    val offset = if (requests == 1) "other" else "next"
                    page("""{"code":0,"data":{"has_more":true,"offset":"$offset","items":[{"mid":${11 + requests},"has_update":false}]}}""")
                }
            )
        }
        assertEquals(2, requests)
    }

    @Test
    fun unterminatedEmptyPageCannotExposeEarlierReadFlags() = runBlocking {
        assertNoSnapshot {
            collectDynamicAuthorUpdates(
                loadPortal = { firstPage() },
                loadPage = {
                    page("""{"code":0,"data":{"has_more":true,"offset":"other","items":[]}}""")
                }
            )
        }
    }

    @Test
    fun advancingCursorWithoutAnyNewAuthorIsNotPaginationProgress() = runBlocking {
        assertNoSnapshot {
            collectDynamicAuthorUpdates(
                loadPortal = { firstPage() },
                loadPage = {
                    page("""{"code":0,"data":{"has_more":true,"offset":"other","items":[{"mid":11,"has_update":false},{"mid":0,"has_update":false}]}}""")
                }
            )
        }
    }

    @Test
    fun nonterminalPageNeedsItsOwnNonblankCursor() = runBlocking {
        listOf("", " ").forEach { offset ->
            assertNoSnapshot {
                collectDynamicAuthorUpdates(
                    loadPortal = { firstPage() },
                    loadPage = {
                        page("""{"code":0,"data":{"has_more":true,"offset":"$offset","items":[{"mid":12,"has_update":false}]}}""")
                    }
                )
            }
        }
    }

    @Test
    fun conflictingAuthorFlagsOnLaterPagesInvalidateTheWholeSnapshot() = runBlocking {
        assertNoSnapshot {
            collectDynamicAuthorUpdates(
                loadPortal = { firstPage() },
                loadPage = {
                    page("""{"code":0,"data":{"has_more":false,"items":[{"mid":11,"has_update":true}]}}""")
                }
            )
        }
    }

    @Test
    fun completePaginationDoesNotStopAtAnArbitraryPageLimit() = runBlocking {
        var requests = 0
        val updates = collectDynamicAuthorUpdates(
            loadPortal = { firstPage() },
            loadPage = {
                requests++
                page("""{"code":0,"data":{"has_more":${requests < 150},"offset":"page-$requests","items":[{"mid":${11 + requests},"has_update":false}]}}""")
            }
        )
        assertEquals(false, updates[161L])
    }

    private fun firstPage(): DynamicAuthorUpdatesPortalResponse = portal(
        """{"code":0,"data":{"up_list":{"has_more":true,"offset":"next","items":[{"mid":11,"has_update":false}]}}}"""
    )

    private fun portal(payload: String): DynamicAuthorUpdatesPortalResponse = json.decodeFromString(payload)

    private fun page(payload: String): DynamicAuthorUpdatesPageResponse = json.decodeFromString(payload)

    private suspend fun assertNoSnapshot(load: suspend () -> Map<Long, Boolean>) {
        val failure = try {
            load()
            null
        } catch (failure: Exception) {
            failure
        }
        assertTrue("An incomplete or inconsistent response must be rejected",
            failure is IOException || failure is IllegalStateException)
    }
}
