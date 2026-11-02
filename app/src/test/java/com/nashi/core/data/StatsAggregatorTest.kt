package com.nashi.core.data

import com.nashi.core.model.Item
import com.nashi.core.model.ItemTag
import com.nashi.core.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 功能9(分类统计) / 8(临期筛选) / 10(低库存阈值) 的纯 JVM 测试。
 *
 * 被测对象是 StatsAggregator —— 生产用的就是这一份实现(StatsViewModel / MoreViewModel 直接调用),
 * 不是测试替身。原先这些规则写在 NashiDao 的 @Query 里, 靠 Robolectric+Room 真库才能验;
 * 搬到纯 Kotlin 后不依赖 Android、不联网。
 *
 * 本文件逐条承接已删除的 NashiDaoTest.kt(Robolectric) 的统计/临期/低库存断言,
 * 断言数值与边界一字未放宽。
 */
class StatsAggregatorTest {

    private fun item(
        id: String,
        name: String = "n-$id",
        categoryId: String = "c0",
        brand: String = "",
        quantity: Double = 1.0,
        roomId: String = "",
        spaceId: String = "",
        minStock: Double = 0.0,
        expiryDate: Long? = null,
        isDeleted: Boolean = false,
    ) = Item(
        id = id,
        name = name,
        categoryId = categoryId,
        brand = brand,
        quantity = quantity,
        roomId = roomId,
        spaceId = spaceId,
        minStock = minStock,
        expiryDate = expiryDate,
        isDeleted = isDeleted,
    )

    private fun tag(id: String, name: String, isDeleted: Boolean = false) =
        Tag(id = id, name = name, isDeleted = isDeleted)

    // ---------- 分房间/类别/品牌聚合 ----------

    @Test
    fun `statByRoom 分组计数与求和并排除已删除`() {
        val rows = StatsAggregator.statByRoom(
            listOf(
                item("a", roomId = "r1", quantity = 2.0),
                item("b", roomId = "r1", quantity = 3.0),
                item("c", roomId = "r2", quantity = 5.0),
                item("d", roomId = "r1", quantity = 100.0, isDeleted = true),
                item("e", roomId = "", quantity = 4.0),
            ),
        ).associateBy { it.k }

        assertEquals("未删除物品分 3 个房间(含空 roomId)", 3, rows.size)
        assertEquals(2, rows.getValue("r1").c)
        assertEquals(5.0, rows.getValue("r1").q, 0.0)
        assertEquals(1, rows.getValue("r2").c)
        assertEquals(5.0, rows.getValue("r2").q, 0.0)
        assertEquals(1, rows.getValue("").c)
        assertEquals(4.0, rows.getValue("").q, 0.0)
    }

    @Test
    fun `statBySpace 分组计数与求和并排除已删除`() {
        val rows = StatsAggregator.statBySpace(
            listOf(
                item("a", spaceId = "s1", quantity = 2.0),
                item("b", spaceId = "s1", quantity = 3.0),
                item("c", spaceId = "s2", quantity = 5.0),
                item("d", spaceId = "s2", quantity = 99.0, isDeleted = true),
                item("e", spaceId = "", quantity = 1.0),
            ),
        ).associateBy { it.k }

        assertEquals(3, rows.size)
        assertEquals(2, rows.getValue("s1").c)
        assertEquals(5.0, rows.getValue("s1").q, 0.0)
        assertEquals(1, rows.getValue("s2").c)
        assertEquals(5.0, rows.getValue("s2").q, 0.0)
        assertEquals(1, rows.getValue("").c)
    }

    @Test
    fun `statByCategory 分组计数与求和并排除已删除`() {
        val rows = StatsAggregator.statByCategory(
            listOf(
                item("a", categoryId = "c1", quantity = 1.5),
                item("b", categoryId = "c1", quantity = 2.5),
                item("c", categoryId = "c2", quantity = 7.0),
                item("d", categoryId = "c2", quantity = 9.0, isDeleted = true),
            ),
        ).associateBy { it.k }

        assertEquals(2, rows.size)
        assertEquals(2, rows.getValue("c1").c)
        assertEquals(4.0, rows.getValue("c1").q, 0.0)
        assertEquals(1, rows.getValue("c2").c)
        assertEquals(7.0, rows.getValue("c2").q, 0.0)
    }

