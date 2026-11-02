package com.nashi.core.data

import com.nashi.core.model.Category
import com.nashi.core.model.CategoryFieldDef
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.StorageSpace

// 默认分类 + 各分类动态字段 + 默认房间空间, 固定 id, 可重复 seed(REPLACE 幂等)
object SeedData {
    val categories = listOf(
        Category("cat-food", "食品"),
        Category("cat-med", "药品"),
        Category("cat-daily", "日用品"),
        Category("cat-electro", "电子产品"),
        Category("cat-cloth", "衣物"),
        Category("cat-other", "其他"),
    )

    val fields = listOf(
        CategoryFieldDef("f-food-exp", "cat-food", "expiry", "保质期", "date", true),
        CategoryFieldDef("f-food-taste", "cat-food", "taste", "口味", "text"),
        CategoryFieldDef("f-food-spec", "cat-food", "spec", "净含量", "text"),
        CategoryFieldDef("f-med-exp", "cat-med", "expiry", "有效期", "date", true),
        CategoryFieldDef("f-med-use", "cat-med", "usage", "用法用量", "text"),
        CategoryFieldDef("f-med-for", "cat-med", "indication", "适应症", "text"),
        CategoryFieldDef("f-daily-spec", "cat-daily", "spec", "规格", "text"),
        CategoryFieldDef("f-daily-note", "cat-daily", "note", "备注", "text"),
        CategoryFieldDef("f-electro-model", "cat-electro", "model", "型号", "text"),
        CategoryFieldDef("f-electro-bought", "cat-electro", "boughtDate", "购买日期", "date"),
        CategoryFieldDef("f-electro-war", "cat-electro", "warranty", "保修期", "text"),
        CategoryFieldDef("f-cloth-size", "cat-cloth", "size", "尺码", "enum", true, "S,M,L,XL,均码"),
        CategoryFieldDef("f-cloth-color", "cat-cloth", "color", "颜色", "text"),
        CategoryFieldDef("f-cloth-mat", "cat-cloth", "material", "材质", "text"),
        CategoryFieldDef("f-other-note", "cat-other", "note", "备注", "text"),
    )

    // 复用 room-bed/room-bath 两个旧 id: 老版本的「卧室/卫生间」改名成主卧/主卫,
    // 已放在里面的物品不会因为换 id 而丢失
    val rooms = listOf(
        HomeRoom("room-bed", "主卧"),
        HomeRoom("room-bed-2", "次卧"),
        HomeRoom("room-kids", "儿童房"),
        HomeRoom("room-bath", "主卫"),
        HomeRoom("room-bath-2", "次卫"),
        HomeRoom("room-kitchen", "厨房"),
        HomeRoom("room-living", "客厅"),
        HomeRoom("room-dining", "餐厅"),
        HomeRoom("room-multi", "多功能间"),
    )

    val spaces = listOf(
        StorageSpace("sp-living-cab", "room-living", "客厅柜", width = 120f, depth = 40f, shelfHeight = 40f, columns = 3),
        StorageSpace("sp-bed-ward", "room-bed", "衣柜", width = 150f, depth = 60f, shelfHeight = 50f, columns = 2),
        StorageSpace("sp-kitchen-cup", "room-kitchen", "橱柜", width = 100f, depth = 50f, shelfHeight = 35f, columns = 2),
        StorageSpace("sp-bath-rack", "room-bath", "储物架", width = 60f, depth = 30f, shelfHeight = 30f, columns = 2),
    )

    /**
     * 老安装补齐默认房间: 旧默认名(卧室/卫生间)原地改名, 已有同名房间不重复添加。
     * 返回需要写入数据库的行; 已是最新时返回空列表。
     */
    fun roomPresetUpserts(existing: List<HomeRoom>, now: Long): List<HomeRoom> {
        val byId = existing.associateBy { it.id }
        val renames = mapOf("room-bed" to ("卧室" to "主卧"), "room-bath" to ("卫生间" to "主卫"))
        val renamed = renames.mapNotNull { (id, pair) ->
            val room = byId[id] ?: return@mapNotNull null
            if (room.name != pair.first) return@mapNotNull null
            room.copy(name = pair.second, updatedAt = now, version = room.version + 1)
        }
        val takenIds = existing.map { it.id }.toSet()
        val takenNames = existing.map { it.name }.toSet() + renamed.map { it.name }
        val added = rooms.filter { it.id !in takenIds && it.name !in takenNames }
        return renamed + added
    }
}
