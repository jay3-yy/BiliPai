package com.android.purebilibili.feature.following

import com.android.purebilibili.data.model.response.RelationTagItem
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowGroupOrderPolicyTest {
    private fun groups(vararg ids: Long) = ids.map { RelationTagItem(tagid = it, name = "分组$it") }

    @Test
    fun movesInBothDirectionsWithoutChangingMembershipOrOriginal() {
        val original = groups(1, 2, 3)
        assertEquals(listOf(2L, 3L, 1L), moveFollowGroup(original, 1, 3).map { it.tagid })
        assertEquals(listOf(3L, 1L, 2L), moveFollowGroup(original, 3, 1).map { it.tagid })
        assertEquals(listOf(1L, 2L, 3L), original.map { it.tagid })
        assertEquals(original, moveFollowGroup(original, 1, 99))
    }

    @Test
    fun systemGroupsCannotBeDraggedAndTheirSlotsRemainFixed() {
        val original = groups(0, -10, 1, -2, 2, 3)
        assertEquals(listOf(0L, -10L, 2L, -2L, 3L, 1L), moveFollowGroup(original, 1, 3).map { it.tagid })
        listOf(0L, -10L, -2L, Long.MIN_VALUE).forEach { fixed ->
            assertFalse(isSortableFollowGroup(fixed))
            assertEquals(original, moveFollowGroup(original, fixed, 1))
            assertEquals(original, moveFollowGroup(original, 1, fixed))
        }
    }

    @Test
    fun savedOrderSurvivesLateMetadataAndPreservesNewGroups() {
        val lateMetadata = groups(-10, 0, 1, 2, 3, 4)
        val applied = applyFollowGroupOrder(lateMetadata, listOf(3, 1, 2))
        assertEquals(listOf(-10L, 0L, 3L, 1L, 2L, 4L), applied.map { it.tagid })
        assertEquals(lateMetadata.map { it.name }.toSet(), applied.map { it.name }.toSet())
    }

    @Test
    fun returningToOriginalOrderClearsDirtyState() {
        val original = groups(1, 2, 3).toImmutableList()
        assertTrue(FollowGroupOrderState(identity = com.android.purebilibili.core.store.AccountSessionIdentity(), original = original, draft = moveFollowGroup(original, 1, 2).toImmutableList()).dirty)
        assertFalse(FollowGroupOrderState(identity = com.android.purebilibili.core.store.AccountSessionIdentity(), original = original, draft = original).dirty)
        assertFalse(FollowGroupOrderState(identity = com.android.purebilibili.core.store.AccountSessionIdentity(), original = null, draft = original).dirty)
    }
    @Test
    fun savingTheLatestSetDropsDeletedCustomGroupsAndKeepsSystemGroups() {
        val cached = groups(-10, -2, 1, 2, 3)
        val latest = groups(2, 1)
        assertEquals(listOf(-10L, -2L, 2L, 1L), replaceSavedFollowGroups(cached, latest).map { it.tagid })
        assertEquals(listOf(-10L, -2L), replaceSavedFollowGroups(cached, emptyList()).map { it.tagid })
        assertEquals(listOf(-10L, -2L, 1L, 2L, 3L), cached.map { it.tagid })
    }

}
