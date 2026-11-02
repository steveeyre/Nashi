package com.nashi.core.data

import android.content.Context
import com.nashi.core.model.Item
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemRepositoryBatchTest {
    private val dao = mockk<NashiDao>(relaxed = true)
    private val repo = ItemRepository(dao, mockk<Context>(relaxed = true))

    private fun item(id: String) = Item(
        id = id, name = "收纳盒", categoryId = "cat-other",
        roomId = "r1", spaceId = "s1", version = 2,
    )

    @Test
    fun `批量移动写入房间空间并递增版本`() = runBlocking {
        coEvery { dao.itemById("i1") } returns item("i1")
        val captured = slot<List<Item>>()

        repo.moveItems(listOf("i1"), roomId = "r2", spaceId = "s2")

        coVerify { dao.upsertItems(capture(captured)) }
        val moved = captured.captured.single()
        assertEquals("r2", moved.roomId)
        assertEquals("s2", moved.spaceId)
        assertEquals(3, moved.version)
        assertTrue(moved.updatedAt > 0)
    }

    @Test
    fun `批量改分类不动数量`() = runBlocking {
        coEvery { dao.itemById("i1") } returns item("i1").copy(quantity = 5.0)
        val captured = slot<List<Item>>()

        repo.setCategory(listOf("i1"), "cat-food")

        coVerify { dao.upsertItems(capture(captured)) }
        assertEquals("cat-food", captured.captured.single().categoryId)
        assertEquals(5.0, captured.captured.single().quantity, 0.0)
    }

    @Test
    fun `批量删除逐条写墓碑`() = runBlocking {
        coEvery { dao.itemById("i1") } returns item("i1")
        coEvery { dao.itemById("i2") } returns item("i2")

        repo.deleteItems(listOf("i1", "i2"))

        coVerify(exactly = 2) { dao.upsertItem(match { it.isDeleted }) }
    }

    @Test
    fun `维护在没有过期墓碑时只清孤儿行`() = runBlocking {
        coEvery { dao.allItems() } returns listOf(
            item("i1"),
            item("i2").copy(isDeleted = true, updatedAt = System.currentTimeMillis()),
        )

        val removed = repo.runMaintenance(now = System.currentTimeMillis())

        assertEquals(0, removed)
        coVerify(exactly = 1) { dao.deleteOrphanItemTags() }
        coVerify(exactly = 1) { dao.deleteOrphanFieldValues() }
        coVerify(exactly = 0) { dao.hardDeleteItem(any()) }
    }

    @Test
    fun `批量操作跳过已删除行`() = runBlocking {
        coEvery { dao.itemById("i1") } returns item("i1").copy(isDeleted = true, version = 4)

        repo.moveItems(listOf("i1"), roomId = "r2", spaceId = "")

        coVerify(exactly = 0) { dao.upsertItems(any()) }
    }
}
