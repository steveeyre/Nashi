package com.nashi.core.data

import androidx.room.*
import com.nashi.core.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NashiDao {
    // 基础查询: FTS 用 LIKE 首版, 后续换 FTS4 虚表
    @Query("SELECT * FROM items WHERE isDeleted=0 AND (name LIKE '%'||:q||'%' OR brand LIKE '%'||:q||'%' OR barcode=:q) ORDER BY updatedAt DESC LIMIT :limit")
    fun searchItems(q: String, limit: Int = 100): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE isDeleted=0 AND roomId=:roomId ORDER BY updatedAt DESC")
    fun itemsByRoom(roomId: String): Flow<List<Item>>

    // 列表页取可见物品后在内存里筛选/排序(ItemQuery), 便于叠加分类/房间/标签/低库存条件
    @Query("SELECT * FROM items WHERE isDeleted=0 ORDER BY updatedAt DESC")
    fun itemsFlow(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id=:id")
    fun itemFlow(id: String): Flow<Item?>

    @Query("SELECT * FROM itemTags")
    fun itemTagsFlow(): Flow<List<ItemTag>>

    @Query("SELECT * FROM itemTags WHERE itemId=:id")
    fun itemTagsFlowOf(id: String): Flow<List<ItemTag>>

    // 回收站: 墓碑物品(可恢复) 与彻底删除
    @Query("SELECT * FROM items WHERE isDeleted=1 ORDER BY updatedAt DESC")
    fun deletedItemsFlow(): Flow<List<Item>>

    @Query("DELETE FROM items WHERE id=:id")
    suspend fun hardDeleteItem(id: String)

    // 统计/临期/低库存: 规则在 StatsAggregator(纯 Kotlin, 可纯 JVM 测), DAO 只提供原始行
    // (allItems / allTags / allItemTags, 见文件末尾)。

    @Query("SELECT * FROM categories WHERE isDeleted=0 ORDER BY name") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM categoryFields WHERE categoryId=:c ORDER BY `key`") fun fieldDefs(c: String): Flow<List<CategoryFieldDef>>
    @Query("SELECT * FROM categoryFields") suspend fun allCategoryFields(): List<CategoryFieldDef>
    @Query("SELECT * FROM categoryFields") fun categoryFieldsFlow(): Flow<List<CategoryFieldDef>>
    @Query("DELETE FROM categoryFields WHERE id=:id") suspend fun deleteCategoryField(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCategoryFields(v: List<CategoryFieldDef>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertItem(v: Item)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFieldValues(v: List<ItemFieldValue>)
    @Query("SELECT * FROM items WHERE id=:id") suspend fun itemById(id: String): Item?
    @Query("SELECT * FROM fieldValues WHERE itemId=:id") suspend fun fieldValuesOf(id: String): List<ItemFieldValue>
    @Query("SELECT * FROM fieldValues WHERE itemId=:id") fun fieldValuesFlowOf(id: String): Flow<List<ItemFieldValue>>
    @Query("SELECT * FROM rooms WHERE isDeleted=0 ORDER BY name") fun roomsFlow(): Flow<List<HomeRoom>>
    @Query("SELECT * FROM spaces WHERE isDeleted=0") fun spacesAllFlow(): Flow<List<StorageSpace>>
    @Query("SELECT * FROM spaces WHERE roomId=:r AND isDeleted=0 ORDER BY positionIndex") fun spacesFlow(r: String): Flow<List<StorageSpace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertItems(v: List<Item>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRooms(v: List<HomeRoom>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSpaces(v: List<StorageSpace>)
    @Query("SELECT * FROM rooms WHERE id=:id") suspend fun roomById(id: String): HomeRoom?
    @Query("SELECT * FROM spaces WHERE id=:id") suspend fun spaceById(id: String): StorageSpace?
    @Query("UPDATE rooms SET isDeleted=1, updatedAt=:now WHERE id=:id") suspend fun deleteRoom(id: String, now: Long)
    @Query("UPDATE spaces SET isDeleted=1, updatedAt=:now WHERE roomId=:roomId") suspend fun deleteSpacesOfRoom(roomId: String, now: Long)
    @Query("UPDATE spaces SET isDeleted=1, updatedAt=:now WHERE id=:id") suspend fun deleteSpace(id: String, now: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCategories(v: List<Category>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTransactions(v: List<StockTransaction>)

    @Query("SELECT * FROM items WHERE updatedAt > :since ORDER BY updatedAt LIMIT 500")
    suspend fun dirtyItems(since: Long): List<Item>
    @Query("SELECT * FROM transactions WHERE createdAt > :since ORDER BY createdAt LIMIT 500")
    suspend fun dirtyTransactions(since: Long): List<StockTransaction>
    @Query("SELECT * FROM rooms") suspend fun allRooms(): List<HomeRoom>
    @Query("SELECT * FROM spaces WHERE roomId=:roomId ORDER BY positionIndex") suspend fun spacesOf(roomId: String): List<StorageSpace>

    // 标签 CRUD
    @Query("SELECT * FROM tags WHERE isDeleted=0 ORDER BY name") fun tagsFlow(): Flow<List<Tag>>
    @Query("SELECT * FROM tags WHERE isDeleted=0 ORDER BY name") suspend fun allTags(): List<Tag>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTags(v: List<Tag>)
    @Query("UPDATE tags SET isDeleted=1, updatedAt=:now WHERE id=:id") suspend fun deleteTag(id: String, now: Long)

    // ItemTag 关联读写
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertItemTags(v: List<ItemTag>)
    @Query("DELETE FROM itemTags WHERE itemId=:itemId") suspend fun deleteItemTagsOf(itemId: String)
    @Query("DELETE FROM itemTags WHERE itemId NOT IN (SELECT id FROM items)") suspend fun deleteOrphanItemTags()
    @Query("DELETE FROM fieldValues WHERE itemId NOT IN (SELECT id FROM items)") suspend fun deleteOrphanFieldValues()
    @Query("SELECT * FROM itemTags") suspend fun allItemTags(): List<ItemTag>

    // 分类: 追加删除(软删除, 同步墓碑), Flow 供管理页实时刷新
    @Query("SELECT * FROM categories WHERE isDeleted=0 ORDER BY name") fun categoriesFlow(): Flow<List<Category>>
    @Query("UPDATE categories SET isDeleted=1, updatedAt=:now WHERE id=:id") suspend fun deleteCategory(id: String, now: Long)

    // 导入导出全量读取
    @Query("SELECT * FROM items") suspend fun allItems(): List<Item>
    @Query("SELECT * FROM spaces") suspend fun allSpaces(): List<StorageSpace>
    @Query("SELECT * FROM transactions") suspend fun allTransactions(): List<StockTransaction>
    @Query("SELECT * FROM transactions WHERE itemId=:itemId ORDER BY createdAt DESC")
    suspend fun transactionsOf(itemId: String): List<StockTransaction>
    @Query("SELECT * FROM fieldValues") suspend fun allFieldValues(): List<ItemFieldValue>

    // 同步用: 含墓碑的全量配置行 + 字段值清理
    @Query("SELECT * FROM categories") suspend fun everyCategory(): List<Category>
    @Query("SELECT * FROM tags") suspend fun everyTag(): List<Tag>
    @Query("DELETE FROM categoryFields") suspend fun clearCategoryFields()
    @Query("DELETE FROM fieldValues WHERE itemId=:itemId") suspend fun deleteFieldValuesOf(itemId: String)
}

data class StatRow(val k: String, val c: Int, val q: Double)

@Database(
    entities = [Category::class, CategoryFieldDef::class, Item::class, ItemFieldValue::class, Tag::class, ItemTag::class, HomeRoom::class, StorageSpace::class, StockTransaction::class],
    version = 2, exportSchema = true,
)
abstract class NashiDatabase : RoomDatabase() {
    abstract fun dao(): NashiDao
}
