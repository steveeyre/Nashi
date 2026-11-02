package com.nashi.core.data

import com.nashi.core.model.Item

enum class ItemSort(val label: String) {
    UPDATED("最近更新"),
    NAME("名称"),
    QUANTITY("数量从少到多"),
    EXPIRY("临期优先"),
}

data class ItemFilter(
    val keyword: String = "",
    val categoryId: String = "",
    val roomId: String = "",
    val tagId: String = "",
    val lowStockOnly: Boolean = false,
    val sort: ItemSort = ItemSort.UPDATED,
)

/** 物品列表的筛选/排序规则: 纯 Kotlin, 可纯 JVM 测。 */
object ItemQuery {
    fun apply(
        items: List<Item>,
        filter: ItemFilter,
        tagItemIds: Set<String> = emptySet(),
    ): List<Item> {
        val q = filter.keyword.trim()
        val matched = items.filter { item ->
            !item.isDeleted &&
                (q.isEmpty() || item.name.contains(q, true) || item.brand.contains(q, true) ||
                    item.barcode.contains(q, true)) &&
                (filter.categoryId.isEmpty() || item.categoryId == filter.categoryId) &&
                (filter.roomId.isEmpty() || item.roomId == filter.roomId) &&
                (filter.tagId.isEmpty() || item.id in tagItemIds) &&
                (!filter.lowStockOnly || isLowStock(item))
        }
        return when (filter.sort) {
            ItemSort.UPDATED -> matched.sortedByDescending { it.updatedAt }
            ItemSort.NAME -> matched.sortedBy { it.name.lowercase() }
            ItemSort.QUANTITY -> matched.sortedWith(compareBy({ it.quantity }, { it.name }))
            ItemSort.EXPIRY -> matched.sortedWith(
                compareBy<Item> { it.expiryDate ?: Long.MAX_VALUE }.thenByDescending { it.updatedAt },
            )
        }
    }

    fun isLowStock(item: Item): Boolean = item.minStock > 0 && item.quantity <= item.minStock
}
