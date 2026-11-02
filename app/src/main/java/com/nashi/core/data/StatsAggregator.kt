package com.nashi.core.data

import com.nashi.core.model.Item
import com.nashi.core.model.ItemTag
import com.nashi.core.model.Tag

/**
 * 基础功能 9(分类统计) / 8(保质期筛选) / 10(低库存阈值) 的纯 Kotlin 实现。
 *
 * 这些规则原先写在 NashiDao 的 @Query 里(SQL), 纯 JVM 单测无法覆盖; 现搬到这里,
 * DAO 只负责 `SELECT *` 取原始行(NashiDao.allItems/allTags/allItemTags)。
 * 每条规则与原 SQL 原文一一对应:
 * - [statByRoom]     : `WHERE isDeleted=0 GROUP BY roomId`  → k=roomId, c=COUNT(*), q=SUM(quantity)
 * - [statByCategory] : `WHERE isDeleted=0 GROUP BY categoryId`
 * - [statByBrand]    : `WHERE isDeleted=0 GROUP BY brand`
 * - [statByTag]      : `itemTags it JOIN tags t ON t.id=it.tagId JOIN items i ON i.id=it.itemId
 *                        WHERE i.isDeleted=0 AND t.isDeleted=0 GROUP BY t.id, t.name` → k=tag.name
 * - [expiringBefore] : `WHERE isDeleted=0 AND expiryDate IS NOT NULL AND expiryDate < :before ORDER BY expiryDate`
 * - [lowStock]       : `WHERE isDeleted=0 AND quantity <= minStock AND minStock > 0`
 */
object StatsAggregator {

    fun statByRoom(items: List<Item>): List<StatRow> = groupByKey(items) { it.roomId }

    fun statBySpace(items: List<Item>): List<StatRow> = groupByKey(items) { it.spaceId }

    fun statByCategory(items: List<Item>): List<StatRow> = groupByKey(items) { it.categoryId }

    fun statByBrand(items: List<Item>): List<StatRow> = groupByKey(items) { it.brand }

    /**
     * 标签统计: 双重过滤(已删除物品的连线、已删除标签都不计入), 以 tag.id 分组、输出 tag.name。
     * 一个物品打了 N 个标签, 就在 N 个分组里各计 1 次 —— 与 `COUNT(*)` over JOIN 一致。
     */
    fun statByTag(items: List<Item>, tags: List<Tag>, itemTags: List<ItemTag>): List<StatRow> {
        val itemById = items.filter { !it.isDeleted }.associateBy { it.id }
        val tagById = tags.filter { !it.isDeleted }.associateBy { it.id }
        val counts = LinkedHashMap<String, Int>()
        val sums = LinkedHashMap<String, Double>()
        for (link in itemTags) {
            val tag = tagById[link.tagId] ?: continue
            val item = itemById[link.itemId] ?: continue
            counts[tag.id] = (counts[tag.id] ?: 0) + 1
            sums[tag.id] = (sums[tag.id] ?: 0.0) + item.quantity
        }
        return counts.keys.map { tagId ->
            StatRow(k = tagById.getValue(tagId).name, c = counts.getValue(tagId), q = sums.getValue(tagId))
        }
    }

    /** 到期/临期筛选: 严格 `expiryDate < before`, 跳过 null 与已删除, 按 expiryDate 升序。 */
    fun expiringBefore(items: List<Item>, before: Long): List<Item> =
        items.filter { !it.isDeleted && it.expiryDate != null && it.expiryDate < before }
            .sortedBy { it.expiryDate }

    /** 低库存阈值: `minStock > 0 && quantity <= minStock`, 排除已删除。 */
    fun lowStock(items: List<Item>): List<Item> =
        items.filter { !it.isDeleted && it.minStock > 0 && it.quantity <= it.minStock }

    private inline fun groupByKey(items: List<Item>, key: (Item) -> String): List<StatRow> {
        val counts = LinkedHashMap<String, Int>()
        val sums = LinkedHashMap<String, Double>()
        for (item in items) {
            if (item.isDeleted) continue
            val k = key(item)
            counts[k] = (counts[k] ?: 0) + 1
            sums[k] = (sums[k] ?: 0.0) + item.quantity
        }
        return counts.keys.map { StatRow(k = it, c = counts.getValue(it), q = sums.getValue(it)) }
    }
}
