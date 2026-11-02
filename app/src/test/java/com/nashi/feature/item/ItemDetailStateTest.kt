package com.nashi.feature.item

import com.nashi.core.model.Category
import com.nashi.core.model.CategoryFieldDef
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.ItemFieldValue
import com.nashi.core.model.ItemTag
import com.nashi.core.model.StorageSpace
import com.nashi.core.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemDetailStateTest {
    private val item = Item(
        id = "i1", name = "耳机", categoryId = "cat-digital", roomId = "r1", spaceId = "s1",
        photoPathsCsv = "photos/a.jpg|photos/b.jpg",
    )

    @Test
    fun `名称标签字段照片按关联行组装`() {
        val state = buildItemDetail(
            item = item,
            categories = listOf(Category(id = "cat-digital", name = "数码")),
            rooms = listOf(HomeRoom(id = "r1", name = "书房")),
            spaces = listOf(StorageSpace(id = "s1", roomId = "r1", name = "第二层")),
            tags = listOf(Tag(id = "t1", name = "易碎"), Tag(id = "t2", name = "未使用")),
            itemTags = listOf(ItemTag("i1", "t1")),
            fieldDefs = listOf(
                CategoryFieldDef(id = "f2", categoryId = "cat-digital", key = "voltage", label = "电压", type = "text"),
                CategoryFieldDef(id = "f1", categoryId = "cat-digital", key = "color", label = "颜色", type = "text"),
            ),
            fieldValues = listOf(
                ItemFieldValue("i1", "color", "白"),
                ItemFieldValue("i1", "voltage", "5V"),
            ),
        )

        assertEquals("数码", state.categoryName)
        assertEquals("书房", state.roomName)
        assertEquals("第二层", state.spaceName)
        assertEquals(listOf("易碎"), state.tagNames)
        assertEquals(listOf("颜色" to "白", "电压" to "5V"), state.fields)
        assertEquals(listOf("photos/a.jpg", "photos/b.jpg"), state.photos)
    }

    @Test
    fun `空字段值与其它分类的字段定义被忽略`() {
        val state = buildItemDetail(
            item = item,
            categories = emptyList(),
            rooms = emptyList(),
            spaces = emptyList(),
            tags = emptyList(),
            itemTags = emptyList(),
            fieldDefs = listOf(
                CategoryFieldDef(id = "f1", categoryId = "cat-digital", key = "color", label = "颜色", type = "text"),
                CategoryFieldDef(id = "f9", categoryId = "cat-other", key = "color", label = "别的", type = "text"),
            ),
            fieldValues = listOf(ItemFieldValue("i1", "color", "  ")),
        )

        assertEquals(emptyList<Pair<String, String>>(), state.fields)
    }

    @Test
    fun `无照片时列表为空`() {
        val state = buildItemDetail(
            item = item.copy(photoPathsCsv = ""),
            categories = emptyList(), rooms = emptyList(), spaces = emptyList(),
            tags = emptyList(), itemTags = emptyList(),
            fieldDefs = emptyList(), fieldValues = emptyList(),
        )

        assertEquals(emptyList<String>(), state.photos)
    }
}
