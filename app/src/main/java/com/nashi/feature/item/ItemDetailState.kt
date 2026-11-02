package com.nashi.feature.item

import com.nashi.core.model.Category
import com.nashi.core.model.CategoryFieldDef
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.ItemFieldValue
import com.nashi.core.model.ItemTag
import com.nashi.core.model.StorageSpace
import com.nashi.core.model.Tag

data class ItemDetailState(
    val item: Item,
    val categoryName: String,
    val roomName: String,
    val spaceName: String,
    val tagNames: List<String>,
    val fields: List<Pair<String, String>>,
    val photos: List<String>,
)

/** 详情页展示数据的纯映射: 输入各项原始表, 输出名称/标签/字段/照片。 */
fun buildItemDetail(
    item: Item,
    categories: List<Category>,
    rooms: List<HomeRoom>,
    spaces: List<StorageSpace>,
    tags: List<Tag>,
    itemTags: List<ItemTag>,
    fieldDefs: List<CategoryFieldDef>,
    fieldValues: List<ItemFieldValue>,
): ItemDetailState {
    val tagIds = itemTags.map { it.tagId }.toSet()
    val values = fieldValues.associate { it.key to it.value }
    val fields = fieldDefs
        .filter { it.categoryId == item.categoryId }
        .sortedBy { it.key }
        .mapNotNull { def ->
            values[def.key]?.takeIf { it.isNotBlank() }?.let { def.label to it }
        }
    return ItemDetailState(
        item = item,
        categoryName = categories.firstOrNull { it.id == item.categoryId }?.name.orEmpty(),
        roomName = rooms.firstOrNull { it.id == item.roomId }?.name.orEmpty(),
        spaceName = spaces.firstOrNull { it.id == item.spaceId }?.name.orEmpty(),
        tagNames = tags.filter { it.id in tagIds }.map { it.name },
        fields = fields,
        photos = item.photoPathsCsv.split("|").filter { it.isNotBlank() },
    )
}
