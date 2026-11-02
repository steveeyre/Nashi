package com.nashi.core.data

import com.nashi.core.model.Item
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TombstonePolicyTest {
    private val now = 100L * 24 * 60 * 60 * 1000
    private val day = 24L * 60 * 60 * 1000

    private fun item(deleted: Boolean, at: Long) = Item(
        id = "i1", name = "收纳盒", categoryId = "cat-other", isDeleted = deleted, updatedAt = at,
    )

    @Test
    fun `超过保留期的墓碑判定为过期`() {
        assertTrue(TombstonePolicy.isExpired(item(true, now - 91 * day), now))
    }

    @Test
    fun `保留期内的墓碑保留`() {
        assertFalse(TombstonePolicy.isExpired(item(true, now - 89 * day), now))
    }

    @Test
    fun `未删除的行永不过期`() {
        assertFalse(TombstonePolicy.isExpired(item(false, 0L), now))
    }
}