    @Test
    fun `statByBrand 分组计数与求和并排除已删除`() {
        val rows = StatsAggregator.statByBrand(
            listOf(
                item("a", brand = "Acme", quantity = 1.0),
                item("b", brand = "Acme", quantity = 2.0),
                item("c", brand = "Beta", quantity = 4.0),
                item("d", brand = "Beta", quantity = 8.0, isDeleted = true),
            ),
        ).associateBy { it.k }

        assertEquals(2, rows.size)
        assertEquals(2, rows.getValue("Acme").c)
        assertEquals(3.0, rows.getValue("Acme").q, 0.0)
        assertEquals(1, rows.getValue("Beta").c)
        assertEquals(4.0, rows.getValue("Beta").q, 0.0)
    }

    @Test
    fun `统计查询排除软删除物品`() {
        val items = listOf(
            item("live", roomId = "r1", categoryId = "c1", brand = "B", quantity = 3.0),
            item("dead", roomId = "r1", categoryId = "c1", brand = "B", quantity = 99.0, isDeleted = true),
        )
        assertEquals(1, StatsAggregator.statByRoom(items).single().c)
        assertEquals(3.0, StatsAggregator.statByRoom(items).single().q, 0.0)
        assertEquals(1, StatsAggregator.statByCategory(items).single().c)
        assertEquals(3.0, StatsAggregator.statByCategory(items).single().q, 0.0)
        assertEquals(1, StatsAggregator.statByBrand(items).single().c)
        assertEquals(3.0, StatsAggregator.statByBrand(items).single().q, 0.0)
    }

