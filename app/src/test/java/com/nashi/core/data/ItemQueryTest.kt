package com.nashi.core.data

import com.nashi.core.model.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemQueryTest {
    private fun item(
        id: String,
        name: String,
        categoryId: String = "cat-food",
        brand: String = "",
        quantity: Double = 1.0,
        minStock: Double = 0.0,
        roomId: String = "room-1",
        expiryDate: Long? = null,
        updatedAt: Long = 100,
        isDeleted: Boolean = false,
    ) = Item(
        id = id, name = name, categoryId = categoryId, brand = brand,
        quantity = quantity, minStock = minStock, roomId = roomId,
        expiryDate = expiryDate, updatedAt = updatedAt, isDeleted = isDeleted,
    )

    private val a = item("a", "牛奶", brand = "光明", quantity = 2.0, minStock = 3.0, expiryDate = 200, updatedAt = 30)
    private val b = item("b", "咖啡豆", categoryId = "cat-other", brand = "Lavazza", quantity = 5.0, expiryDate = 100, updatedAt = 20)
    private val c = item("c", "饼干", quantity = 1.0, updatedAt = 10)

    @Test
    fun `关键词匹配名称品牌条码且忽略大小写`() {
        val byName = ItemQuery.apply(listOf(a, b, c), ItemFilter(keyword = "咖啡"))
        assertEquals(listOf("b"), byName.map { it.id })
        val byBrand = ItemQuery.apply(listOf(a, b, c), ItemFilter(keyword = "lava"))
        assertEquals(listOf("b"), byBrand.map { it.id })
    }

    @Test
    fun `分类与房间筛选可叠加`() {
        val byCategory = ItemQuery.apply(listOf(a, b, c), ItemFilter(categoryId = "cat-food"))
        assertEquals(listOf("a", "c"), byCategory.map { it.id })
        val byRoom = ItemQuery.apply(
            listOf(a, b, c),
            ItemFilter(categoryId = "cat-food", roomId = "room-1"),
        )
        assertEquals(listOf("a", "c"), byRoom.map { it.id })
        val empty = ItemQuery.apply(listOf(a, b, c), ItemFilter(roomId = "room-9"))
        assertEquals(emptyList<String>(), empty.map { it.id })
    }

    @Test
    fun `标签筛选只保留该标签下的物品`() {
        val got = ItemQuery.apply(listOf(a, b, c), ItemFilter(tagId = "t1"), tagItemIds = setOf("b", "c"))
        assertEquals(listOf("b", "c"), got.map { it.id })
    }

    @Test
    fun `低库存筛选排除未设置阈值的物品`() {
        val got = ItemQuery.apply(listOf(a, b, c), ItemFilter(lowStockOnly = true))
        assertEquals(listOf("a"), got.map { it.id })
    }

    @Test
    fun `四种排序规则`() {
        val items = listOf(a, b, c)
        assertEquals(listOf("a", "b", "c"), ItemQuery.apply(items, ItemFilter(sort = ItemSort.UPDATED)).map { it.id })
        assertEquals(listOf("b", "a", "c"), ItemQuery.apply(items, ItemFilter(sort = ItemSort.NAME)).map { it.id })
        assertEquals(listOf("c", "a", "b"), ItemQuery.apply(items, ItemFilter(sort = ItemSort.QUANTITY)).map { it.id })
        assertEquals(listOf("b", "a", "c"), ItemQuery.apply(items, ItemFilter(sort = ItemSort.EXPIRY)).map { it.id })
    }

    @Test
    fun `已删除物品不进入结果`() {
        val hidden = item("d", "过期饼干", isDeleted = true)
        val got = ItemQuery.apply(listOf(a, hidden), ItemFilter())
        assertEquals(listOf("a"), got.map { it.id })
    }
}
