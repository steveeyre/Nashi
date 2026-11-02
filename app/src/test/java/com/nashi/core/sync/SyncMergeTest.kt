package com.nashi.core.sync

import com.nashi.core.model.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拉取合并是最容易悄悄丢数据的地方: LWW 方向、本地专属列保留、墓碑与"本机已彻底删除"抑制。
 * 这些函数是纯 Kotlin, 不需要 Room / Android。
 */
class SyncMergeTest {

    private fun dto(
        id: String = "i1",
        name: String = "远端名",
        at: Long = 100L,
        deleted: Boolean = false,
        unit: String = "件",
        inTime: Long = 0L,
    ) = ItemDto(
        id = id, name = name, categoryId = "c1", quantity = 2.0, unit = unit,
        inTime = inTime, updatedAt = at, isDeleted = deleted,
        fieldValues = mapOf("volume" to "500ml"), tagIds = listOf("t1"),
    )

    private fun localItem(id: String = "i1", at: Long = 100L) = Item(
        id = id, name = "本地名", categoryId = "c1", quantity = 1.0,
        photoPathsCsv = "/data/photos/p1.jpg", qrPayload = "QR-1",
        x = 1f, y = 2f, z = 3f,
        updatedAt = at,
    )

    @Test
    fun `新建物品时补齐默认单位与入库时间`() {
        val item = dto(unit = "", inTime = 0L).toItem()

        assertEquals("件", item.unit)
        assertTrue("缺少入库时间时取当前时间", item.inTime > 0L)
        assertEquals("照片是本机独有, 默认空", "", item.photoPathsCsv)
        assertEquals("坐标不在协议内, 新建时归零", 0f, item.x, 0f)
    }

    @Test
    fun `覆盖时保留照片坐标与二维码等本地专属列`() {
        val local = localItem()
        val merged = local.applyRemote(dto(name = "远端名", at = 200L))

        assertEquals("远端名", merged.name)
        assertEquals(200L, merged.updatedAt)
        assertEquals("/data/photos/p1.jpg", merged.photoPathsCsv)
        assertEquals("QR-1", merged.qrPayload)
        assertEquals(1f, merged.x, 0f)
        assertEquals(2f, merged.y, 0f)
        assertEquals(3f, merged.z, 0f)
    }

    @Test
    fun `本地没有该物品时插入远端行`() {
        val decision = decideItemMerge(dto(at = 100L), null, emptySet())

        assertTrue(decision is ItemMerge.Insert)
        assertEquals("远端名", (decision as ItemMerge.Insert).item.name)
    }

    @Test
    fun `本地更新时忽略远端`() {
        val decision = decideItemMerge(dto(at = 100L), localItem(at = 200L), emptySet())

        assertEquals(ItemMerge.Skip, decision)
    }

    @Test
    fun `时间戳相同视为远端胜出以保证收敛`() {
        val decision = decideItemMerge(dto(name = "远端名", at = 200L), localItem(at = 200L), emptySet())

        assertTrue(decision is ItemMerge.Overwrite)
        assertEquals("远端名", (decision as ItemMerge.Overwrite).item.name)
    }

    @Test
    fun `本机彻底删除过的墓碑不再落库`() {
        val decision = decideItemMerge(dto(deleted = true), localItem(), setOf("i1"))

        assertEquals(ItemMerge.Skip, decision)
    }

    @Test
    fun `未彻底删除的墓碑仍要落库以便同步删除`() {
        val decision = decideItemMerge(dto(deleted = true, at = 200L), localItem(at = 100L), emptySet())

        assertTrue(decision is ItemMerge.Overwrite)
        assertTrue((decision as ItemMerge.Overwrite).item.isDeleted)
    }

    @Test
    fun `push 时带上字段值与标签并补设备号`() {
        val out = localItem().toDto(
            device = "dev-9",
            fieldValues = mapOf("volume" to "500ml"),
            tagIds = listOf("t1", "t2"),
        )

        assertEquals(mapOf("volume" to "500ml"), out.fieldValues)
        assertEquals(listOf("t1", "t2"), out.tagIds)
        assertEquals("dev-9", out.updatedBy)
        assertFalse(out.isDeleted)
    }
}