    @Test
    fun `统计空物品表返回空结果`() {
        assertTrue(StatsAggregator.statByRoom(emptyList()).isEmpty())
        assertTrue(StatsAggregator.statByCategory(emptyList()).isEmpty())
        assertTrue(StatsAggregator.statByBrand(emptyList()).isEmpty())
        assertTrue(StatsAggregator.statByTag(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    // ---------- 标签聚合 (JOIN 语义) ----------

    @Test
    fun `statByTag 双重过滤已删除的物品与标签`() {
        val items = listOf(
            item("i1", quantity = 2.0),
            item("i2", quantity = 7.0, isDeleted = true),
            item("i3", quantity = 4.0),
            item("i4", quantity = 6.0),
        )
        val tags = listOf(
            tag("t1", "食品"),
            tag("t2", "废弃物标签", isDeleted = true),
            tag("t3", "工具"),
        )
        val links = listOf(
            ItemTag("i1", "t1"),
            ItemTag("i2", "t1"),
            ItemTag("i1", "t2"),
            ItemTag("i3", "t3"),
            ItemTag("i4", "t3"),
        )

        val rows = StatsAggregator.statByTag(items, tags, links).associateBy { it.k }

        assertEquals("已删除标签不得出现", 2, rows.size)
        assertEquals(1, rows.getValue("食品").c)
        assertEquals(2.0, rows.getValue("食品").q, 0.0)
        assertEquals(2, rows.getValue("工具").c)
        assertEquals(10.0, rows.getValue("工具").q, 0.0)
        assertFalse(rows.containsKey("废弃物标签"))
    }

    @Test
    fun `statByTag 关联表里指向已删除物品的连线也不计入`() {
        val items = listOf(item("i1", quantity = 2.0), item("i2", quantity = 8.0, isDeleted = true))
        val tags = listOf(tag("t1", "食品"))
        val links = listOf(ItemTag("i1", "t1"), ItemTag("i2", "t1"))

        val rows = StatsAggregator.statByTag(items, tags, links).associateBy { it.k }

        assertEquals(1, rows.getValue("食品").c)
        assertEquals("被删除物品的数量不得累加", 2.0, rows.getValue("食品").q, 0.0)
    }

    @Test
    fun `statByTag 同一物品打多个标签时每个标签各计一次`() {
        val items = listOf(item("i1", quantity = 3.0))
        val tags = listOf(tag("t1", "食品"), tag("t2", "易碎"))
        val links = listOf(ItemTag("i1", "t1"), ItemTag("i1", "t2"))

        val rows = StatsAggregator.statByTag(items, tags, links).associateBy { it.k }

        assertEquals(2, rows.size)
        assertEquals(1, rows.getValue("食品").c)
        assertEquals(3.0, rows.getValue("食品").q, 0.0)
        assertEquals(1, rows.getValue("易碎").c)
        assertEquals(3.0, rows.getValue("易碎").q, 0.0)
    }

    @Test
    fun `statByTag 同名标签按 tagId 各出一行`() {
        // 原 SQL 是 GROUP BY t.id, t.name: 两个不同 id 同名标签不会合并
        val items = listOf(item("i1", quantity = 2.0), item("i2", quantity = 5.0))
        val tags = listOf(tag("t1", "同名"), tag("t2", "同名"))
        val links = listOf(ItemTag("i1", "t1"), ItemTag("i2", "t2"))

        val rows = StatsAggregator.statByTag(items, tags, links)

        assertEquals(2, rows.size)
        assertEquals(listOf("同名", "同名"), rows.map { it.k })
        assertEquals(listOf(1, 1), rows.map { it.c })
        assertEquals(listOf(2.0, 5.0), rows.map { it.q })
    }

    @Test
    fun `statByTag 指向不存在标签的连线被忽略`() {
        val items = listOf(item("i1", quantity = 2.0))
        val tags = listOf(tag("t1", "食品"))
        val links = listOf(ItemTag("i1", "t1"), ItemTag("i1", "ghost"))

        val rows = StatsAggregator.statByTag(items, tags, links)

        assertEquals(1, rows.size)
        assertEquals(2.0, rows.single().q, 0.0)
    }

    // ---------- expiringBefore 边界 ----------

    @Test
    fun `expiringBefore 严格小于 before 且跳过 null 并排除已删除`() {
        val before = 1_000L
        val items = listOf(
            item("eq", expiryDate = before),                            // 恰好等于 -> 不返回(严格 <)
            item("lt1", expiryDate = before - 1),                       // 返回
            item("lt2", expiryDate = before - 5),                       // 返回, 且应排在 lt1 之前
            item("gt", expiryDate = before + 1),                        // 不返回
            item("nul", expiryDate = null),                             // 不返回(IS NOT NULL)
            item("del", expiryDate = before - 100, isDeleted = true),    // 不返回(已删除)
        )

        val ids = StatsAggregator.expiringBefore(items, before).map { it.id }

        assertEquals("只应返回未删除且 expiryDate < before 的两条, 且按 expiryDate 升序", listOf("lt2", "lt1"), ids)
    }

    @Test
    fun `expiringBefore 恰好等于边界时不返回`() {
        val before = 5_000L
        val items = listOf(item("exact", expiryDate = before))
        assertTrue("expiryDate == before 必须被排除(严格小于)", StatsAggregator.expiringBefore(items, before).isEmpty())
    }

    @Test
    fun `expiringBefore 全为 null 的过期字段返回空`() {
        val items = listOf(item("a"), item("b"))
        assertTrue(StatsAggregator.expiringBefore(items, Long.MAX_VALUE).isEmpty())
    }

    // ---------- lowStock 边界 ----------

    @Test
    fun `lowStock 要求 minStock 大于 0 且 quantity 不大于 minStock`() {
        val items = listOf(
            item("eq", quantity = 5.0, minStock = 5.0),                 // == -> 命中(<=)
            item("below", quantity = 2.0, minStock = 5.0),              // <  -> 命中
            item("above", quantity = 6.0, minStock = 5.0),              // >  -> 不命中
            item("zeroMin", quantity = 0.0, minStock = 0.0),            // minStock=0 -> 不命中
            item("negative", quantity = 0.0, minStock = -1.0),          // minStock<0 -> 不命中
            item("del", quantity = 1.0, minStock = 5.0, isDeleted = true), // 已删除 -> 不命中
        )

        val ids = StatsAggregator.lowStock(items).map { it.id }.sorted()

        assertEquals("只有 eq 与 below 命中", listOf("below", "eq"), ids)
    }
}
