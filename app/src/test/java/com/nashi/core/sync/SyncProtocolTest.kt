package com.nashi.core.sync

import com.nashi.core.model.Item
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `push 信封序列化后能还原协议字段`() {
        val body = PushBody(
            changes = ChangeSet(
                items = listOf(
                    ItemDto(
                        id = "i1", name = "收纳盒", categoryId = "cat-other", quantity = 2.0,
                        roomId = "r1", spaceId = "s1", updatedAt = 1700000000000,
                        updatedBy = "dev-a", version = 3, isDeleted = false,
                    )
                ),
                transactions = listOf(TxDto("t1", "i1", -1.0, "out", 1700000000001, "dev-a")),
            ),
            deviceId = "dev-a",
        )
        val restored = json.decodeFromString<PushBody>(json.encodeToString(body))
        assertEquals(body, restored)
        assertEquals("i1", restored.changes.items.single().id)
        assertEquals(-1.0, restored.changes.transactions.single().delta, 0.0)
    }

    @Test
    fun `服务端新增字段不会让旧客户端解码失败`() {
        val raw = """{"changes":{"items":[],"rooms":[{"id":"r1","name":"客厅","updatedAt":1,"color":"warm"}]},"nextSince":9,"serverVersion":2}"""
        val resp = json.decodeFromString<PullResp>(raw)
        assertEquals("客厅", resp.changes.rooms.single().name)
        assertEquals(9L, resp.nextSince)
    }

    @Test
    fun `缺省字段按协议默认值补齐`() {
        val item = json.decodeFromString<ItemDto>("""{"id":"i1","name":"杯子","categoryId":"c","quantity":1.0,"updatedAt":5}""")
        assertEquals("", item.roomId)
        assertEquals("", item.spaceId)
        assertEquals(1, item.version)
        assertEquals(false, item.isDeleted)
        assertTrue("旧服务端不带字段值时视为空集合", item.fieldValues.isEmpty())
        assertTrue(item.tagIds.isEmpty())
    }

    @Test
    fun `物品携带字段值与标签集合往返还原`() {
        val body = PushBody(
            changes = ChangeSet(
                items = listOf(
                    ItemDto(
                        id = "i1", name = "洗衣液", categoryId = "cat-other", quantity = 1.0,
                        updatedAt = 1700000000000,
                        fieldValues = mapOf("volume" to "1.5L", "note" to "阳台"),
                        tagIds = listOf("tg1", "tg2"),
                    )
                ),
            ),
            deviceId = "dev-a",
        )
        val restored = json.decodeFromString<PushBody>(json.encodeToString(body))
        assertEquals(mapOf("volume" to "1.5L", "note" to "阳台"), restored.changes.items.single().fieldValues)
        assertEquals(listOf("tg1", "tg2"), restored.changes.items.single().tagIds)
    }

    @Test
    fun `分类标签与字段定义随增量同步往返还原`() {
        val body = ChangeSet(
            categories = listOf(
                CategoryDto("cat-drink", "饮料", icon = "local_drink", updatedAt = 11, isDeleted = true)
            ),
            tags = listOf(TagDto("tg1", "露营", updatedAt = 12, updatedBy = "dev-a", version = 2)),
            categoryFields = listOf(
                FieldDefDto("f1", "cat-drink", "volume", "容量", "number", required = true, optionsCsv = "")
            ),
        )
        val restored = json.decodeFromString<ChangeSet>(json.encodeToString(body))
        assertEquals(body, restored)
        assertEquals("local_drink", restored.categories.single().icon)
        assertTrue("删除墓碑必须随同步传播", restored.categories.single().isDeleted)
        assertEquals("容量", restored.categoryFields.single().label)
        assertTrue(restored.categoryFields.single().required)
    }

    @Test
    fun `encodeDefaults 打开时空集合也显式输出`() {
        val text = json.encodeToString(ChangeSet())
        assertTrue(text.contains("\"items\":[]"))
        assertTrue(text.contains("\"transactions\":[]"))
    }

    @Test
    fun `物品元数据随协议往返还原`() {
        val dto = ItemDto(
            id = "i1", name = "牛奶", categoryId = "cat-food", quantity = 2.0, updatedAt = 10,
            brand = "光明", unit = "盒", barcode = "6901234567890", inTime = 5,
            expiryDate = 99, minStock = 3.0, width = 10f, height = 20f, depth = 5f,
        )
        val restored = json.decodeFromString<ItemDto>(json.encodeToString(dto))
        assertEquals(dto, restored)
        assertEquals("光明", restored.brand)
        assertEquals("盒", restored.unit)
        assertEquals(99L, restored.expiryDate)
    }

    @Test
    fun `拉取合并用远端元数据覆盖本地且保留照片`() {
        val local = Item(
            id = "i1", name = "牛奶", categoryId = "cat-food", brand = "旧品牌",
            quantity = 1.0, photoPathsCsv = "/data/a.jpg|/data/b.jpg", updatedAt = 1, version = 1,
        )
        val dto = ItemDto(
            id = "i1", name = "牛奶", categoryId = "cat-food", quantity = 2.0, updatedAt = 20,
            brand = "光明", unit = "盒", expiryDate = 99, minStock = 3.0, version = 2,
        )
        val merged = local.applyRemote(dto)
        assertEquals("光明", merged.brand)
        assertEquals("盒", merged.unit)
        assertEquals(2.0, merged.quantity, 0.0)
        assertEquals(99L, merged.expiryDate)
        assertEquals(3.0, merged.minStock, 0.0)
        assertEquals(2, merged.version)
        assertEquals("本地照片列不受同步影响", "/data/a.jpg|/data/b.jpg", merged.photoPathsCsv)
    }

    @Test
    fun `远端新物品转本地实体保留入库时间与元数据`() {
        val dto = ItemDto(
            id = "i9", name = "毛巾", categoryId = "cat-daily", quantity = 1.0, updatedAt = 30,
            brand = "洁丽雅", unit = "条", inTime = 25,
        )
        val item = dto.toItem()
        assertEquals(25L, item.inTime)
        assertEquals("洁丽雅", item.brand)
        assertEquals("条", item.unit)
        assertEquals("", item.photoPathsCsv)
    }

    @Test
    fun `family 建群与加入回包字段对齐`() {
        val init = json.decodeFromString<FamilyInitResp>("""{"familyId":"f1","adminToken":"admin"}""")
        assertEquals("f1", init.familyId)

        val join = json.decodeFromString<JoinResp>("""{"familyId":"f1","deviceToken":"dev"}""")
        assertEquals("dev", join.deviceToken)

        val invite = json.encodeToString(InviteBody(familyId = "f1"))
        assertTrue(invite.contains("\"adminToken\":\"\""))
    }
}
