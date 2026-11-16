package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.feature.space.resolveSpaceDynamicCardItems
import org.junit.Assert.*
import org.junit.Test

class GoodsDynamicFilterTest {
    @Test
    fun `manual user blocks include authors and forwarded originals`() {
        val blocked = DynamicItem(modules = DynamicModules(module_author = DynamicAuthorModule(mid = 42)))
        assertTrue(isDynamicFromBlockedUser(blocked, setOf(42)))
        assertTrue(isDynamicFromBlockedUser(DynamicItem(orig = blocked), setOf(42)))
        assertFalse(isDynamicFromBlockedUser(blocked, emptySet()))
        assertFalse(isDynamicFromBlockedUser(DynamicItem(), setOf(42)))
    }

    @Test
    fun `goods cards and forwarded goods are filtered but ordinary posts are retained`() {
        val goods = DynamicItem(modules = DynamicModules(module_dynamic = DynamicContentModule(
            additional = DynamicAdditional(type = "ADDITIONAL_TYPE_GOODS")
        )))
        assertTrue(isGoodsDynamic(goods))
        assertTrue(isGoodsDynamic(DynamicItem(orig = goods)))
        assertFalse(isGoodsDynamic(DynamicItem()))
    }

    @Test
    fun `space conversion retains goods and dispute metadata including forwarded originals`() {
        val goods = SpaceDynamicItem(modules = SpaceDynamicModules(
            module_dynamic = SpaceDynamicContent(additional = DynamicAdditional(type = "ADDITIONAL_TYPE_GOODS")),
            module_dispute = DynamicDisputeModule(),
        ))
        assertTrue(isGoodsSpaceDynamic(SpaceDynamicItem(orig = goods)))
        assertFalse(isGoodsSpaceDynamic(SpaceDynamicItem()))
        val card = resolveSpaceDynamicCardItems(listOf(goods)).single()
        assertTrue(isGoodsDynamic(card))
        assertEquals(goods.modules.module_dispute, card.modules.module_dispute)
    }
}
