package com.nashi.core.data

import com.nashi.core.model.HomeRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {
    private fun room(id: String, name: String, version: Int = 1) = HomeRoom(id, name, version = version)

    @Test
    fun `空库返回全部九个默认房间`() {
        val upserts = SeedData.roomPresetUpserts(emptyList(), now = 1000L)

        assertEquals(9, upserts.size)
        assertEquals(
            listOf("主卧", "次卧", "儿童房", "主卫", "次卫", "厨房", "客厅", "餐厅", "多功能间"),
            upserts.map { it.name },
        )
    }

    @Test
    fun `旧版四个默认房间原地改名并补齐其余房间`() {
        val old = listOf(
            room("room-living", "客厅"),
            room("room-bed", "卧室"),
            room("room-kitchen", "厨房"),
            room("room-bath", "卫生间"),
        )

        val upserts = SeedData.roomPresetUpserts(old, now = 1000L)

        assertEquals(
            setOf("主卧", "次卧", "儿童房", "主卫", "次卫", "餐厅", "多功能间"),
            upserts.map { it.name }.toSet(),
        )
        val renamed = upserts.first { it.id == "room-bed" }
        assertEquals("主卧", renamed.name)
        assertEquals(2, renamed.version)
        assertEquals(1000L, renamed.updatedAt)
    }

    @Test
    fun `用户自定义过的房间名不被覆盖`() {
        val existing = listOf(
            room("room-living", "客厅"),
            room("room-bed", "老人房"),
            room("room-kitchen", "厨房"),
            room("room-bath", "卫生间"),
        )

        val upserts = SeedData.roomPresetUpserts(existing, now = 1000L)

        assertTrue(upserts.none { it.id == "room-bed" })
        assertTrue(upserts.any { it.id == "room-bath" && it.name == "主卫" })
        assertEquals(6, upserts.size)
    }

    @Test
    fun `已是最新时不重复写入`() {
        assertTrue(SeedData.roomPresetUpserts(SeedData.rooms, now = 1000L).isEmpty())
    }
}
